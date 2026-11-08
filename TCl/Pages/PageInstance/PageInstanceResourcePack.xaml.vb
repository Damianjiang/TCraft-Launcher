''' <summary>
''' 实例的资源包管理页：和 Mod 管理页同款布局，支持多选、筛选、底部操作面板，通过改后缀控制启用状态。
''' </summary>
Public Class PageInstanceResourcePack

#Region "数据"

    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property

    ''' <summary>当前实例的 resourcepacks 文件夹（带结尾斜杠，不存在则创建）。</summary>
    Private ReadOnly Property PackFolder As String
        Get
            Return ModInstanceContent.GetPacksFolder(Instance, "resourcepacks")
        End Get
    End Property

    ''' <summary>当前列出的全部资源包（未筛选、未搜索）。</summary>
    Private AllPacks As New List(Of ModInstanceContent.PackEntry)

    ''' <summary>当前选中的资源包（用于批量操作）。</summary>
    Private SelectedPacks As New List(Of ModInstanceContent.PackEntry)

    ''' <summary>当前筛选类型</summary>
    Private _Filter As FilterType = FilterType.All
    Public Property Filter As FilterType
        Get
            Return _Filter
        End Get
        Set(value As FilterType)
            If _Filter = value Then Return
            _Filter = value
            Select Case value
                Case FilterType.All
                    BtnFilterAll.Checked = True
                Case FilterType.Enabled
                    BtnFilterEnabled.Checked = True
                Case FilterType.Disabled
                    BtnFilterDisabled.Checked = True
            End Select
            Rebuild()
        End Set
    End Property
    Public Enum FilterType As Integer
        All = 0
        Enabled = 1
        Disabled = 2
    End Enum

    Private IsInit As Boolean = False
    Private IsRefreshing As Boolean = False

#End Region

#Region "初始化"

    Private Sub PageInstanceResourcePack_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If Not IsInit Then
            IsInit = True
            InitText()
            InitEvents()
            InitDragDrop()
        End If
        Refresh()
    End Sub

    ''' <summary>固定文案统一在这里套翻译。</summary>
    Private Sub InitText()
        HintRuntime.Text = ModLang.L("游戏运行时修改可能被游戏覆盖，建议关闭游戏后再调整。")
        BtnRefresh.Text = ModLang.L("刷新")
        BtnOpen.Text = ModLang.L("打开文件夹")
        BtnEnableAll.Text = ModLang.L("全部启用")
        BtnDisableAll.Text = ModLang.L("全部停用")
        TxtSearch.HintText = ModLang.L("搜索资源包")
        BtnFilterEnabled.Text = ModLang.L("已启用")
        BtnFilterDisabled.Text = ModLang.L("已禁用")
        LabSelect.Text = ModLang.L("已选择 0 个文件")
        BtnSelectEnable.Text = ModLang.L("启用")
        BtnSelectDisable.Text = ModLang.L("禁用")
        BtnSelectDelete.Text = ModLang.L("删除")
        BtnSelectCancel.Text = ModLang.L("取消选择")
    End Sub

    Private Sub InitEvents()
        '搜索
        AddHandler TxtSearch.TextChanged, Sub(snd, args) Rebuild()
        '顶部按钮
        AddHandler BtnRefresh.Click, Sub(snd, args) RefreshWithHint()
        AddHandler BtnOpen.Click, Sub(snd, args) OpenPackFolder()
        AddHandler BtnEnableAll.Click, Sub(snd, args) SetAllPacks(True)
        AddHandler BtnDisableAll.Click, Sub(snd, args) SetAllPacks(False)
        '底部操作面板按钮
        AddHandler BtnSelectEnable.Click, Sub(snd, args) BatchSetEnabled(True)
        AddHandler BtnSelectDisable.Click, Sub(snd, args) BatchSetEnabled(False)
        AddHandler BtnSelectDelete.Click, Sub(snd, args) BatchDelete()
        AddHandler BtnSelectCancel.Click, Sub(snd, args) ClearSelection()
    End Sub

    ''' <summary>整页接受 .zip 拖入安装。</summary>
    Private Sub InitDragDrop()
        For Each Target As UIElement In New UIElement() {Me, PanBack, PanMain, PanList, CardPacks}
            Target.AllowDrop = True
            AddHandler Target.DragOver, AddressOf HandleDragOver
            AddHandler Target.Drop, AddressOf HandleDrop
        Next
    End Sub

#End Region

#Region "刷新"

    ''' <summary>切换页面时由主控调用。</summary>
    Public Sub Reload()
        Refresh()
    End Sub

    ''' <summary>带提示的刷新。</summary>
    Private Sub RefreshWithHint()
        Hint(ModLang.L("正在刷新……"), Log:=False)
        Refresh()
    End Sub

    ''' <summary>重新读取磁盘列表（扫描放后台线程，期间显示镐子加载卡）。</summary>
    Public Sub Refresh(Optional OnFinished As Action = Nothing)
        If IsRefreshing Then Return
        Dim CurrentInstance = Instance
        If CurrentInstance Is Nothing Then
            PanList.Children.Clear()
            ApplyRefresh(Nothing, Nothing)
            If OnFinished IsNot Nothing Then OnFinished()
            Return
        End If
        IsRefreshing = True
        SelectedPacks.Clear()
        HideSelectCard()
        PanList.Children.Clear()
        PanLoad.Visibility = Visibility.Visible
        Load.State.LoadingState = MyLoading.MyLoadingState.Run
        RunInNewThread(
        Sub()
            Try
                Dim Folder As String = Nothing
                Try : Folder = ModInstanceContent.GetPacksFolder(CurrentInstance, "resourcepacks") : Catch : End Try
                Dim Entries As List(Of ModInstanceContent.PackEntry) = Nothing
                If Folder IsNot Nothing Then Entries = ModInstanceContent.ListPacks(Folder)
                RunInUi(Sub()
                            ApplyRefresh(Folder, Entries)
                            FinishRefresh(OnFinished)
                        End Sub)
            Catch ex As Exception
                RunInUi(Sub()
                            Logger.Error(ex, "刷新资源包列表失败")
                            Load.State.LoadingState = MyLoading.MyLoadingState.Stop
                            PanLoad.Visibility = Visibility.Collapsed
                            IsRefreshing = False
                            Hint(ModLang.L("刷新失败：") & ex.Message, HintType.Red)
                        End Sub)
            End Try
        End Sub)
    End Sub

    Private Sub FinishRefresh(OnFinished As Action)
        Load.State.LoadingState = MyLoading.MyLoadingState.Stop
        PanLoad.Visibility = Visibility.Collapsed
        IsRefreshing = False
        If OnFinished IsNot Nothing Then OnFinished()
    End Sub

    ''' <summary>刷新完成后应用数据。</summary>
    Private Sub ApplyRefresh(Folder As String, Entries As List(Of ModInstanceContent.PackEntry))
        AllPacks = If(Entries, New List(Of ModInstanceContent.PackEntry))
        Rebuild()
    End Sub

#End Region

#Region "列表重建与筛选"

    ''' <summary>点击筛选项触发的改变，和 Mod 页逻辑完全一致</summary>
    Private Sub ChangeFilter(sender As MyRadioButton, raiseByMouse As Boolean) Handles BtnFilterAll.Check, BtnFilterDisabled.Check, BtnFilterEnabled.Check
        Filter = CType(sender.Tag, FilterType)
    End Sub

    ''' <summary>根据筛选、搜索条件重新生成列表。</summary>
    Private Sub Rebuild()
        If PanList Is Nothing Then Return
        PanList.Children.Clear()
        '先按筛选过滤
        Dim Query = AllPacks.AsEnumerable()
        Select Case Filter
            Case FilterType.Enabled
                Query = Query.Where(Function(p) p.IsEnabled)
            Case FilterType.Disabled
                Query = Query.Where(Function(p) Not p.IsEnabled)
        End Select
        '再按搜索过滤
        Dim Keyword = TxtSearch.Text?.Trim()
        If Not String.IsNullOrEmpty(Keyword) Then
            Query = Query.Where(Function(p) p.Name.IndexOf(Keyword, StringComparison.OrdinalIgnoreCase) >= 0)
        End If
        Dim Shown = Query.ToList()
        For Each PackOne In Shown
            PanList.Children.Add(BuildRow(PackOne))
        Next
        '更新选中状态，移除已经不显示的选中项
        SelectedPacks = SelectedPacks.Where(Function(p) Shown.Contains(p)).ToList()
        UpdateSelectCard()
    End Sub

#End Region

#Region "列表项"

    ''' <summary>生成一行：和 Mod 管理页完全同款的 MyLocalPackItem。</summary>
    Private Function BuildRow(Entry As ModInstanceContent.PackEntry) As MyLocalPackItem
        Dim NewItem As New MyLocalPackItem With {
            .SnapsToDevicePixels = True,
            .Entry = Entry,
            .Checked = SelectedPacks.Contains(Entry)
        }
        NewItem.ButtonHandler = AddressOf SetupPackItemButtons
        Return NewItem
    End Function

    ''' <summary>给列表项绑定事件和右侧按钮，和 Mod 页 McModContent 逻辑一致。</summary>
    Private Sub SetupPackItemButtons(sender As MyLocalPackItem, e As EventArgs)
        Dim Entry = sender.Entry
        '点击切换选中
        AddHandler sender.Changed, Sub(snd, args)
                                      If sender.Checked Then
                                          If Not SelectedPacks.Contains(Entry) Then SelectedPacks.Add(Entry)
                                      Else
                                          SelectedPacks.Remove(Entry)
                                      End If
                                      UpdateSelectCard()
                                  End Sub
        AddHandler sender.Click, Sub(ss As MyLocalPackItem, ee As EventArgs) ss.Checked = Not ss.Checked
        '打开位置按钮
        Dim BtnOpen As New MyIconButton With {.LogoScale = 1.05, .SvgIcon = "lucide/folder-open", .Tag = sender}
        BtnOpen.ToolTip = ModLang.L("打开文件位置")
        AddHandler BtnOpen.Click, Sub(snd, args) ShowInFolder(Entry)
        '删除按钮
        Dim BtnDelete As New MyIconButton With {.LogoScale = 1, .SvgIcon = "lucide/trash-2", .Tag = sender}
        BtnDelete.ToolTip = ModLang.L("删除")
        AddHandler BtnDelete.Click, Sub(snd, args) DeleteSinglePack(Entry)
        sender.Buttons = {BtnOpen, BtnDelete}
    End Sub

#End Region

#Region "底部操作面板"

    ''' <summary>更新底部操作面板的显示状态和文字。</summary>
    Private Sub UpdateSelectCard()
        If SelectedPacks.Count > 0 Then
            LabSelect.Text = ModLang.L("已选择 ") & SelectedPacks.Count & ModLang.L(" 个文件")
            ShowSelectCard()
        Else
            HideSelectCard()
        End If
    End Sub

    Private Sub ShowSelectCard()
        If CardSelect.Visibility = Visibility.Visible Then Return
        CardSelect.Visibility = Visibility.Visible
        '简单淡入+上移动画，和 Mod 页一致
        Dim AniOpacity As New Animation.DoubleAnimation(0, 1, New Duration(TimeSpan.FromMilliseconds(150)))
        Dim AniY As New Animation.DoubleAnimation(10, 0, New Duration(TimeSpan.FromMilliseconds(150)))
        CardSelect.BeginAnimation(OpacityProperty, AniOpacity)
        TransSelect.BeginAnimation(TranslateTransform.YProperty, AniY)
    End Sub

    Private Sub HideSelectCard()
        If CardSelect.Visibility = Visibility.Collapsed Then Return
        Dim AniOpacity As New Animation.DoubleAnimation(1, 0, New Duration(TimeSpan.FromMilliseconds(100)))
        Dim AniY As New Animation.DoubleAnimation(0, 10, New Duration(TimeSpan.FromMilliseconds(100)))
        AddHandler AniOpacity.Completed, Sub()
                                             CardSelect.Visibility = Visibility.Collapsed
                                         End Sub
        CardSelect.BeginAnimation(OpacityProperty, AniOpacity)
        TransSelect.BeginAnimation(TranslateTransform.YProperty, AniY)
    End Sub

    Private Sub ClearSelection()
        SelectedPacks.Clear()
        Rebuild()
    End Sub

