Public Class PageSetupLeft

    Private IsLoad As Boolean = False
    Private IsPageSwitched As Boolean = False '如果在 Loaded 前切换到其他页面，会导致触发 Loaded 时再次切换一次
    Private Sub PageSetupLeft_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        '是否处于隐藏的子页面
        Dim IsHiddenPage As Boolean = False
        If ItemLaunch.Checked AndAlso Settings.Get(Of Boolean)("UiHiddenSetupLaunch") Then IsHiddenPage = True
        If ItemUI.Checked AndAlso Settings.Get(Of Boolean)("UiHiddenSetupUi") Then IsHiddenPage = True
        If ItemSystem.Checked AndAlso Settings.Get(Of Boolean)("UiHiddenSetupSystem") Then IsHiddenPage = True
        If ItemLink.Checked AndAlso Settings.Get(Of Boolean)("UiHiddenSetupLink") Then IsHiddenPage = True
        If PageSetupUI.HiddenForceShow Then IsHiddenPage = False
        '若页面错误，或尚未加载，则继续
        If IsLoad AndAlso Not IsHiddenPage Then Return
        IsLoad = True
        '刷新子页面隐藏情况
        PageSetupUI.HiddenRefresh()
        '选择第一个未被禁用的子页面
        If IsPageSwitched Then Return
        If Not Settings.Get(Of Boolean)("UiHiddenSetupLaunch") Then
            ItemLaunch.SetChecked(True, False, False)
        ElseIf Not Settings.Get(Of Boolean)("UiHiddenSetupUi") Then
            ItemUI.SetChecked(True, False, False)
        ElseIf Not Settings.Get(Of Boolean)("UiHiddenSetupSystem") Then
            ItemSystem.SetChecked(True, False, False)
        ElseIf Not Settings.Get(Of Boolean)("UiHiddenSetupLink") Then
            ItemLink.SetChecked(True, False, False)
        Else
            ItemLaunch.SetChecked(True, False, False)
        End If
    End Sub
    Private Sub PageOtherLeft_Unloaded(sender As Object, e As RoutedEventArgs) Handles Me.Unloaded
        IsPageSwitched = False
    End Sub

