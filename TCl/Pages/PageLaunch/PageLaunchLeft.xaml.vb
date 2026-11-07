Imports System.Windows.Threading

Public Class PageLaunchLeft

    '加载当前版本
    Private IsLoad As Boolean = False
    Public IsLoadFinished As Boolean = False
    '微软登录「未启用」提示只弹一次（开源版未内置微软凭据时降级到离线登录）
    Private Shared MsDisabledHintShown As Boolean = False
    Public Sub PageLaunchLeft_Loaded() Handles Me.Loaded
        If IsLoad Then RefreshPage(True, False)

        If IsLoad Then Return
        IsLoad = True
        AniControlEnabled += 1

        '开始按钮

        '加载版本
        RunInNewThread(
        Sub()
            '自动整合包安装：准备
            Dim PackInstallPath As String = Nothing
            If FileUtils.Exists(Paths.Base & "modpack.zip") Then PackInstallPath = Paths.Base & "modpack.zip"
            If FileUtils.Exists(Paths.Base & "modpack.mrpack") Then PackInstallPath = Paths.Base & "modpack.mrpack"
            If PackInstallPath IsNot Nothing Then
                Logger.Warn($"需自动安装整合包：{PackInstallPath}")
                PageSelectLeft.CreateMcFolderInCurrentPath()
                McFolderListLoader.WaitForExit()
            End If
            '确认 Minecraft 文件夹存在
            If McFolderSelected = "" OrElse Not DirectoryUtils.Exists(McFolderSelected) Then
                '无效的文件夹
                If McFolderSelected = "" Then
                    Logger.Info("没有已储存的 Minecraft 文件夹")
                Else
                    Logger.Warn($"Minecraft 文件夹无效，该文件夹已不存在：{McFolderSelected}")
                End If
                McFolderListLoader.WaitForExit(IsForceRestart:=True)
                McFolderSelected = McFolderList.First.Location
            End If
            If Settings.Get(Of Boolean)("SystemDebugDelay") Then Thread.Sleep(RandomInteger(500, 3000))
            '自动整合包安装
            If PackInstallPath IsNot Nothing Then
                Try
                    Dim InstallLoader = ModpackInstall(PackInstallPath)
                    Logger.Info($"自动安装整合包已开始：{PackInstallPath}")
                    InstallLoader.WaitForExit()
                    If InstallLoader.State = LoadState.Finished Then
                        Logger.Info($"自动安装整合包成功，清理安装包：{PackInstallPath}")
                        FileUtils.Delete(PackInstallPath)
                    End If
                Catch ex As Exception
                    If ex.IsCanceled Then
                        Logger.Info($"自动安装整合包已取消：{PackInstallPath}")
                    Else
                        Logger.Error(ex, $"自动安装整合包失败：{PackInstallPath}", LogBehavior.Alert)
                    End If
                End Try
            End If
            '确认 Minecraft 版本存在
            Dim Selection As String = ReadIni(McFolderSelected & "TCL.ini", "Version")
            Dim Instance As McInstance = If(Selection = "", Nothing, New McInstance(Selection))
            If Instance Is Nothing OrElse Not PathUtils.IsParentOf(McFolderSelected, Instance.PathVersion) OrElse Not Instance.Check() Then
                '无效的版本
                Logger.Info($"当前选择的 Minecraft 版本无效：{If(Instance Is Nothing, "null", Instance.PathVersion)}", If(IsNothing(Instance), LogBehavior.None, LogBehavior.ToastIfDebug))
                If Not McInstanceListLoader.State = LoadState.Finished Then LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\", WaitForExit:=True)
                If Not McInstanceList.Any() OrElse McInstanceList.First.Value(0).Logo.Contains("RedstoneBlock") Then
                    Instance = Nothing
                    Logger.Info("无可用 Minecraft 版本")
                Else
                    Instance = McInstanceList.First.Value(0)
                    Logger.Info($"自动选择 Minecraft 版本：{Instance.PathVersion}")
                End If
            End If
            RunInUi(
            Sub()
                McInstanceSelected = Instance '绕这一圈是为了避免 McVersionCheck 触发第二次版本改变
                IsLoadFinished = True
                FrmLaunchRight?.RefreshButtonsUI()
                RefreshPage(False, False) '有可能选择的版本变化了，需要重新刷新
                If McLoginAble() = "" Then McLoginLoader.Start() '自动登录
                '用于自动化测试生成的程序是否可以正常运行
                If Environment.CommandLine.Contains("--test") Then
                    RunInThread(
                    Sub()
                        Thread.Sleep(500)
                        RunInUi(Sub() FrmMain.PageChange(FormMain.PageType.Download))
                        Thread.Sleep(500)
                        RunInUi(Sub() FrmMain.PageChange(FormMain.PageType.Download, FormMain.PageSubType.DownloadMod))
                        Thread.Sleep(500)
                        RunInUi(Sub() FrmMain.PageChange(FormMain.PageType.Setup))
                        Thread.Sleep(500)
                        RunInUi(Sub() FrmMain.PageChange(FormMain.PageType.Other))
                        Thread.Sleep(500)
                        RunInUi(Sub() FrmLaunchRight.BtnVersion_Click())
                        Thread.Sleep(500)
                        RunInUi(Sub() FrmLaunchRight.BtnMore_Click())
                        Thread.Sleep(500)
                        FormMain.EndProgramForce(123)
                    End Sub)
                End If
            End Sub)
        End Sub, "Version Check", ThreadPriority.AboveNormal)

        '刷新登录状态与首页账户摘要
        RefreshPage(False, False)
        If FrmLaunchRight IsNot Nothing Then FrmLaunchRight.RefreshAccountSummary()

        AniControlEnabled -= 1
    End Sub


