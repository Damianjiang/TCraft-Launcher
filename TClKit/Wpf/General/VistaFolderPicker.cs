using System;
using System.Runtime.InteropServices;
using System.Text;

namespace TClKit.Wpf;

/// <summary>
/// 自写的 Vista 风格文件夹选择对话框（IFileDialog COM 封装，第 130 轮替代 Ookii.Dialogs.Wpf 以减少内嵌体积）。
/// <para/>第 137 轮修复：原先用 <c>[ComImport]</c> 类 + <c>new</c> 激活对话框，在实际机器上抛出
/// <c>REGDB_E_CLASSNOTREG (0x80040154)</c>（该 CLSID 明明已注册，是 CLR 的类厂激活路径问题），
/// 异常被 <see cref="Dialogs.SelectFolder"/> 吞掉并返回空数组，表现为「点了没反应」。
/// 现改为直接 P/Invoke <c>CoCreateInstance</c> 取接口指针，并在失败时回落到 shell32 的
/// <c>SHBrowseForFolder</c>（老式树形选择框），保证任何环境下都能选到文件夹。
/// </summary>
public static class VistaFolderPicker {

    #region COM 声明

    [ComImport, Guid("42F85136-DB7E-439C-85F1-E4075D135FC8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IFileDialog {
        [PreserveSig] int Show(IntPtr owner);
        void SetFileTypes(uint count, IntPtr filters);
        void SetFileTypeIndex(uint index);
        [PreserveSig] uint GetFileTypeIndex();
        void Advise(IntPtr advise, out uint cookie);
        void Unadvise(uint cookie);
        void SetOptions(uint options);
        void GetOptions(out uint options);
        void SetDefaultFolder(IShellItem folder);
        void SetFolder(IShellItem folder);
        void GetFolder(out IShellItem folder);
        void GetCurrentSelection(out IShellItem item);
        void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string name);
        void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string name);
        void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title);
        void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string text);
        void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
        void GetResult(out IShellItem item);
        void AddPlace(IShellItem place, int mode);
        void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string extension);
        void Close(int result);
        void SetClientGuid(ref Guid guid);
        void ClearClientData();
        void SetFilter(IntPtr filter);
    }

    [ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IShellItem {
        void BindToHandler(IntPtr bindContext, ref Guid handlerGuid, ref Guid interfaceGuid, out IntPtr handler);
        void GetParent(out IShellItem parent);
        void GetDisplayName(uint nameKind, [MarshalAs(UnmanagedType.LPWStr)] out string displayName);
        void GetAttributes(uint mask, out uint attributes);
        void Compare(IShellItem other, uint hint, out int order);
    }

    // CLSID_FileOpenDialog = {DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}（注册表 HKCR\CLSID 下名称 "File Open Dialog"）。
    // 第 137 轮修正：第 130 轮这里写成了 DC1C5A6C-2EF0-4A3A-950E-372E59EA70D4 —— 整段 GUID 都抄错，
    // 该 CLSID 在任何系统上都不存在，CoCreateInstance 必然返回 REGDB_E_CLASSNOTREG (0x80040154)，
    // 于是「添加已有文件夹」永远静默失败（异常被 SelectFolder 吞掉）。
    private static readonly Guid ClsidFileOpenDialog = new("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7");
    // IID_IFileDialog = {42F85136-DB7E-439C-85F1-E4075D135FC8}（注册表 HKCR\Interface 下名称 "IFileDialog"）。
    // 我们只需要 SetOptions / SetFolder / GetResult，全部在 IFileDialog 上，无需 IFileOpenDialog 的两个扩展方法。
    private static readonly Guid IidFileDialog = new("42F85136-DB7E-439C-85F1-E4075D135FC8");

    private const uint FosPickFolders = 0x20;
    private const uint FosForceFileSystem = 0x40;
    private const uint FosPathMustExist = 0x800;
    private const uint SigdnFilesystemPath = 0x80058000;
    private const uint ClsctxInprocServer = 0x1;

    [DllImport("ole32.dll")]
    private static extern int CoCreateInstance(ref Guid rclsid, IntPtr pUnkOuter, uint dwClsContext,
        ref Guid riid, [MarshalAs(UnmanagedType.Interface)] out IFileDialog ppv);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
    private static extern void SHCreateItemFromParsingName([MarshalAs(UnmanagedType.LPWStr)] string path,
        IntPtr bindContext, ref Guid interfaceGuid, [MarshalAs(UnmanagedType.Interface)] out IShellItem item);

    #endregion

    #region shell32 兜底（SHBrowseForFolder）

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct BrowseInfo {
        public IntPtr Owner;
        public IntPtr Root;
        public IntPtr DisplayName;
        [MarshalAs(UnmanagedType.LPWStr)] public string Title;
        public uint Flags;
        public IntPtr Callback;
        public IntPtr Param;
        public int Image;
    }

    private const uint BifReturnOnlyFsDirs = 0x0001;
    private const uint BifNewDialogStyle = 0x0040;
    private const uint BifEditBox = 0x0010;

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr SHBrowseForFolder(ref BrowseInfo info);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern bool SHGetPathFromIDList(IntPtr pidl, StringBuilder path);

    [DllImport("ole32.dll")]
    private static extern void CoTaskMemFree(IntPtr ptr);

    #endregion

    /// <summary>上一次调用是否「创建对话框失败」（用于区分用户取消与失败，只有失败才走兜底或提示）。</summary>
    [ThreadStatic]
    private static bool CreateFailed;

    [ThreadStatic]
    private static string? LastError;

    /// <summary>最近一次失败原因（成功或用户取消时为空）。</summary>
    public static string? ErrorMessage => LastError;

    /// <summary>
    /// 弹出文件夹选择对话框，返回以分隔符结尾的文件夹路径，取消返回 null。
    /// </summary>
    public static string? PickFolder(string title, string? initialFolder) {
        LastError = null;
        var result = PickFolderVista(title, initialFolder);
        if (result is not null) return result;
        if (!CreateFailed) return null; // 用户取消
        Logger.Warn("Vista 文件夹选择框不可用，回落到 SHBrowseForFolder");
        return PickFolderLegacy(title);
    }

    private static string? PickFolderVista(string title, string? initialFolder) {
        CreateFailed = false;
        IFileDialog? dialog = null;
        try {
            var clsid = ClsidFileOpenDialog;
            var iid = IidFileDialog;
            var hr = CoCreateInstance(ref clsid, IntPtr.Zero, ClsctxInprocServer, ref iid, out dialog);
            if (hr < 0 || dialog is null) {
                CreateFailed = true;
                LastError = $"CoCreateInstance 0x{hr:X8}";
                Logger.Warn($"创建文件夹选择框失败：CoCreateInstance HRESULT=0x{hr:X8}");
                return null;
            }

            dialog.GetOptions(out var options);
            dialog.SetOptions(options | FosPickFolders | FosForceFileSystem | FosPathMustExist);
            dialog.SetTitle(title);

            if (!string.IsNullOrEmpty(initialFolder)) {
                try {
                    var shellItemGuid = new Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");
                    SHCreateItemFromParsingName(initialFolder!, IntPtr.Zero, ref shellItemGuid, out var folderItem);
                    dialog.SetFolder(folderItem);
                    Marshal.ReleaseComObject(folderItem);
                } catch {
                    // 初始目录无效时忽略，不影响弹窗
                }
            }

            IntPtr owner = IntPtr.Zero;
            try {
                var window = System.Windows.Application.Current?.MainWindow;
                if (window != null) owner = new System.Windows.Interop.WindowInteropHelper(window).Handle;
            } catch {
                // 取不到主窗口时无宿主弹窗
            }

            var showHr = dialog.Show(owner);
            if (showHr != 0) return null; // 取消（S_FALSE / ERROR_CANCELLED）
            dialog.GetResult(out var resultItem);
            if (resultItem is null) return null;
            resultItem.GetDisplayName(SigdnFilesystemPath, out var path);
            Marshal.ReleaseComObject(resultItem);
            return string.IsNullOrEmpty(path) ? null : PathUtils.AddSlashSuffix(path);
        } catch (Exception ex) {
            CreateFailed = true;
            LastError = ex.Message;
            Logger.Warn(ex, "文件夹选择框出错");
            return null;
        } finally {
            if (dialog is not null) {
                try { _ = Marshal.ReleaseComObject(dialog); } catch { /* 已释放 */ }
            }
        }
    }

    /// <summary>老式树形文件夹选择框（SHBrowseForFolder），仅在 Vista 对话框创建失败时使用。</summary>
    private static string? PickFolderLegacy(string title) {
        var displayName = Marshal.AllocHGlobal(260 * sizeof(char));
        try {
            var info = new BrowseInfo {
                Owner = IntPtr.Zero,
                Root = IntPtr.Zero,
                DisplayName = displayName,
                Title = title,
                Flags = BifReturnOnlyFsDirs | BifNewDialogStyle | BifEditBox
            };
            try {
                var window = System.Windows.Application.Current?.MainWindow;
                if (window != null) info.Owner = new System.Windows.Interop.WindowInteropHelper(window).Handle;
            } catch { /* 无宿主 */ }

            var pidl = SHBrowseForFolder(ref info);
            if (pidl == IntPtr.Zero) return null;
            try {
                var buffer = new StringBuilder(260);
                if (!SHGetPathFromIDList(pidl, buffer)) return null;
                var path = buffer.ToString();
                return string.IsNullOrEmpty(path) ? null : PathUtils.AddSlashSuffix(path);
            } finally {
                CoTaskMemFree(pidl);
            }
        } catch (Exception ex) {
            LastError = ex.Message;
            Logger.Warn(ex, "SHBrowseForFolder 兜底也失败");
            return null;
        } finally {
            Marshal.FreeHGlobal(displayName);
        }
    }

}
