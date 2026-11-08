'轻量 TLS 1.2 客户端（Windows 7 兼容核心）
'
'背景：Win7 的 SChannel 没有 AES-GCM/ChaCha 密码套件（Win8 才加入），而 Cloudflare 系 CDN
'（Modrinth / ForgeCDN / CurseForge / fabricmc / neoforged 等）已全面禁用 CBC 套件——
'系统网络栈（HttpWebRequest / HttpClientHandler / SslStream 全部走 SChannel）在 Win7 上
'与这些主机无法完成 TLS 握手（密码协商阶段直接失败，连服务器证书都拿不到，
'任何证书兜底都无济于事，PCL 官方确认此问题且无解）。
'
'对策：自实现一个只覆盖启动器所需的**最小 TLS 1.2 客户端**——ECDHE secp256r1 密钥交换 +
'AES-128-GCM 记录加密，加密原语全部来自系统 CNG / .NET（AES-GCM、RSA、ECDSA 验签、HMAC），
'协议状态机为本模块手写。Win7 上网络层自动切换到此实现，彻底绕开 SChannel；
'Windows 10/11 不走此路径（保持系统栈）。
'
'安全边界：不启用会话恢复 / 重协商 / 客户端证书；证书校验 = 系统 X509Chain + 内置根证书束
'（ModProxy.ValidateChainWithBundle）锚定 + 域名匹配；Finished 校验完整启用（防握手篡改）；
'单连接单请求（Connection: close）。
'
'HMCL 在 Win7 正常工作的原理相同：Java 的 TLS 栈独立于 SChannel。

Imports System.IO
Imports System.Net
Imports System.Net.Sockets
Imports System.Numerics
Imports System.Security.Cryptography
Imports System.Security.Cryptography.X509Certificates
Imports System.Text

Public Module ModTls

