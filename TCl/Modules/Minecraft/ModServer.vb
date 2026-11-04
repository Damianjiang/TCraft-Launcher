Imports System.IO
Imports System.IO.Compression
Imports System.Net.Sockets
Imports System.Text
Imports Newtonsoft.Json.Linq

''' <summary>
''' 服务器列表（servers.dat）读写与服务器状态查询。
''' </summary>
Public Module ModServer

    Public Class McServerEntry
        Public Name As String = ""
        Public Ip As String = ""
        Public Icon As String = ""
    End Class

    Public Class McServerPingResult
        Public Online As Boolean = False
        Public Players As Integer = 0
        Public MaxPlayers As Integer = 0
        Public Motd As String = ""
        Public Version As String = ""
        Public Latency As Integer = -1
        Public ErrorMessage As String = ""
    End Class

#Region "NBT 读写"

    ''' <summary>第 130 轮：读取复用 ModWorldData.NbtReader（删除本文件原先的 118 行重复实现），写入仍用下方 NbtWriter。</summary>

    Private Class NbtWriter
        Private ReadOnly S As Stream

        Public Sub New(Stream As Stream)
            S = Stream
        End Sub

        Public Sub WriteByte(Value As Byte)
            S.WriteByte(Value)
        End Sub

        Public Sub WriteInt16(Value As Short)
            S.WriteByte(CByte((CInt(Value) >> 8) And &HFF))
            S.WriteByte(CByte(CInt(Value) And &HFF))
        End Sub

        Public Sub WriteInt32(Value As Integer)
            S.WriteByte(CByte((Value >> 24) And &HFF))
            S.WriteByte(CByte((Value >> 16) And &HFF))
            S.WriteByte(CByte((Value >> 8) And &HFF))
            S.WriteByte(CByte(Value And &HFF))
        End Sub

        Public Sub WriteString(Value As String)
            Dim Buffer = Encoding.UTF8.GetBytes(If(Value, ""))
            WriteInt16(CShort(Buffer.Length))
            S.Write(Buffer, 0, Buffer.Length)
        End Sub
    End Class

    Private Function OpenNbtStream(FilePath As String, ForWrite As Boolean) As Stream
        If ForWrite Then
            Dim FileStream As New FileStream(FilePath, FileMode.Create, FileAccess.Write)
            Return New GZipStream(FileStream, CompressionMode.Compress)
        End If
        Dim Raw As New FileStream(FilePath, FileMode.Open, FileAccess.Read)
        Dim Magic(1) As Byte
        Raw.Read(Magic, 0, 2)
        Raw.Position = 0
        If Magic(0) = &H1F AndAlso Magic(1) = &H8B Then Return New GZipStream(Raw, CompressionMode.Decompress)
        Return Raw
    End Function

    Public Function McServerListRead(PathIndie As String) As List(Of McServerEntry)
        Dim Result As New List(Of McServerEntry)
        Dim FilePath = Path.Combine(PathIndie, "servers.dat")
        If Not File.Exists(FilePath) Then Return Result
        Using Stream = OpenNbtStream(FilePath, False)
            Dim Reader As New ModWorldData.NbtReader(Stream)
            '与 ModWorldData 一致：先读根类型与根名，再读 Compound 负载（ReadPayload 不含类型与名称）
            Dim RootType As Byte = Reader.ReadByteValue()
            If RootType <> 10 Then Throw New Exception("servers.dat 的根标签不是 Compound")
            Reader.ReadString()
            Dim Root As NbtTag = Reader.ReadPayload(10)
            Dim ServersTag = Root?.GetList("servers")
            If ServersTag Is Nothing Then Return Result
            For Each Server As NbtTag In ServersTag.Children
                If Server Is Nothing OrElse Server.Type <> 10 Then Continue For
                Result.Add(New McServerEntry With {
                    .Name = Server.GetString("name"),
                    .Ip = Server.GetString("ip"),
                    .Icon = Server.GetString("icon")
                })
            Next
        End Using
        Return Result
    End Function

    Public Sub McServerListWrite(PathIndie As String, Servers As IEnumerable(Of McServerEntry))
        Dim FilePath = Path.Combine(PathIndie, "servers.dat")
        Using Stream = OpenNbtStream(FilePath, True)
            Dim Writer As New NbtWriter(Stream)
            Writer.WriteByte(10) 'Root Compound
            Writer.WriteString("")
            Writer.WriteByte(9) 'List
            Writer.WriteString("servers")
            Writer.WriteByte(10) 'Element: Compound
            Dim List = Servers.ToList()
            Writer.WriteInt32(List.Count)
            For Each Server In List
                Writer.WriteByte(8) : Writer.WriteString("name") : Writer.WriteString(Server.Name)
                Writer.WriteByte(8) : Writer.WriteString("ip") : Writer.WriteString(Server.Ip)
                If Not String.IsNullOrEmpty(Server.Icon) Then
                    Writer.WriteByte(8) : Writer.WriteString("icon") : Writer.WriteString(Server.Icon)
                End If
                Writer.WriteByte(0)
            Next
            Writer.WriteByte(0) 'End Root
        End Using
    End Sub

