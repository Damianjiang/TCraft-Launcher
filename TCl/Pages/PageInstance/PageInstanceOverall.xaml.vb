Public Class PageInstanceOverall

    Private IsLoad As Boolean = False
    Private Sub PageSetupLaunch_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded

        '重复加载部分
        PanBack.ScrollToHome()

        '更新设置
        Reload()

        '非重复加载部分
        If IsLoad Then Return
        IsLoad = True
        PanDisplay.TriggerForceResize()

    End Sub

    Public InstanceHeaderItem As MyListItem
    ''' <summary>
    ''' 确保当前页面上的信息已正确显示。
    ''' </summary>
    Private Sub Reload()
        AniControlEnabled += 1
        RefreshRecent()

        '刷新设置项目
        ComboDisplayType.SelectedIndex = ReadIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "DisplayType", McInstanceCardType.Auto)
        BtnDisplayStar.Text = If(PageInstanceLeft.Instance.IsStar, ModLang.L("从收藏夹中移除"), ModLang.L("加入收藏夹"))
        BtnFolderMods.Visibility = If(PageInstanceLeft.Instance.Modable, Visibility.Visible, Visibility.Collapsed)
        '刷新版本显示
        PanDisplayItem.Children.Clear()
        InstanceHeaderItem = PageInstanceLeft.Instance.ToListItem().Init()
        InstanceHeaderItem.IsHitTestVisible = False
        PanDisplayItem.Children.Add(InstanceHeaderItem)
        FrmMain.PageNameRefresh()
        '刷新版本图标
        ComboDisplayLogo.SelectedIndex = 0
        Dim Logo As String = ReadIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "Logo", "")
        Dim LogoCustom As Boolean = ReadIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "LogoCustom", "False")
        If LogoCustom Then
            For Each Selection As MyComboBoxItem In ComboDisplayLogo.Items
                If Selection.Tag = Logo OrElse (Selection.Tag = "TCL\Logo.png" AndAlso Logo.EndsWithF("TCL\Logo.png")) Then
                    ComboDisplayLogo.SelectedItem = Selection
                    Exit For
                End If
            Next
        End If

        AniControlEnabled -= 1
    End Sub

#Region "卡片：个性化"

    '版本分类
    Private Sub ComboDisplayType_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles ComboDisplayType.SelectionChanged
        If Not (IsLoad AndAlso AniControlEnabled = 0) Then Return
        If ComboDisplayType.SelectedIndex <> 1 Then
            '改为不隐藏
            Try
                '若设置分类为可安装 Mod，则显示正常的 Mod 管理页面
                WriteIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "DisplayType", ComboDisplayType.SelectedIndex)
                PageInstanceLeft.Instance.DisplayType = ReadIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "DisplayType", McInstanceCardType.Auto)
                FrmInstanceLeft.RefreshModDisabled()

                WriteIni(McFolderSelected & "TCL.ini", "InstanceCache", "") '要求刷新缓存
                LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
            Catch ex As Exception
                Logger.Error(ex, $"修改版本分类失败（{PageInstanceLeft.Instance.Name}）")
            End Try
            Reload() '更新 “打开 Mod 文件夹” 按钮
        Else
            '改为隐藏
            Try
                If Not Settings.Get(Of Boolean)("HintHide") Then
                    If MyMsgBox(ModLang.L("确认要从版本列表中隐藏该版本吗？隐藏该版本后，它将不再出现于 TCL 显示的版本列表中。") & vbCrLf & ModLang.L("此后，在版本列表页面按下 F11 才可以查看被隐藏的版本。"), ModLang.L("隐藏版本提示"),, ModLang.L("取消")) <> 1 Then
                        ComboDisplayType.SelectedIndex = 0
                        Return
                    End If
                    Settings.Set("HintHide", True)
                End If
                WriteIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "DisplayType", McInstanceCardType.Hidden)
                WriteIni(McFolderSelected & "TCL.ini", "InstanceCache", "") '要求刷新缓存
                LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
            Catch ex As Exception
                Logger.Error(ex, $"隐藏版本 {PageInstanceLeft.Instance.Name} 失败")
            End Try
        End If
    End Sub

    '更改描述
    Private Sub BtnDisplayDesc_Click(sender As Object, e As EventArgs) Handles BtnDisplayDesc.Click
        Try
            Dim OldInfo As String = ReadIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "CustomInfo")
            Dim NewInfo As String = MyMsgBoxInput(ModLang.L("更改描述"), ModLang.L("修改版本的描述文本，留空则使用 TCL 的默认描述。"), OldInfo, New ObjectModel.Collection(Of Validate), ModLang.L("默认描述"))
            If NewInfo IsNot Nothing AndAlso OldInfo <> NewInfo Then WriteIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "CustomInfo", NewInfo)
            PageInstanceLeft.Instance = New McInstance(PageInstanceLeft.Instance.Name).Load()
            Reload()
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
        Catch ex As Exception
            Logger.Error(ex, $"版本 {PageInstanceLeft.Instance.Name} 描述更改失败", LogBehavior.Alert)
        End Try
    End Sub

    '重命名版本
    Private Sub BtnDisplayRename_Click(sender As Object, e As EventArgs) Handles BtnDisplayRename.Click
        Try
            If McInstanceLock.IsLocked(PageInstanceLeft.Instance) Then
                Hint(ModLang.L("该版本正在被修改，请稍后再重命名！"), HintType.Red)
                Return
            End If
            '确认输入的新名称
            Dim OldName As String = PageInstanceLeft.Instance.Name
            '修改此部分的同时修改快速安装的版本名检测*
            Dim NewName As String = MyMsgBoxInput(ModLang.L("重命名版本"), "", OldName, New ObjectModel.Collection(Of Validate) From {
                New ValidateFolderName(McFolderSelected & "versions", IgnoreList:=New List(Of String) From {OldName})
            })
            If String.IsNullOrWhiteSpace(NewName) Then Return
            Dim NewInstance = PageInstanceLeft.Instance.Rename(NewName)
            '刷新与提示
            Hint(ModLang.L("重命名成功！"), HintType.Green)
            PageInstanceLeft.Instance = NewInstance
            If Not IsNothing(McInstanceSelected) AndAlso McInstanceSelected.Equals(PageInstanceLeft.Instance) Then WriteIni(McFolderSelected & "TCL.ini", "Version", NewName)
            Reload()
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
        Catch ex As Exception
            Logger.Error(ex, "重命名版本失败", LogBehavior.Alert)
        End Try
    End Sub

    '版本图标
    Private Sub ComboDisplayLogo_SelectionChanged() Handles ItemDisplayLogoCustom.PreviewMouseLeftButtonUp, ComboDisplayLogo.SelectionChanged
        If Not (IsLoad AndAlso AniControlEnabled = 0) Then Return
        '选择 自定义 时修改图片
        Try
            If ComboDisplayLogo.SelectedItem Is ItemDisplayLogoCustom Then
                Dim FileName As String = Dialogs.SelectFile("选择图片", False, filter:={({"png", "jpeg", "jpg", "gif", "webp"}, "常用图片文件")}).FirstOrDefault()
                If String.IsNullOrEmpty(FileName) Then
                    Reload() '还原选项
                    Return
                End If
                FileUtils.Copy(FileName, PageInstanceLeft.Instance.PathVersion & "TCL\Logo.png")
            Else
                FileUtils.Delete(PageInstanceLeft.Instance.PathVersion & "TCL\Logo.png")
            End If
        Catch ex As Exception
            Logger.Error(ex, $"更改自定义版本图标失败（{PageInstanceLeft.Instance.Name}）")
        End Try
        '进行更改
        Try
            Dim NewLogo As String = ComboDisplayLogo.SelectedItem.Tag
            WriteIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "Logo", NewLogo)
            WriteIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "LogoCustom", Not NewLogo = "")
            '刷新显示
            WriteIni(McFolderSelected & "TCL.ini", "InstanceCache", "") '要求刷新缓存
            PageInstanceLeft.Instance = New McInstance(PageInstanceLeft.Instance.Name).Load()
            Reload()
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
        Catch ex As Exception
            Logger.Error(ex, $"更改版本图标失败（{PageInstanceLeft.Instance.Name}）")
        End Try
    End Sub

    '收藏夹
    Private Sub BtnDisplayStar_Click(sender As Object, e As EventArgs) Handles BtnDisplayStar.Click
        Try
            WriteIni(PageInstanceLeft.Instance.PathVersion & "TCL\Setup.ini", "IsStar", Not PageInstanceLeft.Instance.IsStar)
            PageInstanceLeft.Instance = New McInstance(PageInstanceLeft.Instance.Name).Load()
            Reload()
            McInstanceListForceRefresh = True
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
        Catch ex As Exception
            Logger.Error(ex, $"版本 {PageInstanceLeft.Instance.Name} 收藏状态更改失败", LogBehavior.Alert)
        End Try
    End Sub

#End Region

#Region "卡片：快捷方式"

    '版本文件夹
    Private Sub BtnFolderVersion_Click() Handles BtnFolderVersion.Click
        OpenInstanceFolder(PageInstanceLeft.Instance)
    End Sub
    Public Shared Sub OpenInstanceFolder(Instance As McInstance)
        OpenExplorer(Instance.PathVersion)
    End Sub

    '存档文件夹
    Private Sub BtnFolderSaves_Click() Handles BtnFolderSaves.Click
        Dim Folder As String = PageInstanceLeft.Instance.PathIndie & "saves\"
        DirectoryUtils.Create(Folder)
        OpenExplorer(Folder)
    End Sub

    'Mod 文件夹
    Private Sub BtnFolderMods_Click() Handles BtnFolderMods.Click
        Dim Folder As String = PageInstanceLeft.Instance.PathIndie & "mods\"
        DirectoryUtils.Create(Folder)
        OpenExplorer(Folder)
    End Sub

    '截图文件夹
    Private Sub BtnFolderScreen_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnFolderScreen.Click
        Dim Folder As String = PageInstanceLeft.Instance.PathIndie & "screenshots\"
        DirectoryUtils.Create(Folder)
        OpenExplorer(Folder)
    End Sub

#End Region

#Region "卡片：管理"

    '导出启动脚本
    Private Sub BtnManageScript_Click() Handles BtnManageScript.Click
        Try
            '弹窗要求指定脚本的保存位置
            Dim SavePath As String = Dialogs.SaveFile("选择脚本保存位置", "启动 " & PageInstanceLeft.Instance.Name & ".bat", filter:={("bat", "批处理文件")})
            If SavePath Is Nothing Then Return
            '检查中断（等玩家选完弹窗指不定任务就结束了呢……）
            If McLaunchLoader.State = LoadState.Loading Then
                Hint(ModLang.L("请在当前启动任务结束后再试！"), HintType.Red)
                Return
            End If
            '生成脚本
            If McLaunchStart(New McLaunchOptions With {.SaveBatch = SavePath, .Instance = PageInstanceLeft.Instance}) Then
                If Settings.Get(Of McLoginType)("LoginType") = McLoginType.Legacy Then
                    Hint(ModLang.L("正在导出启动脚本……"))
                Else
                    Hint(ModLang.L("正在导出启动脚本……（注意，使用脚本启动可能会导致登录失效！）"))
                End If
            End If
        Catch ex As Exception
            Logger.Error(ex, $"导出启动脚本失败（{PageInstanceLeft.Instance.Name}）", LogBehavior.Alert)
        End Try
    End Sub

    '补全文件
    Private Sub BtnManageCheck_Click(sender As Object, e As EventArgs) Handles BtnManageCheck.Click
        Try
            If McInstanceLock.IsLocked(PageInstanceLeft.Instance) Then
                Hint(ModLang.L("该版本正在被修改，请稍后再补全文件！"), HintType.Red)
                Return
            End If
            '忽略文件检查提示
            If ShouldIgnoreFileCheck(PageInstanceLeft.Instance) Then
                Hint(ModLang.L("请先关闭 [版本设置 → 设置 → 高级启动选项 → 关闭文件校验]，然后再尝试补全文件！"), HintType.Blue)
                Return
            End If
            '重复任务检查
            For Each OngoingLoader In LoaderTaskbar
                If OngoingLoader.Name <> PageInstanceLeft.Instance.Name & " 文件补全" Then Continue For
                Hint(ModLang.L("正在处理中，请稍候！"), HintType.Red)
                Return
            Next
            '启动
            Dim Loader As New LoaderCombo(Of String)(PageInstanceLeft.Instance.Name & " 文件补全", DlClientFix(PageInstanceLeft.Instance, True, False))
            Loader.OnStateChanged =
            Sub()
                Select Case Loader.State
                    Case LoadState.Finished
                        Hint(Loader.Name & ModLang.L("成功！"), HintType.Green)
                    Case LoadState.Failed
                        Hint(Loader.Name & ModLang.L("失败：") & Loader.Error.GetDisplay(False), HintType.Red)
                    Case LoadState.Canceled
                        Hint(Loader.Name & ModLang.L("已取消！"), HintType.Blue)
                End Select
            End Sub
            Loader.Start(PageInstanceLeft.Instance.Name)
            LoaderTaskbarAdd(Loader)
            FrmMain.BtnExtraDownload.ShowRefresh()
            FrmMain.BtnExtraDownload.Ribble()
        Catch ex As Exception
            Logger.Error(ex, $"尝试补全文件失败（{PageInstanceLeft.Instance.Name}）", LogBehavior.Alert)
        End Try
    End Sub

    '删除版本
    '修改此代码时，同时修改 PageSelectRight 中的代码
    Private Sub BtnManageDelete_Click(sender As Object, e As EventArgs) Handles BtnManageDelete.Click
        Try
            If McInstanceLock.IsLocked(PageInstanceLeft.Instance) Then
                Hint(ModLang.L("该版本正在被修改，请稍后再删除！"), HintType.Red)
                Return
            End If
            Dim IsShiftPressed As Boolean = My.Computer.Keyboard.ShiftKeyDown
            Dim IsHintIndie As Boolean = PageInstanceLeft.Instance.State <> McInstance.McInstanceState.Error AndAlso PageInstanceLeft.Instance.PathIndie <> McFolderSelected
            Dim SavesFolder = DirectoryUtils.GetInfo(PageInstanceLeft.Instance.PathIndie & "saves\")
            Dim SaveEntries = If(IsHintIndie AndAlso SavesFolder.Exists, SavesFolder.EnumerateDirectories.OrderByDescending(Function(Save) Save.LastWriteTime).Select(Function(Save) $"{Save.Name}（上次修改：{StringUtils.FormatTimeSpan(Save.LastWriteTime - Date.Now, False)}）").ToList, New List(Of String))
            Dim AskPrefix As String = If(IsShiftPressed, ModLang.L("你确定要永久删除版本 "), ModLang.L("你确定要删除版本 "))
            Select Case MyMsgBox(AskPrefix & PageInstanceLeft.Instance.Name & ModLang.L(" 吗？") &
                        If(IsHintIndie, vbCrLf & ModLang.L("该版本对应的存档、资源包、Mod 等文件也将被一并删除！") &
                            If(SaveEntries.Any, vbCrLf & vbCrLf & ModLang.L("这会删除以下存档：") & vbCrLf & "· " & SaveEntries.Join(vbCrLf & "· "), ""), ""),
                        ModLang.L("版本删除确认"), , ModLang.L("取消"),, IsHintIndie OrElse IsShiftPressed)
                Case 1
                    PageInstanceLeft.Instance.ResetSettingsCache()
                    DirectoryUtils.Delete(PageInstanceLeft.Instance.PathVersion, Not IsShiftPressed)
                    Hint(ModLang.L("已删除版本 ") & PageInstanceLeft.Instance.Name & ModLang.L("！"), HintType.Green)
                Case 2
                    Return
            End Select
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
            FrmMain.PageBack()
        Catch ex As OperationCanceledException
            Logger.Warn(ex, $"删除版本 {PageInstanceLeft.Instance.Name} 被主动取消")
        Catch ex As Exception
            Logger.Error(ex, $"删除版本 {PageInstanceLeft.Instance.Name} 失败", LogBehavior.Alert)
        End Try
    End Sub

#End Region

    ''' <summary>
    ''' 刷新「最近游玩」摘要与「结束游戏」按钮（对齐 LumilioCL 概览的最近游玩 / 结束游戏）。
    ''' </summary>
    Private Sub RefreshRecent()
        Try
            PanRecentList.Children.Clear()
            If PageInstanceLeft.Instance Is Nothing Then Return
            RefreshInstanceSize()
            Dim Sessions = ModInstanceHistory.LoadSessions(PageInstanceLeft.Instance).Take(3).ToList()
            If Sessions.Count = 0 Then
                Dim Empty As New TextBlock With {
                    .Text = ModLang.L("还没有游玩记录。启动一次游戏后，这里会显示最近三次的开始时间与时长。"),
                    .FontSize = 13, .Opacity = 0.6, .TextWrapping = TextWrapping.Wrap, .Margin = New Thickness(0, 0, 0, 4)}
                Empty.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
                PanRecentList.Children.Add(Empty)
            Else
                For Each Session In Sessions
                    Dim Line As New TextBlock With {
                        .Text = Session.StartLocal.ToString("yyyy'/'MM'/'dd HH':'mm") & " · " & Session.DurationText &
                            " · " & Session.ResultText &
                            If(String.IsNullOrEmpty(Session.Player), "", " · " & Session.Player),
                        .FontSize = 13, .Margin = New Thickness(0, 0, 0, 7), .TextWrapping = TextWrapping.Wrap}
                    Line.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
                    PanRecentList.Children.Add(Line)
                Next
            End If
            Dim Running As Boolean = False
            Try
                Running = McLaunchProcess IsNot Nothing AndAlso Not McLaunchProcess.HasExited
            Catch ex As Exception
                Running = False
            End Try
            BtnKillGame.Visibility = If(Running, Visibility.Visible, Visibility.Collapsed)
        Catch ex As Exception
            Logger.Warn(ex, "刷新最近游玩摘要失败")
        End Try
    End Sub

    Private SizeCacheKey As String = ""
    ''' <summary>后台统计实例目录占用，同一实例只算一次。</summary>
    Private Sub RefreshInstanceSize()
        If PageInstanceLeft.Instance Is Nothing Then Return
        Dim Key As String = PageInstanceLeft.Instance.PathIndie
        If SizeCacheKey = Key Then Return
        SizeCacheKey = Key
        LabInstanceSize.Text = ModLang.L("占用空间：计算中…")
        Dim Target As McInstance = PageInstanceLeft.Instance
        RunInNewThread(
        Sub()
            Dim Bytes As Long = ModInstanceTools.FolderSize(Target.PathIndie)
            RunInUi(
            Sub()
                If PageInstanceLeft.Instance IsNot Nothing AndAlso PathUtils.IsSame(PageInstanceLeft.Instance.PathIndie, Target.PathIndie) Then
                    LabInstanceSize.Text = ModLang.L("占用空间：") & StringUtils.FormatByteSize(Bytes)
                End If
            End Sub)
        End Sub, "Instance Size", ThreadPriority.Lowest)
    End Sub

    Private Sub BtnRecentHistory_Click(sender As Object, e As EventArgs) Handles BtnRecentHistory.Click
        FrmInstanceHistory?.Reload()
        FrmMain.PageChange(New FormMain.PageStackData With {.Page = FormMain.PageType.InstanceSetup},
                           FormMain.PageSubType.InstanceHistory)
    End Sub

    ''' <summary>结束本启动器启动的游戏进程。</summary>
    Private Sub BtnKillGame_Click(sender As Object, e As EventArgs) Handles BtnKillGame.Click
        Dim Running As Boolean = False
        Try
            Running = McLaunchProcess IsNot Nothing AndAlso Not McLaunchProcess.HasExited
        Catch ex As Exception
            Running = False
        End Try
        If Not Running Then
            Hint(ModLang.L("当前没有由本启动器启动的游戏进程。"), HintType.Blue)
            RefreshRecent()
            Return
        End If
        If MyMsgBox(ModLang.L("确定要结束正在运行的游戏吗？未保存的游戏进度会丢失。"),
                    ModLang.L("结束游戏"), ModLang.L("结束"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            McLaunchProcess.Kill()
            Hint(ModLang.L("已请求结束游戏进程。"), HintType.Green)
            RefreshRecent()
        Catch ex As Exception
            Logger.Error(ex, "结束游戏进程失败")
            Hint(ModLang.L("结束游戏失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

End Class
