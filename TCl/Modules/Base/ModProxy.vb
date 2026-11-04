''' <summary>
''' 启动器网络代理的统一入口。
'''
''' 设置项沿用项目里已有的 4 个键（Settings.Entries 已注册，改动会自动触发 ModNet.UpdateHttpProxy
''' 刷新全局 HttpProxyManager），不新增设置项：
'''   SystemHttpProxyType            0=不使用 1=跟随系统 2=自定义（HTTP 与 SOCKS5 都存 2）
'''   SystemHttpProxy                自定义地址，例如 127.0.0.1:7890 或 socks5://127.0.0.1:1080
'''   SystemHttpProxyCustomUsername  可选账号
'''   SystemHttpProxyCustomPassword  可选密码
'''
''' 对外模式值：0=不使用 1=跟随系统 2=HTTP 3=SOCKS5。
''' SOCKS5 会把地址存成 socks5://host:port 并仍然按「自定义代理」写入设置，
''' 以便 HttpProxyManager / 游戏启动参数继续正常工作；真实支持程度见 GetProxy() 的注释。
''' </summary>
Public Module ModProxy

    ''' <summary>不使用代理。</summary>
    Public Const ModeNone As Integer = 0
    ''' <summary>跟随 Windows 系统代理。</summary>
    Public Const ModeSystem As Integer = 1
    ''' <summary>自定义 HTTP 代理。</summary>
    Public Const ModeHttp As Integer = 2
    ''' <summary>自定义 SOCKS5 代理（.NET Framework 4.8 上为尽力而为）。</summary>
    Public Const ModeSocks5 As Integer = 3

    Private Const KeyMode As String = "SystemHttpProxyType"
    Private Const KeyAddress As String = "SystemHttpProxy"
    Private Const KeyUserName As String = "SystemHttpProxyCustomUsername"
    Private Const KeyPassword As String = "SystemHttpProxyCustomPassword"

#Region "读取设置"

    ''' <summary>当前代理模式：0=不使用 1=跟随系统 2=HTTP 3=SOCKS5。</summary>
    Public Function GetMode() As Integer
        Dim Raw As Integer = 1
        Try
            Raw = CInt(Settings.Get(Of Integer)(KeyMode))
        Catch ex As Exception
            Logger.Warn(ex, "读取代理模式失败，已回退为跟随系统代理")
            Return ModeSystem
        End Try
        Select Case Raw
            Case 0
                Return ModeNone
            Case 1
                Return ModeSystem
            Case Else
                If GetAddress().TrimStart().StartsWith("socks", StringComparison.OrdinalIgnoreCase) Then Return ModeSocks5
                Return ModeHttp
        End Select
    End Function

    ''' <summary>设置里保存的代理地址原文（可能带 socks5:// 前缀，可能为空）。</summary>
    Public Function GetAddress() As String
        Try
            Return If(Settings.Get(Of String)(KeyAddress), "").Trim()
        Catch ex As Exception
            Logger.Warn(ex, "读取代理地址失败")
            Return ""
        End Try
    End Function

    ''' <summary>解析后的主机名，未配置时为空字符串。</summary>
    Public Function GetHostText() As String
        Dim Scheme As String = "", HostName As String = ""
        Dim Port As Integer = 0
        ParseAddress(Scheme, HostName, Port)
        Return HostName
    End Function

    ''' <summary>解析后的端口，未配置时为 0。</summary>
    Public Function GetPort() As Integer
        Dim Scheme As String = "", HostName As String = ""
        Dim Port As Integer = 0
        ParseAddress(Scheme, HostName, Port)
        Return Port
    End Function

    Public Function GetUserName() As String
        Try
            Return If(Settings.Get(Of String)(KeyUserName), "")
        Catch ex As Exception
            Logger.Warn(ex, "读取代理账号失败")
            Return ""
        End Try
    End Function

    Public Function GetPassword() As String
        Try
            Return If(Settings.Get(Of String)(KeyPassword), "")
        Catch ex As Exception
            Logger.Warn(ex, "读取代理密码失败")
            Return ""
        End Try
    End Function

    ''' <summary>
    ''' 把设置里的地址拆成 协议 / 主机 / 端口。支持 127.0.0.1:7890、http://host:port、
    ''' socks5://host:port，以及省略端口时的默认值（HTTP 8080、SOCKS 1080）。
    ''' </summary>
    Private Function ParseAddress(ByRef Scheme As String, ByRef HostName As String, ByRef Port As Integer) As Boolean
        Scheme = "http"
        HostName = ""
        Port = 0
        Try
            Dim Text As String = GetAddress()
            If Text.Length = 0 Then Return False
            Dim SchemeEnd As Integer = Text.IndexOf("://", StringComparison.Ordinal)
            If SchemeEnd >= 0 Then
                Scheme = Text.Substring(0, SchemeEnd).ToLowerInvariant()
                Text = Text.Substring(SchemeEnd + 3)
            End If
            Dim Slash As Integer = Text.IndexOf("/"c)
            If Slash >= 0 Then Text = Text.Substring(0, Slash)
            Dim At As Integer = Text.LastIndexOf("@"c)
            If At >= 0 Then Text = Text.Substring(At + 1)
            Dim Colon As Integer = Text.LastIndexOf(":"c)
            If Colon > 0 Then
                HostName = Text.Substring(0, Colon)
                Integer.TryParse(Text.Substring(Colon + 1), Port)
            Else
                HostName = Text
                Port = If(Scheme.StartsWith("socks", StringComparison.OrdinalIgnoreCase), 1080, 8080)
            End If
            If HostName.StartsWith("["c) AndAlso HostName.EndsWith("]"c) AndAlso HostName.Length > 2 Then
                HostName = HostName.Substring(1, HostName.Length - 2)
            End If
            Return HostName.Length > 0 AndAlso Port > 0 AndAlso Port <= 65535
        Catch ex As Exception
            Logger.Warn(ex, "解析代理地址失败")
            Return False
        End Try
    End Function