#Region "切换大页面"

    ' <summary>切换至启动中页面。</summary>
    Public Sub PageChangeToLaunching()
        FrmMain.EnsureLaunchRightCreated()
        FrmLaunchRight.PageChangeToLaunching()
    End Sub
    ' <summary>切换至登录页面。</summary>
    Public Sub PageChangeToLogin()
        FrmMain.EnsureLaunchRightCreated()
        FrmLaunchRight.PageChangeToLogin()
    End Sub

#End Region

#Region "登录状态与首页账户摘要"

    ''' <summary>
    ''' 确认登录类型（含版本服务器要求），确保对应登录页实例已创建并同步其状态，
    ''' 然后刷新首页账户摘要。原「正版/离线切换徽章 + 内嵌登录表单」已移入账户管理页:
    ''' 启动时的账户数据仍从登录页窗体状态读取（GetLoginData），故此处保持窗体实例与设置同步。
    ''' </summary>
    Public Sub RefreshPage(KeepInput As Boolean, Anim As Boolean)
        Try
            Dim LoginPageType As Integer
            If McInstanceSelected IsNot Nothing Then
                LoginPageType = Settings.Get(Of Integer)("VersionServerLogin", Instance:=McInstanceSelected)
                '缓存当前版本的页面种类，下一次打开 McInstanceSelected 为空时才能加载出正确的页面
                Settings.Set("LoginPageType", LoginPageType)
            Else
                LoginPageType = Settings.Get(Of Integer)("LoginPageType")
            End If
            Dim Type As McLoginType
            Select Case LoginPageType
                Case 1 '仅正版
                    Type = McLoginType.Ms
                Case 2 '仅离线
                    Type = McLoginType.Legacy
                Case 3 '统一通行证
                    Type = McLoginType.Nide
                Case 4 'Authlib-Injector
                    Type = McLoginType.Auth
                Case Else '0：正版或离线，跟随上次选择
                    Type = Settings.Get(Of McLoginType)("LoginType")
                    If Type <> McLoginType.Ms AndAlso Type <> McLoginType.Legacy AndAlso Type <> McLoginType.Nide AndAlso Type <> McLoginType.Auth Then Type = McLoginType.Legacy
            End Select
            '开源版未内置微软登录凭据：未配置 ClientId 时把正版登录降级为离线，并提示一次
            If Type = McLoginType.Ms AndAlso String.IsNullOrEmpty(OAuthClientId) Then
                Type = McLoginType.Legacy
                Settings.Set("LoginType", Type)
                If Not MsDisabledHintShown Then
                    MsDisabledHintShown = True
                    Hint(ModLang.L("微软登录未启用：请设置环境变量 TCL_MS_CLIENT_ID 后重启启动器。"), HintType.Blue, False)
                End If
            End If
            Settings.Set("LoginType", Type)
            EnsureLoginForm(Type, KeepInput)
            If FrmLaunchRight IsNot Nothing Then FrmLaunchRight.RefreshAccountSummary()
            If FrmLaunchRight IsNot Nothing Then FrmLaunchRight.RefreshGreeting(FrmLaunchRight.CurrentDisplayName)
        Catch ex As Exception
            Logger.Error(ex, "刷新登录状态失败")
        End Try
    End Sub
    Public Sub EnsureLoginForm(Type As McLoginType, KeepInput As Boolean)
        Select Case Type
            Case McLoginType.Legacy
                If FrmLoginLegacy Is Nothing Then FrmLoginLegacy = New PageLoginLegacy
                FrmLoginLegacy.Reload(KeepInput)
            Case McLoginType.Ms
                If FrmLoginMs Is Nothing Then FrmLoginMs = New PageLoginMs
                FrmLoginMs.Reload(KeepInput)
            Case McLoginType.Nide
                If FrmLoginNide Is Nothing Then FrmLoginNide = New PageLoginNide
                FrmLoginNide.Reload(KeepInput)
            Case McLoginType.Auth
                If FrmLoginAuth Is Nothing Then FrmLoginAuth = New PageLoginAuth
                FrmLoginAuth.Reload(KeepInput)
        End Select
    End Sub

    ''' <summary>
    ''' 当前生效的离线账户名（与启动逻辑一致：窗体状态优先，其次列表第一项）。
    ''' </summary>
    Public Function GetCurrentLegacyName() As String
        If FrmLoginLegacy?.IsReloaded Then Return If(FrmLoginLegacy.ComboName.Text.Trim, "")
        If Settings.Get(Of String)("LoginLegacyName") = "" Then Return ""
        Return Settings.Get(Of String)("LoginLegacyName").ToString.BeforeFirst("¨").Trim
    End Function

