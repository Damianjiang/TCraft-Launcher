Imports System.ComponentModel
Imports System.Windows.Interop

Public Class FormMain

#Region "基础"


    '窗口加载
    Private IsWindowLoadFinished As Boolean = False
    '首帧后的收尾工作（翻译/界面语言）是否已执行，防止 ContentRendered 与兜底计时器重复跑
    Private _DeferredStartupDone As Boolean = False
    '主窗口首帧是否已渲染。首帧前暂缓高成本渲染（如背景高斯模糊），首帧后补齐。
    Private HasRenderedFirstFrame As Boolean = False
    ''' <summary>
    ''' 当前是否处于 WPF 软件渲染（RenderCapability.Tier = 0）。远程桌面会话、无独立 GPU 的虚拟机上恒为 True。
    ''' 软件渲染下阴影、半透明、整窗动画、Effect 都以 CPU 逐像素合成，成本成倍上升，启动路径据此降级。
    ''' </summary>
    Public Shared ReadOnly Property IsSoftwareRendering As Boolean
        Get
            If _IsSoftwareRendering Is Nothing Then _IsSoftwareRendering = (System.Windows.Media.RenderCapability.Tier >> 16) = 0
            Return _IsSoftwareRendering.Value
        End Get
    End Property
    Private Shared _IsSoftwareRendering As Boolean? = Nothing

    '背景视频层：原先在 XAML 中即构造 MediaElement，首个实例会初始化
    'WPF 媒体栈（实测数十至百余毫秒），而默认 Collapsed、多数用户不用背景视频——纯浪费。
    '改为首次真正需要时创建，并插入 PanForm 的 ImgBack(索引 0) 之后，保持原 XAML 层级。
    Private _VidBack As MediaElement = Nothing
    ''' <summary>背景视频层是否已创建（不触发构造的判断用）。</summary>
    Public ReadOnly Property HasVidBack As Boolean
        Get
            Return _VidBack IsNot Nothing
        End Get
    End Property
    ''' <summary>背景视频层。首次访问时创建并挂入 PanForm（媒体事件在此接线）。</summary>
    Public ReadOnly Property VidBack As MediaElement
        Get
            If _VidBack Is Nothing Then
                _VidBack = New MediaElement With {
                    .IsHitTestVisible = False, .Stretch = Stretch.UniformToFill,
                    .LoadedBehavior = MediaState.Manual, .UnloadedBehavior = MediaState.Manual,
                    .Visibility = Visibility.Collapsed,
                    .HorizontalAlignment = HorizontalAlignment.Stretch, .VerticalAlignment = VerticalAlignment.Stretch}
                Grid.SetRow(_VidBack, 1)
                PanForm.Children.Insert(1, _VidBack)
                AddHandler _VidBack.MediaEnded, Sub(s, ev) ModVideo.VideoOnMediaEnded()
            End If
            Return _VidBack
        End Get
    End Property

    Public Sub New()
        ApplicationStartTick = GetTimeMs()
        Dim Seg = GetTimeMs() '启动性能分段计时（仅日志）
        'TClKit.Wpf 初始化从 Application_Startup 挪来：其全部内容是 ModernTooltipService
        '的 DP 注册与类处理器注册，必须在任何带 ToolTip 的控件实例化之前完成——本窗口的控件
        '在下方 InitializeComponent 才创建，此处调用正好卡在最后可行时刻，把 5-30ms 移出更早的关键路径
        TClKit.Wpf.Main.Init()
        '窗体参数初始化
        FrmMain = Me
        '左栏与右栏对称：都不在 New 里构造，改到窗口首帧之后（EnsureLaunchLeftCreated / EnsureLaunchRightCreated）。
        'New 阶段仍保留日志占位，便于分段计时。
        LogStartupSegment("左/右栏延迟到首帧后构造", Seg)
        '版本号改变
        Dim LastVersion As Integer = Settings.Get(Of Integer)("SystemLastVersionReg")
        If LastVersion < VersionCode Then
            '触发升级
            UpgradeSub(LastVersion)
        ElseIf LastVersion > VersionCode Then
            '触发降级
            DowngradeSub(LastVersion)
        End If
        '版本隔离设置迁移
        If Not Settings.HasSaved("LaunchArgumentIndieV2") Then
            If Settings.HasSaved("LaunchArgumentIndie") Then
                Logger.Info("从老 TCL 迁移版本隔离")
                Settings.Set("LaunchArgumentIndieV2", Settings.Get(Of Integer)("LaunchArgumentIndie"))
            ElseIf HasIniKey("Setup", "LaunchVersionSelect") Then
                Logger.Info("从老 TCL 升级，但此前未调整版本隔离，使用老的版本隔离默认值")
                Settings.Set("LaunchArgumentIndieV2", Settings.GetDefault("LaunchArgumentIndie"))
            Else
                Logger.Info("全新的 TCL，使用新的版本隔离默认值")
                Settings.Set("LaunchArgumentIndieV2", Settings.GetDefault("LaunchArgumentIndieV2"))
                'Settings.Set 对「与当前值相同」会早退且不落盘，导致 HasSaved 永远为 False、
                '本分支每次启动都重跑；这里直接写 ini 落盘标记，下次启动即跳过
                WriteIni("Setup", "LaunchArgumentIndieV2", Settings.GetDefault("LaunchArgumentIndieV2"))
            End If
        End If
        LogStartupSegment("版本/隔离迁移", Seg)
        '刷新主题（ThemeRefresh 保留在 InitializeComponent 之前，
        '此时 Application.Current.Resources 已可用，先把正确的主题色写进资源字典；
        'ThemeRefreshMain 的守卫已在放宽为 PanTitle 非 null，
        '所以 InitializeComponent 之后调用它即可让首帧直接渲染用户主题色，不再闪默认蓝）
        '
        ThemeRefresh(Settings.Get(Of Integer)("UiLauncherTheme"))
        LogStartupSegment("主题检查与刷新", Seg)
        '注册拖拽事件（不能直接加 Handles，否则没用）
        [AddHandler](DragDrop.DragEnterEvent, New DragEventHandler(AddressOf HandleDrag), handledEventsToo:=True)
        [AddHandler](DragDrop.DragOverEvent, New DragEventHandler(AddressOf HandleDrag), handledEventsToo:=True)
        '加载 UI
        InitializeComponent()
        LogStartupSegment("InitializeComponent(FormMain)", Seg)
        'XAML 中 PanTitle 背景用的是 StaticResource ColorObject4（编译时固定为默认蓝），
        '运行时改字典不会让已解析的 StaticResource 控件变色。
        'ThemeRefreshMain 直接给 PanTitle/PanForm 赋 Background，首帧前就能覆盖掉默认蓝。
        'InitializeComponent 完成后 PanTitle 已存在，直接调用，无需第二次 ThemeRefresh（会命中 ThemeNow 早退）。
        Try
            ThemeRefreshMain()
        Catch ex As Exception
            Logger.Warn(ex, "首帧主题背景刷新失败")
        End Try
        Opacity = 0
        '窗口尺寸必须在首次布局之前定好：原来在 Loaded 里才赋 Width/Height，会让整棵可视树先按 XAML 里的
        '默认值 850×500 完整 Measure/Arrange 一次、再按设置值重排一次（启动关键路径上白花一整轮布局）。
        Try
            Height = Settings.Get(Of Integer)("WindowHeight")
            Width = Settings.Get(Of Integer)("WindowWidth")
        Catch ex As Exception '修复历史缺陷
            Logger.Error(ex, "读取窗口默认大小失败", LogBehavior.Toast)
            Height = MinHeight + 100
            Width = MinWidth + 100
        End Try
        '开启管理员权限下的文件拖拽
        If WindowsUtils.HasAdminRole() Then
            Static Helper As New DragHelper
            AddHandler SourceInitialized,
            Sub()
                Dim WpfHelper As New WindowInteropHelper(Me)
                Helper.HwndIntPtrSource = HwndSource.FromHwnd(WpfHelper.Handle)
                Helper.AddHook()
            End Sub
            AddHandler Closing, Sub() Helper.RemoveDragHook()
            AddHandler Helper.DragDrop, Sub() FileDrag(Helper.DropFilePaths)
        End If
        '切换到首页：
        '主页左右栏改回 Show 之前同步构造。r105 曾为缩短「窗口出现」把它们推迟到首帧后，
        '但代价是首帧是「空壳」、内容随后分批弹入，体感反而更差。现在 r111/r112 的 JIT 档案
        '与预热已把建页成本压低，同步构造后首帧即完整主页（一次到位）。
        '两个 Ensure 均幂等，Loaded 里原有的 Background 队列调用保留为兜底（会直接早退）。
        EnsureLaunchLeftCreated()
        EnsureLaunchRightCreated()
        '背景图 + 壁纸取色同样提前（消「蓝一下」，首帧观感重构）：
        'BackgroundRefresh 会从 TCL\Pictures 随机选图加载（WebP 解码）并经 ApplyWallpaperTheme
        '把整套主题色染成壁纸色——原先这一步在首帧后的 Background 队列里，导致首帧是
        '「无壁纸 + 默认主题色」的空壳（用户反馈的「蓝色一下再完全加载」）。
        '提前到 Show 之前，首帧即「壁纸 + 壁纸主题色 + 完整主页」；Loaded 里的原调用已删除（避免二次随机换图）
        Try
            PageSetupUI.BackgroundRefresh(False, True)
            UpdateBackgroundAndTitleBar()
        Catch ex As Exception
            Logger.Warn(ex, "首帧前更新背景失败（Loaded 中会重试）")
        End Try
        '模式提醒
        If BuildType = BuildTypes.Debug Then Hint(ModLang.L("[开发者模式] TCL 正以开发者模式运行，这可能会造成严重的性能下降，请务必立即向开发者反馈此问题！"), HintType.Red)
        If ModeDebug Then Hint(ModLang.L("[调试模式] TCL 正以调试模式运行，这可能会导致性能下降，若无必要请不要开启！"))
        '尽早执行的加载池
        McFolderListLoader.Start(0) '为了让下载已存在文件检测可以正常运行，必须跑一次；为了让启动按钮尽快可用，需要尽早执行；为了与 PageLaunchLeft 联动，需要为 0 而不是 GetUuid

        LogStartupSegment("主页构造+背景+启动加载池", Seg)
        Logger.Info($"第二阶段加载用时：{GetTimeMs() - ApplicationStartTick} ms")
        '插桩：New 结束 → SourceInitialized 的空档（此前实测 ~670ms 无人认领），
        '用 Style/Template 应用时机把它拆成可归因的段
        NewEndTick = GetTimeMs()
    End Sub
    Private NewEndTick As Long = -1

    ''' <summary>插桩：窗口样式应用时机（发生在 Show 内部、建句柄前）。</summary>
    Protected Overrides Sub OnStyleChanged(oldStyle As Style, newStyle As Style)
        If NewEndTick >= 0 Then Logger.Info($"[启动耗时] OnStyleChanged（New 结束后 {GetTimeMs() - NewEndTick} ms）")
        MyBase.OnStyleChanged(oldStyle, newStyle)
    End Sub

    ''' <summary>插桩：窗口模板应用时机（发生在 Show 内部、建句柄前后）。</summary>
    Protected Overrides Sub OnTemplateChanged(oldTemplate As ControlTemplate, newTemplate As ControlTemplate)
        If NewEndTick >= 0 Then Logger.Info($"[启动耗时] OnTemplateChanged（New 结束后 {GetTimeMs() - NewEndTick} ms）")
        MyBase.OnTemplateChanged(oldTemplate, newTemplate)
    End Sub
    ''' <summary>
    ''' 巨型 Logo 几何延迟赋值：从 FormMain.xaml 挪来的 4 条 Path 数据，
    ''' 原先在 BAML 解析时经 GeometryConverter 同步解析。这些按钮（关闭 Minecraft/返回顶部/
    ''' 任务管理/投降）默认 Collapsed，首帧不可见，解析纯属启动关键路径上的白花。
    ''' 在窗口 Loaded 后、页面构造前的 Background 队列中调用。
    ''' </summary>
    Private Sub ApplyExtraButtonLogos()
        Try
            BtnExtraShutdown.Logo = "M802.152 143.682c31.236 22.212 59.004 47.379 83.297 75.486 24.297 28.116 45.12 58.481 62.472 91.105 17.356 32.624 30.543 67.159 39.569 103.6 9.023 36.442 13.533 73.403 13.533 110.887 0 64.556-12.32 125.116-36.96 181.69-24.643 56.57-57.963 105.854-99.956 147.847-41.994 41.998-91.278 75.313-147.851 99.955-56.57 24.642-117.134 36.966-181.684 36.966-63.86 0-124.08-12.324-180.649-36.966-56.575-24.642-106.032-57.957-148.37-99.955-42.344-41.993-75.662-91.277-99.956-147.846C81.3 649.876 69.155 589.315 69.155 524.76c0-36.788 4.337-72.884 13.016-108.283 8.678-35.4 20.996-69.065 36.96-100.999 15.969-31.926 35.75-61.774 59.35-89.537 23.6-27.768 49.976-52.757 79.133-74.969 15.272-11.107 31.755-15.271 49.456-12.497 17.697 2.782 32.1 11.455 43.21 26.03 11.108 14.583 15.27 30.891 12.496 48.94-2.781 18.047-11.454 32.622-26.03 43.732-43.732 31.927-77.225 71.145-100.48 117.653-23.247 46.507-34.873 96.483-34.873 149.93 0 45.812 8.673 89.027 26.03 129.632 17.352 40.605 41.124 76.003 71.317 106.198 30.2 30.2 65.599 54.145 106.204 71.846 40.607 17.697 83.814 26.549 129.631 26.549 45.811 0 89.02-8.852 129.626-26.549 40.606-17.701 76.01-41.647 106.203-71.846 30.195-30.195 54.14-65.593 71.841-106.198 17.702-40.607 26.549-83.82 26.549-129.631 0-54.14-12.49-105.334-37.478-153.577-24.994-48.24-60.048-87.977-105.162-119.213-15.272-10.412-24.468-24.643-27.592-42.69-3.125-18.049 0.518-34.709 10.93-49.983 10.412-14.575 24.643-23.427 42.69-26.547 18.047-3.12 34.698 0.519 49.97 10.93zM534.57 527.888c-18.05 0-33.495-6.421-46.337-19.264-12.841-12.841-19.263-28.287-19.263-46.335V66.635c0-18.048 6.42-33.666 19.263-46.854C501.076 6.593 516.521 0 534.571 0c18.738 0 34.53 6.593 47.371 19.781 12.842 13.188 19.263 28.806 19.263 46.854v395.654c0 18.047-6.42 33.493-19.263 46.335-12.842 12.843-28.633 19.264-47.371 19.264z"
            BtnExtraBack.Logo = "M858.496 188.9024 173.1072 188.9024c-30.2848 0-54.8352-24.5504-54.8352-54.8352L118.272 106.6496c0-30.2848 24.5504-54.8352 54.8352-54.8352l685.3888 0c30.2848 0 54.8352 24.5504 54.8352 54.8352l0 27.4176C913.3312 164.352 888.7808 188.9024 858.496 188.9024L858.496 188.9024zM150.6048 550.8608c0 0 300.0064-240.3584 303.0272-243.328 13.9776-13.5936 31.1808-21.8624 48.8192-24.7552 1.7152-0.3072 3.4304-0.5888 5.1456-0.768 2.7392-0.3072 5.4528-0.3584 8.192-0.3328 2.7392-0.0256 5.4272 0.0256 8.1664 0.3328 1.7408 0.1792 3.4304 0.4864 5.1456 0.768 17.664 2.8928 34.8672 11.1616 48.8192 24.7552 3.0464 2.944 303.0016 243.328 303.0016 243.328 32.384 31.5136 29.6192 63.9744-2.7392 95.5136-32.3328 31.5392-75.648 2.9696-108.0064-28.544l-185.8816-147.1232 0 485.8368c0 30.3104-24.5248 54.8608-54.8352 54.8608l-27.392 0c-30.2848 0-54.8352-24.5504-54.8352-54.8608L447.232 470.7072l-185.8304 147.0976c-32.3584 31.5392-75.6992 60.1344-108.032 28.5696C121.0368 614.8352 118.272 582.3744 150.6048 550.8608L150.6048 550.8608zM150.6048 550.8608"
            BtnExtraDownload.Logo = "M484.693 225.28 314.027 389.12l-68.267-68.267c-13.653-13.653-40.96-13.653-47.787 0-13.653 13.654-13.653 34.134 0 47.787l95.574 95.573c13.653 13.654 34.133 13.654 47.786 0L532.48 273.067c13.653-13.654 13.653-34.134 0-47.787s-34.133-13.653-47.787 0M819.2 341.333H614.4c-20.48 0-34.133 13.654-34.133 34.134S593.92 409.6 614.4 409.6h204.8c20.48 0 34.133-13.653 34.133-34.133s-13.653-34.134-34.133-34.134m0 341.334H614.4c-20.48 0-34.133 13.653-34.133 34.133s13.653 34.133 34.133 34.133h204.8c20.48 0 34.133-13.653 34.133-34.133s-13.653-34.133-34.133-34.133m-477.867-102.4c-75.093 0-136.533 61.44-136.533 136.533s61.44 136.533 136.533 136.533 136.534-61.44 136.534-136.533-61.44-136.533-136.534-136.533m0 204.8c-40.96 0-68.266-27.307-68.266-68.267s27.306-68.267 68.266-68.267S409.6 675.84 409.6 716.8s-27.307 68.267-68.267 68.267"
            BtnExtraApril.Logo = "M124.96 32c-28.64 0-60.064 23.2-60.064 51.904v856.192c0 28.576 30.688 51.904 59.296 51.904s60.064-23.328 60.064-51.904V83.904C184.256 55.2 153.536 32 124.96 32z m616.352 71.264c-168.416 0-168.416-69.024-336.8-69.024-97.216 0-172.256 45.344-172.256 45.344l-0.736 516.736s75.776-45.28 172.96-45.28c168.416 0 168.416 69.024 336.8 69.024 105.344 0 211.168-55.328 211.168-55.328V48.064c0.032 0-105.792 55.2-211.136 55.2z m0 0"
        Catch ex As Exception
            Logger.Warn(ex, "延迟赋值扩展按钮 Logo 失败")
        End Try
    End Sub

    ''' <summary>
    ''' 构造并挂载启动页左栏。幂等，可在任意时刻调用。
    ''' 之所以延迟到窗口首帧之后（与右栏对称，）：
    ''' 主页左栏的 BAML 解析 + 控件树构造 + 启动加载池首次跑，原同步占 FormMain.New 约 291ms；
    ''' 右栏（PageLaunchRight）自起就用同样手法延迟到首帧后，零副作用，
    ''' 左栏对称化即可把这段成本移出「窗口显示」关键路径。首屏观感与之前一致（首帧渲染
    ''' 时左右栏都在，只是从 XAML 解析到窗口可见的时间被拆到两帧）。
    ''' </summary>
    Public Sub EnsureLaunchLeftCreated()
        Application.EnsureDelayedUiLoaded() '页面内容需要延迟字典里的滚动条/文本框等样式
        If _FrmLaunchLeft IsNot Nothing Then Return
        Try
            _FrmLaunchLeft = New PageLaunchLeft
            If Not IsNothing(_FrmLaunchLeft.Parent) Then _FrmLaunchLeft.SetValue(ContentPresenter.ContentProperty, Nothing)
            PanMainLeft.Child = _FrmLaunchLeft
            PageLeft = _FrmLaunchLeft
            '延迟构造的页面必须立刻套一遍当前语言：FormMain_Loaded 的 ApplyToWindow 跑在本方法之前，
            '若上次会话保存了非简中语言，此处不套会让左栏一直停在简中（与 EnsureLaunchRightCreated 一致）
            ModLang.ApplyTo(_FrmLaunchLeft)
        Catch ex As Exception
            Logger.Error(ex, "构造启动页左栏失败")
        End Try
    End Sub
    ''' <summary>
    ''' 构造并挂载启动页右栏（主页）。幂等，可在任意时刻调用。
    ''' 之所以延迟到窗口首帧之后：主页内容本来就是异步生成的，用户看不出差别，
    ''' 但能把这部分 XAML 解析与控件构造移出「窗口显示」的关键路径（约 430ms）。
    ''' </summary>
    Public Sub EnsureLaunchRightCreated()
        Application.EnsureDelayedUiLoaded() '页面内容需要延迟字典里的滚动条/文本框等样式
        If _FrmLaunchRight IsNot Nothing Then Return
        Try
            _FrmLaunchRight = New PageLaunchRight
            PanMainRight.Child = _FrmLaunchRight
            PageRight = _FrmLaunchRight
            _FrmLaunchRight.PageState = MyPageRight.PageStates.ContentStay
            '延迟构造的页面必须立刻套一遍当前语言：FormMain_Loaded 的 ApplyToWindow 跑在本方法之前，
            '若上次会话保存了非简中语言，此处不套会让主页一直停在简中
            ModLang.ApplyTo(_FrmLaunchRight)
            FrmLaunchRight.RefreshAccountSummary() '初始化标题栏账号徽章
        Catch ex As Exception
            Logger.Error(ex, "构造启动页右栏失败")
        End Try
    End Sub
    ''' <summary>
    ''' 根据设置写入 WPF 渲染模式注册表项（Avalon.Graphics 的 DisableHWAcceleration）。
    ''' 该设置在 WPF 渲染系统初始化时读取，因此重启 TCL 后生效。
    ''' </summary>
    Public Shared Sub ApplyHardwareAcceleration(Optional ShowHint As Boolean = True)
        Try
            Dim Disabled As Boolean = Settings.Get(Of Boolean)("SystemDisableHardwareAcceleration")
            Dim Root As Microsoft.Win32.RegistryKey = Microsoft.Win32.Registry.CurrentUser
            Dim RegKey As Microsoft.Win32.RegistryKey = Root.OpenSubKey("Software\Microsoft\Avalon.Graphics", True)
            If RegKey Is Nothing Then RegKey = Root.CreateSubKey("Software\Microsoft\Avalon.Graphics")
            RegKey.SetValue("DisableHWAcceleration", If(Disabled, 1, 0))
            RegKey.Close()
            Logger.Info("已按设置写入 WPF 渲染模式：" & If(Disabled, "软件渲染", "硬件加速"))
            If ShowHint AndAlso RunInUi() Then
                Hint(ModLang.L("硬件加速已") & If(Disabled, ModLang.L("禁用"), ModLang.L("启用")) & ModLang.L("，重启 TCL 后生效。"), If(Disabled, HintType.Blue, HintType.Green), False)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "切换 WPF 渲染模式失败")
        End Try
    End Sub
    ''' <summary>
    ''' 窗口句柄创建（发生在 Show 内部、首次布局之前）。仅用于把「构造完成 → Loaded」这段
    ''' 拆成「建窗 + 分层窗口初始化」和「首次布局」两段，便于定位耗时到底在哪。
    ''' </summary>
    Private Sub FormMain_SourceInitialized(sender As Object, e As EventArgs) Handles Me.SourceInitialized
        Logger.Info($"[启动耗时] 窗口句柄已创建（自进程启动 {MsSinceProcessStart()} ms）")
    End Sub
    Private Sub FormMain_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        ApplicationStartTick = GetTimeMs()
        Dim Seg = GetTimeMs() '启动性能分段计时（仅日志）
        Logger.Info($"[启动耗时] 窗口 Loaded 触发（自进程启动 {MsSinceProcessStart()} ms）")
        '等待启动预热（词典/网络默认值/代理/默认背景释放等）完成。预热通常早已完成，此处近零开销；
        '冷盘极端情况下最多等待数百毫秒——等价于这些工作原本就在 UI 线程同步执行
        Application.WaitStartupWarmup()
        LogStartupSegment("等待启动预热", Seg)
        Handle = New WindowInteropHelper(Me).Handle
        '清除功能隐藏导致的页面锁定，确保所有主页面与子页面都可见（急救版不提供锁定功能）
        Try
            For Each HiddenKey In {"UiHiddenPageDownload", "UiHiddenPageSetup", "UiHiddenPageOther", "UiHiddenSetupLaunch", "UiHiddenSetupUi", "UiHiddenSetupSystem", "UiHiddenOtherAbout"}
                If Settings.Get(Of Boolean)(HiddenKey) Then Settings.Set(HiddenKey, False)
            Next
        Catch ex As Exception
            Logger.Error(ex, "清除页面隐藏锁定失败")
        End Try
        LogStartupSegment("清除隐藏锁定", Seg)
        '异常退出检测：上次非正常退出（断电/强杀/崩溃）时提示一次，不影响启动流程
        Try
            If Not Settings.Get(Of Boolean)("SystemLastExitClean") AndAlso Settings.Get(Of Integer)("SystemCount") > 2 Then
                RunInNewThread(
                Sub()
                    Dim Result As Integer = MyMsgBox(ModLang.L("上次启动器可能异常退出（如断电、强制结束进程或崩溃）。") & vbCrLf & ModLang.L("如果游戏或启动器出现问题，可以打开日志查看原因。"), ModLang.L("异常退出提示"), ModLang.L("打开日志"), ModLang.L("知道了"))
                    '用户已经看过并确认过这条提示：立刻记成「正常」，同一件事不要每次启动都再弹
                    Try
                        Settings.Set("SystemLastExitClean", True)
                    Catch ex As Exception
                        Logger.Warn(ex, "确认异常退出提示后写入退出标记失败")
                    End Try
                    If Result = 1 Then
                        Try
                            StartProcess(Paths.Base & "TCL\Log1.txt")
                        Catch ex As Exception
                            Logger.Warn(ex, "异常退出提示：打开日志失败")
                        End Try
                    End If
                End Sub, "Abnormal Exit Hint")
            End If
            Settings.Set("SystemLastExitClean", False) '本轮会话开始：若未走到正常退出，下次启动将提示
        Catch ex As Exception
            Logger.Warn(ex, "异常退出检测失败")
        End Try
        '搜索索引空闲预热：启动 8 秒后按子页分片构建，每个分片间隔 150ms，
        '避免一次性实例化 9 个设置子页造成 UI 集中冻结
        RunInNewThread(
        Sub()
            Thread.Sleep(8000)
            For Each WarmId In {FormMain.PageSubType.SetupLaunch, FormMain.PageSubType.SetupLink, FormMain.PageSubType.SetupUI,
                                FormMain.PageSubType.SetupSystem, FormMain.PageSubType.SetupJava, FormMain.PageSubType.SetupLog,
                                FormMain.PageSubType.SetupCache, FormMain.PageSubType.SetupNetwork, FormMain.PageSubType.SetupMore}
                Thread.Sleep(150)
                Dim Id = WarmId
                RunInUi(Sub()
                            Try
                                If FrmSetupLeft Is Nothing Then FrmSetupLeft = New PageSetupLeft
                                FrmSetupLeft.WarmSearchIndexPage(Id)
                            Catch ex As Exception
                                Logger.Warn(ex, "预热设置搜索索引失败")
                            End Try
                        End Sub)
            Next
        End Sub, "Search Index Warmup")
        '读取设置
        '个性化页新增项，必须在首次布局前套上，否则会看到一帧「旧圆角/旧标题栏高度」
        ApplyWindowRadius(Settings.Get(Of Integer)("UiWindowRadius"))
        ApplyTitleBarHeight(Settings.Get(Of Integer)("UiTitleBarHeight"))
        ApplyTitleAccountBadge(Settings.Get(Of Boolean)("UiTitleAccountBadge"))
        ApplyBackgroundRotate(Settings.Get(Of Integer)("UiBackgroundRotate"))
        UpdateBackgroundAndTitleBar()
        LogStartupSegment("更新背景/标题栏", Seg)
        '功能隐藏刷新实测吃掉 470ms（只是几十个 Visibility 赋值，代价在于每次赋值都让顶部栏与整窗失效重排）。
        '它默认什么也不改（除非用户手动隐藏过页面），挪到首帧之后执行：可见结果完全一致，但不再挡在
        '"启动画面 → 主界面"的关键路径上。页面加载时的各个入口（PageSetupLeft/PageOtherLeft 等）仍会各自调用它。
        Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background, New Action(
            Sub()
                Try
                    PageSetupUI.HiddenRefresh()
                Catch ex As Exception
                    Logger.Warn(ex, "启动时刷新功能隐藏状态失败")
                End Try
            End Sub))
        LogStartupSegment("注册 HiddenRefresh 延迟执行", Seg)
        '（原先这里的 BackgroundRefresh 已提前到 FormMain.New 首帧前执行——
        ' 首帧即有壁纸与壁纸主题色；这里不再二次随机选图，避免窗口出现后背景换一张）
        '背景音乐/视频的扫描与初始化很贵（MusicListInit 要枚举 Musics 目录并读标签、VideoRefreshPlay 要探测
        '媒体源），原来同步跑在 Loaded 里会直接推迟「启动画面 → 主界面」的渲染（实测约 300~450ms）。
        '这里只保留事件注册，真正的初始化丢到首帧之后的 Background 优先级，音频/视频晚几十毫秒起播无感。
        '（VidBack.MediaEnded 已随惰性构造接线，）
        Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background, New Action(
            Sub()
                Try
                    MusicRefreshPlay(False, True)
                Catch ex As Exception
                    Logger.Warn(ex, "启动时初始化背景音乐失败")
                End Try
                Try
                    VideoRefreshPlay(False, True)
                Catch ex As Exception
                    Logger.Warn(ex, "启动时初始化背景视频失败")
                End Try
            End Sub))
        LogStartupSegment("背景/音乐/视频", Seg)
        '自定义字体（启动时自动应用字体文件夹里的 .ttf/.otf，跟背景视频同范式）
        ModFont.FontInit()
        LogStartupSegment("字体初始化", Seg)
        '扩展按钮
        BtnExtraDownload.ShowCheck = AddressOf BtnExtraDownload_ShowCheck
        BtnExtraBack.ShowCheck = AddressOf BtnExtraBack_ShowCheck
        BtnExtraApril.ShowCheck = AddressOf BtnExtraApril_ShowCheck
        BtnExtraShutdown.ShowCheck = AddressOf BtnExtraShutdown_ShowCheck
        BtnExtraApril.ShowRefresh()
        '初始化尺寸改变
        Dim Resizer As New MyResizer(Me)
        Resizer.addResizerDown(ResizerB)
        Resizer.addResizerLeft(ResizerL)
        Resizer.addResizerLeftDown(ResizerLB)
        Resizer.addResizerLeftUp(ResizerLT)
        Resizer.addResizerRight(ResizerR)
        Resizer.addResizerRightDown(ResizerRB)
        Resizer.addResizerRightUp(ResizerRT)
        Resizer.addResizerUp(ResizerT)
        LogStartupSegment("尺寸/扩展按钮/Resizer", Seg)
        '加载窗口
        ThemeRefreshMain()
        LogStartupSegment("ThemeRefreshMain", Seg)
        '窗口尺寸已在 FormMain.New（InitializeComponent 之后、首次布局之前）按设置赋好，
        '这里不再重复赋值——原来在这里才设 Width/Height 会让可视树先按 XAML 默认的 850×500 布局一次、
        '再按保存值重新布局一次，白花一整轮全窗口 Measure/Arrange。
        'MinHeight = 50
        'MinWidth = 50
        Topmost = False
        '主界面即将显示，直接关闭启动画面（不再使用淡出动画，避免淡出被界面渲染阻塞而残留）
        StartSplashFallbackTimer()
        '更改窗口
        Dim Centered As Boolean = True
        If Settings.Get(Of Boolean)("UiRememberWindowPos") Then
            Try
                Dim PosX As Double = Settings.Get(Of Integer)("UiWindowPosX")
                Dim PosY As Double = Settings.Get(Of Integer)("UiWindowPosY")
                '用虚拟屏幕（含所有显示器）判定，副屏坐标为负值时也能正确恢复
                Dim VL As Double = SystemParameters.VirtualScreenLeft
                Dim VT As Double = SystemParameters.VirtualScreenTop
                Dim VR As Double = VL + SystemParameters.VirtualScreenWidth
                Dim VB As Double = VT + SystemParameters.VirtualScreenHeight
                '至少要留出 120×60 的可见区域，避免窗口整个跑到屏幕外之后点不到
                If PosX > VL - Width + 120 AndAlso PosX < VR - 120 AndAlso PosY > VT - 10 AndAlso PosY < VB - 60 Then
                    Left = PosX
                    Top = PosY
                    Centered = False
                End If
            Catch ex As Exception
                Logger.Warn(ex, "恢复窗口位置失败，改为居中显示")
            End Try
        End If
        If Centered Then
            'My.Computer.Screen(WinForms) 首触会加载 System.Windows.Forms（10-20ms），
            'SystemParameters.WorkArea 同为主屏工作区且已是 WPF 单位，直接等价替换
            Top = (SystemParameters.WorkArea.Height - Height) / 2
            Left = (SystemParameters.WorkArea.Width - Width) / 2
        End If
        IsSizeSaveable = True
        ShowWindowToTop()
        Dim HwndSource As Interop.HwndSource = PresentationSource.FromVisual(Me)
        HwndSource.AddHook(New Interop.HwndSourceHook(AddressOf WndProc))
        '入场动画：透明度 + 位移 + 旋转 + Logo 淡入，全部保留（用户明确要求不能被裁剪）。
        '软件渲染（RenderCapability.Tier = 0：远程桌面/无 GPU）机自动走终态支路——
        '入场动画在这类机器上由 CPU 逐帧合成，460ms 的动画实测被拖成 ~2s（日志「两个动画帧间隔 1454 ms」），
        '会把「窗口可见」整体后推 1.5~2s。跳过只省时间、终态与动画播完时完全一致（观感等价）。
        '硬件加速机（Tier>0）不受任何影响，全程播放原动画；「跳过入场动画」设置仍可手动强制开启。
        Dim SkipIntroAnim As Boolean = Settings.Get(Of Boolean)("UiSkipIntroAnim")
        If IsSoftwareRendering AndAlso Not SkipIntroAnim Then
            Logger.Info("软件渲染（Tier 0）：自动跳过窗口入场动画（CPU 逐帧合成代价远超收益，终态与动画播完时一致）")
        End If
        If SkipIntroAnim OrElse IsSoftwareRendering Then
            '设置里关闭了入场动画：直接落到终态（透明 + 位置 + 旋转 + Logo 一次到位）。
            '注意 AniControlEnabled 在 Application 启动时 +1，必须在这里减回去，否则控件动画会一直处于禁用状态。
            Opacity = Settings.Get(Of Integer)("UiLauncherTransparent") / 1000 + 0.4
            TransformPos.Y = 0
            TransformRotate.Angle = 0
            PanBack.RenderTransform = Nothing
            For Each Logo In {CType(ShapeTitleLogo, FrameworkElement), CType(LabTitleLogo, FrameworkElement), CType(ImageTitleLogo, FrameworkElement)}
                Logo.Opacity = 1
            Next
            IsWindowLoadFinished = True
            If AniControlEnabled > 0 Then AniControlEnabled -= 1
            '日志区分触发来源：设置手动开启 / Tier 0 自动 / 两者叠加
            Dim SkipReason As String = If(SkipIntroAnim, "设置开启", "软件渲染自动")
            If SkipIntroAnim AndAlso IsSoftwareRendering Then SkipReason = "设置开启 + 软件渲染"
            Logger.Info($"窗口入场动画已跳过（{SkipReason}，终态一次到位）")
        Else
            AniStart({
                    AaCode(Sub() AniControlEnabled -= 1, 50),
                    AaOpacity(Me, Settings.Get(Of Integer)("UiLauncherTransparent") / 1000 + 0.4, 260, 100, New AniEaseOutFluent(AniEasePower.Weak)),
                    AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 480, 100, New AniEaseOutFluent(AniEasePower.Middle)),
                    AaDouble(Sub(i) TransformRotate.Angle += i, -TransformRotate.Angle, 460, 100, New AniEaseOutFluent(AniEasePower.Middle)),
                    AaCode(AddressOf LogoFadeIn, 220),
                    AaCode(
                    Sub()
                        PanBack.RenderTransform = Nothing
                        IsWindowLoadFinished = True
                        Logger.Info($"DPI：{DPI}，系统版本：{Environment.OSVersion.VersionString}，TCL 位置：{PathExe}")
                    End Sub, , True)
                }, "Form Show")
        End If
        'Timer 启动
        AniStart()
        TimerMainStart()
        '巨型 Logo 几何延迟赋值：4 条 1.2~2.5KB 的 Path 字符串原先在 BAML 解析时经
        'GeometryConverter 解析（InitializeComponent 内），这些按钮默认 Collapsed，纯白花——
        '挪到首帧后的 Background 队列，排在页面构造之前，按钮不会出现空图标
        Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background, New Action(AddressOf ApplyExtraButtonLogos))
        '右栏（主页）在首帧之后再构造，避免阻塞窗口显示（主页内容本身异步生成，用户无感）
        Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background, New Action(Sub() EnsureLaunchLeftCreated()))
        Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background, New Action(Sub() EnsureLaunchRightCreated()))
        '加载池
        RunInNewThread(
        Sub()
            '首个网络请求在本线程发出，先确保后台预热（ServicePointManager/HTTP 代理）已完成
            Application.WaitStartupWarmup()
            'EULA 默认视为已同意，不再弹窗
            Const EulaVersion As Integer = 2
            If Settings.Get(Of Integer)("SystemEulaVersion") < EulaVersion Then
                Settings.Set("SystemEulaVersion", EulaVersion)
            End If
            '启动加载器池
            Try
                JavaInit()
                DlClientListMojangLoader.Start(1) 'TCL 会同时根据这里的加载结果决定是否使用官方源进行下载
                RunCountSub()
                ServerLoader.Start()
                RunInNewThread(AddressOf TryClearTaskTemp, "TryClearTaskTemp", ThreadPriority.BelowNormal)
            Catch ex As Exception
                Logger.Error(ex, "初始化加载池运行失败")
            End Try
            '上报
            Telemetry("启动")
        End Sub, "初始化", ThreadPriority.Lowest)

        LogStartupSegment("窗口定位+动画启动", Seg)
        Logger.Info($"第三阶段加载用时：{GetTimeMs() - ApplicationStartTick} ms")
        '翻译文件加载、全窗口套语言、开发快照这三件事原来同步跑在这里（约 100~300ms），会推迟窗口首帧与
        '启动画面淡出。它们不影响首屏骨架，已移到 FormMain_ContentRendered → RunDeferredStartupWork。
    End Sub
    ''' <summary>
    ''' 首帧渲染完成后执行的非首屏收尾工作：加载外置翻译、补套界面语言、开发快照。
    ''' 与 Loaded 分离的目的：把这段时间移出「启动画面 → 主界面可见」的关键路径。
    ''' 幂等：ContentRendered 与启动画面兜底计时器都可能调用，重复调用直接返回。
    ''' </summary>
    Private Sub RunDeferredStartupWork()
        If _DeferredStartupDone Then Return
        _DeferredStartupDone = True
        Dim Seg = GetTimeMs()
        '等待启动预热（ModLang 词典构建等）完成——下方的界面语言应用需要词典就绪；
        '预热在 Startup 入口即启动，到首帧此时点早已完成，此处近零开销
        Application.WaitStartupWarmup()
        LogStartupSegment("等待启动预热（首帧后）", Seg)
        '首帧后立刻合并延迟 UI 资源：覆盖 EnsureLaunch* 之外的构造入口（如 Hint/弹窗），
        '与下方各处的守卫调用幂等
        Application.EnsureDelayedUiLoaded()
        '首帧之后补标题栏 SVG 图标：首次解析要解压 TClCS.dll（内嵌 gz）+ 解析 svg path，
        '放在 FormMain.xaml 里会让 InitializeComponent 多花数百毫秒，直接推迟窗口上屏。
        ApplyTitleIcons()
        '渲染层级（诊断用）：Tier 0 = 软件渲染，此时阴影/半透明/动画都会成倍变慢
        Try
            Logger.Info($"WPF 渲染层级：Tier {System.Windows.Media.RenderCapability.Tier >> 16}（0=软件渲染，2=完整硬件加速）")
        Catch
        End Try        '翻译统一由单个可编辑文件 i18n.json 驱动：启动时确保它存在（不存在则从内置词典自动生成），
        '再把其中全部条目读入内存词典作为语言真相源，让所有界面文案都引用此文件，方便日后直接编辑该文件改文案。
        Try
            Dim I18nPath_ = ModLang.EnsureI18nFile()
            If I18nPath_ IsNot Nothing Then
                If ModLang.ReloadTranslations() Then Logger.Info($"已加载翻译文件 {I18nPath_}")
            End If
        Catch ex As Exception
            Logger.Warn(ex, "启动时处理翻译文件失败（使用内置译文）")
        End Try
        '冷启动时主窗体此刻才真正建成可视树；Application 里那次 ApplyToolLanguage 因 FrmMain 尚未创建而未生效，
        '这里补套一次当前语言，确保上次设定的界面语言（含顶部栏）在重启后也正确显示。
        Try
            ModBase.ApplyToolLanguage(Settings.Get(Of Integer)("ToolLanguage"))
        Catch ex As Exception
            Logger.Warn(ex, "启动时应用界面语言失败")
        End Try
        '语言固定后按语言自适应顶部栏布局（英语标签较宽，紧凑化避免顶到右侧窗口按钮）；
        '切换语言会触发重启，所以只需在启动期此处调用一次
        ApplyTitleLangLayout()
        LogStartupSegment("翻译/界面语言（首帧后）", Seg)
        '开发用界面快照：无桌面环境（远程/沙箱）下 GDI 抓 WPF 窗口只会得到黑图，
        '放一个空的 TCL\.devshot 文件，启动器会在 8 秒后把自己渲染成 TCL\devshot.png
        Try
            If FileUtils.Exists(Paths.Base & "TCL\.devshot") Then RunDevSnapshot()
        Catch ex As Exception
        End Try
        '桌面快捷方式：--launch-instance "<实例名>"
        TryStartupInstance()
    End Sub

    ''' <summary>
    ''' 自动选中并启动命令行指定的实例（由桌面快捷方式写入）。
    ''' 实例列表是异步加载的，这里用 UI 定时器轮询，最多等 15 秒；
    ''' 找不到实例、没账户、启动器正忙时都安静放弃，不影响正常使用。
    ''' </summary>
    Private Sub TryStartupInstance()
        If String.IsNullOrEmpty(Application.StartupInstanceName) Then Return
        Dim Wanted As String = Application.StartupInstanceName
        Dim Wait As New System.Windows.Threading.DispatcherTimer With {.Interval = TimeSpan.FromMilliseconds(250)}
        Dim Tries As Integer = 0
        AddHandler Wait.Tick,
            Sub(sender, args)
                Try
                    Tries += 1
                    If Tries > 60 Then
                        Wait.Stop()
                        Logger.Warn($"快捷方式指定的实例「{Wanted}」在 15 秒内未出现在实例列表中，已取消自动启动")
                        Application.StartupInstanceName = ""
                        Return
                    End If
                    Dim Target As McInstance = Nothing
                    For Each Pair In McInstanceList.ToArray
                        If Pair.Value Is Nothing Then Continue For
                        For Each One In Pair.Value
                            If One IsNot Nothing AndAlso String.Equals(One.Name, Wanted, StringComparison.OrdinalIgnoreCase) Then
                                Target = One
                                Exit For
                            End If
                        Next
                        If Target IsNot Nothing Then Exit For
                    Next
                    If Target Is Nothing Then Return
                    Wait.Stop()
                    Application.StartupInstanceName = ""
                    Logger.Info($"快捷方式启动：选中实例「{Target.Name}」")
                    PageInstanceLeft.Instance = Target
                    PageChange(New PageStackData With {.Page = PageType.Launch})
                    '等页面切换动画走完再点启动：LaunchButtonClick 内部有 PageState 守卫，
                    '切页刚发起时 PageState 还不是 ContentEnter/ContentStay，会直接被 Return 掉
                    Dim Delay As New System.Windows.Threading.DispatcherTimer With {.Interval = TimeSpan.FromMilliseconds(800)}
                    AddHandler Delay.Tick,
                        Sub(s2, a2)
                            Delay.Stop()
                            Try
                                If FrmLaunchRight Is Nothing Then Return
                                'LaunchButtonClick 内部会检查 PageState / 加载状态 / 按钮可用性，
                                '不满足就直接 Return，这里不再重复判断（也避免访问页面私有控件）
                                FrmLaunchRight.LaunchButtonClick()
                            Catch ex As Exception
                                Logger.Warn(ex, "快捷方式自动启动失败")
                            End Try
                        End Sub
                    Delay.Start()
                Catch ex As Exception
                    Wait.Stop()
                    Logger.Warn(ex, "快捷方式自动启动流程异常")
                End Try
            End Sub
        Wait.Start()
    End Sub
    ''' <summary>
    ''' 补上标题栏的 SVG 图标（原先写在 FormMain.xaml 的 SvgIcon 属性上）。
    ''' 首次解析一个图标要解压 TClCS.dll、枚举内嵌资源、解析 svg path，7 个图标串起来是
    ''' InitializeComponent 里最贵的一段；移到首帧之后，窗口能早几百毫秒上屏，图标随淡入动画一起出现。
    ''' </summary>
    Private Sub ApplyTitleIcons()
        Try
            BtnTitleLang.SvgIcon = "lucide/languages"
            BtnTitleSelect0.SvgIcon = "lucide/play"
            BtnTitleSelect5.SvgIcon = "lucide/box"
            BtnTitleSelect1.SvgIcon = "lucide/download"
            BtnTitleSelect2.SvgIcon = "lucide/globe"
            BtnTitleSelect4.SvgIcon = "lucide/grid-2x2"
            BtnTitleInner.SvgIcon = "lucide/arrow-left"
        Catch ex As Exception
            Logger.Warn(ex, "补标题栏图标失败")
        End Try
    End Sub
    ''' <summary>
    ''' 把当前界面渲染成 TCL\devshot.png（仅当运行目录存在 .devshot 开关文件时由 Loaded 触发）。
    ''' </summary>
    Public Sub RunDevSnapshot()
        RunInNewThread(
            Sub()
                Try
                    Thread.Sleep(3500)
                    RunInUi(
                        Sub()
                            Try
                                Dim SnapshotW As Integer = CInt(PanBack.ActualWidth)
                                Dim SnapshotH As Integer = CInt(PanBack.ActualHeight)
                                If SnapshotW <= 0 OrElse SnapshotH <= 0 Then Return
                                Dim Bmp As New RenderTargetBitmap(SnapshotW, SnapshotH, 96, 96, PixelFormats.Pbgra32)
                                Bmp.Render(PanBack)
                                Dim Enc As New PngBitmapEncoder()
                                Enc.Frames.Add(BitmapFrame.Create(Bmp))
                                Using Fs = IO.File.Create(Paths.Base & "TCL\devshot.png")
                                    Enc.Save(Fs)
                                End Using
                                Logger.Info($"已输出界面快照：TCL\devshot.png（{SnapshotW}×{SnapshotH}）")
                                '顺带拍一张「启动中」弹窗
                                Try
                                    FrmLaunchRight?.RunDevLaunchingSnapshot()
                                Catch ex As Exception
                                    Logger.Warn(ex, "渲染启动弹窗快照失败")
                                End Try
                                '顺带拍一张启动按钮的展开菜单
                                Try
                                    If FileUtils.Exists(Paths.Base & "TCL\.devshot-menu") Then FrmLaunchRight?.RunDevMenuSnapshot()
                                Catch ex As Exception
                                    Logger.Warn(ex, "渲染启动菜单快照失败")
                                End Try
                                '存在 TCL\.devshot-login 时再切一次「账户管理」页：
                                '用来核对进入该页时左边栏罩子不再"闪一下"（见 HasRealLeftPanel 注释）
                                Try
                                    If FileUtils.Exists(Paths.Base & "TCL\.devshot-login") Then
                                        Logger.Info("开发快照：切换到账户管理页，观察左边栏罩子日志")
                                        PageChange(PageType.Login)
                                    End If
                                Catch ex As Exception
                                    Logger.Warn(ex, "开发快照切页失败")
                                End Try
                                '存在 TCL\.devshot-pages 时依次切到各主页面并各拍一张：
                                '用来核对英文界面下还有哪些文案没翻译
                                Try
                                    If FileUtils.Exists(Paths.Base & "TCL\.devshot-pages") Then
                                        Dim ShotPages() As PageType = {PageType.Launch, PageType.Download, PageType.Link, PageType.Setup, PageType.InstanceSelect}
                                        Dim ShotNames() As String = {"launch", "download", "link", "setup", "instance"}
                                        RunInNewThread(
                                            Sub()
                                                For Index As Integer = 0 To ShotPages.Length - 1
                                                    Dim I As Integer = Index
                                                    Try
                                                        RunInUi(Sub() PageChange(ShotPages(I)), True)
                                                    Catch ex As Exception
                                                        Logger.Warn(ex, $"开发快照：切到 {ShotNames(I)} 页失败")
                                                    End Try
                                                    Thread.Sleep(1200)
                                                    RunInUi(Sub() SaveDevShot($"devshot-page-{ShotNames(I)}.png"), True)
                                                Next
                                            End Sub, "DevShotPages", ThreadPriority.BelowNormal)
                                    End If
                                Catch ex As Exception
                                    Logger.Warn(ex, "开发快照多页截图失败")
                                End Try
                                '存在 TCL\.devshot-server 时切到第一个实例的「服务器」页并截图：
                                '用来复现/回归「点服务器卡死」
                                Try
                                    If FileUtils.Exists(Paths.Base & "TCL\.devshot-server") Then
                                        RunInNewThread(
                                            Sub()
                                                Try
                                                    Thread.Sleep(600)
                                                    '直接扫版本目录：此时 McInstanceList 可能还没被版本选择页加载过
                                                    Dim VersionsRoot As String = PathUtils.AddSlashSuffix(McFolderSelected) & "versions\"
                                                    Dim FirstDir As String = Nothing
                                                    If DirectoryUtils.Exists(VersionsRoot) Then
                                                        FirstDir = DirectoryUtils.EnumerateDirectories(VersionsRoot, False).OrderBy(Function(D) D).FirstOrDefault()
                                                    End If
                                                    If String.IsNullOrEmpty(FirstDir) Then
                                                        Logger.Warn("开发快照：版本目录里没有实例，跳过服务器页")
                                                        Return
                                                    End If
                                                    Dim TargetName As String = PathUtils.GetLastPart(FirstDir)
                                                    Dim FirstInstance As McInstance = New McInstance(TargetName).Load()
                                                    RunInUi(
                                                        Sub()
                                                            PageInstanceLeft.Instance = FirstInstance
                                                            PageChange(PageType.InstanceSetup, PageSubType.InstanceServer)
                                                        End Sub, True)
                                                    Thread.Sleep(3000)
                                                    RunInUi(Sub() SaveDevShot("devshot-server.png"), True)
                                                    Logger.Info($"开发快照：已切到实例「{TargetName}」的服务器页并截图")
                                                Catch ex As Exception
                                                    Logger.Warn(ex, "开发快照：切到服务器页失败")
                                                End Try
                                            End Sub, "DevShotServer", ThreadPriority.BelowNormal)
                                    End If
                                Catch ex As Exception
                                    Logger.Warn(ex, "开发快照服务器页分支失败")
                                End Try
                                '存在 TCL\.devshot-launchgame 时真的点一次「启动游戏」：
                                '沙箱里合成点击进不了 WPF，只能这样端到端验证启动链路
                                Try
                                    If FileUtils.Exists(Paths.Base & "TCL\.devshot-launchgame") Then
                                        Logger.Info("开发快照：触发一次启动游戏，验证启动链路")
                                        FrmLaunchRight?.LaunchButtonClick()
                                        '1.3s 后再拍一张全窗口：确认启动侧边栏是从窗口最左边展开、带阴影；
                                        '12s 再拍一张：确认启动成功后侧边栏会自动收起
                                        RunInNewThread(
                                            Sub()
                                                Try
                                                    Thread.Sleep(1300)
                                                    RunInUi(Sub() SaveDevShot("devshot-sidebar.png"), True)
                                                    Thread.Sleep(10700)
                                                    RunInUi(Sub() SaveDevShot("devshot-collapsed.png"), True)
                                                Catch ex As Exception
                                                End Try
                                            End Sub, "DevShotSidebar", ThreadPriority.BelowNormal)
                                    End If
                                Catch ex As Exception
                                    Logger.Warn(ex, "开发快照触发启动失败")
                                End Try
                            Catch ex As Exception
                                Logger.Warn(ex, "渲染界面快照失败")
                            End Try
                        End Sub, True)
                Catch ex As Exception
                End Try
            End Sub, "DevSnapshot", ThreadPriority.BelowNormal)
    End Sub
    ''' <summary>
    ''' 把整个窗口渲染成 TCL\&lt;FileName&gt;，仅供开发快照流程使用（必须在 UI 线程调用）。
    ''' </summary>
    Private Sub SaveDevShot(FileName As String)
        Try
            Dim ShotW As Integer = CInt(PanBack.ActualWidth)
            Dim ShotH As Integer = CInt(PanBack.ActualHeight)
            If ShotW <= 0 OrElse ShotH <= 0 Then Return
            Dim ShotBmp As New RenderTargetBitmap(ShotW, ShotH, 96, 96, PixelFormats.Pbgra32)
            ShotBmp.Render(PanBack)
            Dim ShotEnc As New PngBitmapEncoder()
            ShotEnc.Frames.Add(BitmapFrame.Create(ShotBmp))
            Using Fs = IO.File.Create(Paths.Base & "TCL\" & FileName)
                ShotEnc.Save(Fs)
            End Using
            Logger.Info($"已输出界面快照：TCL\{FileName}（{ShotW}×{ShotH}）")
        Catch ex As Exception
            Logger.Warn(ex, "渲染界面快照失败：" & FileName)
        End Try
    End Sub
    ''' <summary>
    ''' 窗口首次渲染后关闭启动画面，确保启动画面不会残留在主界面之上。
    ''' </summary>
    Private Sub FormMain_ContentRendered(sender As Object, e As EventArgs) Handles Me.ContentRendered
        Logger.Info($"[启动耗时] 首帧渲染完成（ContentRendered，自进程启动 {MsSinceProcessStart()} ms）")
        CloseStartSplash()
        '首帧已渲染，标记置位。按渲染模式决定背景高斯模糊：
        ' - 硬件加速（Tier>0）：首帧前已暂缓，这里补齐（观感与最终一致，只是晚了半拍）。
        ' - 软件渲染（Tier 0，远程桌面/无 GPU）：模糊走 CPU 逐像素卷积且每次重绘都要算，
        ' 代价远超视觉收益，彻底跳过，保证主界面流畅（有日志可溯；改回无条件 ApplyBackgroundBlur 即回退）。
        HasRenderedFirstFrame = True
        If IsSoftwareRendering Then
            ImgBack.Effect = Nothing
            ImgBack.Margin = New Thickness(0)
            Logger.Info("软件渲染（Tier 0）：已跳过背景高斯模糊以保证主界面流畅")
        Else
            ApplyBackgroundBlur()
        End If
        '首帧已经上屏、启动画面开始淡出：非首屏收尾工作全部丢到 Background 优先级，
        '让窗口立刻进入可交互状态（原来这些同步跑在 Loaded 里）。
        Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background, New Action(AddressOf RunDeferredStartupWork))
    End Sub
    ''' <summary>
    ''' 关闭启动画面。
    ''' </summary>
    Private Sub CloseStartSplash()
        If FrmStart Is Nothing Then Return
        Try
            FrmStart.Close(TimeSpan.FromSeconds(0.4)) '0.4s 淡出：让启动画面 logo 柔和消失（原 Zero 为防渲染阻塞残留才硬切，现用户明确要求淡出）
        Catch ex As Exception
            Logger.Warn(ex, "关闭启动画面失败")
        End Try
        FrmStart = Nothing
    End Sub
    ''' <summary>
    ''' 主窗口进入动画期间，把当前可见的标题栏 logo（形状 / 文本 / 图片）从透明柔和淡入，
    ''' 与整窗淡入节奏错开一拍，形成"logo 浮现"的观感；窗口淡出时随整窗一起消失。
    ''' </summary>
    Private Sub LogoFadeIn()
        Try
            Dim Logos As IEnumerable(Of FrameworkElement) = {ShapeTitleLogo, LabTitleLogo, ImageTitleLogo}
            For Each Logo In Logos
                If Logo.Visibility = Visibility.Visible AndAlso Logo.Opacity < 1 Then
                    Logo.Opacity = 0
                    AniStart(AaOpacity(Logo, 1, 420, , New AniEaseOutFluent(AniEasePower.Weak)), "Logo FadeIn " & GetType(Logo).Name)
                End If
            Next
        Catch ex As Exception
            Logger.Warn(ex, "logo 淡入动画失败")
        End Try
    End Sub
    ''' <summary>
    ''' 启动画面兜底关闭：若窗口未正常显示，则在 3 秒后强制关闭启动画面。
    ''' </summary>
    Private Sub StartSplashFallbackTimer()
        Dim Timer As New System.Windows.Threading.DispatcherTimer()
        Timer.Interval = TimeSpan.FromSeconds(3)
        AddHandler Timer.Tick,
            Sub()
                Timer.Stop()
                CloseStartSplash()
                '兜底：若 ContentRendered 因极端情况（窗口未正常呈现）没有触发，
                '在这里补跑一次首帧后的收尾工作，避免翻译文件与界面语言一直不生效。方法是幂等的。
                RunDeferredStartupWork()
            End Sub
        Timer.Start()
    End Sub
    '根据打开次数触发的事件
    Private Sub RunCountSub()
        Settings.Set("SystemCount", Settings.Get(Of Integer)("SystemCount") + 1)
        '首次启动弹出 TCL 说明（后续启动不再显示）
        If Not Settings.Get(Of Boolean)("HintTCLInfo") Then
            Settings.Set("HintTCLInfo", True)
            MyMsgBox(ModLang.L("TCL 由 TCraft 团队开发与维护。") & vbCrLf & vbCrLf &
                     ModLang.L("提供游戏与模组、整合包、资源包、光影的下载与管理，支持原版、Forge / NeoForge / Fabric / OptiFine / LiteLoader。") & vbCrLf & vbCrLf &
                     ModLang.L("新版本在群内发布，手动下载新版 exe 覆盖即可。加入 TCraft Launcher 启动器交流群（群号 1131373915）获取更新、反馈问题。"), ModLang.L("关于 TCL"), ModLang.L("我知道了"), IsWarn:=False)
        End If
        If BuildType = BuildTypes.Snapshot Then
            Select Case Settings.Get(Of Integer)("SystemCount")
                Case 1
                    MyMsgBox(ModLang.L("欢迎使用 TCL 快照版！") & vbCrLf &
                             ModLang.L("快照版包含尚未在正式版发布的测试功能，请谨慎使用，不要用于制作或分发整合包。"), ModLang.L("快照版使用说明"))
            End Select
            If Settings.Get(Of Integer)("SystemCount") >= 99 Then
                If ThemeUnlock(6, False) Then
                    MyMsgBox(ModLang.L("你已经使用了 99 次 TCL 啦，感谢你长期以来的支持！") & vbCrLf &
                             ModLang.L("隐藏主题 桃霞粉 已解锁！"), ModLang.L("提示"))
                End If
            End If
        End If
    End Sub
    '升级与降级事件
    Private Sub UpgradeSub(LastVersionCode As Integer)
        Logger.Info($"版本号从 {LastVersionCode} 升高到 {VersionCode}")
        Settings.Set("SystemLastVersionReg", VersionCode)
        '检查有记录的最高版本号
        Dim HighestVersionCode As Integer
        Const SettingKey = If(BuildType = BuildTypes.Snapshot, "SystemHighestAlphaVersionReg", "SystemHighestBetaVersionReg")
        HighestVersionCode = Settings.Get(Of Integer)(SettingKey)
        If HighestVersionCode < VersionCode Then
            Settings.Set(SettingKey, VersionCode)
            Logger.Info($"最高版本号从 {HighestVersionCode} 升高到 {VersionCode}")
        End If
        '被移除的窗口设置选项
        If Settings.Get(Of Integer)("LaunchArgumentWindowType") = 5 Then Settings.Set("LaunchArgumentWindowType", 1)
        '修改主题设置项名称
        If HighestVersionCode <= 207 Then
            Dim UnlockedTheme As New List(Of String) From {"2"}
            UnlockedTheme.AddRange(New List(Of String)(Settings.Get(Of String)("UiLauncherThemeHide").ToString.Split("|")))
            UnlockedTheme.AddRange(New List(Of String)(Settings.Get(Of String)("UiLauncherThemeHide2").ToString.Split("|")))
            Settings.Set("UiLauncherThemeHide2", UnlockedTheme.Distinct.ToList.Join("|"c))
        End If
        '重置曜光彩
        If LastVersionCode <= 115 AndAlso Settings.Get(Of String)("UiLauncherThemeHide2").ToString.Split("|").Contains("13") Then
            Dim UnlockedTheme As New List(Of String)(Settings.Get(Of String)("UiLauncherThemeHide2").ToString.Split("|"))
            UnlockedTheme.Remove("13")
            Settings.Set("UiLauncherThemeHide2", UnlockedTheme.Join("|"c))
            MyMsgBox(ModLang.L("由于新版 TCL 修改了曜光彩的解锁方式，你需要重新解锁曜光彩。") & vbCrLf &
                     ModLang.L("多谢各位的理解啦！"), ModLang.L("重新解锁提醒"))
        End If
        '重置七曜彩
        If LastVersionCode <= 152 AndAlso Settings.Get(Of String)("UiLauncherThemeHide2").ToString.Split("|").Contains("12") Then
            Dim UnlockedTheme As New List(Of String)(Settings.Get(Of String)("UiLauncherThemeHide2").ToString.Split("|"))
            UnlockedTheme.Remove("12")
            Settings.Set("UiLauncherThemeHide2", UnlockedTheme.Join("|"c))
            MyMsgBox(ModLang.L("由于新版 TCL 修改了七曜彩的解锁方式，你需要重新解锁七曜彩。") & vbCrLf &
                     ModLang.L("多谢各位的理解啦！"), ModLang.L("重新解锁提醒"))
        End If
        '移动自定义皮肤
        If LastVersionCode <= 161 AndAlso FileUtils.Exists(Paths.Base & "TCL\CustomSkin.png") AndAlso Not FileUtils.Exists(Paths.AppDataThenName & "CustomSkin.png") Then
            FileUtils.Copy(Paths.Base & "TCL\CustomSkin.png", Paths.AppDataThenName & "CustomSkin.png")
            Logger.Info("已移动离线自定义皮肤 (162)")
        End If
        If LastVersionCode <= 263 AndAlso FileUtils.Exists(PathTemp & "CustomSkin.png") AndAlso Not FileUtils.Exists(Paths.AppDataThenName & "CustomSkin.png") Then
            FileUtils.Copy(PathTemp & "CustomSkin.png", Paths.AppDataThenName & "CustomSkin.png")
            Logger.Info("已移动离线自定义皮肤 (264)")
        End If
        '解除帮助页面的隐藏
        If LastVersionCode <= 205 Then
            Settings.Set("UiHiddenOtherHelp", False)
            Logger.Info("已解除帮助页面的隐藏")
        End If
        '单向迁移微软登录结果
        If Not Settings.Get(Of Boolean)("CacheMsV2Migrated") Then
            Settings.Set("CacheMsV2Migrated", True)
            Settings.Set("CacheMsV2OAuthRefresh", Settings.Get(Of String)("CacheMsOAuthRefresh"))
            Settings.Set("CacheMsV2Access", Settings.Get(Of String)("CacheMsAccess"))
            Settings.Set("CacheMsV2ProfileJson", Settings.Get(Of String)("CacheMsProfileJson"))
            Settings.Set("CacheMsV2Uuid", Settings.Get(Of String)("CacheMsUuid"))
            Settings.Set("CacheMsV2Name", Settings.Get(Of String)("CacheMsName"))
            Logger.Info("已从老版本迁移微软登录结果")
        End If
        'Mod 命名设置迁移
        If Settings.HasSaved("ToolDownloadTranslate") AndAlso Not Settings.HasSaved("ToolDownloadTranslateV2") Then
            Settings.Set("ToolDownloadTranslateV2", Settings.Get(Of Integer)("ToolDownloadTranslate") + 1)
            Logger.Info("已从老版本迁移 Mod 命名设置")
        End If
        '重置 JVM 参数设置
        If LastVersionCode <= 381 AndAlso Settings.HasSaved("LaunchAdvanceJvm") AndAlso
           Settings.Get(Of String)("LaunchAdvanceJvm").ToString.Replace("-XX:+UseG1GC ", "").Replace("-XX:-UseAdaptiveSizePolicy ", "").Trim = Settings.GetDefault("LaunchAdvanceJvm") Then
            Settings.Reset("LaunchAdvanceJvm")
            Logger.Info("已重置 JVM 参数设置")
        End If
    End Sub
    Private Sub DowngradeSub(LastVersionCode As Integer)
        Logger.Info($"版本号从 {LastVersionCode} 降低到 {VersionCode}")
        Settings.Set("SystemLastVersionReg", VersionCode)
    End Sub

#End Region

#Region "自定义窗口"

    '关闭
    Private Sub FormMain_Closing(sender As Object, e As CancelEventArgs) Handles Me.Closing
        EndProgram(True)
        e.Cancel = True
    End Sub
    ''' <summary>
    ''' 正常关闭程序。程序将在执行此方法后约 0.3s 退出。
    ''' </summary>
    ''' <param name="SendWarning">是否在还有下载任务未完成时发出警告。</param>
    Public Sub EndProgram(SendWarning As Boolean)
        '强行结束下载任务？
        If HasDownloadingTask() Then
            If SendWarning AndAlso MyMsgBox(ModLang.L("还有下载任务尚未完成，是否确定退出？"), ModLang.L("提示"), ModLang.L("确定"), ModLang.L("取消")) = 2 Then Return
            RunInNewThread(
            Sub()
                Logger.Info("正在强行停止任务")
                For Each Task As LoaderBase In LoaderTaskbar.ToList()
                    Task.Cancel()
                Next
            End Sub, "强行停止下载任务")
        End If
        '关闭联机？
        If FrmLinkMain?.TryExit(Not SendWarning, True) Then Return
        '关闭
        RunInUiWait(
        Sub()
            '正常退出标记放在最前面：后面无论走动画路径还是同步 EndProgramForce，标记都已经落盘。
            '（原来写在回调末尾，同步退出分支里可能来不及执行，导致下次启动误报「异常退出」）
            Try
                Settings.Set("SystemLastExitClean", True)
            Catch ex As Exception
                Logger.Warn(ex, "写入正常退出标记失败")
            End Try
            '关闭动画会把 Top 改成 -10000，所以位置必须在动画之前存
            If Settings.Get(Of Boolean)("UiRememberWindowPos") Then
                Try
                    Settings.Set("UiWindowPosX", CInt(Left))
                    Settings.Set("UiWindowPosY", CInt(Top))
                Catch ex As Exception
                    Logger.Warn(ex, "保存窗口位置失败")
                End Try
            End If
            IsHitTestVisible = False
            If PanBack.RenderTransform Is Nothing Then
                Dim TransformPos As New TranslateTransform(0, 0)
                Dim TransformRotate As New RotateTransform(0)
                Dim TransformScale As New ScaleTransform(1, 1)
                PanBack.RenderTransform = New TransformGroup() With {.Children = New TransformCollection({TransformRotate, TransformPos, TransformScale})}
                AniStart({
                    AaOpacity(Me, -Opacity, 140, 40, New AniEaseOutFluent(AniEasePower.Weak)),
                    AaDouble(
                    Sub(i)
                        TransformScale.ScaleX += i
                        TransformScale.ScaleY += i
                    End Sub, 0.88 - TransformScale.ScaleX, 180),
                    AaDouble(Sub(i) TransformPos.Y += i, 20 - TransformPos.Y, 180, 0, New AniEaseOutFluent(AniEasePower.Weak)),
                    AaDouble(Sub(i) TransformRotate.Angle += i, 0.6 - TransformRotate.Angle, 180, 0, New AniEaseInoutFluent(AniEasePower.Weak)),
                    AaCode(
                    Sub()
                        IsHitTestVisible = False
                        Top = -10000
                        ShowInTaskbar = False
                    End Sub, 210),
                    AaCode(AddressOf EndProgramForce, 230)
                }, "Form Close")
            Else
                EndProgramForce()
            End If
            Logger.Info("收到关闭指令")
        End Sub)
    End Sub
    Private Shared IsLogShown As Boolean = False
    Public Shared Sub EndProgramForce(Optional ReturnCode As ProcessReturnValues = ProcessReturnValues.Success)
        '多核 JIT（ProfileOptimization）档案的落盘时机是「CLR 正常关机」：StartProfile 后无需也无法
        '显式 StopProfile（该 API 为 .NET Core 3.0+ 才有），Environment.Exit 触发的优雅关机会自动写回
        '%TEMP%\TCL-JIT\Startup.profile；下一次启动时 CLR 读取该档案，并行预编译这些程序集。
        '正常退出原先用 Process.Kill 强杀——CLR 没有关机流程，档案永远不落盘，
        '多核 JIT 实际全程失效（实测 %TEMP%\TCL-JIT 长期为空）。改用 Environment.Exit 后档案
        '在每次正常退出时更新；另加 3 秒看门狗兜底，若优雅关机被终结器卡死仍会强制退出。
        IsProgramEnding = True
        AniControlEnabled += 1
        If ReturnCode = ProcessReturnValues.Exception Then
            If Not IsLogShown Then
                Try
                    FeedbackInfo()
                Catch ex As Exception
                    Logger.Warn(ex, "EndProgramForce: FeedbackInfo 失败")
                End Try
                Logger.Info("如需提交错误报告，请打开日志目录查看 Log 文件。")
                IsLogShown = True
                Try
                    StartProcess(Paths.Base & "TCL\Log1.txt")
                Catch ex As Exception
                    Logger.Warn(ex, "EndProgramForce: 打开日志文件失败")
                End Try
            End If
            Thread.Sleep(500) '防止 TCL 在记事本打开前就被掐掉
        End If
        Logger.Info($"程序已退出，返回值：{ReturnCode}")
        '兜底：不是异常退出码就补写「正常退出」标记。代码里还有直接调 EndProgramForce 的退出路径
        '（不走 FormClosing），漏写会让下次启动误报「异常退出」
        If ReturnCode <> ProcessReturnValues.Exception Then
            Try
                Settings.Set("SystemLastExitClean", True)
            Catch ex As Exception
                Logger.Warn(ex, "EndProgramForce: 写入正常退出标记失败")
            End Try
        End If
        Try
            ConfigUtils.SaveAll()
        Catch ex As Exception
            Logger.Warn(ex, "EndProgramForce: 配置保存失败")
        End Try
        Try
            Logger.Instance.Flush()
        Catch ex As Exception
            Logger.Warn(ex, "EndProgramForce: 日志刷新失败")
        End Try
        Dim ExitWatchdog As New Thread(
            Sub()
                '10 秒：正常 CLR 关机（含 JIT 档案落盘）远快于此；仅当终结器死锁时兜底强杀
                Thread.Sleep(10000)
                Try
                    Process.GetCurrentProcess.Kill()
                Catch
                End Try
            End Sub) With {.IsBackground = True, .Name = "ExitWatchdog"}
        ExitWatchdog.Start()
        Environment.Exit(ReturnCode)
    End Sub
    Private Sub BtnTitleClose_Click(sender As Object, e As RoutedEventArgs) Handles BtnTitleClose.Click
        EndProgram(True)
    End Sub

    '移动
    Private Sub FormDragMove(sender As Object, e As MouseButtonEventArgs) Handles PanTitle.MouseLeftButtonDown, PanMsg.MouseLeftButtonDown
        Try
            If sender.IsMouseDirectlyOver Then DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "FormDragMove 失败")
        End Try
    End Sub

    '改变大小
    ''' <summary>
    ''' 是否可以向注册表储存尺寸改变信息。以此避免初始化时误储存。
    ''' </summary>
    Public IsSizeSaveable As Boolean = False
    Private Sub FormMain_SizeChanged() Handles Me.SizeChanged, Me.Loaded
        If IsSizeSaveable Then
            Settings.Set("WindowHeight", Height)
            Settings.Set("WindowWidth", Width)
        End If
        RectForm.Rect = New Rect(0, 0, BorderForm.ActualWidth, BorderForm.ActualHeight)
        PanForm.Width = BorderForm.ActualWidth + 0.001
        PanForm.Height = BorderForm.ActualHeight + 0.001
        PanMain.Width = PanForm.Width
        '用显式 Height 而不是 ActualHeight：标题栏高度可由设置修改，首帧布局前 ActualHeight 还是 0，
        '那会让主体区按「没有标题栏」算一次高度。
        PanMain.Height = Math.Max(0, PanForm.Height - If(PanTitle.Height > 0, PanTitle.Height, PanTitle.ActualHeight))
        If WindowState = WindowState.Maximized Then WindowState = WindowState.Normal '修复最大化状态下重算布局的问题
        '窗口尺寸变化时重算标题栏标签字号/间距（自适应，任何语言、任意宽度都不压右侧图标）
        AdaptTitleTabsToWidth()
    End Sub

#Region "外观设置应用"

    ''' <summary>窗口圆角半径（px）。0 = 直角。同时改裁剪几何与边框，否则会出现「圆角边 + 方角内容」。</summary>
    Public Sub ApplyWindowRadius(Value As Object)
        If FrmMain Is Nothing Then Return
        Try
            Dim R As Double = Math.Max(0, Math.Min(24, CDbl(Value)))
            BorderForm.CornerRadius = New CornerRadius(R)
            RectForm.RadiusX = R
            RectForm.RadiusY = R
        Catch ex As Exception
            Logger.Warn(ex, "应用窗口圆角失败")
        End Try
    End Sub

    ''' <summary>标题栏高度（px）。改完必须重算主体高度，否则左侧栏与内容区会各按旧值布局。</summary>
    Public Sub ApplyTitleBarHeight(Value As Object)
        If FrmMain Is Nothing Then Return
        Try
            Dim H As Double = Math.Max(36, Math.Min(72, CDbl(Value)))
            PanTitle.Height = H
            Dim Base As Double = Math.Max(PanForm.Height, BorderForm.ActualHeight + 0.001)
            PanMain.Height = Math.Max(0, Base - H)
        Catch ex As Exception
            Logger.Warn(ex, "应用标题栏高度失败")
        End Try
    End Sub

    ''' <summary>标题栏账户徽章（头像 + 名字）是否显示。关掉后标题栏只剩页面切换按钮，适合截图或隐私场景。</summary>
    Public Sub ApplyTitleAccountBadge(Value As Object)
        If FrmMain Is Nothing Then Return
        Try
            BtnTitleAccount.Visibility = If(CBool(Value), Visibility.Visible, Visibility.Collapsed)
        Catch ex As Exception
            Logger.Warn(ex, "应用标题栏账户徽章设置失败")
        End Try
    End Sub

    ''' <summary>
    ''' 个性化「列表按钮常驻显示」切换后调用：把当前界面里已实例化的列表行按钮立刻摆正，
    ''' 不必等重建页面或鼠标划过（虚拟化未实例化的行在 Loaded 时自行读取设置）。
    ''' </summary>
    Public Sub RefreshListButtons()
        Try
            RefreshListButtons(Me)
        Catch ex As Exception
            Logger.Warn(ex, "刷新列表按钮显隐失败")
        End Try
    End Sub
    Private Sub RefreshListButtons(Obj As DependencyObject)
        Dim Count = VisualTreeHelper.GetChildrenCount(Obj)
        For i = 0 To Count - 1
            Dim Child = VisualTreeHelper.GetChild(Obj, i)
            If TypeOf Child Is MyListItem Then
                DirectCast(Child, MyListItem).RefreshButtonVisibility()
            ElseIf TypeOf Child Is MyLocalModItem Then
                DirectCast(Child, MyLocalModItem).RefreshButtonVisibility()
            End If
            RefreshListButtons(Child)
        Next
    End Sub

    ''' <summary>
    ''' 顶部栏语言自适应：英文导航标签（Launch / Instance / Download / Multiplayer / More）比简中 2 字标签
    ''' 宽很多，默认间距下会顶到右侧窗口按钮（关闭/最小化/语言），把标题栏「挤爆」。
    ''' 当界面语言为英语（索引 1）时紧凑化标题栏——缩小导航标签字号与按钮间距、收紧 Logo↔导航间隙、
    ''' 收窄账号徽章名字宽度；其它语言（简中 / 繁中 / 文言，标签≤2 字）恢复默认。
    ''' 幂等，可重复调用；在 RunDeferredStartupWork 应用完界面语言后调用（语言切换会重启，此处语言已固定）。
    ''' </summary>
    Public Sub ApplyTitleLangLayout()
        If FrmMain Is Nothing Then Return
        Try
            Dim IsEn As Boolean = ModBase.ResolveLanguage() = 1
            '账号徽章：XAML 默认 MaxWidth=110 / 字号 12.5；英语收窄为 70 / 11，避免长名字再占一行宽度
            If LabTitleAccount IsNot Nothing Then
                LabTitleAccount.MaxWidth = If(IsEn, 70, 110)
                LabTitleAccount.FontSize = If(IsEn, 11, 12.5)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "应用标题栏语言布局失败")
        End Try
        '标签组按可用宽度自适应（与语言、窗口尺寸均无关），确保任何语言、任意宽度都严格居中且不压右侧图标
        AdaptTitleTabsToWidth()
    End Sub

    ''' <summary>
    ''' 标题栏 5 个页面切换按钮自适应：先按默认字号 13 量出标签组自然宽度，
    ''' 若超出「账号徽章右缘 → 右侧图标区（语言/最小化/关闭）」的可用带，就按比例缩小
    ''' 标签字号与药丸间距，使按钮组严格居中且永不压到右侧图标。随窗口 resize 与
    ''' 语言切换重算，幂等。
    ''' </summary>
    Private Sub AdaptTitleTabsToWidth()
        If FrmMain Is Nothing OrElse PanTitleSelect Is Nothing Then Return
        Try
            Dim Rbs As MyRadioButton() = New MyRadioButton() {BtnTitleSelect0, BtnTitleSelect5, BtnTitleSelect1, BtnTitleSelect2, BtnTitleSelect4}
            '① 复位到默认字号 13，量出标签组自然宽度
            For Each Rb In Rbs
                Dim tb = Rb.LabelText
                If tb IsNot Nothing Then tb.FontSize = 13
                Rb.Margin = New Thickness(5, 0, 5, 0)
            Next
            PanTitleSelect.UpdateLayout()
            Dim Natural As Double = PanTitleSelect.DesiredSize.Width
            '② 可用带 = 标题栏可布局宽 − 左侧已占用（Logo 区 + 账号徽章） − 两侧最小空隙
            Dim LeftUsed As Double = 78 ' Logo 区（图标/文本/图片 logo + 左边距）保守估计
            If BtnTitleAccount IsNot Nothing AndAlso BtnTitleAccount.Visibility = Visibility.Visible Then
                LeftUsed += BtnTitleAccount.ActualWidth
            End If
            Dim GridW As Double = If(PanTitleMain.ActualWidth > 0, PanTitleMain.ActualWidth, BorderForm.ActualWidth - 152) '与 PanTitleMain 右 margin 一致
            Dim Gap As Double = 24 ' 按钮组与「左侧徽章 / 右侧图标」各留的最小空隙
            Dim Available As Double = GridW - LeftUsed - 2 * Gap
            '③ 超出则按可用比缩小字号（下限 9）；未超出保持 13
            Dim Fs As Double = If(Natural > 0 AndAlso Natural > Available, Math.Max(9, 13 * Available / Natural), 13)
            Dim Tight As Boolean = (Fs < 13 - 0.4)
            For Each Rb In Rbs
                Dim tb = Rb.LabelText
                If tb IsNot Nothing Then
                    tb.FontSize = Fs
                    ' 收紧时药丸内文字左右留白随字号同比下调，进一步省宽
                    tb.Margin = If(Tight, New Thickness(Math.Max(3, 8 * Fs / 13), 0, Math.Max(5, 12 * Fs / 13), 0), New Thickness(8, 0, 12, 0))
                End If
                Rb.Margin = If(Tight, New Thickness(2, 0, 2, 0), New Thickness(5, 0, 5, 0))
            Next
        Catch ex As Exception
            Logger.Warn(ex, "标题栏标签自适应失败")
        End Try
    End Sub

    ''' <summary>背景图片自动轮播计时器（分钟）。0 或负数表示关闭。</summary>
    Private BackgroundRotateTimer As System.Windows.Threading.DispatcherTimer
    ''' <summary>背景图片自动轮播：每隔 N 分钟从图片文件夹里随机换一张。</summary>
    Public Sub ApplyBackgroundRotate(Value As Object)
        If FrmMain Is Nothing Then Return
        Try
            Dim Minutes As Integer = CInt(Value)
            If BackgroundRotateTimer IsNot Nothing Then
                BackgroundRotateTimer.Stop()
                RemoveHandler BackgroundRotateTimer.Tick, AddressOf BackgroundRotateTick
                BackgroundRotateTimer = Nothing
            End If
            If Minutes <= 0 Then
                Logger.Info("背景自动轮播：已关闭")
                Return
            End If
            Dim Timer As New System.Windows.Threading.DispatcherTimer With {.Interval = TimeSpan.FromMinutes(Minutes)}
            AddHandler Timer.Tick, AddressOf BackgroundRotateTick
            Timer.Start()
            BackgroundRotateTimer = Timer
            Logger.Info($"背景自动轮播：每 {Minutes} 分钟切换一次")
        Catch ex As Exception
            Logger.Warn(ex, "设置背景自动轮播失败")
        End Try
    End Sub
    Private Sub BackgroundRotateTick(sender As Object, e As EventArgs)
        Try
            '视频背景不走图片轮播，避免把画面抢回静态图
            If Settings.Get(Of Integer)("UiBackgroundType") = 1 Then Return
            PageSetupUI.BackgroundRefresh(False, True)
        Catch ex As Exception
            Logger.Warn(ex, "背景自动轮播切换失败")
        End Try
    End Sub

