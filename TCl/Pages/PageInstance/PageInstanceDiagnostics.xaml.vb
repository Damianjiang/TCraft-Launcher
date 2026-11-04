Imports System.IO
Imports System.Text
Imports System.Text.RegularExpressions

''' <summary>
''' 实例诊断页：日志阅读器（复刻 LumilioCL 的「游戏页 · 诊断」）。
''' 支持游戏最新日志、崩溃报告、启动器日志三类来源，级别筛选、搜索、复制、导出与崩溃分析。
''' </summary>
Public Class PageInstanceDiagnostics

#Region "数据"

    ''' <summary>
    ''' 一条日志。属性必须是 Public，WPF 绑定靠反射读取。
    ''' </summary>
    Public Class DiagLine
        Public Property Text As String = ""
        Public Property Level As Integer = 2 '0 错误 / 1 警告 / 2 信息 / 3 调试
        Public Property ForeBrush As Brush = Brushes.Black
    End Class

    Private Const MaxLines As Integer = 4000
    Private Const MaxBytes As Long = 8L * 1024 * 1024

    Private ReadOnly SourceNames As New List(Of String)
    Private ReadOnly SourcePaths As New List(Of String)
    Private ReadOnly AllLines As New List(Of DiagLine)
    Private ReadOnly DisplayLines As New List(Of DiagLine)
    Private ReadOnly ErrorBrush As New SolidColorBrush(Color.FromRgb(&HC6, &H28, &H28))
    Private ReadOnly WarnBrush As New SolidColorBrush(Color.FromRgb(&HB2, &H6A, 0))
    Private ReadOnly InfoBrush As New SolidColorBrush(Color.FromRgb(&H3A, &H3A, &H3A))
    Private ReadOnly DebugBrush As New SolidColorBrush(Color.FromRgb(&H8C, &H8C, &H8C))
    Private Loading As Boolean = False

    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property

#End Region

#Region "加载"

    Private Sub PageInstanceDiagnostics_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        If SourceNames.Count = 0 Then BuildSources()
        If ComboSource.SelectedIndex < 0 AndAlso SourceNames.Count > 0 Then ComboSource.SelectedIndex = 0
        LoadCurrent()
    End Sub

    ''' <summary>
    ''' 侧边栏刷新按钮与页面切换会调用。
    ''' </summary>
    Public Sub Reload()
        BuildSources()
        If ComboSource.SelectedIndex < 0 AndAlso SourceNames.Count > 0 Then ComboSource.SelectedIndex = 0
        LoadCurrent()
    End Sub

    ''' <summary>
    ''' 构建日志来源列表：游戏最新日志 / 崩溃报告（最近若干份）/ 启动器日志。
    ''' </summary>
    Private Sub BuildSources()
        SourceNames.Clear()
        SourcePaths.Clear()
        ComboSource.Items.Clear()
        Try
            If Instance IsNot Nothing Then
                Dim Latest As String = Instance.PathIndie & "logs\latest.log"
                If FileUtils.Exists(Latest) Then AddSource("游戏最新日志（latest.log）", Latest)
                Dim CrashFolder As String = Instance.PathIndie & "crash-reports\"
                If DirectoryUtils.Exists(CrashFolder) Then
                    Dim Crashes = DirectoryUtils.EnumerateFiles(CrashFolder, False).
                        Select(Function(f) FileUtils.GetInfo(f)).
                        OrderByDescending(Function(f) f.LastWriteTimeUtc).Take(12)
                    For Each Crash In Crashes
                        AddSource("崩溃报告 · " & Crash.Name, Crash.FullName)
                    Next
                End If
            End If
            AddSource("启动器日志（当前）", Paths.Base & "TCL\Log1.txt")
        Catch ex As Exception
            Logger.Error(ex, "构建诊断日志来源失败")
        End Try
        LabSubtitle.Text = If(Instance Is Nothing, ModLang.L("未选择实例，仅显示启动器日志。"),
            ModLang.L("查看游戏日志、崩溃报告与启动器日志，定位启动失败的原因。"))
    End Sub

    Private Sub AddSource(Name As String, Path As String)
        SourceNames.Add(Name)
        SourcePaths.Add(Path)
        ComboSource.Items.Add(Name)
    End Sub

    Private Sub ComboSource_Changed(sender As Object, e As SelectionChangedEventArgs)
        LoadCurrent()
    End Sub

    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs)
        If SourceNames.Count = 0 Then BuildSources()
        LoadCurrent()
        Hint(ModLang.L("日志已刷新。"), HintType.Green)
    End Sub

    ''' <summary>
    ''' 读取当前来源并应用筛选。
    ''' </summary>
    Private Sub LoadCurrent()
        If Loading Then Return
        Dim Index As Integer = ComboSource.SelectedIndex
        If Index < 0 OrElse Index >= SourcePaths.Count Then
            AllLines.Clear()
            DisplayLines.Clear()
            PanLog.ItemsSource = Nothing
            LabEmpty.Visibility = Visibility.Visible
            LabStatus.Text = ModLang.L("没有可用的日志来源。")
            LabPath.Text = ""
            Return
        End If
        Dim Target As String = SourcePaths(Index)
        Loading = True
        LabStatus.Text = ModLang.L("正在读取…")
        LabPath.Text = Target
        RunInNewThread(
        Sub()
            Dim Result As List(Of DiagLine) = Nothing
            Dim Failed As String = Nothing
            Try
                Result = ReadLines(Target)
            Catch ex As Exception
                Failed = ex.Message
                Logger.Warn(ex, $"读取诊断日志失败：{Target}")
            End Try
            RunInUi(
            Sub()
                Loading = False
                AllLines.Clear()
                If Result IsNot Nothing Then AllLines.AddRange(Result)
                If Failed IsNot Nothing Then
                    LabEmpty.Visibility = Visibility.Visible
                    LabEmpty.Text = ModLang.L("读取失败：") & Failed
                End If
                ApplyFilter()
            End Sub)
        End Sub, "DiagnosticsRead", ThreadPriority.BelowNormal)
    End Sub

    ''' <summary>
    ''' 读取日志文件，超大文件只取尾部，最多保留 <see cref="MaxLines"/> 行。
    ''' </summary>
    Private Function ReadLines(Path As String) As List(Of DiagLine)
        Dim Result As New List(Of DiagLine)
        If Not FileUtils.Exists(Path) Then Return Result
        Dim Length As Long = FileUtils.GetInfo(Path).Length
        Using Fs As New FileStream(Path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite)
            If Length > MaxBytes Then Fs.Seek(-MaxBytes, SeekOrigin.End)
            Using Reader As New StreamReader(Fs, Encoding.UTF8, True)
                Dim Raw As New List(Of String)
                Dim Line As String
                Do
                    Line = Reader.ReadLine()
                    If Line Is Nothing Then Exit Do
                    Raw.Add(Line)
                    If Raw.Count > MaxLines * 2 Then Raw.RemoveRange(0, Raw.Count - MaxLines)
                Loop
                Dim Start As Integer = Math.Max(0, Raw.Count - MaxLines)
                For i As Integer = Start To Raw.Count - 1
                    Result.Add(New DiagLine With {.Text = Raw(i), .Level = DetectLevel(Raw(i))})
                Next
            End Using
        End Using
        For Each Item In Result
            Select Case Item.Level
                Case 0 : Item.ForeBrush = ErrorBrush
                Case 1 : Item.ForeBrush = WarnBrush
                Case 3 : Item.ForeBrush = DebugBrush
                Case Else : Item.ForeBrush = InfoBrush
            End Select
        Next
        Return Result
    End Function

    ''' <summary>
    ''' 从行文本判定日志级别（MC 日志形如 [12:00:00] [Render thread/ERROR]: ...）。
    ''' </summary>
    Private Function DetectLevel(Line As String) As Integer
        If Line.IndexOf("/FATAL]") >= 0 OrElse Line.IndexOf("/ERROR]") >= 0 OrElse
           Line.IndexOf("[ERROR]") >= 0 OrElse Line.StartsWith("Caused by:") Then Return 0
        If Line.IndexOf("/WARN]") >= 0 OrElse Line.IndexOf("[WARN]") >= 0 Then Return 1
        If Line.IndexOf("/DEBUG]") >= 0 OrElse Line.IndexOf("/TRACE]") >= 0 Then Return 3
        Return 2
    End Function

