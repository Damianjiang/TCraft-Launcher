''' <summary>
''' 设置 - 清理缓存。统计启动器的日志与各类缓存占用，让用户按需安全释放磁盘空间。
''' 只清理「可重新生成 / 可重新下载」的内容（历史日志、下载、图片、皮肤头像缓存），
''' 绝不触碰用户配置、背景与音乐、皮肤原图、翻译文件或 .minecraft 等游戏数据。
''' </summary>
Public Class PageSetupCache

    ''' <summary>一个可清理的分类。Paths 里可混放目录（递归统计/删除）与文件。</summary>
    Private Class CacheCategory
        Public Title As String
        Public Desc As String
        Public Paths As String()
        Public Enabled As Boolean = True
    End Class

    Private ReadOnly Categories As New List(Of CacheCategory)
    Private PanRows As New List(Of MyCheckBox)
    Private SizeRows As New List(Of TextBlock)

    Private Sub PageSetupCache_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        InitCategories()
        BuildRows()
        '先显示「统计中」，后台算完回填，避免卡 UI（下载缓存可能很大）
        RefreshSizes(Nothing)
        RunInNewThread(Sub()
                          Dim Sizes = Categories.Select(Function(C) DirSize(C.Paths)).ToArray()
                          RunInUi(Sub() RefreshSizes(Sizes))
                      End Sub, "Cache Size Calc")
        InitStorage()
        RefreshStorage()
    End Sub