#End Region

#Region "皮肤"

    '微软正版皮肤
    Public Shared SkinMs As New LoaderTask(Of (String, String), String)("Loader Skin Ms", AddressOf SkinMsLoad, AddressOf SkinMsInput, ThreadPriority.AboveNormal)
    Private Shared Function SkinMsInput() As (String, String)
        '获取名称
        Return (Settings.Get(Of String)("CacheMsV2Name"), Settings.Get(Of String)("CacheMsV2Uuid"))
    End Function
    Private Shared Sub SkinMsLoad(Data As LoaderTask(Of (String, String), String))
        '清空已有皮肤
        '如果在输入时清空皮肤，若输入内容一样则不会执行 Load 方法，导致皮肤不被加载
        RunInUi(Sub()
                        If FrmLoginMsSkin IsNot Nothing AndAlso FrmLoginMsSkin.Skin IsNot Nothing Then FrmLoginMsSkin.Skin.Clear()
                        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
                    End Sub)
        '获取 Url
        Dim UserName As String = Data.Input.Item1
        Dim Uuid As String = Data.Input.Item2
        If UserName = "" Then
            Data.Output = PathImage & "Skins/" & McSkinSex(McLoginLegacyUuid(UserName)) & ".png"
            Logger.Info("获取微软正版皮肤失败，ID 为空")
            GoTo Finish
        End If
        Try
            Dim Result As String = McSkinGetAddress(Uuid, "Ms")
            If Data.IsCanceled Then Throw New OperationCanceledException(ModLang.L("当前任务已取消：") & UserName)
            Result = McSkinDownload(Result)
            If Data.IsCanceled Then Throw New OperationCanceledException(ModLang.L("当前任务已取消：") & UserName)
            Data.Output = Result
        Catch ex As Exception
            If ex.IsCanceled Then
                Data.Output = ""
                Return
            ElseIf ex.GetDisplay(False).Contains("(429)") Then
                Data.Output = PathImage & "Skins/" & McSkinSex(McLoginLegacyUuid(UserName)) & ".png"
                Logger.Error($"获取正版皮肤失败（{UserName}）：获取皮肤太过频繁，请 5 分钟后再试！", LogBehavior.Toast)
            ElseIf ex.GetDisplay(False).Contains("未设置自定义皮肤") Then
                Data.Output = PathImage & "Skins/" & McSkinSex(McLoginLegacyUuid(UserName)) & ".png"
                Logger.Info("用户未设置自定义皮肤，跳过皮肤加载")
            Else
                Data.Output = PathImage & "Skins/" & McSkinSex(McLoginLegacyUuid(UserName)) & ".png"
                Logger.Error(ex, $"获取微软正版皮肤失败（{UserName}）", LogBehavior.Toast)
            End If
        End Try
