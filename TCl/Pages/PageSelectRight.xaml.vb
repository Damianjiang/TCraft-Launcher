Imports System.IO
Imports System.Text.RegularExpressions
Imports System.Windows.Threading

''' <summary>
''' 实例管理页（右侧）。
''' 布局对齐 HMCL 的实例管理：顶部一行式工具栏（搜索 / 排序 / 刷新 / 新建 / 导入）+ 紧凑的扁平实例列表，
''' 每项显示「实例图标 + 实例名 + 版本与加载器 + 最后游玩」，保留 TCl 原有的收藏夹与分类（以分组标题呈现，不做折叠卡片）。
''' </summary>
Public Class PageSelectRight
    Implements IRefreshable

#Region "窗口基础"

    Private Sub PageSelectRight_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        InitToolbar()
        InitViewMode()
        LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.RunOnUpdated, MaxDepth:=1, ExtraPath:="versions\")
        PanBack.ScrollToHome()
    End Sub
    Private Sub LoaderInit() Handles Me.Initialized
        PageLoaderInit(Load, PanLoad, PanAllBack, Nothing, McInstanceListLoader, AddressOf McInstanceListUI, AutoRun:=False)
    End Sub
    Private Sub Load_Click(sender As Object, e As MouseButtonEventArgs) Handles Load.Click
        If McInstanceListLoader.State = LoadState.Failed Then
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
        End If
    End Sub

    ''' <summary>
    ''' 是否显示隐藏的 Minecraft 版本。
    ''' </summary>
    Public ShowHidden As Boolean = False

#End Region

#Region "工具栏"

    Private _ToolbarReady As Boolean = False
    Private _Search As String = ""
    Private _SearchTimer As DispatcherTimer = Nothing
    Private _Sort As Integer = 0

    '排序方式（与下拉框顺序一致）
    Private Const SortRecentPlay As Integer = 0
    Private Const SortName As Integer = 1
    Private Const SortVersion As Integer = 2
    Private Const SortCreateTime As Integer = 3

    Private Sub InitToolbar()
        _ToolbarReady = False
        Try
            Dim Saved As Integer = Settings.Get(Of Integer)("UiInstanceSortMode")
            If Saved < SortRecentPlay OrElse Saved > SortCreateTime Then Saved = SortRecentPlay
            ComboSort.SelectedIndex = Saved
            _Sort = Saved
        Catch ex As Exception
            Logger.Warn(ex, "恢复实例排序方式失败")
            ComboSort.SelectedIndex = SortRecentPlay
            _Sort = SortRecentPlay
        End Try
        _ToolbarReady = True
    End Sub

    Private Sub TxtSearch_TextChanged(sender As Object, e As EventArgs)
        If Not _ToolbarReady Then Return
        Dim NewText As String = If(TxtSearch.Text, "")
        If NewText = _Search AndAlso _SearchTimer Is Nothing Then Return
        _Search = NewText
        '输入防抖：停顿 220ms 再重建，避免逐字符重建上百个控件
        If _SearchTimer Is Nothing Then
            _SearchTimer = New DispatcherTimer With {.Interval = New TimeSpan(0, 0, 0, 0, 220)}
            AddHandler _SearchTimer.Tick,
                Sub()
                    _SearchTimer.Stop()
                    BuildList()
                End Sub
        End If
        _SearchTimer.Stop()
        _SearchTimer.Start()
    End Sub

    Private Sub ComboSort_SelectionChanged(sender As Object, e As SelectionChangedEventArgs)
        If Not _ToolbarReady Then Return
        If ComboSort.SelectedIndex < 0 Then Return
        If _Sort = ComboSort.SelectedIndex Then Return
        _Sort = ComboSort.SelectedIndex
        Settings.Set("UiInstanceSortMode", _Sort)
        BuildList()
    End Sub

    Private Sub BtnRefresh_Click(sender As Object, e As EventArgs)
        RefreshList(True)
    End Sub

    Private Sub BtnNew_Click(sender As Object, e As EventArgs)
        FrmMain.PageChange(New FormMain.PageStackData With {.Page = FormMain.PageType.Download}, FormMain.PageSubType.DownloadInstall)
    End Sub

    Private Sub BtnModpack_Click(sender As Object, e As EventArgs)
        ModpackInstall()
    End Sub

    Private Sub BtnFolder_Click(sender As Object, e As EventArgs)
        Try
            If String.IsNullOrEmpty(McFolderSelected) Then Return
            OpenExplorer(McFolderSelected)
        Catch ex As Exception
            Logger.Error(ex, "打开游戏目录失败", LogBehavior.Toast)
        End Try
    End Sub

    ''' <summary>
    ''' 刷新实例列表。Force 为 True 时丢弃缓存重新扫描版本目录。
    ''' </summary>
    Public Sub RefreshList(Optional Force As Boolean = False)
        _LastPlay = Nothing '重算「最后游玩」
        If Force Then
            McInstanceListForceRefresh = True
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
        Else
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.RunOnUpdated, MaxDepth:=1, ExtraPath:="versions\")
        End If
    End Sub

#End Region

#Region "视图模式"

    ''' <summary>列表视图。</summary>
    Private Const ViewModeList As Integer = 0
    ''' <summary>网格视图。</summary>
    Private Const ViewModeGrid As Integer = 1

    ''' <summary>当前视图模式，0 = 列表，1 = 网格。存于 Settings 的 UiInstanceViewMode。</summary>
    Private _ViewMode As Integer = ViewModeList

    Private Sub InitViewMode()
        Try
            Dim Saved As Integer = Settings.Get(Of Integer)("UiInstanceViewMode")
            _ViewMode = If(Saved = ViewModeGrid, ViewModeGrid, ViewModeList)
        Catch ex As Exception
            Logger.Warn(ex, "恢复实例视图模式失败")
            _ViewMode = ViewModeList
        End Try
        'Click 由 XAML 挂接（与其他四个工具栏按钮一致）。
        '不能再在这里 AddHandler：本页是缓存页，每次切回都会重触发 Loaded → InitViewMode，
        '订阅会不断叠加，点一次视图切换就被执行偶数次 = 状态变回去 = 看起来「点了没用」。
        RefreshViewModeButton()
        ApplyViewMode()
    End Sub

    ''' <summary>列表 / 网格切换按钮。</summary>
    Private Sub BtnViewMode_Click(sender As Object, e As EventArgs)
        _ViewMode = If(_ViewMode = ViewModeGrid, ViewModeList, ViewModeGrid)
        Try
            Settings.Set("UiInstanceViewMode", _ViewMode)
        Catch ex As Exception
            Logger.Warn(ex, "保存实例视图模式失败")
        End Try
        RefreshViewModeButton()
        ApplyViewMode()
        BuildList()
    End Sub

    ''' <summary>按钮图标与提示随当前模式变化：网格模式下提示「切回列表」。</summary>
    Private Sub RefreshViewModeButton()
        If BtnViewMode Is Nothing Then Return
        If _ViewMode = ViewModeGrid Then
            BtnViewMode.SvgIcon = "lucide/layout-list"
            BtnViewMode.ToolTip = ModLang.L("切换为列表视图")
        Else
            BtnViewMode.SvgIcon = "lucide/grid-2x2"
            BtnViewMode.ToolTip = ModLang.L("切换为网格视图")
        End If
    End Sub

    Private Sub ApplyViewMode()
        If PanMain Is Nothing Then Return
        PanMain.Visibility = If(_ViewMode = ViewModeGrid, Visibility.Collapsed, Visibility.Visible)
        If PanGrid IsNot Nothing Then
            PanGrid.Visibility = If(_ViewMode = ViewModeGrid, Visibility.Visible, Visibility.Collapsed)
        End If
    End Sub

#End Region

