Public Class PageInstanceSaves

#Region "基础"

    ''' <summary>
    ''' 当前版本实例。
    ''' </summary>
    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property
    ''' <summary>
    ''' 当前版本的存档文件夹。
    ''' </summary>
    Private ReadOnly Property SavesFolder As String
        Get
            If Instance Is Nothing Then Return Nothing
            Return Instance.PathIndie & "saves\"
        End Get
    End Property

    ''' <summary>
    ''' 当前实例的全部存档（未筛选）。
    ''' </summary>
    Private ReadOnly AllSaves As New List(Of DirectoryInfo)
    ''' <summary>
    ''' 文件夹大小文本缓存，避免搜索 / 排序时重复递归统计。
    ''' </summary>
    Private ReadOnly SizeCache As New Dictionary(Of String, String)(StringComparer.OrdinalIgnoreCase)

    Private Sub PageInstanceSaves_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        HookOverlay()
        Refresh()
    End Sub

    ''' <summary>
    ''' 刷新存档列表（扫描放后台线程，期间显示镐子加载卡）。
    ''' </summary>
    Public Sub Refresh(Optional OnFinished As Action = Nothing)
        If IsRefreshing Then Return
        Dim CurrentInstance = Instance
        If CurrentInstance Is Nothing Then
            PanSaves.Children.Clear()
            PanSaves.Children.Add(New TextBlock With {.Text = ModLang.L("请先选择一个 Minecraft 版本。"), .Margin = New Thickness(0, 0, 0, 10), .Opacity = 0.6})
            If OnFinished IsNot Nothing Then OnFinished()
            Return
        End If
        IsRefreshing = True
        PanLoad.Visibility = Visibility.Visible
        RunInNewThread(
        Sub()
            Try
                Dim Folder As String = SavesFolder
                Dim Infos As New List(Of DirectoryInfo)
                If Folder IsNot Nothing Then
                    DirectoryUtils.Create(Folder)
                    For Each SaveFolder In DirectoryUtils.EnumerateDirectories(Folder, False)
                        Try
                            Infos.Add(DirectoryUtils.GetInfo(SaveFolder))
                        Catch ex As Exception
                            Logger.Warn(ex, $"获取存档文件夹信息失败：{SaveFolder}")
                        End Try
                    Next
                End If
                RunInUi(Sub()
                            Try
                                AllSaves.Clear()
                                SizeCache.Clear()
                                If Folder Is Nothing Then
                                    PanSaves.Children.Clear()
                                    PanSaves.Children.Add(New TextBlock With {.Text = ModLang.L("请先选择一个 Minecraft 版本。"), .Margin = New Thickness(0, 0, 0, 10), .Opacity = 0.6})
                                    Return
                                End If
                                AllSaves.AddRange(Infos)
                                Rebuild()
                            Catch ex As Exception
                                Logger.Error(ex, "刷新存档列表失败")
                            Finally
                                FinishRefresh(OnFinished)
                            End Try
                        End Sub)
            Catch ex As Exception
                Logger.Error(ex, "刷新存档列表失败")
                RunInUi(Sub() FinishRefresh(OnFinished))
            End Try
        End Sub, "Saves Refresh")
    End Sub

    Private IsRefreshing As Boolean = False
    Private Sub FinishRefresh(OnFinished As Action)
        PanLoad.Visibility = Visibility.Collapsed
        IsRefreshing = False
        If OnFinished IsNot Nothing Then OnFinished()
    End Sub

    ''' <summary>
    ''' 按当前搜索词与排序方式重建列表（对齐 LumilioCL 世界页的搜索 / 排序）。
    ''' </summary>
    Private Sub Rebuild()
        'InitializeComponent 期间排序 ComboBox 的默认选中会触发 SelectionChanged，
        '此时 PanSaves / CardList 尚未完成 XAML 赋值，直接跳过（Loaded 后会统一 Refresh）
        If PanSaves Is Nothing OrElse CardList Is Nothing Then Return
        PanSaves.Children.Clear()
        Dim Keyword As String = If(TxtSearch Is Nothing, "", If(TxtSearch.Text, "")).Trim()
        Dim Filtered As IEnumerable(Of DirectoryInfo) = AllSaves
        If Keyword.Length > 0 Then
            Filtered = Filtered.Where(Function(s) s.Name.IndexOf(Keyword, StringComparison.OrdinalIgnoreCase) >= 0)
        End If
        If ComboSort IsNot Nothing AndAlso ComboSort.SelectedIndex = 1 Then
            Filtered = Filtered.OrderBy(Function(s) s.Name, StringComparer.CurrentCulture)
        Else
            Filtered = Filtered.OrderByDescending(Function(s) s.LastWriteTimeUtc)
        End If
        Dim Result As List(Of DirectoryInfo) = Filtered.ToList()
        If Result.Count = 0 Then
            Dim Text As String = If(AllSaves.Count = 0, ModLang.L("该版本还没有任何存档。"), ModLang.L("没有匹配当前搜索的存档。"))
            PanSaves.Children.Add(New TextBlock With {.Text = Text, .Margin = New Thickness(0, 0, 0, 10), .Opacity = 0.6})
            CardList.Title = ModLang.L("存档列表（") & AllSaves.Count & ModLang.L(" 个）")
            Return
        End If
        For Each SaveItem In Result
            PanSaves.Children.Add(SaveListItem(SaveItem))
        Next
        CardList.Title = ModLang.L("存档列表（") & Result.Count & ModLang.L(" 个）")
    End Sub

    Private Sub Search_Changed(sender As Object, e As EventArgs)
        Rebuild()
    End Sub

    Private Sub Sort_Changed(sender As Object, e As SelectionChangedEventArgs)
        Rebuild()
    End Sub

#End Region

