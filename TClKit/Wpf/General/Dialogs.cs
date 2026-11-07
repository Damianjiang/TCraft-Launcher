using Microsoft.Win32;

namespace TClKit.Wpf;

public static class Dialogs {

    /// <summary>
    /// 弹出 “保存” 弹窗，返回用户指定的路径，若取消则返回 <c>null</c>。
    /// <para/> <paramref name="filter"/> 中的扩展名不加 <c>.</c>。
    /// </summary>
    public static string? SaveFile(string title, string defaultFileName = "", string? defaultDirectory = null, IEnumerable<(string Extension, string Display)>? filter = null) {
        var filters = filter?.ToList();
        var dialog = new SaveFileDialog {
            AddExtension = true,
            Title = title,
            FileName = defaultFileName
        };
        if (!string.IsNullOrEmpty(defaultDirectory) && DirectoryUtils.Exists(defaultDirectory!)) dialog.InitialDirectory = PathUtils.ToShortPath(defaultDirectory!);
        if (filters is not null) {
            dialog.Filter = filters.Select(f => $"{f.Display}(*.{f.Extension})|*.{f.Extension}").Join("|");
        }

        Logger.Info($"保存弹窗：{dialog.Title}（FileName={dialog.FileName}, InitialDirectory={dialog.InitialDirectory}, Filter={dialog.Filter}）");
        if (dialog.ShowDialog() != true) return null;

        var result = dialog.FileName;
        Logger.Info($"保存弹窗返回：{result}");
        if (!result.Contains(Path.DirectorySeparatorChar)) return null;
        // AddExtension 可能失效，需要手动补全
        if (filters != null && !string.IsNullOrEmpty(result) && !filters.Any(f => result.EndsWithF("." + f.Extension, true))) {
            result += "." + filters[dialog.FilterIndex - 1].Extension;
            Logger.Warn($"选择文件的返回无扩展名，将会手动添加，修改后为：{result}");
        }
        return result;
    }

    /// <summary>
    /// 弹出 “选择文件” 弹窗，返回用户选择的文件路径，若取消则返回空数组。
    /// <para/> 若使用扩展名 <paramref name="filter"/>，则扩展名不加 <c>.</c>。
    /// </summary>
    public static List<string> SelectFile(string title, bool multiselect, string? defaultDirectory = null, OneOf<IEnumerable<(string[] Extensions, string Display)>, string>? filter = null) {
        var dialog = new OpenFileDialog {
            AddExtension = true,
            CheckFileExists = true,
            Multiselect = multiselect,
            Title = title,
            ValidateNames = true
        };
        if (!string.IsNullOrEmpty(defaultDirectory) && DirectoryUtils.Exists(defaultDirectory!)) dialog.InitialDirectory = PathUtils.ToShortPath(defaultDirectory!);
        if (filter is not null) {
            if (filter.Value.Is<string>()) {
                dialog.Filter = filter.Value.As<string>();
            } else {
                dialog.Filter = filter.Value.As<IEnumerable<(string[] Extensions, string Display)>>().Select(f => {
                    var exts = string.Join(";", f.Extensions.Select(e => $"*.{e}"));
                    return $"{f.Display}({exts})|{exts}";
                }).Join("|");
            }
        }

        Logger.Info($"选择文件弹窗：{dialog.Title}（Multiselect={dialog.Multiselect}, InitialDirectory={dialog.InitialDirectory}, Filter={dialog.Filter}）");
        if (dialog.ShowDialog() != true) return [];

        var results = dialog.FileNames.Where(f => f.Contains(Path.DirectorySeparatorChar)).ToList();
        Logger.Info($"选择文件弹窗返回：{results.Join(",")}");
        return results;
    }

    /// <summary>
    /// 上一次 <see cref="SelectFolder"/> 的失败原因；成功或用户取消时为空字符串。
    /// <para/>以前对话框创建失败只写日志并返回空数组，调用方无法区分「用户取消」，
    /// 表现为按钮点了完全没反应。调用方可据此给出可见提示。
    /// </summary>
    public static string LastFolderError => VistaFolderPicker.ErrorMessage ?? "";

    /// <summary>
    /// 弹出 “选择文件夹” 弹窗，返回用户选择的文件夹路径（以分隔符结尾），若取消则返回空数组。
    /// <para/>改为自写的 IFileDialog COM 封装（VistaFolderPicker）替代 Ookii.Dialogs.Wpf，
    /// 减少内嵌体积约 42KB；项目内全部调用均为 multiselect:=False，多选签名保留但不展开。
    /// </summary>
    public static List<string> SelectFolder(string title, bool multiselect, string? defaultDirectory = null) {
        Logger.Info($"选择文件夹弹窗：{title}（Multiselect={multiselect}, SelectedPath={defaultDirectory}）");
        string? result;
        try {
            result = VistaFolderPicker.PickFolder(title, defaultDirectory);
        } catch (Exception ex) {
            Logger.Warn(ex, "选择文件夹弹窗失败");
            return [];
        }
        Logger.Info($"选择文件夹弹窗返回：{result ?? "(取消)"}");
        if (result is null && !string.IsNullOrEmpty(LastFolderError)) {
            Logger.Warn($"选择文件夹弹窗失败原因：{LastFolderError}");
        }
        return result is null ? [] : [result];
    }

}
