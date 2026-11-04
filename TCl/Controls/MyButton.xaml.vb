Imports System.Windows.Markup

<ContentProperty("Inlines")>
Public Class MyButton

    '声明
    Public Event Click(sender As Object, e As MouseButtonEventArgs) '自定义事件

    '自定义属性
    Public Uuid As Integer = GetUuid()
    Public ReadOnly Property Inlines As InlineCollection
        Get
            Return LabText.Inlines
        End Get
    End Property
    Public Property Text As String
        Get
            Return GetValue(TextProperty)
        End Get
        Set(value As String)
            SetValue(TextProperty, value)
        End Set
    End Property '显示文本
    Public Shared ReadOnly TextProperty As DependencyProperty = DependencyProperty.Register("Text", GetType(String), GetType(MyButton), New PropertyMetadata(New PropertyChangedCallback(
    Sub(sender As DependencyObject, e As DependencyPropertyChangedEventArgs)
        If sender IsNot Nothing Then CType(sender, MyButton).LabText.Text = e.NewValue
    End Sub)))
    Public Property TextPadding As Thickness
        Get
            Return LabText.Padding
        End Get
        Set(value As Thickness)
            LabText.Padding = value
        End Set
    End Property
    Private _ColorType As ColorState = ColorState.Normal  '配色方案
    Public Property ColorType As ColorState
        Get
            Return _ColorType
        End Get
        Set(value As ColorState)
            _ColorType = value
            'Theme 是实心底色 + 白字：边框色在这里只用来驱动文字颜色，必须把可见边框收掉
            If PanFore IsNot Nothing Then PanFore.BorderThickness = If(value = ColorState.Theme, New Thickness(0), New Thickness(1))
            RefreshColor()
        End Set
    End Property
    Public Enum ColorState
        Normal = 0
        Highlight = 1
        Red = 2
        Dark = 3 '深色半透明填充 + 白字（SJMCL blackAlpha 式），用于悬浮在壁纸上的主按钮，任何背景下可读
        Theme = 4 '主题色实心填充 + 白字：同样是悬浮在壁纸上的主按钮，但跟着当前主题走
    End Enum
    '属性穿透
    Public Shared Shadows ReadOnly PaddingProperty As DependencyProperty = DependencyProperty.Register("Padding", GetType(Thickness), GetType(MyButton), New PropertyMetadata(New PropertyChangedCallback(
        Sub(sender As MyButton, e As DependencyPropertyChangedEventArgs) If sender IsNot Nothing Then sender.PanFore.Padding = e.NewValue)))
    Public Overloads Property Padding As Thickness
        Get
            Return PanFore.Padding
        End Get
        Set(value As Thickness)
            PanFore.Padding = value
        End Set
    End Property
    Public Property RealRenderTransform As Transform
        Get
            Return PanFore.RenderTransform
        End Get
        Set(value As Transform)
            PanFore.RenderTransform = value
        End Set
    End Property

    '自定义事件
    Private Const AnimationColorIn As Integer = 100
    Private Const AnimationColorOut As Integer = 200
    Private Sub RefreshColor(Optional obj = Nothing, Optional e = Nothing) Handles Me.MouseEnter, Me.MouseLeave, Me.Loaded, Me.IsEnabledChanged
        Try
            If IsLoaded AndAlso AniControlEnabled = 0 Then '防止默认属性变更触发动画

                If IsEnabled Then
                    Select Case ColorType
                        Case ColorState.Normal
                            If IsMouseOver Then
                                '指向（Main 3）
                                AniStart({AaColor(PanFore, Border.BorderBrushProperty, "ColorBrush3", AnimationColorIn)}, "MyButton Color " & Uuid)
                            Else
                                '普通（Main 1）
                                AniStart({AaColor(PanFore, Border.BorderBrushProperty, "ColorBrush1", AnimationColorOut)}, "MyButton Color " & Uuid)
                            End If
                        Case ColorState.Highlight
                            If IsMouseOver Then
                                '指向（Main 3）
                                AniStart({AaColor(PanFore, Border.BorderBrushProperty, "ColorBrush3", AnimationColorIn)}, "MyButton Color " & Uuid)
                            Else
                                '高亮（Main 2）
                                AniStart({AaColor(PanFore, Border.BorderBrushProperty, "ColorBrush2", AnimationColorOut)}, "MyButton Color " & Uuid)
                            End If
                        Case ColorState.Dark
                            If IsMouseOver Then
                                '指向：微亮（注意 AaColor 的 MyColor 重载是增量，必须传差值）
                                AniStart({
                                    AaColor(PanFore, Border.BorderBrushProperty, "ColorBrushWhite", AnimationColorIn),
                                    AaColor(PanFore, BackgroundProperty, New MyColor(226, 52, 52, 58) - PanFore.Background, AnimationColorIn)
                                }, "MyButton Color " & Uuid)
                            Else
                                '深色半透明
                                AniStart({
                                    AaColor(PanFore, Border.BorderBrushProperty, "ColorBrushWhite", AnimationColorOut),
                                    AaColor(PanFore, BackgroundProperty, New MyColor(214, 25, 25, 30) - PanFore.Background, AnimationColorOut)
                                }, "MyButton Color " & Uuid)
                            End If
                        Case ColorState.Red
                            If IsMouseOver Then
                                '红色指向
                                AniStart({AaColor(PanFore, Border.BorderBrushProperty, "ColorBrushRedLight", AnimationColorIn)}, "MyButton Color " & Uuid)
                            Else
                                '红色
                                AniStart({AaColor(PanFore, Border.BorderBrushProperty, "ColorBrushRedDark", AnimationColorOut)}, "MyButton Color " & Uuid)
                            End If
                        Case ColorState.Theme
                            '主题色实心：指向时提亮一档，常态用 ColorBrush2（这一档白字对比度才够）
                            AniStart({
                                AaColor(PanFore, BackgroundProperty, If(IsMouseOver, "ColorBrush3", "ColorBrush2"), AnimationColorIn),
                                AaColor(PanFore, Border.BorderBrushProperty, "ColorBrushWhite", AnimationColorIn)
                            }, "MyButton Color " & Uuid)
                    End Select
                Else
                    If _ColorType = ColorState.Dark Then
                        '深色按钮（悬浮在壁纸上的主按钮）：禁用时底色仍保持深色，文字用浅色调，
                        '否则「正在加载」会变成半透明白底 + 灰字，在任何壁纸上都看不清
                        AniStart({
                            AaColor(PanFore, BackgroundProperty, New MyColor(214, 25, 25, 30) - PanFore.Background, AnimationColorOut),
                            AaColor(PanFore, Border.BorderBrushProperty, "ColorBrush6", AnimationColorOut)
                        }, "MyButton Color " & Uuid)
                    ElseIf _ColorType = ColorState.Theme Then
                        '主题色按钮禁用：底色保持主题色（按钮上还叠着版本号文字，淡底会让它看不见），
                        '只把文字调成浅色调，读得出"正在加载"又不至于和可用态混淆
                        AniStart({
                            AaColor(PanFore, BackgroundProperty, "ColorBrush2", AnimationColorOut),
                            AaColor(PanFore, Border.BorderBrushProperty, "ColorBrush5", AnimationColorOut)
                        }, "MyButton Color " & Uuid)
                    Else
                        '不可用（Gray 4）
                        AniStart({AaColor(PanFore, Border.BorderBrushProperty, ColorGray4 - PanFore.BorderBrush, AnimationColorOut)}, "MyButton Color " & Uuid)
                    End If
                End If
            Else

                AniStop("MyButton Color " & Uuid)
                If IsEnabled Then
                    Select Case ColorType
                        Case ColorState.Normal
                            If IsMouseOver Then
                                PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrush3")
                            Else
                                PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrush1")
                            End If
                        Case ColorState.Highlight
                            If IsMouseOver Then
                                PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrush3")
                            Else
                                PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrush2")
                            End If
                        Case ColorState.Dark
                            If IsMouseOver Then
                                PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrushWhite")
                                PanFore.Background = New MyColor(226, 52, 52, 58)
                            Else
                                PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrushWhite")
                                PanFore.Background = New MyColor(214, 25, 25, 30)
                            End If
                        Case ColorState.Theme
                            PanFore.SetResourceReference(BackgroundProperty, If(IsMouseOver, "ColorBrush3", "ColorBrush2"))
                            PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrushWhite")
                    End Select
                Else
                    If _ColorType = ColorState.Dark Then
                        PanFore.Background = New MyColor(214, 25, 25, 30)
                        PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrush6")
                    ElseIf _ColorType = ColorState.Theme Then
                        PanFore.SetResourceReference(BackgroundProperty, "ColorBrush2")
                        PanFore.SetResourceReference(Border.BorderBrushProperty, "ColorBrush5")
                    Else
                        PanFore.BorderBrush = ColorGray4
                    End If
                End If

            End If
        Catch ex As Exception
            Logger.Warn(ex, "刷新按钮颜色出错")
        End Try
    End Sub

    '鼠标点击判定（务必放在点击事件之后，以使得 Button_MouseUp 先于 Button_MouseLeave 执行）
    Private IsMouseDown As Boolean = False
    Private Sub Button_ClickRaise(sender As Object, e As MouseButtonEventArgs) Handles Me.MouseLeftButtonUp
        If Not IsMouseDown Then Return
        Logger.Info($"按下按钮：{Text}")
        RaiseEvent Click(sender, e)
        RaiseCustomEvent() '自定义事件
    End Sub
    Private Const BtnScalePressed As Double = 0.96
    Private Sub Button_MouseDown(sender As Object, e As MouseButtonEventArgs) Handles Me.MouseLeftButtonDown
        IsMouseDown = True
        Focus()
        '按压：轻微下压到 0.96，干脆而克制
        AniStart(AaScaleTransform(PanFore, BtnScalePressed - CType(PanFore.RenderTransform, ScaleTransform).ScaleX, 60,, New AniEaseOutFluent(AniEasePower.Strong)), "MyButton Scale " & Uuid)
    End Sub
    Private Sub Button_MouseEnter() Handles Me.MouseEnter
        '悬浮只做柔和的背景色过渡，不再缩放，保持稳重
        'Theme 的底色过渡统一由 RefreshColor 负责（它也挂在 MouseEnter 上），这里必须让开，否则同一属性两套动画打架
        If _ColorType = ColorState.Theme Then Return
        If _ColorType = ColorState.Dark Then
            '深色按钮不能淡到浅色底，否则白字瞬间失去对比度
            AniStart(AaColor(PanFore, BackgroundProperty, New MyColor(226, 52, 52, 58) - PanFore.Background, AnimationColorIn, , New AniEaseOutFluent(AniEasePower.Middle)), "MyButton Background " & Uuid)
        Else
            AniStart(AaColor(PanFore, BackgroundProperty, If(_ColorType = ColorState.Red, "ColorBrushRedBack", "ColorBrush7"), AnimationColorIn, , New AniEaseOutFluent(AniEasePower.Middle)), "MyButton Background " & Uuid)
        End If
    End Sub
    Private Sub Button_MouseUp() Handles Me.MouseLeftButtonUp
        If Not IsMouseDown Then Return
        IsMouseDown = False
        '松开：平滑回到 1.0
        AniStart(AaScaleTransform(PanFore, 1 - CType(PanFore.RenderTransform, ScaleTransform).ScaleX, 200, , New AniEaseOutFluent(AniEasePower.Middle)), "MyButton Scale " & Uuid)
    End Sub
    Private Sub Button_MouseLeave() Handles Me.MouseLeave
        'Theme 的底色过渡由 RefreshColor 负责，这里让开（见 Button_MouseEnter）
        If _ColorType = ColorState.Theme Then
            IsMouseDown = False
            Return
        End If
        If _ColorType = ColorState.Dark Then
            AniStart(AaColor(PanFore, BackgroundProperty, New MyColor(214, 25, 25, 30) - PanFore.Background, AnimationColorOut, , New AniEaseOutFluent(AniEasePower.Middle)), "MyButton Background " & Uuid)
        Else
            AniStart(AaColor(PanFore, BackgroundProperty, "ColorBrushHalfWhite", AnimationColorOut, , New AniEaseOutFluent(AniEasePower.Middle)), "MyButton Background " & Uuid)
        End If
        IsMouseDown = False
        '离开：平滑回落 1.0
        AniStart(AaScaleTransform(PanFore, 1 - CType(PanFore.RenderTransform, ScaleTransform).ScaleX, 220, , New AniEaseOutFluent(AniEasePower.Middle)), "MyButton Scale " & Uuid)
    End Sub

End Class