#Region "合集标签栏"

    ''' <summary>当前生效的合集筛选（空字符串表示「全部」）。</summary>
    Private _CollectionFilter As String = ""

    ''' <summary>重建合集标签栏。没有合集时整条隐藏，不占版面。</summary>
    Public Sub RefreshCollectionBar()
        Try
            If PanCollectionBar Is Nothing Then Return
            PanCollectionBar.Children.Clear()
            Dim All = ModInstanceCollections.ListAll()
            If Not All.Any() Then
                ScrollCollection.Visibility = Visibility.Collapsed
                _CollectionFilter = ""
                Return
            End If
            If _CollectionFilter <> "" AndAlso Not All.Contains(_CollectionFilter) Then _CollectionFilter = ""
            ScrollCollection.Visibility = Visibility.Visible
            PanCollectionBar.Children.Add(BuildChip(ModLang.L("全部"), ""))
            For Each One In All
                PanCollectionBar.Children.Add(BuildChip(One, One))
            Next
        Catch ex As Exception
            Logger.Warn(ex, "构建合集标签栏失败")
        End Try
    End Sub

    ''' <summary>一个合集胶囊标签。Value 为空表示「全部」。</summary>
    Private Function BuildChip(Text As String, Value As String) As FrameworkElement
        Dim Selected As Boolean = (_CollectionFilter = Value)
        Dim Box As New Border With {
            .CornerRadius = New CornerRadius(13),
            .Padding = New Thickness(13, 4, 13, 5),
            .Margin = New Thickness(0, 0, 6, 0),
            .VerticalAlignment = VerticalAlignment.Center,
            .SnapsToDevicePixels = True
        }
        If Selected Then
            Box.SetResourceReference(Border.BackgroundProperty, "ColorBrush3")
        Else
            Box.SetResourceReference(Border.BackgroundProperty, "ColorBrushBg1")
        End If
        Dim Lab As New TextBlock With {.Text = Text, .FontSize = 12.5, .TextTrimming = TextTrimming.CharacterEllipsis, .MaxWidth = 160}
        If Selected Then
            Lab.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush8")
        Else
            Lab.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        End If
        Box.Child = Lab
        Box.Cursor = Cursors.Hand

        AddHandler Box.MouseLeftButtonUp,
            Sub()
                If _CollectionFilter = Value Then Return
                _CollectionFilter = Value
                RefreshCollectionBar()
                BuildList()
            End Sub

        '合集标签（不含「全部」）支持改名与删除
        If Value <> "" Then
            AddHandler Box.MouseRightButtonUp,
                Sub(sender, e)
                    MenuCard.Show(Box, {
                        New MenuCard.MenuCardRow("重命名合集…", "lucide/pencil", Sub() RenameCollection(Value)),
                        New MenuCard.MenuCardRow("删除合集", "lucide/trash-2", Sub() DeleteCollection(Value))})
                    e.Handled = True
                End Sub
        End If
        Return Box
    End Function

    Private Shared Sub RenameCollection(OldName As String)
        Try
            Dim NewName As String = MyMsgBoxInput(ModLang.L("重命名合集"), ModLang.L("所有实例上的这个标签会一起改。"), OldName)
            If String.IsNullOrWhiteSpace(NewName) Then Return
            NewName = NewName.Trim()
            If NewName = OldName Then Return
            Dim ErrorText As String = ModInstanceCollections.ValidateName(NewName, OldName)
            If ErrorText <> "" Then
                Hint(ErrorText, HintType.Red)
                Return
            End If
            ModInstanceCollections.Rename(OldName, NewName)
            FrmSelectRight?.RefreshCollectionBar()
            FrmSelectRight?.BuildListRefresh()
            Hint(ModLang.L("合集已重命名。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "重命名合集失败", LogBehavior.Alert)
        End Try
    End Sub

    Private Shared Sub DeleteCollection(Name As String)
        Try
            If MyMsgBox(ModLang.L("确定要删除合集「") & Name & ModLang.L("」吗？") & vbCrLf &
                        ModLang.L("只会删掉这个标签，实例本身与其文件不受影响。"),
                        ModLang.L("删除合集"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
            ModInstanceCollections.Delete(Name)
            If FrmSelectRight IsNot Nothing AndAlso FrmSelectRight._CollectionFilter = Name Then FrmSelectRight._CollectionFilter = ""
            FrmSelectRight?.RefreshCollectionBar()
            FrmSelectRight?.BuildListRefresh()
            Hint(ModLang.L("合集已删除。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "删除合集失败", LogBehavior.Alert)
        End Try
    End Sub

    ''' <summary>供外部在合集变动后重建列表。</summary>
    Public Sub BuildListRefresh()
        BuildList()
    End Sub

#End Region

