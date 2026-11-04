Public Class MyComboBox
    Inherits ComboBox
    Implements ISettingControl
    Public Event TextChanged(sender As Object, e As TextChangedEventArgs)

    '基础
    Public Uuid As Integer = GetUuid()
    Private TextBox As MyTextBox
    Private HookedPopup As System.Windows.Controls.Primitives.Popup = Nothing
    Private LastPopupAnimTick As Long = 0
    Public Property HintText As String = ""
    Public Overrides Sub OnApplyTemplate()
        MyBase.OnApplyTemplate()
        '两个模板（普通 / 可编辑）的下拉面板都要挂上卡片弹出动画
        HookDropDownPopup()
        If Not IsEditable Then Return
        Try
            TextBox = Template.FindName("PART_EditableTextBox", Me)
            TextBox.AddHandler(LostFocusEvent, New RoutedEventHandler(AddressOf RefreshColor))
            TextBox.ChangedEventList.Add(New RoutedEventHandler(Sub(sender, e) RaiseEvent TextChanged(sender, e)))
            TextBox.Tag = Tag '有时需要用文本框的 Tag 来写入设置
            If Text = "" Then
                TextBox.Text = _Text
            Else
                RaiseEvent TextChanged(Me, Nothing)
            End If
            If HintText.Length > 0 Then TextBox.HintText = HintText
        Catch ex As Exception
            Logger.Error(ex, $"初始化可编辑文本框失败（{If(Name, "")}）")
        End Try
    End Sub
    Private _Text As String = SelectedItem
    Public Shadows Property Text As String
        Get
            If IsEditable Then
                If TextBox Is Nothing Then
                    Return If(_Text, "")
                Else
                    Return If(TextBox.Text, "")
                End If
            Else
                Return If(SelectedItem, "").ToString
            End If
        End Get
        Set(value As String)
            If IsEditable Then
                If IsNothing(TextBox) Then
                    _Text = value
                Else
                    TextBox.Text = value
                End If
            Else
                Throw New NotSupportedException("该 ComboBox 不支持修改文本。")
            End If
        End Set
    End Property

    '鼠标按下接口
    Private IsMouseDown As Boolean = False
    Private Sub MyComboBox_PreviewMouseLeftButtonDown(sender As Object, e As MouseButtonEventArgs) Handles Me.PreviewMouseLeftButtonDown
        IsMouseDown = True
    End Sub
    Private Sub MyComboBox_PreviewMouseLeftButtonUp(sender As Object, e As EventArgs) Handles Me.PreviewMouseLeftButtonUp, Me.MouseLeave
        IsMouseDown = False
    End Sub

    '指向动画
    Public Sub RefreshColor() Handles Me.IsEnabledChanged, Me.MouseEnter, Me.MouseLeave, Me.PreviewMouseLeftButtonDown, Me.PreviewMouseLeftButtonUp, Me.GotKeyboardFocus
        '判断当前颜色
        Dim ForeColorName As String
        Dim BackColorName As String
        Dim Time As Integer
        If IsEnabled Then
            If IsMouseDown OrElse IsDropDownOpen OrElse (IsEditable AndAlso Template.FindName("PART_EditableTextBox", Me).IsFocused) Then
                ForeColorName = "ColorBrush3"
                BackColorName = "ColorBrush7"
                Time = 10
            ElseIf IsMouseOver Then
                ForeColorName = "ColorBrush4"
                BackColorName = "ColorBrush7"
                Time = 100
            Else
                ForeColorName = "ColorBrushBg0"
                BackColorName = "ColorBrushHalfWhite"
                Time = 100
            End If
        Else
            ForeColorName = "ColorBrushGray5"
            BackColorName = "ColorBrushGray6"
            Time = 200
        End If
        '触发颜色动画
        If IsLoaded AndAlso AniControlEnabled = 0 Then '防止默认属性变更触发动画
            '有动画
            AniStart({
                     AaColor(Me, ForegroundProperty, ForeColorName, Time),
                     AaColor(Me, BackgroundProperty, BackColorName, Time)
                 }, "MyComboBox Color " & Uuid)
        Else
            '无动画
            AniStop("MyComboBox Color " & Uuid)
            SetResourceReference(ForegroundProperty, ForeColorName)
            SetResourceReference(BackgroundProperty, BackColorName)
        End If
    End Sub

    ''' <summary>把弹出动画挂到 Popup.Opened：Popup 首次展开时内容才 Loaded，过早播放会被首帧覆盖。</summary>
    Private Sub HookDropDownPopup()
        Try
            Dim Pop = TryCast(Template.FindName("PART_Popup", Me), System.Windows.Controls.Primitives.Popup)
            If Pop Is Nothing OrElse Pop Is HookedPopup Then Return
            HookedPopup = Pop
            AddHandler Pop.Opened, Sub() PlayDropDownAnimation()
        Catch ex As Exception
            Logger.Warn(ex, "绑定下拉框弹出动画失败")
        End Try
    End Sub

    ''' <summary>所有下拉框共用的卡片动画：淡入 130ms + 缩放 0.94→1（回弹）+ 上浮 10→0，
    ''' 参数与菜单卡片一致，并遵循「个性化 → 弹出菜单动画」开关。</summary>
    Private Sub PlayDropDownAnimation()
        '同一次展开内 Popup.Opened 与 DropDownOpened 都会触发，只播放一次
        Dim Tick As Long = GetTimeMs()
        If Tick - LastPopupAnimTick < 60 Then Return
        LastPopupAnimTick = Tick
        Try
            Dim Pan = TryCast(Template.FindName("PanCard", Me), Grid)
            If Pan Is Nothing Then Return
            Dim Group = TryCast(Pan.RenderTransform, TransformGroup)
            Dim Scale As ScaleTransform = Nothing, Pos As TranslateTransform = Nothing
            If Group IsNot Nothing AndAlso Group.Children.Count >= 2 Then
                Scale = TryCast(Group.Children(0), ScaleTransform)
                Pos = TryCast(Group.Children(1), TranslateTransform)
            End If
            If Scale Is Nothing OrElse Pos Is Nothing Then Return
            AniStop("MyComboBox Popup " & Uuid)
            Dim BaseOpacity As Double = MenuCard.PopupBaseOpacity
            If AniControlEnabled > 0 OrElse Not MenuCard.PopupAnimEnabled Then
                '关闭动画时直接呈现终态
                Scale.ScaleX = 1 : Scale.ScaleY = 1
                Pos.Y = 0
                Pan.Opacity = BaseOpacity
                Return
            End If
            Pan.Opacity = 0
            Scale.ScaleX = 0.94 : Scale.ScaleY = 0.94
            Pos.Y = 10
            AniStart({
                AaOpacity(Pan, BaseOpacity - Pan.Opacity, 130),
                AaDouble(Sub(i) Scale.ScaleX += i, 1 - Scale.ScaleX, 220, , New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) Scale.ScaleY += i, 1 - Scale.ScaleY, 220, , New AniEaseOutBack(AniEasePower.Middle)),
                AaDouble(Sub(i) Pos.Y += i, 0 - Pos.Y, 220, , New AniEaseOutFluent(AniEasePower.Weak))
            }, "MyComboBox Popup " & Uuid)
        Catch ex As Exception
            '模板默认就是可见终态，动画异常时下拉列表仍可正常使用
            Logger.Warn(ex, "下拉框弹出动画失败")
        End Try
    End Sub

    Private RealWidth As Double '由于下拉框 Popup 宽度与 Width 一致，故不能为 NaN（Auto）
    Private Sub MyComboBox_DropDownOpened(sender As Object, e As EventArgs) Handles Me.DropDownOpened
        RealWidth = Width
        Width = ActualWidth
        '将下拉菜单透明度与主窗口同步
        Try
            CType(Template.FindName("PanPopup", Me), Grid).Opacity = FrmMain.Opacity
        Catch ex As Exception
            Logger.Error(ex, "设置下拉框透明度失败")
        End Try
        '兜底：若模板替换导致 Popup.Opened 未挂上，仍由这里播放动画
        PlayDropDownAnimation()
    End Sub
    Private Sub MyComboBox_DropDownClosed(sender As Object, e As EventArgs) Handles Me.DropDownClosed
        Width = RealWidth
    End Sub

    '修复 WPF Bug：下拉框文本修改后，依然误认为还选择着此前的选项，导致再次点击该选项时内容不变
    Private IsTextChanging As Boolean = False
    Private Sub MyComboBox_TextChanged(sender As Object, e As TextChangedEventArgs) Handles Me.TextChanged
        If IsTextChanging OrElse Not IsEditable Then Return
        If SelectedItem Is Nothing OrElse Text = SelectedItem.ToString Then Return
        Dim RawText As String = Text
        Dim RawSelectionStart As Integer = TextBox.SelectionStart
        IsTextChanging = True
        SelectedItem = Nothing
        Text = RawText
        TextBox.SelectionStart = RawSelectionStart
        IsTextChanging = False
    End Sub

    Public ReadOnly Property ContentPresenter As ContentPresenter
        Get
            Return Template.FindName("PART_Content", Me)
        End Get
    End Property

    Private Sub MyComboBox_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles Me.SelectionChanged
        If IsLoaded AndAlso AniControlEnabled = 0 Then RaiseCustomEvent()
    End Sub

#Region "设置"

    Private Sub RefreshSetting(NewValue As String) Implements ISettingControl.RefreshSetting
        Dim TargetItem = Items.OfType(Of DependencyObject)().FirstOrDefault(Function(i) Val(If(SettingService.GetValue(i), -1)) = NewValue)
        If TargetItem IsNot Nothing Then
            SelectedItem = TargetItem
        ElseIf Items.Count > NewValue Then
            SelectedIndex = NewValue
        Else
            Logger.Warn($"尝试显示设置 {SettingService.GetKey(Me)}，但没有找到设置值 {NewValue} 的对应项")
            SelectedIndex = -1
            SelectedItem = Nothing
        End If
    End Sub

    Private Function GetCurrentSetting() As String Implements ISettingControl.GetCurrentSetting
        If SelectedItem Is Nothing Then Return Nothing
        If TypeOf SelectedItem Is DependencyObject Then
            Return If(SettingService.GetValue(SelectedItem), SelectedIndex)
        Else
            Return SelectedIndex
        End If
    End Function

    Private Sub SaveSetting() Handles Me.SelectionChanged
        If SelectedItem Is Nothing Then Return
        SettingService.SaveSetting(Me)
    End Sub

#End Region

End Class
