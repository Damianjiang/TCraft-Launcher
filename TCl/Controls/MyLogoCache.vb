''' <summary>
''' 列表 / 卡片图标位图的共享缓存。
''' <para/>
''' 原先 `MyListItem` 与 `PageSelectRight` 各维护一份「路径 → (修改时间, MyBitmap)」字典，
''' 逻辑几乎逐行相同：未命中或文件已变更就重新解码，超过上限清空。合并成一份，
''' 顺带把上限与淘汰行为统一（两处原来都是 256 条清空重建）。
''' </summary>
Friend Module MyLogoCache

    Private ReadOnly Cache As New Dictionary(Of String, KeyValuePair(Of Date, MyBitmap))(StringComparer.OrdinalIgnoreCase)
    Private ReadOnly CacheLock As New Object
    Private Const CacheCap As Integer = 256

    ''' <summary>
    ''' 取缓存位图；未命中或文件修改时间变化时重新解码。
    ''' 解码失败返回 Nothing（与调用方原来的「退回矢量图标」行为一致）。
    ''' </summary>
    Public Function [Get](FilePath As String) As MyBitmap
        If String.IsNullOrEmpty(FilePath) Then Return Nothing
        Dim MTime As Date
        Try
            MTime = IO.File.GetLastWriteTime(FilePath)
        Catch ex As Exception
            '连修改时间都拿不到：直接尝试解码一次，不写缓存
            Try
                Return New MyBitmap(FilePath)
            Catch ex2 As Exception
                Return Nothing
            End Try
        End Try
        SyncLock CacheLock
            Dim Hit As KeyValuePair(Of Date, MyBitmap) = Nothing
            If Cache.TryGetValue(FilePath, Hit) AndAlso Hit.Key = MTime AndAlso Hit.Value IsNot Nothing Then Return Hit.Value
        End SyncLock
        Dim Bmp As MyBitmap
        Try
            Bmp = New MyBitmap(FilePath)
        Catch ex As Exception
            Return Nothing
        End Try
        SyncLock CacheLock
            If Cache.Count >= CacheCap Then Cache.Clear() '图标总量不大，超限直接清空重建
            Cache(FilePath) = New KeyValuePair(Of Date, MyBitmap)(MTime, Bmp)
        End SyncLock
        Return Bmp
    End Function

End Module
