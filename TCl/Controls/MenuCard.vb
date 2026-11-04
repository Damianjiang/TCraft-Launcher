' 通用动画弹出菜单卡片（第 131 轮）：首页「更多」/实例页同款三段动画（淡入 130ms + 缩放
' 0.94→1 OutBack 220ms + 上浮 10→0，关闭 80/90ms），全应用单例，用于替代裸 ContextMenu。
' 行 = 图标 + 标题，点击即关卡并执行动作；对同一目标再次 Show 为收起（切换语义）。
' 第 132 轮：新增 ApplyAnchor，按「锚点 + 对齐方向」精确定位（替代 Placement=Bottom + 硬编码偏移）。
Imports PopupControl = System.Windows.Controls.Primitives.Popup
Imports PopupPlacement = System.Windows.Controls.Primitives.CustomPopupPlacement
Imports PopupAxis = System.Windows.Controls.Primitives.PopupPrimaryAxis
Imports PlacementModes = System.Windows.Controls.Primitives.PlacementMode

Public Class MenuCard

    Private Shared ReadOnly SingleInstance As New MenuCard

    Private ReadOnly Popup As New System.Windows.Controls.Primitives.Popup With {
        .AllowsTransparency = True, .StaysOpen = False,
        .PopupAnimation = System.Windows.Controls.Primitives.PopupAnimation.None,
        .Placement = System.Windows.Controls.Primitives.PlacementMode.Custom}
    Private ReadOnly Card As Border
    Private ReadOnly TransformScale As New ScaleTransform(0.94, 0.94)
    Private ReadOnly TransformPos As New TranslateTransform(0, 10)
    Private ReadOnly Pan As New StackPanel
    ''' <summary>菜单内容滚动容器：用启动器自己的 MyScrollViewer（含 MyScrollBar 样式），
    ''' 条目超出屏幕时显示 TCL 风格滚动条（第 135 轮）。</summary>
    Private ReadOnly Scroll As New MyScrollViewer With {
        .VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
        .HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
        .Content = Pan}
    Private ClosedTick As Long = 0
    Private Const AnimName As String = "MenuCard Popup"
    ''' <summary>卡片 Border 给投影留的 Margin（与构造函数里一致）。</summary>
    Private Const ShadowPad As Double = 14

    ''' <summary>弹出菜单动画是否启用（个性化 → 弹出菜单，第 131 轮）。其他页面的弹卡动画也引用此设置。</summary>
    Public Shared ReadOnly Property PopupAnimEnabled As Boolean
        Get
            Try
                Return Settings.Get(Of Boolean)("UiMenuAnimation")
            Catch ex As Exception
                Return True
            End Try
        End Get
    End Property

    ''' <summary>弹出菜单卡片的基础不透明度 0.1~1（个性化 → 弹出菜单，第 131 轮）。</summary>
    Public Shared ReadOnly Property PopupBaseOpacity As Double
        Get
            Try
                Return (Settings.Get(Of Integer)("UiMenuOpacity") / 100).Clamp(0.1, 1)
            Catch ex As Exception
                Return 1
            End Try
        End Get
    End Property

    ''' <summary>菜单行：标题 / 图标 / 点击动作。</summary>
    Public Structure MenuCardRow
        Public Title As String
        Public Icon As String
        Public Action As Action
        Public Sub New(Title As String, Icon As String, Action As Action)
            Me.Title = Title
            Me.Icon = Icon
            Me.Action = Action
        End Sub
    End Structure

    Private Sub New()
        Card = New Border With {
            .CornerRadius = New CornerRadius(10), .Padding = New Thickness(8), .Margin = New Thickness(ShadowPad),
            .Opacity = 0, .RenderTransformOrigin = New Point(0.5, 0), .Child = Scroll}
        Dim TransformGroup As New TransformGroup
        TransformGroup.Children.Add(TransformScale)
        TransformGroup.Children.Add(TransformPos)
        Card.RenderTransform = TransformGroup
        Card.SetResourceReference(Border.BackgroundProperty, "ColorBrushCardBg")
        Dim Shadow As New System.Windows.Media.Effects.DropShadowEffect With {.BlurRadius = 18, .ShadowDepth = 3, .Opacity = 0.35, .RenderingBias = System.Windows.Media.Effects.RenderingBias.Performance}
        'Freezable 无 SetResourceReference：取当前主题色快照（卡片生命周期内足够）
        Dim ShadowColor = Application.Current?.TryFindResource("ColorObject1")
        If TypeOf ShadowColor Is Color Then Shadow.Color = CType(ShadowColor, Color)
        Card.Effect = Shadow
        Popup.Child = Card
        '锚点 = 目标左下角：卡片左边贴目标左边，下方放不下自动翻到上方（与 ApplyAnchor 同一套思路）
        Popup.CustomPopupPlacementCallback =
            Function(PopupSize As Size, TargetSize As Size, Offset As Point) As PopupPlacement()
                Dim X As Double = -ShadowPad
                Return {
                    New PopupPlacement(New Point(X, TargetSize.Height - ShadowPad), PopupAxis.Vertical),
                    New PopupPlacement(New Point(X, -PopupSize.Height + ShadowPad), PopupAxis.Vertical)
                }
            End Function
        AddHandler Popup.Opened,
        Sub()
            If Not PopupAnimEnabled Then
                '动画关闭：直接呈现终态
                TransformScale.ScaleX = 1
                TransformScale.ScaleY = 1
                TransformPos.Y = 0
                Card.Opacity = PopupBaseOpacity
                Return
            End If
            Dim BaseOpacity = PopupBaseOpacity
            AniStart({
                AaOpacity(Card, BaseOpacity - Card.Opacity, 130),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 1 - TransformScale.ScaleX, 220, , New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 1 - TransformScale.ScaleY, 220, , New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) TransformPos.Y += i, 0 - TransformPos.Y, 220, , New AniEaseOutFluent(AniEasePower.Weak))
            }, AnimName)
        End Sub
        AddHandler Popup.Closed, Sub() ClosedTick = GetTimeMs()
    End Sub

    ''' <summary>弹出菜单。同一目标再次调用为收起；关闭动画期间的重复点击由目标判断与时间守卫兜底。</summary>
    Public Shared Sub Show(Target As FrameworkElement, Rows As IEnumerable(Of MenuCardRow))
        SingleInstance.ShowCore(Target, Rows)
    End Sub

    ''' <summary>
    ''' 把弹出卡片锚到 Target 内部坐标 Anchor 处：卡片贴在锚点正下方，空间不够自动翻到上方/另一侧。
    ''' <para/>
    ''' AlignRight = True 时卡片右边贴锚点（列表行右侧齿轮、⋯ 按钮用），False 时卡片左边贴锚点（右键光标用）。
    ''' <para/>
    ''' ShadowPad = 卡片 Border 给投影留的 Margin（XAML 里是 14）：Popup 的实际尺寸包含这圈透明边，
    ''' 直接按 Popup 尺寸定位会让卡片本体整体偏 14px——这就是「点了菜单，位置却不对」的直接原因。
    ''' </summary>
    Public Shared Sub ApplyAnchor(TargetPopup As PopupControl, Target As FrameworkElement,
                                  Anchor As Point, AlignRight As Boolean, Optional ShadowPad As Double = 14)
        If TargetPopup Is Nothing OrElse Target Is Nothing Then Return
        '旧的固定偏移（HorizontalOffset 硬编码）会叠加到自定义定位上，先清零
        TargetPopup.HorizontalOffset = 0
        TargetPopup.VerticalOffset = 0
        TargetPopup.PlacementTarget = Target
        TargetPopup.Placement = PlacementModes.Custom
        TargetPopup.CustomPopupPlacementCallback =
            Function(PopupSize As Size, TargetSize As Size, Offset As Point) As PopupPlacement()
                '卡片左边贴锚点 / 卡片右边贴锚点
                Dim XNear As Double = Anchor.X - ShadowPad
                Dim XFar As Double = Anchor.X - PopupSize.Width + ShadowPad
                Dim YBelow As Double = Anchor.Y - ShadowPad
                Dim YAbove As Double = Anchor.Y - PopupSize.Height + ShadowPad
                Dim FirstX As Double = If(AlignRight, XFar, XNear)
                Dim SecondX As Double = If(AlignRight, XNear, XFar)
                '四个候选按优先级排：先「主对齐 + 下方」，再上下翻转，最后换另一侧水平对齐
                Dim Result As PopupPlacement() = {
                    New PopupPlacement(New Point(FirstX, YBelow), PopupAxis.Vertical),
                    New PopupPlacement(New Point(FirstX, YAbove), PopupAxis.Vertical),
                    New PopupPlacement(New Point(SecondX, YBelow), PopupAxis.Vertical),
                    New PopupPlacement(New Point(SecondX, YAbove), PopupAxis.Vertical)
                }
                Return Result
            End Function
    End Sub

    ''' <summary>关闭当前卡片（带收起动画）。</summary>
    Public Shared Sub Close()
        SingleInstance.CloseCore()
    End Sub

    ''' <summary>
    ''' 按目标控件在屏幕上的位置，算出菜单最大可用高度（DIP）：
    ''' 取「下方」与「上方」中更宽敞的一侧，留 16px 边距，且不超过所在显示器的工作区。
    ''' <para/>
    ''' 第 135 轮：菜单条目一多就会超出屏幕（用户截图点名）。原来写死 MaxHeight = 380 且滚动条
    ''' 是 Hidden——矮屏上照样溢出，而且看不出还能滚。现在高度按锚点算、滚动条走 TCL 样式。
    ''' </summary>
    Public Shared Function CalcMaxMenuHeight(Target As FrameworkElement) As Double
        Try
            'PointToScreen 给的是物理像素，WorkArea/布局用的是 DIP：先按当前 DPI 折算
            Dim Scale As Double = 1
            Try
                Dim DpiInfo = VisualTreeHelper.GetDpi(Target)
                If DpiInfo.DpiScaleY > 0 Then Scale = DpiInfo.DpiScaleY
            Catch ex As Exception
            End Try
            Dim TopLeft As Point = Target.PointToScreen(New Point(0, 0))
            Dim ScreenTop As Double = TopLeft.Y / Scale
            Dim ScreenBottom As Double = (TopLeft.Y + Target.ActualHeight * Scale) / Scale
            '工作区：默认主屏；能拿到 WinForms 屏幕信息就按目标所在显示器算（多屏）
            Dim Work As Rect = SystemParameters.WorkArea
            Try
                Dim S As System.Windows.Forms.Screen = System.Windows.Forms.Screen.FromPoint(
                    New System.Drawing.Point(CInt(TopLeft.X), CInt(TopLeft.Y)))
                If S IsNot Nothing Then
                    Work = New Rect(S.WorkingArea.X / Scale, S.WorkingArea.Y / Scale,
                                    S.WorkingArea.Width / Scale, S.WorkingArea.Height / Scale)
                End If
            Catch ex As Exception
            End Try
            Dim Below As Double = Work.Bottom - ScreenBottom - 16
            Dim Above As Double = ScreenTop - Work.Top - 16
            Dim Usable As Double = Math.Max(Below, Above)
            '坐标异常（跨屏负坐标等）时退化成「整块工作区可用」
            If Usable < 200 Then Usable = Work.Height - 32
            '扣掉卡片自身的开销：Padding 8×2 + 投影留白 14×2 ≈ 44，再留一点余量
            Return Math.Max(160, Math.Min(Usable - 48, Work.Height - 80))
        Catch ex As Exception
            Return Math.Max(240, SystemParameters.WorkArea.Height - 64)
        End Try
    End Function

    Private Sub ShowCore(Target As FrameworkElement, Rows As IEnumerable(Of MenuCardRow))
        Try
            If Popup.PlacementTarget Is Target AndAlso Popup.IsOpen Then
                CloseCore() : Return '同一目标：切换收起
            End If
            If Popup.IsOpen Then
                AniStop(AnimName)
                Popup.IsOpen = False '换目标：直接切换，不做收起动画
            ElseIf GetTimeMs() - ClosedTick < 120 Then
                Return '刚收起就换目标重开的一瞬，防抖
            End If
            Pan.Children.Clear()
            For Each Row In Rows
                Dim Item As New MyListItem With {
                    .Title = ModLang.L(Row.Title), .SvgIcon = Row.Icon, .Type = MyListItem.CheckType.Clickable,
                    .Height = 34, .MinPaddingRight = 10, .IsScaleAnimationEnabled = False}
                Dim Act = Row.Action
                AddHandler Item.Click, Sub(s, e)
                                           CloseCore()
                                           Act?.Invoke()
                                       End Sub
                Pan.Children.Add(Item)
            Next
            '复位动画初值（Popup 关着时改值不会闪）；真正的展开动画在 Opened 里跑
            TransformScale.ScaleX = 0.94
            TransformScale.ScaleY = 0.94
            TransformPos.Y = 10
            Card.Opacity = If(PopupAnimEnabled, 0, PopupBaseOpacity)
            '菜单最长不超过锚点这一侧的可用高度，超出部分用滚动条看（第 135 轮）
            Scroll.MaxHeight = CalcMaxMenuHeight(Target)
            Popup.PlacementTarget = Target
            Popup.IsOpen = True
        Catch ex As Exception
            Logger.Error(ex, "弹出菜单卡片失败")
        End Try
    End Sub

    Private Sub CloseCore()
        Try
            If Not Popup.IsOpen Then Return
            AniStop(AnimName)
            If Not PopupAnimEnabled Then
                Popup.IsOpen = False
                Return
            End If
            AniStart({
                AaOpacity(Card, -Card.Opacity, 80),
                AaDouble(Sub(i) TransformScale.ScaleX += i, 0.96 - TransformScale.ScaleX, 90),
                AaDouble(Sub(i) TransformScale.ScaleY += i, 0.96 - TransformScale.ScaleY, 90),
                AaCode(Sub() Popup.IsOpen = False, , True)
            }, AnimName)
        Catch ex As Exception
            Logger.Warn(ex, "关闭菜单卡片失败")
        End Try
    End Sub

End Class