#Region "判定与入口"

    ''' <summary>Win7 模式：系统版本低于 6.2（Win8）时，https 下载走内置 TLS 客户端。</summary>
    Public ReadOnly Win7TlsMode As Boolean = Environment.OSVersion.Version < New Version(6, 2)

    ''' <summary>https GET（Win7 兜底）：返回响应体字节，自动跟随最多 5 次重定向。</summary>
    Public Function TlsGet(Url As String, Optional TimeoutMs As Integer = 30000,
                           Optional Headers As List(Of KeyValuePair(Of String, String)) = Nothing,
                           Optional Depth As Integer = 0) As Byte()
        If Depth > 5 Then Throw New Exception("TLS 请求重定向过深")
        Dim U As New Uri(Url)
        If U.Scheme <> "https" Then Throw New Exception("内置 TLS 客户端仅支持 https")
        Dim Host = U.Host
        Dim Status = 0, Location As String = ""
        Dim Body As Byte() = Nothing
        Using C = TlsConnectAndHandshake(Host, TimeoutMs)
            Dim H As New StringBuilder
            H.Append("GET ").Append(If(U.PathAndQuery = "", "/", U.PathAndQuery)).Append(" HTTP/1.1").Append(vbCrLf)
            H.Append("Host: ").Append(Host).Append(vbCrLf)
            H.Append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 TCraft/").Append(VersionBaseName).Append(vbCrLf)
            H.Append("Accept: */*").Append(vbCrLf)
            H.Append("Connection: close").Append(vbCrLf)
            If Headers IsNot Nothing Then
                For Each Pair In Headers
                    If Pair.Key.Equals("Connection", StringComparison.OrdinalIgnoreCase) OrElse
                       Pair.Key.Equals("Host", StringComparison.OrdinalIgnoreCase) Then Continue For
                    H.Append(Pair.Key).Append(": ").Append(Pair.Value).Append(vbCrLf)
                Next
            End If
            H.Append(vbCrLf)
            SendRecord(C.S, C, 23, Encoding.ASCII.GetBytes(H.ToString))
            '读全部应用数据（Connection: close → 服务器回完即断）
            Dim All = ReadAllRemaining(C.S, C)
            '分离头 / 体：找 \r\n\r\n
            Dim SplitIdx = -1
            For i = 0 To All.Count - 4
                If All(i) = 13 AndAlso All(i + 1) = 10 AndAlso All(i + 2) = 13 AndAlso All(i + 3) = 10 Then
                    SplitIdx = i
                    Exit For
                End If
            Next
            If SplitIdx < 0 Then Throw New Exception("TLS 响应缺少头部")
            Dim HeadText = Encoding.ASCII.GetString(All.GetRange(0, SplitIdx).ToArray)
            Dim BodyPart = All.GetRange(SplitIdx + 4, All.Count - SplitIdx - 4)
            Dim HeadLines = HeadText.Replace(vbCrLf, vbLf).Split(vbLf)
            Dim StatusText = HeadLines(0)
            Dim StatusParts = StatusText.Split(" "c)
            Integer.TryParse(If(StatusParts.Length > 1, StatusParts(1), "0"), Status)
            Dim IsChunked = False
            For i = 1 To HeadLines.Length - 1
                Dim Ln = HeadLines(i)
                If Ln.StartsWithF("Content-Length:", True) Then
                    'Content-Length 模式下读到 EOF 即全部数据（Connection: close）
                ElseIf Ln.StartsWithF("Transfer-Encoding:", True) AndAlso Ln.ToLower.Contains("chunked") Then
                    IsChunked = True
                ElseIf Ln.StartsWithF("Location:", True) Then
                    Location = Ln.Substring(9).Trim
                End If
            Next
            If IsChunked Then
                Body = DecodeChunked(BodyPart)
            Else
                Body = BodyPart.ToArray
            End If
        End Using

        If (Status = 301 OrElse Status = 302 OrElse Status = 303 OrElse Status = 307 OrElse Status = 308) AndAlso
           Not String.IsNullOrEmpty(Location) Then
            Dim NextUrl = Location
            If NextUrl.StartsWithF("/") Then NextUrl = U.Scheme & "://" & U.Host & NextUrl
            Return TlsGet(NextUrl, TimeoutMs, Headers, Depth + 1)
        End If
        If Status < 200 OrElse Status >= 300 Then Throw New Exception($"TLS 请求返回错误码 {Status}（{Url}）")
        Return Body
    End Function

    ''' <summary>https 下载兜底（Win7）：按序尝试地址列表，成功写盘 LocalPath。失败抛异常。</summary>
    Public Sub TlsDownload(Urls As IEnumerable(Of String), LocalPath As String, Optional TimeoutMs As Integer = 30000)
        Dim Last As Exception = Nothing
        For Each Url In Urls
            Try
                Dim Data = TlsGet(Url, TimeoutMs)
                FileUtils.Write(LocalPath, Data)
                Return
            Catch ex As Exception
                Last = ex
            End Try
        Next
        Throw New Exception("TLS 下载失败（全部地址）", Last)
    End Sub

#End Region

#Region "TCP / 记录层"

    Private Class TlsConn
        Implements IDisposable

        Public Tcp As TcpClient
        Public S As NetworkStream

        Public Sub Dispose() Implements IDisposable.Dispose
            Try : Tcp.Close() : Catch : End Try
        End Sub
        Public ClientWriteKey As Byte()
        Public ClientIv As Byte()
        Public ServerWriteKey As Byte()
        Public ServerIv As Byte()
        Public ClientSeq As ULong = 0
        Public ServerSeq As ULong = 0
        Public Encrypted As Boolean = False 'ChangeCipherSpec 之后为 True
        Public HandshakeBuf As New List(Of Byte) 'type22 记录载荷累积
        Public Transcript As New List(Of Byte) '全部握手消息（含 4 字节头）
    End Class

    Private Sub WriteAll(S As NetworkStream, Data As Byte())
        S.Write(Data, 0, Data.Length)
        S.Flush()
    End Sub

    Private Sub ReadExact(S As NetworkStream, Buf As Byte(), Offset As Integer, Count As Integer)
        Dim Got = 0
        Do While Got < Count
            Dim n = S.Read(Buf, Offset + Got, Count - Got)
            If n <= 0 Then Throw New IOException("TLS 连接中断")
            Got += n
        Loop
    End Sub

    ''' <summary>读取并解密当前连接上剩余的全部应用数据（直到 EOF / 关闭通知）。</summary>
    Private Function ReadAllRemaining(S As NetworkStream, C As TlsConn) As List(Of Byte)
        Dim Out As New List(Of Byte)
        Try
            Do
                Dim Rec = ReadRecord(S, C)
                If Rec.Key = 21 Then Exit Do '关闭/警告通知：数据结束
                If Rec.Key <> 23 Then Exit Do
                Out.AddRange(Rec.Value)
            Loop
        Catch ex As IOException
            '对端直接断开（未发 close_notify）：按数据结束处理
        End Try
        Return Out
    End Function

    Private Function ReadRecord(S As NetworkStream, C As TlsConn) As KeyValuePair(Of Integer, Byte())
        Dim Head(4) As Byte
        ReadExact(S, Head, 0, 5)
        Dim Type = Head(0)
        Dim Len = (Head(3) << 8) Or Head(4)
        Dim Payload(Len - 1) As Byte
        ReadExact(S, Payload, 0, Len)
        If C.Encrypted Then
            If Len < 24 Then Throw New Exception("TLS 记录过短")
            'GCM：显式 nonce(8) + 密文 + 标签(16)
            Dim ExplicitNonce = New Byte(7) {}
            Array.Copy(Payload, ExplicitNonce, 8)
            Dim CipherLen = Len - 8
            Dim Cipher(CipherLen - 1) As Byte
            Array.Copy(Payload, 8, Cipher, 0, CipherLen)
            Dim Tag(15) As Byte
            Array.Copy(Payload, 8 + CipherLen, Tag, 0, 16)
            Dim Nonce(11) As Byte
            Array.Copy(C.ServerIv, Nonce, 4)
            Dim SeqBytes = BitConverter.GetBytes(C.ServerSeq)
            Array.Reverse(SeqBytes)
            Array.Copy(SeqBytes, 0, Nonce, 4, 8)
            Dim Aad(12) As Byte
            Array.Copy(SeqBytes, Aad, 8)
            Aad(8) = Type
            Aad(9) = Head(1) : Aad(10) = Head(2)
            Aad(11) = Head(3) : Aad(12) = Head(4)
            Dim Plain = AesGcmDecrypt(C.ServerWriteKey, Nonce, Aad, Cipher, Tag)
            C.ServerSeq += 1UL
            Return New KeyValuePair(Of Integer, Byte())(Type, Plain)
        End If
        C.ServerSeq += 1UL
        Return New KeyValuePair(Of Integer, Byte())(Type, Payload)
    End Function

    Private Sub SendRecord(S As NetworkStream, C As TlsConn, Type As Integer, Payload As Byte())
        Dim Body = Payload
        If C.Encrypted Then
            Dim Nonce(11) As Byte
            Array.Copy(C.ClientIv, Nonce, 4)
            Dim SeqBytes = BitConverter.GetBytes(C.ClientSeq)
            Array.Reverse(SeqBytes)
            Array.Copy(SeqBytes, 0, Nonce, 4, 8)
            Dim Aad(12) As Byte
            Array.Copy(SeqBytes, Aad, 8)
            Aad(8) = CByte(Type)
            Aad(9) = 3 : Aad(10) = 3
            Aad(11) = CByte((Payload.Length + 16) >> 8)
            Aad(12) = CByte((Payload.Length + 16) And 255)
            Body = AesGcmEncrypt(C.ClientWriteKey, Nonce, Aad, Payload) '密文 + 标签(16)
            Dim Rec(Body.Length + 4) As Byte
            Rec(0) = CByte(Type) : Rec(1) = 3 : Rec(2) = 3
            Rec(3) = CByte(Body.Length >> 8) : Rec(4) = CByte(Body.Length And 255)
            Array.Copy(Nonce, 4, Rec, 5, 8) '显式 nonce = 序号
            Array.Copy(Body, 0, Rec, 13, Body.Length)
            WriteAll(S, Rec)
            C.ClientSeq += 1UL
        Else
            Dim Rec(Payload.Length + 4) As Byte
            Rec(0) = CByte(Type) : Rec(1) = 3 : Rec(2) = 3
            Rec(3) = CByte(Payload.Length >> 8) : Rec(4) = CByte(Payload.Length And 255)
            Array.Copy(Payload, 0, Rec, 5, Payload.Length)
            WriteAll(S, Rec)
            C.ClientSeq += 1UL
        End If
    End Sub

#End Region

