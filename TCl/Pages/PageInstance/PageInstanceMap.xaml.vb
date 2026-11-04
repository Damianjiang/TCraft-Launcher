''' <summary>
''' 实例世界地图页（第 127 轮，参照 LumilioCL 世界探索器）。
'''
''' 渲染调度为「请求 - 应答」式，没有任何常驻循环：
''' · 视口变化 → 重算可见瓦片清单（按距视口中心排序）→ 唤醒单个后台渲染线程；
''' · 渲染线程每张瓦片逐区块检查取消令牌；
''' · PageExit / Unloaded / 换世界时世代号 +1 + 取消令牌作废 + 清空待渲染与已渲染瓦片——
'''   页面切走后不再渲染、不再持有位图内存（上限 128 张 ≈ 32MB，仅停留期间）。
''' </summary>
Public Class PageInstanceMap

    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property

#Region "世界选择"

    Private Sub PageInstanceMap_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        RefreshWorlds()
    End Sub

    ''' <summary>重新载入世界列表（主控切换页面时调用）。</summary>
    Public Sub Reload()
        RefreshWorlds()
    End Sub

    Private Sub RefreshWorlds()
        Try
            TeardownRender()
            ComboWorld.Items.Clear()
            WorldList.Clear()
            LabInfo.Text = ModLang.L("选择一个存档即可查看它已探索的地形。")
            If Instance Is Nothing Then
                LabInfo.Text = ModLang.L("尚未选择实例。")
                Return
            End If
            Dim SavesFolder As String = Instance.PathIndie & "saves\"
            If Not DirectoryUtils.Exists(SavesFolder) Then
                LabInfo.Text = ModLang.L("该实例还没有任何存档。")
                Return
            End If
            For Each Folder In DirectoryUtils.EnumerateDirectories(SavesFolder, False)
                Try
                    WorldList.Add(DirectoryUtils.GetInfo(Folder))
                Catch ex As Exception
                    Logger.Warn(ex, $"获取存档文件夹信息失败：{Folder}")
                End Try
            Next
            WorldList = WorldList.OrderByDescending(Function(w) w.LastWriteTimeUtc).ToList()
            For Each World In WorldList
                ComboWorld.Items.Add(World.Name)
            Next
            If WorldList.Any() Then
                ComboWorld.SelectedIndex = 0 '默认最近游玩的存档
            Else
                LabInfo.Text = ModLang.L("该实例还没有任何存档。")
            End If
        Catch ex As Exception
            Logger.Error(ex, "刷新世界列表失败")
        End Try
    End Sub

    Private Sub ComboWorld_Changed(sender As Object, e As SelectionChangedEventArgs) Handles ComboWorld.SelectionChanged
        If ComboWorld.SelectedIndex < 0 OrElse ComboWorld.SelectedIndex >= WorldList.Count Then Return
        SwitchWorld(WorldList(ComboWorld.SelectedIndex).FullName)
    End Sub

    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnRefresh.Click
        RefreshWorlds()
    End Sub

    ''' <summary>切换世界：世代号推进使一切在途结果作废，然后重置视图并请求首批瓦片。</summary>
    Private Sub SwitchWorld(WorldFolder As String)
        TeardownRender()
        CurrentWorldFolder = WorldFolder
        RegionFolder = PathUtils.AddSlashSuffix(WorldFolder) & "region\"
        If Not DirectoryUtils.Exists(RegionFolder) Then
            LabInfo.Text = ModLang.L("该存档还没有已生成的地形（region 文件夹不存在）。")
            Return
        End If
        LabInfo.Text = ModLang.L("正在加载已探索地形……")
        ViewportScale = 0.25
        TransScale.ScaleX = ViewportScale
        TransScale.ScaleY = ViewportScale
        TransMove.X = PanViewport.ActualWidth / 2 - 128 * ViewportScale '先以世界原点（瓦片 0,0）为中心
        TransMove.Y = PanViewport.ActualHeight / 2 - 128 * ViewportScale
        UpdateRequests()
    End Sub

    ''' <summary>重置视口到已渲染内容的范围。</summary>
    Private Sub BtnFit_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnFit.Click
        If Not TileCache.Any() Then Return
        Dim MinX As Integer = Integer.MaxValue, MaxX As Integer = Integer.MinValue
        Dim MinZ As Integer = Integer.MaxValue, MaxZ As Integer = Integer.MinValue
        SyncLock TileCacheLock
            For Each Key In TileCache.Keys
                Dim Parts = Key.Split(","c)
                Dim Tx = CInt(Parts(0)), Tz = CInt(Parts(1))
                MinX = Math.Min(MinX, Tx) : MaxX = Math.Max(MaxX, Tx + 1)
                MinZ = Math.Min(MinZ, Tz) : MaxZ = Math.Max(MaxZ, Tz + 1)
            Next
        End SyncLock
        Dim ExtentW As Double = (MaxX - MinX) * ModInstanceMap.TilePixels
        Dim ExtentH As Double = (MaxZ - MinZ) * ModInstanceMap.TilePixels
        If ExtentW <= 0 OrElse ExtentH <= 0 Then Return
        ViewportScale = (Math.Min(PanViewport.ActualWidth / ExtentW, PanViewport.ActualHeight / ExtentH) * 0.9).Clamp(0.03125, 8)
        TransScale.ScaleX = ViewportScale
        TransScale.ScaleY = ViewportScale
        TransMove.X = PanViewport.ActualWidth / 2 - (MinX + (MaxX - MinX) / 2) * ModInstanceMap.TilePixels * ViewportScale
        TransMove.Y = PanViewport.ActualHeight / 2 - (MinZ + (MaxZ - MinZ) / 2) * ModInstanceMap.TilePixels * ViewportScale
        UpdateRequests()
    End Sub

