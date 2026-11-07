''' <summary>
''' HMCL 式「添加账户」弹窗：先选择账户类型（微软账号 / 离线账号），再进入对应流程。
''' 取代原先左栏平铺的两个独立入口，普通用户只需点一次「添加账户」即可看到全部可选项。
''' 返回值：1 = 微软账号，2 = 离线账号；取消或关闭返回 Nothing。
''' </summary>
Public Class MyMsgAddAccount

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()

    Public Sub New(Converter As MyMsgBoxConverter)
        Try

            InitializeComponent()
            Btn2.Name = Btn2.Name & GetUuid()
            MyConverter = Converter
            LabTitle.Text = ModLang.L(Converter.Title)
            ShapeLine.StrokeThickness = GetWPFSize(1)
            '两个选项：图标 + 主标题 + 副标题（HMCL 的 title/subtitle 两行）
            ItemMs.Info = ModLang.L("使用微软账号登录，支持正版皮肤与跨设备同步")
            '开源版未内置微软登录凭据：未配置 TCL_MS_CLIENT_ID 时隐藏微软登录选项
            If String.IsNullOrEmpty(OAuthClientId) Then
                ItemMs.Title = ModLang.L("微软账号（未启用）")
                ItemMs.Info = ModLang.L("开源版未内置微软登录凭据；设置环境变量 TCL_MS_CLIENT_ID 后可用")
                ItemMs.IsEnabled = False
                ItemMs.Opacity = 0.45
            End If
            ItemOffline.Info = ModLang.L("无需正版账号，输入玩家名即可直接游玩")
            ItemAuth.Info = ModLang.L("使用 LittleSkin 账号登录并加载其皮肤，第三方 Yggdrasil 外置登录")
            ItemMs.Type = MyListItem.CheckType.RadioBox
            ItemOffline.Type = MyListItem.CheckType.RadioBox
            ItemAuth.Type = MyListItem.CheckType.RadioBox
            AddHandler ItemMs.Check, AddressOf OnChecked
            AddHandler ItemOffline.Check, AddressOf OnChecked
            AddHandler ItemAuth.Check, AddressOf OnChecked

        Catch ex As Exception
            Logger.Error(ex, "添加账户弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try

            'UI 初始化
            Opacity = 0
            AniStart(AaColor(FrmMain.PanMsg, Grid.BackgroundProperty, New MyColor(90, 0, 0, 0) - FrmMain.PanMsg.Background, 200), "PanMsg Background")
            AniStart({
                AaOpacity(Me, 1, 140, 40),
                AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 300, 50, New AniEaseOutFluent(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 340, 50, New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 340, 50, New AniEaseOutBack(AniEasePower.Middle))
            }, "MyMsgBox " & Uuid)
            '记录日志
            Logger.Info($"添加账户弹窗：{LabTitle.Text}")

        Catch ex As Exception
            Logger.Error(ex, "添加账户弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    ''' <summary>选中某个账户类型：立即关闭弹窗并回传编号（1 = 微软，2 = 离线，3 = LittleSkin）。</summary>
    Private Sub OnChecked(sender As IMyRadio, e As EventArgs)
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = If(ReferenceEquals(sender, ItemMs), 1, If(ReferenceEquals(sender, ItemAuth), 3, 2))
        Close()
    End Sub

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
            Logger.Warn(ex, "MyMsgAddAccount DragMove 失败")
        End Try
    End Sub

End Class
