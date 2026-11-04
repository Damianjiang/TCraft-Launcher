Imports System.IO
Imports System.IO.Compression
Imports System.Text

''' <summary>
''' 世界存档数据读写：完整的 NBT 读写、level.dat 世界信息、玩家数据、世界图标、单世界备份与还原。
''' <para/>不依赖任何第三方 NBT 库；字符串按 Java 的 Modified UTF-8 处理，中文世界名可正常读写。
''' </summary>
Public Module ModWorldData

#Region "NBT 标签"

    ''' <summary>
    ''' 一个 NBT 标签。Compound / List 的子标签放在 Children 里，其它类型的值放在 Value 里。
    ''' </summary>
    Public Class NbtTag

        ''' <summary>0 End / 1 Byte / 2 Short / 3 Int / 4 Long / 5 Float / 6 Double / 7 ByteArray / 8 String / 9 List / 10 Compound / 11 IntArray / 12 LongArray</summary>
        Public Type As Byte
        ''' <summary>标签名（List 元素与根标签可能为空）。</summary>
        Public Name As String = ""
        ''' <summary>Byte / Short / Integer / Long / Single / Double / String / Byte() / Integer() / Long()。</summary>
        Public Value As Object
        ''' <summary>Compound 的子标签，或 List 的元素。</summary>
        Public Children As New List(Of NbtTag)
        ''' <summary>List 的元素类型。</summary>
        Public ListType As Byte

        Public Sub New()
        End Sub

        Public Sub New(TagType As Byte, Optional TagName As String = "", Optional TagValue As Object = Nothing)
            Type = TagType
            Name = If(TagName, "")
            Value = TagValue
        End Sub

        ''' <summary>按名称查找子标签（区分大小写，找不到返回 Nothing）。</summary>
        Public Function GetChild(ChildName As String) As NbtTag
            If Children Is Nothing OrElse ChildName Is Nothing Then Return Nothing
            For Each Item As NbtTag In Children
                If Item Is Nothing Then Continue For
                If String.Equals(Item.Name, ChildName, StringComparison.Ordinal) Then Return Item
            Next
            Return Nothing
        End Function

        ''' <summary>取子 Compound，类型不符或不存在返回 Nothing。</summary>
        Public Function GetCompound(ChildName As String) As NbtTag
            Dim Child As NbtTag = GetChild(ChildName)
            If Child Is Nothing OrElse Child.Type <> 10 Then Return Nothing
            Return Child
        End Function

        ''' <summary>取子 List，类型不符或不存在返回 Nothing。</summary>
        Public Function GetList(ChildName As String) As NbtTag
            Dim Child As NbtTag = GetChild(ChildName)
            If Child Is Nothing OrElse Child.Type <> 9 Then Return Nothing
            Return Child
        End Function

        Public Function GetByte(ChildName As String, Optional Def As Byte = 0) As Byte
            Return NbtToByte(GetChild(ChildName), Def)
        End Function

        Public Function GetBoolean(ChildName As String, Optional Def As Boolean = False) As Boolean
            Return NbtToByte(GetChild(ChildName), CByte(If(Def, 1, 0))) <> 0
        End Function

        Public Function GetInt(ChildName As String, Optional Def As Integer = 0) As Integer
            Return CInt(NbtToLong(GetChild(ChildName), Def))
        End Function

        Public Function GetLong(ChildName As String, Optional Def As Long = 0) As Long
            Return NbtToLong(GetChild(ChildName), Def)
        End Function

        Public Function GetSingle(ChildName As String, Optional Def As Single = 0) As Single
            Return CSng(NbtToDouble(GetChild(ChildName), Def))
        End Function

        Public Function GetDouble(ChildName As String, Optional Def As Double = 0) As Double
            Return NbtToDouble(GetChild(ChildName), Def)
        End Function

        Public Function GetString(ChildName As String, Optional Def As String = "") As String
            Dim Child As NbtTag = GetChild(ChildName)
            If Child Is Nothing OrElse Child.Value Is Nothing Then Return Def
            If Child.Type <> 8 Then Return Def
            Return CStr(Child.Value)
        End Function

        ''' <summary>写入（或覆盖）一个 Byte 子标签。</summary>
        Public Sub SetByte(ChildName As String, Value As Byte)
            SetChildValue(1, ChildName, Value)
        End Sub

        Public Sub SetInt(ChildName As String, Value As Integer)
            SetChildValue(3, ChildName, Value)
        End Sub

        Public Sub SetLong(ChildName As String, Value As Long)
            SetChildValue(4, ChildName, Value)
        End Sub

        Public Sub SetString(ChildName As String, Value As String)
            SetChildValue(8, ChildName, If(Value, ""))
        End Sub

        Private Sub SetChildValue(TagType As Byte, ChildName As String, TagValue As Object)
            Dim Child As NbtTag = GetChild(ChildName)
            If Child Is Nothing Then
                Children.Add(New NbtTag(TagType, ChildName, TagValue))
            Else
                Child.Type = TagType
                Child.Value = TagValue
            End If
        End Sub

    End Class

    '数值安全转换
    Private Function NbtToByte(Tag As NbtTag, Def As Byte) As Byte
        If Tag Is Nothing OrElse Tag.Value Is Nothing Then Return Def
        Try
            Select Case Tag.Type
                Case 1, 2, 3, 4, 5, 6
                    Return CByte(NbtToLong(Tag, Def))
            End Select
        Catch ex As Exception
            Logger.Warn(ex, "NBT 数值转换为 Byte 失败")
        End Try
        Return Def
    End Function

    Private Function NbtToLong(Tag As NbtTag, Def As Long) As Long
        If Tag Is Nothing OrElse Tag.Value Is Nothing Then Return Def
        Try
            Select Case Tag.Type
                Case 1, 2, 3, 4
                    Return CLng(Tag.Value)
                Case 5, 6
                    Return CLng(Math.Round(CDbl(Tag.Value)))
            End Select
        Catch ex As Exception
            Logger.Warn(ex, "NBT 数值转换为 Long 失败")
        End Try
        Return Def
    End Function

    Private Function NbtToDouble(Tag As NbtTag, Def As Double) As Double
        If Tag Is Nothing OrElse Tag.Value Is Nothing Then Return Def
        Try
            Select Case Tag.Type
                Case 1, 2, 3, 4, 5, 6
                    Return CDbl(Tag.Value)
            End Select
        Catch ex As Exception
            Logger.Warn(ex, "NBT 数值转换为 Double 失败")
        End Try
        Return Def
    End Function

