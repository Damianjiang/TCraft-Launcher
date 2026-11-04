Public Class MyMenuItem
    Inherits MenuItem

    Private Sub MyMenuItem_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        If Icon IsNot Nothing Then
            Dim IconControl As Shapes.Path = GetTemplateChild("Icon")
            If IconControl IsNot Nothing Then IconControl.Data = (New GeometryConverter).ConvertFromString(Icon)
        End If
        RefreshSvgIcon()
        '第 124 轮：不再给 ContextMenu 设 Opacity。
        '旧做法乘了 UiLauncherTransparent（整窗透明度），底色又是硬编码纯白，结果是「底不透、字先淡」。
        '现在菜单底色由模板引用 ColorBrushCardBg，ModSecret 调整「组件半透明程度」时会同时改菜单，
        '再叠一层整体 Opacity 就会双重变淡，所以这里什么都不做。
    End Sub

    ''' <summary>
    ''' 矢量图标名（如 "lucide/play"），来自内置 lucide 图标包。
    ''' 用法与 MyListItem.SvgIcon 一致，颜色跟随本项的 Foreground（悬停会变成主题蓝）。
    ''' </summary>
    Private _SvgIcon As String = ""
    Public Property SvgIcon As String
        Get
            Return _SvgIcon
        End Get
        Set(value As String)
            If _SvgIcon = value Then Return
            _SvgIcon = value
            RefreshSvgIcon()
        End Set
    End Property

    Private Sub RefreshSvgIcon()
        Try
            Dim Host As Grid = TryCast(GetTemplateChild("IconHost"), Grid)
            If Host Is Nothing Then Return
            '清掉上一次插入的矢量图标
            For i As Integer = Host.Children.Count - 1 To 0 Step -1
                If TypeOf Host.Children(i) Is Global.TClUI.Controls.SvgIcon.SvgIcon Then Host.Children.RemoveAt(i)
            Next
            '几何路径与矢量图标互斥：有矢量图标时藏掉 Icon 的 Path
            Dim PathIcon As Shapes.Path = TryCast(GetTemplateChild("Icon"), Shapes.Path)
            If PathIcon IsNot Nothing Then
                If String.IsNullOrEmpty(_SvgIcon) Then
                    PathIcon.ClearValue(VisibilityProperty)
                Else
                    PathIcon.Visibility = Visibility.Collapsed
                End If
            End If
            If String.IsNullOrEmpty(_SvgIcon) Then Return
            Dim IconControl As New Global.TClUI.Controls.SvgIcon.SvgIcon With {
                .Icon = _SvgIcon,
                .Width = 16, .Height = 16,
                .HorizontalAlignment = HorizontalAlignment.Center,
                .VerticalAlignment = VerticalAlignment.Center,
                .IsHitTestVisible = False
            }
            IconControl.SetBinding(Global.TClUI.Controls.SvgIcon.SvgIcon.IconBrushProperty,
                                   New Data.Binding("Foreground") With {.Source = Me})
            Host.Children.Add(IconControl)
        Catch ex As Exception
            Logger.Warn(ex, $"菜单项矢量图标加载失败：{_SvgIcon}")
        End Try
    End Sub

    '基础

    Public Uuid As Integer = GetUuid()

    '指向动画

    Private Const AnimationTimeIn As Integer = 100
    Private Const AnimationTimeOut As Integer = 200
    Private ColorName As String
    Private Sub RefreshColor() Handles Me.MouseEnter, Me.MouseLeave, Me.IsEnabledChanged
        '判断当前颜色
        Dim BackName As String, ForeName As String
        Dim Time As Integer
        If Not IsEnabled Then
            BackName = "ColorBrushTransparent"
            ForeName = "ColorBrushGray5"
            Time = AnimationTimeOut
        ElseIf IsMouseOver Then
            '悬停色与列表项一致（MyListItem 悬停用 ColorBrushBg1），同一套「组件半透明程度」
            BackName = "ColorBrushBg1"
            ForeName = "ColorBrush2"
            Time = AnimationTimeIn
        Else
            BackName = "ColorBrushTransparent"
            ForeName = "ColorBrush1"
            Time = AnimationTimeOut
        End If
        '重复性验证
        If ColorName = BackName Then Return
        ColorName = BackName
        '触发颜色动画
        If IsLoaded AndAlso AniControlEnabled = 0 Then '防止默认属性变更触发动画
            '有动画
            AniStart({
                     AaColor(Me, BackgroundProperty, BackName, Time),
                     AaColor(Me, ForegroundProperty, ForeName, Time)
                 }, "MyMenuItem Color " & Uuid)
        Else
            '无动画
            AniStop("MyMenuItem Color " & Uuid)
            SetResourceReference(BackgroundProperty, BackName)
            SetResourceReference(ForegroundProperty, ForeName)
        End If
    End Sub

    Private Sub MyMenuItem_Click(sender As Object, e As RoutedEventArgs) Handles Me.Click
        RaiseCustomEvent()
    End Sub
End Class
