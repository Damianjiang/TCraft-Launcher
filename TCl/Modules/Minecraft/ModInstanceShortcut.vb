''' <summary>
''' 实例桌面快捷方式：为某个实例生成一个「直接启动它」的 .lnk 文件。
''' 快捷方式指向启动器 exe 本身，并附带命令行参数；启动器启动时识别该参数即可跳过界面直接启动对应实例。
''' 注意：只负责生成 .lnk，不解析参数（命令行解析由主控在启动流程里实现）。
''' </summary>
Public Module ModInstanceShortcut

    ''' <summary>快捷方式里携带的启动参数名，格式：--launch-instance "实例名"。</summary>
    Public Const ArgumentName As String = "--launch-instance"

    ''' <summary>
    ''' 在桌面创建「直接启动某实例」的快捷方式。
    ''' </summary>
    ''' <param name="Instance">目标实例。</param>
    ''' <param name="ErrorText">失败原因（成功时为空字符串）。</param>
    ''' <returns>成功返回 True。</returns>
    Public Function Create(Instance As McInstance, ByRef ErrorText As String) As Boolean
        ErrorText = ""
        If Instance Is Nothing Then
            ErrorText = ModLang.L("实例不存在，无法创建快捷方式。")
            Return False
        End If
        If String.IsNullOrWhiteSpace(Instance.Name) Then
            ErrorText = ModLang.L("实例名称为空，无法创建快捷方式。")
            Return False
        End If

        '启动器 exe 路径
        Dim ExePath As String = GetExePath()
        If String.IsNullOrEmpty(ExePath) Then
            ErrorText = ModLang.L("无法获取启动器自身的路径。")
            Return False
        End If
        Dim ExeDirPath As String = ""
        Try
            ExeDirPath = IO.Path.GetDirectoryName(ExePath)
        Catch ex As Exception
            Logger.Warn(ex, "获取启动器所在目录失败")
        End Try
        If String.IsNullOrEmpty(ExeDirPath) Then ExeDirPath = ExePath

        '桌面目录
        Dim DesktopPath As String = ""
        Try
            DesktopPath = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory)
            If String.IsNullOrEmpty(DesktopPath) Then DesktopPath = Environment.GetFolderPath(Environment.SpecialFolder.Desktop)
        Catch ex As Exception
            Logger.Warn(ex, "获取桌面路径失败")
        End Try
        If String.IsNullOrEmpty(DesktopPath) OrElse Not DirectoryUtils.Exists(DesktopPath) Then
            ErrorText = ModLang.L("找不到桌面目录，请检查系统权限。")
            Return False
        End If

        '文件名：启动 实例名.lnk，重名时追加 (2)、(3)…
        Dim BaseName As String = ModLang.L("启动 ") & SafeFileName(Instance.Name)
        Dim LinkPath As String = GetUniqueLinkPath(DesktopPath, BaseName)

        'WScript.Shell 创建 .lnk
        Dim Shell As Object = Nothing
        Try
            Shell = Microsoft.VisualBasic.Interaction.CreateObject("WScript.Shell")
        Catch ex As Exception
            Logger.Warn(ex, "创建 WScript.Shell 失败")
            Shell = Nothing
        End Try
        If Shell Is Nothing Then
            ErrorText = ModLang.L("系统不支持创建快捷方式（WScript.Shell 不可用）。")
            Return False
        End If

        Try
            Dim Shortcut As Object = Shell.CreateShortcut(LinkPath)
            If Shortcut Is Nothing Then
                ErrorText = ModLang.L("创建快捷方式对象失败。")
                Return False
            End If
            Shortcut.TargetPath = ExePath
            Shortcut.Arguments = ArgumentName & " """ & Instance.Name & """"
            Shortcut.WorkingDirectory = ExeDirPath
            Shortcut.IconLocation = ExePath & ",0"
            Shortcut.Description = ModLang.L("启动 ") & Instance.Name & " - TCraft Launcher"
            Shortcut.WindowStyle = 1
            Shortcut.Save()
            Logger.Info($"已创建实例快捷方式：{LinkPath}")
            Return True
        Catch ex As Exception
            Logger.Warn(ex, $"创建快捷方式失败：{LinkPath}")
            ErrorText = ModLang.L("写入快捷方式失败：") & ex.Message
            Return False
        Finally
            Try
                System.Runtime.InteropServices.Marshal.ReleaseComObject(Shell)
            Catch ex As Exception
                Logger.Warn(ex, "释放 WScript.Shell 失败")
            End Try
        End Try
    End Function

    ''' <summary>当前进程的 exe 完整路径；取不到时退回入口程序集位置。</summary>
    Private Function GetExePath() As String
        Try
            Dim ModuleInfo As ProcessModule = Process.GetCurrentProcess().MainModule
            If ModuleInfo IsNot Nothing AndAlso Not String.IsNullOrEmpty(ModuleInfo.FileName) Then Return ModuleInfo.FileName
        Catch ex As Exception
            Logger.Warn(ex, "获取进程主模块路径失败")
        End Try
        Try
            Dim EntryAssembly = Reflection.Assembly.GetEntryAssembly()
            If EntryAssembly IsNot Nothing AndAlso Not String.IsNullOrEmpty(EntryAssembly.Location) Then Return EntryAssembly.Location
        Catch ex As Exception
            Logger.Warn(ex, "获取入口程序集路径失败")
        End Try
        Return ""
    End Function

    ''' <summary>把实例名里不能出现在文件名中的字符替换成下划线。</summary>
    Private Function SafeFileName(SourceName As String) As String
        Dim Result As String = If(SourceName, "").Trim()
        Try
            For Each Bad As Char In IO.Path.GetInvalidFileNameChars()
                Result = Result.Replace(Bad, "_"c)
            Next
        Catch ex As Exception
            Logger.Warn(ex, "清理快捷方式文件名失败")
        End Try
        If Result.Length = 0 Then Result = "实例"
        Return Result
    End Function

    ''' <summary>桌面上同名 .lnk 已存在时，依次尝试 (2)、(3)…</summary>
    Private Function GetUniqueLinkPath(DesktopPath As String, BaseName As String) As String
        Dim Candidate As String = IO.Path.Combine(DesktopPath, BaseName & ".lnk")
        Dim Index As Integer = 2
        While FileExistsSafe(Candidate) AndAlso Index <= 999
            Candidate = IO.Path.Combine(DesktopPath, $"{BaseName} ({Index}).lnk")
            Index += 1
        End While
        Return Candidate
    End Function

    Private Function FileExistsSafe(FilePath As String) As Boolean
        Try
            Return IO.File.Exists(FilePath)
        Catch ex As Exception
            Logger.Warn(ex, $"检查文件是否存在失败：{FilePath}")
            Return False
        End Try
    End Function

End Module
