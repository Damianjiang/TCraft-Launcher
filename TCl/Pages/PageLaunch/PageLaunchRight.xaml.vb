Imports System.Windows.Threading

Public Class PageLaunchRight
    Private Sub Init() Handles Me.Loaded
        '把启动侧边栏移交给窗口层：放在本页里的话它只能从「右栏的左边」开始展开，
        '盖不住左边栏。移交后它和 PanMain 同级，才能真正从窗口最左边擦出来。
        '注意必须先 Children.Remove 再 Add：直接 Add 会报「指定的元素已经是另一个元素的逻辑子元素」。
        Try
            If PanLaunching.Parent IsNot FrmMain.HostLaunchSide Then
                Dim OldParent = TryCast(PanLaunching.Parent, Panel)
                If OldParent IsNot Nothing Then OldParent.Children.Remove(PanLaunching)
                FrmMain.HostLaunchSide.Children.Add(PanLaunching)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "移交启动侧边栏到窗口层失败")
        End Try
        PanBack.ScrollToHome()
        PanLog.Visibility = ModeDebug.ToVisibility
        '快照版提示
        PanHint.Visibility =
            (BuildType <> BuildTypes.Release AndAlso VersionBranchMain = "Official" AndAlso
             CurrentRank < DonationRank.Rank23 AndAlso Not Settings.Get(Of Boolean)("HintSnapshot")).ToVisibility
    End Sub


