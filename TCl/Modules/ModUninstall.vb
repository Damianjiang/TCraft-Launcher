Imports System.Reflection
Imports System.Runtime.InteropServices

''' <summary>
''' 卸载清理模块。
''' <para/>负责三件事：
''' <para/>1. 生成清理清单（<see cref="UninstallPlan"/>），每一项都要通过白名单校验；
''' <para/>2. 把内嵌的清理脚本写到临时目录，并把清单作为命令行参数传入；
''' <para/>3. 以分离进程启动脚本，使启动器可以在脚本开始清理前安全退出。
''' <para/>真正的删除动作全部发生在 <c>Resources\UninstallScript.ps1</c> 中，本模块不直接删任何文件。
''' </summary>
Public Module ModUninstall

#Region "模型"

    ''' <summary>
    ''' 清理目标的类型。脚本里对应 DIR / DIR_IF_EMPTY / FILE / KEY / VAL 五种指令。
    ''' </summary>
    Public Enum UninstallTargetKind
        ''' <summary>删除单个文件。</summary>
        File = 0
        ''' <summary>递归删除整个目录（目录名必须是 TCL）。</summary>
        Directory = 1
        ''' <summary>仅当目录为空时删除，用于安装目录本身。</summary>
        DirectoryIfEmpty = 2
        ''' <summary>删除整个注册表项。</summary>
        RegistryKey = 3
        ''' <summary>删除注册表项下的单个值，不删除整个项。</summary>
        RegistryValue = 4
    End Enum

    ''' <summary>
    ''' 一个清理目标。
    ''' </summary>
    Public Class UninstallTarget
        Public Property Kind As UninstallTargetKind = UninstallTargetKind.File
        ''' <summary>文件或目录的完整路径；注册表目标时为完整键路径。</summary>
        Public Property Path As String = ""
        ''' <summary>仅 <see cref="UninstallTargetKind.RegistryValue"/> 使用。</summary>
        Public Property ValueName As String = ""
        ''' <summary>给用户看的说明。</summary>
        Public Property Description As String = ""

        ''' <summary>传给清理脚本的编码形式。</summary>
        Public ReadOnly Property Token As String
            Get
                Select Case Kind
                    Case UninstallTargetKind.File : Return "FILE|" & Path
                    Case UninstallTargetKind.Directory : Return "DIR|" & Path
                    Case UninstallTargetKind.DirectoryIfEmpty : Return "DIR_IF_EMPTY|" & Path
                    Case UninstallTargetKind.RegistryKey : Return "KEY|" & Path
                    Case Else : Return "VAL|" & Path & "|" & ValueName
                End Select
            End Get
        End Property

        ''' <summary>清单里显示的路径文本。</summary>
        Public ReadOnly Property DisplayPath As String
            Get
                If Kind = UninstallTargetKind.RegistryValue Then Return Path & "  →  " & ValueName
                Return Path
            End Get
        End Property

        Public ReadOnly Property IsRegistry As Boolean
            Get
                Return Kind = UninstallTargetKind.RegistryKey OrElse Kind = UninstallTargetKind.RegistryValue
            End Get
        End Property
    End Class

    ''' <summary>
    ''' 一次卸载的完整计划。
    ''' </summary>
    Public Class UninstallPlan
        ''' <summary>通过白名单校验、确实要删除的条目。</summary>
        Public Property Targets As New List(Of UninstallTarget)
        ''' <summary>找到但不允许删除的候选，附原因，用于向用户说明。</summary>
        Public Property Skipped As New List(Of String)
        ''' <summary>启动器安装目录（不含末尾反斜杠）。</summary>
        Public Property InstallDir As String = ""
        ''' <summary>临时脚本所在目录。</summary>
        Public Property ScriptFolder As String = ""
        ''' <summary>临时清理脚本路径。</summary>
        Public Property ScriptPath As String = ""
        ''' <summary>清理日志路径（有失败项时保留）。</summary>
        Public Property LogPath As String = ""

        Public ReadOnly Property CanUninstall As Boolean
            Get
                Return Targets.Count > 0
            End Get
        End Property

        Public ReadOnly Property FileTargets As List(Of UninstallTarget)
            Get
                Return Targets.Where(Function(T) T.Kind = UninstallTargetKind.File).ToList()
            End Get
        End Property

        Public ReadOnly Property DirectoryTargets As List(Of UninstallTarget)
            Get
                Return Targets.Where(Function(T) T.Kind = UninstallTargetKind.Directory OrElse T.Kind = UninstallTargetKind.DirectoryIfEmpty).ToList()
            End Get
        End Property

        Public ReadOnly Property RegistryTargets As List(Of UninstallTarget)
            Get
                Return Targets.Where(Function(T) T.IsRegistry).ToList()
            End Get
        End Property
    End Class

#End Region

#Region "白名单"

    ''' <summary>
    ''' 允许整体删除的注册表项。只有列在这里的键才可能被递归删除。
    ''' </summary>
    Private ReadOnly RegistryKeyWhitelist As String() = {
        "HKEY_CURRENT_USER\Software\" & RegFolder,
        "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Uninstall\TCL",
        "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Uninstall\TCraft Launcher"
    }

    ''' <summary>
    ''' 允许删除单个值的注册表项。这里只删值，绝不删整个键。
    ''' </summary>
    Private Const RegistryValueKeyWhitelist As String = "HKEY_CURRENT_USER\Software\Microsoft\DirectX\UserGpuPreferences"

    ''' <summary>
    ''' 受保护的位置：不允许删除它们本身，也不允许删除它们的上级目录。
    ''' </summary>
    Private Function GetProtectedPaths() As List(Of String)
        Dim Folders As Environment.SpecialFolder() = {
            Environment.SpecialFolder.Windows,
            Environment.SpecialFolder.System,
            Environment.SpecialFolder.SystemX86,
            Environment.SpecialFolder.ProgramFiles,
            Environment.SpecialFolder.ProgramFilesX86,
            Environment.SpecialFolder.CommonProgramFiles,
            Environment.SpecialFolder.UserProfile,
            Environment.SpecialFolder.Desktop,
            Environment.SpecialFolder.DesktopDirectory,
            Environment.SpecialFolder.MyDocuments,
            Environment.SpecialFolder.ApplicationData,
            Environment.SpecialFolder.LocalApplicationData,
            Environment.SpecialFolder.CommonApplicationData,
            Environment.SpecialFolder.StartMenu,
            Environment.SpecialFolder.Programs,
            Environment.SpecialFolder.CommonStartMenu,
            Environment.SpecialFolder.CommonDesktopDirectory
        }
        Dim Result As New List(Of String)
        For Each Folder In Folders
            Dim P As String = NormalizePath(Environment.GetFolderPath(Folder))
            If P.Length > 0 Then Result.Add(P)
        Next
        For Each P In {Path.GetTempPath(), OsDrive, Environment.GetEnvironmentVariable("PUBLIC")}
            Dim N As String = NormalizePath(P)
            If N.Length > 0 Then Result.Add(N)
        Next
        For Each Drive In Environment.GetLogicalDrives()
            Dim N As String = NormalizePath(Drive)
            If N.Length > 0 Then Result.Add(N)
        Next
        Return Result.Distinct(StringComparer.OrdinalIgnoreCase).ToList()
    End Function

    ''' <summary>
    ''' 允许操作的位置：安装目录、桌面、开始菜单、AppData、临时目录。
    ''' </summary>
    Private Function GetAllowedRoots() As List(Of String)
        Dim Result As New List(Of String)
        For Each P In {
            Paths.Base,
            Environment.GetFolderPath(Environment.SpecialFolder.Desktop),
            Environment.GetFolderPath(Environment.SpecialFolder.StartMenu),
            Paths.AppData,
            Path.GetTempPath()
        }
            Dim N As String = NormalizePath(P)
            If N.Length > 2 Then Result.Add(N)
        Next
        Return Result.Distinct(StringComparer.OrdinalIgnoreCase).ToList()
    End Function

    ''' <summary>
    ''' 规范化路径：去掉引号与末尾反斜杠，展开为完整路径。
    ''' </summary>
    Private Function NormalizePath(Value As String) As String
        If String.IsNullOrWhiteSpace(Value) Then Return ""
        Dim P As String = Value.Trim().Trim(""""c)
        Try
            P = Path.GetFullPath(P)
        Catch ex As Exception
            Logger.Warn(ex, "规范化路径失败（按原样保留，后续校验将拒绝）")
            ' 路径非法时保留原样，后续校验会拒绝
        End Try
        Return P.TrimEnd("\"c)
    End Function

    ''' <summary>
    ''' 校验一个文件/目录目标。返回 <c>Nothing</c> 表示通过，否则返回拒绝原因。
    ''' <para/>这是启动器侧的第一道闸门，清理脚本内部还会再校验一次。
    ''' </summary>
    Public Function ValidatePath(Target As UninstallTarget) As String
        Dim P As String = Target.Path
        If String.IsNullOrWhiteSpace(P) Then Return "路径为空"
        If P.IndexOfAny({"*"c, "?"c}) >= 0 Then Return "路径包含通配符"
        If P.Contains(";") OrElse P.Contains("|") Then Return "路径包含分隔符（; 或 |），无法安全传参"
        If P.Split({"\"c, "/"c}).Contains("..") Then Return "路径包含上级目录引用"
        If Not Path.IsPathRooted(P) Then Return "路径不是绝对路径"
        If P.ToLowerInvariant().Contains(".minecraft") Then Return "涉及 .minecraft，禁止删除"

        Dim Full As String = NormalizePath(P)
        If Full.Length <= 2 Then Return "不允许删除驱动器根目录"

        For Each Guarded In GetProtectedPaths()
            If Full.Equals(Guarded, StringComparison.OrdinalIgnoreCase) Then
                Return ModLang.L("是受保护的系统或用户位置：") & Guarded
            End If
            If Guarded.StartsWith(Full & "\", StringComparison.OrdinalIgnoreCase) Then
                Return ModLang.L("是受保护位置的上级目录：") & Guarded
            End If
        Next

        If Target.Kind = UninstallTargetKind.Directory Then
            If Not Path.GetFileName(Full).Equals("TCL", StringComparison.OrdinalIgnoreCase) Then
                Return "目录名不是 TCL，拒绝递归删除"
            End If
        End If

        If Target.Kind = UninstallTargetKind.DirectoryIfEmpty Then
            ' 安装目录本身就是一个允许的根，因此单独校验，不做“必须位于根目录内”的判断
            If Not Full.Equals(NormalizePath(Paths.Base), StringComparison.OrdinalIgnoreCase) Then
                Return "不是启动器安装目录"
            End If
            Return Nothing
        End If

        Dim Inside As Boolean = False
        For Each Root In GetAllowedRoots()
            If Full.StartsWith(Root & "\", StringComparison.OrdinalIgnoreCase) Then
                Inside = True
                Exit For
            End If
        Next
        If Not Inside Then Return "不在启动器安装目录、桌面、开始菜单、AppData 或临时目录内"

        Return Nothing
    End Function

    ''' <summary>
    ''' 校验注册表目标。返回 <c>Nothing</c> 表示通过，否则返回拒绝原因。
    ''' </summary>
    Public Function ValidateRegistry(Target As UninstallTarget) As String
        Dim Key As String = Target.Path.Trim()
        If Key.Length = 0 Then Return "注册表路径为空"
        If Key.IndexOfAny({"*"c, "?"c}) >= 0 Then Return "注册表路径包含通配符"
        If Key.Contains(";") OrElse Key.Contains("|") Then Return "注册表路径包含分隔符（; 或 |），无法安全传参"
        If Not Key.StartsWith("HKEY_CURRENT_USER\Software\", StringComparison.OrdinalIgnoreCase) Then
            Return "只允许清理 HKEY_CURRENT_USER\Software 之下的键"
        End If

        If Target.Kind = UninstallTargetKind.RegistryKey Then
            If Not RegistryKeyWhitelist.Any(Function(K) K.Equals(Key, StringComparison.OrdinalIgnoreCase)) Then
                Return "注册表项不在白名单内"
            End If
        Else
            If Not Key.Equals(RegistryValueKeyWhitelist, StringComparison.OrdinalIgnoreCase) Then
                Return "注册表值不在白名单内"
            End If
            If String.IsNullOrWhiteSpace(Target.ValueName) Then Return "注册表值名为空"
            If Target.ValueName.IndexOfAny({"*"c, "?"c}) >= 0 Then Return "注册表值名包含通配符"
        End If
        Return Nothing
    End Function

#End Region

#Region "生成计划"

    ''' <summary>
    ''' 扫描本机，生成清理清单。只做查找与校验，不删除任何东西。
    ''' </summary>
    Public Function BuildPlan() As UninstallPlan
        Dim Plan As New UninstallPlan With {.InstallDir = NormalizePath(Paths.Base)}

        ' 1. 启动器自身文件（主程序、附属文件、实际加载的同目录程序集）
        For Each FilePath In GetOwnedFiles()
            AddFileTarget(Plan, FilePath, "启动器程序文件")
        Next

        ' 2. 启动器创建的快捷方式（仅当它确实指向本程序）
        For Each LnkPath In GetOwnedShortcuts()
            AddFileTarget(Plan, LnkPath, "启动器创建的快捷方式")
        Next

        ' 3. 启动器自建的数据目录
        AddDirectoryTarget(Plan, Paths.BaseThenName, "启动器数据：配置、日志、音乐、图片")
        AddDirectoryTarget(Plan, PathTemp, "下载与图片缓存")
        AddDirectoryTarget(Plan, Paths.AppDataThenName, "自定义皮肤等用户数据")

        ' 4. 安装目录本身：只有清理完、且用户没有放别的东西时才删除
        Dim InstallDirTarget As New UninstallTarget With {
            .Kind = UninstallTargetKind.DirectoryIfEmpty,
            .Path = Plan.InstallDir,
            .Description = "安装目录（清理后为空时才删除）"
        }
        Dim Reason As String = ValidatePath(InstallDirTarget)
        If Reason Is Nothing AndAlso Directory.Exists(Plan.InstallDir) Then
            Plan.Targets.Add(InstallDirTarget)
        ElseIf Reason IsNot Nothing Then
            Plan.Skipped.Add(Plan.InstallDir & ModLang.L("（") & Reason & ModLang.L("）"))
        End If

        ' 5. 注册表：启动器设置、卸载信息、DirectX GPU 首选项中的本程序值
        AddRegistryKeyTarget(Plan, "HKEY_CURRENT_USER\Software\" & RegFolder, ModLang.L("启动器设置与自定义事件"))
        AddRegistryKeyTarget(Plan, "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Uninstall\TCL", "卸载信息")
        AddRegistryKeyTarget(Plan, "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Uninstall\TCraft Launcher", "卸载信息")
        AddRegistryValueTarget(Plan, RegistryValueKeyWhitelist, PathExe, "DirectX GPU 首选项")

        Return Plan
    End Function

    Private Sub AddFileTarget(Plan As UninstallPlan, FilePath As String, Description As String)
        If String.IsNullOrWhiteSpace(FilePath) Then Return
        If Not File.Exists(FilePath) Then Return
        Dim Full As String = NormalizePath(FilePath)
        If Plan.Targets.Any(Function(T) T.Kind = UninstallTargetKind.File AndAlso
                                T.Path.Equals(Full, StringComparison.OrdinalIgnoreCase)) Then Return
        Dim Target As New UninstallTarget With {
            .Kind = UninstallTargetKind.File,
            .Path = Full,
            .Description = Description
        }
        Dim Reason As String = ValidatePath(Target)
        If Reason Is Nothing Then
            Plan.Targets.Add(Target)
        Else
            Plan.Skipped.Add(FilePath & ModLang.L("（") & Reason & ModLang.L("）"))
        End If
    End Sub

    Private Sub AddDirectoryTarget(Plan As UninstallPlan, DirPath As String, Description As String)
        If String.IsNullOrWhiteSpace(DirPath) Then Return
        Dim Full As String = NormalizePath(DirPath)
        If Not Directory.Exists(Full) Then Return
        If Plan.Targets.Any(Function(T) T.Kind = UninstallTargetKind.Directory AndAlso
                                T.Path.Equals(Full, StringComparison.OrdinalIgnoreCase)) Then Return
        Dim Target As New UninstallTarget With {
            .Kind = UninstallTargetKind.Directory,
            .Path = Full,
            .Description = Description
        }
        Dim Reason As String = ValidatePath(Target)
        If Reason Is Nothing Then
            Plan.Targets.Add(Target)
        Else
            Plan.Skipped.Add(Full & ModLang.L("（") & Reason & ModLang.L("）"))
        End If
    End Sub

    Private Sub AddRegistryKeyTarget(Plan As UninstallPlan, Key As String, Description As String)
        Dim Target As New UninstallTarget With {
            .Kind = UninstallTargetKind.RegistryKey,
            .Path = Key,
            .Description = Description
        }
        If Not RegistryKeyExists(Key) Then Return
        Dim Reason As String = ValidateRegistry(Target)
        If Reason Is Nothing Then
            Plan.Targets.Add(Target)
        Else
            Plan.Skipped.Add(Key & ModLang.L("（") & Reason & ModLang.L("）"))
        End If
    End Sub

    Private Sub AddRegistryValueTarget(Plan As UninstallPlan, Key As String, ValueName As String, Description As String)
        Dim Target As New UninstallTarget With {
            .Kind = UninstallTargetKind.RegistryValue,
            .Path = Key,
            .ValueName = ValueName,
            .Description = Description
        }
        If Not RegistryValueExists(Key, ValueName) Then Return
        Dim Reason As String = ValidateRegistry(Target)
        If Reason Is Nothing Then
            Plan.Targets.Add(Target)
        Else
            Plan.Skipped.Add(Key & " → " & ValueName & ModLang.L("（") & Reason & ModLang.L("）"))
        End If
    End Sub

    ''' <summary>
    ''' 找出属于启动器的文件：主程序、同名附属文件，以及当前进程真正加载的同目录程序集。
    ''' <para/>不按通配符扫描，因此不会误伤同一目录下用户自己的文件。
    ''' </summary>
    Private Function GetOwnedFiles() As List(Of String)
        Dim Result As New List(Of String)
        Dim Base As String = NormalizePath(Paths.Base)
        Dim ExeName As String = Path.GetFileName(PathExe)

        AddIfExists(Result, PathExe)
        AddIfExists(Result, PathExe & ".config")
        AddIfExists(Result, Path.Combine(Base, Path.GetFileNameWithoutExtension(ExeName) & ".xml"))
        AddIfExists(Result, Path.Combine(Base, "libwebp.dll"))

        For Each Asm In AppDomain.CurrentDomain.GetAssemblies()
            Try
                Dim Loc As String = Asm.Location
                If String.IsNullOrEmpty(Loc) Then Continue For
                Dim Dir As String = Path.GetDirectoryName(Loc)
                If Dir Is Nothing Then Continue For
                If Not NormalizePath(Dir).Equals(Base, StringComparison.OrdinalIgnoreCase) Then Continue For
                AddIfExists(Result, Loc)
                AddIfExists(Result, Path.ChangeExtension(Loc, ".pdb"))
                AddIfExists(Result, Loc & ".config")
            Catch ex As Exception
                Logger.Warn(ex, "获取已加载程序集位置失败（跳过该程序集）")
                ' 动态程序集没有 Location，跳过
            End Try
        Next

        Return Result.Distinct(StringComparer.OrdinalIgnoreCase).ToList()
    End Function

    Private Sub AddIfExists(List As List(Of String), PathValue As String)
        If String.IsNullOrWhiteSpace(PathValue) Then Return
        If File.Exists(PathValue) Then List.Add(PathValue)
    End Sub

    ''' <summary>
    ''' 找出启动器创建过的快捷方式。只有目标指向本程序的才会返回，避免删掉用户自己的同名快捷方式。
    ''' </summary>
    Private Function GetOwnedShortcuts() As List(Of String)
        Dim Result As New List(Of String)
        Dim Desktop As String = Environment.GetFolderPath(Environment.SpecialFolder.Desktop)
        Dim Programs As String = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.StartMenu), "Programs")
        Dim Candidates As String() = {
            Path.Combine(Desktop, "TCL.lnk"),
            Path.Combine(Desktop, "TCraft Launcher.lnk"),
            Path.Combine(Programs, "TCL.lnk"),
            Path.Combine(Programs, "TCraft Launcher.lnk")
        }
        For Each Lnk In Candidates
            If File.Exists(Lnk) AndAlso ShortcutPointsToExe(Lnk) Then Result.Add(Lnk)
        Next
        Return Result
    End Function

    Private Function ShortcutPointsToExe(LnkPath As String) As Boolean
        Try
            Dim Shell As Object = CreateObject("WScript.Shell")
            Dim Shortcut As Object = Shell.CreateShortcut(LnkPath)
            Dim Target As String = CStr(Shortcut.TargetPath)
            If String.IsNullOrWhiteSpace(Target) Then Return False
            Return NormalizePath(Target).Equals(NormalizePath(PathExe), StringComparison.OrdinalIgnoreCase)
        Catch
            ' 读不出目标就不删，宁可留下也不误删
            Return False
        End Try
    End Function

    Private Function SubKeyOf(FullPath As String) As String
        Dim Index As Integer = FullPath.IndexOf("\"c)
        If Index < 0 Then Return FullPath
        Return FullPath.Substring(Index + 1)
    End Function

    Private Function RegistryKeyExists(FullPath As String) As Boolean
        Try
            Dim Base As Microsoft.Win32.RegistryKey = Microsoft.Win32.Registry.CurrentUser
            Using Key = Base.OpenSubKey(SubKeyOf(FullPath))
                Return Key IsNot Nothing
            End Using
        Catch
            Return False
        End Try
    End Function

    Private Function RegistryValueExists(FullPath As String, ValueName As String) As Boolean
        Try
            Dim Base As Microsoft.Win32.RegistryKey = Microsoft.Win32.Registry.CurrentUser
            Using Key = Base.OpenSubKey(SubKeyOf(FullPath))
                If Key Is Nothing Then Return False
                Return Key.GetValueNames().Any(Function(N) N.Equals(ValueName, StringComparison.OrdinalIgnoreCase))
            End Using
        Catch
            Return False
        End Try
    End Function

#End Region

#Region "确认文案"

    ''' <summary>
    ''' 生成给用户确认的清理清单文本：先列出将删除的路径与注册表项，再说明不会删除什么。
    ''' </summary>
    Public Function Describe(Plan As UninstallPlan) As String
        Const MaxItems As Integer = 40
        Dim Sb As New StringBuilder()
        Sb.AppendLine(ModLang.L("即将永久删除以下内容（共 ") & Plan.Targets.Count & ModLang.L(" 项）："))
        Sb.AppendLine()

        AppendSection(Sb, "目录", Plan.DirectoryTargets, MaxItems)
        AppendSection(Sb, "文件", Plan.FileTargets, MaxItems)
        AppendSection(Sb, "注册表", Plan.RegistryTargets, MaxItems)

        If Plan.Skipped.Count > 0 Then
            Sb.AppendLine("【已跳过，不会删除】")
            For Each Item In Plan.Skipped.Take(10)
                Sb.AppendLine("  · " & Item)
            Next
            If Plan.Skipped.Count > 10 Then Sb.AppendLine(ModLang.L("  · 其余 ") & (Plan.Skipped.Count - 10) & ModLang.L(" 项已跳过"))
            Sb.AppendLine()
        End If

        Sb.AppendLine("不会删除：.minecraft、存档、游戏版本、Java 等任何 Minecraft 数据。")
        Sb.AppendLine("确认后启动器会立即退出，约 3 秒后开始清理，清理脚本与日志随后自毁。")
        Sb.AppendLine("若有个别文件因权限不足删除失败，会记录在：")
        Sb.Append("  " & Path.Combine(Path.GetTempPath(), "TCL_Uninstall") & "\")
        Return Sb.ToString()
    End Function

    Private Sub AppendSection(Sb As StringBuilder, Title As String, Items As List(Of UninstallTarget), MaxItems As Integer)
        If Items.Count = 0 Then Return
        Sb.AppendLine(ModLang.L("【") & Title & ModLang.L("】"))
        For Each Item In Items.Take(MaxItems)
            Sb.AppendLine("  · " & Item.DisplayPath & "    " & Item.Description)
        Next
        If Items.Count > MaxItems Then Sb.AppendLine(ModLang.L("  · 其余 ") & (Items.Count - MaxItems) & ModLang.L(" 项…"))
        Sb.AppendLine()
    End Sub

#End Region

#Region "脚本生成与启动"

    ''' <summary>
    ''' 把内嵌的清理脚本写到临时目录。写出的文件带 UTF-8 BOM，PowerShell 5.1 才能正确识别中文。
    ''' </summary>
    Public Sub PrepareScript(Plan As UninstallPlan)
        Plan.ScriptFolder = Path.Combine(Path.GetTempPath(), "TCL_Uninstall")
        Directory.CreateDirectory(Plan.ScriptFolder)
        Dim Stamp As String = Date.Now.ToString("yyyyMMdd_HHmmss")
        Plan.ScriptPath = Path.Combine(Plan.ScriptFolder, "uninstall_" & Stamp & ".ps1")
        Plan.LogPath = Path.Combine(Plan.ScriptFolder, "uninstall_" & Stamp & ".log")

        Dim Template As String = LoadScriptTemplate()
        If String.IsNullOrWhiteSpace(Template) Then
            Throw New InvalidOperationException("找不到内嵌的卸载脚本模板（UninstallScript.ps1）")
        End If
        File.WriteAllText(Plan.ScriptPath, Template, New UTF8Encoding(True))
        Logger.Info("已生成卸载脚本：" & Plan.ScriptPath)
    End Sub

    Private Function LoadScriptTemplate() As String
        Dim Asm As Assembly = Assembly.GetExecutingAssembly()
        Dim Name As String = Asm.GetManifestResourceNames().
            FirstOrDefault(Function(N) N.EndsWith("UninstallScript.ps1.gz", StringComparison.OrdinalIgnoreCase))
        If Name Is Nothing Then Return Nothing
        Using Stream As IO.Stream = Asm.GetManifestResourceStream(Name)
            If Stream Is Nothing Then Return Nothing
            '第 111 轮：模板以 gzip 嵌入（19.7KB → 5.9KB），ModGzip.Unwrap 兼容未压缩旧数据
            Using Ms As New IO.MemoryStream()
                Stream.CopyTo(Ms)
                Using Reader As New StreamReader(New IO.MemoryStream(ModGzip.Unwrap(Ms.ToArray())), Encoding.UTF8, True)
                    Return Reader.ReadToEnd()
                End Using
            End Using
        End Using
    End Function

    ''' <summary>
    ''' 以分离进程启动清理脚本：父进程退出后脚本仍继续运行，不会阻塞界面。
    ''' <para/>若安装目录不可写（例如装在 Program Files），会以管理员权限启动并触发一次 UAC。
    ''' </summary>
    Public Function Run(Plan As UninstallPlan) As Integer
        If String.IsNullOrWhiteSpace(Plan.ScriptPath) Then
            Throw New InvalidOperationException("尚未生成卸载脚本")
        End If
        Dim Elevate As Boolean = Not CanWriteTo(Plan.InstallDir)
        Dim Args As String = BuildArguments(Plan)
        Logger.Info("启动卸载脚本：" & Plan.ScriptPath & "；需要提权：" & Elevate)
        Return StartDetached(PowerShellPath, Args, Elevate)
    End Function

    ''' <summary>
    ''' 组装命令行参数：待清理的目录/文件与注册表项都以参数形式传给脚本。
    ''' </summary>
    Private Function BuildArguments(Plan As UninstallPlan) As String
        Dim FilePart As String = String.Join(";", Plan.Targets.
            Where(Function(T) Not T.IsRegistry).
            Select(Function(T) T.Token))
        Dim RegPart As String = String.Join(";", Plan.Targets.
            Where(Function(T) T.IsRegistry).
            Select(Function(T) T.Token))

        Dim Sb As New StringBuilder()
        Sb.Append("-NoProfile -NonInteractive -ExecutionPolicy Bypass -File """).
            Append(Plan.ScriptPath).Append(""" ")
        ' 空参数不要传：PowerShell 的 -File 解析会把 "" 当成“缺少参数”而直接报错
        If FilePart.Length > 0 Then Sb.Append("-Targets """).Append(FilePart).Append(""" ")
        If RegPart.Length > 0 Then Sb.Append("-RegKeys """).Append(RegPart).Append(""" ")
        Sb.Append("-LogPath """).Append(Plan.LogPath).Append(""" ")
        Sb.Append("-InstallDir """).Append(Plan.InstallDir).Append(""" ")
        Sb.Append("-ParentPid ").Append(Process.GetCurrentProcess().Id).Append(" ")
        Sb.Append("-DelaySeconds 3")
        Return Sb.ToString()
    End Function

    Private ReadOnly Property PowerShellPath As String
        Get
            Dim Full As String = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.System),
                "WindowsPowerShell\v1.0\powershell.exe")
            If File.Exists(Full) Then Return Full
            Return "powershell.exe"
        End Get
    End Property

    ''' <summary>
    ''' 判断目录是否可写，用于决定脚本要不要提权。
    ''' </summary>
    Private Function CanWriteTo(Folder As String) As Boolean
        Try
            If Not Directory.Exists(Folder) Then Return True
            Dim Probe As String = Path.Combine(Folder, ".tcl_uninstall_probe_" & Guid.NewGuid().ToString("N"))
            Using Stream As FileStream = File.Create(Probe, 1, FileOptions.DeleteOnClose)
            End Using
            Return True
        Catch
            Return False
        End Try
    End Function

#End Region

#Region "分离进程"

    Private Const CREATE_BREAKAWAY_FROM_JOB As Integer = &H1000000
    Private Const CREATE_NEW_PROCESS_GROUP As Integer = &H200
    Private Const CREATE_NO_WINDOW As Integer = &H8000000
    Private Const CREATE_UNICODE_ENVIRONMENT As Integer = &H400
    Private Const ERROR_ACCESS_DENIED As Integer = 5

    <StructLayout(LayoutKind.Sequential, CharSet:=CharSet.Unicode)>
    Private Structure STARTUPINFO
        Public cb As Integer
        Public lpReserved As String
        Public lpDesktop As String
        Public lpTitle As String
        Public dwX As Integer
        Public dwY As Integer
        Public dwXSize As Integer
        Public dwYSize As Integer
        Public dwXCountChars As Integer
        Public dwYCountChars As Integer
        Public dwFillAttribute As Integer
        Public dwFlags As Integer
        Public wShowWindow As Short
        Public cbReserved2 As Short
        Public lpReserved2 As IntPtr
        Public hStdInput As IntPtr
        Public hStdOutput As IntPtr
        Public hStdError As IntPtr
    End Structure

    <StructLayout(LayoutKind.Sequential)>
    Private Structure PROCESS_INFORMATION
        Public hProcess As IntPtr
        Public hThread As IntPtr
        Public dwProcessId As Integer
        Public dwThreadId As Integer
    End Structure

    <DllImport("kernel32.dll", SetLastError:=True, CharSet:=CharSet.Unicode)>
    Private Function CreateProcessW(
        lpApplicationName As String,
        lpCommandLine As StringBuilder,
        lpProcessAttributes As IntPtr,
        lpThreadAttributes As IntPtr,
        bInheritHandles As Boolean,
        dwCreationFlags As Integer,
        lpEnvironment As IntPtr,
        lpCurrentDirectory As String,
        ByRef lpStartupInfo As STARTUPINFO,
        ByRef lpProcessInformation As PROCESS_INFORMATION) As Boolean
    End Function

    <DllImport("kernel32.dll", SetLastError:=True)>
    Private Function CloseHandle(hObject As IntPtr) As Boolean
    End Function

    ''' <summary>
    ''' 启动一个脱离父进程的进程。
    ''' <para/>使用 CREATE_BREAKAWAY_FROM_JOB，保证启动器退出（甚至被作业对象回收）时脚本继续运行。
    ''' <para/>刻意不隐藏控制台窗口：清理脚本只做普通的文件删除，让用户看得见过程，
    ''' 也能避免“隐藏窗口 + 自删除”这一组合被安全软件当成恶意行为拦截。
    ''' </summary>
    Private Function StartDetached(FileName As String, Arguments As String, Elevate As Boolean) As Integer
        If Elevate Then
            ' 需要管理员权限：只能交给 ShellExecute 触发 UAC，同样不会阻塞启动器
            Dim StartInfo As New ProcessStartInfo With {
                .FileName = FileName,
                .Arguments = Arguments,
                .UseShellExecute = True,
                .Verb = "runas",
                .WindowStyle = ProcessWindowStyle.Normal
            }
            Dim Started As Process = Process.Start(StartInfo)
            Return If(Started Is Nothing, 0, Started.Id)
        End If

        Dim CommandLine As New StringBuilder("""" & FileName & """ " & Arguments)
        Dim Info As New STARTUPINFO()
        Info.cb = Marshal.SizeOf(GetType(STARTUPINFO))
        Dim ProcInfo As New PROCESS_INFORMATION()
        Dim Flags As Integer = CREATE_NEW_PROCESS_GROUP Or CREATE_UNICODE_ENVIRONMENT Or CREATE_BREAKAWAY_FROM_JOB

        Dim Ok As Boolean = CreateProcessW(Nothing, CommandLine, IntPtr.Zero, IntPtr.Zero, False, Flags, IntPtr.Zero, Nothing, Info, ProcInfo)
        If Not Ok AndAlso Marshal.GetLastWin32Error() = ERROR_ACCESS_DENIED Then
            ' 当前作业对象不允许脱离（例如被某些启动器/沙箱包裹），退化为普通子进程再试一次
            Flags = Flags And Not CREATE_BREAKAWAY_FROM_JOB
            CommandLine = New StringBuilder("""" & FileName & """ " & Arguments)
            Ok = CreateProcessW(Nothing, CommandLine, IntPtr.Zero, IntPtr.Zero, False, Flags, IntPtr.Zero, Nothing, Info, ProcInfo)
        End If

        If Not Ok Then
            Throw New ComponentModel.Win32Exception(Marshal.GetLastWin32Error(), "无法启动卸载脚本")
        End If
        Try
            Return ProcInfo.dwProcessId
        Finally
            CloseHandle(ProcInfo.hThread)
            CloseHandle(ProcInfo.hProcess)
        End Try
    End Function

#End Region

End Module
