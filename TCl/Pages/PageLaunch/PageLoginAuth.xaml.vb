Public Class PageLoginAuth
    Private IsFirstLoad As Boolean = True
    ''' <summary>
    ''' 刷新页面显示的所有信息。
    ''' </summary>
    Public Sub Reload(KeepInput As Boolean)
        '记住密码
        CheckRemember.Checked = Settings.Get(Of Boolean)("LoginRemember")
        If KeepInput AndAlso Not IsFirstLoad Then '避免第一次就以 KeepInput 的方式加载，导致文本框里没东西
            '保留输入，只刷新下拉框列表
            Dim Input As String = ComboName.Text
            ComboName.ItemsSource = If(Settings.Get(Of String)("LoginAuthEmail") = "", Nothing, Settings.Get(Of String)("LoginAuthEmail").Split("¨"))
            ComboName.Text = Input
        Else
            '不保留输入，刷新列表后自动选择第一项
            If Settings.Get(Of String)("LoginAuthEmail") = "" Then
                ComboName.ItemsSource = Nothing
            Else
                ComboName.ItemsSource = Settings.Get(Of String)("LoginAuthEmail").Split("¨")
                ComboName.Text = Settings.Get(Of String)("LoginAuthEmail").BeforeFirst("¨")
                If Settings.Get(Of Boolean)("LoginRemember") Then TextPass.Password = Settings.Get(Of String)("LoginAuthPass").BeforeFirst("¨").Trim
            End If
        End If
        IsFirstLoad = False
    End Sub
    ''' <summary>
    ''' 获取当前页面的登录信息。
    ''' </summary>
    Public Shared Function GetLoginData() As McLoginServer
        Dim Server As String = If(IsNothing(McInstanceSelected), Settings.Get(Of String)("CacheAuthServerServer"), Settings.Get(Of String)("VersionServerAuthServer", Instance:=McInstanceSelected)) & "/authserver"
        ' 优先读登录表单；表单为空时（如从账户管理页「选择账号」直接启动）回退到全局缓存凭据，
        ' 否则 IsVaild 会因账号名为空报「账号不能为空」导致启动失败（第 75 轮修复）
        Dim UserName As String, Pass As String
        If FrmLoginAuth IsNot Nothing AndAlso Not FrmLoginAuth.ComboName.Text.Replace("¨", "").Trim = "" Then
            UserName = FrmLoginAuth.ComboName.Text.Replace("¨", "").Trim
            Pass = FrmLoginAuth.TextPass.Password.Replace("¨", "").Trim
            '未勾选「记住密码」时表单密码框为空，但凭据缓存里有（账户管理页选择账号时写入）——回退，否则启动报「密码不能为空」
            If Pass = "" Then Pass = Settings.Get(Of String)("CacheAuthPass")
        Else
            UserName = Settings.Get(Of String)("CacheAuthUsername")
            Pass = Settings.Get(Of String)("CacheAuthPass")
        End If
        Return New McLoginServer(McLoginType.Auth) With {.Token = "Auth", .BaseUrl = Server, .UserName = UserName, .Password = Pass, .Description = "Authlib-Injector", .Type = McLoginType.Auth}
    End Function
    ''' <summary>
    ''' 当前页面的登录信息是否有效。
    ''' </summary>
    Public Shared Function IsVaild(LoginData As McLoginServer) As String
        If LoginData.UserName = "" Then Return "账号不能为空！"
        If LoginData.Password = "" Then Return "密码不能为空！"
        Return ""
    End Function
    Public Function IsVaild() As String
        Return IsVaild(GetLoginData())
    End Function

    '保存输入信息
    Private Sub ComboName_TextChanged(sender As Object, e As TextChangedEventArgs) Handles ComboName.TextChanged
        If sender.Text = "" Then TextPass.Password = ""
        If AniControlEnabled = 0 Then Settings.Set("CacheAuthAccess", "")  '迫使其不进行 Validate
    End Sub
    Private Sub TextPass_PasswordChanged(sender As Object, e As RoutedEventArgs) Handles TextPass.PasswordChanged
        If AniControlEnabled = 0 Then Settings.Set("CacheAuthAccess", "")
    End Sub
    Private Sub ComboName_SelectionChanged(sender As MyComboBox, e As SelectionChangedEventArgs) Handles ComboName.SelectionChanged
        If sender.SelectedIndex = -1 OrElse Not Settings.Get(Of Boolean)("LoginRemember") Then
            TextPass.Password = ""
        Else
            TextPass.Password = Settings.Get(Of String)("LoginAuthPass").ToString.Split("¨")(sender.SelectedIndex).Trim
        End If
    End Sub
    Private Sub CheckBoxChange(sender As MyCheckBox, e As Object) Handles CheckRemember.Change
        If AniControlEnabled = 0 Then Settings.Set(sender.Tag, sender.Checked)
    End Sub

    'LittleSkin 预设：一键把当前版本的第三方登录服务器设为 LittleSkin（对齐 HMCL 预置 LittleSkin）
    Private Const LITTLE_SKIN_SERVER As String = "https://littleskin.cn/api/yggdrasil"
    Private Const LITTLE_SKIN_REGISTER As String = "https://littleskin.cn/auth/register"
    Private Sub BtnAuthPreset_Click(sender As Object, e As EventArgs) Handles BtnAuthPreset.Click
        Try
            Dim Target = McInstanceSelected
            If Target Is Nothing Then
                Hint(ModLang.L("请先下载游戏，再设置 LittleSkin 登录！"), HintType.Red)
                Return
            End If
            '当前认证服务器若不是 LittleSkin，提示将覆盖
            Dim Cur = Settings.Get(Of String)("VersionServerAuthServer", Instance:=Target)
            If Cur <> "" AndAlso Cur <> LITTLE_SKIN_SERVER Then
                If MyMsgBox(ModLang.L("即将把第三方登录设置覆盖为 LittleSkin 登录。") & vbCrLf & ModLang.L("除非你是服主，或者服主要求你这样做，否则请不要继续。") & vbCrLf & vbCrLf & ModLang.L("是否确实需要覆盖当前设置？"),
                             ModLang.L("设置覆盖确认"), ModLang.L("继续"), ModLang.L("取消")) = 2 Then Return
            End If
            '第三方登录需选「Authlib Injector 或 LittleSkin」(index 4)
            Settings.Set("VersionServerLogin", 4, Instance:=Target)
            Settings.Set("VersionServerAuthServer", LITTLE_SKIN_SERVER, Instance:=Target)
            Settings.Set("VersionServerAuthRegister", LITTLE_SKIN_REGISTER, Instance:=Target)
            Settings.Set("VersionServerAuthName", ModLang.L("LittleSkin 登录"), Instance:=Target)
            '同步到缓存，让登录页立刻可用
            Settings.Set("CacheAuthServerServer", LITTLE_SKIN_SERVER)
            Settings.Set("CacheAuthServerRegister", LITTLE_SKIN_REGISTER)
            Settings.Set("CacheAuthServerName", ModLang.L("LittleSkin 登录"))
            Settings.Set("CacheAuthAccess", "") '服务器改变，要求重新登录
            Hint(ModLang.L("已设为 LittleSkin 登录，请输入你的 LittleSkin 账号与密码。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "设置 LittleSkin 登录预设失败")
            MyMsgBox(ModLang.L("设置 LittleSkin 登录失败：") & ex.GetDisplay(False), ModLang.L("LittleSkin 登录"), IsWarn:=True)
        End Try
    End Sub

    '链接处理
    Private Sub ComboName_TextChanged() Handles ComboName.TextChanged
        BtnLink.Content = If(ComboName.Text = "", ModLang.L("注册账号"), ModLang.L("找回密码"))
    End Sub
    Private Sub Btn_Click(sender As Object, e As EventArgs) Handles BtnLink.Click
        If BtnLink.Content = ModLang.L("注册账号") Then
            OpenWebsite(If(McInstanceSelected IsNot Nothing, Settings.Get(Of String)("VersionServerAuthRegister", Instance:=McInstanceSelected), Settings.Get(Of String)("CacheAuthServerRegister")))
        Else
            Dim Website As String = If(McInstanceSelected IsNot Nothing, Settings.Get(Of String)("VersionServerAuthRegister", Instance:=McInstanceSelected), Settings.Get(Of String)("CacheAuthServerRegister"))
            OpenWebsite(Website.Replace("/auth/register", "/auth/forgot"))
        End If
    End Sub
    '切换注册按钮可见性
    Private Sub ReloadRegisterButton() Handles Me.Loaded
        Dim Address As String = If(McInstanceSelected IsNot Nothing, Settings.Get(Of String)("VersionServerAuthRegister", Instance:=McInstanceSelected), Settings.Get(Of String)("CacheAuthServerRegister"))
        BtnLink.Visibility = If(String.IsNullOrEmpty(New ValidateHttp().Validate(Address)), Visibility.Visible, Visibility.Collapsed)
    End Sub

End Class
