Imports System.Collections.ObjectModel
Imports System.Windows.Interop
Imports System.Windows.Threading

Public Module ModMain

#Region "弹出提示"

    ''' <summary>
    ''' 提示信息的种类。
    ''' 该枚举在自定义事件中使用，是公开 API 的一部分。
    ''' </summary>
    Public Enum HintType
        Blue
        Green
        Red
    End Enum
    Private Structure HintMessage
        Public Text As String
        Public Type As HintType
        Public Log As Boolean
    End Structure

    ''' <summary>
    ''' 等待弹出的提示列表。以 {String, HintType, Log As Boolean} 形式存储为数组。
    ''' </summary>
    Private HintWaiting As New ConcurrentQueue(Of HintMessage)
    ''' <summary>
    ''' 在窗口左下角弹出提示文本。
    ''' </summary>
    Public Sub Hint(Text As String, Optional Type As HintType = HintType.Blue, Optional Log As Boolean = True)
        HintWaiting.Enqueue(New HintMessage With {.Text = If(Text, ""), .Type = Type, .Log = Log})
    End Sub

    Private Sub HintTick()
        Try
            Application.EnsureDelayedUiLoaded() '提示控件依赖延迟字典里的样式（第 112 轮）
            'Tag 存储了：{ 是否可以重用, Uuid }
            Dim CurrentHint As HintMessage = Nothing
            Do While HintWaiting.TryDequeue(CurrentHint)
                ''清除空提示
                'If IsNothing(HintWaiting(0)) OrElse IsNothing(HintWaiting(0)(0)) Then
                '    HintWaiting.RemoveAt(0)
                '    Continue Do
                'End If
                '去回车
                CurrentHint.Text = CurrentHint.Text.ReplaceLineEndings(" ", mergeMultiple:=True)
                '超量提示直接忽略
                If FrmMain.PanHint.Children.Count >= 20 Then GoTo EndHint
                '检查是否有重复提示
                Dim DoubleStack As Border = Nothing
                For Each stack As Border In FrmMain.PanHint.Children
                    If stack.Tag(0) AndAlso CType(stack.Child, TextBlock).Text = CurrentHint.Text Then DoubleStack = stack
                Next
                '获取渐变颜色
                Dim TargetColor0, TargetColor1 As MyColor
                Dim Percent As Double = 0.3
                Select Case CurrentHint.Type
                    Case HintType.Blue
                        TargetColor0 = New MyColor(215, 37, 155, 252)
                        TargetColor1 = New MyColor(215, 10, 142, 252)
                    Case HintType.Green
                        TargetColor0 = New MyColor(215, 33, 177, 33)
                        TargetColor1 = New MyColor(215, 29, 160, 29)
                    Case Else 'HintType.Red
                        TargetColor0 = New MyColor(215, 255, 53, 11)
                        TargetColor1 = New MyColor(215, 255, 43, 0)
                End Select
                If Not IsNothing(DoubleStack) Then
                    '有重复提示，且该提示的进入动画已播放
                    If Not AniIsRun("Hint Show " & DoubleStack.Tag(1)) Then
                        AniStop("Hint Hide " & DoubleStack.Tag(1))
                        Dim Delay As Double = (800 + CurrentHint.Text.Length.Clamp(5, 23) * 180) * AniSpeed
                        AniStart({
                            AaX(DoubleStack, -12 - DoubleStack.Margin.Left, 50,, New AniEaseOutFluent),
                            AaX(DoubleStack, -8, 50, 50, New AniEaseInFluent),
                            AaX(DoubleStack, 8, 50, 100, New AniEaseOutFluent),
                            AaX(DoubleStack, -8, 50, 150, New AniEaseInFluent),
                            AaDouble(Sub(i)
                                         Percent += i
                                         Dim Gradient As LinearGradientBrush = DoubleStack.Background
                                         Gradient.GradientStops(0).Color = TargetColor0 * Percent + New MyColor(255, 255, 255) * (1 - Percent)
                                         Gradient.GradientStops(1).Color = TargetColor1 * Percent + New MyColor(255, 255, 255) * (1 - Percent)
                                     End Sub, 0.7, 250),
                            AaX(DoubleStack, -50, 200, Delay, New AniEaseInFluent),
                            AaOpacity(DoubleStack, -1, 150, Delay),
                            AaCode(Sub() DoubleStack.Tag(0) = False, Delay),
                            AaHeight(DoubleStack, -26, 100,, New AniEaseOutFluent, True),
                            AaCode(Sub() FrmMain.PanHint.Children.Remove(DoubleStack), , True)
                      }, "Hint Hide " & DoubleStack.Tag(1))
                    End If
                Else
                    '准备控件
                    Dim NewHintControl As New Border With {.Tag = {True, GetUuid()}, .Margin = New Thickness(-70, 0, 20, 0), .Opacity = 0, .Height = 0, .HorizontalAlignment = HorizontalAlignment.Left, .CornerRadius = New CornerRadius(0, 6, 6, 0)}
                    NewHintControl.Background = New LinearGradientBrush(New GradientStopCollection(New List(Of GradientStop) From {
                        New GradientStop(TargetColor0 * Percent + New MyColor(255, 255, 255) * (1 - Percent), 0),
                        New GradientStop(TargetColor1 * Percent + New MyColor(255, 255, 255) * (1 - Percent), 1)}), 90)
                    NewHintControl.Child = New TextBlock With {.TextTrimming = TextTrimming.CharacterEllipsis, .FontSize = 13, .Text = CurrentHint.Text, .Foreground = New MyColor(255, 255, 255), .Margin = New Thickness(33, 5, 8, 5)}
                    'AddHandler NewHintControl.MouseLeftButtonDown, AddressOf HideAllHint
                    FrmMain.PanHint.Children.Add(NewHintControl)
                    '控件动画
                    Dim Animations As New List(Of AniData)
                    If FrmMain.PanHint.Children.Count > 1 Then
                        '已有提示
                        Animations.Add(AaHeight(NewHintControl, 26, 150, , New AniEaseOutFluent))
                    Else
                        '是唯一提示
                        NewHintControl.Height = 26
                    End If
                    '开始动画
                    Animations.AddRange({
                        AaX(NewHintControl, 30, 400, , New AniEaseOutElastic(AniEasePower.Weak)),
                        AaX(NewHintControl, 20, 200, , New AniEaseOutFluent),
                        AaOpacity(NewHintControl, 1, 100),
                        AaDouble(Sub(i)
                                     Percent += i
                                     Dim Gradient As LinearGradientBrush = NewHintControl.Background
                                     Gradient.GradientStops(0).Color = TargetColor0 * Percent + New MyColor(255, 255, 255) * (1 - Percent)
                                     Gradient.GradientStops(1).Color = TargetColor1 * Percent + New MyColor(255, 255, 255) * (1 - Percent)
                                 End Sub, 0.7, 250, 100)
                    })
                    AniStart(Animations, "Hint Show " & NewHintControl.Tag(1))
                    '结束动画
                    Dim Delay As Double = (800 + CurrentHint.Text.Length.Clamp(5, 23) * 180) * AniSpeed
                    AniStart({
                        AaX(NewHintControl, -50, 200, Delay, New AniEaseInFluent),
                        AaOpacity(NewHintControl, -1, 150, Delay),
                        AaCode(Sub() NewHintControl.Tag(0) = False, Delay),
                        AaHeight(NewHintControl, -26, 100,, New AniEaseOutFluent, True),
                        AaCode(Sub() FrmMain.PanHint.Children.Remove(NewHintControl), , True)
                    }, "Hint Hide " & NewHintControl.Tag(1))
                End If
                '结束处理