#Region "存储总览"

    ''' <summary>一个存储分类（对应 LumilioCL 的存储占用条形图）。</summary>
    Private Class StorageRow
        Public Title As String
        Public Paths As String()
    End Class

    Private ReadOnly StorageRows As New List(Of StorageRow)
    Private ReadOnly StorageValues As New List(Of TextBlock)
    Private ReadOnly StorageFg As New List(Of ColumnDefinition)
    Private ReadOnly StorageBg As New List(Of ColumnDefinition)
    Private StorageReady As Boolean = False

    ''' <summary>初始化存储分类并生成条形图行。</summary>
    Private Sub InitStorage()
        If StorageReady Then Return
        StorageReady = True
        Dim Root As String = PathUtils.AddSlashSuffix(McFolderSelected)
        StorageRows.Clear()
        StorageRows.Add(New StorageRow With {.Title = ModLang.L("游戏版本"), .Paths = {Root & "versions\"}})
        StorageRows.Add(New StorageRow With {
            .Title = ModLang.L("实例数据（存档 / Mod / 资源包）"),
            .Paths = {Root & "saves\", Root & "mods\", Root & "resourcepacks\", Root & "shaderpacks\"}})
        StorageRows.Add(New StorageRow With {.Title = ModLang.L("共享资源（assets / libraries）"), .Paths = {Root & "assets\", Root & "libraries\"}})
        StorageRows.Add(New StorageRow With {.Title = ModLang.L("缓存"), .Paths = {PathTemp}})
        StorageRows.Add(New StorageRow With {.Title = ModLang.L("启动器数据"), .Paths = {Paths.BaseThenName}})
        PanStorage.Children.Clear()
        StorageValues.Clear()
        StorageFg.Clear()
        StorageBg.Clear()
        For Each R In StorageRows
            Dim Row As New Grid With {.Margin = New Thickness(0, 0, 0, 12)}
            Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(190)})
            Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(1, GridUnitType.Star)})
            Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(120)})
            Dim Lab As New TextBlock With {
                .Text = R.Title, .FontSize = 13, .VerticalAlignment = VerticalAlignment.Center,
                .TextTrimming = TextTrimming.CharacterEllipsis}
            Lab.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
            Grid.SetColumn(Lab, 0)
            Dim BarGrid As New Grid With {.Height = 8, .VerticalAlignment = VerticalAlignment.Center, .Margin = New Thickness(8, 0, 12, 0)}
            Dim ColFg As New ColumnDefinition With {.Width = New GridLength(0.001, GridUnitType.Star)}
            Dim ColBg As New ColumnDefinition With {.Width = New GridLength(1, GridUnitType.Star)}
            BarGrid.ColumnDefinitions.Add(ColFg)
            BarGrid.ColumnDefinitions.Add(ColBg)
            Dim BgBar As New Border With {.CornerRadius = New CornerRadius(4)}
            BgBar.SetResourceReference(Border.BackgroundProperty, "ColorBrushGray6")
            Grid.SetColumnSpan(BgBar, 2)
            Dim FgBar As New Border With {.CornerRadius = New CornerRadius(4)}
            FgBar.SetResourceReference(Border.BackgroundProperty, "ColorBrush3")
            Grid.SetColumn(FgBar, 0)
            BarGrid.Children.Add(BgBar)
            BarGrid.Children.Add(FgBar)
            Grid.SetColumn(BarGrid, 1)
            Dim Val As New TextBlock With {
                .Text = "—", .FontSize = 13, .FontWeight = FontWeights.SemiBold,
                .HorizontalAlignment = HorizontalAlignment.Right, .VerticalAlignment = VerticalAlignment.Center}
            Val.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush2")
            Grid.SetColumn(Val, 2)
            Row.Children.Add(Lab)
            Row.Children.Add(BarGrid)
            Row.Children.Add(Val)
            PanStorage.Children.Add(Row)
            StorageValues.Add(Val)
            StorageFg.Add(ColFg)
            StorageBg.Add(ColBg)
        Next
    End Sub

    ''' <summary>后台统计各类占用并回填条形图。只在页面可见时更新一次。</summary>
    Private Sub RefreshStorage()
        If Not StorageReady Then Return
        LabStorageNote.Text = ModLang.L("后台计算中…")
        RunInNewThread(Sub()
                          Dim Sizes(StorageRows.Count - 1) As Long
                          For i = 0 To StorageRows.Count - 1
                              Sizes(i) = DirSize(StorageRows(i).Paths)
                          Next
                          RunInUi(Sub()
                                      Dim Total As Long = Math.Max(Sizes.Sum(), 1)
                                      For i = 0 To Sizes.Length - 1
                                          StorageValues(i).Text = StringUtils.FormatByteSize(Sizes(i))
                                          Dim Ratio As Double = Math.Max(Sizes(i) / Total, 0.001)
                                          StorageFg(i).Width = New GridLength(Ratio, GridUnitType.Star)
                                          StorageBg(i).Width = New GridLength(Math.Max(1 - Ratio, 0.001), GridUnitType.Star)
                                      Next
                                      LabStorageNote.Text = ModLang.L("合计 ") & StringUtils.FormatByteSize(Sizes.Sum()) &
                                          ModLang.L("；条形图按各分类占比绘制，只统计本机实际存在的目录。")
                                  End Sub)
                      End Sub, "Storage Calc")
    End Sub

    ''' <summary>
    ''' 检查无用文件：扫描所有实例 30 天以前的日志与崩溃报告，只报告可回收空间，确认后才删除。
    ''' </summary>
    Private Sub BtnScanJunk_Click(sender As Object, e As MouseButtonEventArgs)
        Dim Root As String = PathUtils.AddSlashSuffix(McFolderSelected)
        Hint(ModLang.L("正在后台检查无用文件…"), HintType.Blue, False)
        RunInNewThread(Sub()
                          Dim Threshold As DateTime = Now.AddDays(-30)
                          Dim Found As New List(Of String)
                          Dim Bytes As Long = 0
                          Try
                              For Each VerDir In DirectoryUtils.EnumerateDirectories(Root & "versions\", False)
                                  For Each SubName In {"logs\", "crash-reports\"}
                                      Dim Folder As String = PathUtils.AddSlashSuffix(VerDir) & SubName
                                      If Not DirectoryUtils.Exists(Folder) Then Continue For
                                      For Each FilePath In DirectoryUtils.EnumerateFiles(Folder, False)
                                          Try
                                              Dim Info = FileUtils.GetInfo(FilePath)
                                              If Info.LastWriteTime < Threshold Then
                                                  Found.Add(FilePath)
                                                  Bytes += Info.Length
                                              End If
                                          Catch
                                              '单个文件读不到就跳过
                                          End Try
                                      Next
                                  Next
                              Next
                          Catch ex As Exception
                              Logger.Warn(ex, "扫描无用文件失败")
                          End Try
                          RunInUi(Sub()
                                      If Found.Count = 0 Then
                                          Hint(ModLang.L("没有发现 30 天以前的日志或崩溃报告。"), HintType.Green)
                                          Return
                                      End If
                                      If MyMsgBox(ModLang.L("发现 ") & Found.Count & ModLang.L(" 个 30 天以前的日志 / 崩溃报告，可回收 ") &
                                                  StringUtils.FormatByteSize(Bytes) & "。" & vbCrLf & vbCrLf &
                                                  ModLang.L("这些文件只用于事后排查，删除不影响游戏存档与 Mod。是否删除？"),
                                                  ModLang.L("检查无用文件"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
                                      Dim Ok As Integer = 0
                                      For Each FilePath In Found
                                          Try
                                              FileUtils.Delete(FilePath)
                                              Ok += 1
                                          Catch ex As Exception
                                              Logger.Warn(ex, $"删除无用文件失败：{FilePath}")
                                          End Try
                                      Next
                                      Hint(ModLang.L("已删除 ") & Ok & ModLang.L(" 个无用文件。"), HintType.Green)
                                      RefreshStorage()
                                  End Sub)
                      End Sub, "Junk Scan")
    End Sub

#End Region

    ''' <summary>定义可清理的分类（路径不存在时会被跳过，统计/删除都安全）。</summary>
    Private Sub InitCategories()
        Dim DataDir = Paths.BaseThenName     'TCL 数据目录（配置、日志、音乐、图片）
        Dim TempDir = PathTemp               '系统缓存（下载、图片、皮肤头像）
        '历史日志：Log2~Log5（Log1 是当前正在写入的日志，保留）
        Dim OldLogs As New List(Of String)
        For i = 2 To 5
            Dim P = PathUtils.ForApi(Path.Combine(DataDir, "Log" & i & ".txt"))
            If IO.File.Exists(P) Then OldLogs.Add(P)
        Next
        Dim OldLogPaths As String() = If(OldLogs.Count > 0, OldLogs.ToArray(), Array.Empty(Of String))

        Categories.Add(New CacheCategory With {
            .Title = ModLang.L("历史运行日志"),
            .Desc = ModLang.L("旧版日志（保留当前日志 Log1）"),
            .Paths = OldLogPaths})
        Categories.Add(New CacheCategory With {
            .Title = ModLang.L("下载缓存"),
            .Desc = ModLang.L("下载中的文件与解压残留，清理后如需将重新下载"),
            .Paths = {TempDir & "Download\"}})
        Categories.Add(New CacheCategory With {
            .Title = ModLang.L("图片缓存"),
            .Desc = ModLang.L("联网图片的本地副本，清理后按需重新下载"),
            .Paths = {TempDir & "MyImage\"}})
        Categories.Add(New CacheCategory With {
            .Title = ModLang.L("皮肤与头像缓存"),
            .Desc = ModLang.L("3D 皮肤预览与头像图片，清理后重新生成"),
            .Paths = {TempDir & "Cache\Skin\", TempDir & "Faces\"}})
        Categories.Add(New CacheCategory With {
            .Title = ModLang.L("崩溃日志"),
            .Desc = ModLang.L("上次崩溃的调试日志，可安全删除"),
            .Paths = {TempDir & "Crash.txt"}})
    End Sub

    '递归目录 / 单个文件的字节大小（不存在则 0）
    Private Shared Function DirSize(Paths_ As String()) As Long
        Dim Total As Long = 0
        For Each P In Paths_
            Try
                If P.EndsWith("\"c) AndAlso DirectoryUtils.Exists(P) Then
                    For Each F In DirectoryUtils.EnumerateFiles(P, includeSubDirectories:=True)
                        Dim Info = New IO.FileInfo(F)
                        If Info.Exists Then Total += Info.Length
                    Next
                ElseIf IO.File.Exists(P) Then
                    Total += New IO.FileInfo(P).Length
                End If
            Catch
                '单个文件读取失败不计入，不中断统计
            End Try
        Next
        Return Total
    End Function

    '生成勾选行 + 总占用行
    Private Sub BuildRows()
        PanTotal.Children.Clear()
        PanList.Children.Clear()
        PanRows.Clear()
        SizeRows.Clear()
        For Each C In Categories
            Dim Row As New System.Windows.Controls.Grid With {.Margin = New Thickness(0, 0, 0, 14), .Height = 34}
            Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
            Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(1, GridUnitType.Star)})
            Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
            Dim Cb As New MyCheckBox With {.Text = C.Title, .VerticalAlignment = VerticalAlignment.Center, .Height = 28}
            Cb.SetChecked(True, False)
            AddHandler Cb.Change, Sub(s, e) C.Enabled = CType(s, MyCheckBox).Checked
            System.Windows.Controls.Grid.SetColumn(Cb, 0)
            Dim Desc As New TextBlock With {.Text = C.Desc, .FontSize = 12, .Opacity = 0.6, .Margin = New Thickness(12, 0, 0, 0), .VerticalAlignment = VerticalAlignment.Center}
            System.Windows.Controls.Grid.SetColumn(Desc, 1)
            Dim Size As New TextBlock With {.Text = "—", .FontSize = 13, .VerticalAlignment = VerticalAlignment.Center, .Margin = New Thickness(16, 0, 0, 0), .HorizontalAlignment = HorizontalAlignment.Right, .MinWidth = 90}
            Size.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
            System.Windows.Controls.Grid.SetColumn(Size, 2)
            Row.Children.Add(Cb) : Row.Children.Add(Desc) : Row.Children.Add(Size)
            PanList.Children.Add(Row)
            PanRows.Add(Cb)
            SizeRows.Add(Size)
        Next
        '总占用占位
        Dim TotalRow As New System.Windows.Controls.StackPanel With {.Orientation = Orientation.Horizontal, .VerticalAlignment = VerticalAlignment.Center, .Margin = New Thickness(0, 0, 0, 10)}
        Dim TotalLabel As New TextBlock With {.Text = ModLang.L("所选占用"), .FontSize = 14, .VerticalAlignment = VerticalAlignment.Center}
        TotalLabel.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Dim TotalValue As New TextBlock With {.Text = "—", .FontSize = 14, .FontWeight = FontWeights.SemiBold, .VerticalAlignment = VerticalAlignment.Center, .Margin = New Thickness(10, 0, 0, 0)}
        TotalValue.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush2")
        TotalRow.Children.Add(TotalLabel)
        TotalRow.Children.Add(TotalValue)
        PanTotal.Children.Add(TotalRow)
        _PanTotalValue = TotalValue
        _PanTotalLabel = TotalLabel
    End Sub
    Private _PanTotalValue As TextBlock
    Private _PanTotalLabel As TextBlock

    '回填各类大小 + 总计（Sizes 为后台算好的字节数组，Nothing 时显示统计中）
    Private Sub RefreshSizes(Sizes As Long())
        For i = 0 To Categories.Count - 1
            If Sizes Is Nothing Then
                SizeRows(i).Text = ModLang.L("统计中…")
            Else
                SizeRows(i).Text = StringUtils.FormatByteSize(Sizes(i))
            End If
        Next
        If _PanTotalValue IsNot Nothing Then
            If Sizes Is Nothing Then
                _PanTotalValue.Text = ModLang.L("统计中…")
            Else
                Dim Selected As Long = 0
                For i = 0 To Categories.Count - 1
                    If Categories(i).Enabled Then Selected += Sizes(i)
                Next
                _PanTotalValue.Text = StringUtils.FormatByteSize(Selected)
                _PanTotalLabel.Text = ModLang.L("所选占用")
            End If
        End If
    End Sub

    '刷新：重算大小并重置勾选为全选中
    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs)
        For i = 0 To PanRows.Count - 1
            Categories(i).Enabled = True
            PanRows(i).SetChecked(True, False)
        Next
        RunInNewThread(Sub()
                          Dim Sizes = Categories.Select(Function(C) DirSize(C.Paths)).ToArray()
                          RunInUi(Sub() RefreshSizes(Sizes))
                      End Sub, "Cache Size Calc Refresh")
    End Sub

    '打开缓存目录
    Private Sub BtnOpen_Click(sender As Object, e As MouseButtonEventArgs)
        Try
            OpenExplorer(PathTemp)
        Catch ex As Exception
            Hint(ModLang.L("打开缓存目录失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    '清理所选分类：先确认，再后台删除，最后刷新
    Private Sub BtnClean_Click(sender As Object, e As MouseButtonEventArgs)
        Dim Selected = Categories.Where(Function(C) C.Enabled).ToList()
        If Selected.Count = 0 Then
            Hint(ModLang.L("请先勾选要清理的缓存类别。"), HintType.Blue)
            Return
        End If
        '估算所选占用
        Dim Bytes As Long = 0
        For Each C In Selected
            Bytes += DirSize(C.Paths)
        Next
        If Bytes = 0 Then
            Hint(ModLang.L("所选缓存没有占用空间，无需清理。"), HintType.Blue)
            Return
        End If
        Dim Detail As String = String.Join(vbCrLf & "   ", Selected.Select(Function(C) "· " & C.Title))
        Dim Confirm = MyMsgBox(
            ModLang.L("将清理以下缓存，预计释放 ") & StringUtils.FormatByteSize(Bytes) & "：" & vbCrLf & Detail & vbCrLf & vbCrLf &
            ModLang.L("这些内容可重新生成或重新下载，不影响你的配置、皮肤、翻译与游戏数据。"),
            Title:=ModLang.L("清理缓存"), Button1:=ModLang.L("确定"), Button2:=ModLang.L("取消"), IsWarn:=True)
        If Confirm <> 1 Then Return
        Dim ToClean = Selected
        RunInNewThread(Sub()
                          Dim Ok As Integer = 0
                          Dim Fail As Integer = 0
                          For Each C In ToClean
                              For Each P In C.Paths
                                  Try
                                      If P.EndsWith("\"c) AndAlso DirectoryUtils.Exists(P) Then
                                          DirectoryUtils.Delete(P)
                                      ElseIf IO.File.Exists(P) Then
                                          FileUtils.Delete(P)
                                      End If
                                      Ok += 1
                                  Catch
                                      Fail += 1
                                  End Try
                              Next
                          Next
                          RunInUi(Sub()
                                      Dim Msg = ModLang.L("已清理 ") & Ok & ModLang.L(" 项缓存")
                                      If Fail > 0 Then Msg &= ModLang.L("，其中 ") & Fail & ModLang.L(" 项未成功")
                                      Hint(Msg, HintType.Green, False)
                                      '删除后重新统计并取消勾选已清空的项
                                      Dim Sizes = Categories.Select(Function(C) DirSize(C.Paths)).ToArray()
                                      For i = 0 To Categories.Count - 1
                                          If Categories(i).Enabled AndAlso Sizes(i) = 0 Then
                                              Categories(i).Enabled = False
                                              PanRows(i).SetChecked(False, False)
                                          End If
                                      Next
                                      RefreshSizes(Sizes)
                                          End Sub)
                      End Sub, "Cache Clean")
    End Sub

End Class
