Imports System.IO
Imports System.Security.Cryptography
Imports System.Text
Imports Newtonsoft.Json

''' <summary>
''' 一次游玩会话（对应 LumilioCL 游戏页 · 历史的「游玩记录」）。
''' </summary>
Public Class McSessionRecord
    Public Property StartUtc As String = ""
    Public Property EndUtc As String = ""
    Public Property Seconds As Long = 0
    Public Property ExitCode As Integer = 0
    ''' <summary>0 正常结束 / 1 非零退出 / 2 上次未正常记录</summary>
    Public Property Result As Integer = 0
    Public Property Player As String = ""
    Public Property Version As String = ""
    Public ReadOnly Property StartLocal As Date
        Get
            Dim T As Date
            If Date.TryParse(StartUtc, Nothing, Globalization.DateTimeStyles.RoundtripKind, T) Then Return T.ToLocalTime()
            Return Date.MinValue
        End Get
    End Property
    Public ReadOnly Property DurationText As String
        Get
            If Seconds < 60 Then Return Seconds & " 秒"
            If Seconds < 3600 Then Return (Seconds \ 60) & " 分 " & (Seconds Mod 60) & " 秒"
            Return (Seconds \ 3600) & " 小时 " & ((Seconds Mod 3600) \ 60) & " 分"
        End Get
    End Property
    Public ReadOnly Property ResultText As String
        Get
            Select Case Result
                Case 0 : Return "正常结束"
                Case 1 : Return "非正常退出"
                Case Else : Return "上次未正常记录"
            End Select
        End Get
    End Property
End Class

''' <summary>
''' 一个快照文件。
''' </summary>
Public Class McSnapshotRecord
    Public Property FileName As String = ""
    Public Property CreatedUtc As String = ""
    Public Property Note As String = ""
    Public Property Size As Long = 0
    Public Property Scope As String = ""
    Public ReadOnly Property CreatedLocal As Date
        Get
            Dim T As Date
            If Date.TryParse(CreatedUtc, Nothing, Globalization.DateTimeStyles.RoundtripKind, T) Then Return T.ToLocalTime()
            Return Date.MinValue
        End Get
    End Property
    Public ReadOnly Property ScopeText As String
        Get
            If String.IsNullOrEmpty(Scope) Then Return "全部内容"
            Return "世界：" & Scope
        End Get
    End Property
End Class

''' <summary>
''' 实例历史与快照存储（复刻 LumilioCL 的「历史」：游玩记录 / 快照 / 变更）。
''' 数据放在启动器数据目录的 History 下，按实例路径哈希隔离，不污染游戏目录。
''' </summary>
Public Module ModInstanceHistory

    Private ReadOnly Gate As New Object

    ''' <summary>历史数据根目录，以 \ 结尾。</summary>
    Public ReadOnly Property DataRoot As String
        Get
            Return PathUtils.AddSlashSuffix(Paths.BaseThenName) & "History\"
        End Get
    End Property

    ''' <summary>实例唯一键：可读名 + 路径哈希，避免同名实例互相覆盖。</summary>
    Public Function InstanceKey(Instance As McInstance) As String
        If Instance Is Nothing Then Return "unknown"
        Dim SafeName As String = Instance.Name
        For Each C In IO.Path.GetInvalidFileNameChars()
            SafeName = SafeName.Replace(C, "_"c)
        Next
        SafeName = SafeName.Replace(" "c, "_"c)
        If SafeName.Length > 40 Then SafeName = SafeName.Substring(0, 40)
        Dim Hash As String
        Try
            Using Md5 As MD5 = MD5.Create()
                Dim Bytes = Md5.ComputeHash(Encoding.UTF8.GetBytes(PathUtils.ForCompare(Instance.PathVersion)))
                Hash = BitConverter.ToString(Bytes).Replace("-", "").Substring(0, 8)
            End Using
        Catch ex As Exception
            Logger.Warn(ex, "计算实例哈希失败")
            Hash = "00000000"
        End Try
        Return SafeName & "_" & Hash
    End Function

    Private Function SessionFile(Instance As McInstance) As String
        Return DataRoot & InstanceKey(Instance) & ".sessions.json"
    End Function

    Private Function RunningFile(Instance As McInstance) As String
        Return DataRoot & InstanceKey(Instance) & ".running.json"
    End Function

    ''' <summary>快照目录，以 \ 结尾。</summary>
    Public Function SnapshotDir(Instance As McInstance) As String
        Return DataRoot & "Snapshots\" & InstanceKey(Instance) & "\"
    End Function