#Region "列表项"

    ''' <summary>
    ''' 生成单个存档的列表项。
    ''' </summary>
    Private Function SaveListItem(Save As DirectoryInfo) As UIElement
        Dim Folder As String = PathUtils.AddSlashSuffix(Save.FullName)
        Dim SizeText As String = GetFolderSizeText(Folder)
        Dim TimeText As String = If(Save.LastWriteTime.Year < 1980, "未知时间", Save.LastWriteTime.ToString("yyyy'/'MM'/'dd HH':'mm"))
        Dim NewItem As New MyListItem With {
            .Type = MyListItem.CheckType.Clickable,
            .Height = 42,
            .Title = PathUtils.GetLastPart(Folder),
            .Info = ModLang.L("最后游玩：") & (TimeText) & ModLang.L("，大小：") & (SizeText),
            .Tag = Folder,
            .SnapsToDevicePixels = True
        }
        '打开文件夹
        Dim BtnOpen As New MyIconButton With {
            .ToolTip = ModLang.L("打开存档文件夹"),
            .Logo = "M1097 584 250 584 562 896C591 925 591 972 562 1001 533 1030 487 1030 458 1001L21 565C6 550-0 531 0 511L0 511 0 511C-0 492 6 472 21 457L458 21C487-7 533-7 562 21 591 50 591 97 562 126L250 438 1097 438C1137 438 1170 471 1170 511 1170 551 1137 584 1097 584L1097 584Z"
        }
        AddHandler BtnOpen.Click, Sub(sender, e) OpenSaveFolder(Folder)
        '重命名
        Dim BtnRename As New MyIconButton With {
            .ToolTip = ModLang.L("重命名存档"),
            .Logo = "M884.4 130.6H140.6c-41.9 0-76 34.1-76 76v613.3c0 41.9 34.1 76 76 76h743.8c41.9 0 76-34.1 76-76V206.6c0-41.9-34.1-76-76-76z m-743.8 72h743.8c2.2 0 4 1.8 4 4v371.3L796 461.7c-21.7-27.3-54.1-43.7-88.8-45-34.7-1.3-68.1 12.7-91.7 38.3L454 630c-2.8 3-7.2 3.5-10.5 1.3l-103-70.3c-31.5-21.5-72.8-22.5-105.3-2.7l-98.6 60.1V206.6c0-2.2 1.8-4 4-4z m743.8 621.3H140.6c-2.2 0-4-1.8-4-4V702.7l136.1-83c8.4-5.1 19.1-4.8 27.2 0.7l103 70.3c15.9 10.8 35.1 15.6 54.2 13.4 19-2.2 36.7-11.2 49.8-25.3l161.6-175c9.5-10.3 22.3-15.7 36.2-15.1 13.9 0.5 26.3 6.8 35.1 17.8l148.7 187v126.3c-0.1 2.3-1.9 4.1-4.1 4.1z M231.8 400.6a69.6 69.6 0 1 0 98.4-98.4 69.6 69.6 0 1 0-98.4 98.4Z"
        }
        AddHandler BtnRename.Click, Sub(sender, e) RenameSave(Folder)
        '复制世界（对齐 LumilioCL：世界行 ⋯ 菜单的「复制」）
        Dim BtnCopy As New MyIconButton With {
            .ToolTip = ModLang.L("复制这个世界"),
            .Logo = "M884.4 130.6H140.6c-41.9 0-76 34.1-76 76v613.3c0 41.9 34.1 76 76 76h743.8c41.9 0 76-34.1 76-76V206.6c0-41.9-34.1-76-76-76z m-743.8 72h743.8c2.2 0 4 1.8 4 4v371.3L796 461.7c-21.7-27.3-54.1-43.7-88.8-45-34.7-1.3-68.1 12.7-91.7 38.3L454 630c-2.8 3-7.2 3.5-10.5 1.3l-103-70.3c-31.5-21.5-72.8-22.5-105.3-2.7l-98.6 60.1V206.6c0-2.2 1.8-4 4-4z"
        }
        AddHandler BtnCopy.Click, Sub(sender, e) CopySave(Folder)
        '导出为 zip
        Dim BtnExport As New MyIconButton With {
            .ToolTip = ModLang.L("导出为 .zip"),
            .Logo = "M512 64a448 448 0 1 1 0 896 448 448 0 0 1 0-896z m0 64a384 384 0 1 0 0 768 384 384 0 0 0 0-768z m-32 128h64v160h128v64H544v160h-64V480H352v-64h128V256z"
        }
        AddHandler BtnExport.Click, Sub(sender, e) ExportSave(Folder)
        '"更多"菜单：世界信息 / 修改图标 / 备份世界 / 备份列表
        Dim BtnMore As New MyIconButton With {
            .SvgIcon = "lucide/more-vertical",
            .ToolTip = ModLang.L("更多操作")
        }
        AddHandler BtnMore.Click, Sub(sender As Object, e As EventArgs) ShowSaveMenu(BtnMore, Folder)
        '删除
        Dim BtnDelete As New MyIconButton With {
            .ToolTip = ModLang.L("删除存档"),
            .Theme = MyIconButton.Themes.Red,
            .Logo = "M445 545c18 0 33 14 33 33v149a182 182 0 1 1-182-182z m282 0a182 182 0 1 1-182 182v-149c0-18 14-33 33-33zM412 611H296a116 116 0 1 0 116 116V611z m315 0H611v116a116 116 0 1 0 116-116zM296 113a182 182 0 0 1 182 182v149a33 33 0 0 1-33 33H296a182 182 0 0 1 0-365z m431 0a182 182 0 0 1 0 365h-149a33 33 0 0 1-33-33V296A182 182 0 0 1 727 113zM296 180a116 116 0 0 0 0 232H412V296c0-62-48-112-109-115z m431 0a116 116 0 0 0-116 116V412h116c62 0 112-48 115-109l0-6a116 116 0 0 0-116-116z"
        }
        AddHandler BtnDelete.Click, Sub(sender, e) DeleteSave(Folder)
        '启动（QuickPlay，仅支持 --quickPlaySingleplayer 的版本）
        Dim Buttons As New List(Of MyIconButton) From {BtnOpen, BtnRename, BtnCopy, BtnExport, BtnMore, BtnDelete}
        Dim Instance As McInstance = PageInstanceLeft.Instance
        If Instance IsNot Nothing AndAlso Instance.JsonObject IsNot Nothing AndAlso
           Instance.JsonObject.ToString().Contains("--quickPlaySingleplayer") Then
            Dim BtnLaunch As New MyIconButton With {
                .ToolTip = ModLang.L("以此存档启动游戏（1.19+）"),
                .Logo = "M341.3 213.3v597.4l470-298.7z"
            }
            AddHandler BtnLaunch.Click, Sub(sender, e) LaunchSave(Folder)
            Buttons.Add(BtnLaunch)
        End If
        NewItem.Buttons = Buttons.ToArray
        '点击整个条目也打开文件夹
        AddHandler NewItem.Click, Sub(sender, e) OpenSaveFolder(Folder)
        Return NewItem
    End Function

    ''' <summary>
    ''' 获取文件夹大小的人类可读描述。
    ''' </summary>
    Private Function GetFolderSizeText(Folder As String) As String
        If SizeCache.ContainsKey(Folder) Then Return SizeCache(Folder)
        Dim Text As String = CalculateFolderSizeText(Folder)
        SizeCache(Folder) = Text
        Return Text
    End Function

    Private Function CalculateFolderSizeText(Folder As String) As String
        Try
            Dim Size As Long = 0
            For Each File In DirectoryUtils.EnumerateFiles(Folder, True)
                Try
                    Size += FileUtils.GetInfo(File).Length
                Catch ex As Exception
                    Logger.Warn(ex, "读取文件大小失败（跳过该文件）")
                    '跳过无法访问的文件
                End Try
            Next
            If Size < 1024 Then Return Size & " B"
            If Size < 1024 * 1024 Then Return Math.Round(Size / 1024, 1) & " KB"
            If Size < 1024L * 1024 * 1024 Then Return Math.Round(Size / 1024 / 1024, 1) & " MB"
            Return Math.Round(Size / 1024 / 1024 / 1024, 2) & " GB"
        Catch ex As Exception
            Logger.Warn(ex, $"计算文件夹大小失败：{Folder}")
            Return "未知"
        End Try
    End Function

