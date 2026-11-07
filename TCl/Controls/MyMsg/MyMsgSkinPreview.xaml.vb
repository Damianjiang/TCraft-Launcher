Imports Microsoft.Win32

''' <summary>
''' 皮肤预览弹窗：把一张已经取到本地的皮肤先 3D 预览，再决定是否保存。
''' <para/>
''' Converter.Content = <see cref="SkinPreviewArgs"/>（玩家名 + 本地文件 + 来源描述）。
''' 「保存」弹出另存为对话框写文件；「关闭」直接退出，不写任何东西。
''' </summary>
Public Class MyMsgSkinPreview

    Private ReadOnly MyConverter As MyMsgBoxConverter
    Private ReadOnly Uuid As Integer = GetUuid()
    Private ReadOnly Args As SkinPreviewArgs

    Public Sub New(Converter As MyMsgBoxConverter)
        Try
            InitializeComponent()
            BtnSave.Name = BtnSave.Name & GetUuid()
            BtnClose.Name = BtnClose.Name & GetUuid()
            MyConverter = Converter
            Args = If(TypeOf Converter.Content Is SkinPreviewArgs, CType(Converter.Content, SkinPreviewArgs), New SkinPreviewArgs)
            ShapeLine.StrokeThickness = GetWPFSize(1)

            LabTitle.Text = ModLang.L("查看皮肤") & If(String.IsNullOrEmpty(Args.Name), "", " - " & Args.Name)
            LabNameTitle.Text = ModLang.L("玩家名")
            LabModelTitle.Text = ModLang.L("模型")
            LabSourceTitle.Text = ModLang.L("来源")
            LabName.Text = If(String.IsNullOrEmpty(Args.Name), "—", Args.Name)
            LabSource.Text = If(String.IsNullOrEmpty(Args.Source), ModLang.L("正版玩家皮肤"), Args.Source)
            LabHint.Text = ModLang.L("拖动模型可旋转查看；点「保存」把这张皮肤存到本地。")
            BtnSave.Text = ModLang.L("保存")
            BtnClose.Text = ModLang.L("关闭")

            Dim Bmp = ReadSkin(Args.File)
            If Bmp Is Nothing Then
                LabModel.Text = "—"
                LabHint.Text = ModLang.L("皮肤图片读取失败，无法预览。")
                BtnSave.IsEnabled = False
            Else
                Skin3D.SetSkin(Bmp)
                LabModel.Text = ModLang.L(If(Skin3D.IsSlim, "纤细（3 像素手臂）", "经典（4 像素手臂）"))
            End If
        Catch ex As Exception
            Logger.Error(ex, "皮肤预览弹窗初始化失败", LogBehavior.Toast)
        End Try
    End Sub

    ''' <summary>读取本地皮肤图片（失败返回 Nothing）。</summary>
    Private Shared Function ReadSkin(Path As String) As BitmapSource
        Try
            If String.IsNullOrEmpty(Path) OrElse Not FileUtils.Exists(Path) Then Return Nothing
            Using Src As IO.Stream = IO.File.OpenRead(Path)
                Dim Dec As New PngBitmapDecoder(Src, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad)
                Dim Bmp = Dec.Frames(0)
                Bmp.Freeze()
                Return Bmp
            End Using
        Catch ex As Exception
            Logger.Warn(ex, $"皮肤预览：解码失败 {Path}")
            Return Nothing
        End Try
    End Function

    Private Sub LoadAnim(sender As Object, e As EventArgs) Handles MyBase.Loaded
        Try
            Opacity = 0
            AniStart(AaColor(FrmMain.PanMsg, Grid.BackgroundProperty, New MyColor(90, 0, 0, 0) - FrmMain.PanMsg.Background, 200), "PanMsg Background")
            AniStart({
                AaOpacity(Me, 1, 140, 40),
                AaDouble(Sub(i) TransformPos.Y += i, -TransformPos.Y, 300, 50, New AniEaseOutFluent(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 340, 50, New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 340, 50, New AniEaseOutBack(AniEasePower.Middle))
            }, "MyMsgBox " & Uuid)
        Catch ex As Exception
            Logger.Error(ex, "皮肤预览弹窗加载失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub Close()
        MyConverter.WaitFrame.Continue = False
        Interop.ComponentDispatcher.PopModal()
        '统一的关闭动画
        MyMsgCloseAnimation.Play(Me, Uuid, TransformPos, TransformScale)
    End Sub

    ''' <summary>关闭（不保存）。</summary>
    Public Sub BtnClose_Click() Handles BtnClose.Click
        If MyConverter.IsExited Then Return
        MyConverter.IsExited = True
        MyConverter.Result = Nothing
        Close()
    End Sub

    ''' <summary>另存为本地 PNG。</summary>
    Public Sub BtnSave_Click() Handles BtnSave.Click
        Try
            If String.IsNullOrEmpty(Args.File) OrElse Not FileUtils.Exists(Args.File) Then Return
            Dim DefaultName As String = If(String.IsNullOrEmpty(Args.Name), "skin", Args.Name) & ".png"
            Dim SavePath As String = Dialogs.SaveFile(ModLang.L("保存正版皮肤"), DefaultName, filter:={("png", ModLang.L("PNG 图片"))})
            If String.IsNullOrEmpty(SavePath) Then Return
            FileUtils.Copy(Args.File, SavePath)
            LabHint.Text = ModLang.L("皮肤已保存：") & SavePath
        Catch ex As Exception
            Logger.Error(ex, "皮肤预览：保存失败", LogBehavior.Toast)
            LabHint.Text = ModLang.L("保存失败：") & ex.Message
        End Try
    End Sub

    Private Sub Drag(sender As Object, e As MouseButtonEventArgs) Handles PanBorder.MouseLeftButtonDown, LabTitle.MouseLeftButtonDown
        Try
            If e.GetPosition(ShapeLine).Y <= 2 Then FrmMain.DragMove()
        Catch ex As Exception
            Logger.Warn(ex, "MyMsgSkinPreview DragMove 失败")
        End Try
    End Sub

End Class
