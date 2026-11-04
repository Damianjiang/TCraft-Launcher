Imports System.IO
Imports System.Text

''' <summary>
''' 实例内的资源包 / 光影包管理：目录定位、列表读取、options.txt 读写与光影加载器检测。
''' 本模块只做磁盘与文本层面的操作，界面由 PageInstanceResourcePack / PageInstanceShader 负责。
''' </summary>
Public Module ModInstanceContent

#Region "数据类"

    ''' <summary>
    ''' 一个资源包 / 光影包条目（.zip 文件或文件夹）。
    ''' </summary>
    Public Class PackEntry
        ''' <summary>显示名称（文件不带 .zip 后缀）。</summary>
        Public Name As String
        ''' <summary>完整路径。</summary>
        Public FullPath As String
        ''' <summary>占用字节数（文件夹为递归统计结果，失败时为 0）。</summary>
        Public SizeBytes As Long
        ''' <summary>最后修改时间。</summary>
        Public Modified As DateTime
        ''' <summary>是否为文件夹（否则为 .zip）。</summary>
        Public IsFolder As Boolean
    End Class

#End Region

#Region "目录与列表"

    ''' <summary>
    ''' 取实例的独立目录（带结尾斜杠）。实例未加载或目录为空时返回 Nothing，避免拼出盘符根目录。
    ''' </summary>
    Private Function GetIndieRoot(Instance As McInstance) As String
        If Instance Is Nothing Then Return Nothing
        Dim Root As String = Nothing
        Try
            Root = Instance.PathIndie
        Catch ex As Exception
            Logger.Warn(ex, "获取实例目录失败")
            Return Nothing
        End Try
        If String.IsNullOrWhiteSpace(Root) Then Return Nothing
        Return PathUtils.AddSlashSuffix(Root)
    End Function

    ''' <summary>
    ''' 获取指定类别的内容文件夹（会自动创建）。
    ''' </summary>
    ''' <param name="Kind">"resourcepacks" 或 "shaderpacks"。</param>
    Public Function GetPacksFolder(Instance As McInstance, Kind As String) As String
        If Instance Is Nothing Then Return Nothing
        If String.IsNullOrWhiteSpace(Kind) Then Return Nothing
        Dim Root As String = GetIndieRoot(Instance)
        If Root Is Nothing Then Return Nothing
        Dim Folder As String = PathUtils.AddSlashSuffix(Root & Kind)
        Try
            DirectoryUtils.Create(Folder)
        Catch ex As Exception
            Logger.Warn(ex, $"创建内容文件夹失败：{Folder}")
        End Try
        Return Folder
    End Function

    ''' <summary>
    ''' 列出文件夹内的全部资源包 / 光影包（文件与文件夹都算），按名称排序。
    ''' </summary>
    Public Function ListPacks(Folder As String) As List(Of PackEntry)
        Dim Result As New List(Of PackEntry)
        If String.IsNullOrWhiteSpace(Folder) Then Return Result
        Try
            If Not DirectoryUtils.Exists(Folder) Then Return Result
            '文件夹（未打包的资源包 / 光影包）
            For Each DirPath In DirectoryUtils.EnumerateDirectories(Folder, False)
                Try
                    Dim Info As DirectoryInfo = DirectoryUtils.GetInfo(DirPath)
                    Result.Add(New PackEntry With {
                        .Name = PathUtils.GetLastPart(DirPath),
                        .FullPath = DirPath,
                        .SizeBytes = GetFolderSize(DirPath),
                        .Modified = Info.LastWriteTime,
                        .IsFolder = True
                    })
                Catch ex As Exception
                    Logger.Warn(ex, $"读取内容文件夹信息失败：{DirPath}")
                End Try
            Next
            '压缩包
            For Each FilePath In DirectoryUtils.EnumerateFiles(Folder, False)
                Try
                    If Not FilePath.EndsWith(".zip", StringComparison.OrdinalIgnoreCase) Then Continue For
                    Dim Info As FileInfo = FileUtils.GetInfo(FilePath)
                    Result.Add(New PackEntry With {
                        .Name = Path.GetFileNameWithoutExtension(FilePath),
                        .FullPath = FilePath,
                        .SizeBytes = Info.Length,
                        .Modified = Info.LastWriteTime,
                        .IsFolder = False
                    })
                Catch ex As Exception
                    Logger.Warn(ex, $"读取内容文件信息失败：{FilePath}")
                End Try
            Next
        Catch ex As Exception
            Logger.Warn(ex, $"枚举内容文件夹失败：{Folder}")
        End Try
        Return Result.OrderBy(Function(One) One.Name, StringComparer.OrdinalIgnoreCase).ToList()
    End Function

    ''' <summary>
    ''' 递归统计文件夹大小，失败时返回 0。
    ''' </summary>
    Public Function GetFolderSize(Folder As String) As Long
        Dim Total As Long = 0
        Try
            If Not DirectoryUtils.Exists(Folder) Then Return 0
            For Each FilePath In DirectoryUtils.EnumerateFiles(Folder, True)
                Try
                    Total += FileUtils.GetInfo(FilePath).Length
                Catch ex As Exception
                    '单个文件读不到就跳过
                    Logger.Warn(ex, $"统计文件夹大小时跳过文件：{FilePath}")
                End Try
            Next
        Catch ex As Exception
            Logger.Warn(ex, $"统计文件夹大小失败：{Folder}")
        End Try
        Return Total
    End Function

    ''' <summary>
    ''' 把字节数格式化为人类可读文本。
    ''' </summary>
    Public Function FormatSize(Bytes As Long) As String
        If Bytes < 1024 Then Return Bytes & " B"
        If Bytes < 1024L * 1024 Then Return (Bytes / 1024.0).ToString("0.#", Globalization.CultureInfo.InvariantCulture) & " KB"
        If Bytes < 1024L * 1024 * 1024 Then Return (Bytes / 1024.0 / 1024.0).ToString("0.#", Globalization.CultureInfo.InvariantCulture) & " MB"
        Return (Bytes / 1024.0 / 1024.0 / 1024.0).ToString("0.##", Globalization.CultureInfo.InvariantCulture) & " GB"
    End Function