EndHint:
                If CurrentHint.Log Then Logger.Info($"弹出提示：{CurrentHint.Text}")
            Loop
        Catch ex As Exception
            Logger.Info(ex, "显示弹出提示失败")
        End Try
    End Sub
    Private Sub HideAllHint()
        For Each Control As Border In FrmMain.PanHint.Children
            Control.IsHitTestVisible = False
            AniStart({
                AaX(Control, -50, 200, , New AniEaseInFluent),
                AaOpacity(Control, -1, 150, , New AniEaseInFluent),
                AaCode(Sub() Control.Tag(0) = False),
                AaHeight(Control, -26, 100,, New AniEaseOutFluent, True),
                AaCode(Sub() FrmMain.PanHint.Children.Remove(Control), , True)
            }, "Hint Hide " & Control.Tag(1))
        Next
    End Sub

#End Region

#Region "弹窗"

    ''' <summary>
    ''' 存储弹窗信息的转换器。
    ''' </summary>
    Public Class MyMsgBoxConverter
        Public Type As MyMsgBoxType
        Public Title As String
        Public Text As String
        ''' <summary>
        ''' 输入模式：文本框的文本。
        ''' 选择模式：需要放进去的 IEnumberable(Of IMyRadio)。
        ''' 登录模式：登录步骤 1 中返回的 JSON。
        ''' </summary>
        Public Content As Object
        ''' <summary>
        ''' 输入模式：输入验证规则。
        ''' </summary>
        Public ValidateRules As Collection(Of Validate)
        ''' <summary>
        ''' 输入模式：提示文本。
        ''' </summary>
        Public HintText As String = ""
        ''' <summary>
        ''' 有多个按钮时，是否给第一个按钮加高亮。
        ''' </summary>
        Public HighLight As Boolean
        Public Button1 As String = "确定"
        Public Button2 As String = ""
        Public Button3 As String = ""
        ''' <summary>
        ''' 点击第一个按钮将执行该方法，不关闭弹窗。
        ''' </summary>
        Public Button1Action As Action = Nothing
        ''' <summary>
        ''' 点击第二个按钮将执行该方法，不关闭弹窗。
        ''' </summary>
        Public Button2Action As Action = Nothing
        ''' <summary>
        ''' 点击第三个按钮将执行该方法，不关闭弹窗。
        ''' </summary>
        Public Button3Action As Action = Nothing
        Public IsWarn As Boolean = False
        Public ForceWait As Boolean = False
        Public WaitFrame As New DispatcherFrame(True)
        ''' <summary>
        ''' 弹窗是否已经关闭。
        ''' </summary>
        Public IsExited As Boolean = False
        ''' <summary>
        ''' 输入模式：输入的文本。若点击了 非 第一个按钮，则为 Nothing。
        ''' 选择模式：点击的按钮编号，从 1 开始。
        ''' 登录模式：字符串数组 {AccessToken, RefreshToken} 或一个 Exception。
        ''' </summary>
        Public Result As Object
    End Class
    Public Enum MyMsgBoxType
        Text
        [Select]
        Input
        Login
        ''' <summary>HMCL 式“添加离线模式账户”弹窗（仅用户名，UUID 由系统自动生成）。</summary>
        Offline
        ''' <summary>HMCL 式“添加账户”弹窗（先选择微软账号 / 离线账号）。</summary>
        AddAccount
        ''' <summary>HMCL 式离线账户皮肤选择弹窗（默认/Steve/Alex/本地文件/LittleSkin/Blessing Skin）。</summary>
        SkinLegacy
        ''' <summary>添加 LittleSkin/第三方登录账号表单（玩家名 + 密码 + 认证服务器，支持拖入按钮自动识别）。</summary>
        Auth
        ''' <summary>皮肤预览弹窗：先 3D 预览再决定是否保存（用于「正版皮肤获取」）。</summary>
        SkinPreview
        ''' <summary>整合包确认弹窗：显示图标/名称/类型，询问是否安装（拖入压缩包时使用，第 137 轮）。</summary>
        Modpack
    End Enum

    ''' <summary>
    ''' 添加第三方登录账号表单的回传数据。Name 为空表示用户取消/无效。
    ''' </summary>
    Public Structure AuthAccountData
        Public Name As String      '玩家名
        Public Pass As String      '密码
        Public Server As String    'Yggdrasil 认证服务器（如 https://littleskin.cn/api/yggdrasil）
        Public Register As String  '注册链接（如 https://littleskin.cn/auth/register）
    End Structure

    ''' <summary>
    ''' 皮肤预览弹窗的回传数据：已下载到本地的皮肤文件 + 展示用信息。
    ''' </summary>
    Public Structure SkinPreviewArgs
        ''' <summary>玩家名（标题与默认文件名）。</summary>
        Public Name As String
        ''' <summary>本地皮肤图片的绝对路径。</summary>
        Public File As String
        ''' <summary>来源描述（例如"正版玩家皮肤"），为空时按正版处理。</summary>
        Public Source As String
    End Structure

    ''' <summary>
    ''' 显示弹窗，返回点击按钮的编号（从 1 开始）。
    ''' </summary>
    ''' <param name="Title">弹窗的标题。</param>
    ''' <param name="Caption">弹窗的内容。</param>
    ''' <param name="Button1">显示的第一个按钮，默认为“确定”。</param>
    ''' <param name="Button2">显示的第二个按钮，默认为空。</param>
    ''' <param name="Button3">显示的第三个按钮，默认为空。</param>
    ''' <param name="Button1Action">点击第一个按钮将执行该方法，不关闭弹窗。</param>
    ''' <param name="Button2Action">点击第二个按钮将执行该方法，不关闭弹窗。</param>
    ''' <param name="Button3Action">点击第三个按钮将执行该方法，不关闭弹窗。</param>
    ''' <param name="IsWarn">是否为警告弹窗，若为 True，弹窗配色和背景会变为红色。</param>
    Public Function MyMsgBox(Caption As String, Optional Title As String = "提示",
                             Optional Button1 As String = "确定", Optional Button2 As String = "", Optional Button3 As String = "",
                             Optional IsWarn As Boolean = False, Optional HighLight As Boolean = True, Optional ForceWait As Boolean = False,
                             Optional Button1Action As Action = Nothing, Optional Button2Action As Action = Nothing, Optional Button3Action As Action = Nothing) As Integer
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        '默认参数是简体中文（"提示"/"确定"/"取消"），调用点大多不显式传，故在此统一过一次 L()；
        '已翻译过的文本再走一次 L() 是无害的（译文不是字典键，原样返回）。
        Caption = ModLang.L(Caption) : Title = ModLang.L(Title)
        Button1 = ModLang.L(Button1) : Button2 = ModLang.L(Button2) : Button3 = ModLang.L(Button3)
        '将弹窗列入队列
        Dim Converter As New MyMsgBoxConverter With {.Type = MyMsgBoxType.Text, .Button1 = Button1, .Button2 = Button2, .Button3 = Button3, .Text = Caption, .IsWarn = IsWarn, .Title = Title, .HighLight = HighLight, .ForceWait = True, .Button1Action = Button1Action, .Button2Action = Button2Action, .Button3Action = Button3Action}
        If Button2.Length > 0 OrElse ForceWait Then
            '若有多个按钮则开始等待
            If (FrmMain Is Nothing OrElse FrmMain.PanMsg Is Nothing) AndAlso RunInUi() Then
                '主窗体尚未加载，用老土的弹窗来替代
                If Button2.Length > 0 Then
                    Dim RawResult As MsgBoxResult =
                        MsgBox(Caption, If(Button3.Length > 0, MsgBoxStyle.YesNoCancel, MsgBoxStyle.YesNo) + If(IsWarn, MsgBoxStyle.Critical, MsgBoxStyle.Question), Title)
                    Select Case RawResult
                        Case MsgBoxResult.Yes
                            Converter.Result = 1
                        Case MsgBoxResult.No
                            Converter.Result = 2
                        Case MsgBoxResult.Cancel
                            Converter.Result = 3
                    End Select
                Else
                    MsgBox(Caption, MsgBoxStyle.OkOnly + If(IsWarn, MsgBoxStyle.Critical, MsgBoxStyle.Question), Title)
                    Converter.Result = 1
                End If
                Logger.Warn($"主窗体加载完成前出现意料外的等待弹窗：{Button1},{Button2},{Button3}")
            Else
                WaitingMyMsgBox.Enqueue(Converter)
                Try
                    FrmMain.DragStop()
                    If RunInUi() Then MyMsgBoxTick()
                    ComponentDispatcher.PushModal()
                    Dispatcher.PushFrame(Converter.WaitFrame)
                Finally
                    ComponentDispatcher.PopModal()
                End Try
            End If
            Logger.Info($"普通弹框返回：{If(Converter.Result, "null")}")
            Return Converter.Result
        Else
            '不进行等待，直接返回
            WaitingMyMsgBox.Enqueue(Converter)
            Return 1
        End If
    End Function
    ''' <summary>
    ''' 显示输入框并返回输入的文本。若点击第二个按钮，则返回 Nothing。
    ''' </summary>
    ''' <param name="Title">弹窗的标题。</param>
    ''' <param name="ValidateRules">文本框的输入检测。</param>
    ''' <param name="Text">弹窗的介绍文本。</param>
    ''' <param name="DefaultInput">文本框的默认内容。</param>
    ''' <param name="HintText">文本框的提示内容。</param>
    ''' <param name="Button1">显示的第一个按钮，默认为“确定”。</param>
    ''' <param name="Button2">显示的第二个按钮，默认为“取消”。</param>
    ''' <param name="IsWarn">是否为警告弹窗，若为 True，弹窗配色和背景会变为红色。</param>
    Public Function MyMsgBoxInput(Title As String, Optional Text As String = Nothing, Optional DefaultInput As String = Nothing, Optional ValidateRules As Collection(Of Validate) = Nothing, Optional HintText As String = "", Optional Button1 As String = "确定", Optional Button2 As String = "取消", Optional IsWarn As Boolean = False) As String
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        '默认参数是简体中文，调用点大多不显式传，故在此统一过一次 L()
        Title = ModLang.L(Title) : Button1 = ModLang.L(Button1) : Button2 = ModLang.L(Button2)
        If Text IsNot Nothing Then Text = ModLang.L(Text)
        HintText = ModLang.L(HintText)
        '将弹窗列入队列
        Dim Converter As New MyMsgBoxConverter With {.Text = If(Text, ""), .HintText = If(HintText, ""), .Type = MyMsgBoxType.Input, .ValidateRules = If(ValidateRules, New Collection(Of Validate)), .Button1 = Button1, .Button2 = Button2, .Content = DefaultInput, .IsWarn = IsWarn, .Title = Title}
        WaitingMyMsgBox.Enqueue(Converter)
        '虽然我也不知道这是啥但是能用就成了 :)
        Try
            If FrmMain IsNot Nothing Then FrmMain.DragStop()
            If RunInUi() Then MyMsgBoxTick()
            ComponentDispatcher.PushModal()
            Dispatcher.PushFrame(Converter.WaitFrame)
        Finally
            ComponentDispatcher.PopModal()
        End Try
        Logger.Info($"输入弹框返回：{If(Converter.Result, "null")}")
        Return Converter.Result
    End Function
    ''' <summary>
    ''' 显示 HMCL 式“添加离线模式账户”弹窗（仅用户名）。
    ''' 返回玩家名；点击取消或关闭则返回 Nothing。UUID 一律由系统按玩家名自动生成，用户无需干预。
    ''' </summary>
    Public Function MyMsgBoxOfflineAccount() As String
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        Dim Converter As New MyMsgBoxConverter With {.Type = MyMsgBoxType.Offline, .Title = "添加离线模式账户", .Button1 = "登录", .Button2 = "取消", .ForceWait = True}
        WaitingMyMsgBox.Enqueue(Converter)
        Try
            If FrmMain IsNot Nothing Then FrmMain.DragStop()
            If RunInUi() Then MyMsgBoxTick()
            ComponentDispatcher.PushModal()
            Dispatcher.PushFrame(Converter.WaitFrame)
        Finally
            ComponentDispatcher.PopModal()
        End Try
        Return If(Converter.Result, Nothing)
    End Function

    ''' <summary>
    ''' 显示"添加 LittleSkin/第三方登录账号"表单（玩家名 + 密码 + 认证服务器，支持拖入按钮自动识别）。
    ''' 与离线账号表单同一模式：提交即回传账号数据；取消返回 Nothing。
    ''' </summary>
    Public Function MyMsgBoxAuthAccount() As AuthAccountData?
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        Dim Converter As New MyMsgBoxConverter With {.Type = MyMsgBoxType.Auth, .Title = "添加 LittleSkin 账号", .Button1 = "登录", .Button2 = "取消", .ForceWait = True}
        WaitingMyMsgBox.Enqueue(Converter)
        Try
            If FrmMain IsNot Nothing Then FrmMain.DragStop()
            If RunInUi() Then MyMsgBoxTick()
            ComponentDispatcher.PushModal()
            Dispatcher.PushFrame(Converter.WaitFrame)
        Finally
            ComponentDispatcher.PopModal()
        End Try
        If IsNothing(Converter.Result) Then
            Return Nothing
        Else
            Return CType(Converter.Result, AuthAccountData?)
        End If
    End Function
    ''' <summary>
    ''' 显示 HMCL 式“添加账户”弹窗：先选择账户类型，再进入对应流程。
    ''' 返回 1 = 微软账号，2 = 离线账号；取消或关闭则返回 Nothing。
    ''' </summary>
    Public Function MyMsgBoxAddAccount() As Integer?
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        Dim Converter As New MyMsgBoxConverter With {.Type = MyMsgBoxType.AddAccount, .Title = "添加账户", .Button1 = "确定", .Button2 = "取消", .ForceWait = True}
        WaitingMyMsgBox.Enqueue(Converter)
        Try
            If FrmMain IsNot Nothing Then FrmMain.DragStop()
            If RunInUi() Then MyMsgBoxTick()
            ComponentDispatcher.PushModal()
            Dispatcher.PushFrame(Converter.WaitFrame)
        Finally
            ComponentDispatcher.PopModal()
        End Try
        Return If(Converter.Result, Nothing)
    End Function
    ''' <summary>
    ''' 显示 HMCL 式皮肤弹窗（默认/Steve/Alex/正版玩家/本地文件/LittleSkin/自定义皮肤站），
    ''' 左侧是 3D 可旋转模型。返回选择的皮肤类型（ModLegacySkin.SkinConfig.Type）；取消或关闭则返回 Nothing。
    ''' <para/>
    ''' <paramref name="PreviewOnly"/> = True 时用于微软 / 第三方账户的只读预览：不写配置、按钮变成「关闭」。
    ''' </summary>
    Public Function MyMsgBoxSkinLegacy(PlayerName As String, Optional PreviewOnly As Boolean = False,
                                       Optional PresetType As Integer = -1, Optional PresetId As String = "") As Integer?
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        Dim Args As New ModLegacySkin.SkinEditorArgs With {
            .PlayerName = PlayerName, .PreviewOnly = PreviewOnly, .PresetType = PresetType, .PresetId = PresetId}
        Dim Converter As New MyMsgBoxConverter With {.Type = MyMsgBoxType.SkinLegacy, .Title = If(PreviewOnly, "查看皮肤", "编辑皮肤"),
            .Button1 = If(PreviewOnly, "关闭", "确定"), .Button2 = If(PreviewOnly, "", "取消"), .Content = Args, .ForceWait = True}
        WaitingMyMsgBox.Enqueue(Converter)
        Try
            If FrmMain IsNot Nothing Then FrmMain.DragStop()
            If RunInUi() Then MyMsgBoxTick()
            ComponentDispatcher.PushModal()
            Dispatcher.PushFrame(Converter.WaitFrame)
        Finally
            ComponentDispatcher.PopModal()
        End Try
        Return If(Converter.Result, Nothing)
    End Function
    ''' <summary>
    ''' 显示选择框并返回选择的第几项（从 0 开始）。若点击第二个按钮，则返回 Nothing。
    ''' </summary>
    ''' <param name="Title">弹窗的标题。</param>
    ''' <param name="Button1">显示的第一个按钮，默认为 “确定”。</param>
    ''' <param name="Button2">显示的第二个按钮，默认为空。</param>
    ''' <param name="IsWarn">是否为警告弹窗，若为 True，弹窗配色和背景会变为红色。</param>
    Public Function MyMsgBoxSelect(Selections As IEnumerable(Of IMyRadio), Optional Title As String = "提示", Optional Button1 As String = "确定", Optional Button2 As String = "", Optional IsWarn As Boolean = False) As Integer?
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        '默认参数是简体中文，调用点大多不显式传，故在此统一过一次 L()
        Title = ModLang.L(Title) : Button1 = ModLang.L(Button1) : Button2 = ModLang.L(Button2)
        '将弹窗列入队列
        Dim Converter As New MyMsgBoxConverter With {.Type = MyMsgBoxType.Select, .Button1 = Button1, .Button2 = Button2, .Content = Selections, .IsWarn = IsWarn, .Title = Title}
        WaitingMyMsgBox.Enqueue(Converter)
        '虽然我也不知道这是啥但是能用就成了 :)
        Try
            If FrmMain IsNot Nothing Then FrmMain.DragStop()
            If RunInUi() Then MyMsgBoxTick()
            ComponentDispatcher.PushModal()
            Dispatcher.PushFrame(Converter.WaitFrame)
        Finally
            ComponentDispatcher.PopModal()
        End Try
        Logger.Info($"选择弹框返回：{If(Converter.Result, "null")}")
        Return Converter.Result
    End Function

    ''' <summary>
    ''' 皮肤预览弹窗：先把已经下载到本地的一张皮肤 3D 预览出来，用户点「保存」才另存为。
    ''' 同步阻塞直到关闭（与其它弹窗一致），调用方需在 UI 线程执行。
    ''' </summary>
    ''' <param name="Name">玩家名，用作标题与默认文件名。</param>
    ''' <param name="File">本地皮肤图片路径。</param>
    ''' <param name="Source">来源描述，可留空。</param>
    Public Sub MyMsgBoxSkinPreview(Name As String, File As String, Optional Source As String = "")
        Try
            If FrmMain Is Nothing OrElse FrmMain.PanMsg Is Nothing Then
                '主窗体还没起来，退回直接另存为，避免弹窗无处可显示
                Dim SavePath As String = Dialogs.SaveFile(ModLang.L("保存正版皮肤"), If(Name, "skin") & ".png", filter:={("png", ModLang.L("PNG 图片"))})
                If Not String.IsNullOrEmpty(SavePath) Then FileUtils.Copy(File, SavePath)
                Return
            End If
            Dim Converter As New MyMsgBoxConverter With {
                .Type = MyMsgBoxType.SkinPreview,
                .Content = New SkinPreviewArgs With {.Name = Name, .File = File, .Source = Source}}
            WaitingMyMsgBox.Enqueue(Converter)
            Try
                FrmMain.DragStop()
                If RunInUi() Then MyMsgBoxTick()
                ComponentDispatcher.PushModal()
                Dispatcher.PushFrame(Converter.WaitFrame)
            Finally
                ComponentDispatcher.PopModal()
            End Try
        Catch ex As Exception
            Logger.Error(ex, "皮肤预览弹窗失败", LogBehavior.Toast)
        End Try
    End Sub

    ''' <summary>
    ''' 拖入整合包时的确认弹窗：显示包图标、名称与类型，返回 1 = 安装，2 = 取消。
    ''' <para/>同步阻塞直到关闭（与其它弹窗一致）。需要在 UI 线程调用；后台线程请用 <c>RunInUiWait</c> 包一层。
    ''' </summary>
    Public Function MyMsgBoxModpack(Info As McModpackInfo) As Integer
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式
        If FrmMain Is Nothing OrElse FrmMain.PanMsg Is Nothing Then Return 2
        Dim Converter As New MyMsgBoxConverter With {
            .Type = MyMsgBoxType.Modpack,
            .Title = "安装整合包",
            .Button1 = "安装",
            .Button2 = "取消",
            .Text = "是否安装此整合包？安装后会作为一个新实例出现在实例列表中。",
            .Content = Info}
        WaitingMyMsgBox.Enqueue(Converter)
        Try
            FrmMain.DragStop()
            If RunInUi() Then MyMsgBoxTick()
            ComponentDispatcher.PushModal()
            Dispatcher.PushFrame(Converter.WaitFrame)
        Finally
            ComponentDispatcher.PopModal()
        End Try
        Dim Choice As Integer = If(Converter.Result Is Nothing, 2, CInt(Converter.Result))
        Logger.Info($"整合包弹窗返回：{Choice}")
        Return Choice
    End Function

    ''' <summary>
    ''' 等待显示的弹窗。
    ''' </summary>
    Public WaitingMyMsgBox As New ConcurrentQueue(Of MyMsgBoxConverter)
    Public Sub MyMsgBoxTick()
        Try
            If FrmMain?.PanMsg Is Nothing OrElse FrmMain.WindowState = WindowState.Minimized Then Return
            Dim TargetMsgBox As MyMsgBoxConverter = Nothing
            If FrmMain.PanMsg.Children.Count > 0 Then
                '弹窗中
                FrmMain.PanMsg.Visibility = Visibility.Visible
            ElseIf WaitingMyMsgBox.TryDequeue(TargetMsgBox) Then
                '没有弹窗，显示一个等待的弹窗
                FrmMain.PanMsg.Visibility = Visibility.Visible
                Select Case TargetMsgBox.Type
                    Case MyMsgBoxType.Input
                        FrmMain.PanMsg.Children.Add(New MyMsgInput(TargetMsgBox))
                    Case MyMsgBoxType.Select
                        FrmMain.PanMsg.Children.Add(New MyMsgSelect(TargetMsgBox))
                    Case MyMsgBoxType.Text
                        FrmMain.PanMsg.Children.Add(New MyMsgText(TargetMsgBox))
                    Case MyMsgBoxType.Login
                        FrmMain.PanMsg.Children.Add(New MyMsgLogin(TargetMsgBox))
                    Case MyMsgBoxType.Offline
                        FrmMain.PanMsg.Children.Add(New MyMsgOffline(TargetMsgBox))
                    Case MyMsgBoxType.AddAccount
                        FrmMain.PanMsg.Children.Add(New MyMsgAddAccount(TargetMsgBox))
                    Case MyMsgBoxType.SkinLegacy
                        FrmMain.PanMsg.Children.Add(New MyMsgSkinLegacy(TargetMsgBox))
                    Case MyMsgBoxType.Auth
                        FrmMain.PanMsg.Children.Add(New MyMsgAuth(TargetMsgBox))
                    Case MyMsgBoxType.SkinPreview
                        FrmMain.PanMsg.Children.Add(New MyMsgSkinPreview(TargetMsgBox))
                    Case MyMsgBoxType.Modpack
                        FrmMain.PanMsg.Children.Add(New MyMsgModpack(TargetMsgBox))
                End Select
            Else
                '没有弹窗，没有等待的弹窗
                If FrmMain.PanMsg.Visibility <> Visibility.Collapsed Then FrmMain.PanMsg.Visibility = Visibility.Collapsed
            End If
        Catch ex As Exception
            Logger.Error(ex, "处理等待中的弹窗失败")
        End Try
    End Sub

#End Region

    ''' <summary>
    ''' 列表行右侧操作按钮是否常驻显示（个性化 → 列表按钮，第 132 轮）。
    ''' False = 仅在鼠标悬停该行时显示；True = 所有列表行始终显示按钮。
    ''' 每次读取设置，切换后无需重启（FormMain.RefreshListButtons 会刷新已实例化的行）。
    ''' </summary>
    Public ReadOnly Property UiListButtonsAlwaysShow As Boolean
        Get
            Try
                Return Settings.Get(Of Boolean)("UiListButtonsAlwaysShow")
            Catch ex As Exception
                Return False
            End Try
        End Get
    End Property