#Region "页面切换"

    ''' <summary>
    ''' 当前页面的编号。从左往右从 0 开始计算。
    ''' </summary>
    Public PageID As FormMain.PageSubType
    Public Sub New()
        InitializeComponent()
        '选择第一个未被禁用的子页面
        If Not Settings.Get(Of Boolean)("UiHiddenSetupLaunch") Then
            PageID = FormMain.PageSubType.SetupLaunch
        ElseIf Not Settings.Get(Of Boolean)("UiHiddenSetupUi") Then
            PageID = FormMain.PageSubType.SetupUI
        ElseIf Not Settings.Get(Of Boolean)("UiHiddenSetupSystem") Then
            PageID = FormMain.PageSubType.SetupSystem
        ElseIf Not Settings.Get(Of Boolean)("UiHiddenSetupLink") Then
            PageID = FormMain.PageSubType.SetupLink
        Else
            PageID = FormMain.PageSubType.SetupLaunch
        End If
    End Sub

    ''' <summary>
    ''' 勾选事件改变页面。
    ''' </summary>
    Private Sub PageCheck(sender As MyListItem, e As EventArgs) Handles ItemLaunch.Check, ItemSystem.Check, ItemUI.Check, ItemLink.Check, ItemJava.Check, ItemLog.Check, ItemCache.Check, ItemNetwork.Check, ItemMore.Check
        '尚未初始化控件属性时，sender.Tag 为 Nothing，会跳过切换，且由于 PageID 默认为 0 而切换到第一个页面
        '若使用 IsLoaded，则会导致模拟点击不被执行（模拟点击切换页面时，控件的 IsLoaded 为 False）
        If sender.Tag IsNot Nothing Then PageChange(Val(sender.Tag))
    End Sub

    ''' <summary>
    ''' 获取当前导航指定的右页面。
    ''' </summary>
    Public Function PageGet(Optional ID As FormMain.PageSubType = -1)
        If ID = -1 Then ID = PageID
        Select Case ID
            Case FormMain.PageSubType.SetupLaunch
                If FrmSetupLaunch Is Nothing Then FrmSetupLaunch = New PageSetupLaunch
                Return FrmSetupLaunch
            Case FormMain.PageSubType.SetupUI
                If FrmSetupUI Is Nothing Then FrmSetupUI = New PageSetupUI
                Return FrmSetupUI
            Case FormMain.PageSubType.SetupLink
                If FrmSetupLink Is Nothing Then FrmSetupLink = New PageSetupLink
                Return FrmSetupLink
            Case FormMain.PageSubType.SetupSystem
                If FrmSetupSystem Is Nothing Then FrmSetupSystem = New PageSetupSystem
                Return FrmSetupSystem
            Case FormMain.PageSubType.SetupJava
                If FrmSetupJava Is Nothing Then FrmSetupJava = New PageSetupJava
                Return FrmSetupJava
            Case FormMain.PageSubType.SetupLog
                If FrmSetupLog Is Nothing Then FrmSetupLog = New PageSetupLog
                Return FrmSetupLog
            Case FormMain.PageSubType.SetupMore
                If FrmSetupMore Is Nothing Then FrmSetupMore = New PageSetupMore
                Return FrmSetupMore
            Case FormMain.PageSubType.SetupCache
                If FrmSetupCache Is Nothing Then FrmSetupCache = New PageSetupCache
                Return FrmSetupCache
            Case FormMain.PageSubType.SetupNetwork
                If FrmSetupNetwork Is Nothing Then FrmSetupNetwork = New PageSetupNetwork
                Return FrmSetupNetwork
            Case Else
                Throw New Exception(ModLang.L("未知的设置子页面种类：") & ID)
        End Select
    End Function

    ''' <summary>
    ''' 按 Tag（即 SubType 数值）查找侧边栏项。第 113 轮：侧边栏顺序已调整
    ''' （「清理缓存」上移、「返回」固定为最后一项），高亮不再依赖 Children 的物理索引，
    ''' 故 FormMain 改用此函数按 Tag 匹配，与 PageOtherLeft.ItemGet 保持一致。
    ''' </summary>
    Public Function ItemGet(SubType As FormMain.PageSubType) As MyListItem
        Return PanItem.Children.OfType(Of MyListItem)().FirstOrDefault(Function(i) i.Tag IsNot Nothing AndAlso Val(i.Tag) = SubType)
    End Function

    ''' <summary>
    ''' 切换现有页面。
    ''' </summary>
    Public Sub PageChange(ID As FormMain.PageSubType)
        If PageID = ID Then Return
        AniControlEnabled += 1
        IsPageSwitched = True
        Try

            Select Case ID
                Case FormMain.PageSubType.SetupLaunch
                    If IsNothing(FrmSetupLaunch) Then FrmSetupLaunch = New PageSetupLaunch
                    PageChangeRun(FrmSetupLaunch)
                Case FormMain.PageSubType.SetupUI
                    If IsNothing(FrmSetupUI) Then FrmSetupUI = New PageSetupUI
                    PageChangeRun(FrmSetupUI)
                Case FormMain.PageSubType.SetupLink
                    If IsNothing(FrmSetupLink) Then FrmSetupLink = New PageSetupLink
                    PageChangeRun(FrmSetupLink)
                Case FormMain.PageSubType.SetupSystem
                    If IsNothing(FrmSetupSystem) Then FrmSetupSystem = New PageSetupSystem
                    PageChangeRun(FrmSetupSystem)
                Case FormMain.PageSubType.SetupJava
                    If IsNothing(FrmSetupJava) Then FrmSetupJava = New PageSetupJava
                    PageChangeRun(FrmSetupJava)
                Case FormMain.PageSubType.SetupLog
                    If IsNothing(FrmSetupLog) Then FrmSetupLog = New PageSetupLog
                    PageChangeRun(FrmSetupLog)
                Case FormMain.PageSubType.SetupMore
                    If IsNothing(FrmSetupMore) Then FrmSetupMore = New PageSetupMore
                    PageChangeRun(FrmSetupMore)
                Case FormMain.PageSubType.SetupCache
                    If IsNothing(FrmSetupCache) Then FrmSetupCache = New PageSetupCache
                    PageChangeRun(FrmSetupCache)
                Case FormMain.PageSubType.SetupNetwork
                    If IsNothing(FrmSetupNetwork) Then FrmSetupNetwork = New PageSetupNetwork
                    PageChangeRun(FrmSetupNetwork)
                Case Else
                    Throw New Exception(ModLang.L("未知的设置子页面种类：") & ID)
            End Select

            PageID = ID
        Catch ex As Exception
            Logger.Error(ex, $"切换分页面失败（ID {ID}）")
        Finally
            AniControlEnabled -= 1
        End Try
    End Sub
    Private Shared Sub PageChangeRun(Target As MyPageRight)
        AniStop("FrmMain PageChangeRight") '停止主页面的右页面切换动画，防止它与本动画一起触发多次 PageOnEnter
        If Target.Parent IsNot Nothing Then Target.SetValue(ContentPresenter.ContentProperty, Nothing)
        FrmMain.PageRight = Target
        CType(FrmMain.PanMainRight.Child, MyPageRight).PageOnExit()
        AniStart({
            AaCode(
            Sub()
                CType(FrmMain.PanMainRight.Child, MyPageRight).PageOnForceExit()
                FrmMain.PanMainRight.Child = FrmMain.PageRight
                FrmMain.PageRight.Opacity = 0
            End Sub, 130),
            AaCode(
            Sub()
                '延迟触发页面通用动画，以使得在 Loaded 事件中加载的控件得以处理
                FrmMain.PageRight.Opacity = 1
                FrmMain.PageRight.PageOnEnter()
            End Sub, 30, True)
        }, "PageLeft PageChange")
    End Sub

#End Region

    Public Sub Reset(sender As Object, e As EventArgs)
        Select Case Val(sender.Tag)
            Case FormMain.PageSubType.SetupLaunch
                If MyMsgBox(ModLang.L("是否要初始化 启动 页面的所有设置？该操作不可撤销。"), ModLang.L("初始化确认"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
                    If IsNothing(FrmSetupLaunch) Then FrmSetupLaunch = New PageSetupLaunch
                    FrmSetupLaunch.Reset()
                    ItemLaunch.Checked = True
                End If
            Case FormMain.PageSubType.SetupUI
                If MyMsgBox(ModLang.L("是否要初始化 个性化 页面的所有设置？该操作不可撤销。") & vbCrLf & ModLang.L("（背景图片与音乐、主页等外部文件不会被删除）"), ModLang.L("初始化确认"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
                    If IsNothing(FrmSetupUI) Then FrmSetupUI = New PageSetupUI
                    FrmSetupUI.Reset()
                    ItemUI.Checked = True
                End If
            Case FormMain.PageSubType.SetupSystem
                If MyMsgBox(ModLang.L("是否要初始化 系统 页面的所有设置？该操作不可撤销。"), ModLang.L("初始化确认"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
                    If IsNothing(FrmSetupSystem) Then FrmSetupSystem = New PageSetupSystem
                    FrmSetupSystem.Reset()
                    ItemSystem.Checked = True
                End If
            Case FormMain.PageSubType.SetupLink
                If MyMsgBox(ModLang.L("是否要初始化 联机 页面的所有设置？该操作不可撤销。"), ModLang.L("初始化确认"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
                    If IsNothing(FrmSetupLink) Then FrmSetupLink = New PageSetupLink
                    FrmSetupLink.Reset()
                    ItemLink.Checked = True
                End If
            Case FormMain.PageSubType.SetupJava
                If MyMsgBox(ModLang.L("是否要初始化 Java 页面的所有设置？该操作不可撤销。") & vbCrLf & ModLang.L("这将清空已保存的 Java 列表，并重新搜索电脑上所有的 Java。"), ModLang.L("初始化确认"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
                    Configs.JavaList.Reset()
                    Configs.JavaRemovedList.Reset()
                    ManuallySearchJava()
                    If IsNothing(FrmSetupJava) Then FrmSetupJava = New PageSetupJava
                    FrmSetupJava.LoadList()
                    ItemJava.Checked = True
                End If
        End Select
    End Sub

    ''' <summary>
    ''' 返回上一个主页面（左列「返回」按钮）。
    ''' PageLast 在进入设置页之前的最后一次主页面切换时被更新，
    ''' 设置页内各子页面切换不会改写它，故始终指向「打开设置前的那一级」。
    ''' </summary>
    Private Sub ItemBack_Click(sender As Object, e As System.Windows.Input.MouseButtonEventArgs) Handles ItemBack.Click
        FrmMain.PageChange(FrmMain.PageLast)
    End Sub