#End Region

#Region "Modified UTF-8"

    ''' <summary>
    ''' 按 Java 的 Modified UTF-8 编码：U+0000 写成 C0 80，补充平面字符按 UTF-16 代理对拆成两个三字节序列。
    ''' </summary>
    Private Function EncodeModifiedUtf8(Value As String) As Byte()
        If String.IsNullOrEmpty(Value) Then Return New Byte() {}
        Using Ms As New MemoryStream(Value.Length * 3)
            For Each Ch As Char In Value
                Dim Code As Integer = AscW(Ch) And &HFFFF
                If Code >= 1 AndAlso Code <= &H7F Then
                    Ms.WriteByte(CByte(Code))
                ElseIf Code <= &H7FF Then
                    Ms.WriteByte(CByte(&HC0 Or (Code >> 6)))
                    Ms.WriteByte(CByte(&H80 Or (Code And &H3F)))
                Else
                    Ms.WriteByte(CByte(&HE0 Or (Code >> 12)))
                    Ms.WriteByte(CByte(&H80 Or ((Code >> 6) And &H3F)))
                    Ms.WriteByte(CByte(&H80 Or (Code And &H3F)))
                End If
            Next
            Return Ms.ToArray()
        End Using
    End Function

    ''' <summary>
    ''' 解码 Modified UTF-8，同时容错处理标准 UTF-8 的四字节序列（部分工具会写成标准 UTF-8）。
    ''' </summary>
    Private Function DecodeModifiedUtf8(Buffer As Byte()) As String
        If Buffer Is Nothing OrElse Buffer.Length = 0 Then Return ""
        Dim Sb As New StringBuilder(Buffer.Length)
        Dim Index As Integer = 0
        While Index < Buffer.Length
            Dim B As Byte = Buffer(Index)
            If B < &H80 Then
                Sb.Append(ChrW(B))
                Index += 1
            ElseIf (B And &HE0) = &HC0 Then
                If Index + 1 >= Buffer.Length Then Exit While
                Sb.Append(ChrW(((B And &H1F) << 6) Or (Buffer(Index + 1) And &H3F)))
                Index += 2
            ElseIf (B And &HF0) = &HE0 Then
                If Index + 2 >= Buffer.Length Then Exit While
                Sb.Append(ChrW(((B And &HF) << 12) Or ((Buffer(Index + 1) And &H3F) << 6) Or (Buffer(Index + 2) And &H3F)))
                Index += 3
            ElseIf (B And &HF8) = &HF0 Then
                If Index + 3 >= Buffer.Length Then Exit While
                Dim Code As Integer = ((B And &H7) << 18) Or ((Buffer(Index + 1) And &H3F) << 12) Or
                                      ((Buffer(Index + 2) And &H3F) << 6) Or (Buffer(Index + 3) And &H3F)
                Dim Plain As Integer = Code - &H10000
                If Plain >= 0 Then
                    Sb.Append(ChrW(&HD800 + (Plain >> 10)))
                    Sb.Append(ChrW(&HDC00 + (Plain And &H3FF)))
                End If
                Index += 4
            Else
                '非法首字节，跳过以免死循环
                Index += 1
            End If
        End While
        Return Sb.ToString()
    End Function

#End Region

