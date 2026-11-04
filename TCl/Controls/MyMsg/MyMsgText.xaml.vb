Public Class MyMsgText

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()

    Public Sub New(Converter As MyMsgBoxConverter)
        Try

            InitializeComponent()
            Btn1.Name = Btn1.Name & GetUuid()
            Btn2.Name = Btn2.Name & GetUuid()
            Btn3.Name = Btn3.Name & GetUuid()
            MyConverter = Converter
            'i18n：弹窗标题/正文/按钮走 L()。命中词典则翻译，未命中（多为动态拼接文案）保持原文。
            LabTitle.Text = ModLang.L(Converter.Title)
            LabCaption.Text = ModLang.L(Converter.Text)
            Btn1.Text = ModLang.L(Converter.Button1)
            If Converter.IsWarn Then
                Btn1.ColorType = MyButton.ColorState.Red
                LabTitle.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrushRedLight")
            End If
            Btn2.Text = ModLang.L(Converter.Button2)
            Btn3.Text = ModLang.L(Converter.Button3)
            Btn2.Visibility = If(Converter.Button2 = "", Visibility.Collapsed, Visibility.Visible)
            Btn3.Visibility = If(Converter.Button3 = "", Visibility.Collapsed, Visibility.Visible)
            ShapeLine.StrokeThickness = GetWPFSize(1)

        Catch ex As Exception
            Logger.Error(ex, "普通弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try

            'UI 初始化
            If Btn2.IsVisible AndAlso Not Btn1.ColorType = MyButton.ColorState.Red Then Btn1.ColorType = MyButton.ColorState.Highlight
            Btn1.Focus()
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
            Logger.Info($"普通弹窗：{LabTitle.Text}{vbCrLf}{LabCaption.Text}")

        Catch ex As Exception
            Logger.Error(ex, "普通弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub
    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画（第 136 轮：原先 8 个弹窗各复制一份）
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    Public Sub Btn1_Click() Handles Btn1.Click
        If MyConverter.IsExited Then Return
        If MyConverter.Button1Action IsNot Nothing Then
            MyConverter.Button1Action()
        Else
            MyConverter.IsExited = True
            MyConverter.Result = 1
            Close()
        End If
    End Sub
    Public Sub Btn2_Click() Handles Btn2.Click
        If MyConverter.IsExited Then Return
        If MyConverter.Button2Action IsNot Nothing Then
            MyConverter.Button2Action()
        Else
            MyConverter.IsExited = True
            MyConverter.Result = 2
            Close()
        End If
    End Sub
    Public Sub Btn3_Click() Handles Btn3.Click
        If MyConverter.IsExited Then Return
        If MyConverter.Button3Action IsNot Nothing Then
            MyConverter.Button3Action()
        Else
            MyConverter.IsExited = True
            MyConverter.Result = 3
            Close()
        End If
    End Sub

    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgText DragMove 失败")
        End Try
    End Sub

End Class
