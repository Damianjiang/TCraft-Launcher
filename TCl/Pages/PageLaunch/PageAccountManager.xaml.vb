Imports Microsoft.Win32
Imports Microsoft.VisualBasic.FileIO

''' <summary>
''' 账户管理页。
''' 右区 = HMCL 的账户卡片列表：圆点单选钮（TCL MyRadioBox）+ 32×32 头像（TCL MyImage，脸部截图缓存）
''' + 名称/类型两行（HMCL title/subtitle）+ 右侧操作按钮（HMCL：刷新/复制UUID/皮肤/删除，鼠标移入显现）。
''' 卡片间距 10（HMCL card-list spacing）。
''' 添加离线账户走 MyMsgOffline 弹窗；添加微软账户直接开始登录。
''' </summary>
Public Class PageAccountManager

#Region "生命周期"

    Private Sub PageAccountManager_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        Logger.Info("PageAccountManager Loaded 触发")
        InitSkinCard()
        RefreshAccounts()
    End Sub

    ''' <summary>「添加账户」入口：弹窗选择微软账号 / 离线账号。</summary>
    Private Sub BtnAddAccount_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnAddAccount.MouseLeftButtonUp
        Dim Choice = MyMsgBoxAddAccount()
        If Choice Is Nothing Then Return
        Select Case Choice.Value
            Case 1 '微软账号：直接开始登录（无中间页面）
                '开源版未内置微软登录凭据：未配置 ClientId 时明确提示，而不是让用户撞上失败
                If String.IsNullOrEmpty(OAuthClientId) Then
                    Hint(ModLang.L("微软登录未启用：请设置环境变量 TCL_MS_CLIENT_ID 后重启启动器。"), HintType.Blue, False)
                    Return
                End If
                AddMsAccountDirect()
            Case 2 '离线账号
                AddOfflineAccount()
            Case 3 'LittleSkin / 第三方 Yggdrasil 外置登录
                AddLittleSkinAccount()
        End Select
    End Sub

    ''' <summary>兼容旧调用：账户页已合并为单视图，此处仅刷新列表。</summary>
    Public Sub ShowView(ShowList As Boolean)
        Try
            If Not ShowList Then Return
            RefreshAccounts()
        Catch ex As Exception
            Logger.Warn(ex, "切换账户管理视图失败")
        End Try
    End Sub

    '当前已构建的行单选钮（HMCL 单选互斥；MyRadioBox 按父容器互斥，各行父容器不同，需手动全量互斥）
    Private _RowRadios As New List(Of MyRadioBox)

    Private Sub MsRefreshStateChanged(Loader As LoaderBase, NewState As LoadState, OldState As LoadState)
        If Not (NewState = LoadState.Finished OrElse NewState = LoadState.Failed) Then Return
        RemoveHandler McLoginMsLoader.OnStateChangedUi, AddressOf MsRefreshStateChanged
        Try
            If NewState = LoadState.Finished Then
                Hint(ModLang.L("登录状态已刷新。"), HintType.Green, False)
                '登录成功即视为选择正版账户：同步 LoginType 与版本设置，避免 RefreshPage 又把类型顶回旧值
                Settings.Set("LoginType", McLoginType.Ms)
                SyncVersionLogin(McLoginType.Ms)
                RefreshAccounts()
                FrmLaunchLeft.RefreshPage(False, False)
            Else
                Hint(ModLang.L("刷新登录失败，请重新登录。"), HintType.Red)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "刷新登录状态提示失败")
        End Try
    End Sub

    ''' <summary>离线皮肤重载完成：刷新账户行头像（缓存已由 McSkinSaveFace 重新生成）。</summary>
    Private Sub SkinReloadStateChanged(Loader As LoaderBase, NewState As LoadState, OldState As LoadState)
        If Not (NewState = LoadState.Finished OrElse NewState = LoadState.Failed) Then Return
        RemoveHandler PageLaunchLeft.SkinLegacy.OnStateChangedUi, AddressOf SkinReloadStateChanged
        Try
            RefreshAccounts()
        Catch ex As Exception
            Logger.Warn(ex, "刷新账户行头像失败")
        End Try
    End Sub

#End Region

