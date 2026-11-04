Public Class MyScrollBar
    Inherits Primitives.ScrollBar

    '实例唯一标识，用于隔离动画命名。
    Public Uuid As Integer = GetUuid()

    ''' <summary>
    ''' 滚动条在某一状态下应呈现的外观：目标不透明度、目标刷色资源名、过渡时长（毫秒）。
    ''' 数值与原实现逐一对应，仅在此集中定义以便阅读。
    ''' </summary>
    Private Class Look
        Public ReadOnly Property Opacity As Double
        Public ReadOnly Property Brush As String
        Public ReadOnly Property Ms As Integer
        Public Sub New(Opacity As Double, Brush As String, Ms As Integer)
            Me.Opacity = Opacity
            Me.Brush = Brush
            Me.Ms = Ms
        End Sub
    End Class

    ''' <summary>
    ''' 依“不可见 / 捕获中 / 悬停 / 静止”四种状态解析目标外观。
    ''' </summary>
    Private Function ResolveLook() As Look
        If Not IsVisible Then
            '不可见时淡出；用较短时长，避免错误的尺寸判断造成闪烁。
            Return New Look(0, "ColorBrush4", 20)
        ElseIf IsMouseCaptureWithin Then
            '鼠标捕获中：完全不透明。
            Return New Look(1, "ColorBrush4", 50)
        ElseIf IsMouseOver Then
            '悬停：近乎不透明，并切换到强调刷色。
            Return New Look(0.9, "ColorBrush3", 130)
        Else
            '静止：半透明。
            Return New Look(0.5, "ColorBrush4", 180)
        End If
    End Function

    Private Sub RefreshColor() Handles Me.IsEnabledChanged, Me.GotMouseCapture, Me.LostMouseCapture, Me.MouseEnter, Me.MouseLeave, Me.IsVisibleChanged
        Try
            Dim Look As Look = ResolveLook()
            '默认开启动画（AniControlEnabled = 0）时走平滑过渡；否则直接落值。
            If IsLoaded AndAlso AniControlEnabled = 0 Then
                AniStart({
                         AaColor(Me, ForegroundProperty, Look.Brush, Look.Ms),
                         AaOpacity(Me, Look.Opacity - Opacity, Look.Ms)
                 }, "MyScrollBar Color " & Uuid)
            Else
                AniStop("MyScrollBar Color " & Uuid)
                SetResourceReference(ForegroundProperty, Look.Brush)
                Opacity = Look.Opacity
            End If
        Catch ex As Exception
            Logger.Warn(ex, "滚动条颜色改变出错")
        End Try
    End Sub

End Class