#End Region

#Region "筛选"

    Private Sub Search_Changed(sender As Object, e As EventArgs)
        ApplyFilter()
    End Sub
    Private Sub Check_Changed(sender As Object, user As Boolean)
        ApplyFilter()
    End Sub

    Private Sub ApplyFilter()
        DisplayLines.Clear()
        Dim Keyword As String = If(TxtSearch.Text, "").Trim()
        Dim AllowError As Boolean = ChkError.Checked
        Dim AllowWarn As Boolean = ChkWarn.Checked
        Dim AllowInfo As Boolean = ChkInfo.Checked
        Dim AllowDebug As Boolean = ChkDebug.Checked
        For Each Item In AllLines
            Select Case Item.Level
                Case 0 : If Not AllowError Then Continue For
                Case 1 : If Not AllowWarn Then Continue For
                Case 2 : If Not AllowInfo Then Continue For
                Case 3 : If Not AllowDebug Then Continue For
            End Select
            If Keyword.Length > 0 AndAlso Item.Text.IndexOf(Keyword, StringComparison.OrdinalIgnoreCase) < 0 Then Continue For
            DisplayLines.Add(Item)
        Next
        PanLog.ItemsSource = Nothing
        PanLog.ItemsSource = DisplayLines
        LabEmpty.Visibility = If(DisplayLines.Count = 0, Visibility.Visible, Visibility.Collapsed)
        If DisplayLines.Count = 0 Then LabEmpty.Text = If(AllLines.Count = 0, ModLang.L("这个来源还没有内容。"), ModLang.L("没有匹配当前筛选的日志。"))
        LabStatus.Text = ModLang.L("共 ") & AllLines.Count & ModLang.L(" 行，显示 ") & DisplayLines.Count & ModLang.L(" 行")
    End Sub

