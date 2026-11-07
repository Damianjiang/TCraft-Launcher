Imports X509 = System.Security.Cryptography.X509Certificates
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
    ''' <summary>当前环境是否可用 HTTP/2（Win10 1607+ 且非 SOCKS5）。请求版本据此选择，
    ''' 避免 Win7/8 的 HttpClientHandler 收到 Version=2.0 请求时行为未定义。</summary>
    Public ReadOnly Property Http2Available As Boolean
        Get
            Return Environment.OSVersion.Version >= New Version(10, 0, 14393) AndAlso GetMode() <> ModeSocks5
        End Get
    End Property

    ''' <summary>
    ''' 创建已套好代理的 HTTP 消息处理器（新代码统一用这个）。
    ''' Windows 10 1607+ 用 WinHttpHandler：HTTP/2 多路复用 + TLS 1.3，几十个下载线程共用少量连接
    ''' （对齐 PCL-CE（net10）SocketsHttpHandler 的连接行为），省掉每线程独立的 TCP+TLS 握手与慢启动开销；
    ''' HTTP/1.1-only 的源由 ALPN 自动回退、明文 http:// 自动回退，行为不变。
    ''' Win7/8 的 WinHTTP 不支持 HTTP/2 且默认未启用 TLS 1.2，回退 HttpClientHandler
    ''' （TLS 版本由 .NET 4.8 自行管理，启动同步段已显式开启 TLS 1.2），保证 Win7 完整兼容；
    ''' SOCKS5 同样走 HttpClientHandler（WinHTTP 不支持 socks 方案，维持原栈的尽力而为行为）。
    ''' </summary>
    Public Function CreateHandler() As HttpMessageHandler
        If Http2Available Then 'Win10 1607+ 才有支持 HTTP/2 的 WinHTTP
            Try
                Return ApplyWinHttp(New WinHttpHandler With {
                    .AutomaticDecompression = DecompressionMethods.Deflate Or DecompressionMethods.GZip,
                    .ServerCertificateValidationCallback = AddressOf ValidateServerCertificate,
                    .CookieUsePolicy = CookieUsePolicy.IgnoreCookies, '不存 Cookie，从 Header 手动传入（等价原 UseCookies=False）
                    .SendTimeout = TimeSpan.FromSeconds(30),
                    .ReceiveHeadersTimeout = TimeSpan.FromSeconds(60),
                    .ReceiveDataTimeout = TimeSpan.FromSeconds(60),
                    .TcpKeepAliveEnabled = True, 'TCP 保活：跨文件/分片复用池内连接，避免空闲被掐后重新握手
                    .EnableMultipleHttp2Connections = True '单条 h2 连接的流配额不足时自动开新连接，64 并发不排队
                })
            Catch ex As Exception
                Logger.Warn(ex, "创建 WinHttpHandler 失败，回退 HttpClientHandler")
            End Try
        End If
        Return Apply(New HttpClientHandler With {
            .AutomaticDecompression = DecompressionMethods.Deflate Or DecompressionMethods.GZip,
            .ServerCertificateCustomValidationCallback = AddressOf ValidateServerCertificate,
            .UseCookies = False
        })
    End Function

    '────────────────────────────────────────────────────────────────────────────────────
    '证书兜底（Win7 兼容）：Windows 7 已停止支持，系统根证书库冻结，现代 CDN（Let's Encrypt /
    'Google Trust Services 等）签发的证书链在 Win7 上验证失败，表现为所有 HTTPS 请求「未能为 SSL/TLS
    '安全通道建立信任关系」（PCL 官方确认的 Win7 下载问题即此，PCL 只能建议用户升级系统）。
    '对策：内嵌当前 Mozilla CA 根证书束（与 curl 同源，随版本更新），OS 验证失败时改用内置束重新验证——
    '链必须能锚定到内置自签根且域名匹配，安全语义与标准 PKI 一致，只是信任锚更新鲜。

    ''' <summary>内置 CA 根证书束（Mozilla 列表，随构建更新），首次验证失败时惰性加载。</summary>
    Private ReadOnly BundledRoots As New Lazy(Of List(Of X509.X509Certificate2))(
        Function()
            Try
                Dim Raw = ModGzip.Unwrap(DirectCast(My.Resources.ResourceManager.GetObject("CACertBundle"), Byte()))
                Dim Text = System.Text.Encoding.ASCII.GetString(Raw)
                Dim List As New List(Of X509.X509Certificate2)
                For Each Block In Text.Split("-----END CERTIFICATE-----")
                    Dim StartPos = Block.IndexOf("-----BEGIN CERTIFICATE-----")
                    If StartPos < 0 Then Continue For
                    Dim B64 = Block.Substring(StartPos + Len("-----BEGIN CERTIFICATE-----")).Replace(vbCr, "").Replace(vbLf, "").Trim
                    Try
                        Dim Cert As New X509.X509Certificate2(Convert.FromBase64String(B64))
                        If Cert.Subject = Cert.Issuer Then List.Add(Cert) '根 CA 均为自签
                    Catch
                    End Try
                Next
                Logger.Info($"内置 CA 根证书束加载完成：{List.Count} 个根")
                Return List
            Catch ex As Exception
                Logger.Warn(ex, "加载内置 CA 证书束失败（证书兜底将不可用）")
                Return New List(Of X509.X509Certificate2)
            End Try
        End Function, LazyThreadSafetyMode.ExecutionAndPublication)

    ''' <summary>内置根的自签指纹集合（快速比对用）。</summary>
    Private ReadOnly BundledRootThumbprints As New Lazy(Of HashSet(Of String))(
        Function()
            Dim Thumbs As New HashSet(Of String)(StringComparer.OrdinalIgnoreCase)
            For Each Cert In BundledRoots.Value
                Thumbs.Add(Cert.Thumbprint)
            Next
            Return Thumbs
        End Function, LazyThreadSafetyMode.ExecutionAndPublication)

    ''' <summary>服务器证书验证：先信 OS 结果；OS 验证失败（Win7 根库冻结等）改用内置 CA 束重验。
    ''' 重验要求：链能锚定到内置自签根、无时间/签名有效性错误、叶子证书域名与请求主机匹配。</summary>
    Public Function ValidateServerCertificate(Request As Net.Http.HttpRequestMessage, Cert As X509.X509Certificate2,
                                                     Chain As X509.X509Chain, Errors As Net.Security.SslPolicyErrors) As Boolean
        If Errors = Net.Security.SslPolicyErrors.None Then Return True 'OS 验证通过（Win10 常态）
        Try
            If Cert Is Nothing OrElse Request Is Nothing Then Return False
            Dim Roots = BundledRoots.Value
            If Roots.Count = 0 Then Return False
            Dim Chain2 As New X509.X509Chain
            Chain2.ChainPolicy.RevocationMode = X509.X509RevocationMode.NoCheck
            Chain2.ChainPolicy.ExtraStore.AddRange(Roots.ToArray())
            Chain2.Build(Cert) '返回值只代表有无状态错误，链结构以 ChainElements 为准
            If Chain2.ChainElements.Count < 2 Then Return False
            For Each Element In Chain2.ChainElements
                For Each Status In Element.ChainElementStatus
                    If Status.Status = X509.X509ChainStatusFlags.NotTimeValid OrElse
                       Status.Status = X509.X509ChainStatusFlags.NotSignatureValid Then Return False
                Next
            Next
            Dim Last = Chain2.ChainElements(Chain2.ChainElements.Count - 1).Certificate
            If Not (Last.Subject = Last.Issuer AndAlso BundledRootThumbprints.Value.Contains(Last.Thumbprint)) Then Return False
            '域名匹配：由 SChannel 在 OS 层校验并反映在 Errors 标志里（RemoteCertificateNameMismatch），
            '此处只兜「信任锚」问题——域名不匹配无论根是否可信都绝不放行
            If Errors.HasFlag(Net.Security.SslPolicyErrors.RemoteCertificateNameMismatch) Then Return False
            Return True
        Catch ex As Exception
            Logger.Warn(ex, "内置证书束验证失败")
            Return False
        End Try
    End Function

    ''' <summary>给 WinHttpHandler 套上代理。WinHTTP 不读 WebRequest.DefaultWebProxy，必须显式指定。
    ''' 经过 <see cref="WinHttpProxyAdapter"/> 适配直连语义并保留「改设置即时生效」。</summary>
    Private Function ApplyWinHttp(Handler As WinHttpHandler) As WinHttpHandler
        Handler.WindowsProxyUsePolicy = WindowsProxyUsePolicy.UseCustomProxy
        Handler.Proxy = New WinHttpProxyAdapter()
        Return Handler
    End Function

    ''' <summary>
    ''' 把 HttpProxyManager 的 IWebProxy 语义适配成 WinHttpHandler 期望的语义。
    ''' WinHttpHandler（源码 line 1292）：IsBypassed 为 True 或 GetProxy 返回 Nothing = 直连；
    ''' 返回目标地址本身会被当成「连接到目标地址的 NAMED_PROXY」（WebProxy 惯例里这是直连标记），
    ''' 必须显式转成 Nothing，否则无代理环境下所有请求都会炸。
    ''' 每次调用实时透传 HttpProxyManager（动态 IWebProxy），改代理设置即时生效。
    ''' </summary>
    Private Class WinHttpProxyAdapter
        Implements IWebProxy

        Public Property Credentials As ICredentials Implements IWebProxy.Credentials
            Get
                Return HttpProxyManager.Instance.Credentials
            End Get
            Set(value As ICredentials)
                HttpProxyManager.Instance.Credentials = value
            End Set
        End Property

        Public Function GetProxy(destination As Uri) As Uri Implements IWebProxy.GetProxy
            Dim Result As Uri = HttpProxyManager.Instance.GetProxy(destination)
            If Result Is Nothing OrElse Result.Equals(destination) Then Return Nothing '直连
            Return Result
        End Function

        Public Function IsBypassed(destination As Uri) As Boolean Implements IWebProxy.IsBypassed
            Return HttpProxyManager.Instance.IsBypassed(destination)
        End Function
    End Class

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
