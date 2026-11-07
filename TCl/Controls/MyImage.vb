Public Class MyImage
    Inherits Image

#Region "公开属性"

    ''' <summary>
    ''' 网络图片的缓存有效期。
    ''' 在这个时间后，才会重新尝试下载图片。
    ''' </summary>
    Public FileCacheExpiredTime As New TimeSpan(7, 0, 0, 0) '7 天

    ''' <summary>
    ''' 是否允许将网络图片存储到本地用作缓存。
    ''' </summary>
    Public Property EnableCache As Boolean
        Get
            Return GetValue(EnableCacheProperty)
        End Get
        Set(value As Boolean)
            SetValue(EnableCacheProperty, value)
        End Set
    End Property
    Public Shared Shadows ReadOnly EnableCacheProperty As DependencyProperty = DependencyProperty.Register(
        "EnableCache", GetType(Boolean), GetType(MyImage), New PropertyMetadata(True))

    ''' <summary>
    ''' 与 Image 的 Source 类似。
    ''' 若输入以 http 开头的字符串，则会尝试下载图片然后显示；若 EnableCache 设为 True，图片还会保存为本地缓存。
    ''' 支持 WebP 格式的图片。
    ''' </summary>
    Public Shadows Property Source As String '覆写 Image 的 Source 属性
        Get
            Return _Source
        End Get
        Set(value As String)
            If value = "" Then value = Nothing
            If _Source = value Then Return
            _Source = value
            If Not IsInitialized Then Return '属性读取顺序修正：在完成 XAML 属性读取后再触发图片加载
            Load()
        End Set
    End Property
    Private _Source As String = ""
    Public Shared Shadows ReadOnly SourceProperty As DependencyProperty = DependencyProperty.Register(
        "Source", GetType(String), GetType(MyImage), New PropertyMetadata(New PropertyChangedCallback(
    Sub(sender, e) If sender IsNot Nothing Then CType(sender, MyImage).Source = e.NewValue.ToString())))

    ''' <summary>
    ''' 若 Source 是一个网络图片，该地址将作为第二图片源。
    ''' </summary>
    Public Property FallbackSource As String
        Get
            Return _FallbackSource
        End Get
        Set(value As String)
            _FallbackSource = value
        End Set
    End Property
    Private _FallbackSource As String = Nothing

    ''' <summary>
    ''' 正在下载网络图片时显示的本地图片。
    ''' </summary>
    Public Property LoadingSource As String
        Get
            Return _LoadingSource
        End Get
        Set(value As String)
            _LoadingSource = value
        End Set
    End Property
    Private _LoadingSource As String = "pack://application:,,,/images/Icons/NoIcon.png"