#Region "页面声明"
    '在最后进行页面声明，避免颜色尚未加载完毕

    '窗体声明
    Public FrmMain As FormMain
    Public FrmStart As SplashScreen

    '页面声明（出于单元测试考虑，初始化页面已转入 FormMain 中）
    '左栏与右栏对称（第 105 轮启动提速）：延迟到窗口首帧后构造，把「BAML 解析 + 控件树构造」
    '移出「窗口显示」关键路径。任何代码访问 FrmLaunchLeft 都会按需创建，无需担心 Nothing。
    Friend _FrmLaunchLeft As PageLaunchLeft '由 FormMain.EnsureLaunchLeftCreated 延迟填充
    Public Property FrmLaunchLeft As PageLaunchLeft
        Get
            If _FrmLaunchLeft Is Nothing AndAlso FrmMain IsNot Nothing Then FrmMain.EnsureLaunchLeftCreated()
            Return _FrmLaunchLeft
        End Get
        Set(value As PageLaunchLeft)
            _FrmLaunchLeft = value
        End Set
    End Property
    '右栏（主页）延迟创建：主页内容本身就是异步生成的，晚一帧挂上用户无感，
    '但能把约 430ms 的 XAML 解析移出「窗口显示」关键路径（第 65 轮启动提速）。
    '任何代码访问 FrmLaunchRight 都会按需创建，因此无需担心 Nothing。
    Friend _FrmLaunchRight As PageLaunchRight '由 FormMain.EnsureLaunchRightCreated 延迟填充
    Public Property FrmLaunchRight As PageLaunchRight
        Get
            If _FrmLaunchRight Is Nothing AndAlso FrmMain IsNot Nothing Then FrmMain.EnsureLaunchRightCreated()
            Return _FrmLaunchRight
        End Get
        Set(value As PageLaunchRight)
            _FrmLaunchRight = value
        End Set
    End Property
    Public FrmSelectLeft As PageSelectLeft
    Public FrmSelectRight As PageSelectRight
    Public FrmSpeedLeft As PageSpeedLeft
    Public FrmSpeedRight As PageSpeedRight

    '联机页面声明
    Public FrmLinkMain As PageLinkMain

    '下载页面声明
    Public FrmDownloadLeft As PageDownloadLeft
    Public FrmDownloadInstall As PageDownloadInstall
    Public FrmDownloadMod As PageDownloadMod
    Public FrmDownloadPack As PageDownloadPack
    Public FrmDownloadDataPack As PageDownloadDataPack
    Public FrmDownloadShader As PageDownloadShader
    Public FrmDownloadFavorites As PageDownloadFavorites
    Public FrmDownloadWorld As PageDownloadWorld
    Public FrmDownloadResourcePack As PageDownloadResourcePack

    '设置页面声明
    Public FrmSetupLeft As PageSetupLeft
    Public FrmSetupLaunch As PageSetupLaunch
    Public FrmSetupUI As PageSetupUI
    Public FrmSetupSystem As PageSetupSystem
    Public FrmSetupLink As PageSetupLink
    Public FrmSetupJava As PageSetupJava
    Public FrmSetupLog As PageSetupLog
    Public FrmSetupMore As PageSetupMore
    Public FrmSetupCache As PageSetupCache
    Public FrmSetupNetwork As PageSetupNetwork

    '其他页面声明
    Public FrmOtherLeft As PageOtherLeft
    Public FrmOtherHelp As PageOtherHelp
    Public FrmOtherAbout As PageOtherAbout
    Public FrmOtherTest As PageOtherTest
    Public FrmOtherNews As PageOtherNews

    '登录页面声明
    Public FrmLoginLegacy As PageLoginLegacy
    Public FrmLoginNide As PageLoginNide
    Public FrmLoginNideSkin As PageLoginNideSkin
    Public FrmLoginAuth As PageLoginAuth
    Public FrmLoginAuthSkin As PageLoginAuthSkin
    Public FrmLoginMs As PageLoginMs
    Public FrmLoginMsSkin As PageLoginMsSkin
    Public FrmLoginManager As PageAccountManager '账户管理页（第 72 轮，HMCL 式全屏）

    '版本设置页面声明
    Public FrmInstanceLeft As PageInstanceLeft
    Public FrmInstanceOverall As PageInstanceOverall
    Public FrmInstanceMod As PageInstanceMod
    Public FrmInstanceModDisabled As PageInstanceModDisabled
    Public FrmInstanceSetup As PageInstanceSetup
    Public FrmInstanceExport As PageInstanceExport
    Public FrmInstanceSaves As PageInstanceSaves
    Public FrmInstanceServer As PageInstanceServer
    Public FrmInstanceJarInJar As PageInstanceJarInJar
    Public FrmInstanceCompResource As PageInstanceCompResource
    Public FrmInstanceSavesDatapack As PageInstanceSavesDatapack
    Public FrmInstanceDiagnostics As PageInstanceDiagnostics
    Public FrmInstanceOptions As PageInstanceOptions
    Public FrmInstanceHistory As PageInstanceHistory
    Public FrmInstanceResourcePack As PageInstanceResourcePack
    Public FrmInstanceShader As PageInstanceShader
    Public FrmInstanceMap As PageInstanceMap
    Public FrmInstanceScreenshot As PageInstanceScreenshot
    Public FrmDownloadModify As PageDownloadModify

    '资源信息分页声明
    Public FrmDownloadResourceDetail As PageDownloadResourceDetail