#End Region

    '最小化
    Private Sub BtnTitleMin_Click() Handles BtnTitleMin.Click
        WindowState = WindowState.Minimized
    End Sub

    ''' <summary>
    ''' 标题栏账号徽章：显示玩家头像与名字，点击进入账户管理页。
    ''' </summary>
    Private Sub BtnTitleAccount_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnTitleAccount.MouseLeftButtonUp
        If McLaunchLoader.State = LoadState.Loading Then Return
        FrmMain.PageChange(FormMain.PageType.Login)
    End Sub
    ' 设置标题栏账号徽章的显示内容。
    Public TitleAccountName As String = ""
    Private _TitleAccountSig As String = ""
    Public Sub SetTitleAccount(Name As String, TypeName As String, FacePath As String)
        '启动期本方法会被多个刷新链重复调用（首页构造/登录状态/语言套用），内容没变就跳过，避免日志刷屏
        Dim Sig As String = If(Name, "") & "|" & If(TypeName, "") & "|" & If(FacePath, "")
        RunInUi(Sub()
                    Try
                        If Sig = _TitleAccountSig Then Return
                        _TitleAccountSig = Sig
                        '未选择账户时也要有内容，否则标题栏会留下一个空白的药丸（看起来像坏按钮）
                        TitleAccountName = If(Name, "")
                        LabTitleAccount.Text = If(String.IsNullOrEmpty(Name), ModLang.L("未登录"), Name)
                        If FacePath IsNot Nothing AndAlso FacePath <> "" Then
                            ImgTitleAccount.Source = FacePath
                        Else
                            '没有头像缓存时必须退回一张「脸」，不能拿整张皮肤图顶着：
                            '18px 的方框会把 64×64 皮肤缩成一团花屏
                            ImgTitleAccount.Source = PageLaunchLeft.GetAccountFaceOrDefault(Name)
                        End If
                        BtnTitleAccount.ToolTip = If(String.IsNullOrEmpty(Name),
                            "当前没有可用账户，点击进入账户管理添加账户",
                            $"{Name}（{TypeName}）· 点击进入账户管理")
                        Logger.Info($"标题栏账号徽章：{LabTitleAccount.Text}（{TypeName}）")
                    Catch ex As Exception
                        Logger.Warn(ex, "更新标题栏账号徽章失败")
                    End Try
                End Sub, True)
    End Sub
    ' 皮肤加载完成后仅刷新头像图。
    Public Sub RefreshTitleAccountFace()
        Try
            If ImgTitleAccount Is Nothing OrElse LabTitleAccount Is Nothing Then Return
            '用字段而不是 LabText 取账户名：未登录时徽章显示的是「未登录」文案，不是账户键
            If String.IsNullOrEmpty(TitleAccountName) Then Return
            Dim Face = PageLaunchLeft.GetAccountFaceOrDefault(TitleAccountName)
            If Face IsNot Nothing Then
                'MyImage.Source 对「同一个路径」会直接 return（内部有等于判断），
                '头像文件重写后必须先把 Source 清空，否则标题栏会一直显示旧图
                ImgTitleAccount.Source = Nothing
                ImgTitleAccount.Source = Face
            End If
        Catch ex As Exception
            Logger.Warn(ex, "刷新标题栏头像失败")
        End Try
    End Sub

    ''' <summary>
    ''' 标题栏常驻「语言」按钮：点开一个下拉菜单，随时切换 5 种界面语言，无需进入 设置→系统。
    ''' </summary>
    Private Sub BtnTitleLang_Click(sender As Object, e As EventArgs) Handles BtnTitleLang.Click
        Try
            ' 语言项名称按当前界面语言显示（简体中文/English/繁體中文/文言文 本身也做三语）
            Dim Names As String() = {"简体中文", "English", "繁體中文", "文言文", "自动识别系统语言"}
            For i As Integer = 0 To 4
                Names(i) = ModLang.L(Names(i))
            Next
            Dim Cur As Integer = Settings.Get(Of Integer)("ToolLanguage")
            Dim Ctx As New ContextMenu
            For i As Integer = 0 To 4
                Dim Item As New MyMenuItem With {
                    .Header = If(i = Cur, "✓ ", "") & Names(i),
                    .ToolTip = Names(i)
                }
                Dim LangIndex As Integer = i
                AddHandler Item.Click, Sub(s, ev)
                                         Ctx.IsOpen = False
                                         Settings.Set("ToolLanguage", LangIndex)
                                     End Sub
                Ctx.Items.Add(Item)
            Next
            Ctx.PlacementTarget = BtnTitleLang
            Ctx.HorizontalOffset = -30
            Ctx.VerticalOffset = 8
            Ctx.IsOpen = True
        Catch ex As Exception
            Logger.Warn(ex, "打开语言菜单失败")
        End Try
    End Sub

    '背景图片与标题栏
    Public Shared Sub UpdateBackgroundAndTitleBar(Value) '从设置更新
        If FrmMain Is Nothing OrElse Not FrmMain.IsLoaded Then Return
        FrmMain.UpdateBackgroundAndTitleBar()
    End Sub
    ''' <summary>
    ''' 把背景图的高斯模糊单独抽出来，供首帧渲染后补齐调用。
    ''' 首帧前（HasRenderedFirstFrame=False）暂缓，让主窗口先快速上屏、启动画面早点消失。
    ''' 若用户没开背景模糊（UiBackgroundBlur=0），BlurRadius=1，Effect 仍为 Nothing，无副作用。
    ''' </summary>
    Public Sub ApplyBackgroundBlur()
        Try
            Dim BlurRadius As Double = Settings.Get(Of Integer)("UiBackgroundBlur") + 1
            If BlurRadius = 1 Then
                ImgBack.Effect = Nothing
            Else
                ImgBack.Effect = New Effects.BlurEffect With {.Radius = BlurRadius}
            End If
            ImgBack.Margin = New Thickness(-BlurRadius / 1.8)
        Catch ex As Exception
            Logger.Warn(ex, "应用背景模糊失败")
        End Try
    End Sub
    Public Sub UpdateBackgroundAndTitleBar()
        Logger.Info("从设置更新背景图片与标题栏样式")
        '背景图片透明度
        ImgBack.Opacity = Settings.Get(Of Integer)("UiBackgroundOpacity") / 1000
        '背景视频层：跟随同一不透明度，让半透明组件可透出动态背景
        If _VidBack IsNot Nothing Then _VidBack.Opacity = Settings.Get(Of Integer)("UiBackgroundOpacity") / 1000
        '背景图片模糊（首帧前暂缓，避免整幅高斯模糊卷积拖慢首帧渲染、延长启动画面停留；
        '首帧渲染完成（FormMain_ContentRendered）后由 ApplyBackgroundBlur 补齐，最终观感一致）
        If HasRenderedFirstFrame Then
            ApplyBackgroundBlur()
        Else
            ImgBack.Effect = Nothing
            ImgBack.Margin = New Thickness(0)
        End If
        '背景模式下左侧栏随背景透出（HMCL 50% surface 思路）：无背景时保持近实色，避免纯色状态下发灰
        Try
            Dim SidebarAlpha As Byte = If(ImgBack.Visibility = Visibility.Visible OrElse (_VidBack IsNot Nothing AndAlso _VidBack.Visibility = Visibility.Visible), CType(150, Byte), CType(241, Byte))
            Dim OldBrush = TryCast(Application.Current.Resources("ColorBrushBackgroundTransparentSidebar"), SolidColorBrush)
            If OldBrush Is Nothing OrElse OldBrush.Color.A <> SidebarAlpha Then
                Application.Current.Resources("ColorBrushBackgroundTransparentSidebar") =
                    New SolidColorBrush(Color.FromArgb(SidebarAlpha, 255, 255, 255))
            End If
        Catch ex As Exception
            Logger.Warn(ex, "更新侧栏透明度失败")
        End Try
        '背景图片适应方式
        Dim BackgroundType As Integer = Settings.Get(Of Integer)("UiBackgroundSuit")
        If ImgBack.Background IsNot Nothing Then
            Dim Brush As ImageBrush = CType(ImgBack.Background, ImageBrush)
            If BackgroundType = 0 Then
                '智能：当图片较小时平铺，较大时适应
                If Brush.ImageSource.Width < PanMain.ActualWidth / 2 AndAlso Brush.ImageSource.Height < PanMain.ActualHeight / 2 Then
                    BackgroundType = 4 '平铺
                Else
                    BackgroundType = 2 '适应
                End If
            End If
            Brush.Stretch = Stretch.UniformToFill
            Brush.TileMode = TileMode.None
            Brush.Viewport = New Rect(0, 0, 1, 1)
            Brush.ViewportUnits = BrushMappingMode.RelativeToBoundingBox
            Brush.AlignmentX = AlignmentX.Center
            Brush.AlignmentY = AlignmentY.Center
            Select Case BackgroundType
                Case 1 '中
                    Brush.Stretch = Stretch.None
                Case 3 '拉伸
                    Brush.Stretch = Stretch.Fill
                Case 4 '平铺
                    Brush.Stretch = Stretch.None
                    Brush.TileMode = TileMode.Tile
                    Brush.Viewport = New Rect(0, 0, Brush.ImageSource.Width, Brush.ImageSource.Height)
                    Brush.ViewportUnits = BrushMappingMode.Absolute

                Case 2 '适应
                Case 5 '左上
                    Brush.AlignmentX = AlignmentX.Left
                    Brush.AlignmentY = AlignmentY.Top
                Case 6 '右上
                    Brush.AlignmentX = AlignmentX.Right
                    Brush.AlignmentY = AlignmentY.Top
                Case 7 '左下
                    Brush.AlignmentX = AlignmentX.Left
                    Brush.AlignmentY = AlignmentY.Bottom
                Case 8 '右下
                    Brush.AlignmentX = AlignmentX.Right
                    Brush.AlignmentY = AlignmentY.Bottom
                Case 9 '左
                    Brush.AlignmentX = AlignmentX.Left
                    Brush.AlignmentY = AlignmentY.Center
                Case 10 '右
                    Brush.AlignmentX = AlignmentX.Right
                    Brush.AlignmentY = AlignmentY.Center
                Case 11 '上
                    Brush.AlignmentX = AlignmentX.Center
                    Brush.AlignmentY = AlignmentY.Top
                Case 12 '下
                    Brush.AlignmentX = AlignmentX.Center
                    Brush.AlignmentY = AlignmentY.Bottom
            End Select
        End If
        '标题栏显示类型
        Select Case Settings.Get(Of Integer)("UiLogoType")
            Case 0 '无
                ShapeTitleLogo.Visibility = Visibility.Collapsed
                LabTitleLogo.Visibility = Visibility.Collapsed
                ImageTitleLogo.Visibility = Visibility.Collapsed
                If Not IsNothing(FrmSetupUI) Then 'TODO: 和 FrmSetupUI 解耦
                    FrmSetupUI.CheckLogoLeft.Visibility = Visibility.Visible
                    FrmSetupUI.PanLogoText.Visibility = Visibility.Collapsed
                    FrmSetupUI.PanLogoChange.Visibility = Visibility.Collapsed
                End If
            Case 1 '默认
                ShapeTitleLogo.Visibility = Visibility.Collapsed
                LabTitleLogo.Visibility = Visibility.Visible
                ImageTitleLogo.Visibility = Visibility.Collapsed
                If Not IsNothing(FrmSetupUI) Then
                    FrmSetupUI.CheckLogoLeft.Visibility = Visibility.Collapsed
                    FrmSetupUI.PanLogoText.Visibility = Visibility.Collapsed
                    FrmSetupUI.PanLogoChange.Visibility = Visibility.Collapsed
                End If
            Case 2 '文本
                ShapeTitleLogo.Visibility = Visibility.Collapsed
                LabTitleLogo.Visibility = Visibility.Visible
                ImageTitleLogo.Visibility = Visibility.Collapsed
                If Not IsNothing(FrmSetupUI) Then
                    FrmSetupUI.CheckLogoLeft.Visibility = Visibility.Collapsed
                    FrmSetupUI.PanLogoText.Visibility = Visibility.Visible
                    FrmSetupUI.PanLogoChange.Visibility = Visibility.Collapsed
                End If
            Case 3 '图片
                ShapeTitleLogo.Visibility = Visibility.Collapsed
                LabTitleLogo.Visibility = Visibility.Collapsed
                ImageTitleLogo.Visibility = Visibility.Visible
                If Not IsNothing(FrmSetupUI) Then
                    FrmSetupUI.CheckLogoLeft.Visibility = Visibility.Collapsed
                    FrmSetupUI.PanLogoText.Visibility = Visibility.Collapsed
                    FrmSetupUI.PanLogoChange.Visibility = Visibility.Visible
                End If
                Try
                    ImageTitleLogo.Source = Paths.Base & "TCL\Logo.png"
                Catch ex As Exception
                    ImageTitleLogo.Source = Nothing
                    Logger.Error(ex, "显示标题栏图片失败", LogBehavior.Alert)
                End Try
        End Select
        FrmSetupUI?.CardLogo.TriggerForceResize()
        '标题栏文本（无自定义内容时默认显示 TCL）
        LabTitleLogo.Text = If(String.IsNullOrEmpty(Settings.Get(Of String)("UiLogoText")), "TCL", Settings.Get(Of String)("UiLogoText"))
        '标题栏文本是否居左
        '不再把整组导航居中——居左时账号徽章左边会空出一大块（用户反馈）。
        '第 0 列改为 Auto，宽度正好贴合 Logo，导航紧跟其后。
        '（原 UiLogoLeft 分支写的是 0*/1*，靠 0 宽列溢出摆放，该开关的复选框已隐藏，一并废弃）
        PanTitleMain.ColumnDefinitions(0).Width = GridLength.Auto
    End Sub

#End Region

#Region "窗体事件"

    '按键事件
    Private Sub FormMain_KeyDown(sender As Object, e As KeyEventArgs) Handles Me.KeyDown
        If e.IsRepeat Then Return
        '修复按下 Alt 后误认为弹出系统菜单导致的冻结
        If e.SystemKey = Key.LeftAlt OrElse e.SystemKey = Key.RightAlt Then e.Handled = True
        '在有弹窗时：回车选择第一个，Esc 选择最后一个
        If PanMsg.Children.Any Then
            If e.Key = Key.Enter Then
                CType(PanMsg.Children(0), Object).Btn1_Click()
            ElseIf e.Key = Key.Escape Then
                Dim Msg As Object = PanMsg.Children(0)
                If TypeOf Msg IsNot MyMsgInput AndAlso TypeOf Msg IsNot MyMsgSelect AndAlso Msg.Btn3.Visibility = Visibility.Visible Then
                    Msg.Btn3_Click()
                ElseIf Msg.Btn2.Visibility = Visibility.Visible Then
                    Msg.Btn2_Click()
                Else
                    Msg.Btn1_Click()
                End If
            End If
            Return
        End If

        '==========================
        ' 在没有弹窗时：继续检查……
        '==========================

        '按 ESC 取消启动，或返回上一级
        If e.Key = Key.Escape Then
            If PageCurrent = FormMain.PageType.Launch AndAlso McLaunchLoader.State = LoadState.Loading Then
                If FrmLaunchRight IsNot Nothing Then FrmLaunchRight.BtnCancel_Click()
                Return
            End If
            TriggerPageBack()
        End If
        '更改隐藏版本可见性
        If e.Key = Key.F11 AndAlso PageCurrent = FormMain.PageType.InstanceSelect Then
            FrmSelectRight.ShowHidden = Not FrmSelectRight.ShowHidden
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
            Return
        End If
        '按 F5 刷新页面
        If e.Key = Key.F5 Then
            If TypeOf PageLeft Is IRefreshable Then CType(PageLeft, IRefreshable).Refresh()
            If TypeOf PageRight Is IRefreshable Then CType(PageRight, IRefreshable).Refresh()
            Return
        End If
        '调用启动游戏
        If e.Key = Key.Enter AndAlso PageCurrent = FormMain.PageType.Launch Then
            If IsAprilEnabled AndAlso Not IsAprilGiveup Then
                Hint(ModLang.L("木大！"))
            Else
                If FrmLaunchRight IsNot Nothing Then FrmLaunchRight.LaunchButtonClick()
            End If
        End If
    End Sub
    Private Sub FormMain_MouseDown(sender As Object, e As MouseButtonEventArgs) Handles Me.MouseDown
        '鼠标侧键返回上一级
        If FrmMain.PanMsg.Children.Count > 0 OrElse WaitingMyMsgBox.Any Then Return '弹窗中
        If e.ChangedButton = MouseButton.XButton1 OrElse e.ChangedButton = MouseButton.XButton2 Then TriggerPageBack()
    End Sub
    Private Sub TriggerPageBack()
        If PageCurrent = PageType.Download AndAlso PageCurrentSub = PageSubType.DownloadInstall AndAlso FrmDownloadInstall.IsInSelectPage Then
            FrmDownloadInstall.ExitSelectPage()
        Else
            PageBack()
        End If
    End Sub

    '切回窗口
    Private Sub FormMain_Activated() Handles Me.Activated
        '切回窗口时自动刷新
        Try
            If PageCurrent = PageType.InstanceSetup AndAlso PageCurrentSub = PageSubType.InstanceMod Then
                'Mod 管理
                FrmInstanceMod.ReloadModList()
            ElseIf PageCurrent = PageType.InstanceSetup AndAlso (PageCurrentSub = PageSubType.InstanceSetup OrElse PageCurrentSub = PageSubType.InstanceExport) Then
                '更新当前选用的 Java
                PageInstanceLeft.ReloadCurrentJava()
            ElseIf PageCurrent = PageType.InstanceSelect Then
                '版本选择
                LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.RunOnUpdated, MaxDepth:=1, ExtraPath:="versions\")
            End If
        Catch ex As Exception
            Logger.Error(ex, "切回窗口时出错")
        End Try
        '读取剪贴板，自动加入联机房间
        Return 'TODO: 联机复活赛
        If PageLinkMain.LinkState <> PageLinkMain.LinkStates.Waiting Then Return '已启动联机
        If PageCurrent = PageType.Link Then Return '已在联机界面
        Dim Code = ClipboardGetText() : If Code Is Nothing Then Return '剪贴板无文本
        If Settings.Get(Of String)("LinkLastAutoJoinInviteCode") = Code Then Return
        If PageLinkMain.ValidateCodeFormat(Code) IsNot Nothing Then Return '不是邀请码
        Settings.Set("LinkLastAutoJoinInviteCode", Code)
        RunInThread(
        Sub()
            If MyMsgBox(ModLang.L("嘿，是否使用复制的邀请码加入房间？"), ModLang.L("加入联机房间"), ModLang.L("加入"), ModLang.L("取消")) = 2 Then Return '防止弹窗阻碍主线程，所以必须放在工作线程
            RunInUi(
            Sub()
                PageLinkMain.Join(Code)
                ClipboardSet(Nothing, False)
            End Sub)
        End Sub)
    End Sub

    '文件拖放
    Private Sub HandleDrag(sender As Object, e As DragEventArgs)
        Try
            If e.Handled AndAlso (e.Effects <> DragDropEffects.None) Then Return
            e.Handled = True
            '缓存
            Static PrevData As IDataObject, PrevEffects As DragDropEffects
            If e.Data Is PrevData Then
                e.Effects = PrevEffects
                Return
            End If
            '确定拖放效果
            e.Effects = DragDropEffects.None
            If e.Data.GetDataPresent(DataFormats.Text) Then
                Dim Str As String = e.Data.GetData(DataFormats.Text)
                If Str.StartsWithF("authlib-injector:yggdrasil-server:") Then
                    e.Effects = DragDropEffects.Copy
                ElseIf Str.StartsWithF("file:///") Then
                    e.Effects = DragDropEffects.Copy
                End If
            ElseIf e.Data.GetDataPresent(DataFormats.FileDrop) Then
                Dim Files As String() = e.Data.GetData(DataFormats.FileDrop)
                If Files IsNot Nothing AndAlso Files.Length > 0 Then
                    e.Effects = DragDropEffects.Link
                End If
            End If
            PrevData = e.Data
            PrevEffects = e.Effects
            Logger.Info($"设置拖放类型：{e.Effects}")
        Catch ex As Exception
            Logger.Error(ex, "处理拖放时出错")
        End Try
    End Sub
    Private Sub FrmMain_Drop(sender As Object, e As DragEventArgs) Handles Me.Drop
        Try
            ShowWindowToTop()
            If e.Data.GetDataPresent(DataFormats.Text) Then
                '获取文本
                Try
                    Dim Str As String = e.Data.GetData(DataFormats.Text)
                    Logger.Info($"接受文本拖拽：{Str}")
                    If Str.StartsWithF("authlib-injector:yggdrasil-server:") Then
                        'Authlib 拖拽
                        e.Handled = True
                        e.Effects = DragDropEffects.Copy
                        Dim AuthlibServer As String = StringUtils.FormUrlUnescape(Str.Substring("authlib-injector:yggdrasil-server:".Length))
                        Logger.Info($"Authlib 拖拽：{AuthlibServer}")
                        If Not String.IsNullOrEmpty(New ValidateHttp().Validate(AuthlibServer)) Then
                            Hint(ModLang.L("输入的 Authlib 验证服务器不符合网址格式（") & AuthlibServer & ModLang.L("）！"), HintType.Red)
                            Return
                        End If
                        Dim Target = If(PageCurrent = PageType.InstanceSetup, PageInstanceLeft.Instance, McInstanceSelected)
                        If Target Is Nothing Then
                            Hint(ModLang.L("请先下载游戏，再设置第三方登录！"), HintType.Red)
                            Return
                        End If
                        If AuthlibServer = "https://littleskin.cn/api/yggdrasil" Then
                            'LittleSkin
                            If MyMsgBox(ModLang.L("是否要在版本 ") & Target.Name & ModLang.L(" 中开启 LittleSkin 登录？") & vbCrLf &
                                        ModLang.L("你可以在 版本设置 → 设置 → 服务器选项 中修改登录方式。"), ModLang.L("第三方登录开启确认"), ModLang.L("确定"), ModLang.L("取消")) = 2 Then
                                Return
                            End If
                            Settings.Set("VersionServerLogin", 4, Instance:=Target)
                            Settings.Set("VersionServerAuthServer", "https://littleskin.cn/api/yggdrasil", Instance:=Target)
                            Settings.Set("VersionServerAuthRegister", "https://littleskin.cn/auth/register", Instance:=Target)
                            Settings.Set("VersionServerAuthName", "LittleSkin 登录", Instance:=Target)
                        Else
                            '第三方 Authlib 服务器
                            If MyMsgBox(ModLang.L("是否要在版本 ") & Target.Name & ModLang.L(" 中开启第三方登录？") & vbCrLf &
                                        ModLang.L("登录服务器：") & AuthlibServer & vbCrLf & vbCrLf &
                                        ModLang.L("你可以在 版本设置 → 设置 → 服务器选项 中修改登录方式。"), ModLang.L("第三方登录开启确认"), ModLang.L("确定"), ModLang.L("取消")) = 2 Then
                                Return
                            End If
                            Settings.Set("VersionServerLogin", 4, Instance:=Target)
                            Settings.Set("VersionServerAuthServer", AuthlibServer, Instance:=Target)
                            Settings.Set("VersionServerAuthRegister", AuthlibServer.Replace("api/yggdrasil", "auth/register"), Instance:=Target)
                            Settings.Set("VersionServerAuthName", "", Instance:=Target)
                        End If
                        If PageCurrent = PageType.InstanceSetup AndAlso PageCurrentSub = PageSubType.InstanceSetup Then
                            '正在服务器选项页，需要刷新设置项显示
                            FrmInstanceSetup.Reload()
                        ElseIf PageCurrent = PageType.Launch Then
                            '正在主页，需要刷新左边栏
                            FrmLaunchLeft.RefreshPage(True, False)
                        End If
                    ElseIf Str.StartsWithF("file:///") Then
                        '文件拖拽（例如从浏览器下载窗口拖入）
                        Dim FilePath = Net.WebUtility.UrlDecode(Str).Substring("file:///".Length).Replace("/", "\")
                        e.Handled = True
                        e.Effects = DragDropEffects.Copy
                        FileDrag(New List(Of String) From {FilePath})
                    End If
                Catch ex As Exception
                    Logger.Warn(ex, "无法接取文本拖拽事件")
                    Return
                End Try
            ElseIf e.Data.GetDataPresent(DataFormats.FileDrop) Then
                '获取文件并检查
                Dim FilePathRaw = e.Data.GetData(DataFormats.FileDrop)
                If FilePathRaw Is Nothing Then '无可提取的文件路径
                    Hint(ModLang.L("请将文件解压后再拖入！"), HintType.Red)
                    Return
                End If
                e.Handled = True
                e.Effects = DragDropEffects.Link
                FileDrag(CType(FilePathRaw, IEnumerable(Of String)))
            End If
        Catch ex As Exception
            Logger.Error(ex, "接取拖拽事件失败")
        End Try
    End Sub
    Private Sub FileDrag(FilePathList As IEnumerable(Of String))
        RunInNewThread(
        Sub()
            Dim FilePath As String = FilePathList.First
            Logger.Warn($"接受文件拖拽：{FilePath}{If(FilePathList.Any, $" 等 {FilePathList.Count} 个文件", "")}")
            '基础检查
            '拖入文件夹不再直接拒绝——识别为 Minecraft 目录就加进左侧列表并跳到实例页
            If DirectoryUtils.Exists(FilePath) AndAlso Not FileUtils.Exists(FilePath) Then
                DragFolderAdd(FilePath)
                Return
            ElseIf Not FileUtils.Exists(FilePath) Then
                Hint(ModLang.L("拖入的文件不存在：") & FilePath, HintType.Red)
                Return
            End If
            '多文件拖拽
            Dim PathList As List(Of String) = FilePathList.ToList()
            If FilePathList.Count > 1 Then
                '必须要求全部为 jar 文件
                For Each File In PathList
                    If Not {"jar", "litemod", "disabled", "old"}.Contains(PathUtils.GetExtension(File)) Then
                        Hint(ModLang.L("一次请只拖入一个文件！"), HintType.Red)
                        Return
                    End If
                Next
            End If
            Dim Extension As String = PathUtils.GetExtension(FilePath)
            '安装 Mod
            If PageInstanceMod.InstallMods(PathList) Then Return
            '安装整合包
            If {"zip", "rar", "mrpack"}.Any(Function(t) t = Extension) Then '部分压缩包是 zip 格式但后缀为 rar，总之试一试
                Hint(ModLang.L("正在读取压缩包……"))
                Dim PackInfo As McModpackInfo = Nothing
                Try
                    PackInfo = McModpackReadInfo(FilePath)
                Catch ex As Exception
                    Logger.Warn(ex, "预读整合包信息失败，按原流程尝试安装")
                    PackInfo = Nothing
                End Try
                If PackInfo IsNot Nothing AndAlso PackInfo.IsValid Then
                    Try
                        '弹窗必须在 UI 线程：ModpackInstall 内部还会弹「输入版本名称」，同样依赖 UI 线程
                        Dim Choice As Integer = RunInUiWait(Function() MyMsgBoxModpack(PackInfo))
                        If Choice <> 1 Then
                            Hint(ModLang.L("已取消安装整合包。"), HintType.Blue)
                            Return
                        End If
                        Logger.Info($"文件为整合包（{PackInfo.TypeName}：{PackInfo.Name}），开始安装")
                        ModpackInstall(FilePath)
                        RunInUi(Sub() FrmMain.PageChange(FormMain.PageType.TaskManager))
                        Return
                    Catch ex As Exception
                        If ex.IsCanceled Then Return
                        '安装失败，继续往后尝试
                    End Try
                End If
            End If
            'RAR 处理
            If Extension = "rar" Then
                Hint(ModLang.L("TCL 无法处理 rar 格式的压缩包，请在解压后重新压缩为 zip 格式再试！"), HintType.Red)
                Return
            End If
            '错误报告分析
            Try
                Logger.Info("尝试进行错误报告分析")
                Dim Analyzer As New CrashAnalyzer
                Analyzer.Import(FilePath)
                If Not Analyzer.Prepare() Then Exit Try
                Analyzer.Analyze()
                Analyzer.Output(True, New List(Of String))
                Return
            Catch ex As Exception
                Logger.Error(ex, "自主错误报告分析失败")
            End Try
            '未知操作
            Hint(ModLang.L("TCL 无法确定适合的操作……"), HintType.Red)
        End Sub, "文件拖拽")
    End Sub

    ''' <summary>
    ''' 处理「把文件夹拖进窗口」：识别成 Minecraft 目录就加入左侧文件夹列表，并跳到实例页。
    ''' <para/>新增。运行在后台线程（由 FileDrag 调度）。
    ''' </summary>
    Private Sub DragFolderAdd(Source As String)
        Try
            Dim Target As String = PageSelectLeft.DetectMinecraftFolder(Source)
            If String.IsNullOrEmpty(Target) Then
                Hint(ModLang.L("拖入的文件夹里没有 versions 目录，不是可识别的 Minecraft 文件夹。"), HintType.Red)
                Return
            End If
            Dim Name As String = PageSelectLeft.SuggestFolderName(Target)
            '先切到实例页，添加完成时列表就在眼前（AddFolder 内部异步加载，加载完会自动刷新列表）
            RunInUi(Sub() FrmMain.PageChange(FormMain.PageType.InstanceSelect))
            PageSelectLeft.AddFolder(Target, Name, True)
        Catch ex As Exception
            Logger.Error(ex, "拖入文件夹添加失败")
            Hint(ModLang.L("添加文件夹失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    '接受到 Windows 窗体事件
    Public IsSystemTimeChanged As Boolean = False
    Private Function WndProc(hwnd As IntPtr, msg As Integer, wParam As IntPtr, lParam As IntPtr, ByRef handled As Boolean) As IntPtr
        If msg = 30 Then
            Dim NowDate = Date.Now
            If NowDate.Date = ApplicationOpenTime.Date Then
                Logger.Info($"系统时间微调为：{NowDate.ToLongDateString} {NowDate.ToLongTimeString}")
                IsSystemTimeChanged = False
            Else
                Logger.Info($"系统时间修改为：{NowDate.ToLongDateString} {NowDate.ToLongTimeString}")
                IsSystemTimeChanged = True
            End If
        ElseIf msg = 400 * 16 + 2 Then
            Logger.Info($"收到置顶信息：{hwnd.ToInt64}")
            If Not IsWindowLoadFinished Then
                Logger.Info("窗口尚未加载完成，忽略置顶请求")
                Return IntPtr.Zero
            End If
            ShowWindowToTop()
            handled = True
        End If
        Return IntPtr.Zero
    End Function

    '窗口隐藏与置顶
    Private _Hidden As Boolean = False
    Public Property Hidden As Boolean
        Get
            Return _Hidden
        End Get
        Set(value As Boolean)
            If _Hidden = value Then Return
            _Hidden = value
            If value Then
                '隐藏
                Left -= 10000
                ShowInTaskbar = False
                Visibility = Visibility.Hidden
                ModVideo.VideoPauseForHidden() '窗口不可见后 MediaElement 仍会解码背景视频，必须显式暂停
                Logger.Info($"窗口已隐藏，位置：({Left},{Top})")
            Else
                '取消隐藏
                If Left < -2000 Then Left += 10000
                ShowWindowToTop()
                ModVideo.VideoResumeFromHidden()
            End If
        End Set
    End Property
    ''' <summary>
    ''' 把当前窗口拖到最前面。
    ''' </summary>
    Public Sub ShowWindowToTop()
        RunInUi(
        Sub()
            '这一坨乱七八糟的，别改，改了指不定就炸了，自己电脑还复现不出来
            Visibility = Visibility.Visible
            ShowInTaskbar = True
            WindowState = WindowState.Normal
            Hidden = False
            Topmost = True '偶尔 SetForegroundWindow 失效
            Topmost = False
            SetForegroundWindow(Handle)
            Focus()
            Logger.Info($"窗口已置顶，位置：({Left}, {Top}), {Width} x {Height}")
        End Sub)
    End Sub