#Region "NBT 读取 / 写回"

    ''' <summary>NBT 数据结构上限，避免损坏文件把内存吃光。</summary>
    Private Const MaxNbtCount As Integer = 8 * 1024 * 1024

    Public Class NbtReader

        Private ReadOnly S As Stream

        Public Sub New(Input As Stream)
            S = Input
        End Sub

        Public Function ReadByteValue() As Byte
            Dim Value As Integer = S.ReadByte()
            If Value < 0 Then Throw New EndOfStreamException("NBT 数据意外结束")
            Return CByte(Value)
        End Function

        Public Function ReadBytesRequired(Count As Integer) As Byte()
            If Count <= 0 Then Return New Byte() {}
            Dim Buffer(Count - 1) As Byte
            Dim ReadCount As Integer = 0
            While ReadCount < Count
                Dim Got As Integer = S.Read(Buffer, ReadCount, Count - ReadCount)
                If Got <= 0 Then Throw New EndOfStreamException("NBT 数据意外结束")
                ReadCount += Got
            End While
            Return Buffer
        End Function

        Public Function ReadInt16() As Short
            Dim Buffer As Byte() = ReadBytesRequired(2)
            Return CShort((CInt(Buffer(0)) << 8) Or Buffer(1))
        End Function

        Public Function ReadUInt16() As Integer
            Dim Buffer As Byte() = ReadBytesRequired(2)
            Return (CInt(Buffer(0)) << 8) Or Buffer(1)
        End Function

        Public Function ReadInt32() As Integer
            Dim Buffer As Byte() = ReadBytesRequired(4)
            Return (CInt(Buffer(0)) << 24) Or (CInt(Buffer(1)) << 16) Or (CInt(Buffer(2)) << 8) Or Buffer(3)
        End Function

        Public Function ReadInt64() As Long
            Dim Buffer As Byte() = ReadBytesRequired(8)
            Dim Value As Long = 0
            For i As Integer = 0 To 7
                Value = (Value << 8) Or Buffer(i)
            Next
            Return Value
        End Function

        Public Function ReadSingleValue() As Single
            Return BitConverter.ToSingle(BitConverter.GetBytes(ReadInt32()), 0)
        End Function

        Public Function ReadDoubleValue() As Double
            Return BitConverter.ToDouble(BitConverter.GetBytes(ReadInt64()), 0)
        End Function

        Public Function ReadString() As String
            Dim Length As Integer = ReadUInt16()
            If Length <= 0 Then Return ""
            Return DecodeModifiedUtf8(ReadBytesRequired(Length))
        End Function

        ''' <summary>读取一个标签的负载（不含类型与名称）。</summary>
        Public Function ReadPayload(TagType As Byte) As NbtTag
            Dim Tag As New NbtTag With {.Type = TagType}
            Select Case TagType
                Case 1
                    Tag.Value = ReadByteValue()
                Case 2
                    Tag.Value = ReadInt16()
                Case 3
                    Tag.Value = ReadInt32()
                Case 4
                    Tag.Value = ReadInt64()
                Case 5
                    Tag.Value = ReadSingleValue()
                Case 6
                    Tag.Value = ReadDoubleValue()
                Case 7
                    Dim Length As Integer = ReadInt32()
                    If Length < 0 OrElse Length > MaxNbtCount Then Throw New InvalidDataException("NBT 字节数组长度异常：" & Length)
                    Tag.Value = ReadBytesRequired(Length)
                Case 8
                    Tag.Value = ReadString()
                Case 9
                    Tag.ListType = ReadByteValue()
                    Dim Length As Integer = ReadInt32()
                    If Length < 0 OrElse Length > MaxNbtCount Then Throw New InvalidDataException("NBT 列表长度异常：" & Length)
                    If Tag.ListType = 0 Then
                        If Length > 0 Then Throw New InvalidDataException("NBT 列表元素类型为 End 但长度不为 0")
                    Else
                        For i As Integer = 1 To Length
                            Tag.Children.Add(ReadPayload(Tag.ListType))
                        Next
                    End If
                Case 10
                    Do
                        Dim ChildType As Byte = ReadByteValue()
                        If ChildType = 0 Then Exit Do
                        Dim ChildName As String = ReadString()
                        Dim Child As NbtTag = ReadPayload(ChildType)
                        Child.Name = ChildName
                        Tag.Children.Add(Child)
                    Loop
                Case 11
                    Dim Length As Integer = ReadInt32()
                    If Length < 0 OrElse Length > MaxNbtCount Then Throw New InvalidDataException("NBT 整型数组长度异常：" & Length)
                    If Length = 0 Then
                        Tag.Value = New Integer() {}
                    Else
                        Dim Arr(Length - 1) As Integer
                        For i As Integer = 0 To Length - 1
                            Arr(i) = ReadInt32()
                        Next
                        Tag.Value = Arr
                    End If
                Case 12
                    Dim Length As Integer = ReadInt32()
                    If Length < 0 OrElse Length > MaxNbtCount Then Throw New InvalidDataException("NBT 长整型数组长度异常：" & Length)
                    If Length = 0 Then
                        Tag.Value = New Long() {}
                    Else
                        Dim Arr(Length - 1) As Long
                        For i As Integer = 0 To Length - 1
                            Arr(i) = ReadInt64()
                        Next
                        Tag.Value = Arr
                    End If
                Case Else
                    Throw New NotSupportedException("不支持的 NBT 标签类型：" & TagType)
            End Select
            Return Tag
        End Function

    End Class

    Private Class NbtWriter

        Private ReadOnly S As Stream

        Public Sub New(Output As Stream)
            S = Output
        End Sub

        Public Sub WriteByteValue(Value As Byte)
            S.WriteByte(Value)
        End Sub

        Public Sub WriteInt16(Value As Short)
            Dim Raw As Integer = CInt(Value) And &HFFFF
            S.WriteByte(CByte((Raw >> 8) And &HFF))
            S.WriteByte(CByte(Raw And &HFF))
        End Sub

        Public Sub WriteInt32(Value As Integer)
            S.WriteByte(CByte((Value >> 24) And &HFF))
            S.WriteByte(CByte((Value >> 16) And &HFF))
            S.WriteByte(CByte((Value >> 8) And &HFF))
            S.WriteByte(CByte(Value And &HFF))
        End Sub

        Public Sub WriteInt64(Value As Long)
            For Shift As Integer = 56 To 0 Step -8
                S.WriteByte(CByte((Value >> Shift) And &HFF))
            Next
        End Sub

        Public Sub WriteSingleValue(Value As Single)
            WriteInt32(BitConverter.ToInt32(BitConverter.GetBytes(Value), 0))
        End Sub

        Public Sub WriteDoubleValue(Value As Double)
            WriteInt64(BitConverter.ToInt64(BitConverter.GetBytes(Value), 0))
        End Sub

        Public Sub WriteString(Value As String)
            Dim Buffer As Byte() = EncodeModifiedUtf8(If(Value, ""))
            If Buffer.Length > &HFFFF Then Throw New InvalidDataException("NBT 字符串超过 65535 字节，无法写入")
            WriteInt16(CShort(Buffer.Length))
            S.Write(Buffer, 0, Buffer.Length)
        End Sub

        ''' <summary>写入标签负载（不含类型与名称）。</summary>
        Public Sub WritePayload(Tag As NbtTag)
            Select Case Tag.Type
                Case 1
                    WriteByteValue(CByte(If(Tag.Value, CByte(0))))
                Case 2
                    WriteInt16(CShort(If(Tag.Value, CShort(0))))
                Case 3
                    WriteInt32(CInt(If(Tag.Value, 0)))
                Case 4
                    WriteInt64(CLng(If(Tag.Value, 0L)))
                Case 5
                    WriteSingleValue(CSng(If(Tag.Value, 0.0F)))
                Case 6
                    WriteDoubleValue(CDbl(If(Tag.Value, 0.0)))
                Case 7
                    Dim Arr As Byte() = TryCast(Tag.Value, Byte())
                    If Arr Is Nothing Then Arr = New Byte() {}
                    WriteInt32(Arr.Length)
                    S.Write(Arr, 0, Arr.Length)
                Case 8
                    WriteString(CStr(If(Tag.Value, "")))
                Case 9
                    WriteByteValue(Tag.ListType)
                    WriteInt32(Tag.Children.Count)
                    For Each Child As NbtTag In Tag.Children
                        WritePayload(Child)
                    Next
                Case 10
                    For Each Child As NbtTag In Tag.Children
                        WriteByteValue(Child.Type)
                        WriteString(Child.Name)
                        WritePayload(Child)
                    Next
                    WriteByteValue(0)
                Case 11
                    Dim Arr As Integer() = TryCast(Tag.Value, Integer())
                    If Arr Is Nothing Then Arr = New Integer() {}
                    WriteInt32(Arr.Length)
                    For Each One As Integer In Arr
                        WriteInt32(One)
                    Next
                Case 12
                    Dim Arr As Long() = TryCast(Tag.Value, Long())
                    If Arr Is Nothing Then Arr = New Long() {}
                    WriteInt32(Arr.Length)
                    For Each One As Long In Arr
                        WriteInt64(One)
                    Next
                Case Else
                    Throw New NotSupportedException("不支持的 NBT 标签类型：" & Tag.Type)
            End Select
        End Sub

    End Class

    ''' <summary>
    ''' 读取一个 NBT 文件（自动判断是否 gzip 压缩）。根标签必须是 Compound。
    ''' </summary>
    Public Function ReadNbtFile(FilePath As String) As NbtTag
        If Not FileUtils.Exists(FilePath) Then Throw New FileNotFoundException("找不到 NBT 文件", FilePath)
        Using Fs As New FileStream(FilePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite)
            Using Body As Stream = WrapDecompress(Fs)
                Dim Reader As New NbtReader(Body)
                Dim RootType As Byte = Reader.ReadByteValue()
                If RootType <> 10 Then Throw New InvalidDataException("NBT 根标签不是 Compound（实际类型 " & RootType & "）：" & FilePath)
                Dim RootName As String = Reader.ReadString()
                Dim Root As NbtTag = Reader.ReadPayload(10)
                Root.Name = RootName
                Return Root
            End Using
        End Using
    End Function

    ''' <summary>
    ''' 写回一个 NBT 文件（gzip 压缩，先写临时文件再原子替换，避免写一半损坏原文件）。
    ''' </summary>
    Public Sub WriteNbtFile(FilePath As String, Root As NbtTag)
        If Root Is Nothing Then Throw New ArgumentNullException(NameOf(Root))
        Dim Buffer As Byte()
        Using Ms As New MemoryStream()
            Using Gz As New GZipStream(Ms, CompressionMode.Compress, True)
                Dim Writer As New NbtWriter(Gz)
                Writer.WriteByteValue(Root.Type)
                Writer.WriteString(If(Root.Name, ""))
                Writer.WritePayload(Root)
            End Using
            Buffer = Ms.ToArray()
        End Using
        FileUtils.WriteAtomic(FilePath, Buffer)
    End Sub

    ''' <summary>按文件头的 gzip 魔数判断是否需要解压。</summary>
    Private Function WrapDecompress(Fs As FileStream) As Stream
        Dim Magic(1) As Byte
        Dim Got As Integer = Fs.Read(Magic, 0, 2)
        Fs.Position = 0
        If Got = 2 AndAlso Magic(0) = &H1F AndAlso Magic(1) = &H8B Then
            Return New GZipStream(Fs, CompressionMode.Decompress)
        End If
        Return Fs
    End Function