#End Region

#Region "批量操作"

    ''' <summary>批量启用/禁用选中的包。</summary>
    Private Sub BatchSetEnabled(NewEnabled As Boolean)
        If SelectedPacks.Count = 0 Then Return
        Dim Targets = SelectedPacks.ToList()
        RunInNewThread(Sub()
                           Dim Failed = 0
                           For Each Entry In Targets
                               Try
                                   ModInstanceContent.SetPackEnabledByRename(Entry, NewEnabled)
                               Catch
                                   Failed += 1
                               End Try
                           Next
                           RunInUi(Sub()
                                       Refresh()
                                       If Failed = 0 Then
                                           Hint(If(NewEnabled, ModLang.L("已启用选中的资源包"), ModLang.L("已禁用选中的资源包")), HintType.Green)
                                       Else
                                           Hint(ModLang.L("部分操作失败，失败数量：") & Failed, HintType.Red)
                                       End If
                                   End Sub)
                       End Sub)
    End Sub

    ''' <summary>批量删除选中的包。</summary>
    Private Sub BatchDelete()
        If SelectedPacks.Count = 0 Then Return
        If MyMsgBox(ModLang.L("确定要删除选中的 ") & SelectedPacks.Count & ModLang.L(" 个资源包吗？此操作不可恢复。"),
                        ModLang.L("确认删除"), ModLang.L("删除"), ModLang.L("取消"), True) <> 1 Then Return
        Dim Targets = SelectedPacks.ToList()
        RunInNewThread(Sub()
                           Dim Failed = 0
                           For Each Entry In Targets
                               Try
                                   If Entry.IsFolder Then
                                       DirectoryUtils.Delete(Entry.FullPath)
                                   Else
                                       FileUtils.Delete(Entry.FullPath)
                                   End If
                               Catch
                                   Failed += 1
                               End Try
                           Next
                           RunInUi(Sub()
                                       Refresh()
                                       If Failed = 0 Then
                                           Hint(ModLang.L("已删除选中的资源包"), HintType.Green)
                                       Else
                                           Hint(ModLang.L("部分删除失败，失败数量：") & Failed, HintType.Red)
                                       End If
                                   End Sub)
                       End Sub)
    End Sub

    ''' <summary>删除单个包。</summary>
    Private Sub DeleteSinglePack(Entry As ModInstanceContent.PackEntry)
        If MyMsgBox(ModLang.L("确定要删除这个资源包吗？此操作不可恢复。"),
                        ModLang.L("确认删除"), ModLang.L("删除"), ModLang.L("取消"), True) <> 1 Then Return
        RunInNewThread(Sub()
                           Try
                               If Entry.IsFolder Then
                                   DirectoryUtils.Delete(Entry.FullPath)
                               Else
                                   FileUtils.Delete(Entry.FullPath)
                               End If
                               RunInUi(Sub()
                                           Refresh()
                                           Hint(ModLang.L("已删除资源包"), HintType.Green)
                                       End Sub)
                           Catch ex As Exception
                               RunInUi(Sub() Hint(ModLang.L("删除失败：") & ex.Message, HintType.Red))
                           End Try
                       End Sub)
    End Sub

    ''' <summary>全部启用/禁用。</summary>
    Private Sub SetAllPacks(Enabled As Boolean)
        If Instance Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        If AllPacks.Count = 0 Then
            Hint(ModLang.L("这个实例还没有资源包。"), HintType.Blue)
            Return
        End If
        RunInNewThread(Sub()
                           Dim Failed = 0
                           For Each Entry In AllPacks
                               If Entry.IsEnabled = Enabled Then Continue For
                               Try
                                   ModInstanceContent.SetPackEnabledByRename(Entry, Enabled)
                               Catch
                                   Failed += 1
                               End Try
                           Next
                           RunInUi(Sub()
                                       Refresh()
                                       If Failed = 0 Then
                                           Hint(If(Enabled, ModLang.L("已启用全部资源包。"), ModLang.L("已停用全部资源包。")), HintType.Green)
                                       Else
                                           Hint(ModLang.L("部分操作失败，失败数量：") & Failed, HintType.Red)
                                       End If
                                   End Sub)
                       End Sub)
    End Sub