#Region "游玩记录"

    ''' <summary>读取会话记录，最近的在前。</summary>
    Public Function LoadSessions(Instance As McInstance) As List(Of McSessionRecord)
        Dim Result As New List(Of McSessionRecord)
        Try
            Dim FilePath As String = SessionFile(Instance)
            If Not FileUtils.Exists(FilePath) Then Return Result
            Dim Text As String = IO.File.ReadAllText(FilePath, Encoding.UTF8)
            If String.IsNullOrWhiteSpace(Text) Then Return Result
            Dim Items = JsonConvert.DeserializeObject(Of List(Of McSessionRecord))(Text)
            If Items IsNot Nothing Then Result.AddRange(Items)
        Catch ex As Exception
            Logger.Warn(ex, "读取游玩记录失败")
        End Try
        Return Result.OrderByDescending(Function(S) S.StartLocal).ToList()
    End Function

    Private Sub SaveSessions(Instance As McInstance, Items As List(Of McSessionRecord))
        SyncLock Gate
            Try
                DirectoryUtils.Create(DataRoot)
                '只保留最近 100 条
                Dim Trimmed = Items.OrderByDescending(Function(S) S.StartLocal).Take(100).ToList()
                IO.File.WriteAllText(SessionFile(Instance), JsonConvert.SerializeObject(Trimmed, Formatting.Indented), Encoding.UTF8)
            Catch ex As Exception
                Logger.Warn(ex, "保存游玩记录失败")
            End Try
        End SyncLock
    End Sub

    ''' <summary>
    ''' 启动成功时写入「进行中」标记；若上次遗留标记，则补记一条「上次未正常记录」。
    ''' </summary>
    Public Sub BeginSession(Instance As McInstance, Player As String)
        If Instance Is Nothing Then Return
        Try
            RecoverUnfinished(Instance)
            DirectoryUtils.Create(DataRoot)
            Dim Current As New McSessionRecord With {
                .StartUtc = Date.UtcNow.ToString("o"),
                .Player = If(Player, ""),
                .Version = Instance.VersionDisplayName
            }
            IO.File.WriteAllText(RunningFile(Instance), JsonConvert.SerializeObject(Current, Formatting.Indented), Encoding.UTF8)
        Catch ex As Exception
            Logger.Warn(ex, "写入游玩会话标记失败")
        End Try
    End Sub

    ''' <summary>
    ''' 游戏进程退出时调用：把进行中的会话落盘。
    ''' </summary>
    Public Sub EndSession(Instance As McInstance, ExitCode As Integer)
        If Instance Is Nothing Then Return
        Try
            Dim FilePath As String = RunningFile(Instance)
            If Not FileUtils.Exists(FilePath) Then Return
            Dim Text As String = IO.File.ReadAllText(FilePath, Encoding.UTF8)
            Dim Current = JsonConvert.DeserializeObject(Of McSessionRecord)(Text)
            If Current Is Nothing Then Return
            Current.EndUtc = Date.UtcNow.ToString("o")
            Dim StartAt As Date
            If Date.TryParse(Current.StartUtc, Nothing, Globalization.DateTimeStyles.RoundtripKind, StartAt) Then
                Current.Seconds = Math.Max(0, CLng((Date.UtcNow - StartAt.ToUniversalTime()).TotalSeconds))
            End If
            Current.ExitCode = ExitCode
            Current.Result = If(ExitCode = 0, 0, 1)
            Dim Items = LoadSessions(Instance)
            Items.Add(Current)
            SaveSessions(Instance, Items)
            FileUtils.Delete(FilePath)
        Catch ex As Exception
            Logger.Warn(ex, "记录游玩会话失败")
        End Try
    End Sub

    ''' <summary>
    ''' 上次启动器被直接关闭时，补记一条未正常结束的会话。
    ''' </summary>
    Public Sub RecoverUnfinished(Instance As McInstance)
        Try
            Dim FilePath As String = RunningFile(Instance)
            If Not FileUtils.Exists(FilePath) Then Return
            Dim Text As String = IO.File.ReadAllText(FilePath, Encoding.UTF8)
            Dim Current = JsonConvert.DeserializeObject(Of McSessionRecord)(Text)
            If Current Is Nothing Then
                FileUtils.Delete(FilePath)
                Return
            End If
            Current.Result = 2
            Current.EndUtc = ""
            Dim StartAt As Date
            If Date.TryParse(Current.StartUtc, Nothing, Globalization.DateTimeStyles.RoundtripKind, StartAt) Then
                Current.Seconds = Math.Max(0, CLng((Date.UtcNow - StartAt.ToUniversalTime()).TotalSeconds))
            End If
            Dim Items = LoadSessions(Instance)
            Items.Add(Current)
            SaveSessions(Instance, Items)
            FileUtils.Delete(FilePath)
            Logger.Info($"补记未正常结束的游玩会话：{Instance.Name}")
        Catch ex As Exception
            Logger.Warn(ex, "补记未完成会话失败")
        End Try
    End Sub

