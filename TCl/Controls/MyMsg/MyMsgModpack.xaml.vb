Public Class MyMsgModpack

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()

    Public Sub New(Converter As MyMsgBoxConverter)
        Try

            InitializeComponent()
            Btn1.Name = Btn1.Name & GetUuid()
            Btn2.Name = Btn2.Name & GetUuid()
            MyConverter = Converter
            'i18n：标题/按钮/正文走 L()，整合包名与类型是数据，不做翻译
            LabTitle.Text = ModLang.L(Converter.Title)
            Btn1.Text = ModLang.L(Converter.Button1)
            Btn2.Text = ModLang.L(Converter.Button2)
            If Not String.IsNullOrEmpty(Converter.Text) Then LabCaption.Text = ModLang.L(Converter.Text)
            ShapeLine.StrokeThickness = GetWPFSize(1)

            '填充整合包信息：图标 / 名称 / 类型与体积
            Dim Info As McModpackInfo = TryCast(Converter.Content, McModpackInfo)
            If Info IsNot Nothing Then
                LabPackName.Text = If(String.IsNullOrWhiteSpace(Info.Name),
                                      PathUtils.GetFileNameWithoutExtension(If(Info.File, "")), Info.Name)
                LabPackMeta.Text = If(String.IsNullOrWhiteSpace(Info.Detail), Info.TypeName, Info.TypeName & " · " & Info.Detail)
                If Not String.IsNullOrEmpty(Info.IconPath) AndAlso FileUtils.Exists(Info.IconPath) Then
                    ImgIcon.Source = Info.IconPath
                    PanIconEmpty.Visibility = Visibility.Collapsed
                Else
                    PanIconEmpty.Visibility = Visibility.Visible
                End If
            End If

        Catch ex As Exception
            Logger.Error(ex, "整合包确认弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Load(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try

            'UI 初始化
            Btn1.Focus()
            '动画（与其它弹窗一致）
            Opacity = 0
            AniStart(AaColor(FrmMain.PanMsg, Grid.BackgroundProperty, New MyColor(90, 0, 0, 0) - FrmMain.PanMsg.Background, 200), "PanMsg Background")
            AniStart({
                AaOpacity(Me, 1, 140, 40),
                AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 300, 50, New AniEaseOutFluent(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 340, 50, New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 340, 50, New AniEaseOutBack(AniEasePower.Middle))
            }, "MyMsgBox " & Uuid)
            Logger.Info($"整合包确认弹窗：{LabPackName.Text}（{LabPackMeta.Text}）")

        Catch ex As Exception
            Logger.Error(ex, "整合包确认弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    Public Sub Btn1_Click() Handles Btn1.Click
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = 1
        Close()
    End Sub
    Public Sub Btn2_Click() Handles Btn2.Click
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = 2
        Close()
    End Sub

    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgModpack DragMove 失败")
        End Try
    End Sub

End Class