#End Region

#Region "渲染调度（请求 - 应答，无常驻循环）"

    Private WorldList As New List(Of DirectoryInfo)
    Private CurrentWorldFolder As String = Nothing
    Private RegionFolder As String = Nothing

    ''' <summary>世代号：换世界 / 页面退出时 +1，携带旧世代号的结果一律丢弃。</summary>
    Private MapSerial As Integer = 0
    Private RenderCancel As CancellationTokenSource = Nothing
    Private ReadOnly WakeEvent As New AutoResetEvent(False)
    Private ReadOnly PendingLock As New Object
    Private PendingList As New List(Of KeyValuePair(Of Integer, Integer)) '待渲染瓦片，按距视口中心升序
    Private ReadOnly TileCacheLock As New Object
    Private TileCache As New Dictionary(Of String, Image) '已渲染瓦片
    Private TileUsed As New Dictionary(Of String, Long)   '最后使用时刻（LRU 逐出用）
    Private Const TileCacheCap As Integer = 128           '≈ 32MB 位图内存上限
    Private IsWorkerRunning As Boolean = False
    Private ViewportScale As Double = 1.0
    Private EmptyTiles As Integer = 0                     '「区域文件不存在」占位标记的数量

    ''' <summary>页面退出 / 隐藏：全停——取消令牌作废、待渲染清空、位图内存释放。</summary>
    Private Sub TeardownRender()
        MapSerial += 1
        If RenderCancel IsNot Nothing Then RenderCancel.Cancel() : RenderCancel.Dispose() : RenderCancel = Nothing
        SyncLock PendingLock
            PendingList.Clear()
        End SyncLock
        SyncLock TileCacheLock
            PanTiles.Children.Clear()
            TileCache.Clear()
            TileUsed.Clear()
            EmptyTiles = 0
        End SyncLock
        WakeEvent.Set() '让正在等待的渲染线程醒来看见新世代号后退出
    End Sub

    Private Sub PageInstanceMap_PageExit() Handles Me.PageExit
        TeardownRender()
    End Sub

    Private Sub PageInstanceMap_Unloaded(sender As Object, e As RoutedEventArgs) Handles Me.Unloaded
        TeardownRender()
    End Sub

    Private Sub PageInstanceMap_PageEnter() Handles Me.PageEnter
        UpdateRequests()
    End Sub

    ''' <summary>按当前视口重算需要的瓦片：可见范围 → 距中心排序 → 与待渲染列表求差。</summary>
    Private Sub UpdateRequests()
        If RegionFolder Is Nothing OrElse PanViewport.ActualWidth < 10 Then Return
        DistanceMemo.Clear()
        '可见世界坐标范围（世界方块坐标 → 瓦片坐标 = floor(方块 / 256)）
        Dim LeftWorld As Double = -TransMove.X / ViewportScale
        Dim TopWorld As Double = -TransMove.Y / ViewportScale
        Dim RightWorld As Double = LeftWorld + PanViewport.ActualWidth / ViewportScale
        Dim BottomWorld As Double = TopWorld + PanViewport.ActualHeight / ViewportScale
        Dim MinTx As Integer = CInt(Math.Floor(LeftWorld / ModInstanceMap.TilePixels)) - 1
        Dim MaxTx As Integer = CInt(Math.Floor(RightWorld / ModInstanceMap.TilePixels)) + 1
        Dim MinTz As Integer = CInt(Math.Floor(TopWorld / ModInstanceMap.TilePixels)) - 1
        Dim MaxTz As Integer = CInt(Math.Floor(BottomWorld / ModInstanceMap.TilePixels)) + 1
        Dim CenterX As Double = (LeftWorld + RightWorld) / 2, CenterZ As Double = (TopWorld + BottomWorld) / 2
        Dim Desired As New List(Of KeyValuePair(Of Integer, Integer))
        For Tx As Integer = MinTx To MaxTx
            For Tz As Integer = MinTz To MaxTz
                If HasTile(Tx, Tz) Then Continue For
                Dim DX As Double = (Tx + 0.5) * ModInstanceMap.TilePixels - CenterX
                Dim DZ As Double = (Tz + 0.5) * ModInstanceMap.TilePixels - CenterZ
                Desired.Add(New KeyValuePair(Of Integer, Integer)(Tx, Tz))
                '用距离排序：存距离到 Key 复用排序比较
                DistanceMemo(New KeyValuePair(Of Integer, Integer)(Tx, Tz)) = Math.Sqrt(DX * DX + DZ * DZ)
            Next
        Next
        Desired = Desired.OrderBy(Function(t) DistanceMemo(t)).ToList()
        SyncLock PendingLock
            '去掉已经滚出视野的请求（LumilioCL MapSchedule.update 的取消逻辑）
            PendingList = PendingList.Where(Function(t) Desired.Any(Function(d) d.Key = t.Key AndAlso d.Value = t.Value)).ToList()
            For Each Tile In Desired
                If PendingList.Count >= 64 Then Exit For '请求上限，避免一次缩放囤积几百张
                If Not PendingList.Any(Function(p) p.Key = Tile.Key AndAlso p.Value = Tile.Value) Then
                    PendingList.Add(Tile)
                End If
            Next
        End SyncLock
        If PendingList.Any() Then
            StartWorkerIfNeed()
            WakeEvent.Set()
        End If
        EvictFarTiles()
    End Sub

    ''' <summary>排序用的临时距离表（UpdateRequests 内构造后立即消费）。</summary>
    Private DistanceMemo As New Dictionary(Of KeyValuePair(Of Integer, Integer), Double)

    Private Function HasTile(Tx As Integer, Tz As Integer) As Boolean
        SyncLock TileCacheLock
            Return TileCache.ContainsKey(Tx & "," & Tz)
        End SyncLock
    End Function

    ''' <summary>确保渲染线程存在（单线程；退出条件：世代号变化时自然结束）。</summary>
    Private Sub StartWorkerIfNeed()
        If IsWorkerRunning Then Return
        IsWorkerRunning = True
        Dim MySerial As Integer = MapSerial
        RunInNewThread(
        Sub()
            Try
                Do While MySerial = MapSerial
                    Dim Tile As KeyValuePair(Of Integer, Integer)
                    SyncLock PendingLock
                        If Not PendingList.Any() Then Exit Do
                        Tile = PendingList(0)
                        PendingList.RemoveAt(0)
                    End SyncLock
                    Dim MyCancel = RenderCancel
                    If MyCancel Is Nothing Then Exit Do
                    '渲染（后台，逐块/逐 band 可取消）
                    Dim Result As Byte() = Nothing
                    Try
                        Result = ModInstanceMap.RenderTile(RegionFolder, Tile.Key, Tile.Value, MyCancel.Token)
                    Catch ex As OperationCanceledException
                        Exit Do
                    Catch ex As Exception
                        Logger.Warn(ex, $"渲染地图瓦片失败（{Tile.Key}）")
                    End Try
                    If MySerial <> MapSerial Then Exit Do '中途换世界/退出：结果作废
                    RunInUi(Sub() ApplyTile(MySerial, Tile, Result))
                Loop
            Catch ex As Exception
                Logger.Error(ex, "地图渲染线程异常")
            Finally
                IsWorkerRunning = False
                If MySerial = MapSerial Then
                    '本批请求全部结束：若一张有效瓦片都没有，说明该存档没有已生成地形
                    RunInUi(
                    Sub()
                        If MySerial = MapSerial AndAlso RegionFolder IsNot Nothing AndAlso TileCache.Count <= EmptyTiles Then
                            SyncLock PendingLock
                                If Not PendingList.Any() Then
                                    LabInfo.Text = ModLang.L("没有找到已生成的地形（世界地图 v1 仅支持 1.18+ 存档的主世界 region 文件）。")
                                End If
                            End SyncLock
                        End If
                    End Sub)
                    SyncLock PendingLock
                        If PendingList.Any() Then StartWorkerIfNeed() : WakeEvent.Set()
                    End SyncLock
                End If
            End Try
        End Sub, "InstanceMap Render")
    End Sub

    ''' <summary>把渲染结果放上画布（UI 线程）；世代号不符则丢弃。</summary>
    Private Sub ApplyTile(Serial As Integer, Tile As KeyValuePair(Of Integer, Integer), Result As Byte())
        If Serial <> MapSerial OrElse RegionFolder Is Nothing Then Return
        Dim Key As String = Tile.Key & "," & Tile.Value
        SyncLock TileCacheLock
            If TileCache.ContainsKey(Key) Then Return
            If Result Is Nothing Then
                '区域文件不存在：放一个空标记防止反复请求（1 像素占位）
                TileCache(Key) = New Image With {.Width = 0.01, .Height = 0.01, .Opacity = 0}
                TileUsed(Key) = GetTimeMs()
                EmptyTiles += 1
                Return
            End If
            Dim Bitmap As New WriteableBitmap(ModInstanceMap.TilePixels, ModInstanceMap.TilePixels, 96, 96, PixelFormats.Bgra32, Nothing)
            Bitmap.WritePixels(New Int32Rect(0, 0, ModInstanceMap.TilePixels, ModInstanceMap.TilePixels), Result, ModInstanceMap.TilePixels * 4, 0)
            Dim Img As New Image With {.Source = Bitmap, .Width = ModInstanceMap.TilePixels, .Height = ModInstanceMap.TilePixels}
            Canvas.SetLeft(Img, Tile.Key * ModInstanceMap.TilePixels)
            Canvas.SetTop(Img, Tile.Value * ModInstanceMap.TilePixels)
            PanTiles.Children.Add(Img)
            TileCache(Key) = Img
            TileUsed(Key) = GetTimeMs()
        End SyncLock
        If TileCache.Count = 1 OrElse TileCache.Count Mod 32 = 0 Then
            LabInfo.Text = ModLang.L("已加载 ") & TileCache.Count & ModLang.L(" 张地图瓦片，拖动或缩放查看更多。")
        End If
    End Sub

    ''' <summary>超过上限时按「最后使用时间」逐出最久未用且不在可见区的瓦片。</summary>
    Private Sub EvictFarTiles()
        SyncLock TileCacheLock
            If TileCache.Count <= TileCacheCap Then Return
            Dim Visible As New HashSet(Of String)
            For Each Tile In PendingList
                Visible.Add(Tile.Key & "," & Tile.Value)
            Next
            For Each Candidate In TileUsed.OrderBy(Function(k) k.Value).Select(Function(k) k.Key).ToList()
                If TileCache.Count <= TileCacheCap Then Exit For
                If Visible.Contains(Candidate) Then Continue For
                Dim Img As Image = Nothing
                If TileCache.TryGetValue(Candidate, Img) Then
                    If PanTiles.Children.Contains(Img) Then PanTiles.Children.Remove(Img)
                    Img.Source = Nothing '释放位图引用
                    TileCache.Remove(Candidate)
                End If
                TileUsed.Remove(Candidate)
            Next
        End SyncLock
    End Sub

#End Region


#Region "视口交互（拖动 / 缩放 / 坐标）"

    Private IsDragging As Boolean = False
    Private DragStartPos As Point
    Private DragStartMove As Point

    Private Sub PanViewport_MouseLeftButtonDown(sender As Object, e As MouseButtonEventArgs) Handles PanViewport.MouseLeftButtonDown
        IsDragging = True
        DragStartPos = e.GetPosition(PanViewport)
        DragStartMove = New Point(TransMove.X, TransMove.Y)
        PanViewport.CaptureMouse()
    End Sub

    Private Sub PanViewport_MouseMove(sender As Object, e As MouseEventArgs) Handles PanViewport.MouseMove
        If IsDragging Then
            Dim Pos As Point = e.GetPosition(PanViewport)
            TransMove.X = DragStartMove.X + (Pos.X - DragStartPos.X)
            TransMove.Y = DragStartMove.Y + (Pos.Y - DragStartPos.Y)
            UpdateRequests()
        End If
        UpdateCoordLabel(e)
    End Sub

    Private Sub PanViewport_MouseLeftButtonUp(sender As Object, e As MouseButtonEventArgs) Handles PanViewport.MouseLeftButtonUp
        IsDragging = False
        PanViewport.ReleaseMouseCapture()
        UpdateRequests()
    End Sub

    Private Sub PanViewport_MouseWheel(sender As Object, e As MouseWheelEventArgs) Handles PanViewport.MouseWheel
        Dim Factor As Double = If(e.Delta > 0, 1.25, 0.8)
        Dim NewScale As Double = (ViewportScale * Factor).Clamp(0.03125, 8)
        If Math.Abs(NewScale - ViewportScale) < 0.000001 Then Return
        '以光标为锚点缩放：光标下的世界坐标保持不动
        Dim Pos As Point = e.GetPosition(PanViewport)
        Dim WorldX As Double = (Pos.X - TransMove.X) / ViewportScale
        Dim WorldZ As Double = (Pos.Y - TransMove.Y) / ViewportScale
        ViewportScale = NewScale
        TransScale.ScaleX = ViewportScale
        TransScale.ScaleY = ViewportScale
        TransMove.X = Pos.X - WorldX * ViewportScale
        TransMove.Y = Pos.Y - WorldZ * ViewportScale
        UpdateRequests()
    End Sub

    Private Sub UpdateCoordLabel(e As MouseEventArgs)
        If RegionFolder Is Nothing Then Return
        Dim Pos As Point = e.GetPosition(PanViewport)
        Dim WorldX As Integer = CInt(Math.Floor((Pos.X - TransMove.X) / ViewportScale))
        Dim WorldZ As Integer = CInt(Math.Floor((Pos.Y - TransMove.Y) / ViewportScale))
        LabCoord.Text = "X: " & WorldX & "   Z: " & WorldZ & "   缩放：1 像素 = " & Math.Round(1 / ViewportScale, 2) & " 方块"
    End Sub

#End Region

End Class