#End Region

#Region "快照"

    ''' <summary>列出快照，最近的在前。</summary>
    Public Function ListSnapshots(Instance As McInstance) As List(Of McSnapshotRecord)
        Dim Result As New List(Of McSnapshotRecord)
        Try
            Dim Folder As String = SnapshotDir(Instance)
            If Not DirectoryUtils.Exists(Folder) Then Return Result
            Dim IndexFile As String = Folder & "index.json"
            Dim Cache As New Dictionary(Of String, McSnapshotRecord)(StringComparer.OrdinalIgnoreCase)
            If FileUtils.Exists(IndexFile) Then
                Try
                    Dim Items = JsonConvert.DeserializeObject(Of List(Of McSnapshotRecord))(IO.File.ReadAllText(IndexFile, Encoding.UTF8))
                    If Items IsNot Nothing Then
                        For Each Item In Items
                            Cache(Item.FileName) = Item
                        Next
                    End If
                Catch ex As Exception
                    Logger.Warn(ex, "读取快照索引失败（按文件重建）")
                End Try
            End If
            For Each FilePath In DirectoryUtils.EnumerateFiles(Folder, False)
                Dim Name As String = IO.Path.GetFileName(FilePath)
                If Not Name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase) Then Continue For
                Dim Info As New IO.FileInfo(FilePath)
                Dim Record As McSnapshotRecord = Nothing
                If Not Cache.TryGetValue(Name, Record) OrElse Record Is Nothing Then
                    Record = New McSnapshotRecord With {
                        .FileName = Name,
                        .CreatedUtc = Info.CreationTimeUtc.ToString("o"),
                        .Size = Info.Length,
                        .Note = ""
                    }
                Else
                    Record.Size = Info.Length
                End If
                Result.Add(Record)
            Next
        Catch ex As Exception
            Logger.Warn(ex, "列出快照失败")
        End Try
        Return Result.OrderByDescending(Function(S) S.CreatedLocal).ToList()
    End Function

    Private Sub SaveIndex(Instance As McInstance, Items As List(Of McSnapshotRecord))
        Try
            Dim Folder As String = SnapshotDir(Instance)
            DirectoryUtils.Create(Folder)
            IO.File.WriteAllText(Folder & "index.json", JsonConvert.SerializeObject(Items, Formatting.Indented), Encoding.UTF8)
        Catch ex As Exception
            Logger.Warn(ex, "写入快照索引失败")
        End Try
    End Sub

    ''' <summary>
    ''' 创建快照。WorldName 为空表示整个实例内容（不含日志与崩溃报告）。
    ''' </summary>
    Public Function CreateSnapshot(Instance As McInstance, Note As String, Optional WorldName As String = "") As String
        If Instance Is Nothing Then Throw New ArgumentNullException(NameOf(Instance))
        Dim Folder As String = SnapshotDir(Instance)
        DirectoryUtils.Create(Folder)
        Dim Stamp As String = Now.ToString("yyyyMMdd-HHmmss")
        Dim FileName As String = "snapshot-" & Stamp & ".zip"
        Dim Target As String = Folder & FileName
        Dim Counter As Integer = 1
        While FileUtils.Exists(Target)
            Counter += 1
            FileName = "snapshot-" & Stamp & "-" & Counter & ".zip"
            Target = Folder & FileName
        End While
        Dim Source As String
        If String.IsNullOrEmpty(WorldName) Then
            Source = Instance.PathIndie
            ModArchive.CreateZip(Source, Target, {"session.lock", "usercache.json"}, {"logs", "crash-reports", "screenshots"})
        Else
            Source = PathUtils.AddSlashSuffix(Instance.PathIndie) & "saves\" & WorldName
            If Not DirectoryUtils.Exists(Source) Then Throw New IO.DirectoryNotFoundException(Source)
            ModArchive.CreateZip(Source, Target, {"session.lock"})
        End If
        Dim Record As New McSnapshotRecord With {
            .FileName = FileName,
            .CreatedUtc = Date.UtcNow.ToString("o"),
            .Note = If(Note, ""),
            .Scope = If(WorldName, ""),
            .Size = New IO.FileInfo(Target).Length
        }
        Dim Items = ListSnapshots(Instance)
        Items.Add(Record)
        SaveIndex(Instance, Items)
        Logger.Info($"已创建快照：{Target}（{Record.ScopeText}）")
        Return Target
    End Function

    ''' <summary>
    ''' 恢复快照：先把当前状态自动存一份，再解压覆盖。只覆盖快照里包含的文件。
    ''' </summary>
    Public Function RestoreSnapshot(Instance As McInstance, Snapshot As McSnapshotRecord) As String
        If Instance Is Nothing OrElse Snapshot Is Nothing Then Throw New ArgumentNullException()
        Dim FilePath As String = SnapshotDir(Instance) & Snapshot.FileName
        If Not FileUtils.Exists(FilePath) Then Throw New IO.FileNotFoundException(FilePath)
        Dim Safety As String = CreateSnapshot(Instance, "恢复「" & Snapshot.FileName & "」前的自动备份", Snapshot.Scope)
        ModArchive.ExtractZip(FilePath, Instance.PathIndie)
        Logger.Info($"已恢复快照：{FilePath}（自动备份：{Safety}）")
        Return Safety
    End Function

    Public Sub DeleteSnapshot(Instance As McInstance, Snapshot As McSnapshotRecord)
        If Instance Is Nothing OrElse Snapshot Is Nothing Then Return
        Dim FilePath As String = SnapshotDir(Instance) & Snapshot.FileName
        If FileUtils.Exists(FilePath) Then FileUtils.Delete(FilePath)
        Dim Items = ListSnapshots(Instance).Where(Function(S) Not String.Equals(S.FileName, Snapshot.FileName, StringComparison.OrdinalIgnoreCase)).ToList()
        SaveIndex(Instance, Items)
    End Sub

#End Region

End Module