#End Region

#Region "其他操作"

    ''' <summary>在资源管理器中定位。</summary>
    Private Sub ShowInFolder(Entry As ModInstanceContent.PackEntry)
        Try
            If Entry Is Nothing OrElse String.IsNullOrEmpty(Entry.FullPath) Then Return
            If Entry.IsFolder Then
                If Not DirectoryUtils.Exists(Entry.FullPath) Then
                    Hint(ModLang.L("文件夹不存在！"), HintType.Red)
                    Return
                End If
                OpenExplorer(PathUtils.AddSlashSuffix(Entry.FullPath))
            Else
                If Not FileUtils.Exists(Entry.FullPath) Then
                    Hint(ModLang.L("文件不存在！"), HintType.Red)
                    Return
                End If
                OpenExplorer("/select," & Entry.FullPath)
            End If
        Catch ex As Exception
            Logger.Error(ex, "打开资源包位置失败")
            Hint(ModLang.L("打开失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>打开资源包文件夹。</summary>
    Private Sub OpenPackFolder()
        Try
            Dim Folder = PackFolder
            If Folder Is Nothing Then
                Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
                Return
            End If
            OpenExplorer(PathUtils.AddSlashSuffix(Folder))
        Catch ex As Exception
            Logger.Error(ex, "打开资源包文件夹失败")
            Hint(ModLang.L("打开失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

#End Region

#Region "拖放安装"

    Private Sub HandleDragOver(sender As Object, e As DragEventArgs)
        e.Effects = If(e.Data.GetDataPresent(DataFormats.FileDrop), DragDropEffects.Copy, DragDropEffects.None)
        e.Handled = True
    End Sub

    Private Sub HandleDrop(sender As Object, e As DragEventArgs)
        Try
            Dim Files = CType(e.Data.GetData(DataFormats.FileDrop), String())
            If Files Is Nothing OrElse Files.Length = 0 Then Return
            Dim Folder = PackFolder
            If Folder Is Nothing Then
                Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
                Return
            End If
            InstallFiles(Files, Folder)
            e.Handled = True
        Catch ex As Exception
            Logger.Error(ex, "拖放安装资源包失败")
            Hint(ModLang.L("安装失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>把文件/文件夹安装到资源包目录。</summary>
    Private Sub InstallFiles(Files As String(), TargetFolder As String)
        RunInNewThread(Sub()
                           Dim Success = 0
                           Dim Failed = 0
                           For Each SourcePath In Files
                               Try
                                   Dim Name = IO.Path.GetFileName(SourcePath)
                                   Dim TargetPath = PathUtils.AddSlashSuffix(TargetFolder) & Name
                                   If DirectoryUtils.Exists(SourcePath) Then
                                       DirectoryUtils.Copy(SourcePath, TargetPath)
                                   ElseIf FileUtils.Exists(SourcePath) Then
                                       FileUtils.Copy(SourcePath, TargetPath)
                                   Else
                                       Failed += 1
                                       Continue For
                                   End If
                                   Success += 1
                               Catch
                                   Failed += 1
                               End Try
                           Next
                           RunInUi(Sub()
                                       Refresh()
                                       If Success > 0 Then
                                           Hint(ModLang.L("已安装 ") & Success & ModLang.L(" 个资源包"), HintType.Green)
                                       End If
                                       If Failed > 0 Then
                                           Hint(ModLang.L("安装失败 ") & Failed & ModLang.L(" 个"), HintType.Red)
                                       End If
                                   End Sub)
                       End Sub)
    End Sub

#End Region

End Class