#End Region

#Region "世界信息"

    ''' <summary>level.dat 里可以展示 / 修改的世界信息。</summary>
    Public Class WorldInfo
        Public LevelName As String = ""
        ''' <summary>0 和平 / 1 简单 / 2 普通 / 3 困难。</summary>
        Public Difficulty As Byte = 2
        Public DifficultyLocked As Boolean = False
        Public Hardcore As Boolean = False
        Public AllowCommands As Boolean = False
        ''' <summary>0 生存 / 1 创造 / 2 冒险 / 3 旁观。</summary>
        Public GameType As Integer = 0
        Public RandomSeed As Long = 0
        Public SpawnX As Integer = 0
        Public SpawnY As Integer = 64
        Public SpawnZ As Integer = 0
        ''' <summary>世界已流逝的游戏刻数（DayTime）。</summary>
        Public DayTime As Long = 0
        ''' <summary>上次游玩时间，unix 毫秒。</summary>
        Public LastPlayed As Long = 0
        Public VersionName As String = ""
        Public HasIcon As Boolean = False

        ''' <summary>游戏内天数（DayTime ÷ 24000）。</summary>
        Public ReadOnly Property DayCount As Long
            Get
                Return DayTime \ 24000
            End Get
        End Property

        ''' <summary>上次游玩的本地时间文本，未知返回空字符串。</summary>
        Public ReadOnly Property LastPlayedText As String
            Get
                If LastPlayed <= 0 Then Return ""
                Try
                    Dim Origin As New Date(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)
                    Return Origin.AddMilliseconds(LastPlayed).ToLocalTime().ToString("yyyy'/'MM'/'dd HH':'mm")
                Catch ex As Exception
                    Logger.Warn(ex, "换算世界上次游玩时间失败")
                    Return ""
                End Try
            End Get
        End Property
    End Class

    ''' <summary>读取世界信息，文件缺失或损坏时返回 Nothing。</summary>
    Public Function ReadWorldInfo(WorldDir As String) As WorldInfo
        Try
            Dim Folder As String = PathUtils.AddSlashSuffix(WorldDir)
            Dim LevelFile As String = Folder & "level.dat"
            If Not FileUtils.Exists(LevelFile) Then Return Nothing
            Dim Root As NbtTag = ReadNbtFile(LevelFile)
            If Root Is Nothing Then Return Nothing
            Dim Data As NbtTag = Root.GetCompound("Data")
            If Data Is Nothing Then Return Nothing

            Dim Info As New WorldInfo With {
                .LevelName = Data.GetString("LevelName", PathUtils.GetLastPart(WorldDir)),
                .Difficulty = Data.GetByte("Difficulty", CByte(2)),
                .DifficultyLocked = Data.GetBoolean("DifficultyLocked"),
                .Hardcore = Data.GetBoolean("hardcore"),
                .AllowCommands = Data.GetBoolean("allowCommands"),
                .GameType = Data.GetInt("GameType", 0),
                .DayTime = Data.GetLong("DayTime", Data.GetLong("Time", 0L)),
                .LastPlayed = Data.GetLong("LastPlayed", 0L)
            }
            If Info.Difficulty > 3 Then Info.Difficulty = 2

            '种子：老版本在 Data.RandomSeed，1.16+ 在 Data.WorldGenSettings.seed
            Info.RandomSeed = Data.GetLong("RandomSeed", 0L)
            Dim GenSettings As NbtTag = Data.GetCompound("WorldGenSettings")
            If GenSettings IsNot Nothing Then
                Info.RandomSeed = GenSettings.GetLong("seed", Info.RandomSeed)
            End If

            '出生点：1.21.2+ 改成了 SpawnPos 整型数组
            Dim HasSpawn As Boolean = Data.GetChild("SpawnX") IsNot Nothing
            If HasSpawn Then
                Info.SpawnX = Data.GetInt("SpawnX", 0)
                Info.SpawnY = Data.GetInt("SpawnY", 64)
                Info.SpawnZ = Data.GetInt("SpawnZ", 0)
            Else
                Dim SpawnPos As NbtTag = Data.GetChild("SpawnPos")
                If SpawnPos IsNot Nothing Then
                    Dim Arr As Integer() = TryCast(SpawnPos.Value, Integer())
                    If Arr IsNot Nothing AndAlso Arr.Length >= 3 Then
                        Info.SpawnX = Arr(0)
                        Info.SpawnY = Arr(1)
                        Info.SpawnZ = Arr(2)
                    End If
                End If
            End If

            Dim Version As NbtTag = Data.GetCompound("Version")
            If Version IsNot Nothing Then Info.VersionName = Version.GetString("Name", "")
            If String.IsNullOrEmpty(Info.VersionName) Then Info.VersionName = Data.GetString("Version", "")

            Info.HasIcon = FileUtils.Exists(Folder & "icon.png")
            Return Info
        Catch ex As Exception
            Logger.Warn(ex, "读取世界信息失败：" & WorldDir)
            Return Nothing
        End Try
    End Function

    ''' <summary>
    ''' 只把可改字段写回 level.dat，其余标签原样保留。写回前把 level.dat 备份为 level.dat.bak（只备份一次）。
    ''' </summary>
    Public Sub UpdateWorldInfo(WorldDir As String, Info As WorldInfo)
        If Info Is Nothing Then Throw New ArgumentNullException(NameOf(Info))
        Dim Folder As String = PathUtils.AddSlashSuffix(WorldDir)
        Dim LevelFile As String = Folder & "level.dat"
        If Not FileUtils.Exists(LevelFile) Then Throw New FileNotFoundException("找不到 level.dat", LevelFile)

        Dim Root As NbtTag = ReadNbtFile(LevelFile)
        If Root Is Nothing Then Throw New InvalidDataException("level.dat 解析失败")
        Dim Data As NbtTag = Root.GetCompound("Data")
        If Data Is Nothing Then Throw New InvalidDataException("level.dat 里没有 Data 标签")

        Dim NewName As String = If(Info.LevelName, "").Trim()
        If NewName.Length = 0 Then NewName = PathUtils.GetLastPart(WorldDir)
        Data.SetString("LevelName", NewName)
        Data.SetByte("Difficulty", Info.Difficulty)
        Data.SetByte("DifficultyLocked", CByte(If(Info.DifficultyLocked, 1, 0)))
        Data.SetByte("allowCommands", CByte(If(Info.AllowCommands, 1, 0)))
        Data.SetByte("hardcore", CByte(If(Info.Hardcore, 1, 0)))
        Data.SetInt("GameType", Info.GameType)

        BackupLevelDatOnce(LevelFile)
        WriteNbtFile(LevelFile, Root)
        Logger.Info($"已写回世界信息：{NewName}（难度 {Info.Difficulty}，允许作弊 {Info.AllowCommands}）")
    End Sub

    ''' <summary>备份 level.dat 为 level.dat.bak；已存在 .bak 时不再覆盖（保留最初的原件）。</summary>
    Private Sub BackupLevelDatOnce(LevelFile As String)
        Try
            Dim Bak As String = LevelFile & ".bak"
            If FileUtils.Exists(Bak) Then Return
            FileUtils.Copy(LevelFile, Bak)
            Logger.Info("已备份 level.dat：" & Bak)
        Catch ex As Exception
            Logger.Warn(ex, "备份 level.dat 失败：" & LevelFile)
        End Try
    End Sub

    ''' <summary>难度整数转显示文案。</summary>
    Public Function DifficultyText(Difficulty As Byte) As String
        Select Case Difficulty
            Case 0
                Return ModLang.L("和平")
            Case 1
                Return ModLang.L("简单")
            Case 3
                Return ModLang.L("困难")
            Case Else
                Return ModLang.L("普通")
        End Select
    End Function

    ''' <summary>游戏模式整数转显示文案。</summary>
    Public Function GameTypeText(GameType As Integer) As String
        Select Case GameType
            Case 0
                Return ModLang.L("生存")
            Case 1
                Return ModLang.L("创造")
            Case 2
                Return ModLang.L("冒险")
            Case 3
                Return ModLang.L("旁观")
            Case Else
                Return ModLang.L("未知")
        End Select
    End Function

    ''' <summary>维度标识转显示文案。</summary>
    Public Function DimensionText(Dimension As String) As String
        If String.IsNullOrEmpty(Dimension) Then Return ModLang.L("未知")
        Select Case Dimension.ToLowerInvariant()
            Case "minecraft:overworld", "overworld", "0"
                Return ModLang.L("主世界")
            Case "minecraft:the_nether", "the_nether", "-1"
                Return ModLang.L("下界")
            Case "minecraft:the_end", "the_end", "1"
                Return ModLang.L("末地")
            Case Else
                Return Dimension
        End Select
    End Function

#End Region

#Region "玩家数据"

    ''' <summary>世界里的玩家数据（血量 / 饥饿 / 经验 / 坐标 / 死亡点）。</summary>
    Public Class PlayerInfo
        Public Found As Boolean = False
        ''' <summary>数据来源文件名，便于排查。</summary>
        Public FileName As String = ""
        Public Health As Single = 20
        Public MaxHealth As Single = 20
        Public FoodLevel As Integer = 20
        Public FoodSaturation As Single = 5
        Public XpLevel As Integer = 0
        Public XpTotal As Integer = 0
        Public GameType As Integer = -1
        Public X As Double = 0
        Public Y As Double = 0
        Public Z As Double = 0
        Public Dimension As String = ""
        Public HasDeathPoint As Boolean = False
        Public DeathX As Double = 0
        Public DeathY As Double = 0
        Public DeathZ As Double = 0
        Public DeathDim As String = ""
    End Class

    ''' <summary>
    ''' 读取玩家数据：优先 level.dat 的 Data.Player，取不到再扫描 playerdata\*.dat 中最近修改的一个。
    ''' </summary>
    Public Function ReadPlayerInfo(WorldDir As String) As PlayerInfo
        Dim Info As New PlayerInfo
        Try
            Dim Folder As String = PathUtils.AddSlashSuffix(WorldDir)
            Dim PlayerTag As NbtTag = Nothing
            Dim LevelFile As String = Folder & "level.dat"
            If FileUtils.Exists(LevelFile) Then
                Try
                    Dim Root As NbtTag = ReadNbtFile(LevelFile)
                    If Root IsNot Nothing Then
                        Dim Data As NbtTag = Root.GetCompound("Data")
                        If Data IsNot Nothing Then
                            PlayerTag = Data.GetCompound("Player")
                            If PlayerTag IsNot Nothing Then Info.FileName = "level.dat"
                        End If
                    End If
                Catch ex As Exception
                    Logger.Warn(ex, "读取 level.dat 中的玩家数据失败：" & LevelFile)
                End Try
            End If
            If PlayerTag Is Nothing Then
                Dim Chosen As String = GetNewestPlayerData(Folder & "playerdata\")
                If Not String.IsNullOrEmpty(Chosen) Then
                    Try
                        PlayerTag = ReadNbtFile(Chosen)
                        Info.FileName = IO.Path.GetFileName(Chosen)
                    Catch ex As Exception
                        Logger.Warn(ex, "读取玩家数据文件失败：" & Chosen)
                        Return Info
                    End Try
                End If
            End If
            If PlayerTag Is Nothing Then Return Info

            Info.Found = True
            Info.Health = PlayerTag.GetSingle("Health", 20)
            Info.FoodLevel = PlayerTag.GetInt("foodLevel", 20)
            Info.FoodSaturation = PlayerTag.GetSingle("foodSaturationLevel", 5)
            Info.XpLevel = PlayerTag.GetInt("XpLevel", 0)
            Info.XpTotal = PlayerTag.GetInt("XpTotal", 0)
            Info.GameType = PlayerTag.GetInt("playerGameType", -1)

            '最大生命值在 Attributes 列表里，缺省 20
            Dim MaxHealth As Single = 20
            Dim Attributes As NbtTag = PlayerTag.GetList("Attributes")
            If Attributes IsNot Nothing Then
                For Each Attribute As NbtTag In Attributes.Children
                    If Attribute Is Nothing OrElse Attribute.Type <> 10 Then Continue For
                    If Attribute.GetString("Name", "") = "minecraft:generic.max_health" Then
                        MaxHealth = Attribute.GetSingle("Base", 20)
                    End If
                Next
            End If
            If MaxHealth <= 0 Then MaxHealth = 20
            Info.MaxHealth = MaxHealth

            '坐标
            Dim Pos As NbtTag = PlayerTag.GetList("Pos")
            If Pos IsNot Nothing AndAlso Pos.Children.Count >= 3 Then
                Info.X = NbtToDouble(Pos.Children(0), 0)
                Info.Y = NbtToDouble(Pos.Children(1), 0)
                Info.Z = NbtToDouble(Pos.Children(2), 0)
            End If

            '维度：老版本是整型，新版本是字符串
            Dim DimensionTag As NbtTag = PlayerTag.GetChild("Dimension")
            If DimensionTag IsNot Nothing Then
                If DimensionTag.Type = 8 Then
                    Info.Dimension = CStr(DimensionTag.Value)
                ElseIf DimensionTag.Type = 3 Then
                    Info.Dimension = CStr(CInt(DimensionTag.Value))
                End If
            End If

            '上次死亡点（1.19+）
            Dim Death As NbtTag = PlayerTag.GetCompound("LastDeathLocation")
            If Death IsNot Nothing Then
                Info.DeathDim = Death.GetString("dimension", "")
                Dim DeathPos As NbtTag = Death.GetChild("pos")
                If DeathPos IsNot Nothing Then
                    Dim Arr As Integer() = TryCast(DeathPos.Value, Integer())
                    If Arr IsNot Nothing AndAlso Arr.Length >= 3 Then
                        Info.DeathX = Arr(0)
                        Info.DeathY = Arr(1)
                        Info.DeathZ = Arr(2)
                        Info.HasDeathPoint = True
                    End If
                End If
            End If
            Return Info
        Catch ex As Exception
            Logger.Warn(ex, "读取玩家数据失败：" & WorldDir)
            Return Info
        End Try
    End Function

    ''' <summary>取 playerdata 目录下最近修改的 .dat 文件，找不到返回 Nothing。</summary>
    Private Function GetNewestPlayerData(PlayerDir As String) As String
        Try
            If Not DirectoryUtils.Exists(PlayerDir) Then Return Nothing
            Dim Best As String = Nothing
            Dim BestTime As Date = Date.MinValue
            For Each FilePath In DirectoryUtils.EnumerateFiles(PlayerDir, False, "*.dat")
                Try
                    Dim Info As New IO.FileInfo(FilePath)
                    If Info.LastWriteTime > BestTime Then
                        BestTime = Info.LastWriteTime
                        Best = FilePath
                    End If
                Catch ex As Exception
                    Logger.Warn(ex, "读取玩家数据文件信息失败：" & FilePath)
                End Try
            Next
            Return Best
        Catch ex As Exception
            Logger.Warn(ex, "扫描玩家数据目录失败：" & PlayerDir)
            Return Nothing
        End Try
    End Function

#End Region

#Region "世界图标"

    ''' <summary>读取世界图标（icon.png），没有则返回 Nothing。</summary>
    Public Function ReadWorldIcon(WorldDir As String) As BitmapImage
        Try
            Dim FilePath As String = PathUtils.AddSlashSuffix(WorldDir) & "icon.png"
            If Not FileUtils.Exists(FilePath) Then Return Nothing
            Dim Buffer As Byte() = FileUtils.ReadAsBytes(FilePath)
            If Buffer Is Nothing OrElse Buffer.Length = 0 Then Return Nothing
            Using Ms As New MemoryStream(Buffer)
                Dim Bitmap As New BitmapImage
                Bitmap.BeginInit()
                Bitmap.CacheOption = BitmapCacheOption.OnLoad
                Bitmap.CreateOptions = BitmapCreateOptions.IgnoreImageCache
                Bitmap.StreamSource = Ms
                Bitmap.EndInit()
                Bitmap.Freeze()
                Return Bitmap
            End Using
        Catch ex As Exception
            Logger.Warn(ex, "读取世界图标失败：" & WorldDir)
            Return Nothing
        End Try
    End Function

    ''' <summary>
    ''' 设置世界图标：必须是 64×64 的 PNG。写入前把原 icon.png 备份为 icon.png.bak。
    ''' </summary>
    Public Function SetWorldIcon(WorldDir As String, ImagePath As String, ByRef ErrorText As String) As Boolean
        ErrorText = ""
        Try
            If String.IsNullOrWhiteSpace(ImagePath) OrElse Not FileUtils.Exists(ImagePath) Then
                ErrorText = ModLang.L("找不到所选的图片文件。")
                Return False
            End If
            Dim Buffer As Byte() = FileUtils.ReadAsBytes(ImagePath)
            If Not IsPng(Buffer) Then
                ErrorText = ModLang.L("该文件不是有效的 PNG 图片，请选择 .png 格式的图标。")
                Return False
            End If
            Dim Width As Integer = 0
            Dim Height As Integer = 0
            If Not TryGetPngSize(Buffer, Width, Height) Then
                ErrorText = ModLang.L("无法读取该 PNG 的尺寸，文件可能已损坏。")
                Return False
            End If
            If Width <> 64 OrElse Height <> 64 Then
                ErrorText = ModLang.L("世界图标必须是 64×64 像素。") & ModLang.L("（当前为 ") & Width & "×" & Height & ModLang.L("）")
                Return False
            End If

            Dim Folder As String = PathUtils.AddSlashSuffix(WorldDir)
            If Not DirectoryUtils.Exists(Folder) Then
                ErrorText = ModLang.L("世界文件夹不存在。")
                Return False
            End If
            Dim Target As String = Folder & "icon.png"
            Try
                If FileUtils.Exists(Target) Then FileUtils.Copy(Target, Folder & "icon.png.bak")
            Catch ex As Exception
                Logger.Warn(ex, "备份原世界图标失败（继续写入新图标）")
            End Try
            FileUtils.Write(Target, Buffer)
            Logger.Info("已更新世界图标：" & Target)
            Return True
        Catch ex As Exception
            Logger.Error(ex, "设置世界图标失败")
            ErrorText = ex.Message
            Return False
        End Try
    End Function

    ''' <summary>PNG 魔数判断。</summary>
    Private Function IsPng(Buffer As Byte()) As Boolean
        If Buffer Is Nothing OrElse Buffer.Length < 8 Then Return False
        Return Buffer(0) = &H89 AndAlso Buffer(1) = &H50 AndAlso Buffer(2) = &H4E AndAlso Buffer(3) = &H47 AndAlso
               Buffer(4) = &HD AndAlso Buffer(5) = &HA AndAlso Buffer(6) = &H1A AndAlso Buffer(7) = &HA
    End Function

    ''' <summary>从 IHDR 块读取 PNG 宽高。</summary>
    Private Function TryGetPngSize(Buffer As Byte(), ByRef Width As Integer, ByRef Height As Integer) As Boolean
        Width = 0
        Height = 0
        If Buffer Is Nothing OrElse Buffer.Length < 33 Then Return False
        '签名 8 字节 + 长度 4 字节 + "IHDR" 4 字节，之后是宽 4 字节、高 4 字节（大端）
        If Buffer(12) <> AscW("I"c) OrElse Buffer(13) <> AscW("H"c) OrElse Buffer(14) <> AscW("D"c) OrElse Buffer(15) <> AscW("R"c) Then Return False
        Width = (CInt(Buffer(16)) << 24) Or (CInt(Buffer(17)) << 16) Or (CInt(Buffer(18)) << 8) Or Buffer(19)
        Height = (CInt(Buffer(20)) << 24) Or (CInt(Buffer(21)) << 16) Or (CInt(Buffer(22)) << 8) Or Buffer(23)
        Return Width > 0 AndAlso Height > 0
    End Function

#End Region

#Region "世界备份"

    ''' <summary>一个世界备份文件。</summary>
    Public Class BackupEntry
        Public FilePath As String = ""
        Public Name As String = ""
        Public SizeBytes As Long = 0
        Public Created As Date = Date.MinValue

        ''' <summary>人类可读的大小。</summary>
        Public ReadOnly Property SizeText As String
            Get
                Return ModWorldData.FormatSize(SizeBytes)
            End Get
        End Property

        ''' <summary>创建时间的显示文本。</summary>
        Public ReadOnly Property CreatedText As String
            Get
                If Created = Date.MinValue Then Return ""
                Return Created.ToString("yyyy'/'MM'/'dd HH':'mm':'ss")
            End Get
        End Property
    End Class

    ''' <summary>单世界备份的根目录，以 \ 结尾。</summary>
    Public ReadOnly Property BackupRoot As String
        Get
            Return PathUtils.AddSlashSuffix(Paths.BaseThenName) & "WorldBackups\"
        End Get
    End Property

    ''' <summary>把字节数变成人类可读文本。</summary>
    Public Function FormatSize(Bytes As Long) As String
        If Bytes < 1024 Then Return Bytes & " B"
        If Bytes < 1024L * 1024 Then Return Math.Round(Bytes / 1024, 1) & " KB"
        If Bytes < 1024L * 1024 * 1024 Then Return Math.Round(Bytes / 1024 / 1024, 1) & " MB"
        Return Math.Round(Bytes / 1024 / 1024 / 1024, 2) & " GB"
    End Function

    ''' <summary>备份存放目录：WorldBackups\实例名\世界名\</summary>
    Private Function GetBackupFolder(WorldDir As String, InstanceName As String) As String
        Dim WorldName As String = PathUtils.GetLastPart(PathUtils.RemoveSlashSuffix(WorldDir))
        Return BackupRoot & SafeFilePart(InstanceName, "未命名实例") & "\" & SafeFilePart(WorldName, "未命名世界") & "\"
    End Function

    ''' <summary>某世界实际使用的备份目录（供界面展示），以 \ 结尾。</summary>
    Public Function BackupFolderOf(WorldDir As String, InstanceName As String) As String
        Return GetBackupFolder(WorldDir, InstanceName)
    End Function

    ''' <summary>把任意文本变成安全的文件名片段。</summary>
    Private Function SafeFilePart(Value As String, Fallback As String) As String
        Dim Result As String = If(Value, "").Trim()
        For Each BadChar In IO.Path.GetInvalidFileNameChars()
            Result = Result.Replace(BadChar, "_"c)
        Next
        Result = Result.TrimEnd("."c, " "c)
        If Result.Length = 0 Then Result = Fallback
        If Result.Length > 60 Then Result = Result.Substring(0, 60)
        Return Result
    End Function

    ''' <summary>
    ''' 备份一个世界为 zip，返回 zip 路径；失败返回 Nothing。
    ''' <paramref name="NamePrefix"/> 为文件名前缀，默认「备份」，还原前自动备份时用「还原前」。
    ''' </summary>
    Public Function CreateBackup(WorldDir As String, InstanceName As String, Optional NamePrefix As String = "备份") As String
        Try
            Dim Source As String = PathUtils.AddSlashSuffix(WorldDir)
            If Not FileUtils.Exists(Source & "level.dat") Then
                Logger.Warn("世界目录里没有 level.dat，无法备份：" & WorldDir)
                Return Nothing
            End If
            Dim Folder As String = GetBackupFolder(WorldDir, InstanceName)
            DirectoryUtils.Create(Folder)
            Dim Prefix As String = If(String.IsNullOrWhiteSpace(NamePrefix), "备份", NamePrefix)
            Dim Stamp As String = Date.Now.ToString("yyyyMMdd-HHmmss")
            Dim Target As String = Folder & Prefix & "-" & Stamp & ".zip"
            Dim Index As Integer = 1
            While FileUtils.Exists(Target)
                Index += 1
                Target = Folder & Prefix & "-" & Stamp & "-" & Index & ".zip"
            End While
            ModArchive.CreateZip(Source, Target, {"session.lock"})
            If Not FileUtils.Exists(Target) Then Return Nothing
            Logger.Info("已创建世界备份：" & Target)
            Return Target
        Catch ex As Exception
            Logger.Error(ex, "创建世界备份失败")
            Return Nothing
        End Try
    End Function

    ''' <summary>列出某个世界的全部备份，最近的在前。</summary>
    Public Function ListBackups(WorldDir As String, InstanceName As String) As List(Of BackupEntry)
        Dim Result As New List(Of BackupEntry)
        Try
            Dim Folder As String = GetBackupFolder(WorldDir, InstanceName)
            If Not DirectoryUtils.Exists(Folder) Then Return Result
            For Each FilePath In DirectoryUtils.EnumerateFiles(Folder, False, "*.zip")
                Try
                    Dim Info As New IO.FileInfo(FilePath)
                    Result.Add(New BackupEntry With {
                        .FilePath = FilePath,
                        .Name = IO.Path.GetFileNameWithoutExtension(FilePath),
                        .SizeBytes = Info.Length,
                        .Created = Info.LastWriteTime
                    })
                Catch ex As Exception
                    Logger.Warn(ex, "读取备份信息失败：" & FilePath)
                End Try
            Next
        Catch ex As Exception
            Logger.Warn(ex, "列出世界备份失败")
        End Try
        Return Result.OrderByDescending(Function(Item) Item.Created).ToList()
    End Function

    ''' <summary>
    ''' 还原备份：先把当前世界再备份一次（命名为「还原前-时间」），再把 zip 解压覆盖到世界目录。
    ''' </summary>
    Public Function RestoreBackup(ZipPath As String, WorldDir As String, InstanceName As String, ByRef ErrorText As String) As Boolean
        ErrorText = ""
        Try
            If String.IsNullOrWhiteSpace(ZipPath) OrElse Not FileUtils.Exists(ZipPath) Then
                ErrorText = ModLang.L("备份文件不存在或已被移动。")
                Return False
            End If
            Dim Folder As String = PathUtils.AddSlashSuffix(WorldDir)
            '还原前的安全备份
            If FileUtils.Exists(Folder & "level.dat") Then
                Dim Safety As String = CreateBackup(Folder, InstanceName, "还原前")
                If String.IsNullOrEmpty(Safety) Then
                    Logger.Warn("还原前的自动备份失败（继续还原）：" & WorldDir)
                End If
            End If
            DirectoryUtils.Create(Folder)
            ModArchive.ExtractZip(ZipPath, Folder)
            Logger.Info($"已还原世界备份：{ZipPath} → {Folder}")
            Return True
        Catch ex As Exception
            Logger.Error(ex, "还原世界备份失败")
            ErrorText = ex.Message
            Return False
        End Try
    End Function

    ''' <summary>删除一个备份文件（移入回收站），并清理空目录。</summary>
    Public Sub DeleteBackup(ZipPath As String)
        Try
            If String.IsNullOrWhiteSpace(ZipPath) OrElse Not FileUtils.Exists(ZipPath) Then Return
            FileUtils.Delete(ZipPath, True)
            Logger.Info("已删除世界备份：" & ZipPath)
            '清理空目录
            Dim Folder As String = IO.Path.GetDirectoryName(ZipPath)
            If Not String.IsNullOrEmpty(Folder) AndAlso DirectoryUtils.Exists(Folder) Then
                If Not DirectoryUtils.EnumerateFiles(Folder, False).Any() AndAlso
                   Not DirectoryUtils.EnumerateDirectories(Folder, False).Any() Then
                    DirectoryUtils.Delete(Folder)
                End If
            End If
        Catch ex As Exception
            Logger.Warn(ex, "删除世界备份失败：" & ZipPath)
        End Try
    End Sub

#End Region

End Module
