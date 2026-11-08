Public Class PageSetupLog

    ''' <summary>
    ''' 日志文件夹路径。日志文件为 TCL 文件夹下的 Log1.txt ~ Log5.txt。
    ''' </summary>
    Private Shared ReadOnly LogFolder As String = Paths.BaseThenName

    ''' <summary>
    ''' 当前正在写入的日志文件（Log1.txt，该文件不可删除）。
    ''' </summary>
    Private Shared ReadOnly CurrentLogPath As String = PathUtils.ForApi(Path.Combine(LogFolder, "Log1.txt"))

    ''' <summary>
    ''' 获取全部日志文件（按最后修改时间倒序）。
    ''' </summary>
    Private Shared Function LogFiles() As String()
        If Not Directory.Exists(LogFolder) Then Return Array.Empty(Of String)
        Return Directory.GetFiles(LogFolder, "Log*.txt").OrderByDescending(Function(f) File.GetLastWriteTime(f)).ToArray()
    End Function

    Private Sub PageSetupLog_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        '重复加载部分
        PanBack.ScrollToHome()
        LoadList()
        CheckLogAutoScroll.Checked = True
        CheckDebugMode.Checked = Settings.Get(Of Boolean)("SystemDebugMode")
        StartLogWatcher()
        '非重复加载部分
        Static Reloaded As Boolean = False
        If Reloaded Then Return
        Reloaded = True
    End Sub

    '调试模式开关：开了就记全量日志，平时关着省磁盘
    Private Sub CheckDebugMode_Changed(sender As Object, e As RoutedEventArgs)
        Dim IsDebug As Boolean = CheckDebugMode.Checked
        Settings.Set("SystemDebugMode", IsDebug)
        If Logger.Instance IsNot Nothing Then
            Logger.Instance.MinLevel = If(IsDebug, LogLevel.Trace, LogLevel.Info)
        End If
        Hint(If(IsDebug, "调试模式已开启，将记录详细日志", "调试模式已关闭，仅记录重要日志"), HintType.Blue)
    End Sub

    Private Sub PageSetupLog_Unloaded(sender As Object, e As RoutedEventArgs) Handles Me.Unloaded
        StopLogWatcher() '性能纪律：页面不可见即停止监听
    End Sub

#Region "实时日志查看"

    Private LogWatchTimer As System.Windows.Threading.DispatcherTimer
    ''' <summary>下一条要读的 Log1.txt 字节位置；-1 表示首次（只读末尾 64KB）。</summary>
    Private LogReadPos As Long = -1
    ''' <summary>原始日志行缓存（新行追加尾部，超上限截头部）。</summary>
    Private ReadOnly LogRawLines As New List(Of String)
    Private LogViewDirty As Boolean = False

    Private Sub StartLogWatcher()
        LogReadPos = -1
        LogRawLines.Clear()
        LogViewDirty = True
        If LogWatchTimer Is Nothing Then
            LogWatchTimer = New System.Windows.Threading.DispatcherTimer With {.Interval = TimeSpan.FromMilliseconds(800)}
            AddHandler LogWatchTimer.Tick, AddressOf LogWatchTick
        End If
        LogWatchTimer.Start()
        LogWatchTick(Nothing, Nothing)
    End Sub

    Private Sub StopLogWatcher()
        If LogWatchTimer IsNot Nothing Then LogWatchTimer.Stop()
    End Sub

    ''' <summary>增量读取 Log1.txt 新增的完整行（字节级定位，避免多字节字符被截断；文件被并发写入时静默跳过本轮）。</summary>
    Private Sub LogWatchTick(sender As Object, e As EventArgs)
        Try
            If Not FileUtils.Exists(CurrentLogPath) Then Return
            Using Fs As New FileStream(CurrentLogPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite)
                If LogReadPos < 0 Then LogReadPos = Math.Max(0, Fs.Length - 65536)
                If Fs.Length < LogReadPos Then LogReadPos = 0 '日志被重建
                If Fs.Length = LogReadPos Then Return
                Fs.Position = LogReadPos
                Dim EndLen As Integer = CInt(Fs.Length - LogReadPos)
                Dim Buffer(EndLen - 1) As Byte
                Dim Got As Integer = 0
                While Got < EndLen
                    Dim Count = Fs.Read(Buffer, Got, EndLen - Got)
                    If Count <= 0 Then Exit While
                    Got += Count
                End While
                If Got = 0 Then Return
                If Got < EndLen Then EndLen = Got '短读兜底
                '只消费到最后一个换行，半行留给下一轮
                Dim LastLf As Integer = -1
                For i = EndLen - 1 To 0 Step -1
                    If Buffer(i) = 10 Then
                        LastLf = i
                        Exit For
                    End If
                Next
                If LastLf < 0 Then Return
                Dim NewText = Encoding.UTF8.GetString(Buffer, 0, LastLf + 1)
                LogReadPos += LastLf + 1
                '首批可能从 64KB 截断处开始：只对第一行做半行粗滤（不以时间戳开头即丢弃），
                '其余行完整保留，避免误伤异常堆栈的续行
                Dim FirstLine As Boolean = LogRawLines.Count = 0
                For Each Line In NewText.Replace(vbCr, "").Split(CChar(vbLf))
                    If Line = "" Then Continue For
                    If FirstLine Then
                        FirstLine = False
                        If Line.Length < 8 OrElse Not Char.IsDigit(Line(0)) OrElse Line(2) <> ":"c Then Continue For
                    End If
                    LogRawLines.Add(Line)
                Next
                If LogRawLines.Count > 1000 Then LogRawLines.RemoveRange(0, LogRawLines.Count - 1000)
                LogViewDirty = True
            End Using
            RefreshLogView()
        Catch ex As Exception
            '日志并发写/轮转时读取失败属常见情况，静默跳过
        End Try
    End Sub

    ''' <summary>按当前过滤条件重建视图。仅在新增行或过滤条件变化时执行。</summary>
    Private Sub RefreshLogView()
        If Not LogViewDirty Then Return
        '页面控件尚未初始化（定时器先于 Loaded 触发）时直接跳过，下一轮 Tick 会再来
        If TxtLogFilter Is Nothing OrElse TxtLogView Is Nothing OrElse LabLogStatus Is Nothing OrElse
           ComboLogLevel Is Nothing OrElse CheckLogAutoScroll Is Nothing Then Return
        LogViewDirty = False
        Try
            Dim Query = If(TxtLogFilter.Text, "").Trim().ToLowerInvariant()
            Dim Mode = ComboLogLevel.SelectedIndex
            Dim Builder As New StringBuilder
            Dim Shown As Integer = 0
            For Each Line In LogRawLines
                If Mode = 1 AndAlso Not (Line.Contains(" W [") OrElse Line.Contains(" E [")) Then Continue For
                If Mode = 2 AndAlso Not Line.Contains(" E [") Then Continue For
                If Query <> "" AndAlso Not Line.ToLowerInvariant().Contains(Query) Then Continue For
                Builder.Append(Line).Append(vbCrLf)
                Shown += 1
            Next
            TxtLogView.Text = Builder.ToString
            LabLogStatus.Text = ModLang.L("已加载 ") & LogRawLines.Count & ModLang.L(" 行，显示 ") & Shown & ModLang.L(" 行 · 实时监听中")
            If CBool(CheckLogAutoScroll.Checked) Then TxtLogView.ScrollToEnd()
        Catch ex As Exception
            Logger.Warn(ex, "刷新实时日志视图失败")
        End Try
    End Sub

    Private Sub ComboLogLevel_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles ComboLogLevel.SelectionChanged
        LogViewDirty = True
        RefreshLogView()
    End Sub

    Private Sub TxtLogFilter_TextChanged(sender As Object, e As System.Windows.Controls.TextChangedEventArgs) Handles TxtLogFilter.TextChanged
        LogViewDirty = True
        RefreshLogView()
    End Sub

#End Region

    ''' <summary>
    ''' 重新加载日志列表。
    ''' </summary>
    Public Sub LoadList()
        PanList.Children.Clear()
        For Each Item In LogFiles()
            Dim FullPath As String = Path.GetFullPath(Item)
            Dim Title As String = Path.GetFileName(Item)

            'Log1.txt ~ Log5.txt，解析数字部分作为序号
            Dim Index As String = Title.AfterFirst("Log").BeforeFirst(".txt")
            If IsNumeric(Index) Then Title = "日志 " & Index
            If FullPath.Equals(CurrentLogPath) Then
                Title &= "（当前）"
            Else
                '非当前的日志附加修改日期，便于分辨历史日志
                Title &= "（" & File.GetLastWriteTime(Item).ToString("yyyy-MM-dd HH:mm") & "）"
            End If

            Dim NewItem As New MyListItem With {
                .Type = MyListItem.CheckType.Clickable,
                .Title = Title,
                .Info = FullPath,
                .Tag = FullPath
            }
            AddHandler NewItem.Click, Sub(sender, e)
                OpenExplorer(CType(sender, MyListItem).Tag.ToString)
            End Sub
            PanList.Children.Add(NewItem)
        Next
        If PanList.Children.Count = 0 Then
            PanList.Children.Add(New TextBlock With {
                .Text = ModLang.L("暂无日志文件"), .Margin = New Thickness(0, 5, 0, 5),
                .Foreground = TryCast(New DynamicResourceExtension("ColorBrushGray4").ProvideValue(Nothing), Brush),
                .FontSize = 12, .HorizontalAlignment = HorizontalAlignment.Left
            })
        End If
    End Sub

    ''' <summary>
    ''' 将指定的日志文件打包导出为 zip。
    ''' </summary>
    Private Sub ExportLog(SourceFiles As IEnumerable(Of String))
        Dim BaseName As String = "TCL_Logs_" & Date.Now.ToString("yyyyMMddHHmmss")
        Dim FileName As String = BaseName & ".zip"
        Dim DesktopPath As String = Environment.GetFolderPath(Environment.SpecialFolder.Desktop)

        Dim SelectedPath As String = Dialogs.SaveFile(ModLang.L("选择日志导出位置"), FileName, filter:={("zip", ModLang.L("日志压缩文件"))}, defaultDirectory:=DesktopPath)
        If String.IsNullOrEmpty(SelectedPath) Then Return

        Try
            Dim Sources As New Dictionary(Of String, String)
            For Each Item In SourceFiles
                If File.Exists(Item) Then Sources(Path.GetFileName(Item)) = Item
            Next
            If Sources.Count = 0 Then
                Hint(ModLang.L("没有可导出的日志文件！"), HintType.Red)
                Return
            End If
            FileUtils.CreateZipFromFiles(SelectedPath, Sources)
            Hint(ModLang.L("已导出 ") & Sources.Count & ModLang.L(" 个日志文件。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "导出日志失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub BtnOpenDir_Click(sender As Object, e As MouseButtonEventArgs)
        If Not Directory.Exists(LogFolder) Then Directory.CreateDirectory(LogFolder)
        OpenExplorer(LogFolder)
    End Sub

    Private Sub BtnClean_Click(sender As Object, e As MouseButtonEventArgs)
        If MyMsgBox(ModLang.L("确定要删除全部历史日志吗？当前正在使用的日志（Log1.txt）将被保留。"), ModLang.L("清理确认"),, ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            Dim Deleted As Integer = 0
            For Each Item In LogFiles()
                Dim FullPath As String = Path.GetFullPath(Item)
                If FullPath.Equals(CurrentLogPath, StringComparison.OrdinalIgnoreCase) Then Continue For
                Try
                    File.Delete(FullPath)
                    Deleted += 1
                Catch ex2 As Exception
                    Logger.Warn(ex2, $"删除日志文件失败：{FullPath}")
                End Try
            Next
            Hint(ModLang.L("已删除 ") & Deleted & ModLang.L(" 个历史日志文件。"), HintType.Green)
            LoadList()
        Catch ex As Exception
            Logger.Error(ex, "清理日志失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub BtnExportAll_Click(sender As Object, e As MouseButtonEventArgs)
        ExportLog(LogFiles())
    End Sub

    Private Sub BtnExport_Click(sender As Object, e As MouseButtonEventArgs)
        ExportLog(New List(Of String) From {CurrentLogPath})
    End Sub

End Class