#End Region

#Region "切换页面"

    '页面种类与属性
    ''' <summary>
    ''' 页面种类。
    ''' 该枚举在自定义事件中使用，是公开 API 的一部分。
    ''' </summary>
    Public Enum PageType
        ''' <summary>
        ''' 启动。
        ''' </summary>
        Launch = 0
        ''' <summary>
        ''' 下载。
        ''' </summary>
        Download = 1
        ''' <summary>
        ''' 联机。
        ''' </summary>
        Link = 2
        ''' <summary>
        ''' 设置。
        ''' </summary>
        Setup = 3
        ''' <summary>
        ''' 更多。
        ''' </summary>
        Other = 4
        ''' <summary>
        ''' 版本选择。这是一个副页面。
        ''' </summary>
        InstanceSelect = 5
        ''' <summary>
        ''' 任务管理。这是一个副页面。
        ''' </summary>
        TaskManager = 6
        ''' <summary>
        ''' 版本设置。这是一个副页面。
        ''' </summary>
        InstanceSetup = 7
        ''' <summary>
        ''' 社区资源详情。这是一个副页面。
        ''' </summary>
        ResourceDetail = 8
        ''' <summary>
        ''' 账户管理。这是一个副页面。
        ''' </summary>
        Login = 9
    End Enum
    ''' 账户管理区的次级分类（Login 子页面使用）。
    Public Enum AccountSubType
        AccountList = 0
        AccountAdd = 1
    End Enum
    ''' <summary>
    ''' 次要页面种类。其数值必须与 StackPanel 中的下标一致。
    ''' 该枚举在自定义事件中使用，是公开 API 的一部分。
    ''' </summary>
    Public Enum PageSubType
        [Default] = 0
        DownloadInstall = 0
        DownloadMod = 2
        DownloadPack = 3
        DownloadDataPack = 4
        DownloadResourcePack = 5
        DownloadShader = 6
        DownloadFavorites = 7
        DownloadWorld = 8
        SetupLaunch = 0
        SetupLink = 1
        SetupUI = 2
        SetupSystem = 3
        SetupJava = 4
        SetupLog = 5
        SetupMore = 6
        SetupCache = 7
        ''' <summary>
        ''' 网络（代理与下载源测速）。新增。
        ''' </summary>
        SetupNetwork = 8
        LinkMain = 0
        OtherHelp = 0
        OtherAbout = 1
        OtherTest = 2
        OtherNews = 3
        InstanceOverall = 0
        InstanceSetup = 1
        InstanceModify = 2
        InstanceMod = 3
        InstanceModDisabled = 4
        InstanceExport = 5
        InstanceSaves = 6
        InstanceScreenshot = 7
        InstanceServer = 8
        InstanceJarInJar = 9
        InstanceCompResource = 10
        InstanceSavesDatapack = 12
        ''' <summary>
        ''' 诊断（日志阅读器）。新增，对齐 LumilioCL 的游戏页诊断。
        ''' </summary>
        InstanceDiagnostics = 13
        ''' <summary>
        ''' 游戏选项（options.txt 编辑）。新增，对齐 LumilioCL 的「设置 › 选项」。
        ''' </summary>
        InstanceOptions = 14
        ''' <summary>
        ''' 历史（游玩记录 / 快照 / 变更）。新增，对齐 LumilioCL 的游戏页历史。
        ''' </summary>
        InstanceHistory = 15
        ''' <summary>
        ''' 资源包管理（实例 resourcepacks）。新增，对齐 HMCL / SJMCL 的实例内容管理。
        ''' </summary>
        InstanceResourcePack = 16
        ''' <summary>
        ''' 光影包管理（实例 shaderpacks）。新增。
        ''' </summary>
        InstanceShader = 17
    End Enum
    ''' <summary>
    ''' 获取次级页面的名称。若并非次级页面则返回空字符串，故可以以此判断是否为次级页面。
    ''' </summary>
    Private Function PageNameGet(Stack As PageStackData) As String
        Select Case Stack.Page
            Case PageType.InstanceSelect
                Return "" 
            Case PageType.TaskManager
                Return "任务管理"
            Case PageType.InstanceSetup
                Return ModLang.L("版本设置 - ") & If(PageInstanceLeft.Instance Is Nothing, ModLang.L("未知版本"), PageInstanceLeft.Instance.Name)
            Case PageType.ResourceDetail
                Return CType(Stack.Additional(0), ResourceProject).TranslatedName
            Case PageType.Login
                Return "账户管理" '改为 HMCL 式全屏子页面（标题栏返回键返回）
            Case Else
                Return ""
        End Select
    End Function
    ''' <summary>
    ''' 刷新次级页面的名称。
    ''' </summary>
    Public Sub PageNameRefresh(Type As PageStackData)
        LabTitleInner.Text = PageNameGet(Type)
    End Sub
    ''' <summary>
    ''' 刷新次级页面的名称。
    ''' </summary>
    Public Sub PageNameRefresh()
        PageNameRefresh(PageCurrent)
    End Sub

    '页面状态存储
    ''' <summary>
    ''' 当前的主页面。
    ''' </summary>
    Public PageCurrent As PageStackData = PageType.Launch
    ''' <summary>
    ''' 上一个主页面。
    ''' </summary>
    Public PageLast As PageStackData = PageType.Launch
    ''' <summary>
    ''' 当前的子页面。
    ''' </summary>
    Public ReadOnly Property PageCurrentSub As PageSubType
        Get
            Select Case PageCurrent
                Case PageType.Download
                    If FrmDownloadLeft Is Nothing Then FrmDownloadLeft = New PageDownloadLeft
                    Return FrmDownloadLeft.PageID
                Case PageType.Setup
                    If FrmSetupLeft Is Nothing Then FrmSetupLeft = New PageSetupLeft
                    Return FrmSetupLeft.PageID
                Case PageType.Other
                    If FrmOtherLeft Is Nothing Then FrmOtherLeft = New PageOtherLeft
                    Return FrmOtherLeft.PageID
                Case PageType.InstanceSetup
                    If FrmInstanceLeft Is Nothing Then FrmInstanceLeft = New PageInstanceLeft
                    Return FrmInstanceLeft.PageID
                Case Else
                    Return 0 '没有子页面
            End Select
        End Get
    End Property
    ''' <summary>
    ''' 上层页面的编号堆栈，用于返回。
    ''' </summary>
    Public PageStack As New List(Of PageStackData)
    Public Class PageStackData

        Public Page As PageType
        Public Additional As Object

        Public Overrides Function Equals(other As Object) As Boolean
            If other Is Nothing Then Return False
            If TypeOf other Is PageStackData Then
                Dim PageOther As PageStackData = other
                If Page <> PageOther.Page Then Return False
                If Additional Is Nothing Then
                    Return PageOther.Additional Is Nothing
                Else
                    Return PageOther.Additional IsNot Nothing AndAlso Additional.Equals(PageOther.Additional)
                End If
            ElseIf TypeOf other Is Integer Then
                If Page <> other Then Return False
                Return Additional Is Nothing
            Else
                Return False
            End If
        End Function
        Public Shared Operator =(left As PageStackData, right As PageStackData) As Boolean
            Return EqualityComparer(Of PageStackData).Default.Equals(left, right)
        End Operator
        Public Shared Operator <>(left As PageStackData, right As PageStackData) As Boolean
            Return Not left = right
        End Operator
        Public Shared Widening Operator CType(Value As PageType) As PageStackData
            Return New PageStackData With {.Page = Value}
        End Operator
        Public Shared Widening Operator CType(Value As PageStackData) As PageType
            Return Value.Page
        End Operator
    End Class
    Public PageLeft As MyPageLeft, PageRight As MyPageRight

    '引发实际页面切换的入口
    Private IsChangingPage As Boolean = False
    ''' <summary>
    ''' 切换页面，并引起对应选择 UI 的改变。
    ''' </summary>
    Public Sub PageChange(Stack As PageStackData, Optional SubType As PageSubType = PageSubType.Default)
        If PageNameGet(Stack) = "" Then
            '切换到主页面
            PageChangeExit()
            IsChangingPage = True '防止下面的勾选直接触发了 PageChangeActual
            Dim _tabTag As Integer = If(Stack.Page = PageType.Setup, CInt(PageType.Other), CInt(Stack.Page))
            Dim _targetTab As MyRadioButton = Nothing
            For Each _r In PanTitleSelect.Children
                If TypeOf _r Is MyRadioButton AndAlso _r.Tag IsNot Nothing AndAlso Val(_r.Tag) = _tabTag Then
                    _targetTab = _r
                    Exit For
                End If
            Next
            If _targetTab IsNot Nothing Then _targetTab.SetChecked(True, True, PageNameGet(PageCurrent) = "")
            IsChangingPage = False
            Select Case Stack.Page
            Case PageType.Download
                If FrmDownloadLeft Is Nothing Then FrmDownloadLeft = New PageDownloadLeft
                FrmDownloadLeft.ItemGet(SubType)?.SetChecked(True, True, Stack = PageCurrent)
                Case PageType.Setup
                    If FrmSetupLeft Is Nothing Then FrmSetupLeft = New PageSetupLeft
                    '侧边栏「清理缓存」上移、「返回」固定为最后一项，物理索引不再等于 SubType，
                    '故高亮改按 Tag 匹配（ItemGet），与 PageOtherLeft 一致；匹配不到则跳过，不影响切页。
                    Dim _setupItem As MyListItem = FrmSetupLeft.ItemGet(SubType)
                    If _setupItem IsNot Nothing Then _setupItem.SetChecked(True, True, Stack = PageCurrent)
                Case PageType.Other
                    If FrmOtherLeft Is Nothing Then FrmOtherLeft = New PageOtherLeft
                    FrmOtherLeft.ItemGet(SubType)?.SetChecked(True, True, Stack = PageCurrent)
            End Select
            PageChangeActual(Stack, SubType)
        Else
            '切换到次页面
            Select Case Stack.Page
                Case PageType.InstanceSetup
                    If FrmInstanceLeft Is Nothing Then FrmInstanceLeft = New PageInstanceLeft
                    '修复：不能直接用索引访问 PanItem.Children，因为侧边栏顺序和 SubType 编号不对应
                    '否则跳转到诊断页（SubType=13）时会错误选中索引 13 对应的「服务器」按钮
                    Dim _instanceItem As MyListItem = FrmInstanceLeft.ItemGet(SubType)
                    If _instanceItem IsNot Nothing Then _instanceItem.SetChecked(True, True, Stack = PageCurrent)
            End Select
            PageChangeActual(Stack, SubType)
        End If
    End Sub
    ''' <summary>
    ''' 通过点击导航栏改变页面。
    ''' </summary>
    Private Sub BtnTitleSelect_Click(sender As MyRadioButton, raiseByMouse As Boolean) Handles BtnTitleSelect0.Check, BtnTitleSelect5.Check, BtnTitleSelect1.Check, BtnTitleSelect2.Check, BtnTitleSelect4.Check
        If IsChangingPage Then Return
        PageChangeActual(Val(sender.Tag))
    End Sub
    ''' <summary>
    ''' 通过点击返回按钮或手动触发返回来改变页面。
    ''' </summary>
    Public Sub PageBack() Handles BtnTitleInner.Click
        If PageStack.Any() Then
            PageChangeActual(PageStack(0))
        Else
            PageChange(PageType.Launch)
        End If
    End Sub

    '实际处理页面切换
    ''' <summary>
    ''' 切换现有页面的实际方法。
    ''' </summary>
    Private Sub PageChangeActual(Stack As PageStackData, Optional SubType As PageSubType = -1)
        Application.EnsureDelayedUiLoaded() '页面构造依赖延迟字典里的样式
        If PageCurrent = Stack AndAlso (PageCurrentSub = SubType OrElse SubType = -1) Then Return
        If Stack.Page = PageType.InstanceSetup AndAlso PageInstanceLeft.Instance IsNot Nothing AndAlso McInstanceLock.IsLocked(PageInstanceLeft.Instance) Then
            Hint(ModLang.L("该版本正在被修改，暂时无法进入版本设置！"), HintType.Red)
            Return
        End If
        AniControlEnabled += 1
        Try

