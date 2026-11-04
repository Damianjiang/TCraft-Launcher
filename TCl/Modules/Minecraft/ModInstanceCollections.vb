Imports Newtonsoft.Json

''' <summary>
''' 实例合集（标签）存储（复刻 LumilioCL 的「合集」：标签式分组，不移动任何文件）。
''' 合集清单存在启动器数据目录（Collections.json），实例归属写在实例自己的 TCL\Setup.ini 里，
''' 这样换启动器或搬动实例目录时归属会跟着走。
''' </summary>
Public Module ModInstanceCollections

    Private ReadOnly Gate As New Object
    Private Const StoreName As String = "Collections.json"
    ''' <summary>合集名里不允许出现的字符（Setup.ini 用 : 分隔键值，| 用来分隔合集）。</summary>
    Private ReadOnly InvalidChars As Char() = {":"c, "|"c, vbCr, vbLf}

    Public ReadOnly Property StorePath As String
        Get
            Return PathUtils.AddSlashSuffix(Paths.BaseThenName) & StoreName
        End Get
    End Property

    Private Class Store
        Public Property Version As Integer = 1
        Public Property Collections As New List(Of String)
    End Class

    ''' <summary>校验合集名，返回空字符串表示合法。</summary>
    Public Function ValidateName(Name As String, Optional IgnoreName As String = "") As String
        Dim Trimmed As String = If(Name, "").Trim()
        If Trimmed.Length = 0 Then Return ModLang.L("合集名不能为空。")
        If Trimmed.Length > 32 Then Return ModLang.L("合集名不能超过 32 个字符。")
        If Trimmed.IndexOfAny(InvalidChars) >= 0 Then Return ModLang.L("合集名不能包含 : 或 | 。")
        If Trimmed <> IgnoreName AndAlso ListAll().Contains(Trimmed) Then Return ModLang.L("已存在同名合集。")
        Return ""
    End Function

    ''' <summary>读取全部合集名（保持用户排序）。</summary>
    Public Function ListAll() As List(Of String)
        SyncLock Gate
            Try
                If Not FileUtils.Exists(StorePath) Then Return New List(Of String)
                Dim Text As String = IO.File.ReadAllText(StorePath, Encoding.UTF8)
                If String.IsNullOrWhiteSpace(Text) Then Return New List(Of String)
                Dim Data = JsonConvert.DeserializeObject(Of Store)(Text)
                If Data Is Nothing OrElse Data.Collections Is Nothing Then Return New List(Of String)
                Return Data.Collections.Where(Function(One) Not String.IsNullOrWhiteSpace(One)).Distinct().ToList()
            Catch ex As Exception
                Logger.Warn(ex, "读取实例合集失败")
                Return New List(Of String)
            End Try
        End SyncLock
    End Function

    Public Sub SaveAll(Items As List(Of String))
        SyncLock Gate
            Try
                DirectoryUtils.Create(IO.Path.GetDirectoryName(StorePath))
                Dim Data As New Store With {
                    .Collections = If(Items, New List(Of String)).Where(Function(One) Not String.IsNullOrWhiteSpace(One)).Distinct().ToList()
                }
                IO.File.WriteAllText(StorePath, JsonConvert.SerializeObject(Data, Formatting.Indented), Encoding.UTF8)
            Catch ex As Exception
                Logger.Warn(ex, "保存实例合集失败")
            End Try
        End SyncLock
    End Sub

    ''' <summary>确保合集存在（不存在则追加到清单末尾）。返回是否新建。</summary>
    Public Function Ensure(Name As String) As Boolean
        Dim Trimmed As String = If(Name, "").Trim()
        If Trimmed.Length = 0 Then Return False
        Dim Items = ListAll()
        If Items.Contains(Trimmed) Then Return False
        Items.Add(Trimmed)
        SaveAll(Items)
        Return True
    End Function

    ''' <summary>重命名合集，并同步所有可见实例上的归属。</summary>
    Public Sub Rename(OldName As String, NewName As String)
        Dim Trimmed As String = If(NewName, "").Trim()
        If String.IsNullOrWhiteSpace(OldName) OrElse Trimmed.Length = 0 Then Return
        Dim Items = ListAll()
        Dim Index As Integer = Items.IndexOf(OldName)
        If Index < 0 Then Return
        Items(Index) = Trimmed
        SaveAll(Items)
        '同步实例归属
        For Each One In AllInstances()
            Dim Owned = GetOf(One)
            If Owned.Remove(OldName) Then
                If Not Owned.Contains(Trimmed) Then Owned.Add(Trimmed)
                SetOf(One, Owned)
            End If
        Next
    End Sub

    ''' <summary>删除合集（只删标签，不影响实例本身）。</summary>
    Public Sub Delete(Name As String)
        If String.IsNullOrWhiteSpace(Name) Then Return
        Dim Items = ListAll()
        If Not Items.Remove(Name) Then Return
        SaveAll(Items)
        For Each One In AllInstances()
            Dim Owned = GetOf(One)
            If Owned.Remove(Name) Then SetOf(One, Owned)
        Next
    End Sub

    ''' <summary>取某个实例所属的合集。</summary>
    Public Function GetOf(Instance As McInstance) As List(Of String)
        Dim Result As New List(Of String)
        If Instance Is Nothing Then Return Result
        Try
            Dim Raw As String = ReadIni(Instance.PathVersion & "TCL\Setup.ini", "Collections", "")
            If String.IsNullOrWhiteSpace(Raw) Then Return Result
            For Each Part In Raw.Split("|"c)
                Dim One As String = Part.Trim()
                If One.Length > 0 AndAlso Not Result.Contains(One) Then Result.Add(One)
            Next
        Catch ex As Exception
            Logger.Warn(ex, $"读取实例合集失败：{Instance.Name}")
        End Try
        Return Result
    End Function

    ''' <summary>写回某个实例的合集归属。</summary>
    Public Sub SetOf(Instance As McInstance, Items As List(Of String))
        If Instance Is Nothing Then Return
        Try
            Dim Clean As New List(Of String)
            If Items IsNot Nothing Then
                For Each One In Items
                    Dim Trimmed As String = If(One, "").Trim()
                    If Trimmed.Length > 0 AndAlso Not Clean.Contains(Trimmed) Then Clean.Add(Trimmed)
                Next
            End If
            WriteIni(Instance.PathVersion & "TCL\Setup.ini", "Collections", String.Join("|", Clean.ToArray()))
        Catch ex As Exception
            Logger.Warn(ex, $"写入实例合集失败：{Instance.Name}")
        End Try
    End Sub

    ''' <summary>把实例加入某个合集。</summary>
    Public Sub AddTo(Instance As McInstance, Name As String)
        Dim Trimmed As String = If(Name, "").Trim()
        If Trimmed.Length = 0 Then Return
        Ensure(Trimmed)
        Dim Owned = GetOf(Instance)
        If Owned.Contains(Trimmed) Then Return
        Owned.Add(Trimmed)
        SetOf(Instance, Owned)
    End Sub

    ''' <summary>把实例移出某个合集。</summary>
    Public Sub RemoveFrom(Instance As McInstance, Name As String)
        Dim Owned = GetOf(Instance)
        If Not Owned.Remove(Name) Then Return
        SetOf(Instance, Owned)
    End Sub

    ''' <summary>当前实例列表里的全部实例（用于批量同步归属）。</summary>
    Private Function AllInstances() As List(Of McInstance)
        Dim Result As New List(Of McInstance)
        Try
            For Each Pair In McInstanceList.ToArray
                If Pair.Value IsNot Nothing Then Result.AddRange(Pair.Value)
            Next
        Catch ex As Exception
            Logger.Warn(ex, "枚举实例列表失败")
        End Try
        Return Result
    End Function

End Module