#End Region

#Region "操作"

    Private Sub BtnOpenFolder_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnOpenFolder.Click
        Dim Folder As String = SavesFolder
        If Folder Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        DirectoryUtils.Create(Folder)
        OpenExplorer(Folder)
    End Sub
    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnRefresh.Click
        Refresh(Sub() Hint(ModLang.L("存档列表已刷新！"), HintType.Green))
    End Sub

    ''' <summary>
    ''' 导入存档：选择一个文件夹并复制到存档目录。
    ''' </summary>
    Private Sub BtnImport_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnImport.Click
        Dim SavesPath As String = SavesFolder
        If SavesPath Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        Dim Result As List(Of String) = Dialogs.SelectFolder("选择要导入的存档文件夹", False)
        If Not Result.Any() Then Return
        Dim Source As String = Result.First()
        '检查是否为存档文件夹
        If Not DirectoryUtils.Exists(Source) OrElse Not FileUtils.Exists(PathUtils.AddSlashSuffix(Source) & "level.dat") Then
            If MyMsgBox(ModLang.L("所选文件夹中没有找到 level.dat，它可能不是一个完整的存档。") & vbCrLf & ModLang.L("仍要继续导入吗？"),
                ModLang.L("存档检查"), ModLang.L("继续导入"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        End If
        Dim Target As String = PathUtils.AddSlashSuffix(SavesPath) & PathUtils.GetLastPart(Source)
        If DirectoryUtils.Exists(Target) Then
            If MyMsgBox(ModLang.L("已存在名为 ") & PathUtils.GetLastPart(Target) & ModLang.L(" 的存档，是否覆盖？"), ModLang.L("导入存档"), ModLang.L("覆盖"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
            DirectoryUtils.Delete(Target)
        End If
        RunInNewThread(
        Sub()
            Try
                DirectoryUtils.Copy(PathUtils.AddSlashSuffix(Source), Target)
                Logger.Info($"已导入存档：{Source} → {Target}")
                RunInUi(
                Sub()
                    Hint(ModLang.L("存档导入成功！"), HintType.Green)
                    Refresh()
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "导入存档失败")
                RunInUi(Sub() Hint(ModLang.L("导入存档失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Import Save", ThreadPriority.BelowNormal)
    End Sub

    ''' <summary>
    ''' 打开存档文件夹。
    ''' </summary>
    Private Sub OpenSaveFolder(Folder As String)
        If Not DirectoryUtils.Exists(Folder) Then
            Hint(ModLang.L("存档文件夹不存在！"), HintType.Red)
            Return
        End If
        OpenExplorer(Folder)
    End Sub

    ''' <summary>
    ''' 重命名存档。
    ''' </summary>
    Private Sub RenameSave(Folder As String)
        Dim OldName As String = PathUtils.GetLastPart(Folder)
        Dim NewName As String = MyMsgBoxInput(ModLang.L("重命名存档"), ModLang.L("请输入新的存档名称："), OldName, hintText:=OldName)
        If String.IsNullOrEmpty(NewName) OrElse NewName = OldName Then Return
        Try
            Dim Target As String = PathUtils.AddSlashSuffix(SavesFolder) & NewName
            If DirectoryUtils.Exists(Target) Then
                Hint(ModLang.L("已存在同名存档！"), HintType.Red)
                Return
            End If
            DirectoryUtils.Move(Folder, Target)
            Hint(ModLang.L("存档已重命名！"), HintType.Green)
            Refresh()
        Catch ex As Exception
            Logger.Error(ex, "重命名存档失败")
            Hint(ModLang.L("重命名存档失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>
    ''' 删除存档（移入回收站）。
    ''' </summary>
    Private Sub DeleteSave(Folder As String)
        Dim Name As String = PathUtils.GetLastPart(Folder)
        If MyMsgBox(ModLang.L("即将删除存档 ") & Name & ModLang.L("（") & GetFolderSizeText(Folder) & ModLang.L("）。") & vbCrLf &
                    ModLang.L("删除后将被移入回收站，可自行恢复。是否继续？"), ModLang.L("删除存档"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            DirectoryUtils.Delete(Folder, True)
            Hint(ModLang.L("存档已删除。"), HintType.Green)
            Refresh()
        Catch ex As Exception
            Logger.Error(ex, "删除存档失败")
            Hint(ModLang.L("删除存档失败：") & ex.Message, HintType.Red)
        End Try
    End Sub
    ''' <summary>
    ''' 复制世界到新文件夹（重名自动加序号）。
    ''' </summary>
    Private Sub CopySave(Folder As String)
        Try
            Dim Source As String = PathUtils.AddSlashSuffix(Folder)
            Dim Target As String = ModArchive.UniquePath(SavesFolder, PathUtils.GetLastPart(Folder) & " - 副本")
            RunInNewThread(
            Sub()
                Try
                    DirectoryUtils.Copy(Source, Target)
                    RunInUi(
                    Sub()
                        Hint(ModLang.L("世界已复制为：") & PathUtils.GetLastPart(Target), HintType.Green)
                        Refresh()
                    End Sub)
                Catch ex As Exception
                    Logger.Error(ex, "复制世界失败")
                    RunInUi(Sub() Hint(ModLang.L("复制世界失败：") & ex.Message, HintType.Red))
                End Try
            End Sub, "Copy World", ThreadPriority.BelowNormal)
        Catch ex As Exception
            Logger.Error(ex, "复制世界失败")
            Hint(ModLang.L("复制世界失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>
    ''' 把世界导出为 .zip（不含 session.lock）。
    ''' </summary>
    Private Sub ExportSave(Folder As String)
        Dim Target As String = Dialogs.SaveFile(ModLang.L("导出世界"), PathUtils.GetLastPart(Folder) & ".zip",
            filter:={("zip", ModLang.L("Zip 压缩包"))})
        If String.IsNullOrEmpty(Target) Then Return
        Hint(ModLang.L("正在后台打包世界…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Try
                ModArchive.CreateZip(Folder, Target, {"session.lock"})
                RunInUi(
                Sub()
                    Hint(ModLang.L("世界已导出：") & IO.Path.GetFileName(Target), HintType.Green)
                    OpenExplorer(IO.Path.GetDirectoryName(Target))
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "导出世界失败")
                RunInUi(Sub() Hint(ModLang.L("导出世界失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Export World", ThreadPriority.BelowNormal)
    End Sub

    ''' <summary>
    ''' 从 .zip 导入世界：解压到临时目录，找最浅的含 level.dat 的目录，再复制进 saves。
    ''' </summary>
    Private Sub BtnImportZip_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnImportZip.Click
        Dim SavesPath As String = SavesFolder
        If SavesPath Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        Dim Files As List(Of String) = Dialogs.SelectFile(ModLang.L("选择世界压缩包"), False, filter:="zip|Zip 压缩包")
        If Files Is Nothing OrElse Not Files.Any() Then Return
        Dim ZipPath As String = Files.First()
        Hint(ModLang.L("正在后台解压世界…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Dim Temp As String = PathUtils.AddSlashSuffix(PathTemp) & "WorldImport\" & Guid.NewGuid().ToString("N")
            Try
                ModArchive.ExtractZip(ZipPath, Temp)
                Dim Root As String = ModArchive.FindWorldRoot(Temp)
                If Root Is Nothing Then
                    RunInUi(Sub() Hint(ModLang.L("压缩包里没有找到 level.dat，可能不是世界存档。"), HintType.Red))
                    Return
                End If
                Dim Name As String = If(PathUtils.IsSame(Root, Temp), IO.Path.GetFileNameWithoutExtension(ZipPath), PathUtils.GetLastPart(Root))
                If String.IsNullOrWhiteSpace(Name) Then Name = ModLang.L("导入的世界")
                Dim Target As String = ModArchive.UniquePath(SavesPath, Name)
                DirectoryUtils.Copy(PathUtils.AddSlashSuffix(Root), Target)
                RunInUi(
                Sub()
                    Hint(ModLang.L("世界已导入：") & PathUtils.GetLastPart(Target), HintType.Green)
                    Refresh()
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "从压缩包导入世界失败")
                RunInUi(Sub() Hint(ModLang.L("导入世界失败：") & ex.Message, HintType.Red))
            Finally
                Try
                    If DirectoryUtils.Exists(Temp) Then DirectoryUtils.Delete(Temp)
                Catch ex As Exception
                    Logger.Warn(ex, $"清理世界导入临时目录失败：{Temp}")
                End Try
            End Try
        End Sub, "Import World", ThreadPriority.BelowNormal)
    End Sub

    '以此存档快速启动（QuickPlay）
    Private Sub LaunchSave(Folder As String)
        Try
            Dim Instance As McInstance = PageInstanceLeft.Instance
            If Instance Is Nothing Then Return
            If McLaunchStart(New McLaunchOptions With {
                    .Instance = Instance,
                    .WorldName = PathUtils.GetLastPart(Folder)
                }) Then
                FrmMain.PageChange(FormMain.PageType.Launch)
            End If
        Catch ex As Exception
            Logger.Error(ex, "以存档启动游戏失败")
            Hint(ModLang.L("以存档启动游戏失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

#End Region

#Region "世界信息 / 世界图标 / 单世界备份"

    '---- 覆盖层状态 ----

    ''' <summary>覆盖层当前操作的世界目录。</summary>
    Private OverlayWorld As String = Nothing
    ''' <summary>覆盖层当前展示的世界信息。</summary>
    Private OverlayInfo As ModWorldData.WorldInfo = Nothing
    Private OverlayComboDifficulty As MyComboBox = Nothing
    Private OverlayCheckCommands As MyCheckBox = Nothing
    Private OverlayCheckLocked As MyCheckBox = Nothing
    Private OverlayTextName As MyTextBox = Nothing
    Private OverlayHooked As Boolean = False

    ''' <summary>挂上覆盖层的通用事件（只挂一次）。</summary>
    Private Sub HookOverlay()
        If OverlayHooked OrElse PanOverlay Is Nothing Then Return
        OverlayHooked = True
        AddHandler BtnOverlayClose.Click, Sub(sender As Object, e As EventArgs) HideOverlay()
        '点遮罩空白处关闭；点在卡片上时 OriginalSource 不是遮罩本身
        AddHandler PanOverlay.MouseLeftButtonDown,
            Sub(sender As Object, e As MouseButtonEventArgs)
                If e.OriginalSource Is PanOverlay Then HideOverlay()
            End Sub
    End Sub

    ''' <summary>离开本页面时收起覆盖层，避免下次进入还挂着。</summary>
    Private Sub PageInstanceSaves_PageExit() Handles Me.PageExit
        HideOverlay()
    End Sub

    Private Sub ShowOverlay(Title As String)
        If PanOverlay Is Nothing Then Return
        CardOverlay.Title = Title
        PanOverlayScroll.ScrollToHome()
        PanOverlay.Visibility = Visibility.Visible
    End Sub

    Private Sub HideOverlay()
        If PanOverlay Is Nothing Then Return
        PanOverlay.Visibility = Visibility.Collapsed
        PanOverlayBody.Children.Clear()
        OverlayWorld = Nothing
        OverlayInfo = Nothing
        OverlayComboDifficulty = Nothing
        OverlayCheckCommands = Nothing
        OverlayCheckLocked = Nothing
        OverlayTextName = Nothing
    End Sub

    '---- 菜单 ----

    ''' <summary>存档项右侧的「更多」菜单。</summary>
    Private Sub ShowSaveMenu(Target As MyIconButton, Folder As String)
        Try
            Dim Menu As New ContextMenu
            Menu.Items.Add(SaveMenuItem(ModLang.L("世界信息"), Sub() ShowWorldInfo(Folder), "lucide/info"))
            Menu.Items.Add(SaveMenuItem(ModLang.L("修改图标…"), Sub() ChangeWorldIcon(Folder), "lucide/image"))
            Menu.Items.Add(New Separator)
            Menu.Items.Add(SaveMenuItem(ModLang.L("备份世界"), Sub() BackupWorld(Folder), "lucide/archive"))
            Menu.Items.Add(SaveMenuItem(ModLang.L("备份列表…"), Sub() ShowBackupList(Folder), "lucide/history"))
            Menu.PlacementTarget = Target
            Menu.IsOpen = True
        Catch ex As Exception
            Logger.Error(ex, "打开存档操作菜单失败")
        End Try
    End Sub

    Private Function SaveMenuItem(Text As String, Handler As Action, Optional SvgIcon As String = "") As MyMenuItem
        Dim One As New MyMenuItem With {.Header = Text}
        If SvgIcon <> "" Then One.SvgIcon = SvgIcon
        AddHandler One.Click, Sub(sender As Object, e As RoutedEventArgs) Handler()
        Return One
    End Function

    '---- 面板小工具 ----

    ''' <summary>分组标题。</summary>
    Private Function SectionHeader(Text As String) As UIElement
        Dim Lab As New TextBlock With {
            .Text = Text,
            .FontWeight = FontWeights.Bold,
            .FontSize = 13,
            .Margin = New Thickness(0, 12, 0, 8)
        }
        Lab.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Return Lab
    End Function

    ''' <summary>只读信息行：左侧标签 + 右侧值（可选复制按钮）。</summary>
    Private Function InfoRow(Label As String, Value As String, Optional CopyValue As String = Nothing) As UIElement
        Dim Row As New Grid With {.Margin = New Thickness(0, 0, 0, 7)}
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(84)})
        Row.ColumnDefinitions.Add(New ColumnDefinition)
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
        Dim Lab As New TextBlock With {
            .Text = Label,
            .Opacity = 0.6,
            .VerticalAlignment = VerticalAlignment.Center,
            .TextTrimming = TextTrimming.CharacterEllipsis
        }
        Row.Children.Add(Lab)
        Dim Val As New TextBlock With {
            .Text = If(Value, ""),
            .VerticalAlignment = VerticalAlignment.Center,
            .TextTrimming = TextTrimming.CharacterEllipsis,
            .TextWrapping = TextWrapping.NoWrap
        }
        Val.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Grid.SetColumn(Val, 1)
        Row.Children.Add(Val)
        If CopyValue IsNot Nothing Then
            Dim Para As String = CopyValue
            Dim BtnCopy As New MyIconButton With {
                .SvgIcon = "lucide/copy",
                .Width = 24,
                .Height = 24,
                .Margin = New Thickness(6, 0, 0, 0),
                .ToolTip = ModLang.L("复制")
            }
            AddHandler BtnCopy.Click,
                Sub(sender As Object, e As EventArgs)
                    Try
                        Clipboard.SetText(Para)
                        Hint(ModLang.L("已复制到剪贴板"), HintType.Green)
                    Catch ex As Exception
                        Logger.Warn(ex, "复制到剪贴板失败")
                        Hint(ModLang.L("复制失败"), HintType.Red)
                    End Try
                End Sub
            Grid.SetColumn(BtnCopy, 2)
            Row.Children.Add(BtnCopy)
        End If
        Return Row
    End Function

    ''' <summary>可编辑行：左侧标签 + 右侧控件。</summary>
    Private Function EditRow(Label As String, Editor As FrameworkElement) As UIElement
        Dim Row As New Grid With {.Margin = New Thickness(0, 0, 0, 10)}
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(84)})
        Row.ColumnDefinitions.Add(New ColumnDefinition)
        Dim Lab As New TextBlock With {
            .Text = Label,
            .Opacity = 0.6,
            .VerticalAlignment = VerticalAlignment.Center
        }
        Row.Children.Add(Lab)
        Grid.SetColumn(Editor, 1)
        Row.Children.Add(Editor)
        Return Row
    End Function

    ''' <summary>说明文本。</summary>
    Private Function NoteText(Text As String) As UIElement
        Return New TextBlock With {
            .Text = Text,
            .Opacity = 0.6,
            .TextWrapping = TextWrapping.Wrap,
            .Margin = New Thickness(0, 4, 0, 6)
        }
    End Function

    Private Function FormatNumber(Value As Double) As String
        If Math.Abs(Value - Math.Round(Value)) < 0.05 Then Return Math.Round(Value).ToString("0")
        Return Value.ToString("0.##")
    End Function

    '---- 世界信息面板 ----

    Private Sub ShowWorldInfo(Folder As String)
        Try
            HookOverlay()
            If Not FileUtils.Exists(PathUtils.AddSlashSuffix(Folder) & "level.dat") Then
                Hint(ModLang.L("这个文件夹里没有 level.dat，可能不是有效的存档。"), HintType.Red)
                Return
            End If
            Dim Info As ModWorldData.WorldInfo = ModWorldData.ReadWorldInfo(Folder)
            If Info Is Nothing Then
                Hint(ModLang.L("读取世界信息失败，level.dat 可能已损坏。"), HintType.Red)
                Return
            End If
            Dim Player As ModWorldData.PlayerInfo = ModWorldData.ReadPlayerInfo(Folder)
            OverlayWorld = Folder
            OverlayInfo = Info
            BuildWorldInfoPanel(Folder, Info, Player)
            ShowOverlay(ModLang.L("世界信息"))
        Catch ex As Exception
            Logger.Error(ex, "打开世界信息面板失败")
            Hint(ModLang.L("打开世界信息失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    Private Sub BuildWorldInfoPanel(Folder As String, Info As ModWorldData.WorldInfo, Player As ModWorldData.PlayerInfo)
        PanOverlayBody.Children.Clear()

        '只读区
        PanOverlayBody.Children.Add(SectionHeader(ModLang.L("基本信息")))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("世界名"), Info.LevelName))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("存档文件夹"), PathUtils.GetLastPart(Folder)))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("版本"),
            If(String.IsNullOrEmpty(Info.VersionName), ModLang.L("未知"), Info.VersionName)))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("种子"), Info.RandomSeed.ToString(), Info.RandomSeed.ToString()))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("出生点"),
            Info.SpawnX & ", " & Info.SpawnY & ", " & Info.SpawnZ,
            Info.SpawnX & " " & Info.SpawnY & " " & Info.SpawnZ))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("游戏时长"),
            Info.DayCount & ModLang.L(" 天") & ModLang.L("（") & Info.DayTime & ModLang.L(" 刻）")))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("上次游玩"),
            If(String.IsNullOrEmpty(Info.LastPlayedText), ModLang.L("未知"), Info.LastPlayedText)))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("默认游戏模式"), ModWorldData.GameTypeText(Info.GameType)))
        PanOverlayBody.Children.Add(InfoRow(ModLang.L("世界图标"),
            If(Info.HasIcon, ModLang.L("已设置"), ModLang.L("默认（未设置）"))))

        '可改区
        PanOverlayBody.Children.Add(SectionHeader(ModLang.L("可修改")))
        OverlayTextName = New MyTextBox With {
            .Text = Info.LevelName,
            .Height = 32,
            .Width = 250,
            .HorizontalAlignment = HorizontalAlignment.Left,
            .HintText = ModLang.L("世界名称")
        }
        PanOverlayBody.Children.Add(EditRow(ModLang.L("世界名"), OverlayTextName))

        OverlayComboDifficulty = New MyComboBox With {
            .Height = 32,
            .Width = 150,
            .HorizontalAlignment = HorizontalAlignment.Left
        }
        '顺序必须与 0~3 对应
        For Each ItemText In New String() {
                ModLang.L("和平"),
                ModLang.L("简单"),
                ModLang.L("普通"),
                ModLang.L("困难")}
            OverlayComboDifficulty.Items.Add(New MyComboBoxItem With {.Content = ItemText})
        Next
        OverlayComboDifficulty.SelectedIndex = Math.Max(0, Math.Min(3, CInt(Info.Difficulty)))
        PanOverlayBody.Children.Add(EditRow(ModLang.L("难度"), OverlayComboDifficulty))

        OverlayCheckCommands = New MyCheckBox With {
            .Text = ModLang.L("允许作弊（开启命令）"),
            .Checked = Info.AllowCommands,
            .Height = 28,
            .HorizontalAlignment = HorizontalAlignment.Left,
            .Margin = New Thickness(0, 0, 0, 8)
        }
        PanOverlayBody.Children.Add(OverlayCheckCommands)

        OverlayCheckLocked = New MyCheckBox With {
            .Text = ModLang.L("锁定难度（游戏内无法再更改）"),
            .Checked = Info.DifficultyLocked,
            .Height = 28,
            .HorizontalAlignment = HorizontalAlignment.Left,
            .Margin = New Thickness(0, 0, 0, 8)
        }
        PanOverlayBody.Children.Add(OverlayCheckLocked)

        PanOverlayBody.Children.Add(NoteText(ModLang.L("修改会直接写回 level.dat，原文件会保留一份 level.dat.bak。")))

        '玩家区（有数据才显示）
        If Player IsNot Nothing AndAlso Player.Found Then
            PanOverlayBody.Children.Add(SectionHeader(ModLang.L("玩家数据")))
            PanOverlayBody.Children.Add(InfoRow(ModLang.L("血量"),
                FormatNumber(Player.Health) & " / " & FormatNumber(Player.MaxHealth)))
            PanOverlayBody.Children.Add(InfoRow(ModLang.L("饥饿"),
                Player.FoodLevel & ModLang.L("（饱和度 ") & FormatNumber(Player.FoodSaturation) & "）"))
            PanOverlayBody.Children.Add(InfoRow(ModLang.L("经验等级"),
                Player.XpLevel & ModLang.L("（总计 ") & Player.XpTotal & "）"))
            PanOverlayBody.Children.Add(InfoRow(ModLang.L("坐标"),
                FormatNumber(Player.X) & ", " & FormatNumber(Player.Y) & ", " & FormatNumber(Player.Z),
                FormatNumber(Player.X) & " " & FormatNumber(Player.Y) & " " & FormatNumber(Player.Z)))
            PanOverlayBody.Children.Add(InfoRow(ModLang.L("所在维度"), ModWorldData.DimensionText(Player.Dimension)))
            If Player.GameType >= 0 Then
                PanOverlayBody.Children.Add(InfoRow(ModLang.L("玩家游戏模式"), ModWorldData.GameTypeText(Player.GameType)))
            End If
            If Player.HasDeathPoint Then
                PanOverlayBody.Children.Add(InfoRow(ModLang.L("上次死亡点"),
                    FormatNumber(Player.DeathX) & ", " & FormatNumber(Player.DeathY) & ", " & FormatNumber(Player.DeathZ) &
                    ModLang.L("（") & ModWorldData.DimensionText(Player.DeathDim) & "）"))
            End If
        Else
            PanOverlayBody.Children.Add(NoteText(ModLang.L("没有找到玩家数据（可能还没进过这个世界）。")))
        End If

        '底部按钮
        Dim PanBtn As New StackPanel With {
            .Orientation = Orientation.Horizontal,
            .HorizontalAlignment = HorizontalAlignment.Right,
            .Margin = New Thickness(0, 14, 0, 0)
        }
        Dim BtnCancel As New MyButton With {
            .Text = ModLang.L("取消"),
            .MinWidth = 100,
            .Height = 34
        }
        AddHandler BtnCancel.Click, Sub(sender As Object, e As MouseButtonEventArgs) HideOverlay()
        PanBtn.Children.Add(BtnCancel)
        Dim BtnSave As New MyButton With {
            .Text = ModLang.L("保存修改"),
            .MinWidth = 110,
            .Height = 34,
            .Margin = New Thickness(10, 0, 0, 0),
            .ColorType = MyButton.ColorState.Highlight
        }
        AddHandler BtnSave.Click, Sub(sender As Object, e As MouseButtonEventArgs) SaveWorldInfo()
        PanBtn.Children.Add(BtnSave)
        PanOverlayBody.Children.Add(PanBtn)
    End Sub

    ''' <summary>把面板上的改动写回 level.dat。</summary>
    Private Sub SaveWorldInfo()
        Dim Folder As String = OverlayWorld
        Dim Info As ModWorldData.WorldInfo = OverlayInfo
        If Folder Is Nothing OrElse Info Is Nothing Then Return
        Dim NewName As String = If(OverlayTextName Is Nothing, "", If(OverlayTextName.Text, "")).Trim()
        If NewName.Length = 0 Then
            Hint(ModLang.L("世界名不能为空。"), HintType.Red)
            Return
        End If
        Dim NewDifficulty As Byte = CByte(Math.Max(0, Math.Min(3, If(OverlayComboDifficulty Is Nothing, CInt(Info.Difficulty), OverlayComboDifficulty.SelectedIndex))))
        Dim NewCommands As Boolean = OverlayCheckCommands IsNot Nothing AndAlso OverlayCheckCommands.Checked
        Dim NewLocked As Boolean = OverlayCheckLocked IsNot Nothing AndAlso OverlayCheckLocked.Checked

        '只改可改字段，其余原样保留
        Dim Target As New ModWorldData.WorldInfo With {
            .LevelName = NewName,
            .Difficulty = NewDifficulty,
            .DifficultyLocked = NewLocked,
            .AllowCommands = NewCommands,
            .Hardcore = Info.Hardcore,
            .GameType = Info.GameType,
            .RandomSeed = Info.RandomSeed,
            .SpawnX = Info.SpawnX,
            .SpawnY = Info.SpawnY,
            .SpawnZ = Info.SpawnZ,
            .DayTime = Info.DayTime,
            .LastPlayed = Info.LastPlayed,
            .VersionName = Info.VersionName,
            .HasIcon = Info.HasIcon
        }
        RunInNewThread(
        Sub()
            Try
                ModWorldData.UpdateWorldInfo(Folder, Target)
                RunInUi(
                Sub()
                    HideOverlay()
                    Refresh()
                    Hint(ModLang.L("世界信息已保存。"), HintType.Green)
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "保存世界信息失败")
                RunInUi(Sub() Hint(ModLang.L("保存世界信息失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Save World Info", ThreadPriority.BelowNormal)
    End Sub

    '---- 世界图标 ----

    Private Sub ChangeWorldIcon(Folder As String)
        Try
            Dim Files As List(Of String) = Dialogs.SelectFile(ModLang.L("选择世界图标"), False,
                filter:={({"png"}, ModLang.L("PNG 图片"))})
            If Files Is Nothing OrElse Not Files.Any() Then Return
            Dim ImagePath As String = Files.First()
            Dim ErrorText As String = ""
            If ModWorldData.SetWorldIcon(Folder, ImagePath, ErrorText) Then
                Hint(ModLang.L("世界图标已更新。"), HintType.Green)
                Refresh()
            Else
                MyMsgBox(If(String.IsNullOrEmpty(ErrorText), ModLang.L("设置世界图标失败。"), ErrorText),
                         ModLang.L("修改世界图标"), ModLang.L("确定"), "", IsWarn:=True)
            End If
        Catch ex As Exception
            Logger.Error(ex, "修改世界图标失败")
            Hint(ModLang.L("修改世界图标失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    '---- 世界备份 ----

    Private Sub BackupWorld(Folder As String)
        Dim InstanceName As String = If(Instance Is Nothing, "", Instance.Name)
        Hint(ModLang.L("正在后台备份世界…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Dim Target As String = ModWorldData.CreateBackup(Folder, InstanceName)
            RunInUi(
            Sub()
                If String.IsNullOrEmpty(Target) Then
                    Hint(ModLang.L("备份世界失败，请查看日志。"), HintType.Red)
                Else
                    Hint(ModLang.L("世界已备份：") & IO.Path.GetFileName(Target), HintType.Green)
                End If
            End Sub)
        End Sub, "Backup World", ThreadPriority.BelowNormal)
    End Sub

    ''' <summary>备份列表面板。</summary>
    Private Sub ShowBackupList(Folder As String)
        Try
            HookOverlay()
            OverlayWorld = Folder
            Dim InstanceName As String = If(Instance Is Nothing, "", Instance.Name)
            Dim Entries As List(Of ModWorldData.BackupEntry) = ModWorldData.ListBackups(Folder, InstanceName)
            PanOverlayBody.Children.Clear()
            PanOverlayBody.Children.Add(NoteText(ModLang.L("备份保存在：") &
                                                ModWorldData.BackupFolderOf(Folder, InstanceName)))

            If Entries.Count = 0 Then
                PanOverlayBody.Children.Add(SectionHeader(ModLang.L("还没有备份")))
                PanOverlayBody.Children.Add(NoteText(ModLang.L("点存档右侧「更多 → 备份世界」即可创建一份备份。")))
            Else
                PanOverlayBody.Children.Add(SectionHeader(ModLang.L("共 ") & Entries.Count & ModLang.L(" 份备份")))
                For Each Entry As ModWorldData.BackupEntry In Entries
                    PanOverlayBody.Children.Add(BackupListItem(Entry))
                Next
            End If

            Dim PanBtn As New StackPanel With {
                .Orientation = Orientation.Horizontal,
                .HorizontalAlignment = HorizontalAlignment.Right,
                .Margin = New Thickness(0, 14, 0, 0)
            }
            Dim BtnNew As New MyButton With {
                .Text = ModLang.L("立即备份"),
                .MinWidth = 110,
                .Height = 34
            }
            AddHandler BtnNew.Click, Sub(sender As Object, e As MouseButtonEventArgs) BackupWorld(Folder)
            PanBtn.Children.Add(BtnNew)
            Dim BtnClose As New MyButton With {
                .Text = ModLang.L("关闭"),
                .MinWidth = 100,
                .Height = 34,
                .Margin = New Thickness(10, 0, 0, 0),
                .ColorType = MyButton.ColorState.Highlight
            }
            AddHandler BtnClose.Click, Sub(sender As Object, e As MouseButtonEventArgs) HideOverlay()
            PanBtn.Children.Add(BtnClose)
            PanOverlayBody.Children.Add(PanBtn)

            ShowOverlay(ModLang.L("世界备份"))
        Catch ex As Exception
            Logger.Error(ex, "打开世界备份列表失败")
            Hint(ModLang.L("打开备份列表失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>备份列表中的一项。</summary>
    Private Function BackupListItem(Entry As ModWorldData.BackupEntry) As UIElement
        Dim Item As New MyListItem With {
            .Type = MyListItem.CheckType.Clickable,
            .Height = 46,
            .Title = Entry.Name,
            .Info = Entry.CreatedText & ModLang.L("，大小：") & Entry.SizeText,
            .Tag = Entry,
            .IsScaleAnimationEnabled = False
        }
        Dim BtnRestore As New MyIconButton With {
            .SvgIcon = "lucide/download",
            .ToolTip = ModLang.L("还原此备份")
        }
        AddHandler BtnRestore.Click, Sub(sender As Object, e As EventArgs) RestoreBackupEntry(Entry)
        Dim BtnFolder As New MyIconButton With {
            .SvgIcon = "lucide/folder-open",
            .ToolTip = ModLang.L("打开所在文件夹")
        }
        AddHandler BtnFolder.Click, Sub(sender As Object, e As EventArgs) OpenBackupFolder(Entry)
        Dim BtnDelete As New MyIconButton With {
            .SvgIcon = "lucide/trash-2",
            .Theme = MyIconButton.Themes.Red,
            .ToolTip = ModLang.L("删除此备份")
        }
        AddHandler BtnDelete.Click, Sub(sender As Object, e As EventArgs) DeleteBackupEntry(Entry)
        Item.Buttons = New MyIconButton() {BtnRestore, BtnFolder, BtnDelete}
        Return Item
    End Function

    Private Sub OpenBackupFolder(Entry As ModWorldData.BackupEntry)
        Try
            Dim Folder As String = IO.Path.GetDirectoryName(Entry.FilePath)
            If String.IsNullOrEmpty(Folder) OrElse Not DirectoryUtils.Exists(Folder) Then
                Hint(ModLang.L("备份所在文件夹已不存在。"), HintType.Red)
                Return
            End If
            OpenExplorer(Folder)
        Catch ex As Exception
            Logger.Warn(ex, "打开备份文件夹失败")
        End Try
    End Sub

    Private Sub RestoreBackupEntry(Entry As ModWorldData.BackupEntry)
        Dim Folder As String = OverlayWorld
        If Folder Is Nothing Then Return
        If MyMsgBox(ModLang.L("确定要还原备份「") & Entry.Name & ModLang.L("」吗？") & vbCrLf &
                    ModLang.L("当前世界会先被自动备份一次（名字以「还原前」开头），然后被这份备份覆盖。"),
                    ModLang.L("还原备份"), ModLang.L("还原"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Dim InstanceName As String = If(Instance Is Nothing, "", Instance.Name)
        Hint(ModLang.L("正在后台还原世界…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Dim ErrorText As String = ""
            Dim Ok As Boolean = ModWorldData.RestoreBackup(Entry.FilePath, Folder, InstanceName, ErrorText)
            RunInUi(
            Sub()
                If Ok Then
                    Hint(ModLang.L("世界已还原。"), HintType.Green)
                    Refresh()
                    ShowBackupList(Folder)
                Else
                    MyMsgBox(If(String.IsNullOrEmpty(ErrorText), ModLang.L("还原失败。"), ErrorText),
                             ModLang.L("还原备份"), ModLang.L("确定"), "", IsWarn:=True)
                End If
            End Sub)
        End Sub, "Restore World Backup", ThreadPriority.BelowNormal)
    End Sub

    Private Sub DeleteBackupEntry(Entry As ModWorldData.BackupEntry)
        If MyMsgBox(ModLang.L("确定要删除备份「") & Entry.Name & ModLang.L("」吗？") & vbCrLf &
                    ModLang.L("备份会被移入回收站。"),
                    ModLang.L("删除备份"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            ModWorldData.DeleteBackup(Entry.FilePath)
            Hint(ModLang.L("备份已删除。"), HintType.Green)
            Dim Folder As String = OverlayWorld
            If Folder IsNot Nothing Then ShowBackupList(Folder)
        Catch ex As Exception
            Logger.Error(ex, "删除备份失败")
            Hint(ModLang.L("删除备份失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

#End Region

End Class