#Region "子页面处理"
            Dim PageName As String = PageNameGet(Stack)
            If PageName = "" Then
                '即将切换到一个顶级页面
                PageChangeExit()
            Else
                '即将切换到一个子页面
                If PageStack.Any Then
                    '子页面 → 另一个子页面，更新
                    AniStart({
                        AaOpacity(LabTitleInner, -LabTitleInner.Opacity, 130),
                        AaCode(Sub() LabTitleInner.Text = PageName,, True),
                        AaOpacity(LabTitleInner, 1, 150, 30)
                    }, "FrmMain Titlebar SubLayer")
                    If PageStack.Contains(Stack) Then
                        '返回到更上层的子页面
                        Do While PageStack.Contains(Stack)
                            PageStack.RemoveAt(0)
                        Loop
                    Else
                        '进入更深层的子页面
                        PageStack.Insert(0, PageCurrent)
                    End If
                Else
                    '主页面 → 子页面，进入
                    PanTitleInner.Visibility = Visibility.Visible
                    PanTitleMain.IsHitTestVisible = False
                    PanTitleInner.IsHitTestVisible = True
                    PageNameRefresh(Stack)
                    AniStart({
                        AaOpacity(PanTitleMain, -PanTitleMain.Opacity, 150),
                        AaX(PanTitleMain, 12 - PanTitleMain.Margin.Left, 150,, New AniEaseInFluent(AniEasePower.Weak)),
                        AaOpacity(PanTitleInner, 1 - PanTitleInner.Opacity, 150, 200),
                        AaX(PanTitleInner, -PanTitleInner.Margin.Left, 350, 200, New AniEaseOutBack),
                        AaCode(Sub() PanTitleMain.Visibility = Visibility.Collapsed,, True)
                    }, "FrmMain Titlebar FirstLayer")
                    PageStack.Insert(0, PageCurrent)
                End If
            End If