#Region "握手"

    Private Function TlsConnectAndHandshake(Host As String, TimeoutMs As Integer) As TlsConn
        '自解析 DNS 并优先 IPv4：Win7 无 Happy Eyeballs，.NET 顺序尝试地址会让黑洞的
        'IPv6 连接把每个请求拖到超时（实测主力失败模式之一）
        Dim Addr As IPAddress = Nothing
        Try
            Dim All = Dns.GetHostAddresses(Host)
            Addr = All.FirstOrDefault(Function(a) a.AddressFamily = AddressFamily.InterNetwork)
            If Addr Is Nothing Then Addr = All.FirstOrDefault()
        Catch
        End Try
        Dim Tcp As TcpClient
        If Addr IsNot Nothing Then
            Tcp = New TcpClient
            ' 修复：加回调观察异步连接异常，避免超时后 Close Tcp 导致后台未观察异常
            Dim Ar = Tcp.BeginConnect(Addr, 443, Sub(iar)
                                                     Try
                                                         Tcp.EndConnect(iar)
                                                     Catch
                                                         ' 忽略超时后 Close 导致的异常
                                                     End Try
                                                 End Sub, Nothing)
            If Not Ar.AsyncWaitHandle.WaitOne(TimeoutMs) Then
                ' 超时：直接 Close Tcp，回调里会处理 EndConnect 的异常
                Try : Tcp.Close() : Catch : End Try
                Throw New Exception($"TLS 连接 {Host} 超时（{TimeoutMs} ms）")
            End If
            Try
                Tcp.EndConnect(Ar)
            Catch ex As Exception
                Try : Tcp.Close() : Catch : End Try
                Throw New Exception($"TLS 连接 {Host} 失败：{ex.Message}", ex)
            End Try
        Else
            Tcp = New TcpClient(Host, 443)
        End If
        Tcp.ReceiveTimeout = TimeoutMs
        Tcp.SendTimeout = TimeoutMs

        Dim C As New TlsConn With {.S = Tcp.GetStream()}
        Dim Rnd(31) As Byte
        Using Rng = RandomNumberGenerator.Create()
            Rng.GetBytes(Rnd)
        End Using

        'ClientHello
        Dim Body As New List(Of Byte)
        Body.AddRange({3, 3})
        Body.AddRange(Rnd)
        Body.Add(0) 'session_id 空
        Body.AddRange({0, 2, &HC0, &H2B}) 'cipher_suites：ECDHE_ECDSA_AES128_GCM_SHA256
        Body.AddRange({0, 2, &HC0, &H2F}) 'ECDHE_RSA_AES128_GCM_SHA256
        Body.Add(1) : Body.Add(0) 'compression：null
        Dim Ext As New List(Of Byte)
        Ext.AddRange(ExtBlock(0, ServerNameExt(Host))) 'server_name
        Ext.AddRange(ExtBlock(10, {0, 2, 0, 23})) 'supported_groups：secp256r1
        Ext.AddRange(ExtBlock(11, {1, 0})) 'ec_point_formats：uncompressed
        Ext.AddRange(ExtBlock(13, {0, 8, 4, 3, 4, 1, 5, 3, 6, 1})) 'signature_algorithms
        Dim ExtBytes = Ext.ToArray
        Body.Add(CByte(ExtBytes.Length >> 8)) : Body.Add(CByte(ExtBytes.Length And 255))
        Body.AddRange(ExtBytes)
        Dim Hello = HandshakeMsg(1, Body.ToArray)
        SendRecord(C.S, C, 22, Hello)
        C.Transcript.AddRange(Hello)

        '服务器握手：ServerHello → Certificate → ServerKeyExchange → ServerHelloDone
        Dim ServerRandom(31) As Byte
        Dim ServerCert As X509Certificate2 = Nothing
        Dim ChainCerts As New List(Of X509Certificate2)
        Dim SharedX As Byte() = Nothing
        Dim EcdheD As BigInteger = BigInteger.Zero
        Dim EcdhePoint As Byte() = Nothing
        Dim HandshakeDone As Boolean = False
        Do While Not HandshakeDone
            Dim Rec = ReadRecord(C.S, C)
            If Rec.Key = 21 Then Throw New Exception("TLS 握手被服务器拒绝")
            If Rec.Key <> 22 Then Throw New Exception($"TLS 握手收到意外记录类型 {Rec.Key}")
            C.HandshakeBuf.AddRange(Rec.Value)
            Do While C.HandshakeBuf.Count >= 4
                Dim HsType = C.HandshakeBuf(0)
                Dim HsLen = (C.HandshakeBuf(1) << 16) Or (C.HandshakeBuf(2) << 8) Or C.HandshakeBuf(3)
                If C.HandshakeBuf.Count < 4 + HsLen Then Exit Do
                Dim Msg = C.HandshakeBuf.GetRange(4, HsLen).ToArray
                C.Transcript.AddRange(C.HandshakeBuf.GetRange(0, 4 + HsLen).ToArray)
                C.HandshakeBuf.RemoveRange(0, 4 + HsLen)
                Select Case HsType
                    Case 2 'server_hello
                        If Msg.Length < 35 Then Throw New Exception("TLS ServerHello 过短")
                        Array.Copy(Msg, 2, ServerRandom, 0, 32)
                        Dim SidLen = Msg(34)
                        Dim Pos = 35 + SidLen
                        If Pos + 3 > Msg.Length Then Throw New Exception("TLS ServerHello 过短")
                        Dim Cipher = (Msg(Pos) << 8) Or Msg(Pos + 1)
                        If Cipher <> &HC02B AndAlso Cipher <> &HC02F Then Throw New Exception($"TLS 服务器选择了不支持的套件 {Cipher:X4}")
                    Case 11 'certificate
                        Dim CertLen = (Msg(0) << 16) Or (Msg(1) << 8) Or Msg(2)
                        Dim Pos = 3
                        Do While Pos + 3 <= CertLen + 3
                            Dim OneLen = (Msg(Pos) << 16) Or (Msg(Pos + 1) << 8) Or Msg(Pos + 2)
                            Pos += 3
                            If OneLen <= 0 OrElse Pos + OneLen > 3 + CertLen Then Throw New Exception("TLS 证书消息损坏")
                            Dim CertBytes(OneLen - 1) As Byte
                            Array.Copy(Msg, Pos, CertBytes, 0, OneLen)
                            ChainCerts.Add(New X509Certificate2(CertBytes))
                            Pos += OneLen
                        Loop
                        If ChainCerts.Count = 0 Then Throw New Exception("TLS 服务器未提供证书")
                        ServerCert = ChainCerts(0)
                        ValidateServerChain(ChainCerts, Host)
                    Case 12 'server_key_exchange（ECDHE，secp256r1）
                        If ServerCert Is Nothing Then Throw New Exception("TLS 顺序错误：KeyExchange 先于证书")
                        If EcdheD = BigInteger.Zero Then
                            EcdheD = RandomD()
                            EcdhePoint = EcdhePublicPoint(EcdheD)
                        End If
                        SharedX = ParseServerKeyExchange(Msg, ServerCert, Rnd, ServerRandom, EcdheD)
                    Case 14 'server_hello_done
                        HandshakeDone = True
                End Select
            Loop
        Loop
        If Not HandshakeDone OrElse SharedX Is Nothing OrElse ServerCert Is Nothing Then
            Throw New Exception("TLS 握手不完整")
        End If

        '密钥派生（TLS 1.2，SHA256）
        Dim Master = Prf(PremasterFromX(SharedX), "master secret", Rnd.Concat(ServerRandom), 48)
        Dim KeyBlock = Prf(Master, "key expansion", ServerRandom.Concat(Rnd), 40)
        C.ClientWriteKey = New Byte(15) {}
        C.ServerWriteKey = New Byte(15) {}
        C.ClientIv = New Byte(3) {}
        C.ServerIv = New Byte(3) {}
        Array.Copy(KeyBlock, 0, C.ClientWriteKey, 0, 16)
        Array.Copy(KeyBlock, 16, C.ServerWriteKey, 0, 16)
        Array.Copy(KeyBlock, 32, C.ClientIv, 0, 4)
        Array.Copy(KeyBlock, 36, C.ServerIv, 0, 4)

        'ClientKeyExchange：0x04 || X || Y（65 字节非压缩点）
        Dim KexBody As New List(Of Byte)
        KexBody.AddRange(EcdhePoint)
        Dim Kex = HandshakeMsg(16, KexBody.ToArray)
        SendRecord(C.S, C, 22, Kex)
        C.Transcript.AddRange(Kex)

        'ChangeCipherSpec + Finished（客户端）
        SendRecord(C.S, C, 20, {1})
        C.Encrypted = True
        C.ClientSeq = 0
        Dim VerifyClient = Prf(Master, "client finished", ComputeSha256(C.Transcript.ToArray), 12)
        Dim FinBody = HandshakeMsg(20, VerifyClient)
        SendRecord(C.S, C, 22, FinBody)
        C.Transcript.AddRange(FinBody)

        '服务器 CCS + Finished 校验
        Dim RecCcs = ReadRecord(C.S, C)
        If RecCcs.Key <> 20 OrElse RecCcs.Value(0) <> 1 Then Throw New Exception("TLS 握手：缺少 ChangeCipherSpec")
        C.Encrypted = True
        C.ServerSeq = 0
        Dim RecFin = ReadRecord(C.S, C)
        If RecFin.Key <> 22 OrElse RecFin.Value.Length < 16 OrElse RecFin.Value(0) <> 20 Then Throw New Exception("TLS 握手：缺少 Finished")
        Dim VerifyServer = Prf(Master, "server finished", ComputeSha256(C.Transcript.ToArray), 12)
        For i = 0 To 11
            If RecFin.Value(4 + i) <> VerifyServer(i) Then Throw New Exception("TLS Finished 校验失败")
        Next
        Return C
    End Function

    Private Function HandshakeMsg(Type As Integer, Body As Byte()) As Byte()
        Dim R(Body.Length + 3) As Byte
        R(0) = CByte(Type)
        R(1) = CByte(Body.Length >> 16) : R(2) = CByte((Body.Length >> 8) And 255) : R(3) = CByte(Body.Length And 255)
        Body.CopyTo(R, 4)
        Return R
    End Function

    Private Function ExtBlock(Type As Integer, Body As Byte()) As Byte()
        Dim R(Body.Length + 3) As Byte
        R(0) = CByte(Type >> 8) : R(1) = CByte(Type And 255)
        R(2) = CByte(Body.Length >> 8) : R(3) = CByte(Body.Length And 255)
        Body.CopyTo(R, 4)
        Return R
    End Function

    Private Function ServerNameExt(Host As String) As Byte()
        Dim NameBytes = Encoding.ASCII.GetBytes(Host)
        'VB 数组 Dim X(n) 是 0~n 共 n+1 个元素；要放 type(1) + len(2) + name(N) = 3+N 字节，最大索引是 2+N
        Dim Entry(2 + NameBytes.Length) As Byte
        Entry(0) = 0 'host_name
        Entry(1) = CByte(NameBytes.Length >> 8) : Entry(2) = CByte(NameBytes.Length And 255)
        NameBytes.CopyTo(Entry, 3)
        Dim List(NameBytes.Length + 4) As Byte
        List(0) = CByte((Entry.Length) >> 8) : List(1) = CByte(Entry.Length And 255)
        Entry.CopyTo(List, 2)
        Return List
    End Function

    ''' <summary>premaster（ECDHE）= 共享点的 X 坐标，固定 32 字节大端。</summary>
    Private Function PremasterFromX(SharedX As Byte()) As Byte()
        Return Pad32(SharedX)
    End Function

#End Region

#Region "ServerKeyExchange / 证书校验"

    ''' <summary>解析 ECDHE 参数（仅 secp256r1），校验签名，返回共享秘密的 X 坐标。</summary>
    Private Function ParseServerKeyExchange(Msg As Byte(), ServerCert As X509Certificate2,
                                            ClientRandom As Byte(), ServerRandom As Byte(), EcdheD As BigInteger) As Byte()
        Dim Pos = 0
        If Msg(Pos) <> 3 Then Throw New Exception("TLS KeyExchange：非命名曲线")
        Dim Curve = (Msg(Pos + 1) << 8) Or Msg(Pos + 2)
        If Curve <> 23 Then Throw New Exception($"TLS KeyExchange：不支持的曲线 {Curve}")
        Pos += 3
        Dim PointLen = Msg(Pos) : Pos += 1
        If PointLen <> 65 OrElse Msg(Pos) <> 4 Then Throw New Exception("TLS KeyExchange：非压缩 P-256 点")
        Dim Sx = New Byte(31) {}, Sy = New Byte(31) {}
        Array.Copy(Msg, Pos + 1, Sx, 0, 32)
        Array.Copy(Msg, Pos + 33, Sy, 0, 32)
        Pos += PointLen
        Dim SigLen = (Msg(Pos) << 8) Or Msg(Pos + 1)
        Dim Sig = New Byte(SigLen - 1) {}
        Array.Copy(Msg, Pos + 2, Sig, 0, SigLen)
        Pos += 2 + SigLen
        If Pos <> Msg.Length Then Throw New Exception("TLS KeyExchange：消息长度不匹配")

        '签名覆盖 ClientHello.random + ServerHello.random + 本段全部参数
        Dim Signed As New List(Of Byte)
        Signed.AddRange(ClientRandom)
        Signed.AddRange(ServerRandom)
        Signed.AddRange(Msg.Take(Pos))

        Dim SigAlgByte = Msg(Pos - SigLen - 2)
        If SigAlgByte = 3 Then 'ecdsa_secp256r1_sha256
            Dim Ecdsa = ServerCert.GetECDsaPublicKey()
            If Ecdsa Is Nothing Then Throw New Exception("TLS KeyExchange：证书非 ECDSA 但签名是 ECDSA")
            Dim Qp = Ecdsa.ExportParameters(False)
            If Not EcDsaVerifyP256(Qp.Q.X, Qp.Q.Y, ComputeSha256(Signed.ToArray), Sig) Then
                Throw New Exception("TLS KeyExchange：ECDSA 签名无效")
            End If
        ElseIf SigAlgByte = 1 Then 'rsa_pkcs1_sha256
            Dim Rsa = ServerCert.GetRSAPublicKey()
            If Rsa Is Nothing Then Throw New Exception("TLS KeyExchange：证书非 RSA 但签名是 RSA")
            If Not Rsa.VerifyData(Signed.ToArray, Sig, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1) Then
                Throw New Exception("TLS KeyExchange：RSA 签名无效")
            End If
        Else
            Throw New Exception($"TLS KeyExchange：未知签名算法 {SigAlgByte}")
        End If

        'ECDH：d * Q_server，取 X 坐标（32 字节大端）
        Dim Qx = New Byte(31) {}, Qy = New Byte(31) {}
        Array.Copy(Sx, Qx, 32)
        Array.Copy(Sy, Qy, 32)
        Dim Point = EcMultiplyJacobian(EcdheD, BeToBigInt(Qx), BeToBigInt(Qy))
        Dim ZInv = BigInteger.ModPow(Point(2), P256P - 2, P256P)
        Return Pad32(ToBytesUnsigned((Point(0) * ZInv) Mod P256P))
    End Function

    ''' <summary>系统 X509Chain + 内置根证书束锚定校验（复用 ModProxy 的信任语义）+ 域名匹配。</summary>
    Private Sub ValidateServerChain(ChainCerts As List(Of X509Certificate2), Host As String)
        If Not ModProxy.ValidateChainWithBundle(ChainCerts(0)) Then
            Throw New Exception($"TLS 证书校验失败（{Host}）")
        End If
        Dim Dns = ChainCerts(0).GetNameInfo(X509NameType.DnsName, False)
        Dim Cn = ChainCerts(0).GetNameInfo(X509NameType.SimpleName, False)
        Dim Ok = String.Equals(Dns, Host, StringComparison.OrdinalIgnoreCase) OrElse
                 String.Equals(Cn, Host, StringComparison.OrdinalIgnoreCase) OrElse
                 (Dns IsNot Nothing AndAlso Dns.StartsWithF("*.") AndAlso Host.EndsWith(Dns.Substring(1), StringComparison.OrdinalIgnoreCase)) OrElse
                 (Cn IsNot Nothing AndAlso Cn.StartsWithF("*.") AndAlso Host.EndsWith(Cn.Substring(1), StringComparison.OrdinalIgnoreCase))
        'GetNameInfo 在多 SAN 时返回斜杠合并串，逐段再查
        If Not Ok AndAlso Dns IsNot Nothing Then
            For Each Piece In Dns.Split("/"c)
                If Piece = Host OrElse (Piece.StartsWithF("*.") AndAlso Host.EndsWith(Piece.Substring(1), StringComparison.OrdinalIgnoreCase)) Then
                    Ok = True
                    Exit For
                End If
            Next
        End If
        If Not Ok Then Throw New Exception($"TLS 证书域名不匹配（{Host}）")
    End Sub

#End Region

#Region "P-256 椭圆曲线（BigInteger 实现，Jacobian 坐标）"

    Private ReadOnly P256P As BigInteger = BeToBigInt(New Byte() {
        &HFF, &HFF, &HFF, &HFF, &H0, &H0, &H0, &H1, &H0, &H0, &H0, &H0, &H0, &H0, &H0, &H0,
        &H0, &H0, &H0, &H0, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF})
    Private ReadOnly P256N As BigInteger = BeToBigInt(New Byte() {
        &HFF, &HFF, &HFF, &HFF, &H0, &H0, &H0, &H0, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF, &HFF,
        &HBC, &HE6, &HFA, &HAD, &HA7, &H17, &H9E, &H84, &HF3, &HB9, &HCA, &HC2, &HFC, &H63, &H25, &H51})
    Private ReadOnly P256Gx As BigInteger = BeToBigInt(New Byte() {
        &H6B, &H17, &HD1, &HF2, &HE1, &H2C, &H42, &H47, &HF8, &HBC, &HE6, &HE5, &H63, &HA4, &H40, &HF2,
        &H77, &H3, &H7D, &H81, &H2D, &HEB, &H33, &HA0, &HF4, &HA1, &H39, &H45, &HD8, &H98, &HC2, &H96})
    Private ReadOnly P256Gy As BigInteger = BeToBigInt(New Byte() {
        &H4F, &HE3, &H42, &HE2, &HFE, &H1A, &H7F, &H9B, &H8E, &HE7, &HEB, &H4A, &H7C, &HF, &H9E, &H16,
        &H2B, &HCE, &H33, &H57, &H6B, &H31, &H5E, &HCE, &HCB, &HB6, &H40, &H68, &H37, &HBF, &H51, &HF5})

    Private Function BeToBigInt(BeBytes As Byte()) As BigInteger
        Dim Lit = CType(BeBytes.Clone, Byte())
        Array.Reverse(Lit)
        Dim R(Lit.Length) As Byte '多一位补符号位 0 → 正数
        Array.Copy(Lit, 0, R, 1, Lit.Length)
        Return New BigInteger(R)
    End Function

    Private Function ToBytesUnsigned(V As BigInteger) As Byte()
        Dim B = V.ToByteArray() '小端 + 符号位
        Dim End2 = B.Length - 1
        Do While End2 > 0 AndAlso B(End2) = 0
            End2 -= 1
        Loop
        Dim R(End2) As Byte
        For i = 0 To End2
            R(i) = B(End2 - i)
        Next
        Return R
    End Function

    Private Function RandomD() As BigInteger
        Dim D(31) As Byte
        Using Rng = RandomNumberGenerator.Create()
            Rng.GetBytes(D)
        End Using
        Return (BeToBigInt(D) Mod (P256N - 2)) + 1
    End Function

    ''' <summary>生成临时密钥对，返回非压缩公钥点（0x04 || X || Y，65 字节）。</summary>
    Private Function EcdhePublicPoint(D As BigInteger) As Byte()
        Dim Q = EcMultiplyJacobian(D, P256Gx, P256Gy)
        Dim ZInv = BigInteger.ModPow(Q(2), P256P - 2, P256P)
        Dim X = Pad32(ToBytesUnsigned((Q(0) * ZInv) Mod P256P))
        Dim Y = Pad32(ToBytesUnsigned((Q(1) * ZInv) Mod P256P))
        Dim R(64) As Byte
        R(0) = 4
        X.CopyTo(R, 1)
        Y.CopyTo(R, 33)
        Return R
    End Function

    ''' <summary>仿射坐标乘标量：返回 Jacobian {X, Y, Z}。</summary>
    Private Function EcMultiplyJacobian(K As BigInteger, Gx As BigInteger, Gy As BigInteger) As List(Of BigInteger)
        Dim P = P256P
        Dim Kb = ToBytesUnsigned(K)
        Dim X As BigInteger = 0, Y As BigInteger = 0, Z As BigInteger = 0 '无穷远点 Z=0
        For Each ByteV In Kb
            Dim Mask = &H80
            Do While Mask > 0
                If Z = 0 Then
                    If (ByteV And Mask) <> 0 Then X = Gx : Y = Gy : Z = 1
                Else
                    'Jacobian double（一般式）
                    Dim A = (X * X) Mod P
                    Dim B = (Y * Y) Mod P
                    Dim C = (B * B) Mod P
                    Dim D0 = ((X + B) * (X + B) - A - C) Mod P
                    Dim D = (2 * D0) Mod P : If D < 0 Then D += P
                    Dim E = (3 * A) Mod P
                    Dim F = (E * E) Mod P
                    Dim Nx = (F - 2 * D) Mod P : If Nx < 0 Then Nx += P
                    Dim Ny = (E * (D - Nx) - 8 * C) Mod P : If Ny < 0 Then Ny += P
                    Dim Nz = (2 * Y * Z) Mod P
                    X = Nx : Y = Ny : Z = Nz
                    If (ByteV And Mask) <> 0 Then
                        'Jacobian + 仿射（Z2=1）混合加法
                        Dim U1 = X
                        Dim S1 = Y
                        Dim U2 = Gx
                        Dim S2 = Gy
                        Dim H = (U2 - U1) Mod P : If H < 0 Then H += P
                        Dim R2 = (S2 - S1) Mod P : If R2 < 0 Then R2 += P
                        If H = 0 Then
                            If R2 = 0 Then
                                '重合 → double
                                Dim A2 = (X * X) Mod P
                                Dim B2 = (Y * Y) Mod P
                                Dim C2 = (B2 * B2) Mod P
                                Dim D02 = ((X + B2) * (X + B2) - A2 - C2) Mod P
                                Dim D2 = (2 * D02) Mod P : If D2 < 0 Then D2 += P
                                Dim E2 = (3 * A2) Mod P
                                Dim F2 = (E2 * E2) Mod P
                                Dim Nx2 = (F2 - 2 * D2) Mod P : If Nx2 < 0 Then Nx2 += P
                                Dim Ny2 = (E2 * (D2 - Nx2) - 8 * C2) Mod P : If Ny2 < 0 Then Ny2 += P
                                Dim Nz2 = (2 * Y * Z) Mod P
                                X = Nx2 : Y = Ny2 : Z = Nz2
                            Else
                                X = 0 : Y = 0 : Z = 0 '无穷远
                            End If
                        Else
                            Dim H2 = (H * H) Mod P
                            Dim H3 = (H2 * H) Mod P
                            Dim U1H2 = (U1 * H2) Mod P
                            Dim Jx = (R2 * R2 - H3 - 2 * U1H2) Mod P : If Jx < 0 Then Jx += P
                            Dim Jy = (R2 * (U1H2 - Jx) - S1 * H3) Mod P : If Jy < 0 Then Jy += P
                            Dim Jz = (Z * H) Mod P
                            X = Jx : Y = Jy : Z = Jz
                        End If
                    End If
                End If
                Mask >>= 1
            Loop
        Next
        If Z <> 0 Then
            Dim ZInv = BigInteger.ModPow(Z, P256P - 2, P256P)
            Return New List(Of BigInteger) From {(X * ZInv) Mod P256P, (Y * ZInv) Mod P256P, 1}
        End If
        Return New List(Of BigInteger) From {0, 0, 0}
    End Function

    ''' <summary>P-256 ECDSA 验签（手动椭圆曲线运算）。</summary>
    Private Function EcDsaVerifyP256(Qx As Byte(), Qy As Byte(), MsgHash As Byte(), SigDer As Byte()) As Boolean
        Try
            '解析 DER ECDSA-Sig-Value：SEQUENCE { r INTEGER, s INTEGER }
            Dim Pos = 0
            If SigDer(Pos) <> &H30 Then Return False
            Pos += 1
            If (SigDer(Pos) And &H80) <> 0 Then Pos += (SigDer(Pos) And &H7F)
            Pos += 1
            If SigDer(Pos) <> 2 Then Return False
            Dim RLen = SigDer(Pos + 1)
            Dim Rb = New Byte(RLen - 1) {}
            Array.Copy(SigDer, Pos + 2, Rb, 0, RLen)
            Pos += 2 + RLen
            If SigDer(Pos) <> 2 Then Return False
            Dim SLen = SigDer(Pos + 1)
            Dim Sb = New Byte(SLen - 1) {}
            Array.Copy(SigDer, Pos + 2, Sb, 0, SLen)
            Dim R = BeToBigInt(Rb), S = BeToBigInt(Sb)
            If R <= 0 OrElse S >= P256N Then Return False
            Dim E = BeToBigInt(MsgHash)
            Dim W = BigInteger.ModPow(S, P256N - 2, P256N)
            Dim U1 = (E * W) Mod P256N
            Dim U2 = (R * W) Mod P256N
            Dim P1 = EcMultiplyJacobian(U1, P256Gx, P256Gy)
            Dim P2 = EcMultiplyJacobian(U2, BeToBigInt(Qx), BeToBigInt(Qy))
            Dim P = P256P
            Dim U1v = (P1(0) * P2(2) * P2(2)) Mod P
            Dim S1v = (P1(1) * P2(2) * P2(2) * P2(2)) Mod P
            Dim U2v = (P2(0) * P1(2) * P1(2)) Mod P
            Dim S2v = (P2(1) * P1(2) * P1(2) * P1(2)) Mod P
            Dim H = (U2v - U1v) Mod P : If H < 0 Then H += P
            Dim Rr = (S2v - S1v) Mod P : If Rr < 0 Then Rr += P
            If H = 0 Then Return False
            Dim H2 = (H * H) Mod P
            Dim H3 = (H2 * H) Mod P
            Dim U1H2 = (U1v * H2) Mod P
            Dim X3 = (Rr * Rr - H3 - 2 * U1H2) Mod P : If X3 < 0 Then X3 += P
            Dim Z3 = (P1(2) * P2(2)) Mod P
            Dim ZInv = BigInteger.ModPow(Z3, P - 2, P)
            Dim XAff = (X3 * ZInv) Mod P
            Return (XAff Mod P256N) = R
        Catch
            Return False
        End Try
    End Function

