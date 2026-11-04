''' <summary>
''' 自研 gzip 透明层（单文件 exe 体积优化核心）。
''' <para/>单文件部署时，所有内嵌的二进制资源（dll / jar / 图片等）以标准 gzip 压缩形式存储，
''' 运行时透明解压：识别 gzip magic（0x1F 0x8B）则 GZipStream 解压，否则原样返回。
''' <para/>仅依赖 BCL 的 System.IO.Compression，不依赖任何自研库（TClKit 等），
''' 因此可在最早期（Application 的 AssemblyResolve）安全调用——那里不能使用任何库，
''' 但 GZipStream 是 .NET 4.5+ 内置 BCL，不引入额外程序集。
''' <para/>收益：把 resx 里 base64 嵌入的大二进制（含明文 Text 的 TClCS.dll 等）整体压缩，
''' 显著缩小单文件 exe 体积，且不改变任何调用点（加载器/资源释放器内部透明解压）。
''' </summary>
Public Module ModGzip

    Private GzipMagic1 As Byte = 31 '0x1F
    Private GzipMagic2 As Byte = 139 '0x8B

    ''' <summary>字节是否为标准 gzip 流的魔数头。</summary>
    Public Function IsGzip(Data As Byte()) As Boolean
        Return Data IsNot Nothing AndAlso Data.Length >= 2 AndAlso Data(0) = GzipMagic1 AndAlso Data(1) = GzipMagic2
    End Function

    ''' <summary>
    ''' 透明解压：入参若是 gzip 流则解压还原，否则原样返回（幂等、绝不抛异常）。
    ''' 用于把「已 gzip 压缩的嵌入资源」还原为可用字节。
    ''' </summary>
    Public Function Unwrap(Data As Byte()) As Byte()
        If Not IsGzip(Data) Then Return Data '不是 gzip（旧资源 / 文本），原样
        Try
            Using InStream As New System.IO.MemoryStream(Data, 0, Data.Length, False),
                  OutStream As New System.IO.MemoryStream(),
                  Gz As New System.IO.Compression.GZipStream(InStream, System.IO.Compression.CompressionMode.Decompress)
                Gz.CopyTo(OutStream)
                Return OutStream.ToArray()
            End Using
        Catch
            Return Data '解压失败则原样返回，避免阻塞
        End Try
    End Function

End Module