#End Region

#Region "实际更改页面框架 UI"
            PageLast = PageCurrent
            PageCurrent = Stack
            Select Case Stack.Page
                Case PageType.Launch '启动
                    PageChangeAnim(FrmLaunchLeft, FrmLaunchRight)
                Case PageType.Download '下载
                    If FrmDownloadLeft Is Nothing Then FrmDownloadLeft = New PageDownloadLeft
                    'PageGet 方法会在未设置 SubType 时指定默认值，并建立相关页面的实例
                    PageChangeAnim(FrmDownloadLeft, FrmDownloadLeft.PageGet(SubType))
                Case PageType.Link '联机
                    If FrmLinkMain Is Nothing Then FrmLinkMain = New PageLinkMain
                    PageChangeAnim(New MyPageLeft, FrmLinkMain)
                Case PageType.Setup '设置
                    If FrmSetupLeft Is Nothing Then FrmSetupLeft = New PageSetupLeft
                    PageChangeAnim(FrmSetupLeft, FrmSetupLeft.PageGet(SubType))
                Case PageType.Other '更多
                    If FrmOtherLeft Is Nothing Then FrmOtherLeft = New PageOtherLeft
                    PageChangeAnim(FrmOtherLeft, FrmOtherLeft.PageGet(SubType))
                Case PageType.InstanceSelect '版本选择
                    If FrmSelectLeft Is Nothing Then FrmSelectLeft = New PageSelectLeft
                    If FrmSelectRight Is Nothing Then FrmSelectRight = New PageSelectRight
                    PageChangeAnim(FrmSelectLeft, FrmSelectRight)
                Case PageType.TaskManager '任务管理
                    If FrmSpeedLeft Is Nothing Then FrmSpeedLeft = New PageSpeedLeft
                    If FrmSpeedRight Is Nothing Then FrmSpeedRight = New PageSpeedRight
                    PageChangeAnim(FrmSpeedLeft, FrmSpeedRight)
                Case PageType.InstanceSetup '版本设置
                    If FrmInstanceLeft Is Nothing Then FrmInstanceLeft = New PageInstanceLeft
                    PageChangeAnim(FrmInstanceLeft, FrmInstanceLeft.PageGet(SubType))
                Case PageType.ResourceDetail '社区资源详情
                    If FrmDownloadResourceDetail Is Nothing Then FrmDownloadResourceDetail = New PageDownloadResourceDetail
                    PageChangeAnim(New MyPageLeft, FrmDownloadResourceDetail)
                Case PageType.Login '账户管理
                    If FrmLoginManager Is Nothing Then FrmLoginManager = New PageAccountManager
                    PageChangeAnim(New MyPageLeft, FrmLoginManager)
            End Select
