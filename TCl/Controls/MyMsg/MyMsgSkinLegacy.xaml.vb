Imports Microsoft.Win32
Imports System.Windows.Threading

''' <summary>
''' HMCL 式离线账户皮肤选择弹窗：
''' 左侧 3D 皮肤预览 + 右侧单选列表（默认 / Steve / Alex / 正版 / 本地文件 / LittleSkin / 自定义皮肤站）。
''' <para/>
''' 交互与 HMCL 一致：点击单选项仅更新选中态与左侧预览；「确定」才写入按账户隔离的皮肤配置（ModLegacySkin），
''' 「取消」直接关闭不生效。皮肤加载器（PageLaunchLeft.SkinLegacy）会因配置指纹变化自动重载。
''' <para/>
''' 正版 / LittleSkin / 自定义皮肤站这三项需要补充信息（角色名 / CSL 地址），
''' 原先就地铺开输入框会让主弹窗「超载」，现改为弹出**子弹窗**（PanSub 覆盖层）集中收集输入；
''' 子弹窗「确定」才写回暂存并刷新预览，「取消」保留原状。主弹窗本体不再因输入框铺开而增高。
''' <para/>
''' Converter.Content = 玩家名；确定时 Result = 皮肤类型编号（Integer），取消为 Nothing。
''' </summary>
Public Class MyMsgSkinLegacy

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()
    ''' <summary>本次编辑的离线账户名。</summary>
    Private ReadOnly PlayerName As String = ""
    ''' <summary>只读预览（微软 / 第三方账户）：不写配置，按钮只有「关闭」。</summary>
    Private ReadOnly PreviewOnly As Boolean = False

    '单选项（index 与 ModLegacySkin.SkinConfig.Type 对应）
    Private ReadOnly Items As New List(Of MyListItem)
    ''' <summary>Type → 单选控件映射（用于高亮当前配置）。</summary>
    Private ReadOnly ItemTypes As New List(Of KeyValuePair(Of Integer, MyListItem))

    '待应用的皮肤选择（HMCL 语义：选中先暂存，确定才写入配置）
    Private PendingType As Integer = 0
    Private PendingId As String = ""
    Private PendingFile As String = ""
    Private PendingApi As String = ""
    ''' <summary>皮肤站图片的本地缓存路径（仅作离线兜底，不写进 Type=0/1/2 的配置里，避免污染默认皮肤）。</summary>
    Private PendingCache As String = ""
    ''' <summary>底部提示的默认文案（取图失败时会临时替换成警告，切回正常项时恢复）。</summary>
    Private HintDefault As String = ""

    '预览渲染
    ''' <summary>预览请求票号：快速切换选项时丢弃过期线程的回写。</summary>
    Private PreviewTicket As Integer = 0

    Public Sub New(Converter As MyMsgBoxConverter)
        Try

            InitializeComponent()
            Btn1.Name = Btn1.Name & GetUuid()
            Btn2.Name = Btn2.Name & GetUuid()
            BtnSub1.Name = BtnSub1.Name & GetUuid()
            BtnSub2.Name = BtnSub2.Name & GetUuid()
            MyConverter = Converter
            Dim Args As ModLegacySkin.SkinEditorArgs = Nothing
            If TypeOf Converter.Content Is ModLegacySkin.SkinEditorArgs Then Args = CType(Converter.Content, ModLegacySkin.SkinEditorArgs)
            If TypeOf Converter.Content Is String Then PlayerName = CType(Converter.Content, String) '兼容旧调用
            If Args IsNot Nothing Then
                PlayerName = Args.PlayerName
                PreviewOnly = Args.PreviewOnly
            End If
            LabTitle.Text = ModLang.L(If(PreviewOnly, "查看皮肤", "编辑皮肤")) & If(PlayerName = "", "", " - " & PlayerName)
            HintDefault = ModLang.L(If(PreviewOnly, "拖动模型可旋转查看；此窗口只做预览，不会修改皮肤。", "拖动模型可旋转查看，选好来源后点「确定」生效。"))
            LabHint.Text = HintDefault
            If PreviewOnly Then
                Btn1.Text = ModLang.L("关闭")
                Btn2.Visibility = Visibility.Collapsed
            End If
            ShapeLine.StrokeThickness = GetWPFSize(1)

            '构建单选列表（HMCL SkinPane 对齐）
            Dim Current = ModLegacySkin.GetConfig(PlayerName)
            PendingType = Current.Type : PendingId = Current.Id : PendingFile = Current.File : PendingApi = Current.Api
            '配置自愈——越界类型、以及「皮肤站但没有角色名」这种永远取不到图的残配置，
            '一律回落到「默认」。否则弹窗一打开就停在皮肤站上，用户会以为是自己被改成了皮肤站皮肤。
            If PendingType < 0 OrElse PendingType > 6 Then PendingType = 0
            If (PendingType = 5 OrElse PendingType = 6) AndAlso PendingId.Trim = "" Then
                PendingType = 0 : PendingFile = "" : PendingApi = ""
            End If
            '预览模式（微软 / 第三方账户）：按调用方给的来源与名字直接定位，例如正版 ID = 账户名
            If Args IsNot Nothing AndAlso Args.PresetType >= 0 Then
                PendingType = Args.PresetType
                PendingId = Args.PresetId
                PendingFile = ""
                If Args.PresetType = 6 AndAlso PendingApi = "" Then PendingApi = "https://littleskin.cn/csl"
            End If
            AddItem(0, "lucide/user", "默认", "根据玩家名自动选择默认皮肤")
            AddItem(1, "lucide/user", "Steve", "经典 Steve 皮肤")
            AddItem(2, "lucide/user", "Alex", "细手臂 Alex 皮肤")
            AddItem(3, "lucide/globe", "正版玩家皮肤", "输入一个正版 ID，套用它当前使用的皮肤")
            AddItem(4, "lucide/image", "本地皮肤图片文件", "从电脑中选择一张 64×32 或 64×64 的皮肤图片")
            AddItem(5, "lucide/cloud-download", "LittleSkin 皮肤站", "输入 LittleSkin 上的角色名，直接拉取该角色皮肤")
            AddItem(6, "lucide/server", "自定义皮肤站", "填写 Blessing Skin 等站点的 CSL 地址与角色名")
            '补充输入全部收进子弹窗（PanSub），主弹窗不再就地铺开输入框
            TextSubWho.HintText = ModLang.L("玩家名（皮肤站上的角色名）")
            TextSubApi.HintText = ModLang.L("皮肤站地址，例如 https://littleskin.cn/csl")
            '高亮当前生效项并预览（打开时不自动弹子弹窗，只按已存配置预览；需补信息时由用户主动点击该项再弹出）
            Dim CurrentItem As MyListItem = Nothing
            For Each Pair In ItemTypes
                If Pair.Key = PendingType Then CurrentItem = Pair.Value
            Next
            If CurrentItem Is Nothing AndAlso Items.Count > 0 Then
                CurrentItem = Items(0)
                PendingType = 0 : PendingId = "" : PendingFile = "" : PendingApi = ""
            End If
            If CurrentItem IsNot Nothing Then CurrentItem.SetChecked(True, False, False)
            UpdatePreview()

        Catch ex As Exception
            Logger.Error(ex, "皮肤选择弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    ''' <summary>添加一个皮肤来源单选项。</summary>
    Private Sub AddItem(Type As Integer, Icon As String, Title As String, Info As String)
        Dim Item As New MyListItem With {
            .Title = ModLang.L(Title), .Info = ModLang.L(Info), .SvgIcon = Icon,
            .Type = MyListItem.CheckType.RadioBox, .Height = 36, .LogoScale = 0.75}
        AddHandler Item.Click, Sub() OnSelect(Type)
        Items.Add(Item)
        ItemTypes.Add(New KeyValuePair(Of Integer, MyListItem)(Type, Item))
        PanList.Children.Add(Item)
    End Sub

    ''' <summary>
    ''' 选中某项：更新暂存与预览（不应用，HMCL 语义）。需要补充信息的来源弹出子弹窗收集输入。
    ''' </summary>
    Private Sub OnSelect(Type As Integer)
        If MyConverter.IsExited Then Return
        Try
            Select Case Type
                Case 0, 1, 2 '默认 / Steve / Alex：无需补充
                Case 4 '本地皮肤文件：先选文件，取消则不改变选中
                    Dim Dialog As New OpenFileDialog With {.Title = ModLang.L("选择皮肤图片"), .Filter = "PNG 图片|*.png"}
                    If Not CBool(Dialog.ShowDialog()) Then Return
                    Dim Skin = New MyBitmap(Dialog.FileName)
                    Dim W = Skin.Pic.Width, H = Skin.Pic.Height
                    '支持 HD 皮肤：宽度是 64 的整数倍，高度等于宽度或宽度的一半
                    If W Mod 64 <> 0 OrElse W = 0 OrElse (H <> W \ 2 AndAlso H <> W) Then
                        MyMsgBox(ModLang.L("皮肤图片尺寸无效（应为 64×32 或 64×64）。"), ModLang.L("皮肤无效"), IsWarn:=True)
                        Return
                    End If
                    Dim Target = ModLegacySkin.CustomSkinPath(PlayerName)
                    FileUtils.Delete(Target)
                    FileUtils.Copy(Dialog.FileName, Target)
                    PendingFile = Target
                Case 3, 5, 6 '正版 / LittleSkin / 自定义皮肤站：先切换暂存，再弹子弹窗收集输入
            End Select
            PendingType = Type
            '切换来源时清掉不属于该来源的暂存，避免把上一项的文件/缓存写进新配置
            If Type <> 4 Then PendingFile = ""
            If Type <> 5 AndAlso Type <> 6 Then PendingCache = ""
            If Type <> 3 AndAlso Type <> 5 AndAlso Type <> 6 Then PendingId = ""
            '高亮选中项
            For Each Pair In ItemTypes
                Pair.Value.SetChecked(Pair.Key = Type, False, False)
            Next
            If Type = 3 OrElse Type = 5 OrElse Type = 6 Then
                OpenSubDialog(Type) '补充信息改走子弹窗，避免主弹窗铺开输入框
                Return
            End If
            UpdatePreview()
        Catch ex As Exception
            Logger.Warn(ex, "选择皮肤来源失败")
            MyMsgBox(ModLang.L("应用皮肤失败：") & ex.GetDisplay(False), ModLang.L("皮肤无效"), IsWarn:=True)
        End Try
    End Sub

    ''' <summary>
    ''' 确定：把暂存的选择写入配置并关闭（HMCL「确定」语义）。
    ''' 修复：必须为 Public——FormMain_KeyDown 对回车走 VB 晚绑定，只找公共成员；
    ''' Private 会让「回车 / 点击确定」抛 MissingMemberException（未知错误）。
    ''' </summary>
    Public Sub Btn1_Click() Handles Btn1.Click
        If MyConverter.IsExited Then Return
        '子弹窗开着时，回车确认的是子弹窗（写回角色名 / CSL 地址），不是主弹窗的「应用并关闭」
        If PanSub.Visibility = Visibility.Visible Then
            BtnSub1_Click()
            Return
        End If
        If PreviewOnly Then
            '只读预览：按钮就是「关闭」，不碰任何配置
            MyConverter.IsExited = True
            MyConverter.Result = Nothing
            Close()
            Return
        End If
        Try
            '补充信息已收在子弹窗里写回 PendingId/PendingApi，此处不再就地读输入框
            '胖瘦按实际贴图检测（本地文件 / 皮肤站皮肤 / 内置皮肤都能判准），比让用户手选可靠
            Dim Slim As Boolean = Skin3D.HasSkin AndAlso Skin3D.IsSlim
            '皮肤站额外记住本地缓存路径（Config.File 只在这个分支写），断网时还能拿它兜底
            Dim KeepFile As String = If(PendingType = 5 OrElse PendingType = 6, PendingCache, PendingFile)
            ModLegacySkin.SetConfig(PlayerName, New ModLegacySkin.SkinConfig With {
                .Type = PendingType, .Id = PendingId, .File = KeepFile, .Api = PendingApi, .Slim = Slim})
            MyConverter.Result = PendingType
            Logger.Info($"离线账户 {PlayerName} 皮肤配置已更新：Type={PendingType}，Id={PendingId}，Api={PendingApi}")
            '立刻重算这个账户的头像缓存（不管它是不是当前账户），列表与标题栏才不会一直停在旧头像上
            PageLaunchLeft.RefreshAccountSkin(PlayerName)
        Catch ex As Exception
            Logger.Error(ex, "保存皮肤配置失败", LogBehavior.Toast)
        End Try
        Close()
    End Sub

