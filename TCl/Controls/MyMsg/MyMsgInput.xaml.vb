Public Class MyMsgInput

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()

    Public Sub New(Converter As MyMsgBoxConverter)
        Try

            InitializeComponent()
            Btn1.Name = Btn1.Name & GetUuid()
            Btn2.Name = Btn2.Name & GetUuid()
            MyConverter = Converter
            'i18n：弹窗标题/正文/按钮走 L。命中词典则翻译，未命中（多为动态拼接文案）保持原文。
            LabTitle.Text = ModLang.L(Converter.Title)
            LabText.Text = ModLang.L(Converter.Text)
            PanText.Visibility = If(Converter.Text = "", Visibility.Collapsed, Visibility.Visible)
            TextArea.Text = Converter.Content
            TextArea.HintText = ModLang.L(Converter.HintText)
            TextArea.ValidateRules = Converter.ValidateRules
            Btn1.Text = ModLang.L(Converter.Button1)
            If Converter.IsWarn Then
                Btn1.ColorType = MyButton.ColorState.Red
                LabTitle.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrushRedLight")
            End If
            Btn2.Text = ModLang.L(Converter.Button2)
            Btn2.Visibility = If(Converter.Button2 = "", Visibility.Collapsed, Visibility.Visible)
            ShapeLine.StrokeThickness = GetWPFSize(1)

        Catch ex As Exception
            Logger.Error(ex, "输入弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try

            'UI 初始化
            If Btn2.IsVisible AndAlso Not Btn1.ColorType = MyButton.ColorState.Red Then Btn1.ColorType = MyButton.ColorState.Highlight
            TextArea.Focus()
            TextArea.SelectionStart = TextArea.Text.Length
            '动画
            Opacity = 0
            AniStart(AaColor(FrmMain.PanMsg, Grid.BackgroundProperty, If(MyConverter.IsWarn, New MyColor(140, 80, 0, 0), New MyColor(90, 0, 0, 0)) - FrmMain.PanMsg.Background, 200), "PanMsg Background")
            AniStart({
                AaOpacity(Me, 1, 140, 40),
                AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 300, 50, New AniEaseOutFluent(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 340, 50, New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 340, 50, New AniEaseOutBack(AniEasePower.Middle))
            }, "MyMsgBox " & Uuid)
            '记录日志
            Logger.Info($"输入弹窗：{LabTitle.Text}")

        Catch ex As Exception
            Logger.Error(ex, "输入弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub
    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    Public Sub Btn1_Click() Handles Btn1.Click
        TextArea.Validate()
        If MyConverter.IsExited OrElse Not TextArea.IsValidated Then Return
        MyConverter.IsExited = True
        MyConverter.Result = TextArea.Text
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
    End Sub

    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgInput DragMove 失败")
        End Try
    End Sub

End Class