#End Region

#Region "设置为最新状态"
            BtnExtraDownload.ShowRefresh()
            BtnExtraApril.ShowRefresh()
#End Region

            Logger.Info($"切换主要页面：{Stack.Page}, {SubType}")
        Catch ex As Exception
            Logger.Error(ex, $"切换主要页面失败（ID {PageCurrent.Page}）")
        Finally
            AniControlEnabled -= 1
        End Try
        If AniControlEnabled = 0 Then PanMainLeft_Resize(PanMainLeft.ActualWidth) '切页后重算左边栏罩子（首页不画）
    End Sub
    Private Sub PageChangeAnim(TargetLeft As FrameworkElement, TargetRight As FrameworkElement)
        AniStop("FrmMain LeftChange")
        AniStop("PageLeft PageChange") '停止左边栏变更导致的右页面切换动画，防止它与本动画一起触发多次 PageOnEnter
        AniControlEnabled += 1
        '清除新页面关联性
        If TargetLeft.Parent IsNot Nothing Then TargetLeft.SetValue(ContentPresenter.ContentProperty, Nothing)
        If TargetRight IsNot Nothing AndAlso TargetRight.Parent IsNot Nothing Then TargetRight.SetValue(ContentPresenter.ContentProperty, Nothing)
        PageLeft = TargetLeft
        PageRight = TargetRight
        '触发页面通用动画
        '这里原本是盲 CType，Child 为空时抛异常会让整个 PageChangeActual 中断，
        '而标题栏此时已经被置为 IsHitTestVisible=False，于是「所有顶部按钮都点不动」。
        Dim OldLeft = TryCast(PanMainLeft.Child, MyPageLeft)
        If OldLeft IsNot Nothing Then OldLeft.TriggerHideAnimation()
        Dim OldRight = TryCast(PanMainRight.Child, MyPageRight)
        If OldRight IsNot Nothing Then OldRight.PageOnExit()
        AniControlEnabled -= 1
        '执行动画
        AniStart({
            AaCode(
            Sub()
                AniControlEnabled += 1
                '把新页面添加进容器
                PanMainLeft.Child = PageLeft
                PageLeft.Opacity = 0
                PanMainLeft.Background = Nothing
                AniControlEnabled -= 1
                RunInUi(Sub() PanMainLeft_Resize(PanMainLeft.ActualWidth), True)
            End Sub, 110),
            AaCode(
            Sub()
                '延迟触发页面通用动画，以使得在 Loaded 事件中加载的控件得以处理
                PageLeft.Opacity = 1
                PageLeft.TriggerShowAnimation()
                '新页面（含懒加载刚实例化者）此刻已完全 Loaded，套一遍译文，避免停在旧语言
                ModLang.ApplyTo(PageLeft)
            End Sub, 30, True)
        }, "FrmMain PageChangeLeft")
        AniStart({
            AaCode(
            Sub()
                AniControlEnabled += 1
                Dim OldRightPage = TryCast(PanMainRight.Child, MyPageRight)
                If OldRightPage IsNot Nothing Then OldRightPage.PageOnForceExit()
                '把新页面添加进容器
                PanMainRight.Child = PageRight
                PageRight.Opacity = 0
                PanMainRight.Background = Nothing
                AniControlEnabled -= 1
                RunInUi(Sub() BtnExtraBack.ShowRefresh(), True)
            End Sub, 110),
            AaCode(
            Sub()
                '延迟触发页面通用动画，以使得在 Loaded 事件中加载的控件得以处理
                PageRight.Opacity = 1
                PageRight.PageOnEnter()
                '新页面（含懒加载刚实例化者）此刻已完全 Loaded，套一遍译文，避免停在旧语言
                ModLang.ApplyTo(PageRight)
            End Sub, 30, True)
        }, "FrmMain PageChangeRight")
    End Sub
    ''' <summary>
    ''' 退出子界面。
    ''' </summary>
    Private Sub PageChangeExit()
        If PageStack.Any Then
            '子页面 → 主页面，退出
            PanTitleMain.Visibility = Visibility.Visible
            PanTitleMain.IsHitTestVisible = True
            PanTitleInner.IsHitTestVisible = False
            AniStart({
                AaOpacity(PanTitleInner, -PanTitleInner.Opacity, 150),
                AaX(PanTitleInner, -18 - PanTitleInner.Margin.Left, 150,, New AniEaseInFluent),
                AaOpacity(PanTitleMain, 1 - PanTitleMain.Opacity, 150, 200),
                AaX(PanTitleMain, -PanTitleMain.Margin.Left, 350, 200, New AniEaseOutBack(AniEasePower.Weak)),
                AaCode(Sub() PanTitleInner.Visibility = Visibility.Collapsed,, True)
            }, "FrmMain Titlebar FirstLayer")
            PageStack.Clear()
        Else
            '主页面 → 主页面，无事发生
        End If
    End Sub

    '左边栏改变
    Private Sub PanMainLeft_SizeChanged(sender As Object, e As SizeChangedEventArgs) Handles PanMainLeft.SizeChanged
        If Not e.WidthChanged Then Return
        PanMainLeft_Resize(e.NewSize.Width)
    End Sub
    ''' <summary>
    ''' 目标左栏是否真的是一块内容栏。
    ''' 联机、社区资源详情、账户管理等页面传的是空的 New MyPageLeft 占位（本来就没有侧边栏），
    ''' 这种页面不能再画左边栏罩子——否则罩子会先按旧页面的宽度展开、再随空栏宽度归零收起，
    ''' 表现就是「侧边栏突然出现又消失」的无用动画。
    ''' </summary>
    Private ReadOnly Property HasRealLeftPanel As Boolean
        Get
            '用 PageLeft（目标页）而不是 PanMainLeft.Child：切页刚发生时 Child 还是上一页，
            '按旧页面判断就会先画一次罩子，那正是这个闪烁的成因。
            Dim Target As MyPageLeft = If(PageLeft, TryCast(PanMainLeft.Child, MyPageLeft))
            If Target Is Nothing Then Return False
            Return Target.GetType() IsNot GetType(MyPageLeft)
        End Get
    End Property
    Private Sub PanMainLeft_Resize(NewWidth As Double)
        '首页（Launch）与没有侧边栏的页面都不绘制左边栏罩子：壁纸完整透出
        Dim ShowCover As Boolean = PageCurrent <> FormMain.PageType.Launch AndAlso HasRealLeftPanel
        Dim CoverWidth As Double = If(ShowCover, NewWidth, 0)
        Dim CoverOpacity As Double = If(ShowCover AndAlso NewWidth > 0, 1, 0)
        Dim Delta As Double = CoverWidth - RectLeftBackground.Width
        If Math.Abs(Delta) > 0.1 OrElse Math.Abs(CoverOpacity - RectLeftShadow.Opacity) > 0.05 Then
            If AniControlEnabled = 0 Then
                If PanMain.Opacity < 0.1 Then PanMainLeft.IsHitTestVisible = False '避免左边栏指向背景未能完美覆盖左边栏
                If Math.Abs(Delta) > 0.1 Then Logger.Info($"左边栏罩子：{RectLeftBackground.Width:0} → {CoverWidth:0}（页面 {PageCurrent.Page}，左栏 {(If(HasRealLeftPanel, "有", "无"))}）")
                If CoverWidth > 0 AndAlso CoverOpacity > 0 Then
                    '宽度足够，显示
                    AniStart({
                        AaWidth(RectLeftBackground, CoverWidth - RectLeftBackground.Width, 180,, New AniEaseOutFluent(AniEasePower.ExtraStrong)),
                        AaOpacity(RectLeftShadow, CoverOpacity - RectLeftShadow.Opacity, 180),
                        AaCode(Sub() PanMainLeft.IsHitTestVisible = True, 150)
                    }, "FrmMain LeftChange", True)
                Else
                    '宽度不足或首页：隐藏
                    AniStart({
                        AaWidth(RectLeftBackground, -RectLeftBackground.Width, 180,, New AniEaseOutFluent),
                        AaOpacity(RectLeftShadow, -RectLeftShadow.Opacity, 180),
                        AaCode(Sub() PanMainLeft.IsHitTestVisible = True, 150)
                    }, "FrmMain LeftChange", True)
                End If
                Return
            End If
        End If
        RectLeftBackground.Width = CoverWidth
        PanMainLeft.IsHitTestVisible = True
        AniStop("FrmMain LeftChange")
    End Sub

#End Region

#Region "控件拖动"

    '在时钟中调用，使得即使鼠标在窗口外松开，也可以释放控件
    Public Sub DragTick()
        If DragControl Is Nothing Then Return
        If Not Mouse.LeftButton = MouseButtonState.Pressed Then
            DragStop()
        End If
    End Sub
    '在鼠标移动时调用，以改变 Slider 位置
    Public Sub DragDoing() Handles PanBack.MouseMove
        If DragControl Is Nothing Then Return
        If Mouse.LeftButton = MouseButtonState.Pressed Then
            DragControl.DragDoing()
        Else
            DragStop()
        End If
    End Sub
    Public Sub DragStop()
        '存在其他线程调用的可能性，因此需要确保在 UI 线程运行
        RunInUi(Sub()
                    If DragControl Is Nothing Then Return
                    Dim Control = DragControl
                    DragControl = Nothing
                    Control.DragStop() '控件会在该事件中判断 DragControl，所以得放在后面
                End Sub)
    End Sub

#End Region

#Region "附加按钮"

    '音乐
    Private Sub BtnExtraMusic_Click(sender As Object, e As EventArgs) Handles BtnExtraMusic.Click
        MusicControlPause()
    End Sub
    Private Sub BtnExtraMusic_RightClick(sender As Object, e As EventArgs) Handles BtnExtraMusic.RightClick
        MusicControlNext()
    End Sub

    '任务管理
    Private Sub BtnExtraDownload_Click(sender As Object, e As EventArgs) Handles BtnExtraDownload.Click
        PageChange(PageType.TaskManager)
    End Sub
    Private Function BtnExtraDownload_ShowCheck() As Boolean
        Return HasDownloadingTask() AndAlso Not PageCurrent = PageType.TaskManager
    End Function

    '投降
    Public Sub AprilGiveup() Handles BtnExtraApril.Click
        If IsAprilEnabled AndAlso Not IsAprilGiveup Then
            Hint("=D", HintType.Green)
            IsAprilGiveup = True
            FrmLaunchRight.AprilScaleTrans.ScaleX = 1
            FrmLaunchRight.AprilScaleTrans.ScaleY = 1
            BtnExtraApril.ShowRefresh()
        End If
    End Sub
    Public Function BtnExtraApril_ShowCheck() As Boolean
        Return IsAprilEnabled AndAlso Not IsAprilGiveup AndAlso PageCurrent = PageType.Launch
    End Function

    '关闭 Minecraft
    Public Sub BtnExtraShutdown_Click() Handles BtnExtraShutdown.Click
        Try
            If McLaunchLoaderReal IsNot Nothing Then McLaunchLoaderReal.Cancel()
            For Each Watcher In McWatcherList
                Watcher.Kill()
            Next
            Hint(ModLang.L("已关闭运行中的 Minecraft！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "强制关闭所有 Minecraft 失败")
        End Try
    End Sub
    Public Function BtnExtraShutdown_ShowCheck() As Boolean
        Return HasRunningMinecraft
    End Function

    ''' <summary>
    ''' 返回顶部。
    ''' </summary>
    Public Sub BackToTop() Handles BtnExtraBack.Click
        Dim RealScroll As MyScrollViewer = BtnExtraBack_GetRealChild()
        If RealScroll IsNot Nothing Then
            RealScroll.PerformVerticalOffsetDelta(-RealScroll.VerticalOffset)
        Else
            Logger.Error("无法返回顶部，未找到合适的 RealScroll", LogBehavior.Toast)
        End If
    End Sub
    Private Function BtnExtraBack_ShowCheck() As Boolean
        Dim RealScroll As MyScrollViewer = BtnExtraBack_GetRealChild()
        Return RealScroll IsNot Nothing AndAlso RealScroll.Visibility = Visibility.Visible AndAlso RealScroll.VerticalOffset > Height + If(BtnExtraBack.Show, 0, 700)
    End Function
    Private Function BtnExtraBack_GetRealChild() As MyScrollViewer
        If PanMainRight.Child Is Nothing OrElse TypeOf PanMainRight.Child IsNot MyPageRight Then Return Nothing
        Dim Page As MyPageRight = PanMainRight.Child
        Return Page.FindName(Page.PanScroll)
    End Function