#Region "设置搜索（第 129 轮）"

    ''' <summary>搜索索引条目：一个带 SettingService.Key 的控件对应一条。</summary>
    Private Class SetupSearchEntry
        Public Title As String
        Public Keywords As String
        Public Page As FormMain.PageSubType
        Public PageName As String
        Public Icon As String
        Public Control As DependencyObject
    End Class

    ''' <summary>参与搜索的子页面（与侧边栏 + PageGet 一致）。</summary>
    Private Shared ReadOnly SearchPages As New List(Of FormMain.PageSubType) From {
        FormMain.PageSubType.SetupLaunch, FormMain.PageSubType.SetupLink, FormMain.PageSubType.SetupUI,
        FormMain.PageSubType.SetupSystem, FormMain.PageSubType.SetupJava, FormMain.PageSubType.SetupLog,
        FormMain.PageSubType.SetupCache, FormMain.PageSubType.SetupNetwork, FormMain.PageSubType.SetupMore}

    ''' <summary>搜索索引，首次搜索时惰性构建。</summary>
    Private SearchIndex As List(Of SetupSearchEntry) = Nothing

    Private Sub TxtSearch_TextChanged(sender As Object, e As System.Windows.Controls.TextChangedEventArgs) Handles TxtSearch.TextChanged
        ApplySearch(TxtSearch.Text)
    End Sub

    ''' <summary>执行搜索：非空时把侧边栏切换为结果列表，空时恢复导航。</summary>
    Private Sub ApplySearch(Query As String)
        Query = If(Query, "").Trim()
        If Query = "" Then
            PanItem.Visibility = Visibility.Visible
            PanSearchResult.Visibility = Visibility.Collapsed
            Return
        End If
        If SearchIndex Is Nothing Then BuildSearchIndex()
        PanItem.Visibility = Visibility.Collapsed
        PanSearchResult.Visibility = Visibility.Visible
        '清除旧行（保留空态提示）
        For Each Row In PanSearchResult.Children.OfType(Of MyListItem)().ToList()
            PanSearchResult.Children.Remove(Row)
        Next
        Dim Results As New List(Of SetupSearchEntry)
        If SearchIndex IsNot Nothing Then
            Dim QueryLower = Query.ToLowerInvariant()
            Results = SearchIndex.Where(Function(E) E.Keywords.Contains(QueryLower)).Take(30).ToList()
        End If
        For Each Entry In Results
            Dim Row As New MyListItem With {
                .Title = Entry.Title, .Info = ModLang.L(Entry.PageName), .Type = MyListItem.CheckType.Clickable,
                .Height = 34, .MinPaddingRight = 10, .IsScaleAnimationEnabled = False,
                .SvgIcon = Entry.Icon, .LogoScale = 0.85, .Tag = Entry}
            AddHandler Row.Click, AddressOf SearchRow_Click
            PanSearchResult.Children.Add(Row)
        Next
        LabSearchEmpty.Text = If(SearchIndex Is Nothing, ModLang.L("搜索初始化失败，请查看日志"), ModLang.L("没有找到相关设置"))
        LabSearchEmpty.Visibility = If(Results.Count = 0, Visibility.Visible, Visibility.Collapsed)
    End Sub

    Private Sub SearchRow_Click(sender As Object, e As MouseButtonEventArgs)
        Dim Entry = TryCast(TryCast(sender, MyListItem)?.Tag, SetupSearchEntry)
        If Entry IsNot Nothing Then NavigateToEntry(Entry)
    End Sub

    ''' <summary>跳转到结果所在的子页面并滚动高亮到控件。</summary>
    Private Sub NavigateToEntry(Entry As SetupSearchEntry)
        TxtSearch.Text = "" '恢复导航列表
        If FrmMain.PageCurrent.Page = FormMain.PageType.Setup Then
            PageChange(Entry.Page) '已在设置页内，只切子页
        Else
            FrmMain.PageChange(New FormMain.PageStackData With {.Page = FormMain.PageType.Setup}, Entry.Page)
        End If
        HighlightEntry(Entry)
    End Sub

    ''' <summary>等切页动画结束后，滚动到目标控件并闪烁两下；所在折叠卡自动展开。</summary>
    Private Sub HighlightEntry(Entry As SetupSearchEntry)
        Dim Ctrl = Entry.Control
        If Ctrl Is Nothing Then Return
        RunInThread(Sub()
                        Thread.Sleep(600)
                        RunInUi(Sub()
                                    Try
                                        '自动展开所在折叠卡（如调试选项）
                                        Dim Anc As DependencyObject = Ctrl
                                        While Anc IsNot Nothing
                                            If TypeOf Anc Is MyCard AndAlso DirectCast(Anc, MyCard).IsSwapped Then DirectCast(Anc, MyCard).IsSwapped = False
                                            Anc = LogicalTreeHelper.GetParent(Anc)
                                        End While
                                        Dim Element = TryCast(Ctrl, FrameworkElement)
                                        If Element Is Nothing Then Return
                                        Element.BringIntoView()
                                        '闪烁两下（1→0.25→1 ×2），结束后回到正常透明度
                                        Dim Anim As New System.Windows.Media.Animation.DoubleAnimationUsingKeyFrames With {.FillBehavior = System.Windows.Media.Animation.FillBehavior.Stop}
                                        Anim.KeyFrames.Add(New System.Windows.Media.Animation.LinearDoubleKeyFrame(1, TimeSpan.Zero))
                                        Anim.KeyFrames.Add(New System.Windows.Media.Animation.LinearDoubleKeyFrame(0.25, TimeSpan.FromMilliseconds(180)))
                                        Anim.KeyFrames.Add(New System.Windows.Media.Animation.LinearDoubleKeyFrame(1, TimeSpan.FromMilliseconds(360)))
                                        Anim.KeyFrames.Add(New System.Windows.Media.Animation.LinearDoubleKeyFrame(0.25, TimeSpan.FromMilliseconds(540)))
                                        Anim.KeyFrames.Add(New System.Windows.Media.Animation.LinearDoubleKeyFrame(1, TimeSpan.FromMilliseconds(720)))
                                        Element.BeginAnimation(UIElement.OpacityProperty, Anim)
                                    Catch ex As Exception
                                        Logger.Warn(ex, "设置搜索跳转高亮失败")
                                    End Try
                                End Sub)
                    End Sub)
    End Sub

    ''' <summary>空闲预热搜索索引：补齐所有尚未构建的子页（第 130 轮体检 P1）。</summary>
    Public Sub WarmSearchIndex()
        If SearchIndex Is Nothing Then SearchIndex = New List(Of SetupSearchEntry)
        For Each PageId In SearchPages
            WarmSearchIndexPage(PageId)
        Next
    End Sub

    ''' <summary>单个子页的索引预热：分片调用，避免一次性实例化全部子页造成 UI 冻结（第 131 轮）。</summary>
    Public Sub WarmSearchIndexPage(PageId As FormMain.PageSubType)
        If SearchIndex Is Nothing Then SearchIndex = New List(Of SetupSearchEntry)
        If SearchIndex.Any(Function(E) E.Page = PageId) Then Return
        Try
            CollectSearchEntries(PageGet(PageId), PageId, SearchPageName(PageId), SearchIndex)
        Catch ex As Exception
            Logger.Error(ex, $"构建设置搜索索引失败（{PageId}）")
            '失败页记一条空占位，避免每次搜索都重试构建
            SearchIndex.Add(New SetupSearchEntry With {.Title = PageId.ToString, .Keywords = "", .Page = PageId, .PageName = "", .Icon = "", .Control = Nothing})
        End Try
    End Sub

    ''' <summary>供全局搜索（Ctrl+K）读取设置项索引：(标题, 关键词, 子页, 页名, 图标, 设置键名)。</summary>
    Public Function GetSearchEntriesForGlobal() As List(Of Tuple(Of String, String, FormMain.PageSubType, String, String, String))
        If SearchIndex Is Nothing Then BuildSearchIndex()
        Dim Result As New List(Of Tuple(Of String, String, FormMain.PageSubType, String, String, String))
        If SearchIndex Is Nothing Then Return Result
        For Each E In SearchIndex
            Result.Add(Tuple.Create(E.Title, E.Keywords, E.Page, E.PageName, E.Icon, ""))
        Next
        Return Result
    End Function

    ''' <summary>供全局搜索跳转设置项：在索引中按标题+键名找回条目，走本地跳转（含滚动高亮）。</summary>
    Public Sub GlobalJumpToSetting(Page As FormMain.PageSubType, Title As String)
        If SearchIndex Is Nothing Then BuildSearchIndex()
        If SearchIndex Is Nothing Then Return
        Dim Entry = SearchIndex.FirstOrDefault(Function(E) E.Page = Page AndAlso E.Title = Title)
        If Entry IsNot Nothing Then NavigateToEntry(Entry)
    End Sub

    ''' <summary>构建全部设置子页的搜索索引。子页实例本就是常驻单例，构建后直接复用。</summary>
    Private Sub BuildSearchIndex()
        If SearchIndex Is Nothing Then SearchIndex = New List(Of SetupSearchEntry)
        For Each PageId In SearchPages
            If Not SearchIndex.Any(Function(E) E.Page = PageId) Then
                Try
                    CollectSearchEntries(PageGet(PageId), PageId, SearchPageName(PageId), SearchIndex)
                Catch ex As Exception
                    Logger.Error(ex, $"构建设置搜索索引失败（{PageId}）")
                    SearchIndex.Add(New SetupSearchEntry With {.Title = PageId.ToString, .Keywords = "", .Page = PageId, .PageName = "", .Icon = "", .Control = Nothing})
                End Try
            End If
        Next
    End Sub

    ''' <summary>深度优先遍历逻辑树，收集带 SettingService.Key 的控件。</summary>
    Private Sub CollectSearchEntries(Root As DependencyObject, PageId As FormMain.PageSubType, PageName As String, Target As List(Of SetupSearchEntry))
        For Each Child In LogicalTreeHelper.GetChildren(Root).OfType(Of DependencyObject)().ToArray()
            CollectSearchEntries(Child, PageId, PageName, Target)
        Next
        Try
            Dim Key = SettingService.GetKey(Root)
            If String.IsNullOrEmpty(Key) Then Return
            Dim Element = TryCast(Root, FrameworkElement)
            If Element Is Nothing Then Return
            '标题：自身文案 → 同 Grid 行左侧标签 → 所在卡片标题
            Dim Title As String = ""
            If TypeOf Root Is MyCheckBox Then
                Title = If(DirectCast(Root, MyCheckBox).Text, "")
            ElseIf TypeOf Root Is MyRadioBox Then
                Title = If(DirectCast(Root, MyRadioBox).Text, "")
            ElseIf TypeOf Root Is MyListItem Then
                Title = If(DirectCast(Root, MyListItem).Title, "")
            End If
            If Title = "" Then Title = FindRowLabel(Element)
            If Title = "" Then Title = FindCardTitle(Element)
            '关键词：标题 + 提示 + 设置键名
            Dim Keywords As New List(Of String)
            If Title <> "" Then Keywords.Add(Title)
            Dim Tip = If(Element.ToolTip, "")?.ToString()
            If Not String.IsNullOrWhiteSpace(Tip) Then Keywords.Add(Tip)
            Keywords.Add(Key)
            Target.Add(New SetupSearchEntry With {
                .Title = If(Title <> "", Title, Key),
                .Keywords = String.Join(" ", Keywords).ToLowerInvariant(),
                .Page = PageId, .PageName = PageName,
                .Icon = SearchPageIcon(PageId), .Control = Element})
        Catch ex As Exception
            Logger.Warn(ex, "收集设置搜索条目失败")
        End Try
    End Sub

    ''' <summary>在控件所在的 Grid 中寻找同一行左列的标签文本。</summary>
    Private Function FindRowLabel(Element As FrameworkElement) As String
        Dim ParentGrid = TryCast(Element.Parent, Grid)
        If ParentGrid Is Nothing Then Return ""
        Dim Row = Grid.GetRow(Element)
        For Each Child As UIElement In ParentGrid.Children
            If TypeOf Child Is TextBlock AndAlso Grid.GetColumn(Child) = 0 AndAlso Grid.GetRow(Child) = Row Then
                Dim Text = DirectCast(Child, TextBlock).Text
                If Not String.IsNullOrWhiteSpace(Text) Then Return Text
            End If
        Next
        Return ""
    End Function

    ''' <summary>向上寻找控件所在卡片的标题。</summary>
    Private Function FindCardTitle(Element As DependencyObject) As String
        Dim Anc As DependencyObject = Element
        While Anc IsNot Nothing
            If TypeOf Anc Is MyCard Then Return If(DirectCast(Anc, MyCard).Title, "")
            Anc = LogicalTreeHelper.GetParent(Anc)
        End While
        Return ""
    End Function

    Private Function SearchPageName(ID As FormMain.PageSubType) As String
        Select Case ID
            Case FormMain.PageSubType.SetupLaunch : Return "启动"
            Case FormMain.PageSubType.SetupLink : Return "联机"
            Case FormMain.PageSubType.SetupUI : Return "个性化"
            Case FormMain.PageSubType.SetupSystem : Return "系统"
            Case FormMain.PageSubType.SetupJava : Return "Java"
            Case FormMain.PageSubType.SetupLog : Return "日志"
            Case FormMain.PageSubType.SetupCache : Return "存储与清理"
            Case FormMain.PageSubType.SetupNetwork : Return "网络"
            Case Else : Return "更多"
        End Select
    End Function

    Private Function SearchPageIcon(ID As FormMain.PageSubType) As String
        Select Case ID
            Case FormMain.PageSubType.SetupLaunch : Return "lucide/play"
            Case FormMain.PageSubType.SetupLink : Return "lucide/globe"
            Case FormMain.PageSubType.SetupUI : Return "lucide/palette"
            Case FormMain.PageSubType.SetupSystem : Return "lucide/cpu"
            Case FormMain.PageSubType.SetupJava : Return "lucide/coffee"
            Case FormMain.PageSubType.SetupLog : Return "lucide/scroll-text"
            Case FormMain.PageSubType.SetupCache : Return "lucide/trash-2"
            Case FormMain.PageSubType.SetupNetwork : Return "lucide/network"
            Case Else : Return "lucide/settings"
        End Select
    End Function

#End Region

End Class