#End Region

#Region "TLS 1.2 密钥派生（PRF，SHA256）"

    Private Function Prf(Secret As Byte(), Label As String, Seed As Byte(), Length As Integer) As Byte()
        Dim LabelSeed = Encoding.ASCII.GetBytes(Label).Concat(Seed).ToArray
        Dim Out As New List(Of Byte)
        Dim A = HmacSha256(Secret, LabelSeed)
        Do While Out.Count < Length
            Out.AddRange(HmacSha256(Secret, A.Concat(LabelSeed).ToArray))
            A = HmacSha256(Secret, A)
        Loop
        Return Out.Take(Length).ToArray
    End Function

    Private Function HmacSha256(Key As Byte(), Data As Byte()) As Byte()
        Using H As New HMACSHA256(Key)
            Return H.ComputeHash(Data)
        End Using
    End Function

    Private Function ComputeSha256(Data As Byte()) As Byte()
        Using H As System.Security.Cryptography.SHA256 = System.Security.Cryptography.SHA256.Create()
            Return H.ComputeHash(Data)
        End Using
    End Function

    Private Function Pad32(BeBytes As Byte()) As Byte()
        Dim R(31) As Byte
        Dim Start = Math.Max(0, BeBytes.Length - 32)
        Array.Copy(BeBytes, Start, R, 32 - (BeBytes.Length - Start), BeBytes.Length - Start)
        Return R
    End Function