#End Region

    '愚人节鼠标位置
    Public lastMouseArg As MouseEventArgs = Nothing
    Private Sub FormMain_MouseMove(sender As Object, e As MouseEventArgs) Handles Me.MouseMove
        lastMouseArg = e
    End Sub

#Region "全局聚合搜索"

    ''' <summary>结果行对应的动作（回车触发第一条）。</summary>
    Private GlobalSearchActions As New List(Of Action)
    ''' <summary>上次关闭的时间戳，250ms 重点守卫。</summary>
    Private GlobalSearchClosedTick As Long = 0

    Private Sub BtnTitleSearch_Click(sender As Object, e As RoutedEventArgs) Handles BtnTitleSearch.Click
        OpenGlobalSearch()
    End Sub

    Private Sub FormMain_PreviewKeyDown(sender As Object, e As KeyEventArgs) Handles Me.PreviewKeyDown
        If e.Key = Key.K AndAlso Keyboard.Modifiers = ModifierKeys.Control Then
            e.Handled = True
            If PopupGlobalSearch.IsOpen Then
                CloseGlobalSearch()
            Else
                OpenGlobalSearch()
            End If
        ElseIf e.Key = Key.Escape AndAlso PopupGlobalSearch.IsOpen Then
            CloseGlobalSearch()
        End If
    End Sub

    Private Sub OpenGlobalSearch()
        If Environment.TickCount - GlobalSearchClosedTick < 250 Then Return '关闭动画期间重点守卫
        Try
            '复位动画初值（Popup 关着时改值不会闪）
            TransformGlobalSearchScale.ScaleX = 0.94
            TransformGlobalSearchScale.ScaleY = 0.94
            TransformGlobalSearchPos.Y = 10
            TxtGlobalSearch.Text = ""
            PopupGlobalSearch.PlacementTarget = Me
            PopupGlobalSearch.IsOpen = True
        Catch ex As Exception
            Logger.Error(ex, "打开全局搜索失败")
        End Try
    End Sub

    Private Sub PopupGlobalSearch_Opened(sender As Object, e As EventArgs) Handles PopupGlobalSearch.Opened
        TxtGlobalSearch.Focus()
        If Not MenuCard.PopupAnimEnabled Then
            TransformGlobalSearchScale.ScaleX = 1
            TransformGlobalSearchScale.ScaleY = 1
            TransformGlobalSearchPos.Y = 0
            CardGlobalSearch.Opacity = MenuCard.PopupBaseOpacity
            Return
        End If
        AniStart({
            AaOpacity(CardGlobalSearch, MenuCard.PopupBaseOpacity - CardGlobalSearch.Opacity, 130),
            AaDouble(Sub(i) TransformGlobalSearchScale.ScaleX += i, 1 - TransformGlobalSearchScale.ScaleX, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformGlobalSearchScale.ScaleY += i, 1 - TransformGlobalSearchScale.ScaleY, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformGlobalSearchPos.Y += i, 0 - TransformGlobalSearchPos.Y, 220, , New AniEaseOutFluent(AniEasePower.Weak))
        }, "GlobalSearch Popup")
    End Sub

    Private Sub CloseGlobalSearch()
        Try
            If Not PopupGlobalSearch.IsOpen Then Return
            If Not MenuCard.PopupAnimEnabled Then
                PopupGlobalSearch.IsOpen = False
                Return
            End If
            AniStop("GlobalSearch Popup")
            AniStart({
                AaOpacity(CardGlobalSearch, -CardGlobalSearch.Opacity, 80),
                AaDouble(Sub(i) TransformGlobalSearchScale.ScaleX += i, 0.96 - TransformGlobalSearchScale.ScaleX, 90),
                AaDouble(Sub(i) TransformGlobalSearchScale.ScaleY += i, 0.96 - TransformGlobalSearchScale.ScaleY, 90),
                AaCode(Sub() PopupGlobalSearch.IsOpen = False, , True)
            }, "GlobalSearch Popup")
        Catch ex As Exception
            Logger.Warn(ex, "关闭全局搜索失败")
        End Try
    End Sub

    Private Sub PopupGlobalSearch_Closed(sender As Object, e As EventArgs) Handles PopupGlobalSearch.Closed
        GlobalSearchClosedTick = Environment.TickCount
    End Sub

    Private Sub TxtGlobalSearch_TextChanged(sender As Object, e As System.Windows.Controls.TextChangedEventArgs) Handles TxtGlobalSearch.TextChanged
        RunGlobalSearch(TxtGlobalSearch.Text)
    End Sub

    Private Sub TxtGlobalSearch_KeyDown(sender As Object, e As KeyEventArgs) Handles TxtGlobalSearch.KeyDown
        If e.Key = Key.Enter AndAlso GlobalSearchActions.Count > 0 Then
            e.Handled = True
            Dim First As Action = GlobalSearchActions(0)
            CloseGlobalSearch()
            First.Invoke()
        End If
    End Sub

    ''' <summary>执行搜索：页面 / 实例 / 设置项 三节，各限量，行点击即跳转。</summary>
    Private Sub RunGlobalSearch(Query As String)
        PanGlobalSearch.Children.Clear()
        GlobalSearchActions.Clear()
        LabGlobalSearchEmpty.Visibility = Visibility.Collapsed
        Query = If(Query, "").Trim()
        If Query = "" Then Return
        Dim QueryLower = Query.ToLowerInvariant()
        Dim Total As Integer = 0
        '1) 页面
        For Each P In GlobalPageEntries()
            If Total >= 14 Then Exit For
            If P.Name.ToLowerInvariant().Contains(QueryLower) OrElse ModLang.L(P.Name).ToLowerInvariant().Contains(QueryLower) Then
                AddGlobalSearchRow(P.Icon, P.Name, "页面", P.Action)
                Total += 1
            End If
        Next
        '2) 实例（名称匹配，选中并回到版本选择页）
        Try
            If ModMinecraft.McInstanceList IsNot Nothing AndAlso Total < 14 Then
                Dim InstCount As Integer = 0
                For Each Pair In ModMinecraft.McInstanceList
                    For Each Inst In Pair.Value
                        If InstCount >= 6 OrElse Total >= 14 Then Exit For
                        If Inst.Name.ToLowerInvariant().Contains(QueryLower) Then
                            Dim Target = Inst
                            AddGlobalSearchRow("lucide/package", Target.Name, "实例",
                            Sub()
                                Try
                                    McInstanceSelected = Target
                                    WriteIni(McFolderSelected & "TCL.ini", "Version", Target.Name)
                                    FrmLaunchRight?.RefreshButtonsUI()
                                Catch ex As Exception
                                    Logger.Warn(ex, "全局搜索选中实例失败")
                                End Try
                                FrmMain.PageChange(FormMain.PageType.InstanceSelect)
                            End Sub)
                            InstCount += 1
                            Total += 1
                        End If
                    Next
                Next
            End If
        Catch ex As Exception
            Logger.Warn(ex, "全局搜索实例来源失败")
        End Try
        '3) 设置项（复用设置页搜索索引，跳转后滚动高亮）
        Try
            If FrmSetupLeft Is Nothing Then FrmSetupLeft = New PageSetupLeft
            Dim Entries = FrmSetupLeft.GetSearchEntriesForGlobal()
            Dim SetupCount As Integer = 0
            For Each Entry In Entries
                If SetupCount >= 8 OrElse Total >= 14 Then Exit For
                If Entry.Item2.Contains(QueryLower) Then
                    Dim PageId = Entry.Item3
                    Dim Title = Entry.Item1
                    AddGlobalSearchRow(Entry.Item5, Title, "设置 · " & ModLang.L(Entry.Item4), Sub() FrmSetupLeft.GlobalJumpToSetting(PageId, Title))
                    SetupCount += 1
                    Total += 1
                End If
            Next
        Catch ex As Exception
            Logger.Warn(ex, "全局搜索设置来源失败")
        End Try
        LabGlobalSearchEmpty.Visibility = If(Total = 0, Visibility.Visible, Visibility.Collapsed)
    End Sub

    Private Sub AddGlobalSearchRow(Icon As String, Title As String, Info As String, Action As Action)
        Dim Row As New MyListItem With {
            .Title = ModLang.L(Title), .Info = ModLang.L(Info), .Type = MyListItem.CheckType.Clickable,
            .Height = 34, .MinPaddingRight = 10, .IsScaleAnimationEnabled = False,
            .SvgIcon = Icon, .LogoScale = 0.85}
        AddHandler Row.Click, Sub(sender, e)
                                  CloseGlobalSearch()
                                  Action.Invoke()
                              End Sub
        PanGlobalSearch.Children.Add(Row)
        GlobalSearchActions.Add(Action)
    End Sub

    ''' <summary>主页面跳转表（名称 / 图标 / 动作）。</summary>
    Private Class GlobalPageEntry
        Public Name As String
        Public Icon As String
        Public Action As Action
    End Class

    Private Function GlobalPageEntries() As List(Of GlobalPageEntry)
        Dim L As New List(Of GlobalPageEntry)
        L.Add(New GlobalPageEntry With {.Name = "启动", .Icon = "lucide/play", .Action = Sub() FrmMain.PageChange(FormMain.PageType.Launch)})
        L.Add(New GlobalPageEntry With {.Name = "版本选择", .Icon = "lucide/package", .Action = Sub() FrmMain.PageChange(FormMain.PageType.InstanceSelect)})
        L.Add(New GlobalPageEntry With {.Name = "下载", .Icon = "lucide/download", .Action = Sub() FrmMain.PageChange(FormMain.PageType.Download)})
        L.Add(New GlobalPageEntry With {.Name = "联机", .Icon = "lucide/globe", .Action = Sub() FrmMain.PageChange(FormMain.PageType.Link)})
        L.Add(New GlobalPageEntry With {.Name = "任务管理", .Icon = "lucide/layout-list", .Action = Sub() FrmMain.PageChange(FormMain.PageType.TaskManager)})
        L.Add(New GlobalPageEntry With {.Name = "设置", .Icon = "lucide/settings", .Action = Sub() FrmMain.PageChange(FormMain.PageType.Setup)})
        L.Add(New GlobalPageEntry With {.Name = "更多", .Icon = "lucide/wrench", .Action = Sub() FrmMain.PageChange(FormMain.PageType.Other)})
        Return L
    End Function

#End Region

End Class