#End Region

#Region "愚人节"

    Private _IsAprilEnabled As Boolean? = Nothing
    Public ReadOnly Property IsAprilEnabled As Boolean
        Get
            If _IsAprilEnabled Is Nothing Then
                _IsAprilEnabled =
                    Date.Now.Month = 4 AndAlso Date.Now.Day = 1 AndAlso
                    Settings.Get(Of Integer)("AprilYear") <> Date.Now.Year '成功后同年内则不再触发
            End If
            Return _IsAprilEnabled.Value
        End Get
    End Property
    Public IsAprilGiveup As Boolean = False
    Private AprilDifficultiedDistance As Integer = 0
    Private Sub TimerFool()
        Try
            If FrmLaunchLeft Is Nothing OrElse FrmLaunchRight.AprilPosTrans Is Nothing OrElse FrmMain.lastMouseArg Is Nothing Then Return
            If IsAprilGiveup OrElse FrmMain.PageCurrent <> FormMain.PageType.Launch OrElse AniControlEnabled <> 0 OrElse FrmLaunchRight Is Nothing OrElse Not FrmLaunchRight.BtnLaunch.IsLoaded Then Return

            '计算是否空闲
            Dim MousePos = FrmMain.lastMouseArg.GetPosition(FrmMain)
            Static IdieCount As Integer = 0
            Static MousePosLast As New Point(0, 0)
            If MousePos = MousePosLast Then
                IdieCount += 1
            Else
                MousePosLast = MousePos
                IdieCount = 0
            End If
            '计算躲避移动
            Dim Direction As Vector
            Dim Distance As Double
            Dim ButtonWidth = FrmLaunchRight.BtnLaunch.ActualWidth / 2, ButtonHeight = FrmLaunchRight.BtnLaunch.ActualHeight / 2
            Dim Vec As Vector = FrmMain.lastMouseArg.GetPosition(FrmLaunchRight.BtnLaunch) - New Vector(ButtonWidth, ButtonHeight)
            Dim Dir As New Vector(Vec.X, Vec.Y)
            Dir.Normalize()
            Direction = -Dir
            Distance = New Vector(Math.Max(0, Math.Abs(Vec.X) - ButtonWidth), Math.Max(0, Math.Abs(Vec.Y) - ButtonHeight)).Length
            Dim BreathScale = Math.Sin(Timer150Count / 37.5 * Math.PI)
            Dim Difficulty As Double = 1 / (1 + (AprilDifficultiedDistance / 6000) ^ 2) + 0.25 '难度，初始为 1.25x，6000 距离为 0.75x，12000 为 0.5x，24000 为 0.256x，最低 0.25x
            Dim Acc = Math.Max(0, -0.65 - Math.Log((Distance / Difficulty + 0.4) / 200)) * Direction * Difficulty '加速度
            'FrmMain.Title = CInt(AprilTotalDistance) & " - " & Difficulty
            '5s 不动时回到起始点
            If IdieCount >= 64 * 5 Then
                Dim SafeDist As Vector = FrmMain.lastMouseArg.GetPosition(FrmMain.PanMain) - New Vector(ButtonWidth, FrmMain.PanMain.ActualHeight - ButtonHeight * 3)
                Dim Back As New Vector(FrmLaunchRight.AprilPosTrans.X, FrmLaunchRight.AprilPosTrans.Y)
                If SafeDist.Length > 250 AndAlso Back.Length > 0.4 Then
                    Acc -= Back * 0.0005
                    Back.Normalize()
                    Acc -= Back * 0.15
                End If
            End If
            '回到边界
            Static Speed As New Vector(0, 0)
            Dim Relative As Point = FrmLaunchRight.BtnLaunch.TranslatePoint(New Point(0, 0), FrmMain.PanForm)
            If Relative.X < -ButtonWidth * 2 Then
                FrmLaunchRight.AprilPosTrans.X += FrmMain.PanForm.ActualWidth + ButtonWidth * 2 '离开左边界
                Speed.X -= 80
                If Relative.Y < 0 Then
                    FrmLaunchRight.AprilPosTrans.Y += ButtonHeight * 2.5
                ElseIf Relative.Y > FrmMain.PanForm.ActualHeight - ButtonHeight * 2 Then
                    FrmLaunchRight.AprilPosTrans.Y -= ButtonHeight * 2.5
                End If
            ElseIf Relative.X > FrmMain.PanForm.ActualWidth Then
                FrmLaunchRight.AprilPosTrans.X -= FrmMain.PanForm.ActualWidth + ButtonWidth * 2 '离开右边界
                Speed.X += 80
                If Relative.Y < 0 Then
                    FrmLaunchRight.AprilPosTrans.Y += ButtonHeight * 2.5
                ElseIf Relative.Y > FrmMain.PanForm.ActualHeight - ButtonHeight * 2 Then
                    FrmLaunchRight.AprilPosTrans.Y -= ButtonHeight * 2.5
                End If
            ElseIf Relative.Y < -ButtonHeight * 2 Then
                FrmLaunchRight.AprilPosTrans.Y += FrmMain.PanForm.ActualHeight + ButtonHeight * 2 '离开上边界
                Speed.Y -= 25
                If Relative.X < 0 Then
                    FrmLaunchRight.AprilPosTrans.X += ButtonWidth * 2
                ElseIf Relative.X > FrmMain.PanForm.ActualWidth - ButtonWidth * 2 Then
                    FrmLaunchRight.AprilPosTrans.X -= ButtonWidth * 2
                End If
            ElseIf Relative.Y > FrmMain.PanForm.ActualHeight Then
                FrmLaunchRight.AprilPosTrans.Y -= FrmMain.PanForm.ActualHeight + ButtonHeight * 2 '离开下边界
                Speed.Y += 25
                If Relative.X < 0 Then
                    FrmLaunchRight.AprilPosTrans.X += ButtonWidth * 2
                ElseIf Relative.X > FrmMain.PanForm.ActualWidth - ButtonWidth * 2 Then
                    FrmLaunchRight.AprilPosTrans.X -= ButtonWidth * 2
                End If
            End If
            '移动
            Speed = Speed * 0.8 + Acc
            Dim SpeedValue = Math.Min(60, Speed.Length)
            If SpeedValue < 0.01 Then Return
            Speed.Normalize()
            Speed *= SpeedValue
            AprilDifficultiedDistance += SpeedValue
            FrmLaunchRight.AprilPosTrans.X += Speed.X
            FrmLaunchRight.AprilPosTrans.Y += Speed.Y
            '大小改变
            FrmLaunchRight.AprilScaleTrans.ScaleX = (1 - (Math.Abs(Direction.X) - Math.Abs(Direction.Y)) * (SpeedValue / 160)).Clamp(0.2, 1.8)
            FrmLaunchRight.AprilScaleTrans.ScaleY = (1 - (Math.Abs(Direction.Y) - Math.Abs(Direction.X)) * (SpeedValue / 100)).Clamp(0.2, 1.8)
            '放弃提示
            Static GiveUpDistance As Double = -1500
            GiveUpDistance += SpeedValue
            If GiveUpDistance > 2500 Then
                GiveUpDistance = 0
                Select Case RandomInteger(0, 3)
                    Case 0
                        Hint(ModLang.L("放弃吧！只需要点一下右下角的小白旗……"), HintType.Red)
                    Case 1
                        Hint(ModLang.L("看到右下角的那面小白旗了吗？"), HintType.Red)
                    Case 2
                        Hint(ModLang.L("这里建议点一下右下角的小白旗投降呢.jpg"), HintType.Red)
                    Case 3
                        Hint(ModLang.L("右下角的小白旗永远等着你……"), HintType.Red)
                End Select
            End If

        Catch ex As Exception
            Logger.Error(ex, "愚人节移动出错")
        End Try
    End Sub