#Region "子弹窗"

    ''' <summary>
    ''' 打开子弹窗收集 3/5/6 的补充信息：预填当前暂存值，按类型定标题 / 说明 / 提示；
    ''' 第 6 类额外露出 CSL 站点地址输入框。弹出动画走卡片缩放 + 遮罩淡入。
    ''' </summary>
    Private Sub OpenSubDialog(Type As Integer)
        Try
            If MyConverter.IsExited Then Return
            '预填：名字优先用已暂存值，其次回退到当前账户名
            TextSubWho.Text = If(PendingId <> "", PendingId, PlayerName)
            TextSubWho.HintText = ModLang.L(If(Type = 3, "正版玩家 ID", "玩家名（皮肤站上的角色名）"))
            If Type = 6 Then
                TextSubApi.Visibility = Visibility.Visible
                TextSubApi.Text = If(PendingApi <> "", PendingApi, "")
                TextSubApi.HintText = ModLang.L("皮肤站地址，例如 https://littleskin.cn/csl")
            Else
                TextSubApi.Visibility = Visibility.Collapsed
            End If
            Select Case Type
                Case 3
                    LabSubTitle.Text = ModLang.L("输入正版玩家 ID")
                    LabSubDesc.Text = ModLang.L("输入一个正版 ID，套用它当前使用的皮肤")
                Case 5
                    LabSubTitle.Text = ModLang.L("输入 LittleSkin 角色名")
                    LabSubDesc.Text = ModLang.L("输入 LittleSkin 上的角色名，直接拉取该角色皮肤")
                Case 6
                    LabSubTitle.Text = ModLang.L("输入自定义皮肤站信息")
                    LabSubDesc.Text = ModLang.L("填写 Blessing Skin 等站点的 CSL 地址与角色名")
            End Select
            '显示 + 弹入动画
            PanSub.Visibility = Visibility.Visible
            PanSub.Opacity = 0
            SubCardScale.ScaleX = 0.94 : SubCardScale.ScaleY = 0.94
            AniStart({
                AaOpacity(PanSub, 1, 160, 30),
                AaDouble(Sub(i) SubCardScale.ScaleX += i, 1 - SubCardScale.ScaleX, 240, 30, New AniEaseOutBack(AniEasePower.Weak)),
                AaDouble(Sub(i) SubCardScale.ScaleY += i, 1 - SubCardScale.ScaleY, 240, 30, New AniEaseOutBack(AniEasePower.Weak))
            }, "MyMsgSkinSub " & Uuid)
            TextSubWho.Focus()
            TextSubWho.SelectAll()
        Catch ex As Exception
            Logger.Warn(ex, "打开皮肤子弹窗失败")
        End Try
    End Sub

    ''' <summary>关闭子弹窗（恢复卡片缩放/透明度，便于下次打开重走弹入动画）。</summary>
    Private Sub CloseSubDialog()
        Try
            PanSub.Visibility = Visibility.Collapsed
            PanSub.Opacity = 0
            SubCardScale.ScaleX = 0.94 : SubCardScale.ScaleY = 0.94
        Catch ex As Exception
            Logger.Warn(ex, "关闭皮肤子弹窗失败")
        End Try
    End Sub

    ''' <summary>子弹窗「确定」：把输入写回暂存（名字 / 站点地址），关闭子弹窗并刷新 3D 预览。</summary>
    Private Sub BtnSub1_Click() Handles BtnSub1.Click
        If MyConverter.IsExited Then Return
        Try
            PendingId = TextSubWho.Text.Trim
            If PendingType = 6 Then PendingApi = TextSubApi.Text.Trim
            CloseSubDialog()
            '刚填完立刻拉一次预览，用户点「确定」主按钮前就能看到效果（取不到图会出警告提示）
            UpdatePreview()
        Catch ex As Exception
            Logger.Warn(ex, "应用皮肤子弹窗输入失败")
        End Try
    End Sub

    ''' <summary>子弹窗「取消」：不改动暂存，直接收起（保留之前的角色名 / 地址）。</summary>
    Private Sub BtnSub2_Click() Handles BtnSub2.Click
        If MyConverter.IsExited Then Return
        CloseSubDialog()
        UpdatePreview()
    End Sub

#End Region

#Region "皮肤预览（HMCL 左侧正视图对齐）"

    ''' <summary>按暂存选择在后台解析皮肤文件，完成后交给 3D 控件渲染（票号防过期回写）。</summary>
    Private Sub UpdatePreview()
        LabHint.Text = HintDefault '每次重新解析先清掉上一次的警告
        Dim T = PendingType, Id = PendingId, File = PendingFile, Api = PendingApi, Cache = PendingCache
        PreviewTicket += 1
        Dim Ticket = PreviewTicket
        RunInNewThread(
        Sub()
            Dim Out As String = ""
            Dim SlimOverride As Boolean? = Nothing
            Dim WarnText As String = ""
            Dim Fallback As String = ModLegacySkin.DefaultSkinPath(PlayerName)
            Try
                Select Case T
                    Case 1 'Steve
                        Out = ModLegacySkin.BuiltinSkinPath("Steve")
                    Case 2 'Alex
                        Out = ModLegacySkin.BuiltinSkinPath("Alex")
                    Case 3 '正版（按输入的正版 ID 取它当前的皮肤）
                        If Id.Count < 2 Then
                            Out = Fallback
                        Else
                            Dim Address As String = McSkinGetAddress(McLoginMojangUuid(Id, True), "Mojang")
                            Out = McSkinDownload(Address)
                        End If
                    Case 4 '本地皮肤文件
                        Out = If(File <> "" AndAlso FileUtils.Exists(File), File, Fallback)
                    Case 5, 6 '皮肤站（5 = LittleSkin 固定地址，6 = 自定义 CSL 站点）
                        If Id = "" Then
                            WarnText = ModLang.L("请先填写皮肤站上的角色名。")
                            Out = Fallback
                        Else
                            Dim Base As String = If(T = 5, "https://littleskin.cn/csl", Api)
                            If Base.Trim = "" Then
                                Out = Fallback
                            Else
                                Try
                                    Dim Got = McSkinFromCsl(Base, Id, Cache)
                                    Out = Got.Path
                                    SlimOverride = Got.Slim
                                Catch ex As Exception
                                    '取不到图时明确告诉用户，而不是默默显示成默认皮肤
                                    Logger.Warn(ex, $"皮肤站预览失败（{Base} / {Id}）")
                                    WarnText = ModLang.L("该角色名在皮肤站取不到皮肤，可能不存在；当前按默认皮肤预览。")
                                    Out = Fallback
                                End Try
                            End If
                        End If
                    Case Else '0 默认（按 UUID 性别）
                        Out = Fallback
                End Select
            Catch ex As Exception
                Logger.Warn(ex, $"皮肤预览获取失败（Type={T}），回退默认皮肤")
                Out = Fallback
            End Try
            If Out = "" Then Out = Fallback
            Dim Bmp As BitmapSource = DecodeSkin(Out)
            '皮肤站取回来的图顺手记成本地缓存：断网时加载器还能用它兜底
            Dim CachePath As String = ""
            If (T = 5 OrElse T = 6) AndAlso Out.Contains("Cache\Skin\") Then CachePath = Out
            RunInUi(
            Sub()
                If MyConverter.IsExited OrElse Ticket <> PreviewTicket Then Return '弹窗已关或选择已变化
                If CachePath <> "" Then PendingCache = CachePath
                If WarnText <> "" Then LabHint.Text = WarnText
                Skin3D.SetSkin(Bmp, SlimOverride)
                If Bmp Is Nothing Then LabHint.Text = ModLang.L("皮肤图片读取失败，可能文件已损坏或被删除。")
            End Sub)
        End Sub, "Skin Legacy Preview " & Uuid)
    End Sub

    ''' <summary>
    ''' 解码皮肤 PNG 为可跨线程使用的冻结位图。
    ''' <para/>
    ''' 修正：内置皮肤是 pack:// 资源，不能用 IO.File.OpenRead 读（这正是上一版 3D 一片空白的原因），
    ''' 必须走 Application.GetResourceStream 拿到资源流。
    ''' </summary>
    Private Shared Function DecodeSkin(Path As String) As BitmapSource
        Try
            If Path = "" Then Return Nothing
            Dim Src As IO.Stream = Nothing
            If Path.StartsWith("pack://", StringComparison.OrdinalIgnoreCase) Then
                Dim Info = System.Windows.Application.GetResourceStream(New Uri(Path, UriKind.Absolute))
                If Info Is Nothing Then
                    Logger.Warn($"皮肤资源不存在：{Path}")
                    Return Nothing
                End If
                Src = Info.Stream
            ElseIf FileUtils.Exists(Path) Then
                Src = IO.File.OpenRead(Path)
            Else
                Logger.Warn($"皮肤文件不存在：{Path}")
            End If
            Using Src
                Dim Dec As New PngBitmapDecoder(Src, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad)
                Dim Bmp = Dec.Frames(0)
                Bmp.Freeze()
                Return Bmp
            End Using
        Catch ex As Exception
            Logger.Warn(ex, $"皮肤图片解码失败：{Path}")
            Return Nothing
        End Try
    End Function

#End Region


    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try
            '动画
            Opacity = 0
            AniStart(AaColor(FrmMain.PanMsg, Grid.BackgroundProperty, New MyColor(90, 0, 0, 0) - FrmMain.PanMsg.Background, 200), "PanMsg Background")
            AniStart({
                AaOpacity(Me, 1, 140, 40),
                AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 300, 50, New AniEaseOutFluent(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 340, 50, New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 340, 50, New AniEaseOutBack(AniEasePower.Middle))
            }, "MyMsgBox " & Uuid)
            Logger.Info($"皮肤选择弹窗：{LabTitle.Text}")
        Catch ex As Exception
            Logger.Error(ex, "皮肤选择弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    ''' <summary>取消：关闭且不应用暂存的选择（HMCL「取消」语义）。</summary>
    Public Sub Btn2_Click() Handles Btn2.Click
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = Nothing
        Close()
    End Sub

    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgSkinLegacy DragMove 失败")
        End Try
    End Sub

End Class
