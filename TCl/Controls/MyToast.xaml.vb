Imports System.Windows
Imports System.Windows.Controls
Imports System.Windows.Input
Imports System.Windows.Media
Imports System.Windows.Media.Animation

''' <summary>
''' 右下角弹出通知条（样式对照 PCL-CE-dev 的 MyToast）：
''' 状态图标 + 粗体文本 + 关闭按钮 + 底部剩余时间进度条，支持向右拖动关闭。
''' 由 ModMain.HintTick 创建并加入 FormMain.PanHint，展示请调用 <see cref="Show"/>。
''' </summary>
Public Class MyToast

    Public Uuid As Integer = GetUuid()

    ''' <summary>判定为拖动而非点击的最小水平位移（像素）。</summary>
    Private Const DragDeadzone As Double = 4
    ''' <summary>拖动时透明度下限，确保控件始终可见。</summary>
    Private Const DragOpacityFloor As Double = 0.35
    ''' <summary>触发关闭的位移占控件宽度的比例。</summary>
    Private Const DismissThresholdRatio As Double = 0.12
    ''' <summary>触发关闭的最小绝对位移（像素）。</summary>
    Private Const DismissThresholdMin As Double = 24
    ''' <summary>拖动释放后，若剩余显示时间不足此值则直接关闭。</summary>
    Private Const MinRemainingMs As Double = 300
    ''' <summary>拖动释放后回到原位的动画时长（毫秒）。</summary>
    Private Const ReturnAnimationMs As Integer = 150

    '拖动状态
    Private _dragPending As Boolean
    Private _isDragging As Boolean
    Private _dragStartPoint As Point
    Private _dragStartTranslateX As Double
    Private _dragReference As FrameworkElement

    '进度条状态
    Private _progressStartWidth As Double
    Private _progressTotalMs As Double

    ''' <summary>展开后的目标高度。</summary>
    Private _targetHeight As Double = 45

    ''' <summary>是否正在退场（退场中不再接受强调/重复拖动）。</summary>
    Public IsDismissing As Boolean = False
    ''' <summary>是否已关闭（已从 PanHint 移除）。</summary>
    Public IsClosed As Boolean = False

    ''' <summary>本通知展示的状态种类。</summary>
    Public ReadOnly Kind As HintType

    ''' <summary>通知文本（用于查重与显示时长估算）。</summary>
    Public Property Context As String
        Get
            Return TitleText.Text
        End Get
        Set(value As String)
            TitleText.Text = value
        End Set
    End Property

    Public Sub New(Text As String, Type As HintType)
        Kind = Type
        InitializeComponent()
        Context = If(Text, "")
        AddHandler BtnClose.Click, Sub(sender As Object, e As EventArgs) Dismiss()
        UpdateColors()
    End Sub

    ''' <summary>控件被移除时清空动画与进度条，避免残留回调。</summary>
    Private Sub MyToast_Unloaded(sender As Object, e As RoutedEventArgs) Handles Me.Unloaded
        AniStop("Toast Show " & Uuid)
        AniStop("Toast Hide " & Uuid)
        AniStop("Toast Emphasize " & Uuid)
        AniStop("Toast Dismiss " & Uuid)
        AniStop("Toast Drag Return " & Uuid)
        ProgressBar.BeginAnimation(WidthProperty, Nothing)
    End Sub

    ''' <summary>进入动画并开始倒计时。</summary>
    Public Sub Show()
        If Not TypeOf Parent Is Panel Then Return
        '通知条底部与右下角悬浮按钮组保持距离（对照参考项目 HintPresenter）
        Dim ExtraHeight As Double = FrmMain.PanExtraButtons.ActualHeight
        FrmMain.PanHint.Margin = New Thickness(0, 0, 0, If(ExtraHeight > 0, ExtraHeight + 20, 20))
        If Application.Current.MainWindow IsNot Nothing Then
            MaxWidth = Application.Current.MainWindow.ActualWidth * 0.9
        End If
        Margin = New Thickness(0, 0, 16, 4)
        Opacity = 0

        '先量出完整高度，再从 0 展开（对照 PCL-CE MyToast 实现）
        Measure(New Size(Double.PositiveInfinity, Double.PositiveInfinity))
        Arrange(New Rect(0, 0, DesiredSize.Width, DesiredSize.Height))
        _targetHeight = Math.Max(ActualHeight, 45)
        Height = 0

        RenderTransform = New TranslateTransform(60, 0)

        AniStop("Toast Drag Return " & Uuid)
        AniStart({
            AaX(Me, -60, , , New AniEaseOutFluent),
            AaHeight(Me, _targetHeight, 150, , New AniEaseOutFluent),
            AaOpacity(Me, 1, 100)
        }, "Toast Show " & Uuid)

        RestartHideAnimation()
    End Sub

    ''' <summary>重复提示时晃动提醒，并重新开始计时。</summary>
    Public Sub Emphasize()
        AniStop("Toast Show " & Uuid)
        AniStop("Toast Hide " & Uuid)
        AniStop("Toast Emphasize " & Uuid)
        AniStop("Toast Drag Return " & Uuid)
        AniStop("Toast Dismiss " & Uuid)
        ProgressBar.BeginAnimation(WidthProperty, Nothing)
        IsDismissing = False
        If TypeOf RenderTransform Is TranslateTransform Then CType(RenderTransform, TranslateTransform).X = 0
        Opacity = 1
        Height = _targetHeight
        AniStart({
            AaX(Me, -8, 70, , New AniEaseOutFluent),
            AaX(Me, 16, 70, , , True),
            AaX(Me, -8, 60, , New AniEaseOutFluent, True),
            AaCode(AddressOf RestartHideAnimation, , True)
        }, "Toast Emphasize " & Uuid)
    End Sub

    ''' <summary>显示时长（毫秒），按正文长度估算，与参考项目一致。</summary>
    Private ReadOnly Property DisplayDuration As Double
        Get
            Return (800 + Context.Length.Clamp(5, 23) * 180) * AniSpeed
        End Get
    End Property

    Private Sub RestartHideAnimation()
        StartHideAnimation(DisplayDuration)
        StartProgressAnimation(DisplayDuration)
    End Sub

    Private Sub StartHideAnimation(DelayMs As Double)
        Dim Delay As Integer = CInt(Math.Round(DelayMs))
        AniStart({
            AaX(Me, 60, 200, Delay, New AniEaseInFluent),
            AaOpacity(Me, -1, 150, Delay),
            AaHeight(Me, -_targetHeight, 100, , New AniEaseOutFluent, True),
            AaCode(AddressOf OnHideFinished, , True)
        }, "Toast Hide " & Uuid)
    End Sub

    ''' <summary>手动关闭（点关闭按钮或拖出阈值）。</summary>
    Public Sub Dismiss()
        If IsDismissing Then Return
        IsDismissing = True
        _isDragging = False
        _dragPending = False
        If IsMouseCaptured Then ReleaseMouseCapture()
        AniStop("Toast Show " & Uuid)
        AniStop("Toast Hide " & Uuid)
        AniStop("Toast Emphasize " & Uuid)
        AniStop("Toast Drag Return " & Uuid)
        ProgressBar.BeginAnimation(WidthProperty, Nothing)
        AniStart({
            AaX(Me, 60, 150, , New AniEaseInFluent),
            AaOpacity(Me, -1, 100),
            AaCode(AddressOf OnHideFinished, , True)
        }, "Toast Dismiss " & Uuid)
    End Sub

    Private Sub OnHideFinished()
        If TypeOf Parent Is Panel Then CType(Parent, Panel).Children.Remove(Me)
        IsClosed = True
    End Sub

    Private Sub StartProgressAnimation(Duration As Double)
        Dim TotalMs As Integer = CInt(Math.Round(Duration))
        If TotalMs <= 0 Then Return
        Dim W As Double = ProgressBar.ActualWidth
        If W <= 0 Then W = 300
        ProgressBar.HorizontalAlignment = HorizontalAlignment.Left
        ProgressBar.Width = W
        _progressStartWidth = W
        _progressTotalMs = TotalMs
        ProgressBar.BeginAnimation(WidthProperty, New DoubleAnimation(W, 0, TimeSpan.FromMilliseconds(TotalMs)))
    End Sub

    ''' <summary>
    ''' 配色：背景/文字/信息类强调色全部走全局主题资源（DynamicResource，主题刷新时自动跟随）；
    ''' 成功/失败两色保留语义色（对照参考项目：按种类固定色相，信息类则与启动器主题同色系）。
    ''' </summary>
    Private Sub UpdateColors()
        Select Case Kind
            Case HintType.Green 'Success：语义绿（参考项目 hsl(145,75%,60%)）
                ToastIcon.Icon = "lucide/circle-check"
                ToastIcon.IconBrush = New MyColor(255, 77, 230, 140)
                ProgressBar.Fill = New MyColor(255, 77, 230, 140)
            Case HintType.Red 'Error：语义红（参考项目 hsl(355,75%,60%)）
                ToastIcon.Icon = "lucide/circle-minus"
                ToastIcon.IconBrush = New MyColor(255, 230, 77, 89)
                ProgressBar.Fill = New MyColor(255, 230, 77, 89)
            Case Else 'HintType.Blue：信息类提示跟随全局主题强调色（金主题下图标与进度条就是金色）
                ToastIcon.Icon = "lucide/info"
                ToastIcon.SetResourceReference(Global.TClUI.Controls.SvgIcon.SvgIcon.IconBrushProperty, "ColorBrush4")
                ProgressBar.SetResourceReference(System.Windows.Shapes.Shape.FillProperty, "ColorBrush4")
        End Select
        '背景：全局卡片底色（跟随「组件半透明程度」个性化，与 MyCard 内容卡片一致，壁纸磨砂透出）
        Root.SetResourceReference(BackgroundProperty, "ColorBrushCardBg")
        '文字：全局灰 1（#404040，静态色板，不随主题刷新）
        TitleText.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrushGray1")
        BtnClose.Foreground = New MyColor(255, 64, 64, 64) '同为 ColorBrushGray1（CLR 属性，无法走资源引用）
    End Sub

    Private Sub RootGrid_SizeChanged(sender As Object, e As SizeChangedEventArgs)
        '裁掉进度条出圆角的部分
        RootGrid.Clip = New RectangleGeometry(New Rect(0, 0, RootGrid.ActualWidth, RootGrid.ActualHeight), 8, 8)
    End Sub

#Region "拖动关闭"

    Private Sub Toast_PreviewMouseLeftButtonDown(sender As Object, e As MouseButtonEventArgs) Handles Me.PreviewMouseLeftButtonDown
        _dragPending = False
        If IsDismissing Then Return
        If IsVisualDescendantOf(TryCast(e.OriginalSource, DependencyObject), BtnClose) Then Return
        _dragReference = TryCast(Parent, FrameworkElement)
        If _dragReference Is Nothing Then Return
        _dragPending = True
        _isDragging = False
        _dragStartPoint = e.GetPosition(_dragReference)
    End Sub

    Private Sub Toast_PreviewMouseMove(sender As Object, e As MouseEventArgs) Handles Me.PreviewMouseMove
        If _isDragging Then
            If Mouse.LeftButton <> MouseButtonState.Pressed OrElse _dragReference Is Nothing Then
                _isDragging = False
                _dragPending = False
                If IsMouseCaptured Then ReleaseMouseCapture()
                ReturnFromDrag()
                Return
            End If
            Dim DragCurrent = e.GetPosition(_dragReference)
            UpdateDragPosition(DragCurrent.X - _dragStartPoint.X)
            e.Handled = True
            Return
        End If

        If Not _dragPending Then Return
        If Mouse.LeftButton <> MouseButtonState.Pressed OrElse _dragReference Is Nothing Then
            _dragPending = False
            Return
        End If

        Dim Current = e.GetPosition(_dragReference)
        Dim Delta = Current.X - _dragStartPoint.X

        If Delta < DragDeadzone Then Return

        BeginDrag(Delta)
    End Sub

    Private Sub Toast_PreviewMouseLeftButtonUp(sender As Object, e As MouseButtonEventArgs) Handles Me.PreviewMouseLeftButtonUp
        If _dragPending AndAlso Not _isDragging Then
            _dragPending = False
            Return
        End If

        If Not _isDragging Then Return

        _isDragging = False
        _dragPending = False
        e.Handled = True

        If IsMouseCaptured Then ReleaseMouseCapture()

        Dim CurrentX = GetCurrentTranslateX()
        If CurrentX - _dragStartTranslateX >= GetDismissThreshold() Then
            Dismiss()
            Return
        End If

        ReturnFromDrag()
    End Sub

    Private Sub Toast_LostMouseCapture(sender As Object, e As MouseEventArgs) Handles Me.LostMouseCapture
        If Not _isDragging Then Return
        _isDragging = False
        _dragPending = False
        ReturnFromDrag()
    End Sub

    Private Sub BeginDrag(InitialDelta As Double)
        _isDragging = True
        _dragPending = False

        AniStop("Toast Show " & Uuid)
        AniStop("Toast Hide " & Uuid)
        AniStop("Toast Emphasize " & Uuid)
        AniStop("Toast Drag Return " & Uuid)

        PauseProgress()

        _dragStartTranslateX = GetCurrentTranslateX()

        CaptureMouse()

        UpdateDragPosition(InitialDelta)
    End Sub

    Private Sub UpdateDragPosition(Delta As Double)
        Dim NewX = _dragStartTranslateX + ApplyDragResistance(Delta)
        If TypeOf RenderTransform Is TranslateTransform Then CType(RenderTransform, TranslateTransform).X = NewX
        Opacity = GetDragOpacity(NewX)
    End Sub

    Private Sub ReturnFromDrag()
        If Parent Is Nothing OrElse IsDismissing Then Return
        Dim CurrentX = GetCurrentTranslateX()
        Dim CurrentOpacity = Opacity

        Dim Remaining = GetProgressRemainingMs()
        If Remaining < MinRemainingMs Then
            Dismiss()
            Return
        End If

        ResumeProgress(Remaining)

        AniStart({
            AaX(Me, -CurrentX, ReturnAnimationMs, , New AniEaseOutFluent),
            AaOpacity(Me, 1 - CurrentOpacity, ReturnAnimationMs),
            AaCode(Sub() StartHideAnimation(GetProgressRemainingMs()), , True)
        }, "Toast Drag Return " & Uuid)
    End Sub

    ''' <summary>只允许向右拖（拖向屏幕外）。</summary>
    Private Shared Function ApplyDragResistance(Delta As Double) As Double
        Return Math.Max(0, Delta)
    End Function

    Private Function GetDragOpacity(TranslateX As Double) As Double
        If TranslateX <= 0 Then Return 1
        Dim Width = If(ActualWidth > 0, ActualWidth, 1)
        Return Math.Max(DragOpacityFloor, 1 - (TranslateX / Width) * (1 - DragOpacityFloor))
    End Function

    Private Function GetDismissThreshold() As Double
        Return Math.Max(DismissThresholdMin, ActualWidth * DismissThresholdRatio)
    End Function

    Private Function GetCurrentTranslateX() As Double
        If TypeOf RenderTransform Is TranslateTransform Then Return CType(RenderTransform, TranslateTransform).X
        Return 0
    End Function

    ''' <summary>判断是否为指定祖先元素的视觉子级（避免隐藏 Visual.IsDescendantOf）。</summary>
    Private Shared Function IsVisualDescendantOf(Descendant As DependencyObject, Ancestor As DependencyObject) As Boolean
        While Descendant IsNot Nothing
            If Descendant Is Ancestor Then Return True
            Descendant = VisualTreeHelper.GetParent(Descendant)
        End While
        Return False
    End Function

#End Region

#Region "进度条暂停与恢复"

    Private Sub PauseProgress()
        Dim CurrentWidth = ProgressBar.Width
        ProgressBar.BeginAnimation(WidthProperty, Nothing)
        ProgressBar.Width = CurrentWidth
    End Sub

    Private Sub ResumeProgress(RemainingMs As Double)
        If RemainingMs <= 0 Then Return
        Dim CurrentWidth = ProgressBar.Width
        If CurrentWidth <= 0 Then Return
        ProgressBar.BeginAnimation(WidthProperty, New DoubleAnimation(CurrentWidth, 0, TimeSpan.FromMilliseconds(RemainingMs)))
    End Sub

    Private Function GetProgressRemainingMs() As Double
        If _progressStartWidth <= 0 Then Return 0
        Dim CurrentWidth = ProgressBar.Width
        Return _progressTotalMs * (CurrentWidth / _progressStartWidth)
    End Function

#End Region

End Class