#End Region

#Region "options.txt 读写"

    ''' <summary>
    ''' 当前实例的 options.txt 路径。
    ''' </summary>
    Public Function GetOptionsPath(Instance As McInstance) As String
        Dim Root As String = GetIndieRoot(Instance)
        If Root Is Nothing Then Return Nothing
        Return Root & "options.txt"
    End Function

    ''' <summary>
    ''' 读取 options.txt 中某一项的值（冒号后的全部内容）。找不到或文件不存在时返回空字符串。
    ''' </summary>
    Public Function ReadOption(Instance As McInstance, Key As String) As String
        If Instance Is Nothing OrElse String.IsNullOrWhiteSpace(Key) Then Return ""
        Dim FilePath As String = GetOptionsPath(Instance)
        Try
            If Not FileUtils.Exists(FilePath) Then Return ""
            For Each Line In File.ReadAllLines(FilePath, New UTF8Encoding(False))
                Dim Idx As Integer = Line.IndexOf(":"c)
                If Idx <= 0 Then Continue For
                If String.Equals(Line.Substring(0, Idx).Trim(), Key, StringComparison.OrdinalIgnoreCase) Then
                    Return Line.Substring(Idx + 1)
                End If
            Next
        Catch ex As Exception
            Logger.Warn(ex, $"读取 options.txt 失败：{FilePath}")
        End Try
        Return ""
    End Function

    ''' <summary>
    ''' 写入 options.txt 中某一项：已存在则替换该行，不存在则追加到末尾；其它行原样保留。
    ''' 首次覆盖前会备份为 options.txt.bak。
    ''' </summary>
    Public Sub WriteOption(Instance As McInstance, Key As String, Value As String)
        If Instance Is Nothing OrElse String.IsNullOrWhiteSpace(Key) Then Return
        Dim FilePath As String = GetOptionsPath(Instance)
        If FilePath Is Nothing Then Return
        Try
            Dim Raw As String = ""
            If FileUtils.Exists(FilePath) Then Raw = File.ReadAllText(FilePath, New UTF8Encoding(False))
            '沿用文件原有的换行符
            Dim NewLine As String = If(Raw.Contains(vbCrLf), vbCrLf, vbLf)
            Dim Lines As New List(Of String)(Raw.Replace(vbCrLf, vbLf).Split(New String() {vbLf}, StringSplitOptions.None))
            Dim Found As Boolean = False
            For i = 0 To Lines.Count - 1
                Dim Line As String = Lines(i)
                Dim Idx As Integer = Line.IndexOf(":"c)
                If Idx <= 0 Then Continue For
                If String.Equals(Line.Substring(0, Idx).Trim(), Key, StringComparison.OrdinalIgnoreCase) Then
                    Lines(i) = Key & ":" & If(Value, "")
                    Found = True
                    Exit For
                End If
            Next
            If Not Found Then
                '空文件不追加空行
                If Lines.Count = 1 AndAlso Lines(0).Length = 0 Then Lines.Clear()
                Lines.Add(Key & ":" & If(Value, ""))
            End If
            '覆盖前只备份一次
            If FileUtils.Exists(FilePath) Then
                Dim BackupPath As String = FilePath & ".bak"
                If Not FileUtils.Exists(BackupPath) Then
                    Try
                        FileUtils.Copy(FilePath, BackupPath)
                    Catch ex As Exception
                        Logger.Warn(ex, $"备份 options.txt 失败：{BackupPath}")
                    End Try
                End If
            Else
                DirectoryUtils.Create(IO.Path.GetDirectoryName(FilePath))
            End If
            File.WriteAllText(FilePath, String.Join(NewLine, Lines), New UTF8Encoding(False))
        Catch ex As Exception
            Logger.Error(ex, "写入 options.txt 失败")
        End Try
    End Sub