#Region "启动按钮与启动遮罩"

    '启动侧边栏展开后的宽度：XAML 里 PanLaunchSideInner 固定 312，左右各 26 边距
    Private Const LaunchSideWidth As Double = 364
    ''' <summary>
    ''' 侧边栏是否已经收起。PageChangeToLogin 可能被调用两次（提前收起一次、启动真正结束再一次），
    ''' 用它做幂等保护，避免收起动画重播、以及登录框被重建两遍导致输入区闪一下。
    ''' </summary>
    Private LaunchSideCollapsed As Boolean = True
    ''' <summary>
    ''' 是否已经安排过「启动成功后自动收起」，保证一次启动只排一次。
    ''' </summary>
    Private LaunchSideAutoCollapseScheduled As Boolean = False

    Public Sub LaunchInit() Handles Me.Loaded
        AprilPosTrans.X = 0
        AprilPosTrans.Y = 0
        '开始按钮
        AddHandler McInstanceListLoader.LoadingStateChanged, AddressOf RefreshButtonsUI
        AddHandler McFolderListLoader.LoadingStateChanged, AddressOf RefreshButtonsUI
        RefreshButtonsUI()
    End Sub

    ' <summary>切换至启动中页面。</summary>
    Public Sub PageChangeToLaunching()
        '修改登陆方式
        Select Case Settings.Get(Of McLoginType)("LoginType")
            Case McLoginType.Legacy
                LabLaunchingMethod.Text = ModLang.L("离线登录")
            Case McLoginType.Ms
                LabLaunchingMethod.Text = ModLang.L("正版登录")
            Case McLoginType.Nide
                LabLaunchingMethod.Text = ModLang.L("统一通行证登录")
            Case McLoginType.Auth
                LabLaunchingMethod.Text = "Authlib-Injector"
        End Select
        '初始化页面
        LabLaunchingName.Text = McInstanceSelected.Name
        LabLaunchingStage.Text = ModLang.L("初始化")
        LabLaunchingTitle.Text = If(CurrentLaunchOptions?.SaveBatch Is Nothing, ModLang.L("正在启动游戏"), ModLang.L("正在导出启动脚本"))
        LabLaunchingProgress.Text = "0.00 %"
        LabLaunchingProgress.Opacity = 1
        LabLaunchingDownload.Visibility = Visibility.Visible
        LabLaunchingProgressLeft.Opacity = 0.6
        LabLaunchingDownload.Visibility = Visibility.Visible
        LabLaunchingDownload.Text = "0 B/s"
        LabLaunchingDownload.Opacity = 0
        LabLaunchingDownload.Visibility = Visibility.Collapsed
        LabLaunchingDownloadLeft.Opacity = 0
        LabLaunchingDownloadLeft.Visibility = Visibility.Collapsed
        ProgressLaunchingFinished.Width = New GridLength(0, GridUnitType.Star)
        ProgressLaunchingUnfinished.Width = New GridLength(1, GridUnitType.Star)
        PanLaunchingHint.Opacity = 0
        PanLaunchingHint.Visibility = Visibility.Collapsed
        PanLaunchingInfo.Width = Double.NaN '重置宽度改变动画
        McLaunchProcess = Nothing
        McLaunchWatcher = Nothing
        '获取 “你知道吗” 提示
        LabLaunchingHint.Text = PageOtherTest.GetRandomHint()
        '初始化页面动画：左栏淡出，启动侧边栏从窗口最左边向右展开
        FrmLaunchLeft.PanInput.IsHitTestVisible = False
        PanLaunching.IsHitTestVisible = False
        LoadLaunching.State.LoadingState = MyLoading.MyLoadingState.Run
        '复位到"完全收起"状态（此时 Visibility 还是 Collapsed，改值不会闪）
        PanLaunchingCard.Width = 0
        PanLaunchSideInner.Opacity = 0
        RectLaunchingMask.Opacity = 0
        PanLaunching.Visibility = Visibility.Visible
        '重置收起状态：新的一次启动，允许再次展开与再次收起
        LaunchSideCollapsed = False
        LaunchSideAutoCollapseScheduled = False
        '动画说明：
        '① 侧边栏宽度 0 → 364，内容固定宽被右边裁掉，看起来就是一次从左往右的擦除；
        '② 内容等侧边栏展开一半（200ms）再淡入，避免文字跟着边缘一起被"拉"出来；
        '③ 右侧内容同步压暗（RectLaunchingMask）。
        '注意：VB 的隐式续行里不能插独立注释行，所以说明都写在这里。
        AniStart({
                AaOpacity(FrmLaunchLeft.PanInput, 0, 50), '略作延迟，这样如果预检测失败，不会出现奇怪的弹一下的动画
                AaOpacity(FrmLaunchLeft.PanInput, -FrmLaunchLeft.PanInput.Opacity, 110, , New AniEaseInFluent, True),
                AaScaleTransform(FrmLaunchLeft.PanInput, 1.2 - CType(FrmLaunchLeft.PanInput.RenderTransform, ScaleTransform).ScaleX, 160),
                AaWidth(PanLaunchingCard, LaunchSideWidth - PanLaunchingCard.Width, 420, 40, New AniEaseOutFluent(AniEasePower.ExtraStrong)),
                AaOpacity(RectLaunchingMask, 1 - RectLaunchingMask.Opacity, 320, 40),
                AaOpacity(PanLaunchSideInner, 1 - PanLaunchSideInner.Opacity, 200, 200),
                AaCode(Sub() PanLaunching.IsHitTestVisible = True, 380)
            }, "Launch State Page")
    End Sub
    ' <summary>切换至登录页面。</summary>
    Public Sub PageChangeToLogin()
        '已经收起了就直接返回：启动成功时 LaunchingRefresh 会提前收一次，
        '之后 McLaunchState 走到 Finished 还会再调一次，这里挡住重复收起。
        If LaunchSideCollapsed Then
            Logger.Info("启动侧边栏已收起，跳过重复收起")
            Return
        End If
        LaunchSideCollapsed = True
        FrmLaunchLeft.EnsureLoginForm(Settings.Get(Of McLoginType)("LoginType"), KeepInput:=False)
        FrmLaunchLeft.PanInput.IsHitTestVisible = False
        '收回去：先淡出内容，再把侧边栏宽度收窄，最后隐藏（启动成功/失败/取消都会走到这里）
        PanLaunching.IsHitTestVisible = False
        LoadLaunching.State.LoadingState = MyLoading.MyLoadingState.Stop
        FrmLaunchLeft.PanInput.Visibility = Visibility.Visible
        AniStart({
            AaOpacity(PanLaunchSideInner, -PanLaunchSideInner.Opacity, 90),
            AaWidth(PanLaunchingCard, -PanLaunchingCard.Width, 300, 90, New AniEaseOutFluent(AniEasePower.ExtraStrong)),
            AaOpacity(RectLaunchingMask, -RectLaunchingMask.Opacity, 260, 90),
            AaCode(Sub() PanLaunching.Visibility = Visibility.Collapsed, 90, True),
            AaOpacity(FrmLaunchLeft.PanInput, 1 - FrmLaunchLeft.PanInput.Opacity, 250, 50),
            AaScaleTransform(FrmLaunchLeft.PanInput, 1 - CType(FrmLaunchLeft.PanInput.RenderTransform, ScaleTransform).ScaleX, 300, 50, New AniEaseOutBack(AniEasePower.Weak)),
            AaCode(Sub() FrmLaunchLeft.PanInput.IsHitTestVisible = True, 200)
        }, "Launch State Page", True)
    End Sub

    '版本选择按钮
    Public Sub BtnVersion_Click()
        If McLaunchLoader.State = LoadState.Loading Then Return
        FrmMain.PageChange(FormMain.PageType.InstanceSelect)
    End Sub
    '启动按钮
    Public Sub LaunchButtonClick() Handles BtnLaunch.Click
        If McLaunchLoader.State = LoadState.Loading OrElse Not BtnLaunch.IsEnabled OrElse
            (FrmMain.PageRight IsNot Nothing AndAlso FrmMain.PageRight.PageState <> MyPageRight.PageStates.ContentStay AndAlso FrmMain.PageRight.PageState <> MyPageRight.PageStates.ContentEnter) Then Return
        '愚人节处理
        If IsAprilEnabled AndAlso Not IsAprilGiveup Then
            ThemeUnlock(12, False, "隐藏主题 七曜彩 已解锁！")
            IsAprilGiveup = True
            Settings.Set("AprilYear", Date.Now.Year)
            AprilScaleTrans.ScaleX = 1
            AprilScaleTrans.ScaleY = 1
            AprilPosTrans.X = 0
            AprilPosTrans.Y = 0
            FrmMain.BtnExtraApril.ShowRefresh()
        End If
        '实际的启动（依据内部状态号判断，而非按钮文字，避免语言切换后文字改变导致误判）
        If BtnLaunchState = 3 Then
            McLaunchStart()
        ElseIf BtnLaunchState = 2 Then
            FrmMain.PageChange(FormMain.PageType.Download, FormMain.PageSubType.DownloadInstall)
        End If
    End Sub
    Private BtnLaunchState As Integer = 0
    Private BtnLaunchInstance As McInstance = Nothing
    Public Sub RefreshButtonsUI() Handles BtnLaunch.Loaded
        If Not BtnLaunch.IsLoaded Then Return
        '获取当前状态
        Dim CurrentState As Integer
        If (Not FrmLaunchLeft.IsLoadFinished) OrElse McInstanceListLoader.State = LoadState.Loading OrElse McFolderListLoader.State = LoadState.Loading Then
            CurrentState = 0
        Else
            If McInstanceSelected Is Nothing Then
                If Settings.Get(Of Boolean)("UiHiddenPageDownload") AndAlso Not PageSetupUI.HiddenForceShow Then
                    CurrentState = 1
                Else
                    CurrentState = 2
                End If
            Else
                CurrentState = 3
            End If
        End If
        '更新状态。
        '说明：文本/UI 状态必须总是重新应用（幂等），因为切换语言 / i18n 重新套译文时
        '可能把这两个"状态驱动"控件的文本覆盖回 XAML 占位值（表现为启动按钮一直停在
        '"Loading/正在加载"、装好游戏也显示"下载"）；只有日志在状态真正变化时打，避免刷屏。
        Dim StateChanged As Boolean = (CurrentState <> BtnLaunchState) OrElse
            (If(McInstanceSelected Is Nothing, "", McInstanceSelected.PathVersion) <>
             If(BtnLaunchInstance Is Nothing, "", BtnLaunchInstance.PathVersion))
        BtnLaunchInstance = McInstanceSelected
        BtnLaunchState = CurrentState
        Select Case CurrentState
            Case 0
                If StateChanged Then Logger.Info("启动按钮：正在加载 Minecraft 版本")
                BtnLaunch.Text = ModLang.L("正在加载")
                BtnLaunch.IsEnabled = False
                LabVersion.Text = ModLang.L("正在加载中，请稍候")
            Case 1
                If StateChanged Then Logger.Info("启动按钮：无 Minecraft 版本，下载已禁用")
                BtnLaunch.Text = ModLang.L("启动游戏")
                BtnLaunch.IsEnabled = False
                LabVersion.Text = ModLang.L("未找到可用的游戏版本")
            Case 2
                If StateChanged Then Logger.Info("启动按钮：无 Minecraft 版本，要求下载")
                BtnLaunch.Text = ModLang.L("下载游戏")
                BtnLaunch.IsEnabled = True
                LabVersion.Text = ModLang.L("未找到可用的游戏版本")
            Case 3
                If StateChanged Then Logger.Info($"启动按钮：Minecraft 版本：{McInstanceSelected.PathVersion}")
                BtnLaunch.Text = ModLang.L("启动游戏")
                BtnLaunch.IsEnabled = True
                LabVersion.Text = McInstanceSelected.Name
                'BtnMore.Visibility = Visibility.Visible '由功能隐藏设置修改
        End Select
    End Sub
    '取消按钮
    Public Sub BtnCancel_Click() Handles BtnCancel.Click
        If McLaunchLoaderReal IsNot Nothing Then
            McLaunchLoaderReal.Cancel()
            McLaunchLog("已取消启动")
            Try
                If McLaunchWatcher IsNot Nothing Then
                    McLaunchWatcher.Kill()
                ElseIf McLaunchProcess IsNot Nothing Then
                    If Not McLaunchProcess.HasExited Then McLaunchProcess.Kill()
                End If
            Catch ex As Exception
                Logger.Error(ex, "取消启动结束进程失败", LogBehavior.Toast)
            End Try
        End If
    End Sub
    '版本设置按钮
    Public Sub BtnMore_Click()
        If McLaunchLoader.State = LoadState.Loading Then Return
        If McInstanceLock.IsLocked(McInstanceSelected) Then
            Hint(ModLang.L("该版本正在被修改，暂时无法进入版本设置！"), HintType.Red)
            Return
        End If
        McInstanceSelected.Load()
        PageInstanceLeft.Instance = McInstanceSelected
        FrmMain.PageChange(FormMain.PageType.InstanceSetup, 0)
    End Sub
    ' <summary>
    ' 每 0.1s 执行一次，刷新启动的数据 UI 显示。
    ' </summary>
    Public Sub LaunchingRefresh()
        Try
            If McLaunchLoaderReal.State = LoadState.Canceled Then Return
            '阶段状态获取
            Dim IsLaunched As Boolean = False '是否已经启动游戏，只是在等待窗口
            Try
                For Each Loader In McLaunchLoaderReal.GetLoaderList(False)
                    If Loader.State = LoadState.Loading OrElse Loader.State = LoadState.Waiting Then
                        LabLaunchingStage.Text = Loader.Name
                        IsLaunched = Loader.Name = "等待游戏窗口出现" OrElse Loader.Name = "结束处理"
                        Exit Try
                    End If
                Next
                LabLaunchingStage.Text = ModLang.L("已完成")
            Catch ex As Exception
                Logger.Warn(ex, "获取是否启动完成失败，可能是由于启动状态改变导致集合已修改")
                Return
            End Try
            '第 N 轮：这里原先是裸中文字面量，切到英文等语言后每 0.1s 把标题打回中文（用户实测「正在启动游戏」标题不切换）。
            '统一走 L：命中词典则返回当前语言译文，未命中回退原文。
            LabLaunchingTitle.Text = If(IsLaunched OrElse McLaunchLoaderReal.State = LoadState.Finished,
                ModLang.L("已启动游戏"),
                If(CurrentLaunchOptions.SaveBatch Is Nothing, ModLang.L("正在启动游戏"), ModLang.L("正在导出启动脚本")))
            If AniIsRun("Launch State Page") Then IsLaunched = False '等待页面切换动画完成
            '启动成功后自动收起侧边栏。"等待游戏窗口出现" 阶段就代表游戏进程已经拉起来了，
            '此时留 1s 让用户看到「已启动游戏 / 100%」，再把侧边栏滑走；真正结束时 McLaunchState
            '还会调一次 PageChangeToLogin，那里有幂等保护，不会重播收起动画。
            If IsLaunched AndAlso Not LaunchSideCollapsed AndAlso Not LaunchSideAutoCollapseScheduled Then
                LaunchSideAutoCollapseScheduled = True
                Logger.Info("游戏已启动，1s 后自动收起启动侧边栏")
                AniStart({AaCode(Sub() PageChangeToLogin(), 1000)}, "Launching Auto Collapse")
            End If
            '更新进度
            Dim ActualProgress = McLaunchLoaderReal.Progress
            If ActualProgress >= ShowProgress Then ShowProgress += (ActualProgress - ShowProgress) * 0.1 + 0.0025 '向实际进度靠一点
            If ActualProgress <= ShowProgress Then ShowProgress = ActualProgress '原来或处理后变得比实际进度高，直接回退
            If IsLaunched Then ShowProgress = 1 '如果已经完成了，就不卖关子了
            LabLaunchingProgress.Text = (ShowProgress * 100).ToString("0.00") & " %"
            '更新下载速度
            Dim HasLaunchDownloader As Boolean = False
            Try
                If NetManager.Speed = 0 AndAlso LabLaunchingDownload.Visibility = Visibility.Collapsed Then
                    '可能只是在检查文件，没有实际下载
                    HasLaunchDownloader = False
                Else
                    For Each Loader In NetManager.Tasks
                        If Loader.RealParent IsNot Nothing AndAlso Loader.RealParent.Name = "Minecraft 启动" AndAlso Loader.State = LoadState.Loading Then HasLaunchDownloader = True
                    Next
                End If
            Catch ex As Exception
                Logger.Warn(ex, "获取 Minecraft 启动下载器失败，可能是因为启动被取消")
                HasLaunchDownloader = False
            End Try
            LabLaunchingDownload.Text = StringUtils.FormatByteSize(NetManager.Speed) & "/s"
            '进度改变动画
            Dim AnimList As New List(Of AniData) From {
                 AaGridLengthWidth(ProgressLaunchingFinished, ShowProgress - ProgressLaunchingFinished.Width.Value, 130,, New AniEaseOutFluent),
                 AaGridLengthWidth(ProgressLaunchingUnfinished, 1 - ShowProgress - ProgressLaunchingUnfinished.Width.Value, 130,, New AniEaseOutFluent)
            }
            If HasLaunchDownloader = (LabLaunchingDownload.Visibility = Visibility.Collapsed) Then 'IsDownloadStateChanged
                LabLaunchingDownload.Visibility = Visibility.Visible
                LabLaunchingDownloadLeft.Visibility = Visibility.Visible
                AnimList.AddRange({
                    AaOpacity(LabLaunchingDownload, If(HasLaunchDownloader, 1, 0) - LabLaunchingDownload.Opacity, 90),
                    AaOpacity(LabLaunchingDownloadLeft, If(HasLaunchDownloader, 0.5, 0) - LabLaunchingDownloadLeft.Opacity, 90),
                    AaCode(
                    Sub()
                        If Not HasLaunchDownloader Then
                            LabLaunchingDownload.Visibility = Visibility.Collapsed
                            LabLaunchingDownloadLeft.Visibility = Visibility.Collapsed
                        End If
                    End Sub, 110)
                })
            End If
            If (Not IsLaunched) = (LabLaunchingProgress.Visibility = Visibility.Collapsed) Then 'IsProgressStateChanged
                LabLaunchingProgress.Visibility = Visibility.Visible
                LabLaunchingProgressLeft.Visibility = Visibility.Visible
                If IsLaunched Then PanLaunchingHint.Visibility = Visibility.Visible
                AnimList.AddRange({
                    AaOpacity(LabLaunchingProgress, If(Not IsLaunched, 1, 0) - LabLaunchingProgress.Opacity, 90),
                    AaOpacity(LabLaunchingProgressLeft, If(Not IsLaunched, 0.5, 0) - LabLaunchingProgressLeft.Opacity, 90),
                    AaOpacity(PanLaunchingHint, If(IsLaunched, 1, 0) - PanLaunchingHint.Opacity, 90)
                })
            End If
            AniStart(AnimList, "Launching Progress")
        Catch ex As Exception
            Logger.Error(ex, "刷新启动信息失败")
        End Try
    End Sub
    Private ShowProgress As Double = 0
    '尺寸改变动画
    Private IsWidthAnimating As Boolean = False
    Private ActualUsedWidth As Double
    Private Sub PanLaunchingInfo_SizeChangedW(sender As Object, e As SizeChangedEventArgs) Handles PanLaunchingInfo.SizeChanged
        Dim DeltaWidth As Double = e.NewSize.Width - e.PreviousSize.Width
        If e.PreviousSize.Width = 0 OrElse IsWidthAnimating OrElse Math.Abs(DeltaWidth) < 1 OrElse PanLaunchingInfo.ActualWidth = 0 Then Return
        AniStart({
            AaWidth(PanLaunchingInfo, DeltaWidth, 180,, New AniEaseOutFluent),
            AaCode(Sub()
                       IsWidthAnimating = False
                       PanLaunchingInfo.Width = ActualUsedWidth
                   End Sub,, True)
        }, "Launching Info Width")
        IsWidthAnimating = True
        ActualUsedWidth = PanLaunchingInfo.Width
        PanLaunchingInfo.Width = e.PreviousSize.Width
    End Sub
    Private IsHeightAnimating As Boolean = False
    Private ActualUsedHeight As Double
    Private Sub PanLaunchingInfo_SizeChangedH(sender As Object, e As SizeChangedEventArgs) Handles PanLaunchingInfo.SizeChanged
        Dim DeltaHeight As Double = e.NewSize.Height - e.PreviousSize.Height
        If e.PreviousSize.Height = 0 OrElse IsHeightAnimating OrElse Math.Abs(DeltaHeight) < 1 OrElse PanLaunchingInfo.ActualHeight = 0 Then Return
        AniStart({
            AaHeight(PanLaunchingInfo, DeltaHeight, 180,, New AniEaseOutFluent),
            AaCode(Sub()
                       IsHeightAnimating = False
                       PanLaunchingInfo.Height = ActualUsedHeight
                   End Sub,, True)
        }, "Launching Info Height")
        IsHeightAnimating = True
        ActualUsedHeight = PanLaunchingInfo.Height
        PanLaunchingInfo.Height = e.PreviousSize.Height
    End Sub

    ''' <summary>
    ''' 开发用：把「启动中」弹窗摆成典型状态并输出快照（TCL\devshot-launching.png）。
    ''' 沙箱里的合成点击进不了 WPF 输入管线，看不到弹窗长什么样，只能靠这个办法确认排版；
    ''' 仅当存在 TCL\.devshot 时由 FormMain.RunDevSnapshot 调用，正式运行不会执行。
    ''' </summary>
    Public Sub RunDevLaunchingSnapshot()
        '真正在启动中就不要摆拍了：Finally 会把侧边栏强行收起，
        '如果此时用户刚好点了「启动游戏」，正在展开的侧边栏会被这张快照直接清掉。
        If McLaunchLoader.State = LoadState.Loading Then Return
        Try
            '造一份典型假数据：进度 42.13%、正在下载支持库
            LabLaunchingTitle.Text = ModLang.L("正在启动游戏")
            LabLaunchingName.Text = If(McInstanceSelected Is Nothing, "1.16.4-Forge_15.8.0.1560", McInstanceSelected.Name)
            LabLaunchingStage.Text = "下载支持库" '真实运行时这里填的是 Loader.Name 原文，不翻译
            LabLaunchingMethod.Text = ModLang.L("离线登录")
            LabLaunchingProgress.Text = "42.13 %"
            LabLaunchingProgressLeft.Opacity = 0.6
            ProgressLaunchingFinished.Width = New GridLength(0.4213, GridUnitType.Star)
            ProgressLaunchingUnfinished.Width = New GridLength(0.5787, GridUnitType.Star)
            PanLaunchingHint.Visibility = Visibility.Collapsed
            LoadLaunching.State.LoadingState = MyLoading.MyLoadingState.Run
            PanLaunching.Visibility = Visibility.Visible
            PanLaunchingCard.Width = LaunchSideWidth
            PanLaunchSideInner.Opacity = 1
            RectLaunchingMask.Opacity = 1
            PanLaunching.UpdateLayout()
            Dim ShotW As Integer = CInt(PanLaunchingCard.ActualWidth)
            Dim ShotH As Integer = CInt(PanLaunchingCard.ActualHeight)
            If ShotW <= 0 OrElse ShotH <= 0 Then Return
            Dim Bmp As New RenderTargetBitmap(ShotW, ShotH, 96, 96, PixelFormats.Pbgra32)
            '不能直接 Bmp.Render(PanLaunchingCard)：RenderTargetBitmap 会带上元素在父容器里的
            '布局偏移，拍出来会缺左上角、右下角被切。用 VisualBrush 重绘一次即可。
            Dim ShotVisual As New DrawingVisual()
            Using Dc = ShotVisual.RenderOpen()
                Dc.DrawRectangle(New VisualBrush(PanLaunchingCard), Nothing, New Rect(0, 0, ShotW, ShotH))
            End Using
            Bmp.Render(ShotVisual)
            Dim Enc As New PngBitmapEncoder()
            Enc.Frames.Add(BitmapFrame.Create(Bmp))
            Using Fs = IO.File.Create(Paths.Base & "TCL\devshot-launching.png")
                Enc.Save(Fs)
            End Using
            Logger.Info($"已输出启动弹窗快照：TCL\devshot-launching.png（{ShotW}×{ShotH}）")
        Catch ex As Exception
            Logger.Warn(ex, "渲染启动弹窗快照失败")
        Finally
            '快照拍完立刻收起，否则开着 .devshot 启动时弹窗会一直挂在界面上
            PanLaunching.Visibility = Visibility.Collapsed
            PanLaunchingCard.Width = 0
            PanLaunchSideInner.Opacity = 0
            RectLaunchingMask.Opacity = 0
        End Try
    End Sub

    ''' <summary>
    ''' 开发用：把启动按钮的展开菜单摆成打开状态并输出快照（TCL\devshot-menu.png）。
    ''' 仅当存在 TCL\.devshot-menu 时由 FormMain.RunDevSnapshot 调用，正式运行不会执行。
    ''' </summary>
    Public Sub RunDevMenuSnapshot()
        Try
            ItemMenuSetup.Visibility = Visibility.Visible
            CardLaunchMenu.Opacity = MenuCard.PopupBaseOpacity
            TransformLaunchMenuScale.ScaleX = 1
            TransformLaunchMenuScale.ScaleY = 1
            TransformLaunchMenuPos.Y = 0
            PopupLaunchMenu.IsOpen = True
            'Popup 内容是打开后才布局的，等布局空闲再拍，否则 ActualWidth 还是 0
            Dispatcher.BeginInvoke(New Action(
                Sub()
                    Try
                        Dim ShotW As Integer = CInt(CardLaunchMenu.ActualWidth)
                        Dim ShotH As Integer = CInt(CardLaunchMenu.ActualHeight)
                        If ShotW > 0 AndAlso ShotH > 0 Then
                            Dim Bmp As New RenderTargetBitmap(ShotW, ShotH, 96, 96, PixelFormats.Pbgra32)
                            Dim ShotVisual As New DrawingVisual()
                            Using Dc = ShotVisual.RenderOpen()
                                Dc.DrawRectangle(New VisualBrush(CardLaunchMenu), Nothing, New Rect(0, 0, ShotW, ShotH))
                            End Using
                            Bmp.Render(ShotVisual)
                            Dim Enc As New PngBitmapEncoder()
                            Enc.Frames.Add(BitmapFrame.Create(Bmp))
                            Using Fs = IO.File.Create(Paths.Base & "TCL\devshot-menu.png")
                                Enc.Save(Fs)
                            End Using
                            Logger.Info($"已输出启动菜单快照：TCL\devshot-menu.png（{ShotW}×{ShotH}）")
                        End If
                    Catch ex As Exception
                        Logger.Warn(ex, "渲染启动菜单快照失败")
                    Finally
                        PopupLaunchMenu.IsOpen = False
                    End Try
                End Sub), DispatcherPriority.ContextIdle)
        Catch ex As Exception
            Logger.Warn(ex, "打开启动菜单快照失败")
        End Try
    End Sub