#End Region

#Region "AES-128-GCM（系统 CNG：bcrypt.dll，Win7 原生支持）"

    <Runtime.InteropServices.DllImport("bcrypt.dll")>
    Private Function BCryptOpenAlgorithmProvider(ByRef PhAlgorithm As IntPtr, <Runtime.InteropServices.MarshalAs(Runtime.InteropServices.UnmanagedType.LPWStr)> PszAlgId As String, <Runtime.InteropServices.MarshalAs(Runtime.InteropServices.UnmanagedType.LPWStr)> PszImplementation As String, DwFlags As UInteger) As Integer
    End Function
    <Runtime.InteropServices.DllImport("bcrypt.dll")>
    Private Function BCryptSetProperty(hObject As IntPtr, <Runtime.InteropServices.MarshalAs(Runtime.InteropServices.UnmanagedType.LPWStr)> PszPropName As String, PbInput As IntPtr, CbInput As UInteger, DwFlags As UInteger) As Integer
    End Function
    <Runtime.InteropServices.DllImport("bcrypt.dll")>
    Private Function BCryptGenerateSymmetricKey(hAlgorithm As IntPtr, ByRef PhKey As IntPtr, PbKeyObject As IntPtr, CbKeyObject As Integer, PbInput As IntPtr, CbInput As Integer, DwFlags As UInteger) As Integer
    End Function
    <Runtime.InteropServices.DllImport("bcrypt.dll")>
    Private Function BCryptDecrypt(hKey As IntPtr, PbInput As IntPtr, CbInput As Integer, PPadInfo As IntPtr, PbIV As IntPtr, CbIV As Integer, PbOutput As IntPtr, CbOutput As Integer, ByRef CbResult As Integer, DwFlags As UInteger) As Integer
    End Function
    <Runtime.InteropServices.DllImport("bcrypt.dll")>
    Private Function BCryptEncrypt(hKey As IntPtr, PbInput As IntPtr, CbInput As Integer, PPadInfo As IntPtr, PbIV As IntPtr, CbIV As Integer, PbOutput As IntPtr, CbOutput As Integer, ByRef CbResult As Integer, DwFlags As UInteger) As Integer
    End Function
    <Runtime.InteropServices.DllImport("bcrypt.dll")>
    Private Function BCryptDestroyKey(hKey As IntPtr) As Integer
    End Function
    <Runtime.InteropServices.DllImport("bcrypt.dll")>
    Private Function BCryptCloseAlgorithmProvider(hAlgorithm As IntPtr, DwFlags As UInteger) As Integer
    End Function

    Private Const STATUS_AUTH_TAG_MISMATCH As Integer = &HC000A003

    <Runtime.InteropServices.StructLayout(Runtime.InteropServices.LayoutKind.Sequential)>
    Private Structure AuthInfo
        Public CbSize As Integer
        Public DwInfoVersion As Integer
        Public PbNonce As IntPtr
        Public CbNonce As Integer
        Public PbAuthData As IntPtr
        Public CbAuthData As Integer
        Public PbTag As IntPtr
        Public CbTag As Integer
        Public PbContext As IntPtr
        Public CbContext As Integer
        Public DwFlags As Integer
    End Structure

    ''' <summary>加密：返回 密文 || 标签(16)。</summary>
    Private Function AesGcmEncrypt(Key As Byte(), Nonce As Byte(), Aad As Byte(), Plain As Byte()) As Byte()
        Dim Tag(15) As Byte
        Dim Cipher = AesGcmCore(True, Key, Nonce, Aad, Plain, Tag)
        Dim R(Cipher.Length + 15) As Byte
        Cipher.CopyTo(R, 0)
        Tag.CopyTo(R, Cipher.Length)
        Return R
    End Function

    ''' <summary>解密：Data = 密文，Tag = 标签(16)。标签不符抛异常。</summary>
    Private Function AesGcmDecrypt(Key As Byte(), Nonce As Byte(), Aad As Byte(), Data As Byte(), Tag As Byte()) As Byte()
        Return AesGcmCore(False, Key, Nonce, Aad, Data, Tag)
    End Function

    Private Function AesGcmCore(Encrypt As Boolean, Key As Byte(), Nonce As Byte(), Aad As Byte(), Data As Byte(), Tag As Byte()) As Byte()
        Dim H As IntPtr = IntPtr.Zero, HKey As IntPtr = IntPtr.Zero
        Dim PNonce = Runtime.InteropServices.Marshal.AllocHGlobal(12)
        Dim PAad = Runtime.InteropServices.Marshal.AllocHGlobal(Math.Max(1, Aad.Length))
        Dim PTag = Runtime.InteropServices.Marshal.AllocHGlobal(16)
        Dim PInfo = Runtime.InteropServices.Marshal.AllocHGlobal(Runtime.InteropServices.Marshal.SizeOf(Of AuthInfo)())
        Dim DataPin = Runtime.InteropServices.GCHandle.Alloc(Data, Runtime.InteropServices.GCHandleType.Pinned)
        Dim TagPin = Runtime.InteropServices.GCHandle.Alloc(Tag, Runtime.InteropServices.GCHandleType.Pinned)
        Try
            If BCryptOpenAlgorithmProvider(H, "AES", Nothing, 0) <> 0 Then Throw New Exception("CNG 打开 AES 失败")
            Dim ChainMode = Encoding.Unicode.GetBytes("ChainingModeGCM" & vbNullChar)
            Dim ChainPin = Runtime.InteropServices.GCHandle.Alloc(ChainMode, Runtime.InteropServices.GCHandleType.Pinned)
            Try
                If BCryptSetProperty(H, "ChainingMode", Runtime.InteropServices.Marshal.UnsafeAddrOfPinnedArrayElement(ChainMode, 0), ChainMode.Length, 0) <> 0 Then
                    Throw New Exception("CNG 设置 GCM 模式失败")
                End If
            Finally
                ChainPin.Free()
            End Try
            Dim KeyPin = Runtime.InteropServices.GCHandle.Alloc(Key, Runtime.InteropServices.GCHandleType.Pinned)
            Try
                If BCryptGenerateSymmetricKey(H, HKey, IntPtr.Zero, 0,
                    Runtime.InteropServices.Marshal.UnsafeAddrOfPinnedArrayElement(Key, 0), Key.Length, 0) <> 0 Then
                    Throw New Exception("CNG 生成密钥失败")
                End If
            Finally
                KeyPin.Free()
            End Try
            Runtime.InteropServices.Marshal.Copy(Nonce, 0, PNonce, 12)
            If Aad.Length > 0 Then Runtime.InteropServices.Marshal.Copy(Aad, 0, PAad, Aad.Length)
            Dim Info As New AuthInfo With {
                .CbSize = Runtime.InteropServices.Marshal.SizeOf(Of AuthInfo)(), .DwInfoVersion = 1,
                .PbNonce = PNonce, .CbNonce = 12,
                .PbAuthData = If(Aad.Length > 0, PAad, IntPtr.Zero), .CbAuthData = Aad.Length,
                .PbTag = PTag, .CbTag = 16, .PbContext = IntPtr.Zero, .CbContext = 0, .DwFlags = 0}
            Runtime.InteropServices.Marshal.StructureToPtr(Info, PInfo, False)
            Dim Result = New Byte(If(Encrypt, Data.Length + 16, Math.Max(1, Data.Length - 16)) - 1) {}
            Dim OutLen = 0
            Dim InAddr = Runtime.InteropServices.Marshal.UnsafeAddrOfPinnedArrayElement(Data, 0)
            Dim OutPin = Runtime.InteropServices.GCHandle.Alloc(Result, Runtime.InteropServices.GCHandleType.Pinned)
            Try
                Dim St As Integer
                If Encrypt Then
                    St = BCryptEncrypt(HKey, InAddr, Data.Length, PInfo, IntPtr.Zero, 0,
                        Runtime.InteropServices.Marshal.UnsafeAddrOfPinnedArrayElement(Result, 0), Result.Length, OutLen, 0)
                Else
                    St = BCryptDecrypt(HKey, InAddr, Data.Length, PInfo, IntPtr.Zero, 0,
                        Runtime.InteropServices.Marshal.UnsafeAddrOfPinnedArrayElement(Result, 0), Result.Length, OutLen, 0)
                End If
                If St = STATUS_AUTH_TAG_MISMATCH Then Throw New Exception("TLS GCM 标签校验失败（数据被篡改或密钥不匹配）")
                If St <> 0 Then Throw New Exception($"CNG 失败：0x{St:X8}")
                If OutLen > Result.Length Then Throw New Exception("CNG 输出长度异常")
                If Encrypt Then
                    Runtime.InteropServices.Marshal.Copy(PTag, Tag, 0, 16)
                    Dim Full = New Byte(OutLen + 16 - 1) {}
                    Array.Copy(Result, Full, OutLen)
                    Array.Copy(Tag, 0, Full, OutLen, 16)
                    Return Full
                End If
                If OutLen <> Result.Length Then Array.Resize(Result, OutLen)
                Return Result
            Finally
                OutPin.Free()
            End Try
        Finally
            TagPin.Free()
            DataPin.Free()
            If HKey <> IntPtr.Zero Then BCryptDestroyKey(HKey)
            If H <> IntPtr.Zero Then BCryptCloseAlgorithmProvider(H, 0)
            Runtime.InteropServices.Marshal.FreeHGlobal(PNonce)
            Runtime.InteropServices.Marshal.FreeHGlobal(PAad)
            Runtime.InteropServices.Marshal.FreeHGlobal(PTag)
            Runtime.InteropServices.Marshal.FreeHGlobal(PInfo)
        End Try
    End Function

#End Region

#Region "分块响应解码"

    ''' <summary>解码 Transfer-Encoding: chunked 的响应体。</summary>
    Private Function DecodeChunked(Data As List(Of Byte)) As Byte()
        Dim Out As New List(Of Byte)
        Dim Pos = 0
        Do
            Dim Line As New List(Of Byte)
            Do While Pos < Data.Count AndAlso Data(Pos) <> 13
                Line.Add(Data(Pos))
                Pos += 1
            Loop
            Pos += 2 '跳过 \r\n
            Dim Hex = Encoding.ASCII.GetString(Line.ToArray).Split(";"c)(0).Trim
            Dim ChunkLen As Integer
            Try
                ChunkLen = Convert.ToInt32(Hex, 16)
            Catch
                Exit Do
            End Try
            If ChunkLen = 0 Then Exit Do
            If Pos + ChunkLen > Data.Count Then Exit Do
            Out.AddRange(Data.GetRange(Pos, ChunkLen))
            Pos += ChunkLen + 2 '数据 + \r\n
        Loop
        Return Out.ToArray
    End Function

#End Region

End Module
