Imports System.Security.Cryptography

Public Class Settings

    Public Shared ReadOnly Entries As Dictionary(Of String, Setting) = (New List(Of Setting) From {
        New Setting("Identify", "", Source:=Sources.Registry),
        New Setting("WindowHeight", 550),
        New Setting("WindowWidth", 900),
        New Setting("AprilYear", 0, Source:=Sources.Registry, Encrypted:=True),
        New Setting("HintDownloadThread", False, Source:=Sources.Registry),
        New Setting("HintNotice", 0, Source:=Sources.Registry),
        New Setting("HintDownload", 0, Source:=Sources.Registry),
        New Setting("HintHide", False, Source:=Sources.Registry),
        New Setting("HintHandInstall", False, Source:=Sources.Registry),
        New Setting("HintBuy", False, Source:=Sources.Registry),
        New Setting("HintClearRubbish", 0, Source:=Sources.Registry),
        New Setting("HintUpdateMod", False, Source:=Sources.Registry),
        New Setting("HintCustomCommand", False, Source:=Sources.Registry),
        New Setting("HintMoreAdvancedSetup", False, Source:=Sources.Registry),
        New Setting("HintIndieSetup", False, Source:=Sources.Registry),
        New Setting("HintExportConfig", False, Source:=Sources.Registry),
        New Setting("HintSnapshot", False, Source:=Sources.Registry),
        New Setting("HintTCLInfo", False, Source:=Sources.Registry),
        New Setting("SystemEulaVersion", 0, Source:=Sources.Registry),
        New Setting("SystemCount", 0, Source:=Sources.Registry, Encrypted:=True),
        New Setting("SystemLaunchCount", 0, Source:=Sources.Registry, Encrypted:=True),
        New Setting("SystemLastVersionReg", 0, Source:=Sources.Registry, Encrypted:=True),
        New Setting("SystemHighestSavedBetaVersionReg", 0, Source:=Sources.Registry, Encrypted:=True),
        New Setting("SystemHighestBetaVersionReg", 0, Source:=Sources.Registry, Encrypted:=True),
        New Setting("SystemHighestAlphaVersionReg", 0, Source:=Sources.Registry, Encrypted:=True),
        New Setting("SystemDebugMode", False, Source:=Sources.Registry, OnChanged:=Sub() Logger.Instance.MinLevel = If(ModeDebug, LogLevel.Trace, LogLevel.Info)),
        New Setting("SystemLastExitClean", True, Source:=Sources.Registry), '上次是否正常退出（H5：启动读取后立刻置 False，正常退出置 True）
        New Setting("SystemDebugAnim", 9, Source:=Sources.Registry),
        New Setting("SystemDebugDelay", False, Source:=Sources.Registry),
        New Setting("SystemDebugSkipCopy", False, Source:=Sources.Registry),
        New Setting("SystemSystemCache", "", Source:=Sources.Registry),
        New Setting("SystemSystemActivity", 0),
        New Setting("SystemHttpProxyType", 1, Source:=Sources.Registry, OnChanged:=AddressOf ModNet.UpdateHttpProxy),
        New Setting("SystemHttpProxy", "", Source:=Sources.Registry, Encrypted:=True, OnChanged:=AddressOf ModNet.UpdateHttpProxy),
        New Setting("SystemHttpProxyCustomUsername", "", Source:=Sources.Registry, Encrypted:=True, OnChanged:=AddressOf ModNet.UpdateHttpProxy),
        New Setting("SystemHttpProxyCustomPassword", "", Source:=Sources.Registry, Encrypted:=True, OnChanged:=AddressOf ModNet.UpdateHttpProxy),
        New Setting("SystemHttpProxyForGame", True, Source:=Sources.Registry),
        New Setting("SystemDisableHardwareAcceleration", False, Source:=Sources.Registry, OnChanged:=Sub() FormMain.ApplyHardwareAcceleration()),
        New Setting("CacheDrops", "", Source:=Sources.Registry),
        New Setting("CacheExportConfig", "", Source:=Sources.Registry),
        New Setting("CacheMsOAuthRefresh", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsAccess", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsProfileJson", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsUuid", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsName", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsV2Migrated", False, Source:=Sources.Registry),
        New Setting("CacheMsV2OAuthRefresh", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsV2Access", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsV2ProfileJson", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsV2Uuid", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsV2Name", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheMsV2Expires", 0L, Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheNideAccess", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheNideUuid", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheNideName", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheNideUsername", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheNidePass", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheNideServer", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthAccess", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthClient", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthUuid", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthName", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthUsername", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthPass", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthServerServer", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthServerName", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheAuthServerRegister", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("CacheDownloadFolder", "", Source:=Sources.Registry),
        New Setting("CacheJavaListVersion", 0, Source:=Sources.Registry),
        New Setting("LoginRemember", True, Source:=Sources.Registry, Encrypted:=True),
        New Setting("LoginLegacyName", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("LoginMsJson", "{}", Source:=Sources.Registry, Encrypted:=True), '{UserName: OAuthToken, ...}
        New Setting("LoginNideEmail", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("LoginNidePass", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("LoginAuthEmail", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("LoginAuthPass", "", Source:=Sources.Registry, Encrypted:=True),
        New Setting("LoginAuthAccounts", "{}", Source:=Sources.Registry, Encrypted:=True), '{玩家名: {Pass, Server, Register}, ...} 第三方/LittleSkin 账号列表（第 75 轮）
        New Setting("CacheProfilesJson", "{}", Source:=Sources.Registry, Encrypted:=True), '{"Token:用户名": [{Id, Name}, ...]} 登录时缓存的多角色列表（第 130 轮 B1，供账户页切换角色）
        New Setting("LoginType", McLoginType.Legacy, Source:=Sources.Registry),
        New Setting("LoginPageType", 0),
        New Setting("LoginLegacySkinJson", "{}", Source:=Sources.Registry, Encrypted:=True), '离线账户皮肤配置（按玩家名存储，JSON：{玩家名: {Type, Id, Slim}}）；第 70 轮引入：皮肤按账户隔离，不再全局共享（注意：VB 隐式续行下此处不能插入独立注释行）
        New Setting("LaunchFolderSelect", ""),
        New Setting("LaunchFolders", "", Source:=Sources.Registry),
        New Setting("LaunchArgumentTitle", ""),
        New Setting("LaunchArgumentInfo", "TCL"),
        New Setting("LaunchArgumentJavaSelect", "", Source:=Sources.Registry),
        New Setting("LaunchArgumentJavaAll", "[]", Source:=Sources.Registry),
        New Setting("LaunchArgumentIndie", 0),
        New Setting("LaunchArgumentIndieV2", 4),
        New Setting("LaunchArgumentVisible", 5, Source:=Sources.Registry),
        New Setting("LaunchArgumentPriority", 1, Source:=Sources.Registry),
        New Setting("LaunchPreferredIpStack", 1),
        New Setting("LaunchRamCustomInitial", 0),
        New Setting("LaunchAdvanceLockMemory", False, OnChanged:=AddressOf PageSetupLaunch.UpdateRamMemory),
        New Setting("LaunchAdvanceRenderer", 0),
        New Setting("LaunchAdvanceDisableCrashAnalysis", False),
        New Setting("LaunchAdvanceNoJavaw", False),
        New Setting("LaunchAdvanceClasspathHead", ""),
        New Setting("LaunchAdvanceDisableLF", False),
        New Setting("LaunchArgumentWindowWidth", 854),
        New Setting("LaunchArgumentWindowHeight", 480),
        New Setting("LaunchArgumentWindowType", 1),
        New Setting("LaunchArgumentRam", False, Source:=Sources.Registry),
        New Setting("LaunchAdvanceJvm", "-XX:-OmitStackTraceInFastThrow -Djdk.lang.Process.allowAmbiguousCommands=True -Dfml.ignoreInvalidMinecraftCertificates=True -Dfml.ignorePatchDiscrepancies=True"),
        New Setting("LaunchAdvanceGame", ""),
        New Setting("LaunchAdvanceRun", ""),
        New Setting("LaunchAdvanceRunWait", True),
        New Setting("LaunchAdvanceDisableJLW", False),
        New Setting("LaunchAdvanceDisableLUA", False),
        New Setting("LaunchAdvanceGraphicCard", True, Source:=Sources.Registry),
        New Setting("LaunchAdvanceGC", 4),
        New Setting("LaunchRamType", 0, OnChanged:=AddressOf PageSetupLaunch.UpdateRamType),
        New Setting("LaunchRamCustom", 15),
        New Setting("LinkLastAutoJoinInviteCode", "", Source:=Sources.Registry),
        New Setting("LinkLatencyMode", 0, Source:=Sources.Registry),
        New Setting("LinkPeer", 0, Source:=Sources.Registry),
        New Setting("LinkCustomPeer", ""),
        New Setting("LinkEasyTierVersion", -1, Source:=Sources.Registry),
        New Setting("LinkDownloadMode", 0, Source:=Sources.Registry),
        New Setting("LinkDownloadNode", 0, Source:=Sources.Registry),
        New Setting("LinkBestNode", -1, Source:=Sources.Registry),
        New Setting("ToolHelpChinese", True, Source:=Sources.Registry),
        New Setting("UiBackgroundType", 0, Source:=Sources.Registry, OnChanged:=AddressOf ModVideo.ApplyBackgroundType), '背景展示类型：0=图片，1=视频，二选一（第 67 轮）
        New Setting("ToolLanguage", 0, Source:=Sources.Registry, OnChanged:=AddressOf ModBase.ApplyToolLanguage), '默认简体中文（第 93 轮按用户要求改回中文；索引 0=简中，1=英文，原为 1）
        New Setting("ToolDownloadThread", 63, Source:=Sources.Registry),
        New Setting("ToolDownloadSpeed", 42, Source:=Sources.Registry, OnChanged:=AddressOf ModNet.UpdateNetTaskSpeedLimitHigh),
        New Setting("ToolDownloadSource", 1, Source:=Sources.Registry),
        New Setting("ToolDownloadVersion", 1, Source:=Sources.Registry),
        New Setting("ToolDownloadTranslate", 0, Source:=Sources.Registry),
        New Setting("ToolDownloadTranslateV2", 1, Source:=Sources.Registry),
        New Setting("ToolDownloadCurseForgeKey", "", Source:=Sources.Registry, Encrypted:=True), 'CurseForge API Key（世界/模组/整合包搜索与下载需携带；留空则世界列表可能为空）
        New Setting("ToolDownloadCert", False, Source:=Sources.Registry, OnChanged:=AddressOf ModNet.ShouldValidateSslCertificateOnLogin),
        New Setting("ToolDownloadMod", 2, Source:=Sources.Registry),
        New Setting("ToolModLocalNameStyle", 0, Source:=Sources.Registry),
        New Setting("ToolUpdateRelease", False, Source:=Sources.Registry),
        New Setting("ToolUpdateSnapshot", False, Source:=Sources.Registry),
        New Setting("ToolUpdateReleaseLast", "", Source:=Sources.Registry),
        New Setting("ToolUpdateSnapshotLast", "", Source:=Sources.Registry),
        New Setting("UiLauncherTransparent", 600, OnChanged:=Sub(Value As Integer) If FrmMain IsNot Nothing Then FrmMain.Opacity = Value / 1000 + 0.4), '避免与 TCL1 设置冲突（UiLauncherOpacity）
        New Setting("UiLauncherHue", 180),
        New Setting("UiLauncherSat", 80),
        New Setting("UiLauncherDelta", 90),
        New Setting("UiLauncherLight", 20),
        New Setting("UiLauncherTheme", 8, OnChanged:=AddressOf ThemeRefresh), '默认主题：暮霞金
        New Setting("UiLauncherThemeHide", "0|1|2|3|4", Source:=Sources.Registry, Encrypted:=True),
        New Setting("UiLauncherThemeHide2", "0|1|2|3|4", Source:=Sources.Registry, Encrypted:=True),
        New Setting("UiLauncherLogo", True),
        New Setting("UiMaxFps", 0, Source:=Sources.Registry),
        New Setting("UiLauncherEmail", False),
        New Setting("UiComponentOpacity", 50, OnChanged:=AddressOf UpdateComponentOpacity), '组件半透明程度：0 = 完全透明，100 = 完全不透明
        New Setting("UiMenuAnimation", True, Source:=Sources.Registry), '弹出菜单是否启用过渡动画（第 131 轮个性化细化）
        New Setting("UiMenuOpacity", 100, Source:=Sources.Registry), '弹出菜单卡片不透明度：30~100，100 = 完全不透明（第 131 轮个性化细化）
        New Setting("UiListButtonsAlwaysShow", False, Source:=Sources.Registry, OnChanged:=Sub() If FrmMain IsNot Nothing Then FrmMain.RefreshListButtons()), '列表行操作按钮常驻显示（第 132 轮个性化）
        New Setting("UiInstanceSortMode", 0, Source:=Sources.Registry), '实例排序: 0=最近游玩 1=名称 2=版本 3=创建时间
        New Setting("UiInstanceViewMode", 0, Source:=Sources.Registry), '实例视图: 0=列表 1=网格
        New Setting("UiWindowRadius", 6, OnChanged:=Sub(Value As Object) If FrmMain IsNot Nothing Then FrmMain.ApplyWindowRadius(Value)), '窗口圆角半径（px），0 = 直角
        New Setting("UiTitleBarHeight", 48, OnChanged:=Sub(Value As Object) If FrmMain IsNot Nothing Then FrmMain.ApplyTitleBarHeight(Value)), '标题栏高度（px），默认 48
        New Setting("UiTitleAccountBadge", True, OnChanged:=Sub(Value As Object) If FrmMain IsNot Nothing Then FrmMain.ApplyTitleAccountBadge(Value)), '标题栏账户徽章（头像 + 名字）
        New Setting("UiBackgroundRotate", 0, Source:=Sources.Registry, OnChanged:=Sub(Value As Object) If FrmMain IsNot Nothing Then FrmMain.ApplyBackgroundRotate(Value)), '背景图片自动轮播间隔（分钟，0 = 关闭）
        New Setting("UiSkipIntroAnim", False, Source:=Sources.Registry), '跳过启动入场动画（下次启动生效），可省下约 0.5 秒软件渲染开销
        New Setting("UiRememberWindowPos", False), '记住窗口位置（下次启动回到上次所在位置）
        New Setting("UiWindowPosX", 0), '上次的窗口左上角 X（WPF 单位）
        New Setting("UiWindowPosY", 0), '上次的窗口左上角 Y（WPF 单位）
        New Setting("UiBackgroundColorful", True, OnChanged:=Sub() ThemeRefresh()), '不传入参数
        New Setting("UiAutoThemeFromBackground", True, OnChanged:=Sub(Value As Boolean) If Not Value Then ModSecret.ResetWallpaperTheme()), '根据背景图自动提取主题色（HMCL Monet 式取色）
        New Setting("UiBackgroundOpacity", 1000, OnChanged:=AddressOf FormMain.UpdateBackgroundAndTitleBar), '背景图片不透明度，默认 100%
        New Setting("UiBackgroundBlur", 0, OnChanged:=AddressOf FormMain.UpdateBackgroundAndTitleBar),
        New Setting("UiBackgroundSuit", 0, OnChanged:=AddressOf FormMain.UpdateBackgroundAndTitleBar),
        New Setting("UiFontFile", ""), '自定义字体文件路径（TCL\Fonts 下），启动时自动应用
        New Setting("UiFontFamily", ""), '自定义字体家族名（记录用）
        New Setting("UiLogoType", 1, OnChanged:=AddressOf FormMain.UpdateBackgroundAndTitleBar),
        New Setting("UiLogoText", "", OnChanged:=AddressOf FormMain.UpdateBackgroundAndTitleBar),
        New Setting("UiLogoLeft", False, OnChanged:=AddressOf FormMain.UpdateBackgroundAndTitleBar),
        New Setting("UiMusicVolume", 500),
        New Setting("UiMusicStop", False),
        New Setting("UiMusicStart", False),
        New Setting("UiMusicRandom", True),
        New Setting("UiMusicAuto", True),
        New Setting("UiVideoMute", True, OnChanged:=AddressOf ModVideo.VideoUpdateAudio), '背景视频默认静音（画面用）
        New Setting("UiVideoVolume", 500, OnChanged:=AddressOf ModVideo.VideoUpdateAudio),
        New Setting("UiVideoRandom", True),
        New Setting("UiHiddenPageDownload", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenPageLink", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenPageSetup", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenPageOther", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenFunctionSelect", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenFunctionModUpdate", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenFunctionInstanceModify", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenFunctionHidden", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenSetupLaunch", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenSetupUi", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenSetupLink", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenSetupSystem", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenOtherHelp", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenOtherAbout", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("UiHiddenOtherTest", False, OnChanged:=AddressOf PageSetupUI.HiddenRefresh),
        New Setting("VersionAdvanceJvm", "", Source:=Sources.Instance),
        New Setting("VersionAdvanceGame", "", Source:=Sources.Instance),
        New Setting("VersionAdvanceAssets", 0, Source:=Sources.Instance),
        New Setting("VersionAdvanceAssetsV2", False, Source:=Sources.Instance),
        New Setting("VersionAdvanceRenderer", 0, Source:=Sources.Instance),
        New Setting("VersionRamCustomInitial", 0, Source:=Sources.Instance),
        New Setting("VersionAdvanceClasspathHead", "", Source:=Sources.Instance),
        New Setting("VersionAdvanceDisableLF", False, Source:=Sources.Instance),
        New Setting("VersionServerLoginRequire", 0, Source:=Sources.Instance),
        New Setting("VersionServerLoginLock", False, Source:=Sources.Instance),
        New Setting("VersionAdvanceRun", "", Source:=Sources.Instance),
        New Setting("VersionAdvanceRunWait", True, Source:=Sources.Instance),
        New Setting("VersionAdvanceDisableJLW", False, Source:=Sources.Instance),
        New Setting("VersionAdvanceDisableLUA", False, Source:=Sources.Instance),
        New Setting("VersionAdvanceDisableModUpdate", False, Source:=Sources.Instance),
        New Setting("VersionAdvanceGC", 0, Source:=Sources.Instance),
        New Setting("VersionRamType", 2, Source:=Sources.Instance, OnChanged:=AddressOf PageInstanceSetup.OnVersionRamTypeChanged),
        New Setting("VersionRamCustom", 15, Source:=Sources.Instance),
        New Setting("VersionRamOptimize", 0, Source:=Sources.Instance),
        New Setting("VersionArgumentTitle", "", Source:=Sources.Instance),
        New Setting("VersionArgumentInfo", "", Source:=Sources.Instance),
        New Setting("VersionArgumentIndie", -1, Source:=Sources.Instance),
        New Setting("VersionArgumentIndieV2", False, Source:=Sources.Instance),
        New Setting("VersionArgumentJavaSelect", "使用全局设置", Source:=Sources.Instance),
        New Setting("VersionArgumentJavaV2", 0, Source:=Sources.Instance),
        New Setting("VersionArgumentJavaRange", "", Source:=Sources.Instance),
        New Setting("VersionServerEnter", "", Source:=Sources.Instance),
        New Setting("VersionServerLogin", 0, Source:=Sources.Instance, OnChanged:=AddressOf PageInstanceSetup.OnVersionServerLoginChanged),
        New Setting("VersionServerNide", "", Source:=Sources.Instance),
        New Setting("VersionServerAuthRegister", "", Source:=Sources.Instance),
        New Setting("VersionServerAuthName", "", Source:=Sources.Instance),
        New Setting("VersionServerAuthServer", "", Source:=Sources.Instance)
    }).ToDictionary(Function(e) e.Key)

    Public Enum Sources
        Normal
        Registry
        Instance
    End Enum
    Public Class Setting
        Public Key As String
        Public Encrypted As Boolean
        Public DefaultValue As Object
        Public Source As Sources
        ''' <summary>
        ''' 当设置项的值实际被改变时触发，参数为新值。
        ''' </summary>
        Public OnChanged As Action(Of Object)
        ''' <summary>
        ''' 该设置的值的实际类型。
        ''' </summary>
        Public Type As Type
        Public Sub New(Key As String, Value As Object, Optional Source As Sources = Sources.Normal, Optional Encrypted As Boolean = False, Optional OnChanged As Action(Of Object) = Nothing)
            Try
                Me.Key = Key
                Me.DefaultValue = Value
                Me.Encrypted = Encrypted
                Me.Source = Source
                Me.Type = If(Value, "").GetType
                Me.OnChanged = OnChanged
            Catch ex As Exception
                Logger.Error(ex, "初始化设置项失败") '初始化失败时的兜底处理
            End Try
        End Sub

        ''' <summary>
        ''' 读取过的设置的缓存，若从未读取过则为 Nothing。
        ''' 若为版本独立设置，键为版本路径；否则，键为空字符串。
        ''' </summary>
        Public ValueCache As New ConcurrentDictionary(Of String, Object)
        Public Function GetCache(Instance As McInstance)
            Dim Key As String = If(Source = Sources.Instance, Instance.PathVersion, "")
            Dim Result = Nothing
            Return If(ValueCache.TryGetValue(Key, Result), Result, Nothing)
        End Function
        Public Sub [Set](Value As Object, Instance As McInstance)
            Dim Key As String = If(Source = Sources.Instance, Instance.PathVersion, "")
            ValueCache(Key) = Value
        End Sub

        ''' <summary>
        ''' 立即将当前的 Value 写入对应的注册表或文件。
        ''' </summary>
        Public Sub Save(Optional Instance As McInstance = Nothing)
            Dim Value As String = GetCache(Instance)
            Logger.Trace($"保存设置：{Key} {If(Encrypted, "", "= " & Value)}{If(Instance Is Nothing, "", $"（实例：{Instance?.PathVersion}）")}")
            If Encrypted Then
                Try
                    If Value Is Nothing Then Value = ""
                    Value = DesEncrypt(Value, "TCL" & Identify)
                Catch ex As Exception
                    Logger.Warn(ex, $"加密设置失败：{Key}")
                End Try
            End If
            Select Case Source
                Case Sources.Normal
                    WriteIni("Setup", Key, Value)
                Case Sources.Registry
                    Try
                        RegistryUtils.Write($"HKEY_CURRENT_USER\Software\{RegFolder}", Key, Value)
                    Catch ex As Exception
                        Logger.Warn(ex, $"写入注册表失败（{Key} → {Value}）", LogBehavior.Toast)
                    End Try
                Case Sources.Instance
                    If Instance Is Nothing Then Throw New Exception($"保存版本独立设置 {Key} 时未提供目标版本")
                    WriteIni(Instance.PathVersion & "TCL\Setup.ini", Key, Value)
            End Select
        End Sub

    End Class

    ''' <summary>
    ''' 改变某个设置项的值。
    ''' </summary>
    Public Shared Sub [Set](Key As String, Value As Object, Optional Instance As McInstance = Nothing)
        Dim Entry As Setting = Nothing
        If Not Entries.TryGetValue(Key, Entry) Then Throw New KeyNotFoundException(ModLang.L("未找到设置项：") & Key)
        Try
            '判断是否一致
            Value = CTypeDynamic(Value, Entry.Type)
            If Entry.GetCache(Instance) Is Nothing Then [Get](Key, Instance)
            If Entry.GetCache(Instance) = Value Then Return
            '设置新值
            Entry.Set(Value, Instance)
            Entry.Save(Instance)
            '触发改变事件（必须在保存之后，以保证 VersionServerLogin 之类的在事件中读取到的是最新的值）
            If Entry.OnChanged IsNot Nothing Then Entry.OnChanged.Invoke(Value)
        Catch ex As Exception
            Logger.Error(ex, $"设置设置项时出错（{Key}, {Value}）")
        End Try
    End Sub
    ''' <summary>
    ''' 写入某个未经加密的设置项。
    ''' 若该设置项经过了加密，则会抛出异常。
    ''' </summary>
    Public Shared Sub SetSafe(Key As String, Value As Object, Optional Instance As McInstance = Nothing)
        Dim Entry As Setting = Nothing
        If Not Entries.TryGetValue(Key, Entry) Then Throw New KeyNotFoundException(ModLang.L("未找到设置项：") & Key)
        If Entry.Encrypted Then Throw New InvalidOperationException(ModLang.L("禁止写入加密设置项：") & Key)
        [Set](Key, Value, Instance)
    End Sub

    ''' <summary>
    ''' 获取某个设置项的值。
    ''' </summary>
    Public Shared Function [Get](Key As String, Optional Instance As McInstance = Nothing)
        Dim Entry As Setting = Nothing
        If Not Entries.TryGetValue(Key, Entry) Then Throw New KeyNotFoundException(ModLang.L("未找到设置项：") & Key)
        Dim Value = Entry.GetCache(Instance)
        If Value IsNot Nothing Then Return Value
        '正常读取
        Try
            Dim GotValue As String = Nothing '先用 String 储存，避免类型转换
            Dim DefaultValue As String = If(Entry.Encrypted, DesEncrypt(Entry.DefaultValue, "TCL" & Identify), Entry.DefaultValue)
            Select Case Entry.Source
                Case Sources.Normal
                    GotValue = ReadIni("Setup", Key, DefaultValue)
                Case Sources.Registry
                    GotValue = RegistryUtils.TryRead($"HKEY_CURRENT_USER\Software\{RegFolder}", Key, DefaultValue)
                Case Sources.Instance
                    If Instance Is Nothing Then
                        Throw New Exception($"读取版本设置 {Key} 时未提供目标版本")
                    Else
                        GotValue = ReadIni(Instance.PathVersion & "TCL\Setup.ini", Key, DefaultValue)
                    End If
            End Select
            If Entry.Encrypted Then
                If GotValue = DefaultValue Then
                    GotValue = Entry.DefaultValue
                Else
                    Try
                        GotValue = DesDecrypt(GotValue, "TCL" & Identify)
                    Catch ex As Exception
                        Logger.Warn(ex, $"解密设置失败：{Key}")
                        GotValue = Entry.DefaultValue
                        Entry.Set(Entry.DefaultValue, Instance)
                        Entry.Save(Instance)
                    End Try
                End If
            End If
            Entry.Set(CTypeDynamic(GotValue, Entry.Type), Instance)
        Catch ex As Exception
            Logger.Error(ex, $"读取设置失败：{Key}", LogBehavior.Toast)
            Entry.Set(CTypeDynamic(Entry.DefaultValue, Entry.Type), Instance)
        End Try
        Return Entry.GetCache(Instance)
    End Function
    Public Shared Function [Get](Of T)(Key As String, Optional Instance As McInstance = Nothing) As T
        Return [Get](Key, Instance)
    End Function
    ''' <summary>
    ''' 获取某个未经加密的设置项的值。
    ''' 若该设置项经过了加密，则会抛出异常。
    ''' </summary>
    Public Shared Function GetSafe(Key As String, Optional Instance As McInstance = Nothing)
        Dim Entry As Setting = Nothing
        If Not Entries.TryGetValue(Key, Entry) Then Throw New KeyNotFoundException(ModLang.L("未找到设置项：") & Key)
        If Entry.Encrypted Then Throw New InvalidOperationException(ModLang.L("禁止读取加密设置项：") & Key)
        Return [Get](Key, Instance)
    End Function

    ''' <summary>
    ''' 初始化某个设置项的值。
    ''' </summary>
    Public Shared Sub Reset(Key As String, Optional Instance As McInstance = Nothing)
        Dim Entry As Setting = Nothing
        If Not Entries.TryGetValue(Key, Entry) Then Throw New KeyNotFoundException(ModLang.L("未找到设置项：") & Key)
        Try
            '判断是否一致
            If Entry.GetCache(Instance) Is Nothing Then [Get](Key, Instance)
            If Entry.GetCache(Instance) = Entry.DefaultValue Then Return
            '设置新值
            Entry.Set(Entry.DefaultValue, Instance)
            Select Case Entry.Source
                Case Sources.Normal
                    DeleteIniKey("Setup", Key)
                Case Sources.Registry
                    Try
                        RegistryUtils.Delete($"HKEY_CURRENT_USER\Software\{RegFolder}", Key)
                    Catch ex As Exception
                        Logger.Warn(ex, $"删除注册表失败（{Key}）", LogBehavior.Toast)
                    End Try
                Case Sources.Instance
                    If Instance Is Nothing Then Throw New Exception($"重置版本设置 {Key} 时未提供目标版本")
                    DeleteIniKey(Instance.PathVersion & "TCL\Setup.ini", Key)
            End Select
            '触发改变事件
            If Entry.OnChanged IsNot Nothing Then Entry.OnChanged.Invoke(Entry.DefaultValue)
        Catch ex As Exception
            Logger.Error(ex, $"重置设置项时出错（{Key}）")
        End Try
    End Sub

    ''' <summary>
    ''' 获取某个设置项的默认值。
    ''' </summary>
    Public Shared Function GetDefault(Key As String) As String
        Return Entries(Key).DefaultValue
    End Function

    ''' <summary>
    ''' 对应的注册表或文件是否已经储存了某个设置项。
    ''' 若改成了和默认值一样的，在 2.12.3- 会储存更改，在 2.12.4+ 不会储存更改。
    ''' </summary>
    Public Shared Function HasSaved(Key As String, Optional Instance As McInstance = Nothing) As Boolean
        Select Case Entries(Key).Source
            Case Sources.Normal
                Return HasIniKey("Setup", Key)
            Case Sources.Registry
                Try
                    Return RegistryUtils.Has($"HKEY_CURRENT_USER\Software\{RegFolder}", Key)
                Catch ex As Exception
                    Logger.Warn(ex, $"判断注册表 {Key} 是否存在时出错")
                    Return False
                End Try
            Case Else 'Source.Instance
                If Instance Is Nothing Then Throw New Exception($"判断版本设置 {Key} 是否存在时未提供目标版本")
                Return HasIniKey(Instance.PathVersion & "TCL\Setup.ini", Key)
        End Select
    End Function

#Region "DES 加密"

    Private Shared ReadOnly desInitialVector As Byte() = Encoding.UTF8.GetBytes("95168702")

    ''' <summary>
    ''' 使用 DES 对称加密算法加密字符串。
    ''' </summary>
    Private Shared Function DesEncrypt(sourceString As String, Optional key As String = Nothing) As String
        key = If(key Is Nothing, "@;$ Abv2", key.GetStableHashCode().ToString().EnsureLength("X"c, 8).Substring(0, 8))
        Dim btKey As Byte() = Encoding.UTF8.GetBytes(key)
        Using des As New DESCryptoServiceProvider()
            Using ms As New MemoryStream()
                Using cs As New CryptoStream(ms, des.CreateEncryptor(btKey, desInitialVector), CryptoStreamMode.Write)
                    Dim inData As Byte() = Encoding.UTF8.GetBytes(sourceString)
                    cs.Write(inData, 0, inData.Length)
                    cs.FlushFinalBlock()
                    Return Convert.ToBase64String(ms.ToArray())
                End Using
            End Using
        End Using
    End Function

    ''' <summary>
    ''' 使用 DES 对称加密算法解密字符串。
    ''' </summary>
    Private Shared Function DesDecrypt(encryptedString As String, Optional key As String = Nothing) As String
        key = If(key Is Nothing, "@;$ Abv2", key.GetStableHashCode().ToString().EnsureLength("X"c, 8).Substring(0, 8))
        Dim btKey As Byte() = Encoding.UTF8.GetBytes(key)
        Using des As New DESCryptoServiceProvider()
            Using ms As New MemoryStream()
                Using cs As New CryptoStream(ms, des.CreateDecryptor(btKey, desInitialVector), CryptoStreamMode.Write)
                    Dim inData As Byte() = Convert.FromBase64String(encryptedString)
                    cs.Write(inData, 0, inData.Length)
                    cs.FlushFinalBlock()
                    Return Encoding.UTF8.GetString(ms.ToArray())
                End Using
            End Using
        End Using
    End Function

#End Region

End Class