#End Region

#Region "构造代理"

    ''' <summary>
    ''' 按当前设置构造代理对象；不需要或构造失败时返回 Nothing。
    '''
    ''' 返回 Nothing 的两种情况含义不同：
    '''   · 模式 0（不使用代理）：调用方必须同时设置 Handler.UseProxy = False。
    '''   · 模式 1（跟随系统）：调用方保持 UseProxy = True 且 Proxy = Nothing，
    '''     HttpClient 会自动使用 Windows 系统代理，正是需要的行为。
    '''
    ''' SOCKS5 说明：.NET Framework 4.8 的 WebProxy 只实现了 HTTP 代理（CONNECT）语义，
    ''' 没有 SOCKS5 握手实现，因此这里只能把地址标记成 socks5:// 交给底层。
    ''' 结局是：Clash / v2rayN 这类「混合端口」通常恰好可用，纯 SOCKS 端口会失败。
    ''' 界面必须提示用户优先用 HTTP 端口或系统代理，不能假装完整支持。
    ''' </summary>
    Public Function GetProxy() As IWebProxy
        Dim CurMode As Integer = GetMode()
        If CurMode = ModeNone OrElse CurMode = ModeSystem Then Return Nothing
        Try
            Dim Scheme As String = "", HostName As String = ""
            Dim Port As Integer = 0
            If Not ParseAddress(Scheme, HostName, Port) Then
                Logger.Warn("代理地址无效，已回退为不使用代理")
                Return Nothing
            End If
            Dim Result As WebProxy
            If CurMode = ModeSocks5 Then
                Result = New WebProxy(New Uri("socks5://" & HostName & ":" & Port))
            Else
                Result = New WebProxy(HostName, Port)
            End If
            Dim UserName As String = GetUserName()
            If UserName.Length > 0 Then
                Result.Credentials = New NetworkCredential(UserName, GetPassword())
            End If
            Return Result
        Catch ex As Exception
            Logger.Warn(ex, "构造代理失败，已回退为不使用代理")
            Return Nothing
        End Try
    End Function

    ''' <summary>当前设置是否启用了代理（跟随系统也算启用，即使系统实际没有配置代理）。</summary>
    Public Function IsEnabled() As Boolean
        Return GetMode() <> ModeNone
    End Function

    ''' <summary>用于界面显示：不使用代理 / 跟随系统代理 / HTTP 127.0.0.1:7890。</summary>
    Public Function Describe() As String
        Dim Scheme As String = "", HostName As String = ""
        Dim Port As Integer = 0
        Select Case GetMode()
            Case ModeNone
                Return ModLang.L("不使用代理")
            Case ModeSystem
                Return ModLang.L("跟随系统代理")
            Case ModeSocks5
                If ParseAddress(Scheme, HostName, Port) Then Return "SOCKS5 " & HostName & ":" & Port
                Return ModLang.L("SOCKS5（未填写地址）")
            Case Else
                If ParseAddress(Scheme, HostName, Port) Then Return "HTTP " & HostName & ":" & Port
                Return ModLang.L("HTTP（未填写地址）")
        End Select
    End Function

    ''' <summary>给 HttpClientHandler 套上当前代理。返回传入的 handler，方便链式调用。</summary>
    Public Function Apply(Handler As HttpClientHandler) As HttpClientHandler
        If Handler Is Nothing Then Return Nothing
        Try
            If GetMode() = ModeNone Then
                Handler.UseProxy = False
                Handler.Proxy = Nothing
            Else
                Handler.UseProxy = True
                Handler.Proxy = GetProxy() 'Nothing 表示走系统代理
            End If
        Catch ex As Exception
            Logger.Warn(ex, "为 HttpClientHandler 套用代理失败，已回退为直连")
            Handler.UseProxy = False
            Handler.Proxy = Nothing
        End Try
        Return Handler
    End Function

    ''' <summary>创建一个已经套好代理的 HttpClientHandler。新代码统一用这个。</summary>
    Public Function CreateHandler() As HttpClientHandler
        Return Apply(New HttpClientHandler With {
            .AutomaticDecompression = DecompressionMethods.Deflate Or DecompressionMethods.GZip,
            .UseCookies = False
        })
    End Function

#End Region

#Region "保存"

    ''' <summary>
    ''' 写入代理设置。Mode 取 0~3；Mode 为 2 / 3 时必须提供有效的 Host 与 Port。
    ''' 保存后立即刷新全局 HttpProxyManager，无需重启启动器。
    ''' </summary>
    Public Sub Save(Mode As Integer, Host As String, Port As Integer, User As String, Password As String)
        If Mode < ModeNone OrElse Mode > ModeSocks5 Then Throw New ArgumentOutOfRangeException("Mode")
        Host = If(Host, "").Trim()
        User = If(User, "")
        Password = If(Password, "")
        Select Case Mode
            Case ModeNone
                Settings.Set(KeyMode, 0)
            Case ModeSystem
                Settings.Set(KeyMode, 1)
            Case Else
                '允许用户直接把 http:// 前缀粘进主机框
                Dim SchemeEnd As Integer = Host.IndexOf("://", StringComparison.Ordinal)
                If SchemeEnd >= 0 Then Host = Host.Substring(SchemeEnd + 3)
                If Host.Length = 0 Then Throw New ArgumentException(ModLang.L("代理主机不能为空"))
                If Port < 1 OrElse Port > 65535 Then Throw New ArgumentOutOfRangeException("Port")
                Dim Address As String = Host & ":" & Port
                If Mode = ModeSocks5 AndAlso Not Host.StartsWith("socks", StringComparison.OrdinalIgnoreCase) Then
                    Address = "socks5://" & Address
                End If
                '先写地址与凭据，最后写模式，保证 OnChanged 触发 UpdateHttpProxy 时数据已就绪
                Settings.Set(KeyAddress, Address)
                Settings.Set(KeyUserName, User)
                Settings.Set(KeyPassword, Password)
                Settings.Set(KeyMode, 2)
        End Select
        '值没变化时 Settings.Set 会提前返回、不触发 OnChanged，这里补一次刷新
        ForceRefresh()
    End Sub

    ''' <summary>立即让全局 HttpProxyManager 按最新设置重建代理。</summary>
    Private Sub ForceRefresh()
        Try
            ModNet.UpdateHttpProxy()
        Catch ex As Exception
            Logger.Warn(ex, "保存代理设置后刷新全局代理失败")
        End Try
    End Sub

#End Region

End Module