Finish:
        '缓存账户头像：裁出脸部 8×8 区域，供账户管理页行内显示
        McSkinSaveFace(Data.Output, Data.Input.Item1)
        '刷新显示
        If FrmLoginMsSkin IsNot Nothing Then
            RunInUi(AddressOf FrmLoginMsSkin.Skin.Load)
        End If
        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
        If Not Data.IsCanceled Then
            Data.Input = Nothing '清空输入，因为皮肤实际上没有被渲染，如果不清空切换到页面的 Start 会由于输入相同而不渲染
        End If
    End Sub

    ''' <summary>
    ''' 缓存账户头像：从皮肤文件裁出脸部（基础脸 8,8 叠加帽子层 40,8）存到 {缓存}\Faces2\{账户名}.png，供账户管理页行内显示。
    ''' 不触发网络；皮肤文件（内置 pack 资源或本地缓存）读取失败时静默跳过。
    ''' 存到 PathTemp（可写）而非 pack 资源目录——运行中写入嵌入资源在单文件 exe 里无效。
    ''' </summary>
    Public Shared Sub McSkinSaveFace(SkinPath As String, AccountKey As String)
        Try
            If SkinPath = "" OrElse AccountKey = "" Then Return
            Dim Folder As String = PathTemp & "Faces2"
            If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
            SaveFaceFromSkin(SkinPath, Folder & "\" & FaceFileName(AccountKey))
        Catch ex As Exception
            Logger.Warn(ex, "缓存账户头像失败")
        End Try
    End Sub

    ''' <summary>账户名 → 头像缓存文件名（过滤非法字符，McSkinSaveFace / GetAccountFace / ClearAccountFace 共用）。</summary>
    Private Shared Function FaceFileName(AccountKey As String) As String
        Dim Key As String = If(AccountKey, "")
        For Each C As Char In IO.Path.GetInvalidFileNameChars()
            Key = Key.Replace(C, "_"c)
        Next
        Return Key & ".png"
    End Function

    ''' <summary>
    ''' 从皮肤图裁出脸部并写成 PNG，成功返回 True。
    ''' 脸 = 基础脸(8,8) + 帽子层(40,8) 半透明叠加（与 MySkin 2D 预览的 ImgFore 同一约定）；
    ''' 正版自定义皮肤普遍带帽子层（发型/头饰），只取基础层会把头像显示成「贴图错误」。
    ''' HD 皮肤（128×128 的整数倍）脸部区域要按倍率放大，写死 8,8 会裁到错位的像素。
    ''' </summary>
    Private Shared Function SaveFaceFromSkin(SkinPath As String, TargetPath As String) As Boolean
        Try
            If SkinPath = "" Then Return False
            '内置 pack 资源与本地文件都经 MyBitmap 读取（内部自带缓存）
            Dim Skin As New MyBitmap(SkinPath)
            If Skin.Pic Is Nothing OrElse Skin.Pic.Width < 64 Then Return False
            Dim S As Integer = Math.Max(1, Skin.Pic.Width \ 64)
            Dim Face As MyBitmap = Skin.Clip(8 * S, 8 * S, 8 * S, 8 * S)
            Face.Overlay(Skin.Clip(40 * S, 8 * S, 8 * S, 8 * S))
            Face.Save(TargetPath)
            Return True
        Catch ex As Exception
            Logger.Warn(ex, $"裁切账户头像失败（{SkinPath}）")
            Return False
        End Try
    End Function

    ''' <summary>账户头像缓存路径（与 McSkinSaveFace 对应）；无缓存时返回 Nothing。</summary>
    Public Shared Function GetAccountFace(AccountKey As String) As String
        Try
            If AccountKey = "" Then Return Nothing
            Dim Face As String = PathTemp & "Faces2\" & FaceFileName(AccountKey)
            If FileUtils.Exists(Face) Then Return Face
        Catch ex As Exception
        End Try
        Return Nothing
    End Function

    ''' <summary>
    ''' 账户头像；没有缓存时退回「内置默认皮肤裁出来的脸」。
    ''' <para/>
    ''' 绝不能退回整张皮肤图：徽章只有 18px、账户行 32px，整张 64×64 皮肤缩进方框里
    ''' 就是一团花屏。
    ''' </summary>
    Public Shared Function GetAccountFaceOrDefault(AccountKey As String) As String
        Try
            Dim Face As String = GetAccountFace(AccountKey)
            If Face IsNot Nothing Then Return Face
            Return BuiltinFacePath(AccountKey)
        Catch ex As Exception
            Logger.Warn(ex, "获取账户头像失败")
        End Try
        Return Nothing
    End Function

    Private Shared ReadOnly BuiltinFaceLock As New Object

    ''' <summary>内置默认皮肤（按 UUID 选 Steve/Alex）的脸部缓存：首次调用生成，之后直接命中文件。</summary>
    Public Shared Function BuiltinFacePath(AccountKey As String) As String
        Try
            Dim SkinName As String = If(AccountKey = "", "Steve", McSkinSex(McLoginLegacyUuid(AccountKey)))
            Dim Target As String = PathTemp & "Faces2\_builtin_" & SkinName & ".png"
            SyncLock BuiltinFaceLock
                If FileUtils.Exists(Target) Then Return Target
                Dim Folder As String = PathTemp & "Faces2"
                If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
                If SaveFaceFromSkin(ModLegacySkin.BuiltinSkinPath(SkinName), Target) Then Return Target
            End SyncLock
        Catch ex As Exception
            Logger.Warn(ex, "生成内置默认头像失败")
        End Try
        Return Nothing
    End Function

    ''' <summary>清除账户头像缓存。</summary>
    Public Shared Sub ClearAccountFace(AccountKey As String)
        Try
            If AccountKey = "" Then Return
            FileUtils.Delete(PathTemp & "Faces2\" & FaceFileName(AccountKey))
        Catch ex As Exception
            Logger.Warn(ex, "清除账户头像缓存失败")
        End Try
    End Sub

    '离线皮肤
    '皮肤配置改为按离线账户独立存储（ModLegacySkin），不再全局共享。
    Public Shared SkinLegacy As New LoaderTask(Of (String, String), String)("Loader Skin Legacy", AddressOf SkinLegacyLoad, AddressOf SkinLegacyInput, ThreadPriority.AboveNormal)
    ''' <summary>
    ''' 当前生效的离线账户名（皮肤配置的归属者）。
    ''' </summary>
    Public Shared Function CurrentLegacyName() As String
        If FrmLoginLegacy?.IsReloaded Then Return If(FrmLoginLegacy.ComboName.Text.Trim, "")
        If Settings.Get(Of String)("LoginLegacyName") = "" Then Return ""
        Return Settings.Get(Of String)("LoginLegacyName").ToString.BeforeFirst("¨").Trim
    End Function
    Private Shared Function SkinLegacyInput() As (String, String)
        '按当前离线账户读取其独立的皮肤配置
        Dim PlayerName As String = CurrentLegacyName()
        Dim Config = ModLegacySkin.GetConfig(PlayerName)
        '输入格式：Item1 = 配置指纹（类型/文件/ID/站点任一变化都会触发重载），Item2 = 玩家名
        Return (Config.Type & "|" & Config.File & "|" & Config.Id & "|" & Config.Api, PlayerName)
    End Function
    Private Shared Sub SkinLegacyLoad(Data As LoaderTask(Of (String, String), String))
        '清空已有皮肤
        RunInUi(Sub()
                        If FrmLoginLegacy IsNot Nothing AndAlso FrmLoginLegacy.Skin IsNot Nothing Then FrmLoginLegacy.Skin.Clear()
                        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
                    End Sub)
        '按离线账户独立的皮肤配置加载（ModLegacySkin），不再读全局 LaunchSkinType
        Dim PlayerName As String = Data.Input.Item2
        Data.Output = ResolveLegacySkinPath(PlayerName)
        If Data.IsCanceled Then Data.Output = ""
        '缓存账户头像：裁出脸部 8×8 区域，供账户管理页行内显示
        McSkinSaveFace(Data.Output, Data.Input.Item2)
        '刷新显示
        If FrmLoginLegacy IsNot Nothing Then
            RunInUi(AddressOf FrmLoginLegacy.Skin.Load)
        End If
        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
        If Not Data.IsCanceled Then
            Data.Input = Nothing '清空输入，因为皮肤实际上没有被渲染，如果不清空切换到页面的 Start 会由于输入相同而不渲染
        End If
    End Sub

    ''' <summary>
    ''' 按账户的皮肤配置解析出真正要显示的皮肤图片路径（加载器与头像缓存共用同一套规则）。
    ''' 取不到图时回退到该账户的默认皮肤，并给出提示。
    ''' </summary>
    Public Shared Function ResolveLegacySkinPath(PlayerName As String) As String
        Dim Config = ModLegacySkin.GetConfig(PlayerName)
        Dim Fallback As String = ModLegacySkin.DefaultSkinPath(PlayerName)
        Try
            Select Case Config.Type
                Case 1 'Steve
                    Return ModLegacySkin.BuiltinSkinPath("Steve")
                Case 2 'Alex
                    Return ModLegacySkin.BuiltinSkinPath("Alex")
                Case 3 '正版（使用配置中指定的正版玩家名）
                    If Config.Id.Count < 2 Then Return Fallback
                    Try
                        Return McSkinDownload(McSkinGetAddress(McLoginMojangUuid(Config.Id, True), "Mojang"))
                    Catch ex As Exception
                        If ex.IsCanceled Then Return ""
                        If ex.GetDisplay(False).Contains("(429)") Then
                            Logger.Info($"获取离线登录使用的正版皮肤失败（{Config.Id}）：获取皮肤太过频繁，请 5 分钟后再试！")
                        Else
                            Logger.Warn(ex, $"获取离线登录使用的正版皮肤失败（{Config.Id}）")
                        End If
                        Return Fallback
                    End Try
                Case 4 '本地皮肤文件（按账户隔离存放）
                    If Config.File = "" OrElse Not FileUtils.Exists(Config.File) Then
                        Hint(ModLang.L("未找到离线皮肤自定义文件，可能它已被删除。TCL 将使用默认的 Steve 皮肤！"))
                        Return Fallback
                    End If
                    Return Config.File
                Case 5, 6 '皮肤站
                    Try
                        '5 = LittleSkin（地址固定），6 = 自定义皮肤站（Config.Api，形如 https://站点/csl）
                        Dim Api As String = If(Config.Type = 5, "https://littleskin.cn/csl", Config.Api)
                        Dim Who As String = If(Config.Id <> "", Config.Id, PlayerName)
                        Return McSkinFromCsl(Api, Who, Config.File).Path
                    Catch ex As Exception
                        If ex.IsCanceled Then Return ""
                        Logger.Warn(ex, $"从皮肤站获取离线皮肤失败（{PlayerName}）")
                        Hint(ModLang.L("从皮肤站获取皮肤失败，已回退默认皮肤。"))
                        Return Fallback
                    End Try
                Case Else '0 默认（按 UUID 性别）
                    Return Fallback
            End Select
        Catch ex As Exception
            Logger.Warn(ex, $"解析离线皮肤失败（{PlayerName}）")
            Return Fallback
        End Try
    End Function

    ''' <summary>
    ''' 为还没有头像缓存的离线账户补生成头像。
    ''' <para/>
    ''' 头像是皮肤加载完才裁出来的，而皮肤加载只跑「当前账户」，
    ''' 所以列表里其它账户（以及标题栏切过去之前）永远是第一次生成的那张默认脸。
    ''' </summary>
    Public Shared Sub EnsureAccountFaces(Names As IEnumerable(Of String))
        Try
            Dim Missing As New List(Of String)
            SyncLock FaceTriedLock
                For Each N As String In Names
                    If N = "" OrElse GetAccountFace(N) IsNot Nothing Then Continue For
                    If FaceTried.Contains(N) Then Continue For '本会话已试过就不再重试，避免无限刷新
                    FaceTried.Add(N)
                    Missing.Add(N)
                Next
            End SyncLock
            If Missing.Count = 0 Then Return
            RunInNewThread(
            Sub()
                For Each N As String In Missing
                    Try
                        Dim P As String = ResolveLegacySkinPath(N)
                        If P <> "" Then McSkinSaveFace(P, N)
                    Catch ex As Exception
                        Logger.Warn(ex, $"补生成账户 {N} 的头像失败")
                    End Try
                Next
                RunInUi(
                Sub()
                    Try
                        '补生成的头像也要立刻上到标题栏徽章（否则徽章停在默认脸，等下次刷新才更新）
                        FrmMain?.RefreshTitleAccountFace()
                        If FrmLoginManager IsNot Nothing AndAlso FrmLoginManager.IsLoaded Then FrmLoginManager.RefreshAccounts()
                    Catch ex As Exception
                        Logger.Warn(ex, "补生成头像后刷新列表失败")
                    End Try
                End Sub)
            End Sub, "Skin Faces")
        Catch ex As Exception
            Logger.Warn(ex, "补生成账户头像失败")
        End Try
    End Sub
    ''' <summary>本会话内已尝试补生成头像的账户（失败的不要反复重试）。</summary>
    Private Shared ReadOnly FaceTried As New HashSet(Of String)(StringComparer.OrdinalIgnoreCase)
    Private Shared ReadOnly FaceTriedLock As New Object

    ''' <summary>
    ''' 皮肤配置改动后，重新解析该账户的皮肤并刷新它的头像缓存（不管它是不是当前账户）。
    ''' 此前只有「当前账户」会重载皮肤，导致账户列表与标题栏的头像永远停在第一次生成的那张。
    ''' </summary>
    Public Shared Sub RefreshAccountSkin(PlayerName As String)
        If PlayerName = "" Then Return
        RunInNewThread(
        Sub()
            Dim Path As String = ""
            Try
                ClearAccountFace(PlayerName)
                Path = ResolveLegacySkinPath(PlayerName)
                If Path <> "" Then McSkinSaveFace(Path, PlayerName)
            Catch ex As Exception
                Logger.Warn(ex, $"刷新账户 {PlayerName} 的头像失败")
            End Try
            RunInUi(
            Sub()
                Try
                    FrmMain?.RefreshTitleAccountFace()
                    If FrmLoginManager IsNot Nothing AndAlso FrmLoginManager.IsLoaded Then FrmLoginManager.RefreshAccounts()
                Catch ex As Exception
                    Logger.Warn(ex, "刷新账户列表失败")
                End Try
            End Sub)
        End Sub, "Skin Refresh " & PlayerName)
    End Sub

    '统一通行证皮肤
    Public Shared SkinNide As New LoaderTask(Of (String, String), String)("Loader Skin Nide", AddressOf SkinNideLoad, AddressOf SkinNideInput, ThreadPriority.AboveNormal)
    Private Shared Function SkinNideInput() As (String, String)
        '获取名称
        Return (Settings.Get(Of String)("CacheNideName"), Settings.Get(Of String)("CacheNideUuid"))
    End Function
    Private Shared Sub SkinNideLoad(Data As LoaderTask(Of (String, String), String))
        '清空已有皮肤
        '如果在输入时清空皮肤，若输入内容一样则不会执行 Load 方法，导致皮肤不被加载
        RunInUi(Sub()
                        If FrmLoginNideSkin IsNot Nothing AndAlso FrmLoginNideSkin.Skin IsNot Nothing Then FrmLoginNideSkin.Skin.Clear()
                        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
                    End Sub)
        '获取 Url
        Dim UserName As String = Data.Input.Item1
        Dim Uuid As String = Data.Input.Item2
        If UserName = "" Then
            Data.Output = PathImage & "Skins/" & McSkinSex(McLoginLegacyUuid(UserName)) & ".png"
            Logger.Info("获取统一通行证皮肤失败，ID 为空")
            GoTo Finish
        End If
        Try
            Dim Result As String = McSkinGetAddress(Uuid, "Nide")
            If Data.IsCanceled Then Throw New OperationCanceledException(ModLang.L("当前任务已取消：") & UserName)
            Result = McSkinDownload(Result)
            If Data.IsCanceled Then Throw New OperationCanceledException(ModLang.L("当前任务已取消：") & UserName)
            Data.Output = Result
        Catch ex As Exception
            If ex.IsCanceled Then
                Data.Output = ""
                Return
            ElseIf ex.GetDisplay(False).Contains("(429)") Then
                Data.Output = PathImage & "Skins/Steve.png"
                Logger.Error($"获取统一通行证皮肤失败（{UserName}）：获取皮肤太过频繁，请 5 分钟后再试！", LogBehavior.Toast)
            ElseIf ex.GetDisplay(False).Contains("未设置自定义皮肤") Then
                Data.Output = PathImage & "Skins/Steve.png"
                Logger.Info("用户未设置自定义皮肤，跳过皮肤加载")
            Else
                Data.Output = PathImage & "Skins/Steve.png"
                Logger.Error(ex, $"获取统一通行证皮肤失败（{UserName}）", LogBehavior.Toast)
            End If
        End Try
Finish:
        '缓存账户头像：裁出脸部 8×8 区域，供账户管理页行内显示
        McSkinSaveFace(Data.Output, Data.Input.Item1)
        '刷新显示
        If FrmLoginNideSkin IsNot Nothing Then
            RunInUi(AddressOf FrmLoginNideSkin.Skin.Load)
        End If
        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
        If Not Data.IsCanceled Then
            Data.Input = Nothing '清空输入，因为皮肤实际上没有被渲染，如果不清空切换到页面的 Start 会由于输入相同而不渲染
        End If
    End Sub

    'Authlib-Injector 皮肤
    Public Shared SkinAuth As New LoaderTask(Of (String, String), String)("Loader Skin Auth", AddressOf SkinAuthLoad, AddressOf SkinAuthInput, ThreadPriority.AboveNormal)
    Private Shared Function SkinAuthInput() As (String, String)
        '获取名称
        Return (Settings.Get(Of String)("CacheAuthName"), Settings.Get(Of String)("CacheAuthUuid"))
    End Function
    Private Shared Sub SkinAuthLoad(Data As LoaderTask(Of (String, String), String))
        '清空已有皮肤
        '如果在输入时清空皮肤，若输入内容一样则不会执行 Load 方法，导致皮肤不被加载
        RunInUi(Sub()
                        If FrmLoginAuthSkin IsNot Nothing AndAlso FrmLoginAuthSkin.Skin IsNot Nothing Then FrmLoginAuthSkin.Skin.Clear()
                        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
                    End Sub)
        '获取 Url
        Dim UserName As String = Data.Input.Item1
        Dim UUID As String = Data.Input.Item2
        If UserName = "" Then
            Data.Output = PathImage & "Skins/Steve.png"
            Logger.Info("获取 Authlib-Injector 皮肤失败，ID 为空")
            GoTo Finish
        End If
        Try
            Dim Result As String = McSkinGetAddress(UUID, "Auth")
            If Data.IsCanceled Then Throw New OperationCanceledException(ModLang.L("当前任务已取消：") & UserName)
            Result = McSkinDownload(Result)
            If Data.IsCanceled Then Throw New OperationCanceledException(ModLang.L("当前任务已取消：") & UserName)
            Data.Output = Result
        Catch ex As Exception
            If ex.IsCanceled Then
                Data.Output = ""
                Return
            ElseIf ex.GetDisplay(False).Contains("(429)") Then
                Data.Output = PathImage & "Skins/Steve.png"
                Logger.Error($"获取 Authlib-Injector 皮肤失败（{UserName}）：获取皮肤太过频繁，请 5 分钟后再试！", LogBehavior.Toast)
            ElseIf ex.GetDisplay(False).Contains("未设置自定义皮肤") Then
                Data.Output = PathImage & "Skins/Steve.png"
                Logger.Info("用户未设置自定义皮肤，跳过皮肤加载")
            Else
                Data.Output = PathImage & "Skins/Steve.png"
                Logger.Error(ex, $"获取 Authlib-Injector 皮肤失败（{UserName}）", LogBehavior.Toast)
            End If
        End Try
Finish:
        '缓存账户头像：裁出脸部 8×8 区域，供账户管理页行内显示
        McSkinSaveFace(Data.Output, Data.Input.Item1)
        '刷新显示
        If FrmLoginAuthSkin IsNot Nothing Then
            RunInUi(AddressOf FrmLoginAuthSkin.Skin.Load)
        End If
        RunInUi(Sub() FrmMain.RefreshTitleAccountFace())
        If Not Data.IsCanceled Then
            Data.Input = Nothing '清空输入，因为皮肤实际上没有被渲染，如果不清空切换到页面的 Start 会由于输入相同而不渲染
        End If
    End Sub

    '全部皮肤加载器
    '需要放在其中元素的后面，否则会因为它提前被加载而莫名其妙变成 Nothing
    Public Shared SkinLoaders As New List(Of LoaderTask(Of (String, String), String)) From {SkinMs, SkinLegacy, SkinNide, SkinAuth}

#End Region

End Class
