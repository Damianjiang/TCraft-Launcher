Imports System.Windows

''' <summary>
''' "添加 LittleSkin / 第三方登录账号" 表单弹窗：玩家名 + 密码 + 认证服务器（可拖入按钮自动识别）。
''' 与 MyMsgOffline 表单同一模式（"添加账户" → 选 LittleSkin 后弹出）。提交即回传 AuthAccountData，
''' 由调用方登记进账户列表，不强制先下载游戏。
''' </summary>
Public Class MyMsgAuth

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()

    Private Const LITTLE_SKIN_DEFAULT As String = "https://littleskin.cn/api/yggdrasil"
    Private Const LITTLE_SKIN_REGISTER As String = "https://littleskin.cn/auth/register"
    Private Const AUTHLIB_PREFIX As String = "authlib-injector:yggdrasil-server:"

    Public Sub New(Converter As MyMsgBoxConverter)
        Try
            InitializeComponent()
            Btn1.Name = Btn1.Name & GetUuid()
            Btn2.Name = Btn2.Name & GetUuid()
            MyConverter = Converter
            LabTitle.Text = ModLang.L(Converter.Title)
            Btn1.Text = ModLang.L(Converter.Button1)
            Btn2.Text = ModLang.L(Converter.Button2)
            ShapeLine.StrokeThickness = GetWPFSize(1)
            TextServer.Text = LITTLE_SKIN_DEFAULT
            TextServer.HintText = ModLang.L("Yggdrasil 认证服务器完整地址；可把 LittleSkin「外置登录」按钮拖进本行自动填入")
            TextName.HintText = ModLang.L("建议使用英文字符、数字以及下划线命名，且长度不超过 16 个字符")
            TextName.ValidateRules = New ObjectModel.Collection(Of Validate) From {
                New ValidateFunc(Function(Str) If(Str Is Nothing OrElse Str.Trim = "", ModLang.L("必填项"), ""))}
        Catch ex As Exception
            Logger.Error(ex, "添加 LittleSkin 账号弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try
            Btn1.ColorType = MyButton.ColorState.Highlight
            TextName.Focus()
            TextName.SelectionStart = TextName.Text.Length
            Opacity = 0
            AniStart(AaColor(FrmMain.PanMsg, Grid.BackgroundProperty, New MyColor(90, 0, 0, 0) - FrmMain.PanMsg.Background, 200), "PanMsg Background")
            AniStart({
                AaOpacity(Me, 1, 140, 40),
                AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 300, 50, New AniEaseOutFluent(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 340, 50, New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 340, 50, New AniEaseOutBack(AniEasePower.Middle))
            }, "MyMsgBox " & Uuid)
            Logger.Info("添加 LittleSkin 账号弹窗")
        Catch ex As Exception
            Logger.Error(ex, "添加 LittleSkin 账号弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    Public Sub Btn1_Click() Handles Btn1.Click
        If MyConverter.IsExited Then Return
        TextName.Validate()
        If Not TextName.IsValidated Then
            LabError.Text = TextName.ValidateResult
            Return
        End If
        Dim Pass As String = TextPass.Password.Trim
        If Pass = "" Then
            LabError.Text = ModLang.L("必填项")
            TextPass.Focus()
            Return
        End If
        Dim Name As String = TextName.Text.Replace("¨", "").Trim
        Dim Server As String = TextServer.Text.Trim
        '服务器合法性：Yggdrasil API 必须是 http(s) 地址
        If String.IsNullOrEmpty(New ValidateHttp().Validate(Server)) Then
            LabError.Text = ModLang.L("认证服务器地址不符合网址格式！")
            TextServer.Focus()
            Return
        End If
        '注册链接：若填的是 LittleSkin，自动带上注册页；否则保持原样
        Dim Register As String = If(Server.Contains("littleskin.cn"), LITTLE_SKIN_REGISTER, "")
        MyConverter.IsExited = True
        MyConverter.Result = New AuthAccountData With {.Name = Name, .Pass = Pass, .Server = Server, .Register = Register}
        Close()
    End Sub

    Public Sub Btn2_Click() Handles Btn2.Click
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = Nothing
        Close()
    End Sub

    Private Sub TextName_ValidateChanged(sender As Object, e As EventArgs) Handles TextName.ValidateChanged
        If TextName.IsValidated Then LabError.Text = ""
        Btn1.IsEnabled = TextName.IsValidated
    End Sub

    '==== 拖放识别：把 LittleSkin「外置登录」按钮（authlib-injector:yggdrasil-server:<url> 文本）拖进来 ====
    Private Sub OnTextDragEnter(sender As Object, e As DragEventArgs) Handles Me.DragEnter
        '允许任何文本拖入；具体识别在 Drop 时做
        If e.Data.GetDataPresent(DataFormats.Text) Then
            e.Effects = DragDropEffects.Copy
        Else
            e.Effects = DragDropEffects.None
        End If
        e.Handled = True
    End Sub

    Private Sub OnTextDrop(sender As Object, e As DragEventArgs) Handles Me.Drop
        Try
            If Not e.Data.GetDataPresent(DataFormats.Text) Then Return
            Dim Str As String = e.Data.GetData(DataFormats.Text)
            If String.IsNullOrEmpty(Str) Then Return
            'LittleSkin 官方按钮：authlib-injector:yggdrasil-server:<url>
            If Str.StartsWithF(AUTHLIB_PREFIX) Then
                Dim Url As String = StringUtils.FormUrlUnescape(Str.Substring(AUTHLIB_PREFIX.Length))
                If String.IsNullOrEmpty(New ValidateHttp().Validate(Url)) Then
                    LabError.Text = ModLang.L("拖入的认证服务器地址不符合网址格式！")
                    Return
                End If
                TextServer.Text = Url
                LabError.Text = ""
                Logger.Info("LittleSkin 按钮拖入：已识别认证服务器 " & Url)
            Else
                '普通文本（比如直接拖一段 URL）：也允许填到认证服务器框里
                TextServer.Text = Str.Trim
                LabError.Text = ""
            End If
        Catch ex As Exception
            Logger.Warn(ex, "添加 LittleSkin 账号弹窗拖入识别失败")
        End Try
    End Sub

    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgAuth DragMove 失败")
        End Try
    End Sub

End Class
