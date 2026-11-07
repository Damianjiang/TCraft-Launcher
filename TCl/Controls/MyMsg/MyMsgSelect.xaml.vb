Public Class MyMsgSelect

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()

    Public Sub New(Converter As MyMsgBoxConverter)
        Try

            InitializeComponent()
            Btn1.Name = Btn1.Name & GetUuid()
            Btn2.Name = Btn2.Name & GetUuid()
            MyConverter = Converter
            'i18n：弹窗标题/按钮走 L。命中词典则翻译，未命中（多为动态拼接文案）保持原文。
            LabTitle.Text = ModLang.L(Converter.Title)
            Btn1.Text = ModLang.L(Converter.Button1)
            If Converter.IsWarn Then
                Btn1.ColorType = MyButton.ColorState.Red
                LabTitle.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrushRedLight")
            End If
            Btn2.Text = ModLang.L(Converter.Button2)
            Btn2.Visibility = If(Converter.Button2 = "", Visibility.Collapsed, Visibility.Visible)
            ShapeLine.StrokeThickness = GetWPFSize(1)
            '添加选择控件
            Btn1.IsEnabled = False
            For Each Content In Converter.Content
                Content = MyVirtualizingElement.TryInit(Content)
                Dim Selection As IMyRadio = Content
                PanSelection.Children.Add(Selection)
                AddHandler Selection.Check, AddressOf OnChecked
                If TypeOf Selection Is MyListItem Then
                    CType(Selection, MyListItem).Type = MyListItem.CheckType.RadioBox
                    CType(Selection, MyListItem).MinHeight = 24
                Else
                    CType(Selection, MyRadioBox).MinHeight = 24
                End If
            Next

        Catch ex As Exception
            Logger.Error(ex, "选择弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try

            'UI 初始化
            If Btn2.IsVisible AndAlso Not Btn1.ColorType = MyButton.ColorState.Red Then Btn1.ColorType = MyButton.ColorState.Highlight
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
            Logger.Info($"选择弹窗：{LabTitle.Text}")

        Catch ex As Exception
            Logger.Error(ex, "选择弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub
    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    Public Sub Btn1_Click() Handles Btn1.Click
        If MyConverter.IsExited OrElse SelectedIndex = -1 Then Return
        MyConverter.IsExited = True
        MyConverter.Result = SelectedIndex
        Close()
    End Sub
    Public Sub Btn2_Click() Handles Btn2.Click
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = Nothing
        Close()
    End Sub

    Private SelectedIndex As Integer = -1
    Private Sub OnChecked(sender As IMyRadio, e As EventArgs)
        Btn1.IsEnabled = True
        SelectedIndex = PanSelection.Children.IndexOf(sender)
    End Sub
    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgSelect DragMove 失败")
        End Try
    End Sub

End Class