#End Region

#Region "账户摘要"

    ' 当前显示的账户名（供左栏问候语刷新）。
    Public CurrentDisplayName As String = ""



    ' <summary>
    ' 刷新首页的账户摘要（皮肤 + 用户名 + 类型），并同步标题栏徽章与问候语。
    ' </summary>
    Public Sub RefreshAccountSummary()
        Try
            Dim Type = Settings.Get(Of McLoginType)("LoginType")
            Dim Name As String = ""
            Dim TypeName As String = ""
            Select Case Type
                Case McLoginType.Ms
                    Name = Settings.Get(Of String)("CacheMsV2Name")
                    TypeName = ModLang.L("正版登录")
                Case McLoginType.Legacy
                    'FrmLaunchLeft 可能还没构造好（窗口首帧前就会调用本方法），直接读设置兜底
                    If FrmLaunchLeft IsNot Nothing Then
                        Name = FrmLaunchLeft.GetCurrentLegacyName()
                    Else
                        Name = Settings.Get(Of String)("LoginLegacyName").BeforeFirst("¨").Trim
                    End If
                    TypeName = ModLang.L("离线登录")
                Case McLoginType.Nide
                    Name = Settings.Get(Of String)("CacheNideName")
                    TypeName = ModLang.L("统一通行证登录")
                Case McLoginType.Auth
                    'CacheAuthName 是服务端返回的角色名（首次登录前为空）；
                    '回退到 CacheAuthUsername（登录账号名），让选中账号后首页立即显示玩家名
                    Dim AuthName As String = Settings.Get(Of String)("CacheAuthName")
                    If AuthName = "" Then AuthName = Settings.Get(Of String)("CacheAuthUsername")
                    Name = AuthName
                    TypeName = "Authlib-Injector"
            End Select
            '本方法此前只算不用，导致标题栏账号徽章永远是空白药丸、问候语永远不带名字
            CurrentDisplayName = If(Name, "")
            Dim Face As String = ""
            If CurrentDisplayName <> "" Then Face = PageLaunchLeft.GetAccountFaceOrDefault(CurrentDisplayName)
            If FrmMain IsNot Nothing Then FrmMain.SetTitleAccount(CurrentDisplayName, TypeName, Face)
            '启动期就把当前账户的皮肤加载器跑起来：Faces2 头像缓存是「皮肤加载完」才裁出来的，
            '以前只有进「登录」页才启动皮肤加载，标题栏徽章于是一直顶着默认脸
            If FrmMain IsNot Nothing AndAlso CurrentDisplayName <> "" AndAlso PageLaunchLeft.GetAccountFace(CurrentDisplayName) Is Nothing Then
                Select Case Type
                    Case McLoginType.Ms : PageLaunchLeft.SkinMs.Start()
                    Case McLoginType.Legacy : PageLaunchLeft.SkinLegacy.Start()
                    Case McLoginType.Nide : PageLaunchLeft.SkinNide.Start()
                    Case McLoginType.Auth : PageLaunchLeft.SkinAuth.Start()
                End Select
            End If
            RefreshGreeting(CurrentDisplayName)
        Catch ex As Exception
            Logger.Warn(ex, "刷新首页账户摘要失败")
        End Try
    End Sub

