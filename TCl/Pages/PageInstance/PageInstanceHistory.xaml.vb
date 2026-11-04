Imports System.IO

''' <summary>
''' 实例历史页：游玩记录 / 快照 / 变更（复刻 LumilioCL 游戏页 · 历史）。
''' </summary>
Public Class PageInstanceHistory

    Private CurrentTab As Integer = 0
    Private Snapshots As New List(Of McSnapshotRecord)

    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property

#Region "加载"

    Private Sub PageInstanceHistory_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        Reload()
    End Sub

    Public Sub Reload()
        If Instance Is Nothing Then
            LabDesc.Text = ModLang.L("请先选择一个 Minecraft 版本。")
            PanPlay.Children.Clear()
            PanSnap.Children.Clear()
            PanChange.Children.Clear()
            Return
        End If
        Try
            ModInstanceHistory.RecoverUnfinished(Instance)
        Catch ex As Exception
            Logger.Warn(ex, "补记未完成会话失败")
        End Try
        BuildScopeCombo()
        Snapshots = ModInstanceHistory.ListSnapshots(Instance)
        RenderPlay()
        RenderSnap()
        RenderChange()
        ShowTab(CurrentTab)
    End Sub

    ''' <summary>快照范围下拉：全部内容 + 当前实例的每个世界。</summary>
    Private Sub BuildScopeCombo()
        Dim Previous As Integer = ComboScope.SelectedIndex
        ComboScope.Items.Clear()
        ComboScope.Items.Add(ModLang.L("全部内容"))
        Try
            Dim SavesFolder As String = PathUtils.AddSlashSuffix(Instance.PathIndie) & "saves\"
            If DirectoryUtils.Exists(SavesFolder) Then
                For Each Folder In DirectoryUtils.EnumerateDirectories(SavesFolder, False)
                    ComboScope.Items.Add(ModLang.L("世界：") & PathUtils.GetLastPart(Folder))
                Next
            End If
        Catch ex As Exception
            Logger.Warn(ex, "枚举世界列表失败")
        End Try
        If Previous >= 0 AndAlso Previous < ComboScope.Items.Count Then
            ComboScope.SelectedIndex = Previous
        Else
            ComboScope.SelectedIndex = 0
        End If
    End Sub

#End Region

#Region "标签页"

    Private Sub Tab_Click(sender As Object, e As MouseButtonEventArgs)
        Dim Tag As Integer = Val(CType(sender, MyButton).Tag)
        ShowTab(Tag)
    End Sub

    Private Sub ShowTab(Tag As Integer)
        CurrentTab = Tag
        CardPlay.Visibility = If(Tag = 0, Visibility.Visible, Visibility.Collapsed)
        CardSnap.Visibility = If(Tag = 1, Visibility.Visible, Visibility.Collapsed)
        CardChange.Visibility = If(Tag = 2, Visibility.Visible, Visibility.Collapsed)
        BtnTabPlay.ColorType = If(Tag = 0, MyButton.ColorState.Highlight, MyButton.ColorState.Normal)
        BtnTabSnap.ColorType = If(Tag = 1, MyButton.ColorState.Highlight, MyButton.ColorState.Normal)
        BtnTabChange.ColorType = If(Tag = 2, MyButton.ColorState.Highlight, MyButton.ColorState.Normal)
    End Sub

#End Region