#End Region

#Region "服务器状态查询"

    Private Sub WriteVarInt(Stream As Stream, Value As Integer)
        Dim Unsigned = CUInt(Value)
        Do
            Dim Temp = CByte(Unsigned And &H7FUI)
            Unsigned >>= 7
            If Unsigned <> 0 Then Temp = Temp Or &H80
            Stream.WriteByte(Temp)
        Loop While Unsigned <> 0
    End Sub

    Private Function ReadVarInt(Stream As Stream) As Integer
        Dim Result = 0
        Dim Shift = 0
        Do
            Dim Current = Stream.ReadByte()
            If Current < 0 Then Throw New EndOfStreamException("读取 VarInt 失败")
            Result = Result Or ((Current And &H7F) << Shift)
            Shift += 7
            If (Current And &H80) = 0 Then Exit Do
            If Shift > 35 Then Throw New Exception("VarInt 过长")
        Loop
        Return Result
    End Function

    Private Sub WriteMcString(Stream As Stream, Value As String)
        Dim Buffer = Encoding.UTF8.GetBytes(Value)
        WriteVarInt(Stream, Buffer.Length)
        Stream.Write(Buffer, 0, Buffer.Length)
    End Sub

    Private Function ReadMcString(Stream As Stream) As String
        Dim Length = ReadVarInt(Stream)
        If Length <= 0 Then Return ""
        '服务器返回的数据不可信：长度异常时直接失败，别让 New Byte(Length-1) 把内存吃光
        If Length > 1048576 Then Throw New Exception($"服务器返回的字符串长度异常：{Length}")
        Dim Buffer(Length - 1) As Byte
        Dim Read = 0
        While Read < Length
            Dim Count = Stream.Read(Buffer, Read, Length - Read)
            If Count <= 0 Then Throw New EndOfStreamException("读取字符串失败")
            Read += Count
        End While
        Return Encoding.UTF8.GetString(Buffer)
    End Function

    Private Sub SendPacket(Stream As Stream, Payload As Byte())
        WriteVarInt(Stream, Payload.Length)
        Stream.Write(Payload, 0, Payload.Length)
        Stream.Flush()
    End Sub

    Private Function MotdFromJson(Description As JToken) As String
        If Description Is Nothing Then Return ""
        If Description.Type = JTokenType.String Then Return Description.ToString()
        Dim Text = If(Description("text") IsNot Nothing, Description("text").ToString(), "")
        Dim Extra = Description("extra")
        If Extra IsNot Nothing AndAlso Extra.Type = JTokenType.Array Then
            For Each Part In Extra
                Text &= MotdFromJson(Part)
            Next
        End If
        Return Text
    End Function

    ''' <summary>
    ''' 查询 Minecraft 服务器的状态。地址支持 host 或 host:port。
    ''' </summary>
    Public Function McServerPing(Address As String, Optional Timeout As Integer = 3000) As McServerPingResult
        Dim Result As New McServerPingResult
        Dim Host = Address.Trim()
        Dim Port = 25565
        If Host.Contains(":") AndAlso Not Host.Contains("]") Then
            Dim Parts = Host.Split(":"c)
            If Parts.Length = 2 AndAlso Integer.TryParse(Parts(1), Port) Then Host = Parts(0)
        End If
        Dim Client As New TcpClient
        Try
            '注意：Task.Wait 在任务失败时会抛 AggregateException（不是返回 False），
            '所以必须单独接住，否则界面上只会显示「发生一个或多个错误。」这种无意义文案。
            Dim ConnectTask = Client.ConnectAsync(Host, Port)
            Dim Finished As Boolean
            Try
                Finished = ConnectTask.Wait(Timeout)
            Catch ex As AggregateException
                Result.ErrorMessage = FriendlySocketError(ex)
                Return Result
            End Try
            If Not Finished Then
                Result.ErrorMessage = "连接超时"
                Return Result
            End If
            Client.ReceiveTimeout = Timeout
            Client.SendTimeout = Timeout
            Using Stream = Client.GetStream()
                Dim Watch = Stopwatch.StartNew()
                Using Handshake As New MemoryStream
                    WriteVarInt(Handshake, 0)
                    WriteVarInt(Handshake, 47)
                    WriteMcString(Handshake, Host)
                    Handshake.WriteByte(CByte((Port >> 8) And &HFF))
                    Handshake.WriteByte(CByte(Port And &HFF))
                    WriteVarInt(Handshake, 1)
                    SendPacket(Stream, Handshake.ToArray())
                End Using
                SendPacket(Stream, {0})
                Dim PacketLength = ReadVarInt(Stream)
                Dim PacketId = ReadVarInt(Stream)
                If PacketId <> 0 Then Throw New Exception($"意外的状态响应包 ID：{PacketId}")
                Dim Json = ReadMcString(Stream)
                Result.Latency = CInt(Watch.ElapsedMilliseconds)
                Dim Root = JObject.Parse(Json)
                Result.Online = True
                Result.Version = If(Root("version")?("name"), "").ToString()
                Result.Players = If(Root("players")?("online"), 0).Value(Of Integer)()
                Result.MaxPlayers = If(Root("players")?("max"), 0).Value(Of Integer)()
                Result.Motd = MotdFromJson(Root("description"))
            End Using
        Catch ex As Exception
            Result.ErrorMessage = FriendlyExceptionMessage(ex)
        Finally
            Try
                Client.Close()
            Catch
            End Try
        End Try
        Return Result
    End Function

    ''' <summary>把连接失败翻译成用户看得懂的短语。</summary>
    Private Function FriendlySocketError(Ex As AggregateException) As String
        Dim Inner As Exception = If(Ex.InnerException, Ex)
        'AggregateException 可能再包一层
        If TypeOf Inner Is AggregateException AndAlso Inner.InnerException IsNot Nothing Then Inner = Inner.InnerException
        Return FriendlyExceptionMessage(Inner)
    End Function

    Private Function FriendlyExceptionMessage(Ex As Exception) As String
        Dim SockEx = TryCast(Ex, Net.Sockets.SocketException)
        If SockEx IsNot Nothing Then
            Select Case SockEx.SocketErrorCode
                Case Net.Sockets.SocketError.ConnectionRefused
                    Return "连接被拒绝"
                Case Net.Sockets.SocketError.HostNotFound, Net.Sockets.SocketError.NoData
                    Return "无法解析服务器地址"
                Case Net.Sockets.SocketError.TimedOut
                    Return "连接超时"
                Case Net.Sockets.SocketError.NetworkUnreachable, Net.Sockets.SocketError.HostUnreachable
                    Return "网络不可达"
                Case Net.Sockets.SocketError.ConnectionReset
                    Return "连接被重置"
            End Select
            Return SockEx.Message
        End If
        If TypeOf Ex Is IO.IOException Then Return "服务器没有按预期响应"
        If TypeOf Ex Is Threading.Tasks.TaskCanceledException Then Return "连接超时"
        Return Ex.Message
    End Function

#End Region

End Module
