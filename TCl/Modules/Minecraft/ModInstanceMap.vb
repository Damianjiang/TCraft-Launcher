''' <summary>
''' 实例世界地图（第 127 轮，对齐 LumilioCL 世界探索器的「存档底图」，实现方式参照其
''' lumilio-anvil / save\render.rs：高度图取地表 + 北向比较明暗 + 顶面均值颜色表）。
'''
''' 数据源：存档 region 文件（r.X.Z.mca）。一张瓦片 = 16×16 区块 = 256×256 方块，
''' 即一个区域文件（32×32 区块）的四分之一。
'''
''' 仅支持 1.18+ 的区块格式（根标签直接含 sections / Heightmaps）。旧格式的
''' BlockStates 位打包规则不同（跨 Long 边界 / IntArray 高度图），v1 不做。
'''
''' 性能纪律（照抄 LumilioCL）：全部计算在后台线程、逐区块检查取消令牌；
''' 没有任何常驻渲染循环——调用方（页面）退出时取消令牌即作废，线程自然结束。
''' </summary>
Public Module ModInstanceMap

    ''' <summary>瓦片边长（1 像素 = 1 方块）。</summary>
    Public Const TilePixels As Integer = 256
    ''' <summary>每瓦片横向区块数（区域文件 = 32×32 区块，瓦片是它的四分之一）。</summary>
    Public Const ChunksPerTile As Integer = 16

#Region "颜色表"

    Private ReadOnly ColorTableLock As New Object
    ''' <summary>方块 ID → {R, G, B, 着色标志}。标志位：1=透视 2=草着色 4=树叶着色 8=水着色。</summary>
    Private ColorTable As Dictionary(Of String, Integer()) = Nothing

    ''' <summary>
    ''' 加载内嵌的方块颜色表（LumilioCL block-colors 1.21.4，取自原版材质顶面像素均值，MIT）。
    ''' </summary>
    Private Function GetColorTable() As Dictionary(Of String, Integer())
        If ColorTable IsNot Nothing Then Return ColorTable
        SyncLock ColorTableLock
            If ColorTable IsNot Nothing Then Return ColorTable
            Dim Result As New Dictionary(Of String, Integer())
            Try
                Dim Name As String = GetType(ModInstanceMap).Assembly.GetManifestResourceNames().
                    FirstOrDefault(Function(n) n.EndsWith("BlockColors.json.gz", StringComparison.OrdinalIgnoreCase))
                If Name IsNot Nothing Then
                    Using Gz As New GZipStream(GetType(ModInstanceMap).Assembly.GetManifestResourceStream(Name), CompressionMode.Decompress)
                        Using Ms As New MemoryStream()
                            Gz.CopyTo(Ms)
                            Dim Blocks = Text.Encoding.UTF8.GetString(Ms.ToArray()).DeserializeJson(Of JObject)()("blocks")
                            For Each Prop As JProperty In Blocks
                                Dim Arr = Prop.Value
                                Result(Prop.Name.ToString) = {CInt(Arr(0)), CInt(Arr(1)), CInt(Arr(2)), CInt(Arr(3))}
                            Next
                        End Using
                    End Using
                End If
                Logger.Info($"已加载方块颜色表：{Result.Count} 项")
            Catch ex As Exception
                Logger.Warn(ex, "加载方块颜色表失败，未知方块将以紫色显示")
            End Try
            ColorTable = Result
            Return ColorTable
        End SyncLock
    End Function

    ''' <summary>常见生物群系的草 / 植被着色乘数（值取自原版 colormap 的典型色）。未列出的用平原色。</summary>
    Private ReadOnly BiomeTints As New Dictionary(Of String, Integer()) From {
        {"plains", {145, 189, 89}}, {"sunflower_plains", {145, 189, 89}}, {"beach", {145, 189, 89}},
        {"forest", {79, 151, 49}}, {"flower_forest", {79, 151, 49}}, {"birch_forest", {136, 176, 87}},
        {"old_growth_birch_forest", {136, 176, 87}}, {"dark_forest", {50, 116, 29}},
        {"taiga", {11, 132, 85}}, {"old_growth_pine_taiga", {11, 132, 85}}, {"old_growth_spruce_taiga", {11, 132, 85}},
        {"snowy_plains", {128, 180, 151}}, {"snowy_taiga", {128, 180, 151}}, {"snowy_beach", {128, 180, 151}},
        {"snowy_slopes", {128, 180, 151}}, {"grove", {128, 180, 151}}, {"frozen_peaks", {128, 180, 151}},
        {"desert", {191, 183, 85}}, {"savanna", {191, 183, 85}}, {"savanna_plateau", {191, 183, 85}},
        {"windswept_savanna", {191, 183, 85}},
        {"jungle", {83, 201, 60}}, {"sparse_jungle", {83, 201, 60}}, {"bamboo_jungle", {83, 201, 60}},
        {"swamp", {106, 112, 57}}, {"mangrove_swamp", {106, 112, 57}},
        {"badlands", {144, 152, 35}}, {"wooded_badlands", {144, 152, 35}}, {"eroded_badlands", {144, 152, 35}},
        {"cherry_grove", {246, 219, 233}}, {"meadow", {131, 191, 116}},
        {"windswept_hills", {142, 180, 108}}, {"windswept_forest", {142, 180, 108}}, {"windswept_gravelly_hills", {142, 180, 108}},
        {"stony_shore", {142, 180, 108}}, {"stony_peaks", {142, 180, 108}}, {"jagged_peaks", {142, 180, 108}},
        {"mushroom_fields", {85, 255, 85}},
        {"the_nether", {135, 99, 99}}, {"crimson_forest", {135, 99, 99}}, {"warped_forest", {90, 150, 135}},
        {"the_end", {128, 128, 128}}, {"end_barrens", {128, 128, 128}}, {"end_highlands", {128, 128, 128}}
    }
    Private ReadOnly DefaultTint As Integer() = {145, 189, 89}
    Private ReadOnly WaterTint As Integer() = {63, 118, 228}
    Private ReadOnly UnknownColor As Integer() = {231, 106, 231}