#End Region

#Region "操作"

    Private Sub BtnCopy_Click(sender As Object, e As MouseButtonEventArgs)
        If DisplayLines.Count = 0 Then
            Hint(ModLang.L("没有可复制的日志。"), HintType.Red)
            Return
        End If
        Try
            Dim Sb As New StringBuilder
            For Each Item In DisplayLines
                Sb.AppendLine(Item.Text)
            Next
            Clipboard.SetText(Sb.ToString())
            Hint(ModLang.L("已复制 ") & DisplayLines.Count & ModLang.L(" 行日志。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "复制日志失败")
            Hint(ModLang.L("复制失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    Private Sub BtnExport_Click(sender As Object, e As MouseButtonEventArgs)
        If AllLines.Count = 0 Then
            Hint(ModLang.L("没有可导出的日志。"), HintType.Red)
            Return
        End If
        Dim Target As String = Dialogs.SaveFile(ModLang.L("导出日志"), "diagnostics-" & Now.ToString("yyyyMMdd-HHmmss") & ".log",
            filter:={("log", ModLang.L("日志文件")), ("txt", ModLang.L("文本文件"))})
        If String.IsNullOrEmpty(Target) Then Return
        Try
            Dim Lines As New List(Of String)
            For Each Item In AllLines
                Lines.Add(Desensitize(Item.Text))
            Next
            File.WriteAllLines(Target, Lines, New UTF8Encoding(False))
            Hint(ModLang.L("日志已导出（已脱敏）。"), HintType.Green)
            OpenExplorer(Path.GetDirectoryName(Target))
        Catch ex As Exception
            Logger.Error(ex, "导出日志失败")
            Hint(ModLang.L("导出失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>
    ''' 脱敏：替换用户目录、实例路径与 UUID。
    ''' </summary>
    Private Function Desensitize(Line As String) As String
        Try
            If Instance IsNot Nothing Then Line = Line.Replace(Instance.PathIndie, "<GAME>\")
            Line = Line.Replace(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "<HOME>")
            Line = Regex.Replace(Line, "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<UUID>")
        Catch ex As Exception
            Logger.Warn(ex, "日志脱敏失败（按原文导出）")
        End Try
        Return Line
    End Function

    Private Sub BtnBottom_Click(sender As Object, e As MouseButtonEventArgs)
        If PanLog.Items.Count = 0 Then Return
        PanLog.ScrollIntoView(PanLog.Items(PanLog.Items.Count - 1))
    End Sub

    ''' <summary>
    ''' 崩溃分析：按关键字规则给出可能原因与建议，不需要联网。
    ''' </summary>
    Private Sub BtnAnalyze_Click(sender As Object, e As MouseButtonEventArgs)
        If AllLines.Count = 0 Then
            Hint(ModLang.L("请先加载一份日志。"), HintType.Red)
            Return
        End If
        Dim Rules As New List(Of Tuple(Of String, String, String)) From {
            Tuple.Create("OutOfMemoryError", "内存不足", "把最大内存调高（设置 › 启动 › 内存），并检查是否安装了过多大型 Mod。"),
            Tuple.Create("Java heap space", "内存不足", "把最大内存调高，或减少同时加载的 Mod / 光影。"),
            Tuple.Create("UnsupportedClassVersionError", "Java 版本不匹配", "这个版本需要更高版本的 Java；到 设置 › Java 重新选择运行时。"),
            Tuple.Create("requires Java", "Java 版本不匹配", "按崩溃提示的版本要求更换 Java。"),
            Tuple.Create("Missing or unsupported mandatory dependencies", "缺少前置 Mod", "按日志列出的 modid 补齐前置 Mod，或换用兼容版本。"),
            Tuple.Create("Mod resolution failed", "Mod 依赖解析失败", "检查缺失依赖、重复 Mod 与版本冲突。"),
            Tuple.Create("Duplicate mods", "Mod 重复", "同一个 Mod 放了多个版本，删掉多余的 jar。"),
            Tuple.Create("Mixin apply failed", "Mixin 注入失败", "常见于 Mod 与游戏版本不匹配，逐一停用最近添加的 Mod。"),
            Tuple.Create("MixinApplyError", "Mixin 注入失败", "常见于 Mod 与游戏版本不匹配，逐一停用最近添加的 Mod。"),
            Tuple.Create("java.lang.ClassNotFoundException", "缺少类 / 依赖", "确认 Forge / Fabric 等加载器安装完整，必要时修复游戏文件。"),
            Tuple.Create("NoSuchMethodError", "Mod 与游戏版本不匹配", "把 Mod 换成对应游戏版本的版本。"),
            Tuple.Create("GLFW error", "图形环境异常", "更新显卡驱动；笔记本切换到独显运行。"),
            Tuple.Create("Pixel format not accelerated", "显卡驱动不支持 OpenGL", "安装厂商驱动，不要用系统自带的基础驱动。"),
            Tuple.Create("Failed to create window", "无法创建游戏窗口", "检查显卡驱动与远程桌面 / 虚拟机环境。"),
            Tuple.Create("AccessDeniedException", "文件被占用或无权限", "关闭占用文件的程序，或以管理员身份运行启动器。"),
            Tuple.Create("The game crashed whilst", "游戏崩溃", "结合下方错误行定位；也可以在「日志」里换崩溃报告查看完整堆栈。"),
            Tuple.Create("Exception in thread ""main""", "主线程异常", "查看紧随其后的 Caused by 行。")
        }
        Dim Hits As New List(Of String)
        Dim Evidence As New List(Of String)
        For Each Rule In Rules
            Dim Found As DiagLine = Nothing
            For Each Item In AllLines
                If Item.Text.IndexOf(Rule.Item1, StringComparison.OrdinalIgnoreCase) >= 0 Then
                    Found = Item
                    Exit For
                End If
            Next
            If Found IsNot Nothing Then
                Hits.Add("· " & Rule.Item2 & "：" & Rule.Item3)
                Evidence.Add("    " & Found.Text.Trim())
            End If
        Next
        If Hits.Count = 0 Then
            For Each Item In AllLines
                If Item.Level = 0 Then Evidence.Add("    " & Item.Text.Trim())
                If Evidence.Count >= 6 Then Exit For
            Next
            MyMsgBox(ModLang.L("没有匹配到已知的崩溃特征。") & vbCrLf & vbCrLf &
                     If(Evidence.Count > 0, ModLang.L("最近的错误行：") & vbCrLf & String.Join(vbCrLf, Evidence),
                        ModLang.L("这份日志里也没有错误级别的行。")),
                     ModLang.L("崩溃分析"), IsWarn:=False)
            Return
        End If
        Dim Text As New StringBuilder
        Text.AppendLine(ModLang.L("可能的原因与建议："))
        For Each Hit In Hits
            Text.AppendLine(Hit)
        Next
        Text.AppendLine()
        Text.AppendLine(ModLang.L("证据（日志原文）："))
        For Each Item In Evidence
            Text.AppendLine(Item)
        Next
        MyMsgBox(Text.ToString(), ModLang.L("崩溃分析"), IsWarn:=Hits.Count > 0)
    End Sub

#End Region

End Class
