Public Class MyPageLeft
    Inherits Grid
    Private Uuid As Integer = GetUuid()

    '全局侧边栏滚动。与设置 / 下载页一致：导航 StackPanel 超出窗口高度时可竖向滚动，
    '内容不超高时不显示滚动条。只处理「唯一子元素为导航 StackPanel」的页面；
    '设置 / 下载 / 首页 / 实例选择等自带滚动或复杂布局的页面不受影响。
    Private SidebarScrollReady As Boolean = False

    Private Sub MyPageLeft_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        EnsureSidebarScrollable()
    End Sub

    Private Sub EnsureSidebarScrollable()
        If SidebarScrollReady Then Return
        SidebarScrollReady = True
        Try
            If Children.Count <> 1 Then Return
            Dim Target = TryCast(Children(0), StackPanel)
            If Target Is Nothing Then Return
            Dim Scroll As New MyScrollViewer With {
                .PanningMode = PanningMode.VerticalOnly,
                .HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                .VerticalScrollBarVisibility = ScrollBarVisibility.Auto
            }
            Try : Scroll.Style = CType(FindResource("ScrollViewerFullMargin"), Style) : Catch : End Try
            Children.Remove(Target)
            Scroll.Content = Target
            Children.Add(Scroll)
        Catch ex As Exception
            Logger.Warn(ex, "侧边栏滚动包装失败")
        End Try
    End Sub

    '执行逐个进入动画的控件
    Public Property AnimatedControl As String '需要在 Loaded 之后才能获取到控件，所以不能用 Binding 直接绑定
        Get
            Return GetValue(AnimatedControlProperty)
        End Get
        Set(value As String)
            SetValue(AnimatedControlProperty, value)
        End Set
    End Property
    Public Shared ReadOnly AnimatedControlProperty As DependencyProperty = DependencyProperty.Register("AnimatedControl", GetType(String), GetType(MyPageLeft), New PropertyMetadata(Nothing))

    Public Sub TriggerShowAnimation()
        If AnimatedControl Is Nothing Then
            '缩放动画
            If TypeOf RenderTransform IsNot ScaleTransform Then
                RenderTransform = New ScaleTransform(0.96, 0.96)
                RenderTransformOrigin = New Point(0.5, 0.5)
            End If
            Opacity = 0
            AniStart({
                AaScaleTransform(Me, 1 - CType(RenderTransform, ScaleTransform).ScaleX, 400,, New AniEaseOutBack(2)),
                AaOpacity(Me, 1, 100)
            }, "PageLeft PageChange " & Uuid)
        Else
            '逐个进入动画
            Dim AniList As New List(Of AniData)
            Dim Id As Integer = 0, Delay As Integer = 0
            Dim AllControls = GetAllAnimControls(True)
            '如果控件太多，减少动画复杂度，避免卡顿
            Dim UseSimpleAnimation As Boolean = AllControls.Count > 12
            For Each Element As FrameworkElement In AllControls
                Element = MyVirtualizingElement.TryInit(Element)
                If Element.Visibility = Visibility.Collapsed Then
                    '还原之前的隐藏动画可能导致的改变
                    Element.Opacity = 1
                    Element.RenderTransform = New TranslateTransform(0, 0)
                    If TypeOf Element Is MyListItem Then CType(Element, MyListItem).IsMouseOverAnimationEnabled = True
                Else
                    Element.Opacity = 0
                    Element.RenderTransform = New TranslateTransform(-15, 0)
                    If TypeOf Element Is MyListItem Then CType(Element, MyListItem).IsMouseOverAnimationEnabled = False
                    If UseSimpleAnimation Then
                        '简单模式：只有 Opacity 和一次平移，动画更短
                        AniList.Add(AaOpacity(Element, If(TypeOf Element Is TextBlock, 0.6, 1), 80, Delay, New AniEaseOutFluent(AniEasePower.Weak)))
                        AniList.Add(AaTranslateX(Element, 15, 150, Delay, New AniEaseOutFluent))
                    Else
                        '完整模式：原来的动画
                        AniList.Add(AaOpacity(Element, If(TypeOf Element Is TextBlock, 0.6, 1), 100, Delay, New AniEaseOutFluent(AniEasePower.Weak)))
                        AniList.Add(AaTranslateX(Element, 5, 200, Delay, New AniEaseOutFluent))
                        AniList.Add(AaTranslateX(Element, 20, 300, Delay, New AniEaseOutBack(AniEasePower.Weak)))
                    End If
                    If TypeOf Element Is MyListItem Then
                        AniList.Add(AaCode(
                        Sub()
                            CType(Element, MyListItem).IsMouseOverAnimationEnabled = True
                            CType(Element, MyListItem).RefreshColor(Me, New EventArgs)
                        End Sub, Delay + If(UseSimpleAnimation, 150, 280)))
                    End If
                    '延迟上限：最多 150ms，避免控件太多时动画太长
                    Delay += Math.Min(10, Math.Max(8, 15 - Id))
                    If Delay > 150 Then Delay = 150
                    Id += 1
                End If
            Next
            AniStart(AniList, "PageLeft PageChange " & Uuid)
        End If
    End Sub
    Public Sub TriggerHideAnimation()
        If AnimatedControl Is Nothing Then
            '缩放动画
            If TypeOf RenderTransform IsNot ScaleTransform Then
                RenderTransform = New ScaleTransform(1, 1)
                RenderTransformOrigin = New Point(0.5, 0.5)
            End If
            AniStart({
                AaScaleTransform(Me, 0.95 - CType(RenderTransform, ScaleTransform).ScaleX, 110,, New AniEaseInFluent(AniEasePower.Weak)),
                AaOpacity(Me, -Opacity, 80, 30)
            }, "PageLeft PageChange " & Uuid)
        Else
            '逐个退出动画
            Dim AniList As New List(Of AniData)
            Dim Id As Integer = 0
            Dim Controls = GetAllAnimControls()
            '如果控件太多，减少动画时长，避免卡顿
            Dim ExitDuration As Integer = If(Controls.Count > 12, 30, 50)
            Dim TotalDelay As Integer = If(Controls.Count > 12, 50, 70)
            For Each Element As FrameworkElement In Controls
                Dim Delay As Integer = If(Controls.Count > 0, TotalDelay / Controls.Count * Id, 0)
                AniList.Add(AaOpacity(Element, -Element.Opacity, ExitDuration, Delay))
                AniList.Add(AaTranslateX(Element, -6, ExitDuration, Delay))
                Id += 1
            Next
            AniStart(AniList, "PageLeft PageChange " & Uuid)
        End If
    End Sub

    '遍历获取所有需要生成动画的控件
    Private Function GetAllAnimControls(Optional IgnoreInvisibility As Boolean = False) As List(Of FrameworkElement)
        Dim AllControls As New List(Of FrameworkElement)
        GetAllAnimControls(FindName(AnimatedControl), AllControls, IgnoreInvisibility)
        Return AllControls
    End Function
    Private Sub GetAllAnimControls(Element As FrameworkElement, ByRef AllControls As List(Of FrameworkElement), IgnoreInvisibility As Boolean)
        If Not IgnoreInvisibility AndAlso Element.Visibility = Visibility.Collapsed Then Return
        If TypeOf Element Is MyTextButton Then
            AllControls.Add(Element)
        ElseIf TypeOf Element Is MyListItem Then
            AllControls.Add(Element)
        ElseIf TypeOf Element Is ContentControl Then
            GetAllAnimControls(CType(Element, ContentControl).Content, AllControls, IgnoreInvisibility)
        ElseIf TypeOf Element Is Panel Then
            For Each Element2 As FrameworkElement In CType(Element, Panel).Children
                GetAllAnimControls(Element2, AllControls, IgnoreInvisibility)
            Next
        Else
            AllControls.Add(Element)
        End If
    End Sub

End Class