#End Region

#Region "系统"

    ''' <summary>
    ''' 把某个 TCL 窗口拖到最前面。
    ''' </summary>
    Public Sub ShowWindowToTop(Handle As IntPtr)
        Try
            PostMessage(Handle, 400 * 16 + 2, 0, 0)
            SetForegroundWindow(Handle) '不在这里放不行，神秘 WinAPI，建议别动
        Catch ex As Exception
            Logger.Error(ex, "设置窗口置顶失败", LogBehavior.Toast)
        End Try
    End Sub
    Public Declare Function FindWindow Lib "user32" Alias "FindWindowA" (ClassName As String, WindowName As String) As IntPtr
    Public Declare Function SetForegroundWindow Lib "user32" (hWnd As IntPtr) As Integer
    Private Declare Function PostMessage Lib "user32" Alias "PostMessageA" (hWnd As IntPtr, msg As UInteger, wParam As Long, lParam As Long) As Boolean

    ''' <summary>
    ''' 将特定程序设置为使用高性能显卡启动。
    ''' 如果失败，则抛出异常。
    ''' </summary>
    Public Sub SetGPUPreference(Executeable As String)
        Const REG_KEY As String = "HKEY_CURRENT_USER\Software\Microsoft\DirectX\UserGpuPreferences"
        Const REG_VALUE As String = "GpuPreference=2;"
        '查看现有设置
        If RegistryUtils.Read(REG_KEY, Executeable).result?.ToString() = REG_VALUE Then
            Logger.Info($"无需调整显卡设置：{Executeable}")
            Return
        End If
        '写入新设置
        RegistryUtils.Write(REG_KEY, Executeable, REG_VALUE)
        Logger.Info($"已调整显卡设置：{Executeable}")
    End Sub

    ''' <summary>
    ''' 对替换标记进行处理。会对替换内容使用 EscapeHandler 进行转义。
    ''' </summary>
    Public Function ArgumentReplace(Text As String, Optional EscapeHandler As Func(Of String, String) = Nothing, Optional ReplaceTime As Boolean = True) As String
        If Text Is Nothing OrElse Not Text.Contains("{") Then Return Text
        '预处理（注意，文件夹必须以 \ 结尾）
        '注意：此处不能用 Static——Static 局部变量只初始化一次，会把首次调用捕获到的
        'EscapeHandler 冻结下来，导致后续传入 HtmlEncode/XmlEscape 的调用复用错误的处理器（转义被跳过）。
        Dim Replacer As Func(Of String, String) =
        Function(s As String) As String
            If s Is Nothing Then Return ""
            If EscapeHandler Is Nothing Then Return s
            If s.Contains(":\") Then
                Dim IsFolder = s.EndsWithF("\") OrElse s.EndsWithF("/")
                s = PathUtils.ToShortPath(s)
                If IsFolder Then s = PathUtils.AddSlashSuffix(s)
            End If
            Return EscapeHandler(s)
        End Function
        '基础
        Text = Text.Replace("{tcl_version}", Replacer(VersionBaseName))
        Text = Text.Replace("{tcl_version_code}", Replacer(VersionCode))
        Text = Text.Replace("{tcl_version_branch}", Replacer(BuildTypeDisplay))
        Text = Text.Replace("{tcl_build_type}", Replacer(BuildType.ToString))
        Text = Text.Replace("{tcl_branch}", Replacer(VersionBranchMain))
        Text = Text.Replace("{identify}", Replacer(Identify))
        Text = Text.Replace("{path}", Replacer(Paths.Base))
        Text = Text.Replace("{path_with_name}", Replacer(PathExe))
        Text = Text.Replace("{path_temp}", Replacer(PathTemp))
        Text = Text.Replace("{tcl_md5}", Function() Replacer(CryptographyUtils.ComputeFileHash(PathExe, CryptographyUtils.HashMethod.Md5)))
        Text = Text.Replace("{tcl_sha1}", Function() Replacer(CryptographyUtils.ComputeFileHash(PathExe, CryptographyUtils.HashMethod.Sha1)))
        '时间
        If ReplaceTime Then '在窗口标题中，时间会被后续动态替换，所以此时不应该替换
            Text = Text.Replace("{date}", Replacer(Date.Now.ToString("yyyy'/'M'/'d")))
            Text = Text.Replace("{time}", Replacer(Date.Now.ToString("HH':'mm':'ss")))
        End If
        'Minecraft
        Text = Text.Replace("{java}", Replacer(McLaunchJavaSelected?.Folder))
        Text = Text.Replace("{minecraft}", Replacer(McFolderSelected))
        If McInstanceSelected?.IsLoaded Then
            Text = Text.Replace("{version_path}", Replacer(McInstanceSelected.PathVersion)) : Text = Text.Replace("{verpath}", Replacer(McInstanceSelected.PathVersion))
            Text = Text.Replace("{version_indie}", Replacer(McInstanceSelected.PathIndie)) : Text = Text.Replace("{verindie}", Replacer(McInstanceSelected.PathIndie))
            Text = Text.Replace("{name}", Replacer(McInstanceSelected.Name))
            If {"unknown", "old", "pending"}.Contains(McInstanceSelected.Version.VanillaName.Lower) Then
                Text = Text.Replace("{version}", Replacer(McInstanceSelected.Name))
            Else
                Text = Text.Replace("{version}", Replacer(McInstanceSelected.Version.VanillaName))
            End If
            Text = Text.Replace("{forge}", Replacer(If(McInstanceSelected.Version.ModLoaders.HasFlagF(ModLoaders.Forge), McInstanceSelected.Version.ModLoaderVersions(ModLoaders.Forge), "")))
            Text = Text.Replace("{fabric}", Replacer(If(McInstanceSelected.Version.ModLoaders.HasFlagF(ModLoaders.Fabric), McInstanceSelected.Version.ModLoaderVersions(ModLoaders.Fabric), "")))
            Text = Text.Replace("{neoforge}", Replacer(If(McInstanceSelected.Version.ModLoaders.HasFlagF(ModLoaders.NeoForge), McInstanceSelected.Version.ModLoaderVersions(ModLoaders.NeoForge), "")))
            Text = Text.Replace("{liteloader}", Replacer(If(McInstanceSelected.Version.ModLoaders.HasFlagF(ModLoaders.LiteLoader), "true", "")))
        Else
            Text = Text.Replace("{version_path}", Replacer(Nothing)) : Text = Text.Replace("{verpath}", Replacer(Nothing))
            Text = Text.Replace("{version_indie}", Replacer(Nothing)) : Text = Text.Replace("{verindie}", Replacer(Nothing))
            Text = Text.Replace("{name}", Replacer(Nothing))
            Text = Text.Replace("{version}", Replacer(Nothing))
            Text = Text.Replace("{forge}", Replacer(Nothing))
            Text = Text.Replace("{fabric}", Replacer(Nothing))
            Text = Text.Replace("{neoforge}", Replacer(Nothing))
            Text = Text.Replace("{liteloader}", Replacer(Nothing))
        End If
        '登录信息
        If McLoginLoader.State = LoadState.Finished Then
            Text = Text.Replace("{user}", Replacer(McLoginLoader.Output.Name))
            Text = Text.Replace("{uuid}", Replacer(McLoginLoader.Output.Uuid?.Lower))
            Select Case McLoginLoader.Input.Type
                Case McLoginType.Legacy
                    Text = Text.Replace("{login}", Replacer("离线"))
                Case McLoginType.Ms
                    Text = Text.Replace("{login}", Replacer("正版"))
                Case McLoginType.Nide
                    Text = Text.Replace("{login}", Replacer("统一通行证"))
                Case McLoginType.Auth
                    Text = Text.Replace("{login}", Replacer("Authlib-Injector"))
            End Select
        Else
            Text = Text.Replace("{user}", Replacer(Nothing))
            Text = Text.Replace("{uuid}", Replacer(Nothing))
            Text = Text.Replace("{login}", Replacer(Nothing))
        End If
        '高级
        Text = Text.RegexReplace("\{hint\}", Function() Replacer(PageOtherTest.GetRandomHint()))
        Text = Text.RegexReplace("\{cave\}", Function() Replacer(PageOtherTest.GetRandomCave()))
        Text = Text.RegexReplace("\{setup:([a-zA-Z0-9]+)\}", Function(m) Replacer(Settings.GetSafe(m.Groups(1).Value, McInstanceSelected)))
        Text = Text.RegexReplace("\{varible:([^:\}]+)(?::([^\}]+))?\}", Function(m) Replacer(RegistryUtils.TryRead($"HKEY_CURRENT_USER\Software\{RegFolder}", $"CustomEvent{m.Groups(1).Value}", m.Groups(2).Value)))
        Text = Text.RegexReplace("\{variable:([^:\}]+)(?::([^\}]+))?\}", Function(m) Replacer(RegistryUtils.TryRead($"HKEY_CURRENT_USER\Software\{RegFolder}", $"CustomEvent{m.Groups(1).Value}", m.Groups(2).Value)))
        Return Text
    End Function

#End Region

#Region "任务缓存"

    Private IsTaskTempCleared As Boolean = False
    Private IsTaskTempClearing As Boolean = False

    ''' <summary>
    ''' 尝试清理任务缓存文件夹。
    ''' 在整次运行中只会实际清理一次。
    ''' </summary>
    Public Sub TryClearTaskTemp()
        If Not IsTaskTempCleared Then
            IsTaskTempCleared = True
            IsTaskTempClearing = True
            Try
                Logger.Info("开始清理任务缓存文件夹")
                DirectoryUtils.Delete($"{OsDrive}ProgramData\TCL\TaskTemp\")
                DirectoryUtils.Delete($"{PathTemp}TaskTemp\")
                Logger.Info("已清理任务缓存文件夹")
            Catch ex As Exception
                Logger.Warn(ex, "清理任务缓存文件夹失败")
            Finally
                IsTaskTempClearing = False
            End Try
        ElseIf IsTaskTempClearing Then
            '等待另一个清理步骤完成
            Do While IsTaskTempClearing
                Thread.Sleep(1)
            Loop
        End If
    End Sub

    ''' <summary>
    ''' 申请一个可用于任务缓存的临时文件夹，以 \ 结尾。这些文件夹无需进行后续清理。
    ''' 若所有缓存位置均没有权限，会抛出异常。
    ''' </summary>
    ''' <param name="RequireNonSpace">是否要求路径不包含空格。</param>
    Public Function RequestTaskTempFolder(Optional RequireNonSpace As Boolean = False) As String
        TryClearTaskTemp()
        Dim ResultFolder As String
        Try
            ResultFolder = $"{PathTemp}TaskTemp\{GetUuid()}-{RandomInteger(0, 1000000)}\"
            If RequireNonSpace AndAlso ResultFolder.Contains(" ") Then Exit Try '带空格
            DirectoryUtils.Create(ResultFolder)
            CheckPermissionWithException(ResultFolder)
            Return ResultFolder
        Catch ex As Exception
            Logger.Warn(ex, "创建主临时目录失败（改用备用路径）")
        End Try
        '使用备用路径
        ResultFolder = $"{OsDrive}ProgramData\TCL\TaskTemp\{GetUuid()}-{RandomInteger(0, 1000000)}\"
        DirectoryUtils.Create(ResultFolder)
        CheckPermission(ResultFolder)
        Return ResultFolder
    End Function

#End Region

#Region "反馈与遥测"

    '反馈
    Public Sub Feedback(Optional ShowMsgbox As Boolean = True, Optional ForceOpenLog As Boolean = False, Optional Detail As String = "")
        Try
            FeedbackInfo()
        Catch
        End Try
        Try
            If Not String.IsNullOrEmpty(Detail) Then ClipboardSet(Detail)
        Catch
        End Try
        Try
            MyMsgBox(ModLang.L("请加入 QQ 群 1081955336 提交问题。") & vbCrLf &
                     If(String.IsNullOrEmpty(Detail), ModLang.L("你也可以在 设置 → 日志 中查看并复制错误信息。"), ModLang.L("错误信息已复制到剪贴板，可直接粘贴到群内。")),
                     ModLang.L("反馈"), IsWarn:=True)
        Catch
        End Try
    End Sub
    ''' <summary>
    ''' 在日志中输出系统诊断信息。
    ''' </summary>
    Public Sub FeedbackInfo()
        Application.EnsureDelayedUiLoaded() '弹窗依赖延迟字典里的样式（第 112 轮）
        Try
            Logger.Warn($"诊断信息：{vbCrLf}" &
                ModLang.L("操作系统：") & My.Computer.Info.OSFullName & vbCrLf &
                ModLang.L("剩余内存：") & Int(My.Computer.Info.AvailablePhysicalMemory / 1024 / 1024) & " M / " & Int(My.Computer.Info.TotalPhysicalMemory / 1024 / 1024) & " M" & vbCrLf &
                ModLang.L("DPI：") & DPI & ModLang.L("（") & Math.Round(DPI / 96, 2) * 100 & ModLang.L("%）") & vbCrLf &
                ModLang.L("MC 文件夹：") & If(McFolderSelected, "Nothing") & vbCrLf &
                ModLang.L("文件位置：") & Paths.Base)
        Catch
        End Try
    End Sub

    '遥测（匿名数据收集已按用户要求移除：不再向任何远程服务上报）
    Public Sub Telemetry([Event] As String, ParamArray Datas As String())
        Return '匿名数据收集功能已删除，此处为空操作
    End Sub

#End Region

    Public DragControl = Nothing
    Private Timer4Count As Integer = 0
    Private Timer150Count As Integer = 0
    Private Sub TimerMain()
        Try
#Region "每 50ms 执行一次的代码"
            HintTick()
            MyMsgBoxTick()
            FrmMain.DragTick()
            LoaderTaskbarProgressRefresh()
            If ThemeDontClick = 2 Then ThemeRefresh()
#End Region
        Catch ex As Exception
            Logger.Error(ex, "短程主时钟执行异常", LogBehavior.AlertThenCrash)
        End Try
        Timer4Count += 1
        If Timer4Count = 4 Then
            Timer4Count = 0
            Try
#Region "每 250ms 执行一次的代码"
                If ThemeNow = 12 Then ThemeRefresh()
#End Region
            Catch ex As Exception
                Logger.Warn(ex, "中程主时钟执行异常")
            End Try
        End If
        Timer150Count += 1
        If Timer150Count = 150 Then
            Timer150Count = 0
            Try
#Region "每 7.5s 执行一次的代码"
                If FrmMain.BtnExtraApril_ShowCheck AndAlso AprilDifficultiedDistance <> 0 Then FrmMain.BtnExtraApril.Ribble()
                '以未知原因窗口被丢到一边去的修复（Top、Left = -25600），还有 #745
                RunInUi(
                Sub()
                    If Not FrmMain.Hidden Then
                        If FrmMain.Top < -9000 Then FrmMain.Top = 100
                        If FrmMain.Left < -9000 Then FrmMain.Left = 100 '窗口拉至最大时 Left = -18.8
                    End If
                End Sub)
#End Region
            Catch ex As Exception
                Logger.Error(ex, "长程主时钟执行异常", LogBehavior.AlertThenCrash)
            End Try
        End If
    End Sub
    Public Sub TimerMainStart()
        RunInNewThread(
        Sub()
            Try
                Do While True
                    RunInUiWait(AddressOf TimerMain)
                    Thread.Sleep(50 * 0.98)
                Loop
            Catch ex As Exception
                Logger.Error(ex, "程序主时钟出错")
            End Try
        End Sub, "Timer Main")
        If Not IsAprilEnabled Then Return
        RunInNewThread(
        Sub()
            Try
                Dim LastTime = My.Computer.Clock.TickCount
                Do While True
                    If LastTime <> My.Computer.Clock.TickCount Then
                        LastTime = My.Computer.Clock.TickCount
                        RunInUiWait(AddressOf TimerFool)
                    End If
                    '原来是 Sleep(1)：每秒上千次唤醒，只为了比对一下 TickCount，
                    '在 4 月 1 日当天会让一个核持续空转、并阻止 CPU 进入省电状态。
                    '这个彩蛋只需要 60Hz 左右的动作，16ms 足够，开销降到原来的 1/16。
                    Thread.Sleep(16)
                Loop
            Catch ex As Exception
                Logger.Error(ex, "愚人节主时钟出错")
            End Try
        End Sub, "Timer Main Fool")
    End Sub

End Module