#End Region

#Region "渲染"

    ''' <summary>
    ''' 渲染一张瓦片，返回 BGRA 字节数组（TilePixels² × 4，可直接写入 WriteableBitmap）。
    ''' 区域文件不存在时返回 Nothing（调用方按「未生成」处理）。
    ''' 瓦片坐标：1 瓦片 = 16×16 区块；负坐标向下取整到区域四分位。
    ''' </summary>
    Public Function RenderTile(RegionFolder As String, TileX As Integer, TileZ As Integer, CancelToken As CancellationToken) As Byte()
        If RegionFolder Is Nothing Then Return Nothing
        Dim RegionX As Integer = TileX >> 1, RegionZ As Integer = TileZ >> 1
        Dim QuadX As Integer = TileX And 1, QuadZ As Integer = TileZ And 1
        Dim RegionPath As String = RegionFolder & "r." & RegionX & "." & RegionZ & ".mca"
        If Not FileUtils.Exists(RegionPath) Then Return Nothing

        Dim RegionBytes As Byte()
        Try
            RegionBytes = IO.File.ReadAllBytes(RegionPath)
        Catch ex As Exception
            Logger.Warn(ex, $"读取区域文件失败：{RegionPath}")
            Return Nothing
        End Try
        If RegionBytes.Length < 8192 Then Return Nothing

        '高度与颜色的瓦片内矩阵（先收集完再做明暗，才能比较相邻列）
        Dim Heights(TilePixels - 1, TilePixels - 1) As Integer
        Dim Colors(TilePixels - 1, TilePixels - 1) As Integer '打包 RGB，-1 = 未生成（透明）
        For X As Integer = 0 To TilePixels - 1
            For Z As Integer = 0 To TilePixels - 1
                Colors(X, Z) = -1
                Heights(X, Z) = Integer.MinValue
            Next
        Next

        Dim Table = GetColorTable()
        Dim BaseX As Integer = RegionX * 512 + QuadX * 256 '本瓦片左上角的世界方块坐标
        Dim BaseZ As Integer = RegionZ * 512 + QuadZ * 256

        For Cz As Integer = 0 To ChunksPerTile - 1
            For Cx As Integer = 0 To ChunksPerTile - 1
                CancelToken.ThrowIfCancellationRequested()
                Try
                    RenderChunk(RegionBytes, QuadX * ChunksPerTile + Cx, QuadZ * ChunksPerTile + Cz,
                                BaseX + Cx * 16, BaseZ + Cz * 16, Heights, Colors, Table)
                Catch ex As OperationCanceledException
                    Throw
                Catch ex As Exception
                    '单个区块损坏只跳过自身，不影响整张瓦片
                    Logger.Trace($"地图渲染跳过异常区块（{RegionX},{RegionZ} 区块的 {Cx},{Cz}）：{ex.Message}")
                End Try
            Next
        Next

        '输出：北向比较明暗（LumilioCL SHADE 思路：比邻列高 → 亮，比邻列低 → 暗）
        Dim Output(TilePixels * TilePixels * 4 - 1) As Byte
        For X As Integer = 0 To TilePixels - 1
            For Z As Integer = 0 To TilePixels - 1
                Dim Packed As Integer = Colors(X, Z)
                Dim O = (Z * TilePixels + X) * 4
                If Packed < 0 Then Continue For '未生成 → 透明
                Dim H As Integer = Heights(X, Z)
                Dim Factor As Integer = 255
                If H <> Integer.MinValue Then
                    Dim WestH As Integer = If(X > 0, Heights(X - 1, Z), H)
                    Dim NorthH As Integer = If(Z > 0, Heights(X, Z - 1), H)
                    If H > WestH OrElse H > NorthH Then
                        Factor = 220 'LumilioCL：比邻列高 → 乘 220/255 提亮
                    ElseIf H < WestH OrElse H < NorthH Then
                        Factor = 180
                    End If
                End If
                Output(O) = CByte((Packed And &HFF) * Factor \ 255)                 'B
                Output(O + 1) = CByte(((Packed >> 8) And &HFF) * Factor \ 255)      'G
                Output(O + 2) = CByte(((Packed >> 16) And &HFF) * Factor \ 255)     'R
                Output(O + 3) = 255                                                 'A
            Next
        Next
        Return Output
    End Function

    ''' <summary>渲染单个区块（16×16 列）到瓦片矩阵。</summary>
    Private Sub RenderChunk(RegionBytes As Byte(), ChunkX As Integer, ChunkZ As Integer,
                            WorldBaseX As Integer, WorldBaseZ As Integer,
                            Heights(,) As Integer, Colors(,) As Integer, Table As Dictionary(Of String, Integer()))
        '区域文件头：1024 个 4 字节定位项（3 字节扇区号 + 1 字节扇区数）
        Dim ChunkIndex As Integer = ChunkX + ChunkZ * 32
        Dim Loc As Integer = ChunkIndex * 4
        Dim Sector As Integer = (RegionBytes(Loc) << 16) Or (RegionBytes(Loc + 1) << 8) Or RegionBytes(Loc + 2)
        If Sector = 0 Then Return '未生成

        '扇区数据：4 字节长度（大端）+ 1 字节压缩方案 + 数据
        Dim DataStart As Integer = Sector * 4096
        If DataStart + 5 > RegionBytes.Length Then Return
        Dim Length As Integer = (RegionBytes(DataStart) << 24) Or (RegionBytes(DataStart + 1) << 16) Or
                                (RegionBytes(DataStart + 2) << 8) Or RegionBytes(DataStart + 3)
        If Length < 2 OrElse DataStart + 4 + Length > RegionBytes.Length Then Return
        Dim Scheme As Byte = RegionBytes(DataStart + 4)
        Dim Compressed As Byte() = New Byte(Length - 2) {}
        Array.Copy(RegionBytes, DataStart + 5, Compressed, 0, Length - 1)

        Dim Root As NbtTag
        Using Ms As New MemoryStream(Compressed)
            Select Case Scheme
                Case 2 'zlib：跳过 2 字节头后是 deflate 流
                    Ms.Position = 2
                    Using Ds As New DeflateStream(Ms, CompressionMode.Decompress)
                        Root = ReadNbtRoot(Ds)
                    End Using
                Case 1 'gzip
                    Using Gs As New GZipStream(Ms, CompressionMode.Decompress)
                        Root = ReadNbtRoot(Gs)
                    End Using
                Case Else
                    Return '不认识的压缩方案（如 lz4），v1 不支持
            End Select
        End Using
        If Root Is Nothing OrElse Root.Children Is Nothing OrElse Not Root.Children.Any() Then Return

        '1.18+：根标签直接含 sections / Heightmaps；其余格式 v1 不支持
        Dim Sections As NbtTag = Root.GetList("sections")
        Dim Heightmaps As NbtTag = Root.GetChild("Heightmaps")
        If Sections Is Nothing OrElse Heightmaps Is Nothing Then Return
        Dim SurfaceMap As NbtTag = Heightmaps.GetChild("WORLD_SURFACE")
        If SurfaceMap Is Nothing OrElse SurfaceMap.Value Is Nothing Then Return
        Dim HeightData As Long() = CType(SurfaceMap.Value, Long())

        '解析 sections 到按 Y 索引的表
        Dim SectionMap As New Dictionary(Of Integer, NbtTag)
        For Each Section As NbtTag In Sections.Children
            If Section Is Nothing Then Continue For
            SectionMap(Section.GetInt("Y")) = Section
        Next

        For Lz As Integer = 0 To 15
            For Lx As Integer = 0 To 15
                Dim ColumnIndex As Integer = Lx + Lz * 16
                If ColumnIndex >= 256 Then Continue For
                Dim H As Integer = PackedGet(HeightData, ColumnIndex, 9) '1.14+ 高度图固定 9 位
                If H <= 0 Then Continue For
                Dim WorldY As Integer = H - 1 'WORLD_SURFACE 的值 = 最高非空气方块 Y + 1
                Dim SectionY As Integer = WorldY >> 4
                If Not SectionMap.ContainsKey(SectionY) Then Continue For
                Dim Section As NbtTag = SectionMap(SectionY)
                Dim States As NbtTag = Section.GetCompound("block_states")
                If States Is Nothing Then Continue For
                Dim Palette As NbtTag = States.GetList("palette")
                If Palette Is Nothing OrElse Not Palette.Children.Any() Then Continue For

                '方块索引：调色板内下标（数据缺失 = 调色板只有一项）
                Dim BlockIndex As Integer = 0
                Dim StateData As NbtTag = States.GetChild("data")
                If StateData IsNot Nothing AndAlso StateData.Value IsNot Nothing Then
                    Dim Bits As Integer = Math.Max(4, CInt(Math.Ceiling(Math.Log(Palette.Children.Count, 2))))
                    '区块内按 YZX 排列：下标 = y*256 + z*16 + x
                    BlockIndex = PackedGet(CType(StateData.Value, Long()), (WorldY And 15) * 256 + Lz * 16 + Lx, Bits)
                End If
                If BlockIndex >= Palette.Children.Count Then Continue For
                Dim BlockName As String = Palette.Children(BlockIndex).GetString("Name")
                If String.IsNullOrEmpty(BlockName) Then Continue For

                Dim BlockColor As Integer() = Nothing
                Table.TryGetValue(BlockName, BlockColor)
                If BlockColor Is Nothing Then
                    '去掉命名空间再试一次（颜色表键形如 minecraft:stone）
                    Table.TryGetValue(BlockName.AfterFirst(":"), BlockColor)
                End If
                If BlockColor Is Nothing Then BlockColor = UnknownColor

                '着色：2=草 4=树叶（按生物群系），8=水（固定水色）
                If (BlockColor(3) And (2 Or 4 Or 8)) <> 0 Then
                    Dim Tint As Integer() = DefaultTint
                    If (BlockColor(3) And 8) <> 0 Then
                        Tint = WaterTint
                    Else
                        Dim BiomeName As String = GetBiomeName(Section, Lx, Lz, WorldY)
                        If BiomeTints.ContainsKey(BiomeName) Then Tint = BiomeTints(BiomeName)
                    End If
                    BlockColor = {BlockColor(0) * Tint(0) \ 255, BlockColor(1) * Tint(1) \ 255, BlockColor(2) * Tint(2) \ 255, BlockColor(3)}
                End If

                '瓦片内像素 = 区块在四分位中的位置（ChunkX 为区域内的区块号，与 15 取与得到四分位内序号）
                Colors((ChunkX And 15) * 16 + Lx, (ChunkZ And 15) * 16 + Lz) = (BlockColor(0) << 16) Or (BlockColor(1) << 8) Or BlockColor(2)
                Heights((ChunkX And 15) * 16 + Lx, (ChunkZ And 15) * 16 + Lz) = WorldY
            Next
        Next
    End Sub

    ''' <summary>取该列所在位置的生物群系 ID（按 section 的 biomes 调色板，4×4×4 网格）。</summary>
    Private Function GetBiomeName(Section As NbtTag, Lx As Integer, Lz As Integer, WorldY As Integer) As String
        Try
            Dim Biomes As NbtTag = Section.GetCompound("biomes")
            If Biomes Is Nothing Then Return ""
            Dim Palette As NbtTag = Biomes.GetList("palette")
            If Palette Is Nothing OrElse Not Palette.Children.Any() Then Return ""
            Dim BiomeIndex As Integer = 0
            Dim BiomeData As NbtTag = Biomes.GetChild("data")
            If BiomeData IsNot Nothing AndAlso BiomeData.Value IsNot Nothing Then
                Dim Bits As Integer = Math.Max(1, CInt(Math.Ceiling(Math.Log(Palette.Children.Count, 2))))
                Dim Index As Integer = (WorldY >> 2) * 16 + (Lz >> 2) * 4 + (Lx >> 2)
                BiomeIndex = PackedGet(CType(BiomeData.Value, Long()), Index, Bits)
            End If
            If BiomeIndex >= Palette.Children.Count Then Return ""
            Return Palette.Children(BiomeIndex).GetString("Name").AfterFirst(":")
        Catch ex As Exception
            Return ""
        End Try
    End Function

    ''' <summary>从流中读取一个 NBT Compound 根标签（复用 ModWorldData 的 NbtReader）。</summary>
    Private Function ReadNbtRoot(Source As Stream) As NbtTag
        Dim Reader As New NbtReader(Source)
        If Reader.ReadByteValue() <> 10 Then Return Nothing
        Reader.ReadString()
        Return Reader.ReadPayload(10)
    End Function

    ''' <summary>
    ''' 读取 1.16+ 的位打包数据（每 Long 不跨界）。Bits 为 0 时恒返 0。
    ''' </summary>
    Private Function PackedGet(Data As Long(), Index As Integer, Bits As Integer) As Integer
        If Data Is Nothing OrElse Bits <= 0 Then Return 0
        Dim PerLong As Integer = 64 \ Bits
        Dim LongIndex As Integer = Index \ PerLong
        If LongIndex >= Data.Length Then Return 0
        Dim Shift As Integer = (Index Mod PerLong) * Bits
        Return CInt((Data(LongIndex) >> Shift) And ((1L << Bits) - 1))
    End Function

#End Region

End Module