#Region "列表构建"

    Private Const ItemHeight As Integer = 56
    Private Const ItemIconSize As Integer = 34

    Private _IsBuilding As Boolean = False
    ''' <summary>实例列表分页：每页渲染的实例数。</summary>
    Private Const InstancePageSize As Integer = 50
    ''' <summary>当前页码（0 起）。</summary>
    Private _CurrentPage As Integer = 0
    ''' <summary>实例路径 → 最后游玩时间（本地时间）。由后台线程算好后整体替换，读取端不加锁。</summary>
    Private Shared _LastPlay As Dictionary(Of String, Date) = Nothing
    Private Shared _LastPlayFolder As String = ""

    ''' <summary>分组顺序：收藏夹置顶，其余按版本类型排列。</summary>
    Private Shared Function GroupOrder() As McInstanceCardType()
        Return {McInstanceCardType.Star, McInstanceCardType.OriginalLike, McInstanceCardType.API,
                McInstanceCardType.Fool, McInstanceCardType.Rubbish, McInstanceCardType.Error, McInstanceCardType.Hidden}
    End Function

    Private Sub McInstanceListUI(Loader As LoaderTask(Of String, Integer))
        '首次进入某个游戏目录：先渲染不带游玩时间的列表（保证秒开），再后台算出时间后重绘
        If _LastPlay Is Nothing OrElse Not PathUtils.IsSame(_LastPlayFolder, McFolderSelected) Then
            _LastPlayFolder = McFolderSelected
            _LastPlay = New Dictionary(Of String, Date)(StringComparer.OrdinalIgnoreCase)
            BuildList()
            Dim FolderSnapshot As String = McFolderSelected
            Dim Instances As New List(Of McInstance)
            Try
                For Each Pair In McInstanceList.ToArray
                    If Pair.Value IsNot Nothing Then Instances.AddRange(Pair.Value)
                Next
            Catch ex As Exception
                Logger.Warn(ex, "收集实例快照失败")
            End Try
            If Not Instances.Any() Then Return
            RunInNewThread(
                Sub()
                    Dim Map As New Dictionary(Of String, Date)(StringComparer.OrdinalIgnoreCase)
                    For Each One In Instances
                        Try
                            Dim Sessions = ModInstanceHistory.LoadSessions(One)
                            If Sessions IsNot Nothing AndAlso Sessions.Count > 0 Then
                                Map(PathUtils.ForCompare(One.PathVersion)) = Sessions(0).StartLocal
                            End If
                        Catch ex As Exception
                            Logger.Warn(ex, $"读取最后游玩时间失败：{One.Name}")
                        End Try
                    Next
                    RunInUi(
                        Sub()
                            If FrmSelectRight Is Nothing Then Return
                            If Not PathUtils.IsSame(_LastPlayFolder, FolderSnapshot) Then Return
                            _LastPlay = Map
                            BuildList()
                        End Sub)
                End Sub, "Instance LastPlay")
            Return
        End If
        BuildList()
    End Sub

    ''' <summary>按当前搜索词与排序方式重建列表（分页渲染：每页 50 个，防大量实例卡死 UI）。</summary>
    Private Sub BuildList(Optional ResetPage As Boolean = True)
        If FrmSelectRight Is Nothing OrElse PanMain Is Nothing Then Return
        If _IsBuilding Then Return
        _IsBuilding = True
        Try
            RefreshCollectionBar()
            PanMain.Children.Clear()
            If PanGrid IsNot Nothing Then PanGrid.Children.Clear()
            If ResetPage Then _CurrentPage = 0
            Dim GridMode As Boolean = (_ViewMode = ViewModeGrid AndAlso PanGrid IsNot Nothing)
            '先扁平化出完整有序列表（组序 → 组内筛选排序），再按页切片渲染：
            '实例很多时只构建当前页的卡片，避免一次建上百个控件把 UI 线程卡死
            Dim Flat As New List(Of KeyValuePair(Of McInstanceCardType, McInstance))
            Dim GroupCounts As New Dictionary(Of McInstanceCardType, Integer)
            Dim GroupTitles As New Dictionary(Of McInstanceCardType, String)
            For Each Kind In GroupOrder()
                If Kind = McInstanceCardType.Hidden AndAlso Not ShowHidden Then Continue For
                Dim Bucket As List(Of McInstance) = Nothing
                If Not McInstanceList.TryGetValue(Kind, Bucket) Then Continue For
                If Bucket Is Nothing Then Continue For
                Dim Shown As List(Of McInstance) = Bucket.Where(Function(One) Not McInstanceLock.IsLocked(One) AndAlso MatchSearch(One)).ToList()
                If Not Shown.Any() Then Continue For
                Shown = SortInstances(Shown)
                GroupCounts(Kind) = Shown.Count
                GroupTitles(Kind) = GroupTitle(Kind, Bucket)
                For Each One In Shown
                    Flat.Add(New KeyValuePair(Of McInstanceCardType, McInstance)(Kind, One))
                Next
            Next
            Dim Total As Integer = Flat.Count
            Dim TotalPages As Integer = Math.Max(1, CInt(Math.Ceiling(Total / CDbl(InstancePageSize))))
            If _CurrentPage >= TotalPages Then _CurrentPage = TotalPages - 1
            If _CurrentPage < 0 Then _CurrentPage = 0
            '渲染当前页切片；组头带全组数量（切片内同组成员必然连续，按组变化切换组头）
            Dim LastKind As McInstanceCardType? = Nothing
            Dim GroupHost As Panel = PanMain
            For Each Pair In Flat.Skip(_CurrentPage * InstancePageSize).Take(InstancePageSize)
                If LastKind Is Nothing OrElse Pair.Key <> LastKind Then
                    LastKind = Pair.Key
                    If GridMode Then
                        PanGrid.Children.Add(BuildGroupHeader(GroupTitles(Pair.Key), GroupCounts(Pair.Key)))
                        GroupHost = New WrapPanel With {.Orientation = Orientation.Horizontal, .Margin = New Thickness(0, 0, 0, 4)}
                        PanGrid.Children.Add(GroupHost)
                    Else
                        PanMain.Children.Add(BuildGroupHeader(GroupTitles(Pair.Key), GroupCounts(Pair.Key)))
                        GroupHost = PanMain
                    End If
                End If
                If GridMode Then
                    GroupHost.Children.Add(BuildCard(Pair.Value))
                Else
                    GroupHost.Children.Add(BuildListItemWrapped(Pair.Value))
                End If
            Next
            '页脚翻页条：只有一页时不显示
            If TotalPages > 1 Then
                GroupHost.Children.Add(BuildPageBar(_CurrentPage, TotalPages, Total))
            End If

            If Total = 0 Then
                PanEmpty.Visibility = Visibility.Visible
                PanBack.Visibility = Visibility.Collapsed
                If Not String.IsNullOrWhiteSpace(_Search) Then
                    LabEmptyTitle.Text = ModLang.L("没有匹配的实例")
                    LabEmptyContent.Text = ModLang.L("换个关键词试试，或者清空右上角的搜索框。")
                    BtnEmptyDownload.Visibility = Visibility.Collapsed
                ElseIf ShowHidden Then
                    LabEmptyTitle.Text = ModLang.L("无隐藏版本")
                    LabEmptyContent.Text = ModLang.L("没有版本被隐藏，你可以在版本设置的版本分类选项中隐藏版本。") & vbCrLf & ModLang.L("再次按下 F11 即可退出隐藏版本查看模式。")
                    BtnEmptyDownload.Visibility = Visibility.Collapsed
                Else
                    LabEmptyTitle.Text = ModLang.L("无可用版本")
                    LabEmptyContent.Text = ModLang.L("未找到任何版本的游戏，请先下载任意版本的游戏。") & vbCrLf & ModLang.L("若有已存在的游戏，请在左边的列表中选择添加文件夹，选择 .minecraft 文件夹将其导入。")
                    BtnEmptyDownload.Visibility = If(Settings.Get(Of Boolean)("UiHiddenPageDownload") AndAlso Not PageSetupUI.HiddenForceShow, Visibility.Collapsed, Visibility.Visible)
                End If
            Else
                PanBack.Visibility = Visibility.Visible
                PanEmpty.Visibility = Visibility.Collapsed
            End If
        Catch ex As Exception
            Logger.Error(ex, "将版本列表转换显示时失败")
        Finally
            _IsBuilding = False
        End Try
        If Not ResetPage AndAlso PanBack IsNot Nothing Then PanBack.ScrollToHome() '翻页后回到列表顶部
    End Sub

    ''' <summary>构建页脚翻页条：上一页 / 页码 / 下一页。</summary>
    Private Function BuildPageBar(CurrentPage As Integer, TotalPages As Integer, Total As Integer) As FrameworkElement
        Dim Bar As New StackPanel With {.Orientation = Orientation.Horizontal, .HorizontalAlignment = HorizontalAlignment.Center, .Margin = New Thickness(0, 10, 0, 4)}
        Dim BtnPrev As New MyButton With {.Text = ModLang.L("上一页"), .MinWidth = 84, .Height = 28, .Padding = New Thickness(10, 0, 10, 0), .IsEnabled = CurrentPage > 0}
        AddHandler BtnPrev.Click, Sub() GoPage(-1)
        Dim Lab As New TextBlock With {.Text = $"{CurrentPage + 1} / {TotalPages} · {Total}", .VerticalAlignment = VerticalAlignment.Center, .Margin = New Thickness(14, 0, 14, 0), .Opacity = 0.7}
        Lab.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Dim BtnNext As New MyButton With {.Text = ModLang.L("下一页"), .MinWidth = 84, .Height = 28, .Padding = New Thickness(10, 0, 10, 0), .IsEnabled = CurrentPage < TotalPages - 1}
        AddHandler BtnNext.Click, Sub() GoPage(1)
        Bar.Children.Add(BtnPrev)
        Bar.Children.Add(Lab)
        Bar.Children.Add(BtnNext)
        Return Bar
    End Function

    ''' <summary>翻页并回到列表顶部。</summary>
    Private Sub GoPage(Delta As Integer)
        _CurrentPage += Delta
        BuildList(False)
    End Sub

    ''' <summary>分组标题：小号半透明文字 + 数量，替代原先可折叠的大卡片。</summary>
    Private Function BuildGroupHeader(Text As String, Count As Integer) As FrameworkElement
        Dim Row As New StackPanel With {.Orientation = Orientation.Horizontal, .Margin = New Thickness(8, 14, 4, 5)}
        Dim Lab As New TextBlock With {.Text = Text, .FontSize = 12.5, .FontWeight = FontWeights.SemiBold, .Opacity = 0.5}
        Lab.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Dim Num As New TextBlock With {.Text = "· " & Count, .FontSize = 12, .Opacity = 0.35, .Margin = New Thickness(6, 0, 0, 0), .VerticalAlignment = VerticalAlignment.Center}
        Num.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Row.Children.Add(Lab)
        Row.Children.Add(Num)
        Return Row
    End Function

    Private Function GroupTitle(Kind As McInstanceCardType, Items As List(Of McInstance)) As String
        Select Case Kind
            Case McInstanceCardType.Star
                Return ModLang.L("收藏夹")
            Case McInstanceCardType.OriginalLike
                Return ModLang.L("常规版本")
            Case McInstanceCardType.API
                Dim HasForge As Boolean = False
                Dim HasNeoForge As Boolean = False
                Dim HasFabric As Boolean = False
                Dim HasLite As Boolean = False
                For Each One In Items
                    If One.Version Is Nothing Then Continue For
                    If One.Version.ModLoaders.HasFlagF(ModLoaders.Fabric) Then HasFabric = True
                    If One.Version.ModLoaders.HasFlagF(ModLoaders.LiteLoader) Then HasLite = True
                    If One.Version.ModLoaders.HasFlagF(ModLoaders.Forge) Then HasForge = True
                    If One.Version.ModLoaders.HasFlagF(ModLoaders.NeoForge) Then HasNeoForge = True
                Next
                If (If(HasLite, 1, 0) + If(HasForge, 1, 0) + If(HasFabric, 1, 0) + If(HasNeoForge, 1, 0)) > 1 Then Return ModLang.L("可安装 Mod")
                If HasForge Then Return ModLang.L("Forge 版本")
                If HasNeoForge Then Return ModLang.L("NeoForge 版本")
                If HasLite Then Return ModLang.L("LiteLoader 版本")
                Return ModLang.L("Fabric 版本")
            Case McInstanceCardType.Fool
                Return ModLang.L("愚人节版本")
            Case McInstanceCardType.Rubbish
                Return ModLang.L("不常用版本")
            Case McInstanceCardType.Error
                Return ModLang.L("错误的版本")
            Case McInstanceCardType.Hidden
                Return ModLang.L("隐藏的版本")
        End Select
        Return ModLang.L("实例")
    End Function

    ''' <summary>按合集、实例名与版本号做匹配；关键词支持 regex: 前缀。</summary>
    Private Function MatchSearch(Instance As McInstance) As Boolean
        '合集筛选优先
        If Not String.IsNullOrEmpty(_CollectionFilter) Then
            If Not ModInstanceCollections.GetOf(Instance).Contains(_CollectionFilter) Then Return False
        End If
        Dim Key As String = If(_Search, "").Trim()
        If Key.Length = 0 Then Return True
        If Key.StartsWithF("regex:", True) Then
            '原来每个实例都 New Regex 一次（列表 200 个实例 = 每次重建编译 200 次正则，
            '而正则编译是这套搜索里最贵的一步）。现在按「模式串」缓存一份，只在模式变化时重编译。
            Dim Pattern As String = Key.Substring(6)
            If _SearchRegex Is Nothing OrElse _SearchRegexPattern <> Pattern Then
                _SearchRegex = Nothing
                _SearchRegexPattern = Pattern
                Try
                    _SearchRegex = New Regex(Pattern, RegexOptions.IgnoreCase)
                Catch ex As Exception
                    '非法正则：本批全部不匹配（保持原来的行为），模式不变时不重复报错
                End Try
            End If
            If _SearchRegex Is Nothing Then Return False
            Try
                Return _SearchRegex.IsMatch(If(Instance.Name, ""))
            Catch ex As Exception
                Return False
            End Try
        End If
        If Instance.Name IsNot Nothing AndAlso Instance.Name.IndexOf(Key, StringComparison.OrdinalIgnoreCase) >= 0 Then Return True
        If Instance.Version IsNot Nothing AndAlso Instance.Version.VanillaName IsNot Nothing AndAlso
           Instance.Version.VanillaName.IndexOf(Key, StringComparison.OrdinalIgnoreCase) >= 0 Then Return True
        Return False
    End Function

    ''' <summary>regex: 搜索用的已编译正则缓存（模式不变则复用）。</summary>
    Private _SearchRegex As Regex = Nothing
    Private _SearchRegexPattern As String = Nothing

    Private Function SortInstances(Items As List(Of McInstance)) As List(Of McInstance)
        Select Case _Sort
            Case SortName
                Return Items.OrderBy(Function(One) One.Name, StringComparer.OrdinalIgnoreCase).ToList()
            Case SortVersion
                Return Items.OrderByDescending(Function(One) If(One.Version Is Nothing, 0, One.Version.Drop)).
                             ThenByDescending(Function(One) If(One.Version Is Nothing, "", If(One.Version.VanillaName, ""))).
                             ThenBy(Function(One) One.Name, StringComparer.OrdinalIgnoreCase).ToList()
            Case SortCreateTime
                Return Items.OrderByDescending(Function(One) GetCreateTime(One)).
                             ThenBy(Function(One) One.Name, StringComparer.OrdinalIgnoreCase).ToList()
            Case Else
                Return Items.OrderByDescending(Function(One) GetLastPlay(One)).
                             ThenBy(Function(One) One.Name, StringComparer.OrdinalIgnoreCase).ToList()
        End Select
    End Function

    Private Function GetLastPlay(Instance As McInstance) As Date
        Dim Map = _LastPlay
        If Map Is Nothing Then Return Date.MinValue
        Dim Value As Date
        If Map.TryGetValue(PathUtils.ForCompare(Instance.PathVersion), Value) Then Return Value
        Return Date.MinValue
    End Function

    Private Function GetCreateTime(Instance As McInstance) As Date
        Try
            Dim Info As New DirectoryInfo(Instance.PathVersion.TrimEnd("\"c))
            If Not Info.Exists Then Return Date.MinValue
            Return Info.CreationTimeUtc
        Catch ex As Exception
            Return Date.MinValue
        End Try
    End Function

    ''' <summary>把「多久之前」格式化成短文本。</summary>
    Private Function FormatAgo(Time As Date) As String
        If Time = Date.MinValue OrElse Time.Year < 2000 Then Return ""
        Dim Span As TimeSpan
        Try
            Span = Date.UtcNow - Time.ToUniversalTime
        Catch ex As Exception
            Return ""
        End Try
        If Span.TotalMinutes < 1 Then Return ModLang.L("刚刚游玩")
        If Span.TotalHours < 1 Then Return CInt(Span.TotalMinutes) & ModLang.L(" 分钟前")
        If Span.TotalDays < 1 Then Return CInt(Span.TotalHours) & ModLang.L(" 小时前")
        If Span.TotalDays < 30 Then Return CInt(Span.TotalDays) & ModLang.L(" 天前")
        If Span.TotalDays < 365 Then Return CInt(Span.TotalDays / 30) & ModLang.L(" 个月前")
        Return CInt(Span.TotalDays / 365) & ModLang.L(" 年前")
    End Function

    ''' <summary>实例副标题：自定义描述或版本+加载器，再接最后游玩时间。</summary>
    Private Function BuildInfoText(Instance As McInstance) As String
        If Instance.State = McInstance.McInstanceState.Error Then
            Return If(String.IsNullOrWhiteSpace(Instance.Info), ModLang.L("该实例无法被识别"), Instance.Info)
        End If
        Dim Parts As New List(Of String)
        Dim Custom As String = ""
        Try
            Custom = ReadIni(Instance.PathVersion & "TCL\Setup.ini", "CustomInfo")
        Catch ex As Exception
            Logger.Warn(ex, "读取实例自定义描述失败")
        End Try
        If Not String.IsNullOrWhiteSpace(Custom) Then
            Parts.Add(Custom.Trim())
        Else
            Dim Desc As String = ""
            Try
                Desc = Instance.VersionDisplayName()
            Catch ex As Exception
                Logger.Warn(ex, "生成版本描述失败")
            End Try
            If Not String.IsNullOrWhiteSpace(Desc) Then Parts.Add(Desc)
        End If
        Dim Ago As String = FormatAgo(GetLastPlay(Instance))
        If Ago <> "" Then Parts.Add(Ago)
        Return String.Join("   ·   ", Parts.ToArray())
    End Function

    ''' <summary>构建单个实例条目：方形图标 + 两行文本 + 操作按钮。</summary>
    Private Function BuildItem(Instance As McInstance) As MyListItem
        Dim Item As New MyListItem With {
            .IsScaleAnimationEnabled = False,
            .Type = MyListItem.CheckType.RadioBox,
            .MinPaddingRight = 86,
            .Height = ItemHeight,
            .Margin = New Thickness(0, 1, 0, 1),
            .Tag = Instance,
            .Title = Instance.Name,
            .LogoSquare = True,
            .LogoSize = ItemIconSize,
            .LogoRadius = 8,
            .SnapsToDevicePixels = True
        }
        Item.Info = BuildInfoText(Instance)

        '实例图标：自定义 Logo.png 优先，其次内置方块图
        Try
            If String.IsNullOrWhiteSpace(Instance.Logo) Then
                Item.Logo = PathImage & "Blocks/GrassBlock.png"
            ElseIf Instance.Logo.EndsWithF("TCL\Logo.png") OrElse Instance.Logo.EndsWithF("TCL/Logo.png") Then
                Item.Logo = Instance.PathVersion & "TCL\Logo.png"
            Else
                Item.Logo = Instance.Logo
            End If
        Catch ex As Exception
            Logger.Warn(ex, $"加载实例图标失败：{Instance.Name}")
        End Try

        '收藏
        Dim BtnStar As New MyIconButton With {.LogoScale = 1.05}
        If Instance.IsStar Then
            BtnStar.ToolTip = ModLang.L("取消收藏")
            BtnStar.SvgIcon = "lucide/heart-filled"
        Else
            BtnStar.ToolTip = ModLang.L("收藏")
            BtnStar.SvgIcon = "lucide/heart"
        End If
        AddHandler BtnStar.Click, Sub() ToggleStar(Instance)

        '实例设置
        Dim BtnSetup As New MyIconButton With {.LogoScale = 1.05, .SvgIcon = "lucide/settings", .ToolTip = ModLang.L("实例设置")}
        AddHandler BtnSetup.Click, Sub() OpenInstanceSetup(Instance)

        '更多：首页启动按钮同款的 Popup 动画卡片，右键菜单保留不动
        Dim BtnMore As New MyIconButton With {.LogoScale = 1.05, .SvgIcon = "lucide/more-vertical", .ToolTip = ModLang.L("更多操作")}
        AddHandler BtnMore.Click,
            Sub()
                If PopupInstanceMenu.IsOpen AndAlso MenuInstance Is Instance Then
                    CloseInstanceMenu() : Return
                End If
                '同 250ms 守卫只挡「同一实例刚被这次点击关掉」的情况；换一行立刻就能开
                If GetTimeMs() - InstanceMenuClosedTick < 250 AndAlso MenuInstance Is Instance Then Return
                Dim BtnW As Double = If(BtnMore.ActualWidth > 0, BtnMore.ActualWidth, 22)
                Dim BtnH As Double = If(BtnMore.ActualHeight > 0, BtnMore.ActualHeight, 22)
                OpenInstanceMenu(Instance, BtnMore, New Point(BtnW, BtnH + 2), True)
            End Sub

        Item.Buttons = {BtnStar, BtnSetup, BtnMore}

        '右键菜单
        AddHandler Item.MouseRightButtonUp,
            Sub(sender, e)
                OpenInstanceMenu(Instance, Item, e.GetPosition(Item), False)
                e.Handled = True
            End Sub

        '单击：选中并返回启动页
        AddHandler Item.Click, AddressOf Item_Click

        '当前选中项
        Try
            If McInstanceSelected IsNot Nothing AndAlso McInstanceSelected.Equals(Instance) Then Item.Checked = True
        Catch ex As Exception
            Logger.Warn(ex, "标记当前选中实例失败")
        End Try

        Return Item
    End Function

    ''' <summary>列表模式条目：右侧 MyListItem，左侧可选一根 4px 的颜色标签竖条（无标签时直接返回原控件）。</summary>
    Private Function BuildListItemWrapped(Instance As McInstance) As FrameworkElement
        Dim Item As MyListItem = BuildItem(Instance)
        Dim TagColor As String = GetColorTag(Instance)
        If TagColor = "" Then Return Item

        Dim Host As New Grid
        Host.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(8)})
        Host.ColumnDefinitions.Add(New ColumnDefinition)
        Dim Bar As New Shapes.Rectangle With {
            .Width = 4,
            .RadiusX = 2, .RadiusY = 2,
            .Fill = ColorBrushOf(TagColor),
            .HorizontalAlignment = HorizontalAlignment.Left,
            .VerticalAlignment = VerticalAlignment.Stretch,
            .Margin = New Thickness(2, 9, 0, 9),
            .IsHitTestVisible = False,
            .SnapsToDevicePixels = True
        }
        Grid.SetColumn(Bar, 0)
        Host.Children.Add(Bar)
        Grid.SetColumn(Item, 1)
        Host.Children.Add(Item)
        Return Host
    End Function

