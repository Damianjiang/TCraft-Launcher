Imports System.Windows.Forms

Public Class PageLoginLegacy

    Public Sub New()
        InitializeComponent()
        Skin.Loader = PageLaunchLeft.SkinLegacy
    End Sub
    Private Sub PageLoginLegacy_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        Skin.Loader.Start()
    End Sub

    Public IsReloaded As Boolean = False
    ''' <summary>
    ''' 刷新页面显示的所有信息。
    ''' </summary>
    Public Sub Reload(KeepInput As Boolean)
        If KeepInput AndAlso IsReloaded Then '避免第一次就以 KeepInput 的方式加载，导致文本框里没东西
            '保留输入，只刷新下拉框列表
            Dim Input As String = ComboName.Text.Trim
            ComboName.ItemsSource = If(Settings.Get(Of String)("LoginLegacyName") = "", Nothing, Settings.Get(Of String)("LoginLegacyName").Split("¨"))
            ComboName.Text = Input
        Else
            '不保留输入，刷新列表后自动选择第一项
            If Settings.Get(Of String)("LoginLegacyName") = "" Then
                ComboName.ItemsSource = Nothing
            Else
                ComboName.ItemsSource = Settings.Get(Of String)("LoginLegacyName").ToString.Split("¨")
                ComboName.Text = Settings.Get(Of String)("LoginLegacyName").ToString.BeforeFirst("¨").Trim
            End If
        End If
        IsReloaded = True
    End Sub
    ''' <summary>
    ''' 获取当前页面的登录信息。
    ''' </summary>
    Public Shared Function GetLoginData() As McLoginData
        Dim UserName As String = If(FrmLoginLegacy Is Nothing, "", FrmLoginLegacy.ComboName.Text.Replace("¨", "").Trim)
        'UUID 一律由系统按用户名自动生成（McLoginLegacyUuid 规则），不提供自定义入口
        '第 71 轮：皮肤配置按离线账户独立读取（ModLegacySkin），不再读全局 LaunchSkinType
        Dim Config = ModLegacySkin.GetConfig(UserName)
        Return New McLoginLegacy With {.UserName = UserName, .SkinType = Config.Type, .SkinName = Config.Id, .SkinSlim = Config.Slim, .SkinFile = Config.File}
    End Function
    ''' <summary>
    ''' 当前页面的登录信息是否有效。
    ''' </summary>
    Public Shared Function IsVaild(LoginData As McLoginLegacy) As String
        If LoginData.UserName.Trim = "" Then Return "玩家名不能为空！"
        If LoginData.UserName.Contains("""") Then Return "玩家名不能包含英文引号！"
        If McInstanceSelected IsNot Nothing AndAlso
           McInstanceSelected.Version.Valid AndAlso McInstanceSelected.Version.Vanilla >= New Version(20, 0, 3) AndAlso
           LoginData.UserName.Trim.Length > 16 Then
            Return "自 1.20.3 起，玩家名至多只能包含 16 个字符！"
        End If
        Return ""
    End Function
    Public Function IsVaild() As String
        Return IsVaild(GetLoginData())
    End Function

    Private Sub ComboName_PreviewKeyDown(sender As Object, e As Input.KeyEventArgs) Handles ComboName.PreviewKeyDown
        If e.Key = Key.Space Then
            Dim Index = CType(ComboName.Template.FindName("PART_EditableTextBox", ComboName), MyTextBox).CaretIndex
            If Index = ComboName.Text.Length OrElse Index = 0 Then e.Handled = True
        End If
    End Sub
    Private Sub ComboLegacy_TextChanged(sender As Object, e As TextChangedEventArgs) Handles ComboName.TextChanged
        '玩家名变化：皮肤配置指纹（含玩家名）随之变化，由 ShouldStart 自动判断是否重载
        PageLaunchLeft.SkinLegacy.Start()
        HintChinese.Visibility = If(ComboName.Text.RegexCheck("^[0-9A-Za-z_]*$"), Visibility.Collapsed, Visibility.Visible)
        RunInUi(Sub() PanMain.InvalidateMeasure(), True) '由于 WPF 的 Bug，它不一定会自动更新 HintChinese 的大小
    End Sub
    ''' <summary>
    ''' 输入框失焦（提交）时持久化当前合法离线玩家名，避免逐键保存把 "S/St/Ste" 等中间片段塞进历史。
    ''' </summary>
    Private Sub ComboName_LostFocus(sender As Object, e As RoutedEventArgs) Handles ComboName.LostFocus
        PersistLegacyName()
    End Sub
    Private Sub Skin_Click() Handles Skin.Click
        If (Settings.Get(Of Boolean)("UiHiddenPageSetup") OrElse Settings.Get(Of Boolean)("UiHiddenSetupLaunch")) AndAlso Not PageSetupUI.HiddenForceShow Then
            Hint(ModLang.L("启动设置已被禁用！"), HintType.Red)
        Else
            FrmMain.PageChange(FormMain.PageType.Setup, FormMain.PageSubType.SetupLaunch) '切换到皮肤设置页面
        End If
    End Sub

    ''' <summary>
    ''' 切走/关闭离线登录页时，把当前输入的合法玩家名持久化进历史，
    ''' 这样下次打开启动器仍能记住上次填的名字（此前只有"成功启动离线游戏"才存，导致总是忘记）。
    ''' </summary>
    Private Sub PageLoginLegacy_Unloaded(sender As Object, e As RoutedEventArgs) Handles Me.Unloaded
        PersistLegacyName()
    End Sub
    ''' <summary>
    ''' 把当前输入框里的合法离线玩家名持久化进历史（去重、上限 10 条）。
    ''' 供「切走离线登录页 / 失焦 / 直接点启动」等入口统一调用，确保玩家名不会丢。
    ''' </summary>
    Public Sub PersistLegacyName()
        Try
            Dim Name As String = If(ComboName Is Nothing, "", ComboName.Text).Replace("¨", "").Trim
            If Name = "" Then Return
            If Not Name.RegexCheck("^[0-9A-Za-z_]+$") Then Return '与界面提示一致：合法离线名只含字母/数字/下划线
            Dim Cur As String = Settings.Get(Of String)("LoginLegacyName")
            '重建去重列表：当前名置顶，旧历史去重保留，上限 10 条
            Dim Names As New List(Of String)
            Names.Add(Name)
            If Cur <> "" Then
                For Each n In Cur.Split("¨"c)
                    Dim t = n.Trim
                    If t <> "" AndAlso t <> Name AndAlso Not Names.Contains(t) Then Names.Add(t)
                Next
            End If
            If Names.Count > 10 Then Names.RemoveRange(10, Names.Count - 10)
            Dim NewVal As String = Names.Join("¨"c)
            If NewVal <> Cur Then Settings.Set("LoginLegacyName", NewVal)
        Catch ex As Exception
            Logger.Warn(ex, "保存离线玩家名失败")
        End Try
    End Sub

End Class