#Region "账户列表（HMCL 卡片式行）"

    ''' <summary>刷新账户列表：微软账户 + 离线账户卡片。</summary>
    Public Sub RefreshAccounts()
        If PanAccounts Is Nothing Then
            Logger.Info("刷新账户列表：PanAccounts 尚未初始化，跳过")
            Return
        End If
        Try
            '修复：卡片是 Loaded 后动态添加行的，会触发 MyCard 高度动画（StartHeightAnimation
            '会把 Height 钉在旧值、依赖动画收尾块恢复 NaN）。若收尾块被页面切换动画打断，卡片高度
            '会永久卡在标题栏高度，导致行全部被裁剪（用户实测：列表空白）。禁用动画并强制自适应。
            CardAccounts.UseAnimation = False
            Dim CurrentType = Settings.Get(Of McLoginType)("LoginType")
            PanAccounts.Children.Clear()
            _RowRadios.Clear()
            '微软账户
            Try
                Dim MsJson As JObject = Settings.Get(Of String)("LoginMsJson").DeserializeJson()
                For Each Account As KeyValuePair(Of String, JToken) In CType(MsJson, JObject)
                    AddMsRow(Account.Key, Account.Value.ToString,
                             CurrentType = McLoginType.Ms AndAlso Settings.Get(Of String)("CacheMsV2Name") = Account.Key.ToString)
                Next
            Catch ex As Exception
                Logger.Warn(ex, "读取微软账户列表失败")
            End Try
            '离线账户
            If Settings.Get(Of String)("LoginLegacyName") <> "" Then
                For Each Name As String In Settings.Get(Of String)("LoginLegacyName").ToString.Split("¨"c)
                    If Name = "" Then Continue For
                    Dim N = Name
                    AddOfflineRow(N, CurrentType = McLoginType.Legacy AndAlso GetCurrentLegacyName() = N)
                Next
            End If
            '第三方 / LittleSkin 账号
            Try
                Dim AuthJson As JObject = Settings.Get(Of String)("LoginAuthAccounts").DeserializeJson()
                For Each Account As KeyValuePair(Of String, JToken) In CType(AuthJson, JObject)
                    Dim AuthEntry = CType(Account.Value, JObject)
                    AddAuthRow(Account.Key, If(AuthEntry("Server") Is Nothing, "", AuthEntry("Server").ToString),
                               CurrentType = McLoginType.Auth AndAlso Settings.Get(Of String)("CacheAuthUsername") = Account.Key)
                Next
            Catch ex As Exception
                Logger.Warn(ex, "读取第三方账号列表失败")
            End Try
            Logger.Info($"账户行构建完成：共 {PanAccounts.Children.Count} 项")
            If PanAccounts.Children.Count = 0 Then
                Dim Empty As New TextBlock With {
                    .Text = ModLang.L("尚未添加任何账户，请使用左侧的「添加账户」。"),
                    .FontSize = 13, .Opacity = 0.55, .HorizontalAlignment = HorizontalAlignment.Center,
                    .Margin = New Thickness(0, 12, 0, 0)}
                Empty.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
                PanAccounts.Children.Add(Empty)
            End If
            '给还没有头像缓存的离线账户补生成头像。
            '头像是皮肤加载完才裁出来的，而皮肤加载只跑「当前账户」，非当前账户会一直显示默认脸。
            Try
                Dim LegacyNames As New List(Of String)
                For Each N As String In Settings.Get(Of String)("LoginLegacyName").ToString.Split("¨"c)
                    If N <> "" Then LegacyNames.Add(N)
                Next
                PageLaunchLeft.EnsureAccountFaces(LegacyNames)
            Catch ex As Exception
                Logger.Warn(ex, "补生成离线账户头像失败")
            End Try
            '强制卡片高度瞬间自适应内容（禁用高度动画后双保险）
            CardAccounts.TriggerForceResize()
            '正版皮肤与披风卡片跟随当前账户类型显示
            RefreshSkinCard()
            '补丁 4：行入场级联动画（对齐下载页版本列表的加载效果：从上方落下、逐个级联入场）
            PlayRowsEnterAnimation()
            '左栏「已添加」摘要列表已删除，无需同步 FrmAccountLeft
        Catch ex As Exception
            Logger.Error(ex, "刷新账户列表失败")
        End Try
    End Sub

    ''' <summary>
    ''' 行入场级联动画（对齐 MyPageRight 进入动画 / 下载页版本列表加载效果）：
    ''' 每行从上方 14px 落下并带回弹落位，逐行级联（间隔 30ms），与页面切换动画同一手感。
    ''' </summary>
    Private Sub PlayRowsEnterAnimation()
        If AniControlEnabled > 0 Then Return
        Dim AniList As New List(Of AniData)
        Dim Delay As Integer = 0
        For Each Row As FrameworkElement In PanAccounts.Children
            Row.Opacity = 0
            Row.RenderTransform = New TranslateTransform(0, -14)
            AniList.Add(AaOpacity(Row, 1, 150, Delay, New AniEaseOutFluent(AniEasePower.Weak)))
            AniList.Add(AaTranslateY(Row, 14, 320, Delay, New AniEaseOutFluent(AniEasePower.Middle)))
            Delay += 30
        Next
        If AniList.Count > 0 Then AniStart(AniList, "AccountList Enter " & GetUuid(), True)
    End Sub

    ''' <summary>
    ''' HMCL 卡片式账户行（AccountListItemSkin 对齐）：
    ''' 左 = JFXRadioButton（TCL 用 MyRadioBox 真实圆点）；中 = 32×32 头像 + 名称/类型两行；右 = 操作按钮（hover 显现）。
    ''' 整行点击 = 选中（单选互斥 + 选择逻辑）。
    ''' </summary>
    Private Sub BuildCardRow(Key As String, Title As String, Info As String, Face As String,
                             IsCurrent As Boolean, Buttons As IEnumerable(Of MyIconButton),
                             OnSelect As Action)
        Dim Row As New Grid With {.Height = 56, .Margin = New Thickness(0, 0, 0, 10), .Background = Brushes.Transparent}
        Dim Shadow As New MyDropShadow With {.CornerRadius = New CornerRadius(6), .ShadowRadius = 2, .Opacity = 0.05, .IsHitTestVisible = False}
        Shadow.SetResourceReference(MyDropShadow.ColorProperty, "ColorObject1")
        Dim Border As New Border With {.CornerRadius = New CornerRadius(6), .BorderThickness = New Thickness(GetWPFSize(1)), .SnapsToDevicePixels = True}
        Border.SetResourceReference(Border.BackgroundProperty, "ColorBrushCardBg")
        Border.SetResourceReference(Border.BorderBrushProperty, "ColorBrush6")
        Dim Layout As New Grid
        Layout.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(44)})
        Layout.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(46)})
        Layout.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(1, GridUnitType.Star)})
        Layout.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
        '左侧：真实圆点单选钮（MyRadioBox；圆点互斥走 _RowRadios 手动全量互斥，因各行父容器不同）
        Dim Radio As New MyRadioBox With {.Width = 18, .Height = 18, .HorizontalAlignment = HorizontalAlignment.Center, .VerticalAlignment = VerticalAlignment.Center}
        If IsCurrent Then Radio.SetChecked(True, False)
        _RowRadios.Add(Radio)
        Grid.SetColumn(Radio, 0)
        '头像：32×32（HMCL Canvas(32,32) 同款）；没有缓存时退回内置皮肤裁出的脸，不能拿整张皮肤图
        Dim FaceImg As New MyImage With {
            .Source = If(Face, PageLaunchLeft.GetAccountFaceOrDefault(Key)),
            .Width = 32, .Height = 32, .Stretch = Stretch.Uniform, .SnapsToDevicePixels = True}
        '8×8 的脸放大到 32px：最近邻才是一格一格的像素脸，线性插值会糊成一团
        RenderOptions.SetBitmapScalingMode(FaceImg, BitmapScalingMode.NearestNeighbor)
        Dim FaceBorder As New Border With {
            .Child = FaceImg, .CornerRadius = New CornerRadius(4), .Width = 32, .Height = 32,
            .HorizontalAlignment = HorizontalAlignment.Left, .VerticalAlignment = VerticalAlignment.Center, .SnapsToDevicePixels = True}
        FaceBorder.BorderThickness = New Thickness(GetWPFSize(1))
        FaceBorder.SetResourceReference(Border.BorderBrushProperty, "ColorBrush6")
        Grid.SetColumn(FaceBorder, 1)
        '名称 + 类型两行（HMCL title/subtitle：13 / 12 字号，副文本降透明）
        Dim Texts As New StackPanel With {.HorizontalAlignment = HorizontalAlignment.Left, .VerticalAlignment = VerticalAlignment.Center}
        Dim LabTitle As New TextBlock With {.Text = Title, .FontSize = 13, .FontWeight = FontWeights.SemiBold, .SnapsToDevicePixels = False, .UseLayoutRounding = False}
        LabTitle.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Dim LabInfo As New TextBlock With {.Text = Info, .FontSize = 12, .SnapsToDevicePixels = False, .UseLayoutRounding = False, .Opacity = 0.55}
        LabInfo.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        Texts.Children.Add(LabTitle) : Texts.Children.Add(LabInfo)
        Grid.SetColumn(Texts, 2)
        '右侧操作按钮
        Dim BtnPanel As New StackPanel With {
            .Orientation = Orientation.Horizontal, .HorizontalAlignment = HorizontalAlignment.Right,
            .VerticalAlignment = VerticalAlignment.Center, .Opacity = 1, .Margin = New Thickness(0, 0, 2, 0)}
        For Each Btn In Buttons
            If Btn.Width.Equals(Double.NaN) Then Btn.Width = 28
            If Btn.Height.Equals(Double.NaN) Then Btn.Height = 28
            Btn.Margin = New Thickness(3, 0, 0, 0)
            BtnPanel.Children.Add(Btn)
        Next
        Grid.SetColumn(BtnPanel, 3)
        Layout.Children.Add(Radio) : Layout.Children.Add(FaceBorder) : Layout.Children.Add(Texts) : Layout.Children.Add(BtnPanel)
        '修复：阴影与内容必须是兄弟节点（MyCard 同款模式）。
        '此前把整行内容塞进 Shadow.Child，而 WPF 的 Opacity 作用于整个子树，
        '导致行内容以 5% 透明度渲染（用户实测：卡片看起来是空的，只有幽灵残影）。
        Shadow.Child = Nothing
        Border.Child = Layout
        Row.Children.Add(Shadow)
        Row.Children.Add(Border)

        '整行点击 = 选中：先做全量单选互斥（各行圆点不是兄弟节点，MyRadioBox 自带互斥不适用），再触发选择逻辑
        '修复：如果点击的是右侧按钮区域，不触发选中逻辑，避免误弹出"已切换启动账户"提示
        AddHandler Row.PreviewMouseLeftButtonUp, Sub(sender, e)
            '判断点击位置是否在按钮面板内，如果是则不执行选中逻辑
            Dim HitElement As DependencyObject = e.OriginalSource
            Do While HitElement IsNot Nothing
                If TypeOf HitElement Is Grid AndAlso DirectCast(HitElement, Grid).Equals(BtnPanel) Then Return
                HitElement = VisualTreeHelper.GetParent(HitElement)
            Loop

            If Not Radio.Checked Then
                For Each Other As MyRadioBox In _RowRadios
                    If Not Other.Equals(Radio) AndAlso Other.Checked Then Other.SetChecked(False, False)
                Next
                Radio.SetChecked(True, True)
            End If
            OnSelect()
        End Sub
        '选中态：主题色描边（HMCL 选中卡 = 高亮底色；TCL 用描边色表现）
        AddHandler Radio.Changed, Sub(sender, e)
            If Radio.Checked Then
                Border.SetResourceReference(Border.BorderBrushProperty, "ColorBrush2")
            Else
                Border.SetResourceReference(Border.BorderBrushProperty, "ColorBrush6")
            End If
        End Sub
        '当前选中：主题色描边（HMCL 选中卡 = 高亮底色；TCL 用描边色表现）
        If IsCurrent Then
            Border.SetResourceReference(Border.BorderBrushProperty, "ColorBrush2")
        End If
        '补丁 4：鼠标悬停动画（对齐下载页列表项的手感：背景平滑过渡 + 阴影加深，移出反向恢复）
        Dim HoverId = GetUuid()
        AddHandler Row.MouseEnter,
            Sub(sender, e)
                Try
                    AniStart({
                        AaColor(Border, Border.BackgroundProperty, "ColorBrushBg1", 120),
                        AaOpacity(Shadow, 0.12 - Shadow.Opacity, 150,, New AniEaseOutFluent)
                    }, "AccountRow Hover " & HoverId)
                Catch ex As Exception
                    Logger.Warn(ex, "账户行悬停动画失败")
                End Try
            End Sub
        AddHandler Row.MouseLeave,
            Sub(sender, e)
                Try
                    AniStart({
                        AaColor(Border, Border.BackgroundProperty, "ColorBrushCardBg", 180),
                        AaOpacity(Shadow, 0.05 - Shadow.Opacity, 180)
                    }, "AccountRow Hover " & HoverId)
                Catch ex As Exception
                    Logger.Warn(ex, "账户行悬停恢复动画失败")
                End Try
            End Sub
        PanAccounts.Children.Add(Row)
    End Sub

    ''' <summary>微软账户卡片行：上传皮肤 / 刷新 / 复制UUID / 删除（HMCL AccountListItemSkin 对齐）。</summary>
    Private Sub AddMsRow(Name As String, RefreshToken As String, IsCurrent As Boolean)
        '3D 查看该账户的皮肤（复刻 HMCL 的账号皮肤预览）
        Dim BtnViewSkin As New MyIconButton With {.SvgIcon = "lucide/scan-face", .ToolTip = ModLang.L("3D 查看该账户的皮肤")}
        AddHandler BtnViewSkin.Click,
            Sub(sender, e)
                Try
                    MyMsgBoxSkinLegacy(Name, PreviewOnly:=True, PresetType:=3, PresetId:=Name)
                Catch ex As Exception
                    Logger.Warn(ex, "3D 预览微软账户皮肤失败")
                End Try
            End Sub
        'HMCL：微软账户「上传皮肤」= 打开 minecraft.net 的个人资料页
        Dim BtnUploadSkin As New MyIconButton With {.SvgIcon = "lucide/shirt", .ToolTip = ModLang.L("在官网管理该账户的皮肤")}
        AddHandler BtnUploadSkin.Click,
            Sub(sender, e)
                Try
                    OpenWebsite("https://www.minecraft.net/profile")
                Catch ex As Exception
                    Logger.Warn(ex, "打开皮肤管理网页失败")
                End Try
            End Sub
        Dim BtnRefresh As New MyIconButton With {.SvgIcon = "lucide/refresh-cw", .ToolTip = ModLang.L("刷新该账户的登录状态")}
        AddHandler BtnRefresh.Click,
            Sub(sender, e)
                '与登录页「登录」按钮同一通道：共享 McLoginMsLoader，完成后由 MsRefreshStateChanged 提示并刷新列表
                If McLoginMsLoader.State = LoadState.Loading Then Return
                Try
                    Dim Data As New McLoginMs With {.OAuthRefreshToken = RefreshToken, .UserName = Name}
                    RemoveHandler McLoginMsLoader.OnStateChangedUi, AddressOf MsRefreshStateChanged
                    AddHandler McLoginMsLoader.OnStateChangedUi, AddressOf MsRefreshStateChanged
                    McLoginMsLoader.Start(Data, IsForceRestart:=True)
                Catch ex As Exception
                    Logger.Warn(ex, "刷新微软登录状态失败")
                End Try
            End Sub
        Dim BtnCopy As New MyIconButton With {.SvgIcon = "lucide/copy", .ToolTip = ModLang.L("复制该账户的 UUID")}
        AddHandler BtnCopy.Click,
            Sub(sender, e)
                Try
                    Dim Uuid As String = Settings.Get(Of String)("CacheMsV2Uuid")
                    If Uuid = "" Then Uuid = McLoginMojangUuid(Name, False)
                    Clipboard.SetText(FormatUuid(Uuid))
                    Hint(ModLang.L("已复制到剪贴板。"), HintType.Green, False)
                Catch ex As Exception
                    Logger.Warn(ex, "复制 UUID 失败")
                End Try
            End Sub
        Dim BtnDelete As New MyIconButton With {.SvgIcon = "lucide/trash-2", .ToolTip = ModLang.L("删除此账户")}
        AddHandler BtnDelete.Click, Sub()
            If MyMsgBox(ModLang.L("确定要删除账户 ") & Name & ModLang.L(" 吗？此操作不可撤销。"), ModLang.L("删除账户"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) = 1 Then
                DeleteMs(Name)
                RefreshAccounts()
                FrmLaunchLeft.RefreshPage(False, False)
            End If
        End Sub
        BuildCardRow(Name, Name, ModLang.L("正版登录") & If(IsCurrent, " · " & ModLang.L("当前账户"), ""),
                     PageLaunchLeft.GetAccountFaceOrDefault(Name), IsCurrent,
                     {BtnViewSkin, BtnUploadSkin, BtnRefresh, BtnCopy, BtnDelete}, Sub() SelectMs(Name, RefreshToken))
    End Sub

    ''' <summary>离线账户卡片行：刷新皮肤 / 编辑皮肤 / 复制UUID / 删除（HMCL AccountListItemSkin 对齐）。</summary>
    Private Sub AddOfflineRow(Name As String, IsCurrent As Boolean)
        'UUID 一律由系统按用户名自动生成（McLoginLegacyUuid 规则），用户无需干预
        Dim Uuid As String = McLoginLegacyUuid(Name)
        'HMCL：离线账户「刷新」= 清除皮肤/头像缓存后重新加载
        Dim BtnRefresh As New MyIconButton With {.SvgIcon = "lucide/refresh-cw", .ToolTip = ModLang.L("刷新该账户的皮肤与头像缓存")}
        AddHandler BtnRefresh.Click,
            Sub(sender, e)
                Try
                    PageLaunchLeft.ClearAccountFace(Name)
                    PageLaunchLeft.SkinLegacy.Start(IsForceRestart:=True)
                    Hint(ModLang.L("已刷新。"), HintType.Green, False)
                Catch ex As Exception
                    Logger.Warn(ex, "刷新离线账户皮肤缓存失败")
                End Try
            End Sub
        Dim BtnCopy As New MyIconButton With {.SvgIcon = "lucide/copy", .ToolTip = ModLang.L("复制该账户的 UUID")}
        AddHandler BtnCopy.Click,
            Sub(sender, e)
                Try
                    Clipboard.SetText(FormatUuid(Uuid))
                    Hint(ModLang.L("已复制到剪贴板。"), HintType.Green, False)
                Catch ex As Exception
                    Logger.Warn(ex, "复制 UUID 失败")
                End Try
            End Sub
        Dim BtnSkin As New MyIconButton With {.SvgIcon = "lucide/palette", .ToolTip = ModLang.L("编辑该账户的皮肤")}
        AddHandler BtnSkin.Click,
            Sub(sender, e)
                Try
                    'HMCL 式皮肤选择弹窗（默认/Steve/Alex/本地文件/LittleSkin/Blessing Skin），
                    '写入按账户隔离的配置（ModLegacySkin），配置指纹变化会自动触发皮肤重载
                    Dim Type = MyMsgBoxSkinLegacy(Name)
                    If Type Is Nothing Then Return
                    '皮肤重载完成后（头像缓存重新生成）再刷新账户行
                    RemoveHandler PageLaunchLeft.SkinLegacy.OnStateChangedUi, AddressOf SkinReloadStateChanged
                    AddHandler PageLaunchLeft.SkinLegacy.OnStateChangedUi, AddressOf SkinReloadStateChanged
                    PageLaunchLeft.SkinLegacy.Start() '输入指纹变化时自动重载
                Catch ex As Exception
                    Logger.Warn(ex, "设置离线皮肤失败")
                End Try
            End Sub
        Dim BtnDelete As New MyIconButton With {.SvgIcon = "lucide/trash-2", .ToolTip = ModLang.L("删除此账户")}
        AddHandler BtnDelete.Click, Sub()
            If MyMsgBox(ModLang.L("确定要删除账户 ") & Name & ModLang.L(" 吗？此操作不可撤销。"), ModLang.L("删除账户"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) = 1 Then
                DeleteLegacy(Name)
                RefreshAccounts()
                FrmLaunchLeft.RefreshPage(False, False)
            End If
        End Sub
        BuildCardRow(Name, Name, ModLang.L("离线登录") & If(IsCurrent, " · " & ModLang.L("当前账户"), ""),
                     PageLaunchLeft.GetAccountFaceOrDefault(Name), IsCurrent,
                     {BtnRefresh, BtnCopy, BtnSkin, BtnDelete}, Sub() SelectLegacy(Name))
    End Sub

    ''' <summary>32 位无连字符 UUID 转为标准连字符格式展示。</summary>
    Private Function FormatUuid(Uuid As String) As String
        If Uuid Is Nothing OrElse Uuid.Length <> 32 Then Return Uuid
        Return Uuid.Substring(0, 8) & "-" & Uuid.Substring(8, 4) & "-" & Uuid.Substring(12, 4) & "-" & Uuid.Substring(16, 4) & "-" & Uuid.Substring(20, 12)
    End Function

#End Region

#Region "选择 / 删除账户"

    ''' <summary>确保对应登录页已创建，并使其 UI 状态与设置一致（启动时 GetLoginData 读取窗体状态）。</summary>
    Private Sub EnsureForm(Type As McLoginType)
        Select Case Type
            Case McLoginType.Legacy
                If FrmLoginLegacy Is Nothing Then FrmLoginLegacy = New PageLoginLegacy
                FrmLoginLegacy.Reload(False)
            Case McLoginType.Ms
                If FrmLoginMs Is Nothing Then FrmLoginMs = New PageLoginMs
                FrmLoginMs.Reload(False)
            Case McLoginType.Nide
                If FrmLoginNide Is Nothing Then FrmLoginNide = New PageLoginNide
            Case McLoginType.Auth
                If FrmLoginAuth Is Nothing Then FrmLoginAuth = New PageLoginAuth
        End Select
    End Sub

    ''' <summary>
    ''' 用户明确选择了某账户：同步版本的登录方式设置，避免 RefreshPage 用旧的
    ''' VersionServerLogin（如曾设置过 LittleSkin 预设=4）把 LoginType 顶回去，
    ''' 导致首页永远显示「未登录」、启动报「账号不能为空」。
    ''' 无选中版本时同步全局 LoginPageType（RefreshPage 无版本时读它）。
    ''' </summary>
    Private Sub SyncVersionLogin(Type As McLoginType)
        Try
            Dim Value As Integer
            Select Case Type
                Case McLoginType.Ms : Value = 1
                Case McLoginType.Legacy : Value = 2
                Case McLoginType.Nide : Value = 3
                Case McLoginType.Auth : Value = 4
                Case Else : Value = 0
            End Select
            If McInstanceSelected IsNot Nothing Then
                If CInt(Settings.Get(Of Integer)("VersionServerLogin", Instance:=McInstanceSelected)) <> Value Then
                    Settings.Set("VersionServerLogin", Value, Instance:=McInstanceSelected)
                End If
            Else
                Settings.Set("LoginPageType", Value)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "同步版本登录方式失败")
        End Try
    End Sub

    Private Sub SelectMs(Name As String, RefreshToken As String)
        Settings.Set("LoginType", McLoginType.Ms)
        SyncVersionLogin(McLoginType.Ms)
        '凭据交接（对齐 SelectAuth）：把所选账户的 RefreshToken 写入全局缓存并清空旧资料，
        '否则选一个本机从未完成登录的微软账户时，首页与启动校验仍按「未登录」处理
        If RefreshToken <> "" Then
            Settings.Set("CacheMsV2OAuthRefresh", RefreshToken)
            Settings.Set("CacheMsV2Access", "")
            Settings.Set("CacheMsV2Uuid", "")
            Settings.Set("CacheMsV2Expires", 0L)
            Settings.Set("CacheMsV2ProfileJson", "")
            Settings.Set("CacheMsV2Name", Name)
        End If
        EnsureForm(McLoginType.Ms)
        '在登录页下拉框中选中该账户
        For i = 0 To FrmLoginMs.ComboAccounts.Items.Count - 1
            Dim Item = FrmLoginMs.ComboAccounts.Items(i)
            If TypeOf Item Is MyListItem AndAlso CType(Item, MyListItem).Title = Name Then
                FrmLoginMs.ComboAccounts.SelectedIndex = i
                Exit For
            End If
        Next
        AfterSelect()
    End Sub

    ''' <summary>由左侧摘要列表调用：切换到指定的微软账户（自动取其 RefreshToken）。</summary>
    Public Sub SelectMsAccount(Name As String)
        Dim RefreshToken As String = ""
        Try
            Dim MsJson As JObject = Settings.Get(Of String)("LoginMsJson").DeserializeJson()
            Dim Token = MsJson(Name)
            If Token IsNot Nothing Then RefreshToken = Token.ToString
        Catch ex As Exception
            Logger.Warn(ex, "读取微软账户凭据失败")
        End Try
        SelectMs(Name, RefreshToken)
    End Sub

    ''' <summary>由左侧摘要列表调用：切换到指定的离线账户。</summary>
    Public Sub SelectLegacyAccount(Name As String)
        SelectLegacy(Name)
    End Sub

    Private Sub SelectLegacy(Name As String)
        Settings.Set("LoginType", McLoginType.Legacy)
        SyncVersionLogin(McLoginType.Legacy)
        '先把所选账户置顶到历史，再 EnsureForm：Reload(False) 会把下拉框重置为历史第一项，
        '顺序反了会被覆盖回旧账户（选了 B 显示 A）
        Dim Names As New List(Of String)
        Names.AddRange(Settings.Get(Of String)("LoginLegacyName").Split("¨"c))
        Names.Remove(Name)
        Names.Insert(0, Name)
        Settings.Set("LoginLegacyName", Names.Join("¨"c))
        EnsureForm(McLoginType.Legacy)
        FrmLoginLegacy.ComboName.Text = Name
        AfterSelect()
    End Sub

    Private Sub AfterSelect()
        Logger.Info($"已切换启动账户，类型：{Settings.Get(Of McLoginType)("LoginType")}")
        Hint(ModLang.L("已切换启动账户。"), HintType.Green, False)
        RefreshAccounts()
        FrmLaunchLeft.RefreshPage(False, False)
    End Sub

    Private Sub DeleteMs(Name As String)
        Try
            Dim MsJson As JObject = Settings.Get(Of String)("LoginMsJson").DeserializeJson()
            MsJson.Remove(Name)
            Settings.Set("LoginMsJson", MsJson.ToString(Newtonsoft.Json.Formatting.None))
            If Settings.Get(Of String)("CacheMsV2Name") = Name Then
                '删除的是当前登录的账户：清空其缓存，避免继续使用已被删除的凭据
                Settings.Set("CacheMsV2OAuthRefresh", "")
                Settings.Set("CacheMsV2Access", "")
                Settings.Set("CacheMsV2Name", "")
                Settings.Set("CacheMsV2Uuid", "")
            End If
            Logger.Info($"已删除微软账户：{Name}")
        Catch ex As Exception
            Logger.Error(ex, "删除微软账户失败")
        End Try
    End Sub

    Private Sub DeleteLegacy(Name As String)
        Dim Names As New List(Of String)
        Names.AddRange(Settings.Get(Of String)("LoginLegacyName").Split("¨"))
        Names.Remove(Name)
        Settings.Set("LoginLegacyName", Names.Join("¨"c))
        Logger.Info($"已删除离线账户：{Name}")
    End Sub

    ''' <summary>当前生效的离线账户名（与启动逻辑一致：窗体状态优先，其次列表第一项）。</summary>
    Private Function GetCurrentLegacyName() As String
        If FrmLoginLegacy?.IsReloaded Then Return If(FrmLoginLegacy.ComboName.Text.Trim, "")
        If Settings.Get(Of String)("LoginLegacyName") = "" Then Return ""
        Return Settings.Get(Of String)("LoginLegacyName").BeforeFirst("¨").Trim
    End Function

#End Region

#Region "添加账户"

    ''' <summary>
    ''' 添加离线账户：HMCL 式弹窗（仅用户名）。
    ''' UUID 由系统按用户名自动生成（McLoginLegacyUuid 规则），用户无需理解也无需干预。
    ''' </summary>
    Public Sub AddOfflineAccount()
        Dim Result = MyMsgBoxOfflineAccount()
        If Result Is Nothing Then Return
        Dim Name = Result.Replace("¨", "").Trim
        If Name = "" Then Return
        If Name.Contains("""") Then
            Hint(ModLang.L("玩家名不能包含英文引号！"), HintType.Red)
            Return
        End If
        Dim Names As New List(Of String)
        Names.AddRange(Settings.Get(Of String)("LoginLegacyName").Split("¨"))
        Names.Remove(Name)
        Names.Insert(0, Name) '置顶，成为默认账户
        Settings.Set("LoginLegacyName", Names.Join("¨"c))
        SelectLegacy(Name)
    End Sub

    ''' <summary>
    ''' 添加微软账户：不打开任何中间页面，
    ''' 直接启动微软登录加载器 → 弹出设备码登录弹窗（自动打开验证网页 + 复制设备码）→ 登录成功后自动落库。
    ''' </summary>
    Public Sub AddMsAccountDirect()
        If McLoginMsLoader.State = LoadState.Loading Then
            Hint(ModLang.L("正在登录中，请稍候。"), HintType.Blue)
            Return
        End If
        RunInNewThread(
        Sub()
            Try
                '空输入 + 强制重启 → 必走完整登录流程（MsLoginStep1New → 设备码弹窗）
                McLoginMsLoader.Start(New McLoginMs, IsForceRestart:=True)
                Do While McLoginMsLoader.State = LoadState.Loading
                    Thread.Sleep(50)
                Loop
                If McLoginMsLoader.State = LoadState.Finished Then
                    '登录成功：账户已由 ModLaunch 写入 LoginMsJson，同步类型后刷新列表并提示
                    RunInUi(
                    Sub()
                        Settings.Set("LoginType", McLoginType.Ms)
                        SyncVersionLogin(McLoginType.Ms)
                        Hint(ModLang.L("登录成功！"), HintType.Green, False)
                        RefreshAccounts()
                        FrmLaunchLeft.RefreshPage(False, False)
                    End Sub)
                ElseIf McLoginMsLoader.State = LoadState.Canceled Then
                    '用户取消：静默
                ElseIf McLoginMsLoader.Error IsNot Nothing Then
                    Throw New Exception(McLoginMsLoader.Error.Message, McLoginMsLoader.Error)
                Else
                    Throw New Exception(ModLang.L("未知错误！"))
                End If
            Catch ex As Exception
                If ex.IsCanceled Then
                ElseIf ex.Message.StartsWithF("$") Then
                    Hint(ex.Message.TrimStart("$"), HintType.Red)
                ElseIf TypeOf ex Is Security.Authentication.AuthenticationException AndAlso ex.Message.Contains("SSL/TLS") Then
                    Logger.Error(ex, $"正版登录验证失败，请考虑在 [设置 → 其他] 中关闭 [在正版登录时验证 SSL 证书]，然后再试。{vbCrLf}{vbCrLf}原始错误信息：", LogBehavior.Alert)
                Else
                    Logger.Error(ex, "正版登录尝试失败", LogBehavior.Alert)
                End If
            End Try
        End Sub, "Ms Login Direct")
    End Sub

    ''' <summary>
    ''' 添加 LittleSkin / 第三方登录账号：
    ''' 弹窗填写 玩家名 + 密码 + 认证服务器（可把 LittleSkin「外置登录」按钮拖进来自动识别），
    ''' 提交即登记进账户列表（LoginAuthAccounts）并写入全局缓存；
    ''' 若当前已选版本，顺带配置该版本的第三方登录。不强制先下载游戏。
    ''' </summary>
    Public Sub AddLittleSkinAccount()
        Dim Data = MyMsgBoxAuthAccount()
        If Data Is Nothing Then Return
        Dim Name = Data.Value.Name
        If Name = "" Then Return
        If Name.Contains("""") Then
            Hint(ModLang.L("玩家名不能包含英文引号！"), HintType.Red)
            Return
        End If
        Try
            '1) 登记进账户列表（按玩家名去重，置顶）
            Dim Accounts As JObject = Settings.Get(Of String)("LoginAuthAccounts").DeserializeJson()
            Accounts.Remove(Name)
            Accounts.Add(Name, New JObject(
                New JProperty("Pass", Data.Value.Pass),
                New JProperty("Server", Data.Value.Server),
                New JProperty("Register", Data.Value.Register)))
            Settings.Set("LoginAuthAccounts", Accounts.ToString(Newtonsoft.Json.Formatting.None))
            '2) 选为该账号（SelectAuth 统一处理：全局缓存 + 登录页记住列表 + 当前版本第三方配置 + LoginType + 刷新首页）
            SelectAuth(Name)
        Catch ex As Exception
            Logger.Error(ex, "添加 LittleSkin 账号失败")
            MyMsgBox(ModLang.L("添加 LittleSkin 账号失败：") & ex.GetDisplay(False), ModLang.L("LittleSkin 登录"), IsWarn:=True)
        End Try
    End Sub

    ''' <summary>
    ''' 把账号写入登录页「记住密码」列表（LoginAuthEmail/LoginAuthPass，¨ 分隔，按账号名去重、新账号置顶）。
    ''' 登录页下拉框据此显示，启动时 PageLoginAuth.GetLoginData 也据此取凭据。
    ''' </summary>
    Private Sub InsertRememberAuth(Name As String, Pass As String)
        Try
            Dim Emails As New List(Of String)
            Dim Passes As New List(Of String)
            If Settings.Get(Of String)("LoginAuthEmail") <> "" Then Emails.AddRange(Settings.Get(Of String)("LoginAuthEmail").Split("¨"c))
            If Settings.Get(Of String)("LoginAuthPass") <> "" Then Passes.AddRange(Settings.Get(Of String)("LoginAuthPass").Split("¨"c))
            '两列表严格对齐（启动模块写入时同序生成），按账号名从后往前去重
            For i = Emails.Count - 1 To 0 Step -1
                If Emails(i) = Name Then
                    Emails.RemoveAt(i)
                    If i < Passes.Count Then Passes.RemoveAt(i)
                End If
            Next
            Emails.Insert(0, Name)
            Passes.Insert(0, Pass)
            Settings.Set("LoginAuthEmail", Emails.Join("¨"c))
            Settings.Set("LoginAuthPass", Passes.Join("¨"c))
        Catch ex As Exception
            Logger.Warn(ex, "更新第三方登录记住列表失败")
        End Try
    End Sub

    ''' <summary>
    ''' 选择某第三方账号为当前登录账号：
    ''' 1) 全局缓存：当前账号凭据（让登录 / 皮肤加载器立即可用，并让首页立即显示玩家名）；
    ''' 2) 登录页「记住」列表：让登录页下拉框显示该账号；
    ''' 3) 当前版本的第三方登录配置：让 RefreshPage 不再把 LoginType 顶回旧登录方式；
    ''' 4) LoginType=Auth + 刷新登录表单 + 首页摘要。
    ''' </summary>
    Private Sub SelectAuth(Name As String)
        Try
            Dim Accounts As JObject = Settings.Get(Of String)("LoginAuthAccounts").DeserializeJson()
            Dim Entry = Accounts(Name)
            If Entry Is Nothing Then Return
            Dim Pass As String = Entry("Pass").ToString
            Dim Server As String = Entry("Server").ToString
            Dim Register As String = If(Entry("Register") Is Nothing, "", Entry("Register").ToString)
            '1) 全局缓存（服务器/账号变更，清空旧访问令牌要求重新登录；同步清空 UUID/角色名避免误用上一账号角色）
            Settings.Set("CacheAuthUsername", Name)
            Settings.Set("CacheAuthPass", Pass)
            Settings.Set("CacheAuthServerServer", Server)
            Settings.Set("CacheAuthServerRegister", Register)
            Settings.Set("CacheAuthServerName", ModLang.L("LittleSkin 登录"))
            Settings.Set("CacheAuthAccess", "")
            Settings.Set("CacheAuthClient", "")
            Settings.Set("CacheAuthUuid", "")
            Settings.Set("CacheAuthName", "")
            '2) 登录页「记住」列表
            InsertRememberAuth(Name, Pass)
            '3) 当前版本的第三方登录配置（缺了它，RefreshPage 会把 LoginType 顶回该版本旧登录方式）
            If McInstanceSelected IsNot Nothing Then
                Settings.Set("VersionServerLogin", 4, Instance:=McInstanceSelected)
                Settings.Set("VersionServerAuthServer", Server, Instance:=McInstanceSelected)
                Settings.Set("VersionServerAuthRegister", Register, Instance:=McInstanceSelected)
                Settings.Set("VersionServerAuthName", ModLang.L("LittleSkin 登录"), Instance:=McInstanceSelected)
            End If
            '4) 切到该账号并刷新（SyncVersionLogin 兼顾无选中版本时的全局 LoginPageType）
            SyncVersionLogin(McLoginType.Auth)
            Settings.Set("LoginType", McLoginType.Auth)
            EnsureForm(McLoginType.Auth)
            AfterSelect()
        Catch ex As Exception
            Logger.Warn(ex, "切换第三方账号失败")
        End Try
    End Sub

    ''' <summary>从账户列表删除某第三方账号；若是当前登录账号则一并清空其缓存。</summary>
    Private Sub DeleteAuth(Name As String)
        Try
            Dim Accounts As JObject = Settings.Get(Of String)("LoginAuthAccounts").DeserializeJson()
            Accounts.Remove(Name)
            Settings.Set("LoginAuthAccounts", Accounts.ToString(Newtonsoft.Json.Formatting.None))
            If Settings.Get(Of String)("CacheAuthUsername") = Name Then
                Settings.Set("CacheAuthAccess", "")
                Settings.Set("CacheAuthClient", "")
                Settings.Set("CacheAuthUuid", "")
                Settings.Set("CacheAuthName", "")
                Settings.Set("CacheAuthUsername", "")
                Settings.Set("CacheAuthPass", "")
            End If
            Logger.Info($"已删除第三方账号：{Name}")
        Catch ex As Exception
            Logger.Error(ex, "删除第三方账号失败")
        End Try
    End Sub

    ''' <summary>第三方账号卡片行：刷新皮肤 / 复制UUID / 删除（HMCL 对齐）。</summary>
    Private Sub AddAuthRow(Name As String, Server As String, IsCurrent As Boolean)
        Dim BtnProfile As New MyIconButton With {.SvgIcon = "lucide/arrow-right-left", .ToolTip = ModLang.L("切换该账号的角色（多角色账户）")}
        AddHandler BtnProfile.Click,
            Sub(sender, e)
                Try
                    SwitchAuthProfile(Name)
                Catch ex As Exception
                    Logger.Warn(ex, "切换第三方账号角色失败")
                End Try
            End Sub
        Dim BtnRefresh As New MyIconButton With {.SvgIcon = "lucide/refresh-cw", .ToolTip = ModLang.L("刷新该账号的皮肤与头像缓存")}
        AddHandler BtnRefresh.Click, Sub(sender, e)
            Try
                PageLaunchLeft.ClearAccountFace(Name)
                PageLaunchLeft.SkinAuth.Start(IsForceRestart:=True)
                Hint(ModLang.L("已刷新。"), HintType.Green, False)
            Catch ex As Exception
                Logger.Warn(ex, "刷新第三方账号皮肤缓存失败")
            End Try
        End Sub
        Dim BtnCopy As New MyIconButton With {.SvgIcon = "lucide/copy", .ToolTip = ModLang.L("复制该账号的 UUID")}
        AddHandler BtnCopy.Click, Sub(sender, e)
            Try
                Dim Uuid = Settings.Get(Of String)("CacheAuthUuid")
                If Uuid = "" Then
                    Hint(ModLang.L("该账号尚未登录，无法获取 UUID。"), HintType.Blue)
                Else
                    Clipboard.SetText(FormatUuid(Uuid))
                    Hint(ModLang.L("已复制到剪贴板。"), HintType.Green, False)
                End If
            Catch ex As Exception
                Logger.Warn(ex, "复制 UUID 失败")
            End Try
        End Sub
        Dim BtnSkin As New MyIconButton With {.SvgIcon = "lucide/scan-face", .ToolTip = ModLang.L("3D 查看该账户的皮肤")}
        AddHandler BtnSkin.Click,
            Sub(sender, e)
                Try
                    '第三方账户的皮肤由皮肤站提供，这里只做只读 3D 预览（默认从 LittleSkin 按角色名取图）
                    MyMsgBoxSkinLegacy(Name, PreviewOnly:=True, PresetType:=5, PresetId:=Name)
                Catch ex As Exception
                    Logger.Warn(ex, "3D 预览第三方账户皮肤失败")
                End Try
            End Sub
        Dim BtnDelete As New MyIconButton With {.SvgIcon = "lucide/trash-2", .ToolTip = ModLang.L("删除此账户")}
        AddHandler BtnDelete.Click, Sub()
            If MyMsgBox(ModLang.L("确定要删除账号 ") & Name & ModLang.L(" 吗？此操作不可撤销。"), ModLang.L("删除账户"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) = 1 Then
                DeleteAuth(Name)
                RefreshAccounts()
                FrmLaunchLeft.RefreshPage(False, False)
            End If
        End Sub
        BuildCardRow(Name, Name, ModLang.L("LittleSkin 登录") & If(IsCurrent, " · " & ModLang.L("当前账户"), ""),
                     PageLaunchLeft.GetAccountFaceOrDefault(Name), IsCurrent,
                     {BtnProfile, BtnRefresh, BtnCopy, BtnSkin, BtnDelete}, Sub() SelectAuth(Name))
    End Sub

    ''' <summary>
    ''' 切换第三方账户的角色：从登录时缓存的角色列表中选择并写入当前缓存，
    ''' 下次登录该账号时生效。仅多角色账号需要，MS 账户无多角色概念。
    ''' </summary>
    Private Sub SwitchAuthProfile(UserName As String)
        Dim ProfilesJson As JObject = Settings.Get(Of String)("CacheProfilesJson").DeserializeJson()
        Dim ProfilesToken = ProfilesJson("Auth:" & UserName)
        If ProfilesToken Is Nothing OrElse CType(ProfilesToken, JArray).Count <= 1 Then
            MyMsgBox(ModLang.L("尚未缓存该账号的多角色列表，或该账号只有一个角色。") & vbCrLf & ModLang.L("使用该账号登录一次游戏后即可在此切换角色。"), ModLang.L("切换角色"))
            Return
        End If
        Dim Profiles = CType(ProfilesToken, JArray)
        Dim CurrentId = Settings.Get(Of String)("CacheAuthUuid")
        Dim SelectionControl As New List(Of IMyRadio)
        Dim Selection As New List(Of JToken)
        For Each Profile As JToken In Profiles
            SelectionControl.Add(New MyRadioBox With {.Text = Profile("Name").ToString & If(Profile("Id").ToString = CurrentId, ModLang.L("（当前）"), "")})
            Selection.Add(Profile)
        Next
        Dim Index = MyMsgBoxSelect(SelectionControl, ModLang.L("选择使用的角色"))
        Dim SelectedId = Selection(Index)("Id").ToString
        Dim SelectedName = Selection(Index)("Name").ToString
        Settings.Set("CacheAuthUuid", SelectedId)
        Settings.Set("CacheAuthName", SelectedName)
        Hint(ModLang.L("已切换为角色 ") & SelectedName & ModLang.L("，下次登录该账号时生效。"), HintType.Green)
    End Sub

#End Region

#Region "正版皮肤与披风"

    Private _SkinCardHooked As Boolean = False

    ''' <summary>皮肤加载器只挂一次：页面反复 Loaded 时不要重复叠加处理器。</summary>
    Private Sub InitSkinCard()
        Try
            If _SkinCardHooked Then Return
            _SkinCardHooked = True
            SkinPreview.Loader = PageLaunchLeft.SkinMs
            AddHandler PageLaunchLeft.SkinMs.OnStateChangedUi, AddressOf SkinCardStateChanged
        Catch ex As Exception
            Logger.Warn(ex, "初始化皮肤卡片失败")
        End Try
    End Sub

    ''' <summary>正版皮肤加载完成：把皮肤画到预览上；失败则清空，避免显示上一个账户的脸。</summary>
    Private Sub SkinCardStateChanged(Loader As LoaderBase, NewState As LoadState, OldState As LoadState)
        If NewState <> LoadState.Finished AndAlso NewState <> LoadState.Failed Then Return
        RunInUi(
        Sub()
            Try
                If NewState = LoadState.Finished Then SkinPreview.Load() Else SkinPreview.Clear()
            Catch ex As Exception
                Logger.Warn(ex, "刷新皮肤预览失败")
            End Try
        End Sub)
    End Sub

    ''' <summary>按当前登录方式显示/隐藏皮肤卡，并保证预览已载入。</summary>
    Private Sub RefreshSkinCard()
        Try
            Dim Name As String = Settings.Get(Of String)("CacheMsV2Name")
            Dim IsMs As Boolean = Settings.Get(Of McLoginType)("LoginType") = McLoginType.Ms AndAlso Name <> ""
            CardSkin.Visibility = If(IsMs, Visibility.Visible, Visibility.Collapsed)
            If Not IsMs Then Return
            InitSkinCard()
            LabSkinHint.Text = ModLang.L("当前正版账户：") & Name &
                ModLang.L("。修改皮肤 / 披风后会同步刷新账户头像与标题栏徽章；皮肤文件也可另存为 PNG。")
            '预览：加载器已有结果就直接画，否则启动一次加载
            If PageLaunchLeft.SkinMs.State = LoadState.Finished AndAlso Not String.IsNullOrEmpty(PageLaunchLeft.SkinMs.Output) Then
                SkinPreview.Load()
            ElseIf PageLaunchLeft.SkinMs.State <> LoadState.Loading Then
                PageLaunchLeft.SkinMs.Start()
            End If
        Catch ex As Exception
            Logger.Warn(ex, "刷新皮肤卡片失败")
        End Try
    End Sub

    Private Sub BtnSkinChange_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSkinChange.Click
        Try
            If McLoginMsLoader.State = LoadState.Failed Then
                Hint(ModLang.L("登录失败，无法更改皮肤！"), HintType.Red)
                Return
            End If
            Dim Info As McSkinInfo = McSkinSelect()
            If Not Info.IsVaild Then Return
            Hint(ModLang.L("正在更改皮肤……"))
            ModMsSkin.UploadSkin(Info)
        Catch ex As Exception
            Logger.Error(ex, "修改正版皮肤失败")
        End Try
    End Sub

    Private Sub BtnCapeChange_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnCapeChange.Click
        Try
            InitSkinCard()
            SkinPreview.BtnSkinCape_Click()
        Catch ex As Exception
            Logger.Error(ex, "更换披风失败")
        End Try
    End Sub

    Private Sub BtnSkinSaveFile_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSkinSaveFile.Click
        Try
            InitSkinCard()
            SkinPreview.BtnSkinSave_Click()
        Catch ex As Exception
            Logger.Error(ex, "保存皮肤文件失败")
        End Try
    End Sub

    Private Sub BtnSkinRefreshNow_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSkinRefreshNow.Click
        Try
            InitSkinCard()
            SkinPreview.RefreshClick()
        Catch ex As Exception
            Logger.Error(ex, "刷新皮肤失败")
        End Try
    End Sub

    Private Sub BtnRedeem_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnRedeem.Click
        Try
            OpenWebsite("https://www.minecraft.net/zh-hans/redeem")
        Catch ex As Exception
            Logger.Warn(ex, "打开兑换页面失败")
        End Try
    End Sub

    Private Sub BtnProfileWeb_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnProfileWeb.Click
        Try
            OpenWebsite("https://www.minecraft.net/profile")
        Catch ex As Exception
            Logger.Warn(ex, "打开个人资料页失败")
        End Try
    End Sub

#End Region

End Class