#Region "网格卡片"

    Private Const CardWidth As Integer = 156
    Private Const CardHeight As Integer = 168
    Private Const CardIconSize As Integer = 64

    ''' <summary>网格视图的单个实例卡片：图标 + 名称 + 版本·加载器，左键选中、右键出菜单。</summary>
    Private Function BuildCard(Instance As McInstance) As FrameworkElement
        Dim Card As New MyCard With {
            .Width = CardWidth,
            .Height = CardHeight,
            .UseAnimation = False,
            .Margin = New Thickness(4, 4, 4, 4),
            .Cursor = Cursors.Hand,
            .ToolTip = Instance.Name
        }

        Dim CardContent As New Grid
        CardContent.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})
        CardContent.RowDefinitions.Add(New RowDefinition With {.Height = New GridLength(1, GridUnitType.Star)})
        CardContent.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})
        CardContent.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})

        '颜色标签：卡片顶部一条 4px 横条
        Dim TagColor As String = GetColorTag(Instance)
        If TagColor <> "" Then
            Dim Bar As New Border With {
                .Height = 4,
                .CornerRadius = New CornerRadius(12, 12, 0, 0),
                .Background = ColorBrushOf(TagColor),
                .VerticalAlignment = VerticalAlignment.Top,
                .IsHitTestVisible = False,
                .SnapsToDevicePixels = True
            }
            Grid.SetRow(Bar, 0)
            CardContent.Children.Add(Bar)
        End If

        '实例图标
        Dim IconHost As New Grid With {.Margin = New Thickness(0, 14, 0, 6)}
        IconHost.Children.Add(BuildCardIcon(Instance, CardIconSize))
        Grid.SetRow(IconHost, 1)
        CardContent.Children.Add(IconHost)

        '实例名
        Dim LabName As New TextBlock With {
            .Text = If(Instance.Name, ""),
            .FontSize = 13.5,
            .TextAlignment = TextAlignment.Center,
            .TextTrimming = TextTrimming.CharacterEllipsis,
            .Margin = New Thickness(10, 0, 10, 3),
            .HorizontalAlignment = HorizontalAlignment.Stretch,
            .IsHitTestVisible = False
        }
        LabName.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Grid.SetRow(LabName, 2)
        CardContent.Children.Add(LabName)

        '版本 · 加载器
        Dim LabInfo As New TextBlock With {
            .Text = BuildCardInfoText(Instance),
            .FontSize = 12,
            .Opacity = 0.6,
            .TextAlignment = TextAlignment.Center,
            .TextTrimming = TextTrimming.CharacterEllipsis,
            .Margin = New Thickness(10, 0, 10, 13),
            .HorizontalAlignment = HorizontalAlignment.Stretch,
            .IsHitTestVisible = False
        }
        LabInfo.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Grid.SetRow(LabInfo, 3)
        CardContent.Children.Add(LabInfo)

        Card.Children.Add(CardContent)

        '收藏星标
        If Instance.IsStar Then
            Dim Star As New Global.TClUI.Controls.SvgIcon.SvgIcon With {
                .Icon = "lucide/heart-filled",
                .Width = 13, .Height = 13,
                .HorizontalAlignment = HorizontalAlignment.Right,
                .VerticalAlignment = VerticalAlignment.Top,
                .Margin = New Thickness(0, 8, 8, 0),
                .IsHitTestVisible = False
            }
            Star.SetResourceReference(Global.TClUI.Controls.SvgIcon.SvgIcon.IconBrushProperty, "ColorBrush3")
            Card.Children.Add(Star)
        End If

        '当前选中：描一圈主题色
        Try
            If McInstanceSelected IsNot Nothing AndAlso McInstanceSelected.Equals(Instance) Then
                Dim Ring As New Border With {
                    .CornerRadius = New CornerRadius(13),
                    .BorderThickness = New Thickness(2),
                    .Margin = New Thickness(1),
                    .IsHitTestVisible = False,
                    .SnapsToDevicePixels = True
                }
                Ring.SetResourceReference(Border.BorderBrushProperty, "ColorBrush3")
                Card.Children.Add(Ring)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "标记当前选中实例失败")
        End Try

        '单击：选中并返回启动页（与列表模式同一套逻辑）
        AddHandler Card.MouseLeftButtonUp, Sub(snd, args) SelectItem(Instance)

        '右键菜单
        AddHandler Card.MouseRightButtonUp,
            Sub(snd, args)
                OpenInstanceMenu(Instance, Card, args.GetPosition(Card), False)
                args.Handled = True
            End Sub

        Return Card
    End Function

    ''' <summary>网格图标：走共享的 MyLogoCache。</summary>
    Private Shared Function GetCachedLogo(LogoPath As String) As MyBitmap
        Dim Bmp As MyBitmap = MyLogoCache.Get(LogoPath)
        If Bmp Is Nothing Then Logger.Warn($"缓存实例图标失败：{LogoPath}")
        Return Bmp
    End Function

    ''' <summary>卡片图标：实例自带图标（实例加载时已补全默认方块图），解码失败才退回矢量图标。</summary>
    Private Function BuildCardIcon(Instance As McInstance, Size As Integer) As FrameworkElement
        Dim LogoPath As String = ""
        Try
            LogoPath = If(Instance.Logo, "").Trim()
            If LogoPath.EndsWithF("TCL\Logo.png") OrElse LogoPath.EndsWithF("TCL/Logo.png") Then
                LogoPath = Instance.PathVersion & "TCL\Logo.png"
            End If
        Catch ex As Exception
            Logger.Warn(ex, $"加载实例图标失败：{Instance.Name}")
            LogoPath = ""
        End Try
        If Not String.IsNullOrEmpty(LogoPath) AndAlso Not LogoPath.StartsWithF("http", True) Then
            Try
                Dim CachedLogo = GetCachedLogo(LogoPath)
                If CachedLogo Is Nothing Then Throw New Exception("图标解码失败")
                Dim Box As New Border With {
                    .Width = Size, .Height = Size,
                    .CornerRadius = New CornerRadius(10),
                    .Background = New ImageBrush(CachedLogo) With {.Stretch = Stretch.UniformToFill},
                    .HorizontalAlignment = HorizontalAlignment.Center,
                    .VerticalAlignment = VerticalAlignment.Center,
                    .SnapsToDevicePixels = True,
                    .UseLayoutRounding = False
                }
                RenderOptions.SetBitmapScalingMode(Box, BitmapScalingMode.Linear)
                Return Box
            Catch ex As Exception
                Logger.Warn(ex, $"解码实例图标失败：{Instance.Name}")
            End Try
        End If
        '没有可用位图（含网络图标）时按版本类型退回矢量图标
        Dim Fallback As New Global.TClUI.Controls.SvgIcon.SvgIcon With {
            .Icon = DefaultSvgIcon(Instance),
            .Width = Size - 8, .Height = Size - 8,
            .Stretch = Stretch.Uniform,
            .HorizontalAlignment = HorizontalAlignment.Center,
            .VerticalAlignment = VerticalAlignment.Center
        }
        Fallback.SetResourceReference(Global.TClUI.Controls.SvgIcon.SvgIcon.IconBrushProperty, "ColorBrush2")
        Return Fallback
    End Function

    ''' <summary>实例没有可用图标时的矢量图标：原版用 box，带加载器的用 package。</summary>
    Private Function DefaultSvgIcon(Instance As McInstance) As String
        Try
            Dim StateNow As McInstance.McInstanceState = Instance.State
            If StateNow = McInstance.McInstanceState.Forge OrElse StateNow = McInstance.McInstanceState.NeoForge OrElse
               StateNow = McInstance.McInstanceState.Fabric OrElse StateNow = McInstance.McInstanceState.LiteLoader Then
                Return "lucide/package"
            End If
            If Instance.Version IsNot Nothing AndAlso Instance.Version.ModLoaders.Flags.Any Then Return "lucide/package"
        Catch ex As Exception
            Logger.Warn(ex, "判断实例默认图标失败")
        End Try
        Return "lucide/box"
    End Function

    ''' <summary>卡片副标题：版本号 · 加载器（卡片窄，不带游玩时间）。</summary>
    Private Function BuildCardInfoText(Instance As McInstance) As String
        If Instance.State = McInstance.McInstanceState.Error Then
            Return If(String.IsNullOrWhiteSpace(Instance.Info), ModLang.L("该实例无法被识别"), Instance.Info)
        End If
        Try
            Dim Desc As String = ""
            Try
                Desc = Instance.VersionDisplayName()
            Catch ex As Exception
                Logger.Warn(ex, "生成版本描述失败")
            End Try
            If String.IsNullOrWhiteSpace(Desc) Then Return ModLang.L("实例")
            Return Desc.Replace("   ·   ", " · ")
        Catch ex As Exception
            Return ModLang.L("实例")
        End Try
    End Function

#End Region


    Private Shared Sub NewCollectionFor(Instance As McInstance)
        Try
            Dim Name As String = MyMsgBoxInput(ModLang.L("新建合集"), ModLang.L("合集只是标签，不会移动实例文件。"))
            If String.IsNullOrWhiteSpace(Name) Then Return
            Name = Name.Trim()
            Dim ErrorText As String = ModInstanceCollections.ValidateName(Name)
            If ErrorText <> "" Then
                Hint(ErrorText, HintType.Red)
                Return
            End If
            ModInstanceCollections.AddTo(Instance, Name)
            FrmSelectRight?.RefreshCollectionBar()
            FrmSelectRight?.BuildListRefresh()
            Hint(ModLang.L("已加入合集 ") & Name, HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "新建合集失败", LogBehavior.Alert)
        End Try
    End Sub


#End Region

#Region "更多动画卡片"

    ''' <summary>当前卡片对应的实例（卡片内容全部围绕它构建）。</summary>
    Private MenuInstance As McInstance = Nothing
    ''' <summary>菜单上次被关掉的时刻。StaysOpen=False 时再点按钮会先被 WPF 关掉再触发 Click，
    ''' 此时 IsOpen 已是 False——用 250ms 时间窗挡掉这次（与首页 PopupLaunchMenu 同款处理）。</summary>
    Private InstanceMenuClosedTick As Integer = 0

    ''' <summary>
    ''' 打开实例「更多」卡片。Anchor = Target 内部锚点，AlignRight = 卡片右边贴锚点。
    ''' </summary>
    Private Sub OpenInstanceMenu(Instance As McInstance, Target As FrameworkElement, Anchor As Point, AlignRight As Boolean)
        MenuInstance = Instance
        BuildInstanceCardMain()
        '复位动画初值（Popup 关着时改值不会闪）；真正的展开动画在 Opened 里跑
        CardInstanceMenu.Opacity = 0
        TransformInstanceMenuScale.ScaleX = 0.94
        TransformInstanceMenuScale.ScaleY = 0.94
        TransformInstanceMenuPos.Y = 10
        '定位按锚点算
        MenuCard.ApplyAnchor(PopupInstanceMenu, Target, Anchor, AlignRight, 14)
        '条目多时按锚点这一侧的可用高度封顶，超出用 TCL 滚动条
        ScrollInstanceMenu.MaxHeight = MenuCard.CalcMaxMenuHeight(Target)
        PopupInstanceMenu.IsOpen = True
    End Sub

    Private Sub PopupInstanceMenu_Opened(sender As Object, e As EventArgs) Handles PopupInstanceMenu.Opened
        '放在 Opened 里跑：Popup 首次打开时内容才 Loaded，早于此开始动画会被首帧覆盖
        If Not MenuCard.PopupAnimEnabled Then
            TransformInstanceMenuScale.ScaleX = 1
            TransformInstanceMenuScale.ScaleY = 1
            TransformInstanceMenuPos.Y = 0
            CardInstanceMenu.Opacity = MenuCard.PopupBaseOpacity
            Return
        End If
        AniStart({
            AaOpacity(CardInstanceMenu, MenuCard.PopupBaseOpacity - CardInstanceMenu.Opacity, 130),
            AaDouble(Sub(i) TransformInstanceMenuScale.ScaleX += i, 1 - TransformInstanceMenuScale.ScaleX, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformInstanceMenuScale.ScaleY += i, 1 - TransformInstanceMenuScale.ScaleY, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformInstanceMenuPos.Y += i, -TransformInstanceMenuPos.Y, 220, , New AniEaseOutFluent(AniEasePower.Middle))
        }, "Instance Menu Popup")
    End Sub

    Private Sub CloseInstanceMenu()
        Try
            If Not PopupInstanceMenu.IsOpen Then Return
            If Not MenuCard.PopupAnimEnabled Then
                PopupInstanceMenu.IsOpen = False
                Return
            End If
            AniStop("Instance Menu Popup")
            AniStart({
                AaOpacity(CardInstanceMenu, -CardInstanceMenu.Opacity, 80),
                AaDouble(Sub(i) TransformInstanceMenuScale.ScaleX += i, 0.96 - TransformInstanceMenuScale.ScaleX, 90),
                AaDouble(Sub(i) TransformInstanceMenuScale.ScaleY += i, 0.96 - TransformInstanceMenuScale.ScaleY, 90),
                AaCode(Sub() PopupInstanceMenu.IsOpen = False, , True)
            }, "Instance Menu Popup")
        Catch ex As Exception
            Logger.Warn(ex, "关闭实例更多卡片失败")
        End Try
    End Sub

    Private Sub PopupInstanceMenu_Closed(sender As Object, e As EventArgs) Handles PopupInstanceMenu.Closed
        InstanceMenuClosedTick = GetTimeMs()
    End Sub

    ''' <summary>卡片行工厂：点击 = 关卡片 + 执行动作。</summary>
    Private Sub MenuAddRow(Title As String, Icon As String, Action As Action)
        Dim Row As New MyListItem With {
            .Title = ModLang.L(Title), .SvgIcon = Icon, .Type = MyListItem.CheckType.Clickable,
            .Height = 38, .IsScaleAnimationEnabled = False}
        AddHandler Row.Click,
            Sub()
                CloseInstanceMenu()
                Action()
            End Sub
        PanInstanceMenu.Children.Add(Row)
    End Sub

    Private Sub MenuAddSep()
        PanInstanceMenu.Children.Add(New Windows.Shapes.Rectangle With {
            .Height = 1, .Margin = New Thickness(8, 3, 8, 3),
            .Fill = CType(Application.Current.TryFindResource("ColorBrush6"), Brush)})
    End Sub

    Private Sub MenuAddBackRow()
        Dim Row As New MyListItem With {
            .Title = ModLang.L("返回"), .SvgIcon = "lucide/arrow-left", .Type = MyListItem.CheckType.Clickable,
            .Height = 38, .IsScaleAnimationEnabled = False}
        AddHandler Row.Click, Sub() BuildInstanceCardMain()
        PanInstanceMenu.Children.Add(Row)
    End Sub

    ''' <summary>一级卡片：与右键菜单 BuildMenu 同一套动作。</summary>
    Private Sub BuildInstanceCardMain()
        PanInstanceMenu.Children.Clear()
        If MenuInstance Is Nothing Then Return
        Dim Instance = MenuInstance
        MenuAddRow("启动游戏", "lucide/play", Sub() LaunchInstance(Instance))
        MenuAddSep()
        MenuAddRow("实例设置", "lucide/settings", Sub() OpenInstanceSetup(Instance))
        MenuAddRow("重命名", "lucide/pencil", Sub() RenameInstance(Instance))
        MenuAddRow("复制实例", "lucide/copy", Sub() DuplicateInstance(Instance))
        MenuAddRow("备份为压缩包", "lucide/archive", Sub() BackupInstance(Instance))
        MenuAddRow("导出整合包", "lucide/package", Sub() ExportInstance(Instance))
        MenuAddRow("更换图标", "lucide/image", Sub() ChangeInstanceIcon(Instance))
        MenuAddRow("颜色标签", "lucide/palette", Sub() BuildInstanceCardColors())
        MenuAddRow("加入合集", "lucide/layers", Sub() BuildInstanceCardCollections())
        MenuAddRow("打开实例文件夹", "lucide/folder-open", Sub() PageInstanceOverall.OpenInstanceFolder(Instance))
        MenuAddRow("创建桌面快捷方式", "lucide/external-link", Sub() CreateShortcut(Instance))
        MenuAddSep()
        MenuAddRow("删除", "lucide/trash-2", Sub() DeleteInstance(Nothing, Instance))
    End Sub

    ''' <summary>二级卡片：颜色标签（色点点选，替代右键菜单的子菜单）。</summary>
    Private Sub BuildInstanceCardColors()
        PanInstanceMenu.Children.Clear()
        If MenuInstance Is Nothing Then Return
        Dim Instance = MenuInstance
        PanInstanceMenu.Children.Add(New TextBlock With {
            .Text = ModLang.L("颜色标签"), .Margin = New Thickness(10, 4, 10, 2), .Opacity = 0.6, .FontSize = 12})
        Dim Wrap As New WrapPanel With {.Margin = New Thickness(6, 2, 6, 6)}
        For i = 0 To ColorTagPresets.GetUpperBound(0)
            Dim HexColor As String = ColorTagPresets(i, 1)
            Dim Dot As New Border With {
                .Width = 30, .Height = 30, .CornerRadius = New CornerRadius(15), .Margin = New Thickness(5),
                .Background = New SolidColorBrush(CType(ColorConverter.ConvertFromString(HexColor), Color)),
                .ToolTip = ModLang.L(ColorTagPresets(i, 0)), .Cursor = Cursors.Hand}
            AddHandler Dot.MouseLeftButtonDown,
                Sub(sender, e)
                    CloseInstanceMenu()
                    SetColorTag(Instance, HexColor)
                    e.Handled = True
                End Sub
            Wrap.Children.Add(Dot)
        Next
        PanInstanceMenu.Children.Add(Wrap)
        MenuAddRow("清除标签", "lucide/x", Sub() SetColorTag(Instance, ""))
        MenuAddSep()
        MenuAddBackRow()
    End Sub

    ''' <summary>二级卡片：加入合集（勾选即加入/移出，替代右键菜单的子菜单）。</summary>
    Private Sub BuildInstanceCardCollections()
        PanInstanceMenu.Children.Clear()
        If MenuInstance Is Nothing Then Return
        Dim Instance = MenuInstance
        Try
            Dim Owned = ModInstanceCollections.GetOf(Instance)
            Dim All = ModInstanceCollections.ListAll()
            For Each One In All
                Dim NameCopy As String = One
                Dim Check As New MyCheckBox With {
                    .Text = NameCopy, .Checked = Owned.Contains(NameCopy),
                    .Margin = New Thickness(12, 5, 10, 5)}
                AddHandler Check.Change,
                    Sub(sender As Object, user As Boolean)
                        Try
                            If user Then
                                ModInstanceCollections.AddTo(Instance, NameCopy)
                            Else
                                ModInstanceCollections.RemoveFrom(Instance, NameCopy)
                            End If
                            FrmSelectRight?.RefreshCollectionBar()
                            FrmSelectRight?.BuildListRefresh()
                        Catch ex As Exception
                            Logger.Warn(ex, "切换实例合集归属失败")
                        End Try
                    End Sub
                PanInstanceMenu.Children.Add(Check)
            Next
            If All.Any() Then MenuAddSep()
        Catch ex As Exception
            Logger.Warn(ex, "构建合集卡片失败")
        End Try
        MenuAddRow("新建合集…", "lucide/circle-plus", Sub() NewCollectionFor(Instance))
        MenuAddSep()
        MenuAddBackRow()
    End Sub

#End Region

#Region "颜色标签"

    ''' <summary>颜色标签预设：中文名 + 十六进制值。与合集一样存在实例自己的 TCL\Setup.ini 里。</summary>
    Private Shared ReadOnly ColorTagPresets As String(,) = {
        {"红色", "#e74c3c"},
        {"橙色", "#e67e22"},
        {"黄色", "#f1c40f"},
        {"绿色", "#2ecc71"},
        {"青色", "#1abc9c"},
        {"蓝色", "#3498db"},
        {"紫色", "#9b59b6"},
        {"灰色", "#95a5a6"}
    }

    ''' <summary>读取实例的颜色标签，没有则返回空字符串。</summary>
    Private Shared Function GetColorTag(Instance As McInstance) As String
        If Instance Is Nothing Then Return ""
        Try
            Dim Raw As String = ReadIni(Instance.PathVersion & "TCL\Setup.ini", "ColorTag", "")
            If String.IsNullOrWhiteSpace(Raw) Then Return ""
            Dim Hex As String = Raw.Trim()
            If Not Hex.StartsWithF("#") Then Hex = "#" & Hex
            Return Hex
        Catch ex As Exception
            Logger.Warn(ex, $"读取实例颜色标签失败：{Instance.Name}")
            Return ""
        End Try
    End Function

    ''' <summary>把十六进制颜色字符串转成画刷；非法值返回透明画刷。</summary>
    Private Shared Function ColorBrushOf(Hex As String) As Brush
        Try
            If String.IsNullOrWhiteSpace(Hex) Then Return Brushes.Transparent
            Dim Value As Object = ColorConverter.ConvertFromString(Hex.Trim())
            If Value Is Nothing Then Return Brushes.Transparent
            Dim TagBrush As New SolidColorBrush(CType(Value, Color))
            TagBrush.Freeze()
            Return TagBrush
        Catch ex As Exception
            Logger.Warn(ex, $"解析颜色标签失败：{Hex}")
            Return Brushes.Transparent
        End Try
    End Function

    ''' <summary>设置或清除实例的颜色标签（空字符串表示清除）。</summary>
    Private Shared Sub SetColorTag(Instance As McInstance, Hex As String)
        If Instance Is Nothing Then Return
        Try
            Dim SetupFile As String = Instance.PathVersion & "TCL\Setup.ini"
            If String.IsNullOrWhiteSpace(Hex) Then
                DeleteIniKey(SetupFile, "ColorTag")
                Hint(ModLang.L("已清除颜色标签。"), HintType.Green)
            Else
                WriteIni(SetupFile, "ColorTag", Hex.Trim())
                Hint(ModLang.L("已设置颜色标签。"), HintType.Green)
            End If
            FrmSelectRight?.BuildListRefresh()
        Catch ex As Exception
            Logger.Error(ex, "设置实例颜色标签失败", LogBehavior.Alert)
        End Try
    End Sub


    ''' <summary>创建桌面快捷方式，失败时把原因提示给用户。</summary>
    Private Shared Sub CreateShortcut(Instance As McInstance)
        Try
            Dim ErrorText As String = ""
            If ModInstanceShortcut.Create(Instance, ErrorText) Then
                Hint(ModLang.L("已在桌面创建快捷方式。"), HintType.Green)
            Else
                Hint(If(String.IsNullOrWhiteSpace(ErrorText), ModLang.L("创建快捷方式失败。"), ErrorText), HintType.Red)
            End If
        Catch ex As Exception
            Logger.Error(ex, "创建桌面快捷方式失败", LogBehavior.Alert)
        End Try
    End Sub