#End Region

#Region "资源包"

    ''' <summary>
    ''' 解析 options.txt 里的列表值（如 ["vanilla","file/a.zip"]），返回去引号的原始项。
    ''' </summary>
    Private Function ParsePackList(Raw As String) As List(Of String)
        Dim Result As New List(Of String)
        If String.IsNullOrWhiteSpace(Raw) Then Return Result
        Dim Text As String = Raw.Trim()
        If Text.StartsWith("[", StringComparison.Ordinal) Then Text = Text.Substring(1)
        If Text.EndsWith("]", StringComparison.Ordinal) Then Text = Text.Substring(0, Text.Length - 1)
        For Each Part In Text.Split(","c)
            Dim One As String = Part.Trim().Trim(""""c)
            If One.Length > 0 Then Result.Add(One)
        Next
        Return Result
    End Function

    ''' <summary>
    ''' 把资源包项规范化成可与本地条目比较的名称：去掉 file/ 前缀与 .zip 后缀。
    ''' </summary>
    Public Function NormalizePackName(Raw As String) As String
        Dim One As String = If(Raw, "").Trim().Trim(""""c)
        If One.StartsWith("file/", StringComparison.OrdinalIgnoreCase) Then One = One.Substring(5)
        If One.EndsWith(".zip", StringComparison.OrdinalIgnoreCase) Then One = One.Substring(0, One.Length - 4)
        Return One
    End Function

    ''' <summary>
    ''' 获取当前已启用的资源包名称（已去掉 file/ 前缀与 .zip 后缀，可直接与列表条目比较）。
    ''' </summary>
    Public Function GetEnabledResourcePacks(Instance As McInstance) As List(Of String)
        Dim Result As New List(Of String)
        If Instance Is Nothing Then Return Result
        For Each One In ParsePackList(ReadOption(Instance, "resourcePacks"))
            Result.Add(NormalizePackName(One))
        Next
        Return Result
    End Function

    ''' <summary>
    ''' 启用 / 停用一个资源包，并立即写回 options.txt（其它行保持不变）。
    ''' </summary>
    Public Sub SetResourcePackEnabled(Instance As McInstance, PackName As String, Enabled As Boolean)
        If Instance Is Nothing OrElse String.IsNullOrWhiteSpace(PackName) Then Return
        Try
            Dim Target As String = NormalizePackName(PackName)
            Dim RawValue As String = ReadOption(Instance, "resourcePacks").Trim()
            '1.12 及更早的写法是 [MyPack.zip]，不带引号与 file/ 前缀；有引号就按新版格式处理
            Dim IsLegacy As Boolean = RawValue.Length > 2 AndAlso Not RawValue.Contains("""")
            Dim RawList As List(Of String) = ParsePackList(RawValue)
            '先移除同名项（含大小写差异）
            RawList = RawList.Where(Function(One) Not String.Equals(NormalizePackName(One), Target, StringComparison.OrdinalIgnoreCase)).ToList()
            If Enabled Then
                '文件夹包与 .zip 包在 options.txt 里的写法不同，按磁盘上实际存在的形式判断
                Dim Folder As String = GetPacksFolder(Instance, "resourcepacks")
                Dim IsFolderPack As Boolean = False
                Dim FilePack As String = ""
                If Folder IsNot Nothing Then
                    IsFolderPack = DirectoryUtils.Exists(PathUtils.AddSlashSuffix(Folder) & Target)
                    FilePack = PathUtils.AddSlashSuffix(Folder) & Target & ".zip"
                End If
                Dim PackFileName As String = Target
                If Not (IsFolderPack AndAlso Not FileUtils.Exists(FilePack)) Then PackFileName &= ".zip"
                If IsLegacy Then
                    RawList.Add(PackFileName)
                Else
                    RawList.Add("file/" & PackFileName)
                End If
            End If
            WriteOption(Instance, "resourcePacks", FormatPackList(RawList, IsLegacy))
        Catch ex As Exception
            Logger.Error(ex, "切换资源包启用状态失败")
        End Try
    End Sub

    Private Function FormatPackList(Items As List(Of String), IsLegacy As Boolean) As String
        Dim Parts As New List(Of String)
        For Each One In Items
            If IsLegacy Then
                Parts.Add(One)
            Else
                Parts.Add("""" & One.Replace("""", "").Replace(",", "") & """")
            End If
        Next
        Return "[" & String.Join(",", Parts) & "]"
    End Function

#End Region

#Region "光影包"

    ''' <summary>
    ''' 获取当前使用的光影包文件名（不含引号）。没有启用或值为 off 时返回空字符串。
    ''' </summary>
    Public Function GetCurrentShader(Instance As McInstance) As String
        Dim Raw As String = ReadOption(Instance, "shaderPack").Trim()
        If Raw.StartsWith("""", StringComparison.Ordinal) AndAlso Raw.EndsWith("""", StringComparison.Ordinal) AndAlso Raw.Length >= 2 Then
            Raw = Raw.Substring(1, Raw.Length - 2)
        End If
        Raw = Raw.Trim()
        If String.Equals(Raw, "off", StringComparison.OrdinalIgnoreCase) Then Return ""
        Return Raw
    End Function

    ''' <summary>
    ''' 设置当前光影包（传空字符串表示取消），并立即写回 options.txt。
    ''' </summary>
    Public Sub SetCurrentShader(Instance As McInstance, PackName As String)
        If Instance Is Nothing Then Return
        Dim Text As String = If(PackName, "").Trim()
        If String.Equals(Text, "off", StringComparison.OrdinalIgnoreCase) Then Text = ""
        WriteOption(Instance, "shaderPack", Text)
    End Sub

    ''' <summary>
    ''' 检测实例的 mods 文件夹里是否装有 Iris / Oculus / OptiFine。
    ''' </summary>
    Public Function HasShaderLoader(Instance As McInstance) As Boolean
        Dim Root As String = GetIndieRoot(Instance)
        If Root Is Nothing Then Return False
        Try
            Dim ModsFolder As String = PathUtils.AddSlashSuffix(Root & "mods")
            If Not DirectoryUtils.Exists(ModsFolder) Then Return False
            For Each FilePath In DirectoryUtils.EnumerateFiles(ModsFolder, False)
                Dim NameLower As String = IO.Path.GetFileName(FilePath).ToLowerInvariant()
                If Not NameLower.EndsWith(".jar") Then Continue For
                If NameLower.Contains("iris") OrElse NameLower.Contains("oculus") OrElse NameLower.Contains("optifine") Then
                    Return True
                End If
            Next
        Catch ex As Exception
            Logger.Warn(ex, "检测光影加载器失败")
        End Try
        Return False
    End Function

#End Region

End Module