#Region "游玩记录"

    Private Sub RenderPlay()
        PanPlay.Children.Clear()
        Dim Sessions = ModInstanceHistory.LoadSessions(Instance)
        If Sessions.Count = 0 Then
            LabPlaySum.Text = ModLang.L("还没有记录。启动一次游戏后，这里会显示本次会话的开始时间、时长与退出结果。")
            Return
        End If
        Dim TotalSeconds As Long = Sessions.Sum(Function(S) S.Seconds)
        LabPlaySum.Text = ModLang.L("共 ") & Sessions.Count & ModLang.L(" 次会话，累计 ") & FormatDuration(TotalSeconds) &
            ModLang.L("；最近 50 次显示在下方，点一行查看对应日志。")
        For Each Session In Sessions.Take(50)
            Dim InfoText As String = Session.ResultText & ModLang.L(" · 时长 ") & Session.DurationText
            If Not String.IsNullOrEmpty(Session.Player) Then InfoText &= ModLang.L(" · 玩家 ") & Session.Player
            If Not String.IsNullOrEmpty(Session.Version) Then InfoText &= ModLang.L(" · ") & Session.Version
            Dim Item As New MyListItem With {
                .Type = MyListItem.CheckType.Clickable,
                .Height = 46,
                .Title = If(Session.StartLocal = Date.MinValue, Session.StartUtc, Session.StartLocal.ToString("yyyy'/'MM'/'dd HH':'mm")),
                .Info = InfoText,
                .SnapsToDevicePixels = True
            }
            AddHandler Item.Click, Sub() GoDiagnostics()
            PanPlay.Children.Add(Item)
        Next
    End Sub

    Private Function FormatDuration(Seconds As Long) As String
        If Seconds < 60 Then Return Seconds & ModLang.L(" 秒")
        If Seconds < 3600 Then Return (Seconds \ 60) & ModLang.L(" 分 ") & (Seconds Mod 60) & ModLang.L(" 秒")
        Return (Seconds \ 3600) & ModLang.L(" 小时 ") & ((Seconds Mod 3600) \ 60) & ModLang.L(" 分")
    End Function

    ''' <summary>跳到诊断页查看日志（对应 LumilioCL 的「查看日志」）。</summary>
    Private Sub GoDiagnostics()
        FrmInstanceDiagnostics?.Reload()
        FrmMain.PageChange(New FormMain.PageStackData With {.Page = FormMain.PageType.InstanceSetup},
                           FormMain.PageSubType.InstanceDiagnostics)
    End Sub

#End Region

#Region "快照"

    Private Sub RenderSnap()
        PanSnap.Children.Clear()
        If Snapshots.Count = 0 Then
            LabSnapSum.Text = ModLang.L("还没有快照。快照会打包 Mod、配置、存档与资源包，用来在改动前留一份回滚点。")
            Return
        End If
        Dim TotalSize As Long = Snapshots.Sum(Function(S) S.Size)
        LabSnapSum.Text = ModLang.L("共 ") & Snapshots.Count & ModLang.L(" 份，占用 ") & StringUtils.FormatByteSize(TotalSize)
        For Each Snapshot In Snapshots
            Dim Title As String = If(String.IsNullOrWhiteSpace(Snapshot.Note), Snapshot.FileName, Snapshot.Note)
            Dim InfoText As String = Snapshot.CreatedLocal.ToString("yyyy'/'MM'/'dd HH':'mm") &
                ModLang.L(" · ") & StringUtils.FormatByteSize(Snapshot.Size) & ModLang.L(" · ") & Snapshot.ScopeText
            Dim Item As New MyListItem With {
                .Type = MyListItem.CheckType.Clickable,
                .Height = 48,
                .Title = Title,
                .Info = InfoText,
                .SnapsToDevicePixels = True
            }
            Dim BtnRestore As New MyIconButton With {
                .ToolTip = ModLang.L("恢复到这个快照（会先自动备份当前状态）"),
                .Logo = "M512 64a448 448 0 1 1 0 896 448 448 0 0 1 0-896z m0 64a384 384 0 1 0 0 768 384 384 0 0 0 0-768z m-16 160a32 32 0 0 1 32 32v176l144 84a32 32 0 0 1-32 55l-160-94a32 32 0 0 1-16-28V320a32 32 0 0 1 32-32z"
            }
            AddHandler BtnRestore.Click, Sub() RestoreSnapshot(Snapshot)
            Dim BtnDelete As New MyIconButton With {
                .ToolTip = ModLang.L("删除这个快照"),
                .Theme = MyIconButton.Themes.Red,
                .Logo = "M445 545c18 0 33 14 33 33v149a182 182 0 1 1-182-182z m282 0a182 182 0 1 1-182 182v-149c0-18 14-33 33-33zM412 611H296a116 116 0 1 0 116 116V611z m315 0H611v116a116 116 0 1 0 116-116zM296 113a182 182 0 0 1 182 182v149a33 33 0 0 1-33 33H296a182 182 0 0 1 0-365z m431 0a182 182 0 0 1 0 365h-149a33 33 0 0 1-33-33V296A182 182 0 0 1 727 113zM296 180a116 116 0 0 0 0 232H412V296c0-62-48-112-109-115z m431 0a116 116 0 0 0-116 116V412h116c62 0 112-48 115-109l0-6a116 116 0 0 0-116-116z"
            }
            AddHandler BtnDelete.Click, Sub() DeleteSnapshot(Snapshot)
            Item.Buttons = {BtnRestore, BtnDelete}
            PanSnap.Children.Add(Item)
        Next
    End Sub

    Private Sub BtnCreateSnap_Click(sender As Object, e As MouseButtonEventArgs)
        If Instance Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        Dim Scope As String = ""
        If ComboScope.SelectedIndex > 0 Then
            Scope = ComboScope.SelectedItem.ToString()
            Dim Prefix As String = ModLang.L("世界：")
            If Scope.StartsWith(Prefix, StringComparison.Ordinal) Then Scope = Scope.Substring(Prefix.Length)
        End If
        Dim Note As String = MyMsgBoxInput(ModLang.L("创建快照"), ModLang.L("给这份快照写一句备注（可留空）："), "",
                                           hintText:=ModLang.L("例如：装光影前"))
        If Note Is Nothing Then Return
        BtnCreateSnap.IsEnabled = False
        Hint(ModLang.L("正在后台创建快照…"), HintType.Blue, False)
        Dim ScopeCopy As String = Scope
        Dim NoteCopy As String = Note
        Dim Target As McInstance = Instance
        RunInNewThread(
        Sub()
            Try
                ModInstanceHistory.CreateSnapshot(Target, NoteCopy, ScopeCopy)
                RunInUi(
                Sub()
                    BtnCreateSnap.IsEnabled = True
                    Snapshots = ModInstanceHistory.ListSnapshots(Target)
                    RenderSnap()
                    RenderChange()
                    Hint(ModLang.L("快照已创建。"), HintType.Green)
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "创建快照失败")
                RunInUi(
                Sub()
                    BtnCreateSnap.IsEnabled = True
                    Hint(ModLang.L("创建快照失败：") & ex.Message, HintType.Red)
                End Sub)
            End Try
        End Sub, "Create Snapshot", ThreadPriority.BelowNormal)
    End Sub

    Private Sub RestoreSnapshot(Snapshot As McSnapshotRecord)
        If MyMsgBox(ModLang.L("恢复会用「") & If(String.IsNullOrWhiteSpace(Snapshot.Note), Snapshot.FileName, Snapshot.Note) &
                    ModLang.L("」里的文件覆盖当前实例，当前状态会先自动存一份。是否继续？"),
                    ModLang.L("恢复快照"), ModLang.L("恢复"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Dim Target As McInstance = Instance
        Hint(ModLang.L("正在后台恢复快照…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Try
                ModInstanceHistory.RestoreSnapshot(Target, Snapshot)
                RunInUi(
                Sub()
                    Snapshots = ModInstanceHistory.ListSnapshots(Target)
                    RenderSnap()
                    RenderChange()
                    Hint(ModLang.L("快照已恢复，原状态已自动备份。"), HintType.Green)
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "恢复快照失败")
                RunInUi(Sub() Hint(ModLang.L("恢复快照失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Restore Snapshot", ThreadPriority.BelowNormal)
    End Sub

    Private Sub DeleteSnapshot(Snapshot As McSnapshotRecord)
        If MyMsgBox(ModLang.L("确定删除快照「") & Snapshot.FileName & ModLang.L("」吗？删除后无法找回。"),
                    ModLang.L("删除快照"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            ModInstanceHistory.DeleteSnapshot(Instance, Snapshot)
            Snapshots = ModInstanceHistory.ListSnapshots(Instance)
            RenderSnap()
            RenderChange()
            Hint(ModLang.L("快照已删除。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "删除快照失败")
            Hint(ModLang.L("删除快照失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

#End Region

#Region "变更"

    Private Sub RenderChange()
        PanChange.Children.Clear()
        Dim Events As New List(Of Tuple(Of Date, String, String))
        For Each Session In ModInstanceHistory.LoadSessions(Instance).Take(60)
            Events.Add(Tuple.Create(Session.StartLocal, ModLang.L("游玩"),
                Session.ResultText & ModLang.L(" · ") & Session.DurationText))
        Next
        For Each Snapshot In Snapshots
            Events.Add(Tuple.Create(Snapshot.CreatedLocal, ModLang.L("快照"),
                If(String.IsNullOrWhiteSpace(Snapshot.Note), Snapshot.FileName, Snapshot.Note) & ModLang.L(" · ") & Snapshot.ScopeText))
        Next
        If Events.Count = 0 Then
            LabChangeSum.Text = ModLang.L("还没有变更记录。")
            Return
        End If
        LabChangeSum.Text = ModLang.L("按时间倒序显示游玩与快照事件，最近的 ") & Math.Min(Events.Count, 60) & ModLang.L(" 条。")
        For Each Item In Events.OrderByDescending(Function(E) E.Item1).Take(60)
            Dim Line As New MyListItem With {
                .Type = MyListItem.CheckType.Clickable,
                .Height = 44,
                .Title = If(Item.Item1 = Date.MinValue, "", Item.Item1.ToString("yyyy'/'MM'/'dd HH':'mm")) & "  " & Item.Item2,
                .Info = Item.Item3,
                .SnapsToDevicePixels = True
            }
            PanChange.Children.Add(Line)
        Next
    End Sub

#End Region

End Class