#End Region

#Region "实例操作"

    '点击选项
    Public Shared Sub Item_Click(sender As MyListItem, e As EventArgs)
        If sender Is Nothing Then Return
        SelectItem(TryCast(sender.Tag, McInstance))
    End Sub

    ''' <summary>选中指定实例并返回启动页；列表项与网格卡片共用这一套逻辑。</summary>
    Private Shared Sub SelectItem(Instance As McInstance)
        If Instance Is Nothing Then Return
        If McInstanceLock.IsLocked(Instance) Then
            Hint(ModLang.L("该版本正在被修改，请稍后再选择！"), HintType.Red)
            Return
        End If
        If New McInstance(Instance.PathVersion).Check Then
            '正常版本
            McInstanceSelected = Instance
            FrmMain.PageBack()
        Else
            '错误版本
            PageInstanceOverall.OpenInstanceFolder(Instance)
        End If
    End Sub

    Private Shared Sub LaunchInstance(Instance As McInstance)
        If McInstanceLock.IsLocked(Instance) Then
            Hint(ModLang.L("该版本正在被修改，请稍后再试！"), HintType.Red)
            Return
        End If
        If Not New McInstance(Instance.PathVersion).Check Then
            PageInstanceOverall.OpenInstanceFolder(Instance)
            Return
        End If
        McInstanceSelected = Instance
        FrmMain.PageBack()
    End Sub

    Private Shared Sub OpenInstanceSetup(Instance As McInstance)
        PageInstanceLeft.Instance = Instance
        FrmMain.PageChange(FormMain.PageType.InstanceSetup, 0)
    End Sub

    ''' <summary>跳到实例的「导出」页，把实例打包成整合包。</summary>
    Private Shared Sub ExportInstance(Instance As McInstance)
        PageInstanceLeft.Instance = Instance
        FrmMain.PageChange(FormMain.PageType.InstanceSetup, FormMain.PageSubType.InstanceExport)
    End Sub

    Private Shared Sub ToggleStar(Instance As McInstance)
        Try
            WriteIni(Instance.PathVersion & "TCL\Setup.ini", "IsStar", Not Instance.IsStar)
            FrmSelectRight?.RefreshList(True)
        Catch ex As Exception
            Logger.Error(ex, $"版本 {Instance.Name} 收藏状态更改失败", LogBehavior.Alert)
        End Try
    End Sub

    Private Shared Sub RenameInstance(Instance As McInstance)
        Try
            If McInstanceLock.IsLocked(Instance) Then
                Hint(ModLang.L("该版本正在被修改，请稍后再重命名！"), HintType.Red)
                Return
            End If
            Dim OldName As String = Instance.Name
            Dim NewName As String = MyMsgBoxInput(ModLang.L("重命名实例"), ModLang.L("新名称会成为实例的文件夹名。"), OldName,
                New ObjectModel.Collection(Of Validate) From {
                    New ValidateFolderName(ModInstanceTools.VersionsRootOf(Instance).TrimEnd("\"c), True, New List(Of String) From {OldName})
                })
            If String.IsNullOrWhiteSpace(NewName) Then Return
            Dim NewInstance = Instance.Rename(NewName)
            Hint(ModLang.L("重命名成功！"), HintType.Green)
            If McInstanceSelected IsNot Nothing AndAlso McInstanceSelected.Equals(Instance) Then
                McInstanceSelected = NewInstance
                WriteIni(McFolderSelected & "TCL.ini", "Version", NewName)
            End If
            FrmSelectRight?.RefreshList(True)
            FrmLaunchRight?.RefreshButtonsUI() '首页当前版本名联动
        Catch ex As Exception
            Logger.Error(ex, "重命名实例失败", LogBehavior.Alert)
        End Try
    End Sub

    Private Shared Sub DuplicateInstance(Instance As McInstance)
        Try
            If McInstanceLock.IsLocked(Instance) Then
                Hint(ModLang.L("该版本正在被修改，请稍后再复制！"), HintType.Red)
                Return
            End If
            Dim VersionsRoot As String = ModInstanceTools.VersionsRootOf(Instance)
            Dim NewName As String = MyMsgBoxInput(ModLang.L("复制实例"), ModLang.L("新实例会得到独立的版本文件、Mod 与存档。"), Instance.Name & "-副本",
                New ObjectModel.Collection(Of Validate) From {
                    New ValidateFolderName(VersionsRoot.TrimEnd("\"c), True, Nothing)
                })
            If String.IsNullOrWhiteSpace(NewName) Then Return
            Dim ErrorText As String = ModInstanceTools.ValidateInstanceName(NewName, VersionsRoot)
            If ErrorText <> "" Then
                Hint(ErrorText, HintType.Red)
                Return
            End If
            RunInNewThread(
                Sub()
                    Try
                        ModInstanceTools.CopyInstance(Instance, NewName)
                        RunInUi(
                            Sub()
                                Hint(ModLang.L("实例已复制为 ") & NewName, HintType.Green)
                                FrmSelectRight?.RefreshList(True)
                            End Sub)
                    Catch ex As Exception
                        Logger.Error(ex, "复制实例失败", LogBehavior.Toast)
                    End Try
                End Sub, "Copy Instance")
        Catch ex As Exception
            Logger.Error(ex, "复制实例失败", LogBehavior.Alert)
        End Try
    End Sub

    Private Shared Sub BackupInstance(Instance As McInstance)
        Try
            Dim Target As String = Dialogs.SaveFile(ModLang.L("备份实例"), Instance.Name & "-备份-" & Now.ToString("yyyyMMdd-HHmmss") & ".zip",
                filter:={("zip", ModLang.L("压缩包"))})
            If String.IsNullOrEmpty(Target) Then Return
            RunInNewThread(
                Sub()
                    Try
                        ModInstanceTools.BackupInstance(Instance, Target)
                        RunInUi(Sub() Hint(ModLang.L("实例已备份到 ") & Target, HintType.Green))
                    Catch ex As Exception
                        Logger.Error(ex, "备份实例失败", LogBehavior.Alert)
                    End Try
                End Sub, "Backup Instance")
        Catch ex As Exception
            Logger.Error(ex, "备份实例失败", LogBehavior.Alert)
        End Try
    End Sub

    Private Shared Sub ChangeInstanceIcon(Instance As McInstance)
        Try
            Dim FileName As String = Dialogs.SelectFile(ModLang.L("选择图片"), False,
                filter:={({"png", "jpeg", "jpg", "gif", "webp"}, ModLang.L("常用图片文件"))}).FirstOrDefault()
            If String.IsNullOrEmpty(FileName) Then Return
            FileUtils.Copy(FileName, Instance.PathVersion & "TCL\Logo.png")
            WriteIni(Instance.PathVersion & "TCL\Setup.ini", "Logo", "TCL\Logo.png")
            WriteIni(Instance.PathVersion & "TCL\Setup.ini", "LogoCustom", True)
            WriteIni(McFolderSelected & "TCL.ini", "InstanceCache", "") '要求刷新缓存
            Hint(ModLang.L("实例图标已更新！"), HintType.Green)
            FrmSelectRight?.RefreshList(True)
        Catch ex As Exception
            Logger.Error(ex, "更换实例图标失败", LogBehavior.Alert)
        End Try
    End Sub

    '修改此代码时，同时修改 PageInstanceOverall 中的代码
    Public Shared Sub DeleteInstance(Item As MyListItem, Instance As McInstance)
        Try
            If McInstanceLock.IsLocked(Instance) Then
                Hint(ModLang.L("该版本正在被修改，请稍后再删除！"), HintType.Red)
                Return
            End If
            Dim IsShiftPressed As Boolean = My.Computer.Keyboard.ShiftKeyDown
            Dim IsHintIndie As Boolean = Instance.State <> McInstance.McInstanceState.Error AndAlso Instance.PathIndie <> McFolderSelected
            Dim SavesFolder = DirectoryUtils.GetInfo(Instance.PathIndie & "saves\")
            Dim SaveEntries = If(IsHintIndie AndAlso SavesFolder.Exists, SavesFolder.EnumerateDirectories.OrderByDescending(Function(Save) Save.LastWriteTime).Select(Function(Save) $"{Save.Name}（上次修改：{StringUtils.FormatTimeSpan(Save.LastWriteTime - Date.Now, False)}）").ToList, New List(Of String))
            Select Case MyMsgBox(ModLang.L("你确定要") & If(IsShiftPressed, ModLang.L("永久"), "") & ModLang.L("删除版本 ") & Instance.Name & ModLang.L(" 吗？") &
                        If(IsHintIndie, vbCrLf & ModLang.L("该版本对应的存档、资源包、Mod 等文件也将被一并删除！"), "") &
                        If(SaveEntries.Any, vbCrLf & vbCrLf & ModLang.L("这会删除以下存档：") & vbCrLf & "· " & SaveEntries.Join(vbCrLf & "· "), ""),
                        ModLang.L("版本删除确认"), , ModLang.L("取消"),, True)
                Case 1
                    Instance.ResetSettingsCache()
                    DirectoryUtils.Delete(Instance.PathVersion, Not IsShiftPressed)
                    Hint(ModLang.L("版本 ") & Instance.Name & ModLang.L(" 已删除！"), HintType.Green)
                    If McInstanceSelected IsNot Nothing AndAlso PathUtils.IsSame(Instance.PathVersion, McInstanceSelected.PathVersion) Then McInstanceSelected = Nothing
                    FrmSelectRight?.RefreshList(True)
                Case 2
                    Return
            End Select
        Catch ex As OperationCanceledException
            Logger.Warn(ex, $"删除版本 {Instance.Name} 被主动取消")
        Catch ex As Exception
            Logger.Error(ex, $"删除版本 {Instance.Name} 失败", LogBehavior.Alert)
        End Try
    End Sub

    Public Sub RefreshCurrent() Implements IRefreshable.Refresh
        RefreshList(True)
    End Sub

    Private Sub BtnDownload_Click(sender As Object, e As EventArgs) Handles BtnEmptyDownload.Click
        FrmMain.PageChange(FormMain.PageType.Download, FormMain.PageSubType.DownloadInstall)
    End Sub

    Public Sub BtnEmptyDownload_Loaded() Handles BtnEmptyDownload.Loaded
        Dim NewVisibility = If((Settings.Get(Of Boolean)("UiHiddenPageDownload") AndAlso Not PageSetupUI.HiddenForceShow) OrElse ShowHidden, Visibility.Collapsed, Visibility.Visible)
        If BtnEmptyDownload.Visibility <> NewVisibility Then
            BtnEmptyDownload.Visibility = NewVisibility
            PanLoad.TriggerForceResize()
        End If
    End Sub

#End Region

End Class
