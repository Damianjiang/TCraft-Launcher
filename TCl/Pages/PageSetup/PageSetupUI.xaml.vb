Public Class PageSetupUI

    Private Sub PageSetupUI_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        '重复加载部分
        PanBack.ScrollToHome()
        ThemeCheckAll(True)
        If ThemeDontClick <> 0 Then
            Dim NewText As String = Nothing
            Select Case ThemeDontClick
                Case 1
                    NewText = "眼瞎白"
                Case 2
                    NewText = "真·七曜彩"
            End Select
            '主题单选项已并入 WrapPanel，改为递归遍历逻辑树（原直接子项遍历早已失效）
            RenameThemeRadios(PanLauncherTheme, NewText)
        End If

        AniControlEnabled += 1
        Refresh()
        AniControlEnabled -= 1

        '非重复加载部分
        Static Reloaded As Boolean = False
        If Reloaded Then Return
        Reloaded = True

        SliderLoad()

    End Sub
    Public Sub Refresh()
        Try
            SettingService.RefreshSettings(Me)
            BackgroundRefresh(False, False)

            '标题栏
            CheckLogoLeft.Visibility = If(RadioLogoType0.Checked, Visibility.Visible, Visibility.Collapsed)
            PanLogoText.Visibility = If(RadioLogoType2.Checked, Visibility.Visible, Visibility.Collapsed)
            PanLogoChange.Visibility = If(RadioLogoType3.Checked, Visibility.Visible, Visibility.Collapsed)

            '背景音乐
            MusicRefreshUI()

            '背景视频
            VideoRefreshUI()
            UpdateBackgroundCardUI()
        Catch ex As NullReferenceException
            Logger.Error(ex, "个性化设置项存在异常，已被自动重置", LogBehavior.Alert)
            Reset()
        Catch ex As Exception
            Logger.Error(ex, "重载个性化设置时出错")
        End Try
    End Sub
    Public Sub Reset()
        Try
            SettingService.ResetSettings(Me)
            Logger.Info("已初始化个性化设置！")
            Hint(ModLang.L("已初始化个性化设置"), HintType.Green, False)
        Catch ex As Exception
            Logger.Error(ex, "初始化个性化设置失败", LogBehavior.Alert)
        End Try
        Refresh()
    End Sub

    '背景图片
    Private Sub BtnUIBgOpen_Click(sender As Object, e As EventArgs) Handles BtnBackgroundOpen.Click
        OpenExplorer(Paths.Base & "TCL\Pictures\")
    End Sub
    Private Sub BtnBackgroundRefresh_Click(sender As Object, e As EventArgs) Handles BtnBackgroundRefresh.Click
        BackgroundRefresh(True, True)
    End Sub
    '背景卡片状态
    Private BackgroundImageShow As Boolean = False
    Private BackgroundImageCount As Integer = 0
    Private BackgroundVideoCount As Integer = 0

    Public Sub BackgroundRefreshUI(Show As Boolean, Count As Integer)
        If IsNothing(PanBackgroundOpacity) Then Return
        BackgroundImageShow = Show
        BackgroundImageCount = Count
        If Show Then
            PanBackgroundOpacity.Visibility = Visibility.Visible
            PanBackgroundBlur.Visibility = Visibility.Visible
            PanBackgroundSuit.Visibility = Visibility.Visible
            PanBackgroundRotate.Visibility = Visibility.Visible
            BtnBackgroundClear.Visibility = Visibility.Visible
        Else
            PanBackgroundOpacity.Visibility = Visibility.Collapsed
            PanBackgroundBlur.Visibility = Visibility.Collapsed
            PanBackgroundSuit.Visibility = Visibility.Collapsed
            PanBackgroundRotate.Visibility = Visibility.Collapsed
            BtnBackgroundClear.Visibility = Visibility.Collapsed
        End If
        UpdateBackgroundCardUI()
    End Sub

    ' <summary>
    ' 按背景类型（UiBackgroundType：0=图片，1=视频）切换卡片内容显隐并更新标题。
    ' 图片与视频只能选其一作为背景展示。
    ' </summary>
    Public Sub UpdateBackgroundCardUI()
        Try
            If IsNothing(PanBackgroundImage) OrElse IsNothing(PanVideoSettings) Then Return
            Dim IsVideo As Boolean = Settings.Get(Of Integer)("UiBackgroundType") = 1
            PanBackgroundImage.Visibility = If(IsVideo, Visibility.Collapsed, Visibility.Visible)
            PanVideoSettings.Visibility = If(IsVideo, Visibility.Visible, Visibility.Collapsed)
            If IsVideo Then
                Dim ShowV As Boolean = BackgroundVideoCount > 0
                PanVideoVolume.Visibility = If(ShowV, Visibility.Visible, Visibility.Collapsed)
                PanVideoDetail.Visibility = If(ShowV, Visibility.Visible, Visibility.Collapsed)
                BtnVideoClear.Visibility = If(ShowV, Visibility.Visible, Visibility.Collapsed)
                CardBackground.Title = If(ShowV, ModLang.L("背景视频（") & BackgroundVideoCount & ModLang.L(" 个）"), ModLang.L("背景视频"))
            Else
                CardBackground.Title = If(BackgroundImageShow, ModLang.L("背景图片（") & BackgroundImageCount & ModLang.L(" 张）"), ModLang.L("背景图片"))
            End If
            CardBackground.TriggerForceResize()
        Catch ex As Exception
            Logger.Warn(ex, "更新背景卡片 UI 失败")
        End Try
    End Sub
    Private Sub BtnBackgroundClear_Click(sender As Object, e As EventArgs) Handles BtnBackgroundClear.Click
        If MyMsgBox(ModLang.L("即将删除背景图片文件夹中的所有文件。") & vbCrLf & ModLang.L("此操作不可撤销，是否确定？"), ModLang.L("警告"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
            DirectoryUtils.Delete(Paths.Base & "TCL\Pictures")
            BackgroundRefresh(False, True)
            Hint(ModLang.L("背景图片已清空！"), HintType.Green)
        End If
    End Sub
    ''' <summary>
    ''' 刷新背景图片及设置页 UI。
    ''' </summary>
    ''' <param name="IsHint">是否显示刷新提示。</param>
    ''' <param name="Refresh">是否刷新图片显示。</param>
    Public Shared Sub BackgroundRefresh(IsHint As Boolean, Refresh As Boolean)
        Try

            '获取可用的图片文件
            DirectoryUtils.Create(Paths.Base & "TCL\Pictures\")
            Dim Pic As New List(Of String)
            For Each File In DirectoryUtils.EnumerateFiles(Paths.Base & "TCL\Pictures\", True)
                Dim Extension As String = PathUtils.GetExtension(File)
                If Extension <> "ini" AndAlso Extension <> "db" Then Pic.Add(File) '文件夹可能会被加入 .ini 和 thumbs.db
            Next
            '加载
            If Not Pic.Any() Then
                If Refresh Then
                    If FrmMain.ImgBack.Visibility = Visibility.Collapsed Then
                        If IsHint Then Hint(ModLang.L("未检测到可用背景图片！"), HintType.Red)
                    Else
                        FrmMain.ImgBack.Visibility = Visibility.Collapsed
                        If IsHint Then Hint(ModLang.L("背景图片已清除！"), HintType.Green)
                    End If
                End If
                If Not IsNothing(FrmSetupUI) Then FrmSetupUI.BackgroundRefreshUI(False, 0)
            Else
                If Refresh Then
                    Dim Address As String = RandomOne(Pic)
                    Try
                        Logger.Info($"加载背景图片：{Address}")
                        FrmMain.ImgBack.Background = MyBitmap.CreateImageBrush(Address, CInt(SystemParameters.PrimaryScreenWidth))
                        FrmMain.ImgBack.Visibility = Visibility.Visible
                        FrmMain.UpdateBackgroundAndTitleBar()
                        'HMCL Monet 式取色：从新背景提取主题色，整套 UI 颜色跟随壁纸（可在设置中关闭）
                        ModSecret.ApplyWallpaperTheme(FrmMain.ImgBack.Background)
                        If IsHint Then Hint(ModLang.L("背景图片已刷新：") & PathUtils.GetLastPart(Address), HintType.Green, False)
                    Catch ex As Exception
                        If ex.Message.Contains("参数无效") Then
                            Logger.Error($"刷新背景图片失败，该图片文件可能并非标准格式。{vbCrLf}你可以尝试使用画图打开该文件并重新保存，这会让图片变为标准格式。{vbCrLf}文件：{Address}", LogBehavior.Alert)
                        Else
                            Logger.Error(ex, $"刷新背景图片失败（{Address}）", LogBehavior.Alert)
                        End If
                    End Try
                End If
                If Not IsNothing(FrmSetupUI) Then FrmSetupUI.BackgroundRefreshUI(True, Pic.Count)
            End If

        Catch ex As Exception
            Logger.Error(ex, "刷新背景图片时出现未知错误")
        End Try
    End Sub

    '顶部栏
    Private Sub BtnLogoChange_Click(sender As Object, e As EventArgs) Handles BtnLogoChange.Click
        Dim FileName As String = Dialogs.SelectFile(ModLang.L("选择图片"), False, filter:={({"png", "jpg", "jpeg", "gif", "webp"}, ModLang.L("常用图片文件"))}).FirstOrDefault()
        If String.IsNullOrEmpty(FileName) Then Return
        Dim TargetPath As String = Paths.Base & "TCL\Logo.png"
        Try
            '复制文件
            FileUtils.Copy(FileName, TargetPath)
            '设置当前显示
            FrmMain.ImageTitleLogo.Source = Nothing '防止因为 Source 属性前后的值相同而不更新
            FrmMain.ImageTitleLogo.Source = TargetPath
        Catch ex As Exception
            If ex.Message.Contains("参数无效") Then
                Logger.Error($"改变标题栏图片失败，该图片文件可能并非标准格式。{vbCrLf}你可以尝试使用画图打开该文件并重新保存，这会让图片变为标准格式。{vbCrLf}文件：{TargetPath}", LogBehavior.Alert)
            Else
                Logger.Error(ex, "设置标题栏图片失败", LogBehavior.Alert)
            End If
            FrmMain.ImageTitleLogo.Source = Nothing
        End Try
    End Sub
    Private Sub RadioLogoType3_Check(sender As Object, e As RouteEventArgs) Handles RadioLogoType3.PreviewCheck
        If Not (AniControlEnabled = 0 AndAlso e.RaiseByMouse) Then Return
Refresh:
        '已有图片则不再选择
        Dim TargetPath As String = Paths.Base & "TCL\Logo.png"
        If FileUtils.Exists(TargetPath) Then
            Try
                FrmMain.ImageTitleLogo.Source = Nothing '防止因为 Source 属性前后的值相同而不更新
                FrmMain.ImageTitleLogo.Source = TargetPath
            Catch ex As Exception
                If ex.Message.Contains("参数无效") Then
                    Logger.Error($"改变标题栏图片失败，该图片文件可能并非标准格式。{vbCrLf}你可以尝试使用画图打开该文件并重新保存，这会让图片变为标准格式。{vbCrLf}文件：{TargetPath}", LogBehavior.Alert)
                Else
                    Logger.Error(ex, "调整标题栏图片失败", LogBehavior.Alert)
                End If
                FrmMain.ImageTitleLogo.Source = Nothing
                e.Handled = True
                Try
                    FileUtils.Delete(TargetPath)
                Catch exx As Exception
                    Logger.Error(exx, "清理错误的标题栏图片失败", LogBehavior.Alert)
                End Try
            End Try
            Return
        End If
        '没有图片则要求选择
        Dim FileName As String = Dialogs.SelectFile(ModLang.L("选择图片"), False, filter:={({"png", "jpeg", "jpg", "gif", "webp"}, ModLang.L("常用图片文件"))}).FirstOrDefault()
        If String.IsNullOrEmpty(FileName) Then
            FrmMain.ImageTitleLogo.Source = Nothing
            e.Handled = True
        Else
            Try
                FileUtils.Copy(FileName, TargetPath)
                GoTo Refresh
            Catch ex As Exception
                Logger.Error(ex, "复制标题栏图片失败", LogBehavior.Alert)
            End Try
        End If
    End Sub
    Private Sub BtnLogoDelete_Click(sender As Object, e As EventArgs) Handles BtnLogoDelete.Click
        Try
            FileUtils.Delete(Paths.Base & "TCL\Logo.png")
            RadioLogoType1.SetChecked(True, True)
            Hint(ModLang.L("标题栏图片已清空！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "清空标题栏图片失败", LogBehavior.Alert)
        End Try
    End Sub

    '背景音乐
    Private Sub BtnMusicOpen_Click(sender As Object, e As EventArgs) Handles BtnMusicOpen.Click
        OpenExplorer(Paths.Base & "TCL\Musics\")
    End Sub
    Private Sub BtnMusicRefresh_Click(sender As Object, e As EventArgs) Handles BtnMusicRefresh.Click
        MusicRefreshPlay(True)
    End Sub
    Public Sub MusicRefreshUI()
        If PanBackgroundOpacity Is Nothing Then Return
        If MusicAllList.Any Then
            PanMusicVolume.Visibility = Visibility.Visible
            PanMusicDetail.Visibility = Visibility.Visible
            BtnMusicClear.Visibility = Visibility.Visible
            CardMusic.Title = ModLang.L("背景音乐（") &
                DirectoryUtils.EnumerateFiles(Paths.Base & "TCL\Musics\", True).Count(Function(f) Not {"ini", "jpg", "txt", "cfg", "lrc", "db", "png"}.Contains(PathUtils.GetExtension(f))) &
                " 首）"
        Else
            PanMusicVolume.Visibility = Visibility.Collapsed
            PanMusicDetail.Visibility = Visibility.Collapsed
            BtnMusicClear.Visibility = Visibility.Collapsed
            CardMusic.Title = ModLang.L("背景音乐")
        End If
        CardMusic.TriggerForceResize()
    End Sub
    Private Sub BtnMusicClear_Click(sender As Object, e As EventArgs) Handles BtnMusicClear.Click
        If MyMsgBox(ModLang.L("即将删除背景音乐文件夹中的所有文件。") & vbCrLf & ModLang.L("此操作不可撤销，是否确定？"), ModLang.L("警告"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
            RunInThread(
            Sub()
                Hint(ModLang.L("正在删除背景音乐……"))
                '停止播放音乐
                MusicStopAll()
                MusicWaitingList = New List(Of String)
                MusicAllList = New List(Of String)
                Thread.Sleep(200)
                '删除文件
                Try
                    DirectoryUtils.Delete(Paths.Base & "TCL\Musics")
                    Hint(ModLang.L("背景音乐已删除！"), HintType.Green)
                Catch ex As Exception
                    Logger.Error(ex, "删除背景音乐失败", LogBehavior.Alert)
                End Try
                Try
                    DirectoryUtils.Create(Paths.Base & "TCL\Musics\")
                    RunInUi(Sub() MusicRefreshPlay(False))
                Catch ex As Exception
                    Logger.Error(ex, "重建背景音乐文件夹失败", LogBehavior.Alert)
                End Try
            End Sub)
        End If
    End Sub
    Private Sub CheckMusicStart_Change() Handles CheckMusicStart.Change
        If AniControlEnabled <> 0 Then Return
        If CheckMusicStart.Checked Then CheckMusicStop.Checked = False
    End Sub
    Private Sub CheckMusicStop_Change() Handles CheckMusicStop.Change
        If AniControlEnabled <> 0 Then Return
        If CheckMusicStop.Checked Then CheckMusicStart.Checked = False
    End Sub

    '背景视频
    Private Sub BtnVideoOpen_Click(sender As Object, e As EventArgs) Handles BtnVideoOpen.Click
        OpenExplorer(ModVideo.VideoFolder())
    End Sub
    Private Sub BtnVideoRefresh_Click(sender As Object, e As EventArgs) Handles BtnVideoRefresh.Click
        VideoRefreshPlay(True)
    End Sub
    Private Sub BtnVideoClear_Click(sender As Object, e As EventArgs) Handles BtnVideoClear.Click
        If MyMsgBox(ModLang.L("即将删除背景视频文件夹中的所有文件。") & vbCrLf & ModLang.L("此操作不可撤销，是否确定？"), ModLang.L("警告"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
            VideoClear()
        End If
    End Sub
    '自定义字体
    Private Sub BtnFontPick_Click(sender As Object, e As EventArgs) Handles BtnFontPick.Click
        ModFont.FontPickAndSave()
    End Sub
    Private Sub BtnFontRefresh_Click(sender As Object, e As EventArgs) Handles BtnFontRefresh.Click
        ModFont.FontRefresh(True)
    End Sub
    Private Sub BtnFontOpen_Click(sender As Object, e As EventArgs) Handles BtnFontOpen.Click
        OpenExplorer(ModFont.FontFolder())
    End Sub
    Private Sub BtnFontReset_Click(sender As Object, e As EventArgs) Handles BtnFontReset.Click
        ModFont.FontReset()
        Hint(ModLang.L("已恢复默认字体"), HintType.Green)
    End Sub
    ''' <summary>刷新「自定义字体」卡的预览与名称标签（ModFont 应用字体后调用）。</summary>
    Public Sub FontPreview(ff As System.Windows.Media.FontFamily)
        Try
            If LabFontPreview Is Nothing Then Return
            If ff IsNot Nothing Then
                LabFontPreview.FontFamily = ff
                Dim fam As String = Settings.Get(Of String)("UiFontFamily")
                LabFontName.Text = ModLang.L("当前：") & If(String.IsNullOrWhiteSpace(fam), ModLang.L("自定义字体"), fam)
            Else
                'WPF FontFamily 依赖属性不接受 Nothing，恢复默认用空族名 ""（= 系统默认字体）
                LabFontPreview.FontFamily = New System.Windows.Media.FontFamily("")
                LabFontName.Text = ModLang.L("当前：系统默认字体")
            End If
        Catch ex As Exception
            Logger.Warn(ex, "刷新字体预览失败")
        End Try
    End Sub
    Public Sub VideoRefreshUI()
        If IsNothing(PanVideoVolume) Then Return
        Try
            BackgroundVideoCount = DirectoryUtils.EnumerateFiles(ModVideo.VideoFolder(), True).Count(Function(f) {"mp4", "wmv", "mov", "mkv", "avi", "webm"}.Contains(PathUtils.GetExtension(f)))
        Catch
            BackgroundVideoCount = 0
        End Try
        UpdateBackgroundCardUI()
    End Sub

    '主题自定义
    ''' <summary>递归遍历逻辑树，把所有启用的主题单选项文本改为指定文案（愚人节彩蛋改名用）。</summary>
    Private Sub RenameThemeRadios(Target As DependencyObject, NewText As String)
        For Each Node As DependencyObject In LogicalTreeHelper.GetChildren(Target).OfType(Of DependencyObject)().ToArray()
            If TypeOf Node Is MyRadioBox AndAlso CType(Node, MyRadioBox).IsEnabled Then CType(Node, MyRadioBox).Text = NewText
            RenameThemeRadios(Node, NewText)
        Next
    End Sub

    Private Sub RadioLauncherTheme14_Change(sender As Object, e As RouteEventArgs) Handles RadioLauncherTheme14.Changed
        If RadioLauncherTheme14.Checked Then
            If LabLauncherHue.Visibility = Visibility.Visible Then Return
            LabLauncherHue.Visibility = Visibility.Visible
            SliderLauncherHue.Visibility = Visibility.Visible
            LabLauncherSat.Visibility = Visibility.Visible
            SliderLauncherSat.Visibility = Visibility.Visible
            LabLauncherDelta.Visibility = Visibility.Visible
            SliderLauncherDelta.Visibility = Visibility.Visible
            LabLauncherLight.Visibility = Visibility.Visible
            SliderLauncherLight.Visibility = Visibility.Visible
        Else
            If LabLauncherHue.Visibility = Visibility.Collapsed Then Return
            LabLauncherHue.Visibility = Visibility.Collapsed
            SliderLauncherHue.Visibility = Visibility.Collapsed
            LabLauncherSat.Visibility = Visibility.Collapsed
            SliderLauncherSat.Visibility = Visibility.Collapsed
            LabLauncherDelta.Visibility = Visibility.Collapsed
            SliderLauncherDelta.Visibility = Visibility.Collapsed
            LabLauncherLight.Visibility = Visibility.Collapsed
            SliderLauncherLight.Visibility = Visibility.Collapsed
        End If
        CardLauncher.TriggerForceResize()
    End Sub
    Private Sub HSL_Change() Handles SliderLauncherHue.Change, SliderLauncherLight.Change, SliderLauncherSat.Change, SliderLauncherDelta.Change
        If AniControlEnabled <> 0 OrElse SliderLauncherSat Is Nothing OrElse Not SliderLauncherSat.IsLoaded Then Return
        ThemeRefresh()
    End Sub

#Region "功能隐藏"

    Private Shared _HiddenForceShow As Boolean = False
    ''' <summary>
    ''' 是否强制显示被禁用的功能。
    ''' </summary>
    Public Shared Property HiddenForceShow As Boolean
        Get
            Return _HiddenForceShow
        End Get
        Set(value As Boolean)
            _HiddenForceShow = value
            HiddenRefresh()
        End Set
    End Property

    ''' <summary>
    ''' 更新功能隐藏带来的显示变化。
    ''' </summary>
    Public Shared Sub HiddenRefresh() Handles Me.Loaded
        If FrmMain.PanTitleSelect Is Nothing OrElse Not FrmMain.PanTitleSelect.IsLoaded Then Return
        Try
            '顶部栏
            If Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenPageDownload") AndAlso Settings.Get(Of Boolean)("UiHiddenPageLink") AndAlso Settings.Get(Of Boolean)("UiHiddenPageSetup") AndAlso Settings.Get(Of Boolean)("UiHiddenPageOther") Then
                '顶部栏已被全部隐藏
                FrmMain.PanTitleSelect.Visibility = Visibility.Collapsed
            Else
                '顶部栏未被全部隐藏
                FrmMain.PanTitleSelect.Visibility = Visibility.Visible
                FrmMain.BtnTitleSelect1.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenPageDownload"), Visibility.Collapsed, Visibility.Visible)
                FrmMain.BtnTitleSelect2.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenPageLink"), Visibility.Collapsed, Visibility.Visible)
                FrmMain.BtnTitleSelect4.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenPageOther"), Visibility.Collapsed, Visibility.Visible)
            End If
            '功能
            If FrmLaunchRight IsNot Nothing Then FrmLaunchRight.RefreshButtonsUI()
            If FrmSetupUI IsNot Nothing Then
                FrmSetupUI.CardSwitch.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenFunctionHidden"), Visibility.Collapsed, Visibility.Visible)
            End If
            '版本设置子页面
            If FrmInstanceLeft IsNot Nothing Then
                FrmInstanceLeft.ItemModify.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenFunctionInstanceModify"), Visibility.Collapsed, Visibility.Visible)
            End If
            '设置子页面
            If FrmSetupLeft IsNot Nothing Then
                FrmSetupLeft.ItemLaunch.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenSetupLaunch"), Visibility.Collapsed, Visibility.Visible)
                FrmSetupLeft.ItemUI.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenSetupUi"), Visibility.Collapsed, Visibility.Visible)
                FrmSetupLeft.ItemLink.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenSetupLink"), Visibility.Collapsed, Visibility.Visible)
                FrmSetupLeft.ItemSystem.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenSetupSystem"), Visibility.Collapsed, Visibility.Visible)
                '隐藏左边选择卡
                Dim AvaliableCount As Integer = 0
                If Not Settings.Get(Of Boolean)("UiHiddenSetupLaunch") Then AvaliableCount += 1
                If Not Settings.Get(Of Boolean)("UiHiddenSetupUi") Then AvaliableCount += 1
                If Not Settings.Get(Of Boolean)("UiHiddenSetupLink") Then AvaliableCount += 1
                If Not Settings.Get(Of Boolean)("UiHiddenSetupSystem") Then AvaliableCount += 1
                FrmSetupLeft.PanItem.Visibility = If(AvaliableCount < 2 AndAlso Not HiddenForceShow, Visibility.Collapsed, Visibility.Visible)
            End If
            '更多子页面
            Dim OtherAvaliableCount As Integer = 0
            If Not Settings.Get(Of Boolean)("UiHiddenOtherHelp") Then OtherAvaliableCount += 1
            If Not Settings.Get(Of Boolean)("UiHiddenOtherAbout") Then OtherAvaliableCount += 1
            If Not Settings.Get(Of Boolean)("UiHiddenOtherTest") Then OtherAvaliableCount += 1
            OtherAvaliableCount += 1 'Minecraft 新闻暂不可隐藏
            If FrmOtherLeft IsNot Nothing Then
                FrmOtherLeft.ItemHelp.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenOtherHelp"), Visibility.Collapsed, Visibility.Visible)
                FrmOtherLeft.ItemAbout.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenOtherAbout"), Visibility.Collapsed, Visibility.Visible)
                FrmOtherLeft.ItemTest.Visibility = If(Not HiddenForceShow AndAlso Settings.Get(Of Boolean)("UiHiddenOtherTest"), Visibility.Collapsed, Visibility.Visible)
                '隐藏左边选择卡
                FrmOtherLeft.PanItem.Visibility = If(OtherAvaliableCount < 2 AndAlso Not HiddenForceShow, Visibility.Collapsed, Visibility.Visible)
            End If
            If OtherAvaliableCount = 1 AndAlso Not HiddenForceShow Then
                FrmMain.BtnTitleSelect4.Text = ModLang.L("关于")
            Else
                FrmMain.BtnTitleSelect4.Text = ModLang.L("更多")
            End If
            '各个页面的入口
            If FrmMain.PageCurrent = FormMain.PageType.InstanceSelect Then FrmSelectRight.BtnEmptyDownload_Loaded()
            If FrmMain.PageCurrent = FormMain.PageType.Launch Then If FrmLaunchRight IsNot Nothing Then FrmLaunchRight.RefreshButtonsUI()
            If FrmMain.PageCurrent = FormMain.PageType.InstanceSetup AndAlso FrmInstanceModDisabled IsNot Nothing Then FrmInstanceModDisabled.BtnDownload_Loaded()
            '备注
            If FrmSetupUI IsNot Nothing Then FrmSetupUI.CardSwitch.Title = If(HiddenForceShow, ModLang.L("功能隐藏（已暂时关闭，按 F12 以重新启用）"), ModLang.L("功能隐藏"))
        Catch ex As Exception
            Logger.Error(ex, "刷新功能隐藏项目失败")
        End Try
    End Sub

    'UI 协同改变
    Private Sub HiddenSetupMain() Handles CheckHiddenPageSetup.Change
        '设置主页面
        If CheckHiddenPageSetup.Checked Then
            '开启
            CheckHiddenSetupLaunch.Checked = True
            CheckHiddenSetupSystem.Checked = True
            CheckHiddenSetupLink.Checked = True
            CheckHiddenSetupUI.Checked = True
        Else
            '关闭
            If Settings.Get(Of Boolean)("UiHiddenSetupLaunch") AndAlso Settings.Get(Of Boolean)("UiHiddenSetupUi") AndAlso Settings.Get(Of Boolean)("UiHiddenSetupSystem") AndAlso Settings.Get(Of Boolean)("UiHiddenSetupLink") Then
                CheckHiddenSetupLaunch.Checked = False
                CheckHiddenSetupSystem.Checked = False
                CheckHiddenSetupLink.Checked = False
                CheckHiddenSetupUI.Checked = False
            End If
        End If
    End Sub
    Private Sub HiddenSetupSub() Handles CheckHiddenSetupLaunch.Change, CheckHiddenSetupSystem.Change, CheckHiddenSetupLink.Change, CheckHiddenSetupUI.Change
        '设置子页面
        If Settings.Get(Of Boolean)("UiHiddenSetupLaunch") AndAlso Settings.Get(Of Boolean)("UiHiddenSetupUi") AndAlso Settings.Get(Of Boolean)("UiHiddenSetupSystem") AndAlso Settings.Get(Of Boolean)("UiHiddenSetupLink") Then
            '已被全部隐藏
            CheckHiddenPageSetup.Checked = True
        Else
            '未被全部隐藏
            CheckHiddenPageSetup.Checked = False
        End If
    End Sub
    Private Sub HiddenOtherMain() Handles CheckHiddenPageOther.Change
        '更多主页面
        If CheckHiddenPageOther.Checked Then
            '开启
            CheckHiddenOtherAbout.Checked = True
        Else
            '关闭
            If Settings.Get(Of Boolean)("UiHiddenOtherAbout") Then
                CheckHiddenOtherAbout.Checked = False
            End If
        End If
    End Sub
    Private Sub HiddenOtherSub(sender As Object, user As Boolean) Handles CheckHiddenOtherAbout.Change
        '更多子页面
        If Settings.Get(Of Boolean)("UiHiddenOtherAbout") Then
            '已被全部隐藏
            CheckHiddenPageOther.Checked = True
        Else
            '未被全部隐藏
            CheckHiddenPageOther.Checked = False
        End If
    End Sub

    '警告提示
    Private Sub HiddenHint(sender As Object, user As Boolean) Handles CheckHiddenFunctionHidden.Change, CheckHiddenPageSetup.Change, CheckHiddenSetupUI.Change
        If AniControlEnabled = 0 AndAlso sender.Checked Then Hint(ModLang.L("对应的隐藏将在下次启动时自动恢复显示。"))
    End Sub

#End Region

    '滑动条
    Private Sub SliderLoad()
        SliderMusicVolume.GetHintText = Function(v) Math.Ceiling(v * 0.1) & "%"
        SliderVideoVolume.GetHintText = Function(v) Math.Ceiling(v * 0.1) & "%"
        SliderLauncherTransparent.GetHintText = Function(v) Math.Round(40 + v * 0.1) & "%"
        SliderLauncherHue.GetHintText = Function(v) v & "°"
        SliderLauncherSat.GetHintText = Function(v) v & "%"
        SliderLauncherDelta.GetHintText =
        Function(Value As Integer) As String
            If Value > 90 Then
                Return "+" & (Value - 90)
            ElseIf Value = 90 Then
                Return 0
            Else
                Return Value - 90
            End If
        End Function
        SliderLauncherLight.GetHintText =
        Function(Value As Integer) As String
            If Value > 20 Then
                Return "+" & (Value - 20)
            ElseIf Value = 20 Then
                Return 0
            Else
                Return Value - 20
            End If
        End Function
        SliderBackgroundOpacity.GetHintText = Function(v) Math.Round(v * 0.1) & "%"
        SliderBackgroundBlur.GetHintText = Function(v) v & " 像素"
        SliderWindowRadius.GetHintText =
        Function(v)
            Return v & " px"
        End Function
    End Sub

End Class