#End Region

    ' 问候语

    ''' <summary>
    ''' 顶部欢迎语：按本地时区的当前时间自动问候（凌晨/上午/中午/下午/晚上），并带上玩家名。
    ''' </summary>
    Public Sub RefreshGreeting(Name As String)
        Try
            If LabGreeting Is Nothing Then Return
            Dim Hour As Integer = Now.Hour
            Dim Greet As String
            Select Case Hour
                Case 0 To 5 : Greet = ModLang.L("夜深了")
                Case 6 To 11 : Greet = ModLang.L("早上好")
                Case 12 To 17 : Greet = ModLang.L("下午好")
                Case Else : Greet = ModLang.L("晚上好")
            End Select
            If String.IsNullOrEmpty(Name) Then
                LabGreeting.Text = Greet
                LabGreetingSub.Text = ModLang.L("欢迎回到 TCL")
            Else
                LabGreeting.Text = Greet & "，" & Name
                LabGreetingSub.Text = ModLang.L("欢迎回来，今天也要玩得开心")
            End If
        Catch ex As Exception
            Logger.Warn(ex, "生成欢迎语失败")
        End Try
    End Sub
    '连体下拉：版本选择 / 版本设置（HMCL 式主按钮 + 附加段）
    '注意：MyIconButton 的 MouseUp 会把 e.Handled 置 True，事件不会冒泡到 BtnLaunchMenu，
    '因此图标本身必须单独挂一次（否则点箭头没反应——用户反馈的「按钮坏掉」）。
    Private Sub BtnLaunchMenu_Click(sender As Object, e As RoutedEventArgs) Handles BtnLaunchMenu.MouseLeftButtonUp
        ToggleLaunchMenu()
    End Sub
    Private Sub LabLaunchMenuIcon_Click(sender As Object, e As EventArgs) Handles LabLaunchMenuIcon.Click
        ToggleLaunchMenu()
    End Sub

    '菜单上次被关掉的时刻。
    'StaysOpen=False 时，再点一次按钮会先让 WPF 把 Popup 关掉、再触发按钮的 Click，
    '此时 IsOpen 已经是 False——天真的实现会立刻又把它打开，表现就是「怎么点都收不起来」。
    '用 250ms 的时间窗挡掉这一次，就能让同一个按钮既负责展开也负责收起。
    Private LaunchMenuClosedTick As Integer = 0

    Private Sub ToggleLaunchMenu()
        Try
            If McLaunchLoader.State = LoadState.Loading Then Return
            If PopupLaunchMenu.IsOpen Then
                CloseLaunchMenu()
                Return
            End If
            If GetTimeMs() - LaunchMenuClosedTick < 250 Then Return '这次点击刚刚把菜单关掉，别再打开
            OpenLaunchMenu()
        Catch ex As Exception
            Logger.Error(ex, "切换启动按钮菜单失败")
        End Try
    End Sub

    Private Sub OpenLaunchMenu()
        '没有选中实例时不需要「版本设置」
        ItemMenuSetup.Visibility = If(McInstanceSelected Is Nothing, Visibility.Collapsed, Visibility.Visible)
        '复位动画初值（Popup 关着时改值不会闪）；真正的展开动画在 Opened 里跑
        CardLaunchMenu.Opacity = 0
        TransformLaunchMenuScale.ScaleX = 0.94
        TransformLaunchMenuScale.ScaleY = 0.94
        TransformLaunchMenuPos.Y = 10
        RotateLaunchMenuIcon(180, New AniEaseOutBack(AniEasePower.Weak), 240)
        PopupLaunchMenu.IsOpen = True
        Logger.Info("打开启动按钮菜单")
    End Sub

    Private Sub PopupLaunchMenu_Opened(sender As Object, e As EventArgs) Handles PopupLaunchMenu.Opened
        '放在 Opened 里跑：Popup 首次打开时内容才 Loaded，早于此开始动画会被首帧覆盖
        If Not MenuCard.PopupAnimEnabled Then
            '个性化关闭了弹卡动画：直接呈现终态
            TransformLaunchMenuScale.ScaleX = 1
            TransformLaunchMenuScale.ScaleY = 1
            TransformLaunchMenuPos.Y = 0
            CardLaunchMenu.Opacity = 1
            Return
        End If
        AniStart({
            AaOpacity(CardLaunchMenu, MenuCard.PopupBaseOpacity - CardLaunchMenu.Opacity, 130),
            AaDouble(Sub(i) TransformLaunchMenuScale.ScaleX += i, 1 - TransformLaunchMenuScale.ScaleX, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformLaunchMenuScale.ScaleY += i, 1 - TransformLaunchMenuScale.ScaleY, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformLaunchMenuPos.Y += i, -TransformLaunchMenuPos.Y, 220, , New AniEaseOutFluent(AniEasePower.Middle))
        }, "Launch Menu Popup")
    End Sub

    Private Sub CloseLaunchMenu()
        Try
            If Not PopupLaunchMenu.IsOpen Then Return
            If Not MenuCard.PopupAnimEnabled Then
                PopupLaunchMenu.IsOpen = False
                Return
            End If
            AniStop("Launch Menu Popup")
            AniStart({
                AaOpacity(CardLaunchMenu, -CardLaunchMenu.Opacity, 80),
                AaDouble(Sub(i) TransformLaunchMenuScale.ScaleX += i, 0.96 - TransformLaunchMenuScale.ScaleX, 90),
                AaDouble(Sub(i) TransformLaunchMenuScale.ScaleY += i, 0.96 - TransformLaunchMenuScale.ScaleY, 90),
                AaCode(Sub() PopupLaunchMenu.IsOpen = False, , True)
            }, "Launch Menu Popup")
        Catch ex As Exception
            Logger.Warn(ex, "关闭启动按钮菜单失败")
        End Try
    End Sub

    '关闭统一在这里收尾（点外面、点菜单项、代码关闭都会走到），保证箭头一定转回去
    Private Sub PopupLaunchMenu_Closed(sender As Object, e As EventArgs) Handles PopupLaunchMenu.Closed
        LaunchMenuClosedTick = GetTimeMs()
        RotateLaunchMenuIcon(0, New AniEaseOutFluent, 200)
    End Sub

    Private Sub ItemMenuSelect_Click(sender As Object, e As MouseButtonEventArgs) Handles ItemMenuSelect.Click
        PopupLaunchMenu.IsOpen = False
        BtnVersion_Click()
    End Sub
    Private Sub ItemMenuSetup_Click(sender As Object, e As MouseButtonEventArgs) Handles ItemMenuSetup.Click
        PopupLaunchMenu.IsOpen = False
        BtnMore_Click()
    End Sub
    ' 把下拉箭头转到指定角度（相对当前角度做增量动画，避免连点叠加出错）
    Private Sub RotateLaunchMenuIcon(TargetAngle As Double, Ease As AniEase, Time As Integer)
        Try
            Dim Rt = TryCast(LabLaunchMenuIcon.RenderTransform, RotateTransform)
            If Rt Is Nothing Then
                Rt = New RotateTransform(0)
                LabLaunchMenuIcon.RenderTransform = Rt
                LabLaunchMenuIcon.RenderTransformOrigin = New Point(0.5, 0.5)
            End If
            AniStart(AaRotateTransform(LabLaunchMenuIcon, TargetAngle - Rt.Angle, Time, , Ease), "Launch Menu Arrow")
        Catch ex As Exception
            Logger.Warn(ex, "旋转启动菜单箭头失败")
        End Try
    End Sub
End Class
