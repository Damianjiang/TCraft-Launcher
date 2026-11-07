Imports System.Collections.ObjectModel

Public Class PageDownloadResourceDetail
    Private ResourceItem As MyResourceItem = Nothing

    ''' <summary>
    ''' 当前页面应展示的内容类别。
    ''' 若当前页面是在查看前置时进入，则为 Any；这也能用于指示当前是否在查看前置。
    ''' </summary>
    Private TargetResourceType As ResourceTypes
    Private Project As ResourceProject
    Private TargetVersion As String
    Private TargetLoaders As ModLoaders
    Public Sub LoadPageArguments() Handles Me.Loaded
        Project = FrmMain.PageCurrent.Additional(0)
        TargetVersion = FrmMain.PageCurrent.Additional(2)
        TargetLoaders = CType(FrmMain.PageCurrent.Additional(3), ModLoaders)
        If TargetLoaders <> ModLoaders.None AndAlso Not TargetLoaders.Flags.IsSingle Then TargetLoaders = TargetLoaders.Flags.First() '强制取第一个
        TargetResourceType = FrmMain.PageCurrent.Additional(4)
    End Sub

#Region "加载器"

    Private ResourceFileLoader As New LoaderTask(Of Integer, List(Of ResourceVersion))(
        "Resource File",
        Sub(Task)
            LoadPageArguments()
            Dim Result = ResourceVersion.FromProjectId(Project.Id, Project.Platform)
            If Task.IsCanceled Then Return
            Task.Output = Result
        End Sub)

    '初始化加载器信息
    Private Sub PageDownloadResourceDetail_Inited(sender As Object, e As EventArgs) Handles Me.Initialized
        LoadPageArguments()
        PageLoaderInit(Load, PanLoad, PanMain, CardIntro, ResourceFileLoader, AddressOf Load_OnFinish)
    End Sub
    '自动重试 + 失败提示
    Private Sub Load_State(sender As Object, state As MyLoading.MyLoadingState, oldState As MyLoading.MyLoadingState) Handles Load.StateChanged
        Select Case ResourceFileLoader.State
            Case LoadState.Failed
                Dim ErrorMessage As String = ""
                If ResourceFileLoader.Error IsNot Nothing Then ErrorMessage = ResourceFileLoader.Error.Message
                If ErrorMessage.Contains("不是有效的 json 文件") Then
                    Logger.Warn("下载的文件 json 列表损坏，已自动重试")
                    PageLoaderRestart()
                Else
                    '版本列表拉取失败（网络 / CurseForge Key 缺失等）：给出可操作提示，避免静默空白
                    Dim Suffix As String
                    If Project.Platform = ResourcePlatforms.CurseForge Then
                        Suffix = "该资源来自 CurseForge：请检查网络，或在「设置 → 系统 → 社区资源 → CurseForge Key」配置密钥后重试；也可点上方「转到 CurseForge」手动查看。"
                    Else
                        Suffix = "请检查网络后重试；也可点上方「转到 " & Project.Platform & "」手动查看。"
                    End If
                    Hint(ModLang.L("获取版本列表失败：") & If(ErrorMessage, ModLang.L("未知错误")) & vbCrLf & Suffix, HintType.Red)
                End If
        End Select
    End Sub
    '结果 UI 化
    Private Class CardSorter
        Implements IComparer(Of String)
        Public Topmost As String = ""
        Public Function Compare(x As String, y As String) As Integer Implements IComparer(Of String).Compare
            '相同
            If x = y Then Return 0
            '置顶
            If x = Topmost Then Return -1
            If y = Topmost Then Return 1
            '特殊版本
            Dim IsXSpecial As Boolean = Not x.Contains(".")
            Dim IsYSpecial As Boolean = Not y.Contains(".")
            If IsXSpecial AndAlso IsYSpecial Then Return x.CompareTo(y)
            If IsXSpecial Then Return 1
            If IsYSpecial Then Return -1
            '比较版本号
            Dim VersionCodeSort = -McUtils.CompareVersion(x.Replace(x.BeforeFirst(" ") & " ", ""), y.Replace(y.BeforeFirst(" ") & " ", ""))
            If VersionCodeSort <> 0 Then Return VersionCodeSort
            '比较全部
            Return -McUtils.CompareVersion(x, y)
        End Function
        Public Sub New(Optional Topmost As String = "")
            Me.Topmost = If(Topmost, "")
        End Sub
    End Class

    Private VersionFilter As String
    Private GroupedDrop As Boolean '是否按 Drop 筛选（1.21 / 1.20 / 1.19 / ...）而非小版本号（1.21.1 / 1.21 / 1.20.4 / ...）
    Private GroupedOld As Boolean '是否折叠远古版本为一个选项
    '筛选类型相同的结果（Modrinth 会返回 Mod、服务端插件、数据包混合的列表）
    Private Function GetResults() As List(Of ResourceVersion)
        Dim Results As List(Of ResourceVersion) = ResourceFileLoader.Output
        Select Case TargetResourceType
            Case ResourceTypes.Any
                Results = Results.Where(Function(r) r.ResourceType <> ResourceTypes.Plugin).ToList
            Case ResourceTypes.Shader, ResourceTypes.ResourcePack
                '不筛选光影和资源包，否则原版光影会因为是资源包格式而被过滤
            Case Else
                Results = Results.Where(Function(r) r.ResourceType = TargetResourceType).ToList
        End Select
        Return Results
    End Function
    Private Sub Load_OnFinish()
        Dim Results = GetResults()

        '初始化筛选器
        Dim Filters As List(Of String) = Nothing
        Dim UpdateFilters =
        Sub()
            Filters = Results.SelectMany(Function(v) v.GameVersions).Select(Function(v) GetGroupedVersionName(v, GroupedDrop, GroupedOld)).
                Distinct.OrderByDescending(Function(s) s, Comparer(Of String).Create(Function(x, y) McUtils.CompareVersion(x, y))).ToList
        End Sub

        '确定分组方式
        GroupedDrop = False : GroupedOld = False
        UpdateFilters()
        If Filters.Count >= 9 Then
            GroupedDrop = True : GroupedOld = False
            UpdateFilters()
            If Filters.Count >= 9 Then
                GroupedDrop = False : GroupedOld = True
                UpdateFilters()
                If Filters.Count >= 9 Then
                    GroupedDrop = True : GroupedOld = True
                    UpdateFilters()
                End If
            End If
        End If

        'UI 化筛选器
        PanFilter.Children.Clear()
        If Filters.Count < 2 Then
            CardFilter.Visibility = Visibility.Collapsed
            VersionFilter = Nothing
        Else
            CardFilter.Visibility = Visibility.Visible
            Filters.Insert(0, ModLang.L("全部"))
            '转化为按钮
            For Each Version As String In Filters
                Dim NewButton As New MyRadioButton With {
                    .Text = Version, .Margin = New Thickness(2, 0, 2, 0), .ColorType = MyRadioButton.ColorState.Highlight}
                NewButton.LabText.Margin = New Thickness(-2, 0, 10, 0)
                AddHandler NewButton.Check,
                Sub(sender As MyRadioButton, raiseByMouse As Boolean)
                    VersionFilter = If(sender.Text = ModLang.L("全部"), Nothing, sender.Text)
                    UpdateFilterResult()
                End Sub
                PanFilter.Children.Add(NewButton)
            Next
            '自动选择
            Dim ToCheck As MyRadioButton = Nothing
            If TargetVersion <> "" Then
                Dim TargetFile = Results.FirstOrDefault(Function(v) v.GameVersions.Contains(TargetVersion))
                If TargetFile IsNot Nothing Then
                    Dim TargetGroup = GetGroupedVersionName(TargetVersion, GroupedDrop, GroupedOld)
                    For Each Button As MyRadioButton In PanFilter.Children
                        If Button.Text <> TargetGroup Then Continue For
                        ToCheck = Button
                        Exit For
                    Next
                End If
            End If
            If ToCheck Is Nothing Then ToCheck = PanFilter.Children(0)
            ToCheck.Checked = True
        End If

        '更新筛选结果（文件列表 UI 化）
        UpdateFilterResult()
    End Sub
    Private Sub UpdateFilterResult()
        Dim Results = GetResults()

        Dim TargetCardName As String = If(TargetVersion <> "" OrElse TargetLoaders <> ModLoaders.None,
            $"{If(TargetLoaders <> ModLoaders.None, TargetLoaders.ToString + " ", "")}{TargetVersion}（所选版本）", "")
        '归类到卡片下
        Dim Dict As New SortedDictionary(Of String, List(Of ResourceVersion))(New CardSorter(TargetCardName))
        Dict.Add("其他", New List(Of ResourceVersion))
        For Each Version As ResourceVersion In Results
            For Each GameVersion In Version.GameVersions
                '检查是否符合版本筛选器
                If VersionFilter IsNot Nothing AndAlso
                    GetGroupedVersionName(GameVersion, GroupedDrop, GroupedOld) <> VersionFilter Then Continue For
                '决定添加到哪个卡片
                Dim VerName As String = GetGroupedVersionName(GameVersion, False, False)
                '遍历加入的加载器列表
                Dim Loaders As New List(Of String)
                If Project.ModLoaders.Flags.Count > 1 AndAlso '工程至少有两个加载器
                    Version.ResourceType = ResourceTypes.Mod AndAlso '是 Mod
                    McVersion.IsStandardFormat(VerName) Then '不是 “快照版本” 之类的
                    Loaders.AddRange(Version.ModLoaders.Flags.Select(Function(f) f.ToString & " "))
                End If
                If Not Loaders.Any() Then Loaders.Add("") '保底加一个空的，确保它在一张卡片里
                '实际添加
                For Each Loader In Loaders
                    Dim TargetCard As String = Loader & VerName
                    If Not Dict.ContainsKey(TargetCard) Then Dict.Add(TargetCard, New List(Of ResourceVersion))
                    If Not Dict(TargetCard).Contains(Version) Then Dict(TargetCard).Add(Version)
                Next
            Next
        Next
        '添加筛选的版本的卡片
        If TargetCardName <> "" AndAlso (VersionFilter Is Nothing OrElse GetGroupedVersionName(TargetVersion, GroupedDrop, GroupedOld).StartsWithF(VersionFilter)) Then
            Dict.Add(TargetCardName, New List(Of ResourceVersion))
            For Each Version As ResourceVersion In Results
                If Version.GameVersions.Contains(TargetVersion) AndAlso
                   (TargetLoaders = ModLoaders.None OrElse TargetLoaders.Flags.Intersect(Version.ModLoaders.Flags).Any) Then
                    '检查是否符合版本筛选器
                    If VersionFilter IsNot Nothing AndAlso
                        Not Version.GameVersions.Any(Function(v) GetGroupedVersionName(v, GroupedDrop, GroupedOld) = VersionFilter) Then Continue For
                    If Not Dict(TargetCardName).Contains(Version) Then Dict(TargetCardName).Add(Version)
                End If
            Next
        End If
        '转化为 UI
        Try
            PanResults.Children.Clear()
            For Each Pair As KeyValuePair(Of String, List(Of ResourceVersion)) In Dict
                If Not Pair.Value.Any() Then Continue For
                If Pair.Key = TargetCardName.Replace("（所选版本）", "") Then Continue For
                '增加卡片
                Dim NewCard As New MyCard With {.Title = Pair.Key, .Margin = New Thickness(0, 0, 0, 15)}
                Dim NewStack As New StackPanel With {.Margin = New Thickness(20, MyCard.SwapedHeight, 18, 0), .VerticalAlignment = VerticalAlignment.Top, .RenderTransform = New TranslateTransform(0, 0), .Tag = Pair.Value}
                NewCard.Children.Add(NewStack)
                NewCard.SwapControl = NewStack
                NewCard.SetContentBuilder(AddressOf InitializeStackResourceVersions)
                PanResults.Children.Add(NewCard)
                '确定卡片是否默认展开
                If Pair.Key = TargetCardName OrElse
                   (FrmMain.PageCurrent.Additional IsNot Nothing AndAlso
                   CType(FrmMain.PageCurrent.Additional(1), List(Of String)).Contains(NewCard.Title)) Then
                    NewCard.BuildContent()
                Else
                    NewCard.IsSwapped = True
                End If
                '增加提示
                If Pair.Key = "其他" Then
                    NewStack.Children.Add(New MyHint With {.Text = ModLang.L("由于版本信息更新缓慢，可能无法识别刚更新的 MC 版本。几天后即可正常识别。"), .Theme = MyHint.Themes.Yellow, .Margin = New Thickness(5, 0, 0, 8)})
                End If
            Next
            '如果只有一张卡片，展开第一张卡片
            If PanResults.Children.Count = 1 Then
                CType(PanResults.Children(0), MyCard).IsSwapped = False
            End If
            '替代提示
            If Project.Types = ResourceTypes.ModOrDataPack AndAlso (TargetResourceType = ResourceTypes.Mod OrElse TargetResourceType = ResourceTypes.DataPack) Then
                HintAlternative.Visibility = Visibility.Visible
                HintAlternative.Text = If(TargetResourceType = ResourceTypes.Mod,
                    "以下是该项目的 Mod 版本。点击这里查看其数据包版本。", "以下是该项目的数据包版本。点击这里查看其 Mod 版本。")
            Else
                HintAlternative.Visibility = Visibility.Collapsed
            End If
        Catch ex As Exception
            Logger.Error(ex, "可视化工程下载列表出错")
        End Try
    End Sub
    ''' <summary>
    ''' 根据卡片中的 StackPanel.Tag 将资源版本列表数据实例化为 UI。
    ''' </summary>
    Private Sub InitializeStackResourceVersions(Stack As StackPanel)
        Dim Versions = CType(Stack.Tag, List(Of ResourceVersion)).SortByComparison(Function(a, b) a.ReleaseDate > b.ReleaseDate)
        Dim HasDuplicates = Versions.DistinctBy(Function(a) a.Display).Count <> Versions.Count
        Dim Dependences As List(Of String)
        '获取并添加前置信息
        If TargetResourceType = ResourceTypes.ModPack Then
            Dependences = New List(Of String) '整合包的前置信息会显示其中包含的一大堆 Mod，不是真正意义的前置
        Else
            Dependences = Versions.SelectMany(Function(f) f.Dependencies).Distinct.Ordered.ToList()
        End If
        If Dependences.Any() Then
            Dependences = Dependences.Where(
            Function(d)
                If Not ResourceProject.Cache.ContainsKey(d) Then Logger.Warn($"未找到 ID {d} 的前置信息")
                Return ResourceProject.Cache.ContainsKey(d)
            End Function).ToList
            '添加开头间隔
            Stack.Children.Add(New TextBlock With {.Text = ModLang.L("前置资源"), .FontSize = 14, .HorizontalAlignment = HorizontalAlignment.Left, .Margin = New Thickness(6, 2, 0, 5)})
            '添加前置列表
            For Each Dep In Dependences
                Dim Item = ResourceProject.Cache(Dep).ToResourceItem(False, False)
                Stack.Children.Add(Item)
            Next
            '添加结尾间隔
            Stack.Children.Add(New TextBlock With {.Text = ModLang.L("版本列表"), .FontSize = 14, .HorizontalAlignment = HorizontalAlignment.Left, .Margin = New Thickness(6, 12, 0, 5)})
        End If
        '实例化每个元素
        For Each Version In Versions
            If TargetResourceType = ResourceTypes.ModPack Then
                Stack.Children.Add(Version.ToListItem(AddressOf Install_Click, AddressOf Save_Click, IsBadDisplay:=HasDuplicates))
            Else
                Stack.Children.Add(Version.ToListItem(AddressOf Save_Click, IsBadDisplay:=HasDuplicates))
            End If
        Next
        Stack.Children.Add(New FrameworkElement With {.Height = 18})
        Stack.Tag = Nothing
    End Sub
    Private Function GetGroupedVersionName(Name As String, GroupedByDrop As Boolean, FoldOld As Boolean) As String
        Dim Drop = McVersion.VersionToDrop(Name)
        If Name Is Nothing Then
            Return ModLang.L("其他")
        ElseIf Name.Contains("w") OrElse Drop = 209 Then
            Return ModLang.L("快照版")
        ElseIf Not McVersion.IsStandardFormat(Name) OrElse (FoldOld AndAlso Drop < 120) Then
            Return ModLang.L("远古版")
        ElseIf GroupedByDrop Then
            Return McVersion.DropToVersion(Drop)
        Else
            Return Name
        End If
    End Function

#End Region

    Private IsFirstInit As Boolean = True
    Public Sub Init() Handles Me.PageEnter
        AniControlEnabled += 1
        Project = FrmMain.PageCurrent.Additional(0)
        PanBack.ScrollToHome()

        '重启加载器
        If IsFirstInit Then
            '在 Me.Initialized 已经初始化了加载器，不再重复初始化
            IsFirstInit = False
        Else
            PageLoaderRestart(IsForceRestart:=True)
        End If

        '放置当前工程
        If ResourceItem IsNot Nothing Then PanIntro.Children.Remove(ResourceItem)
        ResourceItem = Project.ToResourceItem(True, True).Init()
        ResourceItem.CanInteraction = False
        ResourceItem.Margin = New Thickness(-7, -7, 0, 8)
        PanIntro.Children.Insert(0, ResourceItem)

        '决定按钮显示
        BtnIntroWeb.Text = ModLang.L("转到 ") & Project.Platform
        BtnIntroWiki.Visibility = If(Project.WikiId = 0, Visibility.Collapsed, Visibility.Visible)
        UpdateStarButton()

        AniControlEnabled -= 1
    End Sub

    '整合包安装
    Public Sub Install_Click(sender As MyListItem, e As EventArgs)
        Try

            '获取基本信息
            Dim File As ResourceVersion = sender.Tag
            Dim LoaderName As String = $"{Project.Platform} 整合包下载：{Project.TranslatedName} "

            '获取版本名
            Dim PackName As String = Project.TranslatedName.Replace(".zip", "").Replace(".rar", "").Replace(".mrpack", "").Replace("\", "＼").Replace("/", "／").Replace("|", "｜").Replace(":", "：").Replace("<", "＜").Replace(">", "＞").Replace("*", "＊").Replace("?", "？").Replace("""", "").Replace("： ", "：")
            Dim Validate As New ValidateFolderName(McFolderSelected & "versions")
            If Validate.Validate(PackName) <> "" Then PackName = ""
            Dim InstanceName As String = MyMsgBoxInput(ModLang.L("输入版本名称"), "", PackName, New Collection(Of Validate) From {Validate})
            If String.IsNullOrEmpty(InstanceName) Then Return

            '构造步骤加载器
            Dim Loaders As New List(Of LoaderBase)
            Dim Target As String = $"{McFolderSelected}versions\{InstanceName}\原始整合包.{If(Project.Platform = ResourcePlatforms.CurseForge, "zip", "mrpack")}"
            Dim LogoFileAddress As String = ResourceItem.PathLogo.ActualSource
            Loaders.Add(CreateFileDownloadStep("下载整合包文件", File.DownloadUrls, Target, 10))
            Loaders.Add(New LoaderTask(Of Integer, Integer)("准备安装整合包",
            Sub() ModpackInstall(Target, InstanceName, If(FileUtils.Exists(LogoFileAddress), LogoFileAddress, Nothing))) With {.ProgressWeight = 0.1})

            '启动
            Dim Loader As New LoaderCombo(Of String)(LoaderName, Loaders) With {.OnStateChanged =
            Sub(MyLoader)
                Select Case MyLoader.State
                    Case LoadState.Failed
                        Hint(MyLoader.Name & ModLang.L("失败：") & MyLoader.Error.GetDisplay(False), HintType.Red)
                    Case LoadState.Canceled
                        Hint(MyLoader.Name & ModLang.L("已取消！"), HintType.Blue)
                    Case LoadState.Loading
                        Return '不重新加载版本列表
                End Select
                McInstallFailedClearFolder(MyLoader)
            End Sub}
            Loader.Start(McFolderSelected & "versions\" & InstanceName & "\")
            LoaderTaskbarAdd(Loader)
            FrmMain.BtnExtraDownload.ShowRefresh()
            FrmMain.PageChange(FormMain.PageType.TaskManager)

        Catch ex As Exception
            Logger.Error(ex, "下载资源整合包失败")
        End Try
    End Sub
    '资源下载；整合包另存为
    Public Shared CachedFolder As New Dictionary(Of ResourceTypes, String) '仅在本次缓存的下载文件夹
    Public Sub Save_Click(sender As Object, e As EventArgs)
        Dim File As ResourceVersion = If(TypeOf sender Is MyListItem, sender, sender.Parent).Tag
        RunInNewThread(
        Sub()
            Try
                '展示名与落盘子目录统一走 ResourceKit（避免各页面各自重写映射）
                Dim Desc As String = ResourceKit.DisplayName(File.ResourceType)
                '确认默认保存位置
                Dim DefaultFolder As String = Nothing
                If File.ResourceType <> ResourceTypes.ModPack Then
                    Dim SubFolder As String = ResourceKit.SaveSubFolder(File.ResourceType) '数据包落到版本根目录（空串）
                    Dim IsInstanceSuitable As Func(Of McInstance, Boolean) = Nothing
                    '获取资源所需的加载器
                    Dim AllowedLoaders As ModLoaders = ModLoaders.None
                    If File.ModLoaders <> ModLoaders.None Then
                        AllowedLoaders = File.ModLoaders
                    ElseIf Project.ModLoaders <> ModLoaders.None Then
                        AllowedLoaders = Project.ModLoaders
                    End If
                    Logger.Info($"{Desc}要求的加载器种类：{AllowedLoaders}")
                    '判断某个版本是否符合资源要求
                    IsInstanceSuitable =
                    Function(Instance As McInstance)
                        If Not Instance.IsLoaded Then Instance.Load()
                        '只对 Mod 和数据包进行版本检测（兼容性判定统一走 ResourceKit，任一支持版本匹配即可用）
                        If (File.ResourceType = ResourceTypes.Mod OrElse File.ResourceType = ResourceTypes.DataPack) AndAlso
                            Not ResourceKit.IsVersionCompatible(File.GameVersions, Instance.Version.VanillaName, Instance.Version.Drop) Then
                            Return False
                        End If
                        '加载器
                        Return AllowedLoaders = ModLoaders.None OrElse AllowedLoaders.Flags.Intersect(Instance.Version.ModLoaders.Flags).Any
                    End Function
                    '获取常规资源默认下载位置
                    If CachedFolder.ContainsKey(File.ResourceType) Then
                        DefaultFolder = CachedFolder(File.ResourceType)
                        Logger.Info($"使用上次下载时的文件夹作为默认下载位置：{DefaultFolder}")
                    ElseIf McInstanceSelected IsNot Nothing AndAlso IsInstanceSuitable(McInstanceSelected) Then
                        DefaultFolder = $"{McInstanceSelected.PathIndie}{SubFolder}"
                        DirectoryUtils.Create(DefaultFolder)
                        Logger.Info($"使用当前版本作为默认下载位置：{DefaultFolder}")
                    Else
                        '查找所有可能的版本
                        Dim NeedLoad As Boolean = McInstanceListLoader.State <> LoadState.Finished
                        If NeedLoad Then
                            Hint(ModLang.L("正在查找适合的游戏版本……"))
                            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\", WaitForExit:=True)
                        End If
                        Dim SuitableInstanceList = McInstanceList.Values.SelectMany(Function(l) l).Where(Function(v) IsInstanceSuitable(v)).
                            Select(Function(v) DirectoryUtils.GetInfo($"{v.PathIndie}{SubFolder}"))
                        If SuitableInstanceList.Any Then
                            Dim SelectedInstance = SuitableInstanceList.
                                OrderByDescending(Function(Dir) If(Dir.Exists, Dir.LastWriteTimeUtc, Date.MinValue)). '先按文件夹更改时间降序
                                ThenByDescending(Function(Dir) If(Dir.Exists, Dir.GetFiles().Length, -1)). '再按文件夹中的文件数量降序
                                First()
                            DefaultFolder = PathUtils.RemoveExtendedPrefix(SelectedInstance.FullName)
                            DirectoryUtils.Create(DefaultFolder)
                            Logger.Info($"使用适合的游戏版本作为默认下载位置：{DefaultFolder}")
                        Else
                            DefaultFolder = McFolderSelected
                            If NeedLoad Then
                                Hint(ModLang.L("当前 MC 文件夹中没有找到合适的版本！"))
                            Else
                                Logger.Info("由于当前版本不兼容，使用当前的 MC 文件夹作为默认下载位置")
                            End If
                        End If
                    End If
                End If
                'C3：必需前置检查（仅 Mod）。C：递归化（对照 PCL-CE ModDependencyResolver）——
                '前置的前置也会被解析补全（深度 32、按平台+ID 访问去重、已装即视为满足），不再只查一层
                Dim DepFiles As New List(Of Tuple(Of String, ResourceVersion)) '(前置显示名, 版本)
                Dim UnresolvedNames As New List(Of String) '无法自动解析的前置名（弹窗里提示手动下载）
                If File.ResourceType = ResourceTypes.Mod AndAlso File.Dependencies.Any Then
                    Try
                        CollectMissingDependencies(File, Project, DefaultFolder, New HashSet(Of String), DepFiles, UnresolvedNames, 0)
                        If DepFiles.Any OrElse UnresolvedNames.Any Then
                            Logger.Info($"必需前置检查完成：{DepFiles.Count} 个可自动下载，{UnresolvedNames.Count} 个无法解析")
                        End If
                    Catch ex As Exception
                        Logger.Warn(ex, "检查 Mod 必需前置失败，跳过检查")
                    End Try
                End If
                '获取基本信息
                Dim FileName As String
                If Project.TranslatedName = Project.RawName Then
                    FileName = File.FileName
                Else
                    Dim ChineseName As String = Project.TranslatedName.BeforeFirst(" (").BeforeFirst(" - ").
                        Replace("\", "＼").Replace("/", "／").Replace("|", "｜").Replace(":", "：").Replace("<", "＜").Replace(">", "＞").Replace("*", "＊").Replace("?", "？").Replace("""", "").Replace("： ", "：")
                    Select Case Settings.Get(Of Integer)("ToolDownloadTranslateV2")
                        Case 0
                            FileName = $"【{ChineseName}】{File.FileName}"
                        Case 1
                            FileName = $"[{ChineseName}] {File.FileName}"
                        Case 2
                            FileName = $"{ChineseName}-{File.FileName}"
                        Case 3
                            FileName = $"{File.FileName}-{ChineseName}"
                        Case Else
                            FileName = File.FileName
                    End Select
                End If
                If File.ResourceType = ResourceTypes.Mod Then FileName = FileName.Replace("~", "-") '~ 会导致 Mixin 加载失败
                RunInUi(
                Sub()
                    '弹窗要求选择保存位置
                    Dim Target As String
                    Dim Ext As String = If(File.ResourceType = ResourceTypes.Mod,
                        If(PathUtils.GetExtension(File.FileName) = "litemod", "litemod", "jar"),
                        If(PathUtils.GetExtension(File.FileName) = "mrpack", "mrpack", "zip"))
                    Target = Dialogs.SaveFile("选择保存位置", FileName, DefaultFolder, {(Ext, Desc & "文件")})
                    If Target Is Nothing Then Return
                    '发现缺失前置时询问是否一并下载
                    If DepFiles.Any Then
                        Dim DepListText = DepFiles.Select(Function(D) D.Item1).Join("、")
                        Dim UnresolvedText = If(UnresolvedNames.Any,
                            ModLang.L("；另有以下前置无法自动解析，需要手动下载：") & UnresolvedNames.Join("、"), "")
                        Dim DepChoice = MyMsgBox(
                            ModLang.L("该资源需要以下前置，但在目标文件夹中未发现它们：") & vbCrLf & DepListText & UnresolvedText & vbCrLf &
                            ModLang.L("是否一并下载这些前置？"),
                            ModLang.L("发现缺失的前置"), ModLang.L("一并下载"), ModLang.L("仅下载原资源"), ModLang.L("取消"))
                        Select Case DepChoice
                            Case 1 '一并下载
                            Case 2 '仅下载原资源
                                DepFiles = New List(Of Tuple(Of String, ResourceVersion))
                            Case Else '取消整个下载
                                Return
                        End Select
                    ElseIf UnresolvedNames.Any Then
                        '只有无法解析的前置：提示但不阻塞下载本体
                        Hint(ModLang.L("以下前置无法自动解析，游戏可能无法运行，请手动下载：") & UnresolvedNames.Join("、"), HintType.Red)
                    End If
                    '构造步骤加载器
                    Dim LoaderName As String = Desc & "下载：" & PathUtils.GetFileNameWithoutExtension(Target) & " "
                    If Not PathUtils.IsSame(PathUtils.RemoveLastPart(Target), DefaultFolder) Then CachedFolder(File.ResourceType) = PathUtils.RemoveLastPart(Target)
                    Dim Loaders As New List(Of LoaderBase)
                    Loaders.Add(CreateFileDownloadStep("下载文件", File.DownloadUrls, Target, 6))
                    '一并下载缺失的前置
                    If DepFiles.Any Then
                        Dim DepNetFiles = DepFiles.Select(
                        Function(D)
                            Dim DepTarget = IO.Path.Combine(PathUtils.RemoveLastPart(Target), D.Item2.FileName)
                            Return D.Item2.ToNetFile(DepTarget, ResourceVersion.DownloadReason.Dependency)
                        End Function).ToList
                        If ModTls.Win7TlsMode Then
                            'Win7：SChannel 与 Cloudflare 系无法协商 TLS → 前置文件走内置 TLS 客户端逐个下载
                            Loaders.Add(New LoaderTask(Of Integer, Integer)("下载前置文件",
                            Sub(MyTask As LoaderTask(Of Integer, Integer))
                                For Each DepFile In DepFiles
                                    Dim DepTargetInner = IO.Path.Combine(PathUtils.RemoveLastPart(Target), DepFile.Item2.FileName)
                                    ModTls.TlsDownload(DepFile.Item2.DownloadUrls, DepTargetInner)
                                Next
                            End Sub) With {.ProgressWeight = 3, .Block = True})
                        Else
                            Loaders.Add(New LoaderDownload("下载前置文件", DepNetFiles) With {.ProgressWeight = 3, .Block = True})
                        End If
                    End If
                    '世界下载后需解压到 saves\ 再删除压缩包，否则 MC 无法直接使用该 zip 存档（对齐主流启动器的世界安装流程）
                    Dim ExtL As String = If(PathUtils.GetExtension(File.FileName), "").Lower
                    If File.ResourceType = ResourceTypes.Map AndAlso (ExtL = "zip" OrElse ExtL = "mca") Then
                        Dim ExtractDir As String = PathUtils.RemoveLastPart(Target)
                        Loaders.Add(New LoaderTask(Of Integer, Integer)("安装世界存档",
                        Sub(MyTask As LoaderTask(Of Integer, Integer))
                            FileUtils.ExtractToDirectory(Target, ExtractDir, MyTask.CreateCancellationToken())
                            If FileUtils.Exists(Target) Then FileUtils.Delete(Target)
                        End Sub) With {.ProgressWeight = 0.1, .Block = True})
                    End If
                    '启动
                    Dim Loader As New LoaderCombo(Of Integer)(LoaderName, Loaders) With {.OnStateChanged =
                    Sub(MyLoader)
                        LoaderStateChangedHintOnly(MyLoader)
                        If MyLoader.State = LoadState.Finished AndAlso File.ResourceType = ResourceTypes.ModPack Then OpenExplorer(Target)
                    End Sub}
                    '完成后打开文件所在文件夹（任务中心 C1 收尾）：世界压缩包会解压到 saves\ 目录，故该目录即解压结果；
                    '整合包分支已在 OnStateChanged 中打开文件本身，不再重复挂回调
                    If File.ResourceType <> ResourceTypes.ModPack Then
                        Loader.OnFinishedAction = Sub() OpenExplorer(PathUtils.RemoveLastPart(Target))
                    End If
                    Loader.Start(1)
                    LoaderTaskbarAdd(Loader)
                    FrmMain.BtnExtraDownload.ShowRefresh()
                    FrmMain.BtnExtraDownload.Ribble()
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "保存资源文件失败")
            End Try
        End Sub, "Download Resource Detail Save")
    End Sub

    Private Sub BtnIntroWeb_Click(sender As Object, e As EventArgs) Handles BtnIntroWeb.Click
        OpenWebsite(Project.Website)
    End Sub
    Private Sub BtnIntroWiki_Click(sender As Object, e As EventArgs) Handles BtnIntroWiki.Click
        '独立化：不再依赖第三方 MC 百科站，改为官方 Minecraft 维基（按 slug 查条目）
        OpenWebsite("https://minecraft.wiki/w/" & Project.Slug)
    End Sub
    Private Sub BtnIntroCopy_Click(sender As Object, e As EventArgs) Handles BtnIntroCopy.Click
        ClipboardSet(ResourceItem.LabTitle.Text & ResourceItem.LabTitleRaw.Text)
    End Sub
    Private Sub BtnIntroStar_Click(sender As Object, e As EventArgs) Handles BtnIntroStar.Click
        Try
            If ModResourceFavorites.FavoritesContains(Project) Then
                ModResourceFavorites.FavoritesRemove(Project)
                Hint(ModLang.L("已取消收藏！"), HintType.Green)
            Else
                ModResourceFavorites.FavoritesAdd(Project)
                Hint(ModLang.L("已加入收藏夹！"), HintType.Green)
            End If
            UpdateStarButton()
        Catch ex As Exception
            Logger.Error(ex, "切换收藏状态失败", LogBehavior.Toast)
        End Try
    End Sub
    Private Sub UpdateStarButton()
        BtnIntroStar.Text = If(ModResourceFavorites.FavoritesContains(Project), ModLang.L("取消收藏"), ModLang.L("收藏"))
    End Sub

    'Mod / 数据包 互相跳转
    Private Sub HintAlternative_Click() Handles HintAlternative.Click
        TargetResourceType = If(TargetResourceType = ResourceTypes.Mod, ResourceTypes.DataPack, ResourceTypes.Mod)
        FrmMain.PageCurrent.Additional(4) = TargetResourceType '加载器会从这里重新拿数据
        PageLoaderRestart(IsForceRestart:=True)
    End Sub

    'C3：前置依赖检查
    ''' <summary>
    ''' 通过文件名相似度判断前置是否已存在于目标文件夹（宽松匹配：宁可漏判，不误杀）。
    ''' </summary>
    Private Shared Function IsDependencyInstalled(Folder As String, Dep As ResourceProject) As Boolean
        Try
            If String.IsNullOrEmpty(Folder) OrElse Not DirectoryUtils.Exists(Folder) Then Return False
            Dim Keys As New List(Of String)
            For Each Raw In {Dep.Slug, Dep.RawName}
                If String.IsNullOrEmpty(Raw) Then Continue For
                Dim Key = String.Concat(Raw.ToLower().Where(Function(Ch) Char.IsLetterOrDigit(Ch)))
                If Key.Length >= 4 Then Keys.Add(Key) '过短的键容易误匹配
            Next
            If Not Keys.Any() Then Return False
            Return DirectoryUtils.EnumerateFiles(Folder, False).Any(
            Function(F)
                Dim Name = String.Concat(IO.Path.GetFileName(F).ToLower().Where(Function(Ch) Char.IsLetterOrDigit(Ch)))
                Return Keys.Any(Function(K) Name.Contains(K))
            End Function)
        Catch ex As Exception
            Logger.Warn(ex, "检查前置是否已安装失败")
            Return False
        End Try
    End Function

    ''' <summary>
    ''' 为缺失的前置挑选一个适用版本（C 对齐 PCL-CE SelectBestFile：
    ''' 发行类型分级 Release &gt; Beta &gt; Alpha，同级别取发布时间最新者；
    ''' 之前只认正式版，导致不少前置解析失败被跳过）。
    ''' 已知目标实例时按实例的游戏版本与加载器筛选；否则退化为要求与主文件支持同一游戏版本。
    ''' </summary>
    Private Shared Function SelectDependencyVersion(Dep As ResourceProject, MainFile As ResourceVersion, TargetFolder As String) As ResourceVersion
        Dim Versions = ResourceVersion.FromProjectId(Dep.Id, Dep.Platform)
        '按目标文件夹反查所属实例
        Dim InstanceVanilla As String = Nothing, InstanceDrop As Integer = 0, InstanceLoaders As ModLoaders = ModLoaders.None
        Dim MatchedInstance = McInstanceList.Values.SelectMany(Function(l) l).FirstOrDefault(
        Function(V) PathUtils.IsSame($"{V.PathIndie}{ResourceKit.SaveSubFolder(ResourceTypes.Mod)}", TargetFolder))
        If MatchedInstance IsNot Nothing Then
            If Not MatchedInstance.IsLoaded Then MatchedInstance.Load()
            InstanceVanilla = MatchedInstance.Version.VanillaName
            InstanceDrop = MatchedInstance.Version.Drop
            InstanceLoaders = MatchedInstance.Version.ModLoaders
        End If
        '没有实例时退化为按主文件声明的加载器过滤
        Dim EffectiveLoaders = If(InstanceLoaders <> ModLoaders.None, InstanceLoaders, MainFile.ModLoaders)
        Return Versions.Where(Function(V) V.DownloadAvailable).
            Where(Function(V) V.ModLoaders = ModLoaders.None OrElse V.ModLoaders.Flags.Intersect(EffectiveLoaders.Flags).Any).
            Where(Function(V)
                      If InstanceVanilla Is Nothing Then
                          Return V.GameVersions.Intersect(MainFile.GameVersions).Any '没有实例：至少与主文件支持同一游戏版本
                      Else
                          Return ResourceKit.IsVersionCompatible(V.GameVersions, InstanceVanilla, InstanceDrop)
                      End If
                  End Function).
            OrderBy(Function(V) ReleaseTypeRank(V.ReleaseType)). '发行类型分级（对照 PCL-CE NormalizeReleaseType）
            ThenByDescending(Function(V) V.ReleaseDate).
            FirstOrDefault()
    End Function

    ''' <summary>发行类型分级：Release=0 &lt; Beta=1 &lt; Alpha=2 &lt; 其他（对照 PCL-CE NormalizeReleaseType）。</summary>
    Private Shared Function ReleaseTypeRank(Type As ResourceVersion.ReleaseTypes) As Integer
        Select Case Type
            Case ResourceVersion.ReleaseTypes.Release : Return 0
            Case ResourceVersion.ReleaseTypes.Beta : Return 1
            Case ResourceVersion.ReleaseTypes.Alpha : Return 2
            Case Else : Return 3
        End Select
    End Function

    ''' <summary>
    ''' 递归收集缺失的必需前置（C，对照 PCL-CE ModDependencyResolver.Resolve：
    ''' 深度上限 32、按「平台:ID」访问去重、已装即视为满足、命中后继续递归其前置的前置）。
    ''' 结果写入 <paramref name="DepFiles"/>（可自动下载，按发现顺序）与 <paramref name="UnresolvedNames"/>（仅记录名称）。
    ''' </summary>
    Private Shared Sub CollectMissingDependencies(Version As ResourceVersion, Project As ResourceProject, TargetFolder As String,
                                                  Visited As HashSet(Of String), DepFiles As List(Of Tuple(Of String, ResourceVersion)),
                                                  UnresolvedNames As List(Of String), Depth As Integer)
        If Depth > 32 Then Return '对照 PCL-CE MaxDepth，防循环依赖
        For Each DepId In Version.Dependencies
            If String.IsNullOrWhiteSpace(DepId) Then Continue For
            Dim Key = $"{Project.Platform}:{DepId}"
            If Not Visited.Add(Key) Then Continue For '访问去重（递归链 + 跨分支共享）
            Try
                Dim DepProject = ResourceProject.FromProjectId(DepId, Project.Platform)
                If DepProject Is Nothing Then
                    Logger.Warn($"前置项目 {DepId} 无法获取，视为无法解析")
                    UnresolvedNames.Add(DepId)
                    Continue For
                End If
                If IsDependencyInstalled(TargetFolder, DepProject) Then Continue For '已装即视为满足（对照 PCL-CE IsInstalledCompatible）
                Dim DepVersion = SelectDependencyVersion(DepProject, Version, TargetFolder)
                If DepVersion Is Nothing Then
                    Logger.Info($"前置 {If(DepProject.TranslatedName, DepProject.RawName)} 没有适用于目标的版本，视为无法解析")
                    UnresolvedNames.Add(If(DepProject.TranslatedName, DepProject.RawName))
                    Continue For
                End If
                If Not DepFiles.Any(Function(D) D.Item2.Id = DepVersion.Id) Then
                    DepFiles.Add(Tuple.Create(If(DepProject.TranslatedName, DepProject.RawName), DepVersion))
                End If
                '递归解析该前置自己的必需前置
                CollectMissingDependencies(DepVersion, DepProject, TargetFolder, Visited, DepFiles, UnresolvedNames, Depth + 1)
            Catch ex As Exception
                Logger.Warn(ex, $"解析前置失败，已跳过：{DepId}")
            End Try
        Next
    End Sub

End Class
