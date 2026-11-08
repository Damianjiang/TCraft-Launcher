Imports System.Reflection
Imports System.Windows.Threading
Imports System.Threading.Tasks

Public Class Application

    '启动性能分析用的时间戳（仅日志用途）
    Friend Shared BootCtorTime As Date
    Friend Shared BootStartupTime As Date

    ''' <summary>
    ''' 命令行 --launch-instance "&lt;实例名&gt;" 指定的启动目标。
    ''' 由桌面快捷方式写入；启动器在首帧后解析并自动选中、自动启动，成功后清空。
    ''' 若启动器已在运行，单例检查会把已有窗口拉到前台并退出，不会重复开进程。
    ''' </summary>
    Public Shared StartupInstanceName As String = ""

    '开始
    Private Sub Application_Startup(sender As Object, e As StartupEventArgs) Handles Me.Startup
        BootStartupTime = Date.Now
        '最先在 UI 线程上锁定 ModBase 的 UiThreadId（见 InitUiThread 注释）：
        '必须在下方 Task.Run(StartupWarmup) 发车之前，否则预热线程抢先触发模块 cctor，
        'UiThreadId 锁错线程，RunInUi 在 UI 线程上恒为 False（「McLaunchStart 必须在 UI 线程调用」崩溃）。
        InitUiThread()
        '解析 --launch-instance "<名称>"：只取第一个，后面多余的忽略
        Try
            If e.Args IsNot Nothing Then
                For i As Integer = 0 To e.Args.Length - 1
                    If String.Equals(e.Args(i), "--launch-instance", StringComparison.OrdinalIgnoreCase) AndAlso i + 1 < e.Args.Length Then
                        StartupInstanceName = e.Args(i + 1)
                        Exit For
                    End If
                Next
            End If
        Catch ex As Exception
            '参数解析失败不影响正常启动
        End Try
        Try
            '单例检查提前到 TClKit 初始化之前：第二实例完全不必白花
            'gz 解压 + 库初始化（约 450ms）就能退出；异常路径所需的最小能力（Win32/Beep）不依赖任何库
            If Not (e.Args.Contains("--gpu") OrElse e.Args.Contains("--memory")) Then '若是通过命令行调用，则不检查单例、不轮换日志
                WaitingMutex(e)
            End If
            '核心初始化
            TClKit.Main.Init("TCL")
            'TClKit.Wpf.Main.Init 移到 FormMain 构造前（审计确认其全部工作是
            'ModernTooltipService 的 DP 注册与 7 个类处理器注册，5-30ms，而 Tooltip 最早也只会在
            '窗口存在后出现——首帧前无窗口，天然满足时序约束；App 构造函数的注释约束不变）
            If Not (e.Args.Contains("--gpu") OrElse e.Args.Contains("--memory")) Then
                Logger.Instance = New TclLogger With {.logFolder = Paths.BaseThenName, .MinLevel = If(ModeDebug, LogLevel.Trace, LogLevel.Info)}
            End If
            '后台线程异常兜底：DispatcherUnhandledException 只覆盖 UI 线程，.NET Framework 下
            '任意后台线程的未处理异常会直接静默杀掉整个进程（无报错窗口、日志戛然而止）。
            '「取消启动闪退」正是这种形态——取消链路上某个后台线程抛了异常，进程无声死亡，
            '此前连崩溃原因都无法留下。这里至少把异常与完整堆栈写进日志，让闪退可被诊断。
            AddHandler AppDomain.CurrentDomain.UnhandledException,
                Sub(snd As Object, evt As UnhandledExceptionEventArgs)
                    Try
                        Dim Ex = TryCast(evt.ExceptionObject, Exception)
                        Logger.Error(If(Ex IsNot Nothing, Ex, New Exception(If(evt.ExceptionObject?.ToString(), "未知异常对象"))),
                                     $"后台线程发生未处理异常（{(If(evt.IsTerminating, "进程即将退出", "已拦截，进程继续运行"))}）")
                    Catch
                        '日志系统自身不可用时的保底：直接落一份崩溃现场文件
                        Try
                            IO.File.WriteAllText(IO.Path.Combine(IO.Path.GetTempPath(), "TCL\CrashLast.txt"), evt.ExceptionObject?.ToString())
                        Catch
                        End Try
                    End Try
                End Sub
            AddHandler TaskScheduler.UnobservedTaskException,
                Sub(snd As Object, evt As UnobservedTaskExceptionEventArgs)
                    '必须先 SetObserved 再记日志：若记日志过程中抛异常，SetObserved 不执行，
                    '.NET 照样在终结器线程上把异常抛出来（用户实测仍弹未观察异常窗口）。
                    evt.SetObserved() '阻止 .NET 在最终化时因未观察任务异常终止进程
                    Try
                        '从堆栈里提取最顶层业务代码帧（跳过 System. 系统库帧），标注触发模块，方便精准定位
                        Dim OriginModule As String = "未知模块"
                        Try
                            Dim FullEx As Exception = evt.Exception
                            Do While FullEx?.InnerException IsNot Nothing
                                FullEx = FullEx.InnerException
                            Loop
                            If FullEx?.StackTrace IsNot Nothing Then
                                For Each Line In FullEx.StackTrace.Split(vbCrLf)
                                    Dim Trimmed = Line.TrimStart()
                                    '跳过系统库帧（System. / Microsoft. / mscorlib 等），找第一个业务代码帧
                                    If Trimmed.StartsWithF("at ") AndAlso
                                       Not Trimmed.StartsWithF("at System.") AndAlso
                                       Not Trimmed.StartsWithF("at Microsoft.") AndAlso
                                       Not Trimmed.StartsWithF("at MS.Win32.") AndAlso
                                       Not Trimmed.StartsWithF("at MS.Internal.") Then
                                        OriginModule = Trimmed.Substring(3)
                                        Exit For
                                    End If
                                Next
                            End If
                        Catch
                        End Try
                        'WinHTTP 连接意外重置（12030）这类网络抖动属常见情况，降级为 Warn 避免 Error 级刷屏
                        'WinHttpException 是 Friend 级别，外面访问不到类型，用消息特征判断
                        Dim RootEx As Exception = evt.Exception
                        Do While RootEx?.InnerException IsNot Nothing
                            RootEx = RootEx.InnerException
                        Loop
                        Dim RootMsg As String = If(RootEx?.Message, If(evt.Exception?.Message, "未知错误"))
                        Dim IsConnectionReset = RootMsg.Contains("WINHTTP_CALLBACK_STATUS_REQUEST_ERROR") OrElse
                                                RootMsg.Contains("连接意外终止") OrElse
                                                RootMsg.Contains("12030")
                        If IsConnectionReset Then
                            Logger.Warn(evt.Exception, $"后台网络连接意外重置（{OriginModule}）：{RootMsg}")
                        Else
                            Logger.Error(evt.Exception, $"后台任务存在未观察的异常（{OriginModule}）：{RootMsg}")
                        End If
                    Catch
                    End Try
                End Sub
            '清理环境变量里仅大小写不同的重复项（HTTP_PROXY / http_proxy 这种）：
            '不清理的话，任何 ProcessStartInfo.EnvironmentVariables 访问都会抛「已添加项」，
            '启动游戏、安装 OptiFine 都会直接失败（见 ModBase.EnvironmentSanitize 注释）。
            '移入后台预热：首个此类访问在游戏启动（远晚于首帧），无需占用 UI 线程。
            '启动性能：进程→App 构造→Startup 入口（含 Application.xaml 的 BAML 解析）
            Try
                Dim ProcStart As Date = Diagnostics.Process.GetCurrentProcess().StartTime
                Logger.Info($"[启动耗时] 进程→App 构造：{CLng((BootCtorTime - ProcStart).TotalMilliseconds)} ms")
                Logger.Info($"[启动耗时] App 构造→Startup（Application.xaml 解析）：{CLng((BootStartupTime - BootCtorTime).TotalMilliseconds)} ms")
                Logger.Info($"[启动耗时] Startup 入口→日志就绪：{CLng((Date.Now - BootStartupTime).TotalMilliseconds)} ms")
            Catch
                '仅日志
            End Try
            Dim Seg = GetTimeMs() '启动性能分段计时（仅日志）
            '启动提速——后台预热池：
            '把「与首帧渲染无数据依赖」的重初始化移出 UI 线程，由后台任务顺序完成（StartupWarmup）：
            'ModLang 词典构建(~97ms) / HTTP 代理初始化 / .NET 版本检查 /（ServicePointManager 已上移到同步段）
            '渲染模式注册表 / 环境变量清理 / 默认背景释放。UI 线程只在真正需要结果处等待
            '（FormMain_Loaded / RunDeferredStartupWork / 初始化加载池），彼时预热早已完成。
            StartupWarmupTask = Task.Run(New Action(AddressOf StartupWarmup))
            '启动画面已改用 SplashScreen 构建动作（vbproj）：由 WPF 生成的 Main 在进程最早期显示，
            '比手动 New SplashScreen 提前约 1.2-2.4 秒（覆盖 CLR/WPF 初始化的空白期）。
            '首次窗口渲染后自动淡出关闭；FrmStart 字段保留（恒为 Nothing，相关代码均有空值防护）。
            '主线程（WPF Dispatcher）保持 Normal 优先级。
            '此前设为 Highest 会持续抢占 CPU，把所有后台 Loader / 文件扫描 / 下载 / Java 检测线程
            '（均为 Normal/AboveNormal）反复压制饿死，表现为启动页一直"正在加载"、整体打开极慢、
            '后台任务体感"线程卡死"。WPF Dispatcher 默认即为 Normal，无需提权；恢复默认即可。
            Thread.CurrentThread.Priority = ThreadPriority.Normal
            '执行开发版测试
            If BuildType = BuildTypes.Debug Then
                Try
                    ModDevelop.Start()
                Catch ex As Exception
                    Logger.Error(ex, "开发者模式测试出错", LogBehavior.Alert)
                End Try
            End If
            LogStartupSegment("TClKit/互斥/日志", Seg)
            '应用界面语言：只设置线程文化（几 ms）。词典构建已移入后台预热；整套语言应用
            '（套界面文案/活翻译注册）在首帧后的 RunDeferredStartupWork 中执行——FrmMain 未创建前
            'ApplyToWindow 本来就是空转，非简中用户的首屏翻译时机与原先完全一致（首帧后补套）。
            Try
                ModBase.ApplyThreadCultureOnly(Settings.Get(Of Integer)("ToolLanguage"))
            Catch ex As Exception
                Logger.Warn(ex, "应用界面语言文化失败（保持当前文化）")
            End Try
            LogStartupSegment("界面语言文化（词典后台预热中）", Seg)
            '修复 WPF 字体加载。
            '原先的做法是无条件构造一次 FormattedText 去「试」是否抛 UriFormatException，
            '但参数里的 Fonts.SystemTypefaces 会把系统里所有字体族×4 全部枚举一遍，
            '实测每次启动白花 150~190ms。这里改为先做 O(1) 的环境变量检查：
            '根因就是 windir 缺失/无效导致 WPF 取字体路径失败，直接查它即可，
            '只有确实异常时才去改环境变量（不再做昂贵的字体枚举探测）。
            Try
                Dim WindirValue As String = Environment.GetEnvironmentVariable("windir")
                If String.IsNullOrEmpty(WindirValue) OrElse Not IO.Directory.Exists(WindirValue) Then
                    Environment.SetEnvironmentVariable("windir", Environment.GetEnvironmentVariable("SystemRoot"), EnvironmentVariableTarget.User)
                    Logger.Warn($"windir 环境变量异常（{If(WindirValue, "空")}），已按 SystemRoot 修复")
                End If
            Catch ex As Exception
                Logger.Warn(ex, "检查/修复 windir 环境变量失败")
            End Try
            LogStartupSegment("字体枚举修复", Seg)
            '检查参数调用
            If e.Args.Length > 0 Then
                If e.Args(0) = "--gpu" AndAlso e.Args.Length > 1 Then
                    '调整显卡设置
                    Try
                        SetGPUPreference(e.Args(1).Trim(""""))
                        Environment.Exit(ProcessReturnValues.TaskDone)
                    Catch ex As Exception
                        Environment.Exit(ProcessReturnValues.Fail)
                    End Try
                ElseIf e.Args(0) = "--memory" Then
                    '内存优化
                    Dim Ram = My.Computer.Info.AvailablePhysicalMemory
                    Try
                        PageOtherTest.MemoryOptimizeInternal(False)
                    Catch ex As Exception
                        MsgBox(ex.Message, MsgBoxStyle.Critical, ModLang.LSafe("内存优化失败"))
                        Environment.Exit(-1)
                    End Try
                    If My.Computer.Info.AvailablePhysicalMemory < Ram Then '避免 ULong 相减出现负数
                        Environment.Exit(0)
                    Else
                        Environment.Exit((My.Computer.Info.AvailablePhysicalMemory - Ram) / 1024) '返回清理的内存量（K）
                    End If
                End If
            End If
            LogStartupSegment("命令行参数检查", Seg)
            '初始化文件结构
            Try
                DirectoryUtils.Create(Paths.Base & "TCL\Pictures\")
                DirectoryUtils.Create(Paths.Base & "TCL\Musics\")
                CheckPermissionWithException(Paths.Base & "TCL\")
            Catch ex As Exception
                MsgBox(ModLang.LSafe("TCL 没有对当前文件夹的权限（") & Paths.Base & "TCL\" & ModLang.LSafe("），请尝试：") & vbCrLf &
                  ModLang.LSafe("1. 将 TCL 移动到其他文件夹") & If(PathUtils.GetDiskName(Paths.Base) = "C"c, ModLang.LSafe("，例如 C 盘和桌面以外的其他位置。"), ModLang.LSafe("。")) & vbCrLf &
                  ModLang.LSafe("2. 删除当前目录中的 TCL 文件夹，然后再试。") & vbCrLf &
                  ModLang.LSafe("3. 右键 TCL 选择属性，打开 兼容性 中的 以管理员身份运行此程序。"),
                MsgBoxStyle.Critical, ModLang.LSafe("运行环境错误"))
                Environment.[Exit](ProcessReturnValues.Cancel)
            End Try
            '默认背景图片释放已移入后台预热（StartupWarmup）；背景图真正被读取是在
            'FormMain_Loaded 的 UpdateBackgroundAndTitleBar（彼时预热已完成并已等待）
            LogStartupSegment("目录结构", Seg)
RetryCacheCheck:
            Try
                DirectoryUtils.Create(PathTemp)
                CheckPermissionWithException(PathTemp)
            Catch ex As Exception
                If PathUtils.IsSame(PathTemp, Path.GetTempPath() & "TCL\") Then
                    MyMsgBox(ModLang.L("TCL 无法访问缓存文件夹，可能导致程序出错或无法正常使用！") & vbCrLf & vbCrLf & ModLang.L("错误原因：") & ex.GetDisplay(True), "缓存文件夹不可用")
                Else
                    MyMsgBox(ModLang.L("手动设置的缓存文件夹不可用，TCL 将使用默认缓存文件夹。") & vbCrLf & vbCrLf & ModLang.L("错误原因：") & ex.GetDisplay(True), "缓存文件夹不可用")
                    Settings.Set("SystemSystemCache", "")
                    PathTemp = Path.GetTempPath() & "TCL\"
                    GoTo RetryCacheCheck
                End If
            End Try
            DirectoryUtils.Create(PathTemp & "Cache\")
            DirectoryUtils.Create(Paths.AppDataThenName)
            LogStartupSegment("缓存目录校验", Seg)
            '设置 ToolTipService 默认值（几 ms，保留 UI 线程：必须在任何 Tooltip 展示前生效）
            ToolTipService.InitialShowDelayProperty.OverrideMetadata(GetType(DependencyObject), New FrameworkPropertyMetadata(100))
            'HTTP 代理初始化已移入后台预热（StartupWarmup）；ServicePointManager 在 Startup 同步段（2026-11-07 起）——
            '首个网络请求在首帧后的加载池线程上，彼时预热早已完成（初始化线程会先等待预热结束）
            LogStartupSegment("网络/工具提示默认值", Seg)
            '写入 WPF 渲染模式注册表已移入后台预热（该值重启才生效，当前进程不读它）
            '（启动画面已提前到日志就绪后立即显示，见上）
            LogStartupSegment("创建启动画面", Seg)
            '基础信息日志已移入后台预热（StartupWarmup，）：这 5 行里
            '「系统编码」行会触碰 Encoding.Default.HeaderName（物化 ANSI 编码表，实测数十毫秒），
            'Identify/HasAdminRole 也有可观的注册表与 CPU 开销，且均不在首帧渲染依赖上。
            '检测异常环境（依赖日志已就绪的弹窗判断）仍留在 UI 线程：
            If Paths.Base.Contains(Path.GetTempPath()) OrElse Paths.Base.Contains("AppData\Local\Temp\") Then
                MyMsgBox(ModLang.L("请将 TCL 从压缩包中解压后再使用！") & vbCrLf & ModLang.L("如果不会解压，可以在网上寻找教程。"), "需要解压！", "我知道了", IsWarn:=True, ForceWait:=True)
                FormMain.EndProgramForce(ProcessReturnValues.Cancel)
            End If
            If Not Environment.Is64BitOperatingSystem Then
                MyMsgBox(ModLang.L("TCL 和新版 Minecraft 均不再支持 32 位系统，请重装为 64 位系统后再进行游戏！"), "环境警告", "我知道了", IsWarn:=True, ForceWait:=True)
                FormMain.EndProgramForce(ProcessReturnValues.Cancel)
            End If
            'Windows 7 兼容：.NET Framework 4.8 只能装在 Win7 SP1（6.1.7601+）上，
            'RTM（7600）装不上，急救安装器会永远失败，这里提前拦截并给出明确指引。
            '在 Win8+ 系统上此分支恒不触发；先于单例互斥后的所有初始化执行。
            Try
                Dim Win7Os = Environment.OSVersion
                If Win7Os.Version.Major = 6 AndAlso Win7Os.Version.Minor = 1 AndAlso Win7Os.Version.Build < 7601 Then
                    MyMsgBox(ModLang.L("检测到 Windows 7 尚未安装 Service Pack 1！") & vbCrLf &
                             ModLang.L("TCraft Launcher 需要 Windows 7 SP1 及更高版本（同时要求 64 位系统与 .NET Framework 4.8）。") & vbCrLf &
                             ModLang.L("请先通过 Windows Update 或微软官网安装 SP1，然后再运行本程序。"), "系统版本过低", "我知道了", IsWarn:=True, ForceWait:=True)
                    FormMain.EndProgramForce(ProcessReturnValues.Cancel)
                End If
            Catch ex As Exception
                Logger.Warn(ex, "Win7 SP1 检查失败（跳过）")
            End Try
            LogStartupSegment("基础信息+环境检测", Seg)
            '计时
            Logger.Info($"第一阶段加载用时：{GetTimeMs() - ApplicationStartTick} ms")
            ApplicationStartTick = GetTimeMs()
            'ServicePointManager 全局网络默认值（同步段，必须在预热线程与任何网络请求之前——
            'Win7 的系统默认协议栈不含 TLS 1.2，首发请求竞态输掉就会 TLS 握手失败）
            ServicePointManager.Expect100Continue = False
            ServicePointManager.DefaultConnectionLimit = 10000
            ServicePointManager.UseNagleAlgorithm = False
            ServicePointManager.EnableDnsRoundRobin = True
            ServicePointManager.ReusePort = True
            If Environment.OSVersion.Version < New Version(6, 2) Then 'Windows 7 兼容：显式启用 TLS 1.2 等
                ServicePointManager.SecurityProtocol = SecurityProtocolType.Ssl3 Or SecurityProtocolType.Tls Or SecurityProtocolType.Tls11 Or SecurityProtocolType.Tls12
            End If
            '证书兜底回调（全局）：OS 验证失败（Win7 根库冻结）时用内置 CA 束重验——见 ModProxy.ValidateServerCertificate。
            '必须挂全局 ServicePointManager 回调：handler 级回调在 .NET Framework 上不保证触发（实测 Win7 从不触发）。
            ServicePointManager.ServerCertificateValidationCallback = AddressOf ModProxy.ValidateServerCertificate
            AniControlEnabled += 1
        Catch ex As Exception
            Dim FilePath As String = Nothing
            Try
                FilePath = PathExe
            Catch ex2 As Exception
                Logger.Warn(ex2, "获取程序路径失败（错误提示将无法显示路径）")
            End Try
            MsgBox(ex.GetDisplay(True) & vbCrLf & ModLang.LSafe("TCL 所在路径：") & If(String.IsNullOrEmpty(FilePath), ModLang.LSafe("获取失败"), FilePath), MsgBoxStyle.Critical, ModLang.LSafe("TCL 初始化错误"))
            FormMain.EndProgramForce(ProcessReturnValues.Exception)
        End Try
    End Sub
    ''' <summary>
    ''' 后台预热任务句柄（Application_Startup 创建）。
    ''' </summary>
    Private Shared StartupWarmupTask As Task

    '延迟 UI 资源是否已合并
    Private Shared DelayedUiLoaded As Boolean = False

    ''' <summary>
    ''' 合并延迟 UI 资源字典（Resources\DelayedUi.xaml：转换器/Label/滚动条/输入框/下拉框/右键菜单）。
    ''' 这些资源原先随 Application.xaml 在 Startup 前同步解析（实测占 BAML 段的主要部分），
    ''' 而首帧可视树（FormMain 自身）根本用不到——全部消费方都在页面/弹窗里。
    ''' 改为首帧后、任何页面构造前合并；所有可能构造内容的入口都必须先调用本方法：
    ''' EnsureLaunchLeftCreated / EnsureLaunchRightCreated / PageChangeActual / MyMsgBox / Hint / RunDeferredStartupWork。
    ''' 幂等；必须在 UI 线程调用（操作 Application.Resources）。
    ''' </summary>
    Public Shared Sub EnsureDelayedUiLoaded()
        If DelayedUiLoaded Then Return
        If Application.Current Is Nothing Then Return
        DelayedUiLoaded = True
        Try
            Application.Current.Resources.MergedDictionaries.Add(
                New ResourceDictionary With {.Source = New Uri("pack://application:,,,/Resources/DelayedUi.xaml")})
            Logger.Info("延迟 UI 资源已合并（页面构造前）")
        Catch ex As Exception
            DelayedUiLoaded = False
            Logger.Warn(ex, "合并延迟 UI 资源失败（界面样式将缺失，请反馈）")
        End Try
    End Sub

    ''' <summary>
    ''' 等待启动后台预热（词典/网络默认值/代理/默认背景释放等）完成。
    ''' 预热已完成时近零开销；未完成时阻塞至多数百毫秒（等价于这些工作原本就在 UI 线程同步执行）。
    ''' </summary>
    Public Shared Sub WaitStartupWarmup()
        Try
            If StartupWarmupTask IsNot Nothing Then StartupWarmupTask.Wait()
        Catch ex As Exception
            Try
                Logger.Warn(ex, "等待启动预热完成失败")
            Catch
            End Try
        End Try
    End Sub

    ''' <summary>
    ''' 启动后台预热：把与首帧渲染无数据依赖的重初始化移出 UI 线程。
    ''' 顺序执行；每段独立 Try/Catch，任何一段失败不影响其余段与 UI 线程。
    ''' </summary>
    Private Shared Sub StartupWarmup()
        Dim Seg As Long = 0
        Try
            Seg = GetTimeMs()
            'ModLang 词典构建（~85-97ms 纯 CPU）：预热后任何线程首次触碰 ModLang 均零等待
            ModLang.Warmup()
            LogStartupSegment("预热:ModLang 词典", Seg)
            '（ServicePointManager 全局网络默认值已上移到 Startup 同步段：预热是后台线程，
            '  与首个网络请求存在竞态——Win7 上竞态输掉的首发请求会用 Ssl3|Tls 老协议栈，
            '  TLS 1.2-only 的 CDN 全挂，表现为图标/图片不显示）
            '初始化全局 HTTP 代理
            WebRequest.DefaultWebProxy = HttpProxyManager.Instance
            ModNet.UpdateHttpProxy()
            LogStartupSegment("预热:网络默认值+HTTP 代理", Seg)
            '检查 .NET Framework 版本（缺失时的急救弹窗由后台线程弹出，仅影响弹窗时机）
            If CInt(RegistryUtils.TryRead("HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full", "Release", 999999)) < 528040 Then OldEnvironmentAssert()
            '写入 WPF 渲染模式设置（重启生效，当前进程不读它）
            FormMain.ApplyHardwareAcceleration(False)
            '基础信息日志：VersionDisplay/Identify/PathExe/编码表/HasAdminRole
            Logger.Info($"程序版本：{VersionDisplay} ({VersionCode}{If(CommitHash = "", "", $"，#{CommitHash}")})")
            If BuildType = BuildTypes.Snapshot Then
                Logger.Info($"识别码：{Identify}{If(ThemeCheckOne(9), "，已解锁反馈主题", "，未解锁反馈主题")}")
            Else
                Logger.Info($"识别码：{Identify}")
            End If
            Logger.Info($"程序路径：{PathExe}")
            Logger.Info($"系统编码：{Encoding.Default.HeaderName} ({Encoding.Default.CodePage}, GBK={IsGBKEncoding})")
            Logger.Info($"管理员权限：{WindowsUtils.HasAdminRole()}")
            LogStartupSegment("预热:.NET 检查+渲染注册表+基础信息", Seg)
            '清理环境变量里仅大小写不同的重复项（首个 ProcessStartInfo 访问在游戏启动，远晚于首帧）
            EnvironmentSanitize()
            '写入默认背景图片（真正被读取在 FormMain_Loaded 的 UpdateBackgroundAndTitleBar）。
            '背景由 1920×1080 的 JPEG（旧 TCraft.png，196.7KB）改为 WebP q85（94KB），
            '加载端 MyBitmap 按 RIFF 文件头自动识别 WebP，与扩展名无关（MyBitmap.vb:141）。
            If Not DirectoryUtils.EnumerateFiles(Paths.Base & "TCL\Pictures\").Any() Then
                ExtractResources(Paths.Base & "TCL\Pictures\TCraft.webp", "TCraft")
            End If
            'WebP 解码链预热：把 libwebp64.dll 释放与 Imazen.WebP 程序集加载放到后台，
            '避免 FormMain_Loaded 的背景加载路径多花数十毫秒
            Try
                ExtractLibwebp()
                Assembly.Load("Imazen.WebP")
            Catch ex As Exception
                Try : Logger.Warn(ex, "预热 WebP 解码链失败") : Catch : End Try
            End Try
            LogStartupSegment("预热:环境清理+默认背景", Seg)
        Catch ex As Exception
            Try
                Logger.Warn(ex, "启动后台预热失败")
            Catch
            End Try
        End Try
    End Sub

    ''' <summary>
    ''' 要求程序以单例运行。
    ''' 如果已在运行，则将其窗口拖出来并播放提示音，然后结束当前进程。
    ''' </summary>
    Private Shared Sub WaitingMutex(e As StartupEventArgs)
        If BuildType = BuildTypes.Debug Then Return
        Try
            Dim MutexCreatedNew As Boolean
            TclMutex = New Mutex(True, "TCL_SingletonMutex", MutexCreatedNew)
            If MutexCreatedNew Then Return
            Logger.Warn("已有一个 TCL 实例正在运行")
            '等待已有的 TCL 退出
            If e.Args.Length > 0 AndAlso e.Args(0) = "--wait" Then
                Try
                    If TclMutex.WaitOne(10000) Then Return '等待至多 10 秒
                Catch Ignored As AbandonedMutexException
                    Return '已有实例异常退出，继续启动
                End Try
            End If
            '将已有的 TCL 窗口拖出来
            Dim WindowHwnd As IntPtr = FindWindow(Nothing, "TCL　")
            If WindowHwnd = IntPtr.Zero Then WindowHwnd = FindWindow(Nothing, "TCL　")
            If WindowHwnd = IntPtr.Zero Then WindowHwnd = FindWindow(Nothing, "TCL　")
            If WindowHwnd <> IntPtr.Zero Then ShowWindowToTop(WindowHwnd)
            '播放提示音并退出
            Beep()
            Environment.[Exit](ProcessReturnValues.Cancel)
        Catch ex As Exception
            Logger.Warn(ex, "单例检查失败")
        End Try
    End Sub
    Private Shared TclMutex As Mutex

    '结束
    Private Sub Application_SessionEnding(sender As Object, e As SessionEndingCancelEventArgs) Handles Me.SessionEnding
        'Windows 关机 / 注销：系统随时会强杀进程，EndProgram 里的关闭动画、联机退出判断都可能来不及走完
        '（TryExit 甚至会直接 Return）。先把「正常退出」标记落盘，避免下次开机误报「异常退出」
        Try
            Settings.Set("SystemLastExitClean", True)
        Catch ex As Exception
            Logger.Warn(ex, "SessionEnding 写入退出标记失败")
        End Try
        FrmMain?.EndProgram(False)
    End Sub

    '异常
    Private Sub Application_DispatcherUnhandledException(sender As Object, e As DispatcherUnhandledExceptionEventArgs) Handles Me.DispatcherUnhandledException
        '触发页面的 Dispatcher
        If FrmMain?.PageLeft IsNot Nothing AndAlso TypeOf FrmMain.PageLeft Is IDispatcherUnhandledException Then
            Try
                CType(FrmMain.PageLeft, IDispatcherUnhandledException).DispatcherUnhandledException(sender, e)
            Catch ex As Exception
                Logger.Warn(ex, "PageLeft DispatcherUnhandledException 失败")
            End Try
            If e.Handled Then Return
        End If
        If FrmMain?.PageRight IsNot Nothing AndAlso TypeOf FrmMain.PageRight Is IDispatcherUnhandledException Then
            Try
                CType(FrmMain.PageRight, IDispatcherUnhandledException).DispatcherUnhandledException(sender, e)
            Catch ex As Exception
                Logger.Warn(ex, "PageRight DispatcherUnhandledException 失败")
            End Try
            If e.Handled Then Return
        End If
        '正常处理
        e.Handled = True
        If IsProgramEnding Then Return
        Try
            FeedbackInfo()
        Catch ex As Exception
            Logger.Warn(ex, "FeedbackInfo 失败")
        End Try
        Dim Detail As String = e.Exception.GetDisplay(True)
        If Detail.Contains("System.Windows.Threading.Dispatcher.Invoke") OrElse Detail.Contains("MS.Internal.AppModel.ITaskbarList.HrInit") OrElse Detail.Contains("未能加载文件或程序集") OrElse
           Detail.Contains(".NET Framework") Then ' “自动错误判断” 的结果分析
            OldEnvironmentAssert(e.Exception)
        Else
            '未处理的 UI 线程异常仅记录日志，不强制退出程序。
            '此前使用 AlertThenCrash，一次动画强转异常（MyListItem.RefreshColor）就会直接退出整个启动器。
            Logger.Error(e.Exception, "程序出现未知错误")
        End If
    End Sub
    '检测到电脑缺少 .NET Framework 4.8：TCL 急救版将自动下载并安装它，无需用户自行配置
    Private Shared Sub OldEnvironmentAssert(Optional ex As Exception = Nothing)
        Const InstallerUrl As String = "https://go.microsoft.com/fwlink/?LinkId=2085155" '官方 .NET Framework 4.8 联网安装包
        Dim InstallPath As String = Path.GetTempPath() & "TCL\ndp48-web.exe"
        Try
            '询问是否自动安装
            If MsgBox(ModLang.LSafe("检测到电脑缺少 .NET Framework 4.8 运行库！") & vbCrLf & vbCrLf &
                      ModLang.LSafe("TCL 急救版可以自动下载并安装 .NET Framework 4.8。") & vbCrLf &
                      ModLang.LSafe("点击 [是] 将自动下载官方安装包并启动安装，期间可能弹出 UAC 确认框。") & vbCrLf &
                      ModLang.LSafe("点击 [否] 则改为打开下载页，由你手动下载安装。"), MsgBoxStyle.Question + MsgBoxStyle.YesNo, ModLang.LSafe("缺少 .NET Framework 4.8")) = MsgBoxResult.No Then
                OpenWebsite("https://dotnet.microsoft.com/zh-cn/download/dotnet-framework/thank-you/net48-offline-installer")
                Environment.Exit(ProcessReturnValues.Cancel)
            Else
                '自动下载安装包
                If Not FileUtils.Exists(InstallPath) Then
                    Logger.Info("开始自动下载 .NET Framework 4.8 安装包……")
                    DirectoryUtils.Create(InstallPath.BeforeLast("\"))
                    NetDownloadByClient(InstallerUrl, InstallPath, SimulateBrowserHeaders:=True)
                    Logger.Info(".NET Framework 4.8 安装包下载完成")
                End If
                '启动安装，等待其结束后退出本程序
                Dim InstallProcess = StartProcess(InstallPath)
                If Not InstallProcess.WaitForExit(Integer.MaxValue) Then InstallProcess.Kill()
                Environment.Exit(ProcessReturnValues.Cancel)
            End If
        Catch exp As Exception
            '任何失败都退回打开官网下载页
            Logger.Error(exp, "自动安装 .NET Framework 4.8 失败")
            OpenWebsite("https://dotnet.microsoft.com/zh-cn/download/dotnet-framework/thank-you/net48-offline-installer")
            If ex Is Nothing Then
                Logger.Error(ModLang.LSafe("你的 .NET Framework 版本过低或损坏，请下载并重新安装 .NET Framework 4.8！") & vbCrLf & ModLang.LSafe("若安装失败，可以先卸载高版本的 .NET Framework 然后再试。"), LogBehavior.AlertThenCrash)
            Else
                Logger.Error(ex, ModLang.LSafe("你的 .NET Framework 版本过低或损坏，请下载并重新安装 .NET Framework 4.8！") & vbCrLf & ModLang.LSafe("若安装失败，可以先卸载高版本的 .NET Framework 然后再试。"), LogBehavior.AlertThenCrash)
            End If
        End Try
    End Sub

    '动态 DLL 加载
    Private Sub New() '这里必须尽早调用，且不能使用任何库，否则加载 TClKit 就会导致崩溃
        BootCtorTime = Date.Now
        '多核 JIT（ProfileOptimization）：首次启动把 JIT 顺序记入档案，之后每次启动由后台线程
        '并行预编译这些程序集，显著缩短「进程→窗口可见」之间约 3.6 秒的 JIT 占比（.NET Framework 的标准加速手段）
        Try
            Dim ProfileRoot = IO.Path.Combine(IO.Path.GetTempPath(), "TCL-JIT")
            IO.Directory.CreateDirectory(ProfileRoot)
            System.Runtime.ProfileOptimization.SetProfileRoot(ProfileRoot)
            System.Runtime.ProfileOptimization.StartProfile("Startup.profile")
        Catch
        End Try
        EnableUnsafeHeaderParsing()
        Static Prefixes As String() = {"Newtonsoft.Json", "Imazen.WebP", "Microsoft.Win32.Registry", "System.Net.Http.WinHttpHandler",
                                       "System.Buffers", "System.Memory", "System.Numerics.Vectors", "System.Runtime.CompilerServices.Unsafe",
                                       "TClCS", "TClKit.Wpf", "TClKit"}
        Static LoadedAssemblies As New ConcurrentDictionary(Of String, Lazy(Of Assembly))(StringComparer.Ordinal) '缓存
        AddHandler AppDomain.CurrentDomain.AssemblyResolve,
        Function(sender As Object, Args As ResolveEventArgs) As Assembly
            '只按完整名匹配（前缀后必须紧跟逗号）。卫星资源探测的请求名形如
            '"System.Net.Http.WinHttpHandler.resources, Culture=zh-CN, ..."，旧写法裸 StartsWith 会
            '把探测请求误匹配进来、拿中性主程序集冒充 zh-CN 卫星 → 运行时因文化不匹配硬抛
            'FileNotFoundException（System.SR 只捕获 MissingManifestResourceException 拦不住它），
            '每次网络请求失败/取消都变成未观察异常刷屏。改后探测请求被跳过、走优雅回退链
            '（zh-CN→zh-CHS→zh-Hans→zh→中性），最终由 SR 内置英文默认文案兜底，零异常。
            Dim Prefix As String = Prefixes.FirstOrDefault(Function(p) Args.Name.StartsWith(p & ",", StringComparison.Ordinal) OrElse Args.Name = p)
            If Prefix Is Nothing Then Return Nothing
            Dim LazyAssembly = LoadedAssemblies.GetOrAdd(Prefix, Function(p) New Lazy(Of Assembly)(
            Function()
                Dim LoadedAssembly = LoadEmbeddedAssembly(p)
                If p = "Imazen.WebP" Then ExtractLibwebp() 'WebP 特判
                Return LoadedAssembly
            End Function, LazyThreadSafetyMode.ExecutionAndPublication))
            Return LazyAssembly.Value
        End Function
    End Sub

    ''' <summary>
    ''' 从内嵌 gz 资源加载程序集：解压 → 落盘缓存 → Assembly.LoadFrom。
    ''' 原先直接 Assembly.Load(字节)。内存程序集被多核 JIT（ProfileOptimization）明确排除，
    ''' 改为落盘 + LoadFrom 后即可进入预编译范围，且非首次启动直接读缓存、免重复解压。
    ''' 缓存放 %AppData%\TCL\Cache\Assemblies（此处只能用 BCL：AssemblyResolve 最早期，ModBase/Paths 尚不可用）。
    ''' 文件名带内容指纹（长度+FNV-1a）：exe 更新后 DLL 内容变化 → 文件名变化 → 自动重建缓存。
    ''' 落盘/加载任一步失败都回退 Assembly.Load(字节)，保证行为与原先完全一致。
    ''' </summary>
    ''' <remarks>
    ''' **绝对禁止在本函数内调用 Logger/任何会触发其他程序集解析的 API**：
    ''' 本函数运行在 AssemblyResolve 处理器内、持有当前前缀的 Lazy 锁——此时触碰 TCl 侧类型会递归
    ''' 解析其他程序集、最终再次请求本前缀，同线程重入 ExecutionAndPublication 的 Lazy 直接死锁
    ''' （症状：启动器进程静默挂死，连 Log1.txt 都不会创建）。诊断信息只允许写入文件。
    ''' </remarks>
    Private Shared Function LoadEmbeddedAssembly(Prefix As String) As Assembly
        Dim Raw = ModGzip.Unwrap(DirectCast(My.Resources.ResourceManager.GetObject(Prefix), Byte())) '嵌入资源可能为 gzip（单文件瘦身），透明解压
        Try
            Dim Dir = IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "TCL", "Cache", "Assemblies")
            IO.Directory.CreateDirectory(Dir)
            'FNV-1a 内容指纹。VB 的 UInteger 乘法默认带溢出检查（与 C# 不同，无
            '/removeintchecks 时溢出直接抛 OverflowException），原先的 UInteger 版本每次都在这里炸、
            '被外层 Catch 吞掉后回退内存加载——缓存自 r111 起从未真正生效。改用 ULong 掩码运算：
            '2^32 × 16777619 < 2^64 不可能溢出，结果与 32 位 FNV 完全一致。
            Dim Hash As ULong = 2166136261UL
            For Each B As Byte In Raw
                Hash = ((Hash Xor B) * 16777619UL) And &HFFFFFFFFUL
            Next
            Dim AsmPath = IO.Path.Combine(Dir, Prefix & "_" & Raw.Length & "_" & Hash.ToString("x") & ".dll")
            If Not IO.File.Exists(AsmPath) Then
                '并发双实例防冲突：先写唯一临时名再原子 Move；Move 失败即另一实例已写好，用它的
                Dim Tmp = AsmPath & "." & Guid.NewGuid().ToString("N") & ".tmp"
                IO.File.WriteAllBytes(Tmp, Raw)
                Try
                    IO.File.Move(Tmp, AsmPath)
                Catch moveEx As Exception
                    Try : IO.File.Delete(Tmp) : Catch : End Try
                    Try : IO.File.WriteAllText(IO.Path.Combine(Dir, "err_move_" & Prefix & ".txt"), moveEx.ToString()) : Catch : End Try
                End Try
            End If
            If IO.File.Exists(AsmPath) Then
                Try
                    Return Assembly.LoadFrom(AsmPath)
                Catch loadEx As Exception
                    Try : IO.File.WriteAllText(IO.Path.Combine(Dir, "err_load_" & Prefix & ".txt"), loadEx.ToString()) : Catch : End Try
                End Try
            End If
        Catch ex As Exception
            '缓存目录不可写等：回退内存加载；异常落文件（此处禁用 Logger，见 remarks）
            Try : IO.File.WriteAllText(IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "TCL", "Cache", "Assemblies", "err_" & Prefix & ".txt"), ex.ToString()) : Catch : End Try
        End Try
        Return Assembly.Load(Raw)
    End Function
    Private Shared Sub ExtractLibwebp() '这个方法会调用 ModBase，进而调用 TClKit，所以不能放在 New 里
        SetDllDirectory(PathUtils.RemoveSlashSuffix(PathPure.Value))
        Try
            ExtractResources(PathPure.Value & "libwebp.dll", "libwebp64")
        Catch ex As Exception
            Logger.Warn(ex, "写入 libwebp.dll 失败") '防止同时加载多个图片时，同时写入文件导致文件占用，进而导致崩溃
        End Try
    End Sub
    Private Declare Function SetDllDirectory Lib "kernel32" Alias "SetDllDirectoryA" (lpPathName As String) As Boolean

    '单文件运行时没有 exe.config，改为在代码中开启不安全的 HTTP 头解析
    Private Shared Sub EnableUnsafeHeaderParsing()
        Try
            Dim SettingsType = Type.GetType("System.Net.Configuration.SettingsSectionInternal, System, Version=4.0.0.0, Culture=neutral, PublicKeyToken=b77a5c561934e089")
            If SettingsType Is Nothing Then Return
            Dim Section = SettingsType.GetField("Section", BindingFlags.Static Or BindingFlags.NonPublic Or BindingFlags.Public)?.GetValue(Nothing)
            If Section Is Nothing Then Return
            SettingsType.GetField("useUnsafeHeaderParsing", BindingFlags.Instance Or BindingFlags.NonPublic Or BindingFlags.Public)?.SetValue(Section, True)
        Catch
            '此处在 AssemblyResolve 处理器注册之前运行，TClKit（含 Logger）尚未可加载，
            '不能引用任何库；失败时只能静默跳过（方法注释明确声明此约束）。
        End Try
    End Sub

    '控件模板事件
    Private Sub MyIconButton_Click(sender As Object, e As EventArgs)
        Select Case Settings.Get(Of McLoginType)("LoginType")
            Case McLoginType.Ms
                '微软
                Dim MsJson As JObject = Settings.Get(Of String)("LoginMsJson").DeserializeJson()
                MsJson.Remove(sender.Tag)
                Settings.Set("LoginMsJson", MsJson.ToString(Newtonsoft.Json.Formatting.None))
                '登录页可能尚未实例化（从未打开过微软登录页）
                If FrmLoginMs IsNot Nothing Then
                    If FrmLoginMs.ComboAccounts.SelectedItem Is sender.Parent Then FrmLoginMs.ComboAccounts.SelectedIndex = 0
                    FrmLoginMs.ComboAccounts.Items.Remove(sender.Parent)
                End If
            Case McLoginType.Legacy
                '离线
                Dim Names As New List(Of String)
                Names.AddRange(Settings.Get(Of String)("LoginLegacyName").ToString.Split("¨"))
                Names.Remove(sender.Tag)
                Settings.Set("LoginLegacyName", Names.Join("¨"c))
                If FrmLoginLegacy IsNot Nothing Then
                    FrmLoginLegacy.ComboName.ItemsSource = Names
                    FrmLoginLegacy.ComboName.Text = If(Names.Any, Names(0), "")
                End If
            Case Else
                '第三方
                Dim Token As String = Settings.Get(Of McLoginType)("LoginType").ToString()
                Dim Dict As New Dictionary(Of String, String)
                Dim Names As New List(Of String)
                Dim Passs As New List(Of String)
                If Not Settings.Get(Of String)("Login" & Token & "Email") = "" Then Names.AddRange(Settings.Get(Of String)("Login" & Token & "Email").Split("¨"))
                If Not Settings.Get(Of String)("Login" & Token & "Pass") = "" Then Passs.AddRange(Settings.Get(Of String)("Login" & Token & "Pass").Split("¨"))
                '两列表长度可能因历史数据去同步，取小者；用赋值而非 Add，重复键不再抛异常
                For i = 0 To Math.Min(Names.Count, Passs.Count) - 1
                    Dict(Names(i)) = Passs(i)
                Next
                Dict.Remove(sender.Tag)
                Settings.Set("Login" & Token & "Email", Dict.Keys.Join("¨"c))
                Settings.Set("Login" & Token & "Pass", Dict.Values.Join("¨"c))
                Select Case Token
                    Case "Nide"
                        If FrmLoginNide IsNot Nothing Then
                            FrmLoginNide.ComboName.ItemsSource = Dict.Keys
                            FrmLoginNide.ComboName.Text = If(Dict.Keys.Any, Dict.Keys(0), "")
                            FrmLoginNide.TextPass.Password = If(Dict.Values.Any, Dict.Values(0), "")
                        End If
                    Case "Auth"
                        If FrmLoginAuth IsNot Nothing Then
                            FrmLoginAuth.ComboName.ItemsSource = Dict.Keys
                            FrmLoginAuth.ComboName.Text = If(Dict.Keys.Any, Dict.Keys(0), "")
                            FrmLoginAuth.TextPass.Password = If(Dict.Values.Any, Dict.Values(0), "")
                        End If
                End Select
        End Select
    End Sub

End Class
