'一个万能的自动图片类型转换工具类

Imports System.Drawing.Imaging

Public Class MyBitmap

    ''' <summary>
    ''' 存储的图片
    ''' </summary>
    Public Pic As System.Drawing.Bitmap

    '自动类型转换
    '支持的类：Image，ImageSource，Bitmap，ImageBrush，BitmapSource
    Public Shared Widening Operator CType(Image As System.Drawing.Image) As MyBitmap
        If Image Is Nothing Then Return Nothing
        Return New MyBitmap(Image)
    End Operator
    Public Shared Widening Operator CType(Image As MyBitmap) As System.Drawing.Image
        If Image Is Nothing Then Return Nothing
        Return Image.Pic
    End Operator
    Public Shared Widening Operator CType(Image As ImageSource) As MyBitmap
        If Image Is Nothing Then Return Nothing
        Return New MyBitmap(Image)
    End Operator
    Public Shared Widening Operator CType(Image As MyBitmap) As ImageSource
        If Image Is Nothing Then Return Nothing
        Dim BitmapPic As System.Drawing.Bitmap = Image.Pic
        Dim rect = New System.Drawing.Rectangle(0, 0, BitmapPic.Width, BitmapPic.Height)
        Dim bitmapData = BitmapPic.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb)
        Try
            Dim Result = BitmapSource.Create(BitmapPic.Width, BitmapPic.Height, BitmapPic.HorizontalResolution, BitmapPic.VerticalResolution,
                                       PixelFormats.Bgra32, Nothing, bitmapData.Scan0,
                                       rect.Width * rect.Height * 4, bitmapData.Stride)
            Result.Freeze()
            Return Result
        Finally
            BitmapPic.UnlockBits(bitmapData)
        End Try
    End Operator
    Public Shared Widening Operator CType(Image As System.Drawing.Bitmap) As MyBitmap
        If Image Is Nothing Then Return Nothing
        Return New MyBitmap(Image)
    End Operator
    Public Shared Widening Operator CType(Image As MyBitmap) As System.Drawing.Bitmap
        If Image Is Nothing Then Return Nothing
        Return Image.Pic
    End Operator
    Public Shared Widening Operator CType(Image As ImageBrush) As MyBitmap
        If Image Is Nothing Then Return Nothing
        Return New MyBitmap(Image)
    End Operator
    Public Shared Widening Operator CType(Image As MyBitmap) As ImageBrush
        If Image Is Nothing Then Return Nothing
        Return New ImageBrush(New MyBitmap(Image.Pic))
    End Operator

    '构造函数
    Public Sub New()
    End Sub
    Public Sub New(FilePathOrResourceName As String)
        Try
            FilePathOrResourceName = FilePathOrResourceName.Replace("pack://application:,,,/images/", PathImage)
            If FilePathOrResourceName.StartsWithF(PathImage) Then
                '使用缓存。
                '无界 ConcurrencyDictionary 会随本地图片路径数量无限增长（皮肤/头像/图标），
                '长会话中 GDI+ 位图句柄与托管内存只增不减。改用「上限 + 队列驱逐」（同 MyImage.LocalImageCache）：
                '命中即复用，未命中才解码并入队；超过上限就丢弃最旧的（不主动 Dispose，
                '避免破坏仍共享该 GDI 位图的活动 ImageBrush，交给 GDI+ 终结器回收句柄）。
                Static Cache As New Concurrent.ConcurrentDictionary(Of String, MyBitmap)
                Static CacheKeys As New Concurrent.ConcurrentQueue(Of String)
                Const CacheLimit As Integer = 256
                If Cache.ContainsKey(FilePathOrResourceName) Then
                    Pic = Cache(FilePathOrResourceName).Pic
                Else
                    Pic = New MyBitmap(CType((New ImageSourceConverter).ConvertFromString(FilePathOrResourceName), ImageSource))
                    If Cache.TryAdd(FilePathOrResourceName, Pic) Then
                        CacheKeys.Enqueue(FilePathOrResourceName)
                        Do While CacheKeys.Count > CacheLimit
                            Dim OldKey As String = Nothing
                            If Not CacheKeys.TryDequeue(OldKey) Then Exit Do
                            Dim Removed As MyBitmap = Nothing
                            Cache.TryRemove(OldKey, Removed)
                        Loop
                    End If
                End If
            Else
                '使用这种自己接管 FileStream 的方法加载才能解除文件占用
                Using InputStream = FileUtils.ReadAsStream(FilePathOrResourceName)
                    '判断是否为 WebP 文件头
                    Dim Header(1) As Byte
                    InputStream.Read(Header, 0, 2)
                    InputStream.Seek(0, SeekOrigin.Begin)
                    If Header(0) = 82 AndAlso Header(1) = 73 Then
                        '读取 WebP
                        Dim FileBytes(InputStream.Length - 1) As Byte
                        InputStream.Read(FileBytes, 0, FileBytes.Length)
                        Pic = WebPDecoder.DecodeFromBytes(FileBytes) '将代码隔离在另外一个类中，这样只要不走进这个分支就不会加载 Imazen.WebP.dll
                    Else
                        Pic = New System.Drawing.Bitmap(InputStream)
                    End If
                End Using
            End If
        Catch ex As Exception
            Pic = My.Application.TryFindResource(FilePathOrResourceName)
            If Pic Is Nothing Then
                Pic = New System.Drawing.Bitmap(1, 1)
                If TypeOf ex Is ArgumentException Then
                    Throw New Exception($"图片格式不支持，或图片文件损坏（{FilePathOrResourceName}）", ex)
                Else
                    Throw New Exception($"加载 MyBitmap 意外失败（{FilePathOrResourceName}）", ex)
                End If
            Else
                Logger.Warn(ex, $"指定类型有误的 MyBitmap 加载（{FilePathOrResourceName}）")
                Exit Try
            End If
        End Try
    End Sub
    Public Sub New(Image As ImageSource)
        Using MS = New MemoryStream()
            Dim Encoder = New PngBitmapEncoder()
            Encoder.Frames.Add(BitmapFrame.Create(Image))
            Encoder.Save(MS)
            Pic = New System.Drawing.Bitmap(MS)
        End Using
    End Sub
    Public Sub New(Image As System.Drawing.Image)
        Pic = Image
    End Sub
    Public Sub New(Image As System.Drawing.Bitmap)
        Pic = Image
    End Sub
    Public Sub New(Image As ImageBrush)
        Using MS = New MemoryStream()
            Dim Encoder = New BmpBitmapEncoder()
            Encoder.Frames.Add(BitmapFrame.Create(Image.ImageSource))
            Encoder.Save(MS)
            Pic = New System.Drawing.Bitmap(MS)
        End Using
    End Sub
    Private Class WebPDecoder '将代码隔离在另外一个类中，这样只要不调用这个方法就不会加载 Imazen.WebP.dll
        Public Shared Function DecodeFromBytes(Bytes As Byte()) As System.Drawing.Bitmap
            Return New Imazen.WebP.SimpleDecoder().DecodeFromBytes(Bytes, Bytes.Length)
        End Function
    End Class

    ''' <summary>
    ''' 创建用于窗口背景的 ImageBrush，按最大宽度解码，降低加载耗时与内存占用。
    ''' </summary>
    Public Shared Function CreateImageBrush(FilePath As String, MaxWidth As Integer) As ImageBrush
        Dim Header(1) As Byte
        Using InputStream = FileUtils.ReadAsStream(FilePath)
            InputStream.Read(Header, 0, 2)
        End Using
        If Header(0) = 82 AndAlso Header(1) = 73 Then Return CType(New MyBitmap(FilePath), ImageBrush) 'WebP
        Dim Bitmap As New BitmapImage()
        Bitmap.BeginInit()
        Bitmap.CacheOption = BitmapCacheOption.OnLoad
        If MaxWidth > 0 Then Bitmap.DecodePixelWidth = MaxWidth
        Bitmap.UriSource = New Uri(FilePath, UriKind.Absolute)
        Bitmap.EndInit()
        Bitmap.Freeze()
        Return New ImageBrush(Bitmap)
    End Function

    ''' <summary>
    ''' 获取裁切的图片，这个方法不会导致原对象改变且会返回一个新的对象。
    ''' </summary>
    Public Function Clip(X As Integer, Y As Integer, Width As Integer, Height As Integer) As MyBitmap
        '固定 32bpp ARGB：索引色位图（8bpp 调色板 PNG，内置皮肤 Steve/Alex 就是这种）
        '无法创建 Graphics，会直接抛「A Graphics object cannot be created from an image
        'that has an indexed pixel format」，头像裁切因此从未成功过。
        Dim bmp As New System.Drawing.Bitmap(Width, Height, System.Drawing.Imaging.PixelFormat.Format32bppArgb)
        bmp.SetResolution(Pic.HorizontalResolution, Pic.VerticalResolution)
        Using g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor
            g.TranslateTransform(-X, -Y)
            g.DrawImage(Pic, New System.Drawing.Rectangle(0, 0, Pic.Width, Pic.Height))
        End Using
        Return bmp
    End Function

    ''' <summary>
    ''' 将另一张图按 alpha 混合叠加到本图之上（source-over，保留双方透明度）。
    ''' 叠加图尺寸不同时按左上角对齐拉伸到本图尺寸；只修改本对象。
    ''' 用途：Minecraft 皮肤的脸 = 基础脸 + 帽子层半透明叠加（见 MySkin 的 ImgFore）。
    ''' </summary>
    Public Sub Overlay(Top As MyBitmap)
        If Top Is Nothing OrElse Top.Pic Is Nothing Then Return
        Using g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(Pic)
            g.DrawImage(Top.Pic, New System.Drawing.Rectangle(0, 0, Pic.Width, Pic.Height))
        End Using
    End Sub

    ''' <summary>
    ''' 获取旋转或翻转后的图片，这个方法不会导致原对象改变且会返回一个新的对象。
    ''' </summary>
    Public Function RotateFlip(Type As System.Drawing.RotateFlipType) As MyBitmap
        Dim bmp As New System.Drawing.Bitmap(Pic)
        bmp.SetResolution(Pic.HorizontalResolution, Pic.VerticalResolution)
        bmp.RotateFlip(Type)
        Return bmp
    End Function

    ''' <summary>
    ''' 将图像保存到文件。
    ''' </summary>
    Public Sub Save(FilePath As String)
        Dim encoder As BitmapEncoder = New PngBitmapEncoder()
        encoder.Frames.Add(BitmapFrame.Create(Me))
        Using fileStream = FileUtils.CreateAsStream(FilePath)
            encoder.Save(fileStream)
        End Using
    End Sub

End Class
