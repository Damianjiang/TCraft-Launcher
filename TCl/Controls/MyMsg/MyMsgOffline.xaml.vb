''' <summary>
''' HMCL 式“添加离线模式账户”弹窗：仅用户名（必填校验 + 命名建议提示）、购买链接、登录/取消按钮。
''' 早期版本带有「高级设置」折叠区（自定义 UUID），现已移除——UUID 一律由系统按用户名自动生成
''' （McLoginLegacyUuid 规则），普通用户无需理解 UUID 概念即可完成添加。
''' </summary>
Public Class MyMsgOffline

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()

    Public Sub New(Converter As MyMsgBoxConverter)
        Try

            InitializeComponent()
            Btn1.Name = Btn1.Name & GetUuid()
            Btn2.Name = Btn2.Name & GetUuid()
            MyConverter = Converter
            LabTitle.Text = ModLang.L(Converter.Title)
            TextArea.HintText = ModLang.L("建议使用英文字符、数字以及下划线命名，且长度不超过 16 个字符")
            TextArea.ValidateRules = New ObjectModel.Collection(Of Validate) From {
                New ValidateFunc(Function(Str) If(Str Is Nothing OrElse Str.Trim = "", ModLang.L("必填项"), ""))}
            Btn1.Text = ModLang.L(Converter.Button1)
            Btn2.Text = ModLang.L(Converter.Button2)
            ShapeLine.StrokeThickness = GetWPFSize(1)

        Catch ex As Exception
            Logger.Error(ex, "添加离线账户弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    ''' <summary>打开购买 Minecraft 的官网链接。</summary>
    Private Sub LinkPurchase_Click(sender As Object, e As MouseButtonEventArgs) Handles LinkPurchase.MouseLeftButtonUp
        Try
            OpenWebsite("https://www.minecraft.net/zh-hans/store/minecraft-java-bedrock-edition-pc")
        Catch ex As Exception
            Logger.Warn(ex, "打开购买链接失败")
        End Try
    End Sub

    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try

            'UI 初始化
            Btn1.ColorType = MyButton.ColorState.Highlight
            TextArea.Focus()
            TextArea.SelectionStart = TextArea.Text.Length
            '动画
            Opacity = 0
            AniStart(AaColor(FrmMain.PanMsg, Grid.BackgroundProperty, New MyColor(90, 0, 0, 0) - FrmMain.PanMsg.Background, 200), "PanMsg Background")
            AniStart({
                AaOpacity(Me, 1, 140, 40),
                AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 300, 50, New AniEaseOutFluent(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 340, 50, New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 340, 50, New AniEaseOutBack(AniEasePower.Middle))
            }, "MyMsgBox " & Uuid)
            '记录日志
            Logger.Info($"添加离线账户弹窗：{LabTitle.Text}")

        Catch ex As Exception
            Logger.Error(ex, "添加离线账户弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub
    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    ''' <summary>用户名是否符合 MC 命名规则（与 HMCL 一致：英文字母数字下划线，≤16 字符）。</summary>
    Private Function IsNameValidPattern(Name As String) As Boolean
        If Name = "" OrElse Name.Length > 16 Then Return False
        For Each C As Char In Name
            If C = "_"c Then Continue For
            If AscW(C) >= 128 OrElse Not Char.IsLetterOrDigit(C) Then Return False
        Next
        Return True
    End Function

    Public Sub Btn1_Click() Handles Btn1.Click
        TextArea.Validate()
        If MyConverter.IsExited OrElse Not TextArea.IsValidated Then Return
        Dim Name = TextArea.Text.Replace("¨", "").Trim
        '与 HMCL 一致：用户名不符合命名规则时弹出警告确认，允许继续
        If Not IsNameValidPattern(Name) Then
            LabError.Text = ""
            If MyMsgBox(ModLang.L("离线模式账户用户名建议仅使用英文字符、数字以及下划线，且长度不超过 16 个字符。确定要继续吗？"),
                        ModLang.L("警告"), ModLang.L("继续"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Else
            LabError.Text = ""
        End If
        MyConverter.IsExited = True
        '只回传用户名：UUID 交由系统按用户名自动生成
        MyConverter.Result = Name
        Close()
    End Sub
    Public Sub Btn2_Click() Handles Btn2.Click
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = Nothing
        Close()
    End Sub

    Private Sub TextCaption_ValidateChanged(sender As Object, e As EventArgs) Handles TextArea.ValidateChanged
        Btn1.IsEnabled = TextArea.IsValidated
        LabError.Text = If(TextArea.IsValidated, "", TextArea.ValidateResult)
    End Sub

    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgOffline DragMove 失败")
        End Try
    End Sub

End Class