#End Region

    ''' <summary>
    ''' 实际被呈现的图片地址。
    ''' </summary>
    Public Property ActualSource As String
        Get
            Return _ActualSource
        End Get
        Set(value As String)
            If value = "" Then value = Nothing
            If _ActualSource = value Then Return
            _ActualSource = value
            If value Is Nothing Then
                RunInUi(Sub() MyBase.Source = Nothing)
                Return
            End If
            '内置资源：同步加载（MyBitmap 内部已有缓存）
            If value.StartsWithF("pack://", True) Then
                Try
                    Dim Bitmap As ImageSource = New MyBitmap(value)
                    RunInUi(Sub() MyBase.Source = Bitmap)
                Catch ex As Exception
                    Logger.Warn(ex, $"加载图片失败（{value}）")
                End Try
                Return
            End If
            '本地文件：优先命中缓存，未命中则在后台线程解码，避免阻塞 UI
            Dim Info = FileUtils.GetInfo(value)
            Dim CacheKey As String = If(Info.Exists, value & "|" & Info.LastWriteTimeUtc.Ticks & "|" & Info.Length, value)
            Dim Cached As ImageSource = Nothing
            If LocalImageCache.TryGetValue(CacheKey, Cached) Then
                RunInUi(Sub() MyBase.Source = Cached)
                Return
            End If
            RunInNewThread(
            Sub()
                Try
                    Dim Bitmap As ImageSource = New MyBitmap(value)
                    If Info.Exists AndAlso Info.Length < 4 * 1024 * 1024 AndAlso Bitmap.Width * Bitmap.Height <= 262144 Then
                        If LocalImageCache.TryAdd(CacheKey, Bitmap) Then
                            LocalImageCacheKeys.Enqueue(CacheKey)
                            Do While LocalImageCacheKeys.Count > LocalImageCacheLimit
                                Dim OldKey As String = Nothing
                                If Not LocalImageCacheKeys.TryDequeue(OldKey) Then Exit Do
                                Dim Removed As ImageSource = Nothing
                                LocalImageCache.TryRemove(OldKey, Removed)
                            Loop
                        End If
                    End If
                    RunInUi(
                    Sub()
                        If _ActualSource = value Then MyBase.Source = Bitmap
                    End Sub)
                Catch ex As Exception
                    Logger.Warn(ex, $"加载图片失败（{value}）")
                    Try
                        If value.StartsWithF(PathTemp, True) Then FileUtils.Delete(value)
                    Catch ex2 As Exception
                        Logger.Warn(ex2, "清理临时图片文件失败")
                    End Try
                End Try
            End Sub, "MyImage LocalLoader " & GetUuid() & "#", ThreadPriority.BelowNormal)
        End Set
    End Property
    Private _ActualSource As String = Nothing
    Private Shared ReadOnly LocalImageCache As New Concurrent.ConcurrentDictionary(Of String, ImageSource)(StringComparer.OrdinalIgnoreCase)
    Private Shared ReadOnly LocalImageCacheKeys As New Concurrent.ConcurrentQueue(Of String)
    Private Const LocalImageCacheLimit As Integer = 256

    Private Sub Load() _
        Handles Me.Initialized '属性读取顺序修正：在完成 XAML 属性读取后再触发图片加载
        '空
        If Source Is Nothing Then
            ActualSource = Nothing
            Return
        End If
        '本地图片
        If Not Source.StartsWithF("http") Then
            ActualSource = Source
            Return
        End If
        '从缓存加载网络图片
        Dim EnableCache As Boolean = Me.EnableCache
        Dim TempPath As String = $"{PathTemp}MyImage\{Source.GetStableHashCode()}.png{If(EnableCache, "", GetUuid())}" '不启用缓存时在末尾加上随机字符串，避免冲突
        Dim TempFile = FileUtils.GetInfo(TempPath)
        If EnableCache AndAlso TempFile.Exists Then
            ActualSource = TempPath '显示缓存的图片
            If (Date.Now - TempFile.LastWriteTime) < FileCacheExpiredTime Then Return '无需刷新缓存
        Else
            ActualSource = LoadingSource '显示加载中图片
        End If
        RunInNewThread(
        Sub()
            Dim IsLocalFallback As Boolean = Not String.IsNullOrEmpty(FallbackSource) AndAlso Not FallbackSource.StartsWithF("http")
            Try
                '下载。有 HTTP 镜像备用源时走双源竞速：直连源先发、镜像 1.5s 后补发，任一完成即用——
                '原先的串行换源在慢 CDN（forgecdn 实测单源 15s+）下要等满首源超时才轮到镜像，列表图标因此迟迟不显示
                If FallbackSource IsNot Nothing AndAlso FallbackSource.StartsWithF("http") AndAlso Source <> FallbackSource Then
                    NetDownloadByRace(Source, FallbackSource, TempPath, SimulateBrowserHeaders:=True)
                Else
                    NetDownloadByLoader(Source, TempPath, SimulateBrowserHeaders:=True)
                End If
                ActualSource = TempPath
                If Not EnableCache Then FileUtils.Delete(TempPath)
            Catch ex As Exception
                Try
                    If TempPath IsNot Nothing Then FileUtils.Delete(TempPath)
                Catch ex2 As Exception
                    Logger.Warn(ex2, "清理临时图片文件失败")
                End Try
                '加载本地备用图片
                If IsLocalFallback Then
                    Try
                        ActualSource = FallbackSource
                        Logger.Warn(ex, $"下载图片失败，使用本地备用图片（{Source}）")
                        Return
                    Catch exx As Exception
                        Logger.Error(ex, $"下载图片失败（{Source}）", LogBehavior.Toast)
                        Logger.Error(exx, $"加载备用图片失败（{FallbackSource}）", LogBehavior.Toast)
                    End Try
                Else
                    Logger.Error(ex, $"下载图片失败（{Source}，备用：{FallbackSource}）", LogBehavior.Toast)
                End If
            End Try
        End Sub, "MyImage PicLoader " & GetUuid() & "#", ThreadPriority.BelowNormal)
    End Sub

End Class