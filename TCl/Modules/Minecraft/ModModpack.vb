Imports System.Collections.ObjectModel
Imports System.Text.RegularExpressions

Public Module ModModpack

    '触发整合包安装的外部接口
    ''' <summary>
    ''' 弹窗要求选择一个整合包文件并进行安装。
    ''' </summary>
    Public Sub ModpackInstall()
        Dim File As String = Dialogs.SelectFile("选择整合包压缩文件", False, filter:={({"rar", "zip", "mrpack"}, "整合包文件")}).FirstOrDefault() '选择整合包文件
        If String.IsNullOrEmpty(File) Then Return
        '与「拖入整合包」走同一套确认弹窗，先让用户看清要装的是哪个游戏版本 / 加载器版本，
        '避免装完才发现版本不对（这个入口原来直接开装，界面上看不到任何版本信息）
        Dim PackInfo As McModpackInfo = McModpackReadInfo(File)
        If PackInfo.IsValid AndAlso MyMsgBoxModpack(PackInfo) <> 1 Then Return
        RunInThread(
        Sub()
            Try
                ModpackInstall(File)
            Catch ex As Exception
                ex.ThrowIfCanceled()
                Logger.Error(ex, "手动安装整合包失败", LogBehavior.Alert)
            End Try
        End Sub)
    End Sub
    ''' <summary>
    ''' 整合包预读信息：既是拖入压缩包时的确认弹窗数据，也是安装时唯一的版本解析结果。
    ''' <para/>预读与安装共用 <see cref="ParsePackInfo"/>，避免「弹窗说一套、安装装另一套」。
    ''' </summary>
    Public Class McModpackInfo
        ''' <summary>是否为可识别的整合包。</summary>
        Public IsValid As Boolean = False
        ''' <summary>整合包名称（读不到时退回文件名）。</summary>
        Public Name As String = ""
        ''' <summary>图标本地路径（已解压到临时目录）；空字符串表示没有图标。</summary>
        Public IconPath As String = ""
        ''' <summary>类型名：CurseForge / Modrinth / HMCL / MCBBS / MultiMC / 启动器包。</summary>
        Public TypeName As String = ""
        ''' <summary>附加说明：游戏版本、加载器、压缩包体积等（可为空）。</summary>
        Public Detail As String = ""
        ''' <summary>原始文件路径。</summary>
        Public File As String = ""
        ''' <summary>整合包类型编号：0 CurseForge / 1 HMCL / 2 MultiMC / 3 MCBBS / 4 Modrinth / 9 启动器包；-1 表示不是整合包。</summary>
        Public PackType As Integer = -1
        ''' <summary>包内声明的原版游戏版本；解析不到时为空字符串。</summary>
        Public MinecraftVersion As String = ""
        ''' <summary>包内声明的 Mod 加载器版本（Forge / NeoForge / Fabric）。</summary>
        Public LoaderVersions As New Dictionary(Of ModLoaders, String)
        ''' <summary>包内声明但 TCL 不支持的加载器（例如 quilt-0.26.0），用于给出明确提示。</summary>
        Public UnsupportedLoaders As New List(Of String)

        ''' <summary>加载器展示文本，例如「Forge 47.2.0」；没有加载器时为空。</summary>
        Public ReadOnly Property LoaderText As String
            Get
                Dim Parts As New List(Of String)
                If LoaderVersions.ContainsKey(ModLoaders.Forge) Then Parts.Add("Forge " & LoaderVersions(ModLoaders.Forge))
                If LoaderVersions.ContainsKey(ModLoaders.NeoForge) Then Parts.Add("NeoForge " & LoaderVersions(ModLoaders.NeoForge))
                If LoaderVersions.ContainsKey(ModLoaders.Fabric) Then Parts.Add("Fabric " & LoaderVersions(ModLoaders.Fabric))
                Return Parts.Join(" + ")
            End Get
        End Property
    End Class

    ''' <summary>
    ''' 只读地预读一个整合包压缩包的名称、图标、类型、游戏版本与加载器版本，不做任何安装动作。
    ''' <para/>新增；任何解析失败都不会抛出（返回 IsValid=False 或退化信息），
    ''' 调用方按普通文件流程继续处理即可。
    ''' </summary>
    ''' <param name="File">压缩包路径。</param>
    Public Function McModpackReadInfo(File As String) As McModpackInfo
        Dim Info As New McModpackInfo With {.Name = PathUtils.GetFileNameWithoutExtension(File), .File = File}
        Try
            Using Archive As ZipArchive = FileUtils.OpenZip(File)
                ParsePackInfo(Archive, Info)
            End Using
        Catch ex As Exception
            Logger.Warn(ex, $"预读整合包信息失败：{File}")
        End Try
        '体积（关闭压缩包后计算，避免占用文件）
        Try
            Dim Bytes As Long = New IO.FileInfo(File).Length
            Dim SizeText As String = If(Bytes >= 1024 * 1024,
                                        Math.Round(Bytes / 1024 / 1024, 1) & " MB",
                                        Math.Max(1, Bytes \ 1024) & " KB")
            Info.Detail = If(String.IsNullOrEmpty(Info.Detail), SizeText, Info.Detail & " · " & SizeText)
        Catch ex As Exception
        End Try
        Return Info
    End Function

    ''' <summary>
    ''' 在已打开的压缩包上解析整合包信息。安装器直接复用它，保证「预读」与「实际安装」用的是同一份解析。
    ''' <para/>会修改 <paramref name="Info"/> 的 IsValid / PackType / TypeName / Name / MinecraftVersion /
    ''' LoaderVersions / UnsupportedLoaders / Detail（不含体积与图标）。
    ''' </summary>
    Public Sub ParsePackInfo(Archive As ZipArchive, Info As McModpackInfo)
        '1. 判定类型与基准目录（与 ModpackInstall 的优先级完全一致）
        Dim PackType As Integer = -1
        Dim Base As String = ""
        If HasEntry(Archive, "mcbbs.packmeta") Then
            PackType = 3
        ElseIf HasEntry(Archive, "mmc-pack.json") Then
            PackType = 2
        ElseIf HasEntry(Archive, "modrinth.index.json") Then
            PackType = 4
        ElseIf HasEntry(Archive, "manifest.json") Then
            Dim Manifest As JObject = ReadJson(Archive, "manifest.json")
            PackType = If(Manifest IsNot Nothing AndAlso Manifest("addons") Is Nothing, 0, 3)
        ElseIf HasEntry(Archive, "modpack.json") Then
            PackType = 1
        ElseIf HasEntry(Archive, "modpack.zip") OrElse HasEntry(Archive, "modpack.mrpack") Then
            PackType = 9
        Else
            '一级子目录（整合包常被多套一层文件夹）
            For Each Entry In Archive.Entries
                Dim Parts As String() = Entry.FullName.Split("/"c)
                If Parts.Count <> 2 Then Continue For
                Base = Parts(0) & "/"
                Select Case Parts(1)
                    Case "mcbbs.packmeta" : PackType = 3
                    Case "mmc-pack.json" : PackType = 2
                    Case "modrinth.index.json" : PackType = 4
                    Case "manifest.json"
                        Dim Manifest As JObject = ReadJson(Archive, Base & "manifest.json")
                        PackType = If(Manifest IsNot Nothing AndAlso Manifest("addons") Is Nothing, 0, 3)
                    Case "modpack.json" : PackType = 1
                    Case "modpack.zip", "modpack.mrpack" : PackType = 9
                End Select
                If PackType >= 0 Then Exit For
            Next
        End If

        If PackType < 0 Then Return '不是整合包
        Info.IsValid = True
        Info.PackType = PackType
        Info.TypeName = ModpackTypeName(PackType)

        '2. 按类型解析名称 / 游戏版本 / 加载器
        Select Case PackType
            Case 0 'CurseForge
                Dim Json As JObject = ReadJson(Archive, Base & "manifest.json")
                ReadPackName(Json, Info)
                ParseCurseforgeLoaders(Json, Info)
            Case 1 'HMCL
                Dim Json As JObject = ReadJson(Archive, Base & "modpack.json")
                ReadPackName(Json, Info)
                If Json IsNot Nothing AndAlso Json("gameVersion") IsNot Nothing Then
                    Info.MinecraftVersion = Json("gameVersion").ToString().Trim()
                End If
            Case 2 'MultiMC
                ParseMmcLoaders(ReadJson(Archive, Base & "mmc-pack.json"), Info)
                Dim InstanceCfg As String = ReadText(Archive, Base & "instance.cfg")
                If InstanceCfg <> "" Then
                    Dim NameMatch As Match = Regex.Match(InstanceCfg, "(?im)^\s*name\s*=\s*([^\r\n]+)")
                    If NameMatch.Success AndAlso Not String.IsNullOrWhiteSpace(NameMatch.Groups(1).Value) Then
                        Info.Name = NameMatch.Groups(1).Value.Trim()
                    End If
                End If
            Case 3 'MCBBS（mcbbs.packmeta 或带 addons 的 manifest.json）
                Dim JsonFile As String = If(HasEntry(Archive, Base & "mcbbs.packmeta"), "mcbbs.packmeta", "manifest.json")
                Dim Json As JObject = ReadJson(Archive, Base & JsonFile)
                ReadPackName(Json, Info)
                ParseMcbbsLoaders(Json, Info)
            Case 4 'Modrinth
                Dim Json As JObject = ReadJson(Archive, Base & "modrinth.index.json")
                ReadPackName(Json, Info)
                ParseModrinthLoaders(Json, Info)
        End Select

        '3. 组装展示用说明
        Dim DetailParts As New List(Of String)
        If Not String.IsNullOrWhiteSpace(Info.MinecraftVersion) Then DetailParts.Add(ModLang.L("游戏版本 ") & Info.MinecraftVersion)
        If Not String.IsNullOrEmpty(Info.LoaderText) Then DetailParts.Add(Info.LoaderText)
        If Info.UnsupportedLoaders.Any() Then DetailParts.Add(ModLang.L("不支持的加载器：") & Info.UnsupportedLoaders.Join("、"))
        If DetailParts.Any() Then Info.Detail = DetailParts.Join(" · ")
    End Sub

    ''' <summary>读取整合包名称（为空时保留调用方给的默认名）。</summary>
    Private Sub ReadPackName(Json As JObject, Info As McModpackInfo)
        If Json Is Nothing Then Return
        Dim Token As JToken = Json("name")
        If Token IsNot Nothing AndAlso Not String.IsNullOrWhiteSpace(Token.ToString()) Then Info.Name = Token.ToString().Trim()
    End Sub

    ''' <summary>
    ''' 解析 CurseForge manifest：minecraft.version + modLoaders。
    ''' <para/>modLoaders 可能列多个，只有 primary=true 的那个是该整合包实际使用的（其余多为作者试过但没用的），
    ''' 之前把所有条目都写进版本信息会导致装出错误的加载器组合，修正。
    ''' </summary>
    Private Sub ParseCurseforgeLoaders(Json As JObject, Info As McModpackInfo)
        If Json Is Nothing Then Return
        Dim MinecraftNode As JObject = TryCast(Json("minecraft"), JObject)
        If MinecraftNode Is Nothing Then Return
        Dim VersionToken As JToken = MinecraftNode("version")
        If VersionToken IsNot Nothing Then Info.MinecraftVersion = VersionToken.ToString().Trim()
        Dim Loaders As JArray = TryCast(MinecraftNode("modLoaders"), JArray)
        If Loaders Is Nothing OrElse Loaders.Count = 0 Then Return
        '先找 primary=true 的那一条
        Dim PrimaryId As String = ""
        For Each Item As JToken In Loaders
            Dim LoaderObject As JObject = TryCast(Item, JObject)
            If LoaderObject Is Nothing Then Continue For
            Dim PrimaryToken As JToken = LoaderObject("primary")
            If PrimaryToken IsNot Nothing AndAlso PrimaryToken.ToString().Trim().Equals("True", StringComparison.OrdinalIgnoreCase) Then
                Dim IdToken As JToken = LoaderObject("id")
                If IdToken IsNot Nothing Then PrimaryId = IdToken.ToString()
                Exit For
            End If
        Next
        If PrimaryId <> "" Then
            ParseLoaderId(PrimaryId, Info)
            Return
        End If
        '没有 primary 标记时退回遍历全部
        For Each Item As JToken In Loaders
            Dim LoaderObject As JObject = TryCast(Item, JObject)
            If LoaderObject Is Nothing Then Continue For
            Dim IdToken As JToken = LoaderObject("id")
            If IdToken IsNot Nothing Then ParseLoaderId(IdToken.ToString(), Info)
        Next
    End Sub

    ''' <summary>把 CurseForge 风格的加载器 id（forge-47.2.0 / neoforge-21.1.80 / fabric-0.15.11）写入信息。</summary>
    Private Sub ParseLoaderId(Id As String, Info As McModpackInfo)
        If String.IsNullOrWhiteSpace(Id) Then Return
        Dim Text As String = Id.Trim()
        Dim Lower As String = Text.ToLowerInvariant()
        If Lower.StartsWithF("forge-") Then
            '老格式 forge-1.12.2-recommended / forge-1.12.2-latest 无法确定具体版本
            If Lower.EndsWithF("-recommended") OrElse Lower.EndsWithF("-latest") Then
                Info.UnsupportedLoaders.Add(Text)
                Return
            End If
            Info.LoaderVersions(ModLoaders.Forge) = Text.Substring("forge-".Length)
        ElseIf Lower.StartsWithF("neoforge-") Then
            Info.LoaderVersions(ModLoaders.NeoForge) = Text.Substring("neoforge-".Length)
        ElseIf Lower.StartsWithF("fabric-") Then
            Info.LoaderVersions(ModLoaders.Fabric) = Text.Substring("fabric-".Length)
        ElseIf Lower.StartsWithF("quilt-") Then
            Info.UnsupportedLoaders.Add(Text) 'TCL 不支持 Quilt
        Else
            Info.UnsupportedLoaders.Add(Text)
        End If
    End Sub

    ''' <summary>解析 Modrinth 的 dependencies（minecraft / forge / neoforge / fabric-loader / quilt-loader）。</summary>
    Private Sub ParseModrinthLoaders(Json As JObject, Info As McModpackInfo)
        If Json Is Nothing Then Return
        Dim Deps As JObject = TryCast(Json("dependencies"), JObject)
        If Deps Is Nothing Then Return
        For Each Prop As JProperty In Deps.Properties
            Dim Value As String = If(Prop.Value Is Nothing, "", Prop.Value.ToString().Trim())
            Select Case Prop.Name.ToLowerInvariant()
                Case "minecraft" : Info.MinecraftVersion = Value
                Case "forge" : Info.LoaderVersions(ModLoaders.Forge) = Value
                Case "neoforge", "neo-forge" : Info.LoaderVersions(ModLoaders.NeoForge) = Value
                Case "fabric-loader" : Info.LoaderVersions(ModLoaders.Fabric) = Value
                Case "quilt-loader" : Info.UnsupportedLoaders.Add("quilt-" & Value)
            End Select
        Next
    End Sub

    ''' <summary>解析 MultiMC 的 mmc-pack.json components。</summary>
    Private Sub ParseMmcLoaders(Json As JObject, Info As McModpackInfo)
        If Json Is Nothing Then Return
        Dim Components As JArray = TryCast(Json("components"), JArray)
        If Components Is Nothing Then Return
        For Each Component As JToken In Components
            If Component("uid") Is Nothing Then Continue For
            Dim Version As String = If(Component("version") Is Nothing, "", Component("version").ToString().Trim())
            Select Case Component("uid").ToString()
                Case "net.minecraft" : Info.MinecraftVersion = Version
                Case "net.minecraftforge" : Info.LoaderVersions(ModLoaders.Forge) = Version
                Case "net.neoforged" : Info.LoaderVersions(ModLoaders.NeoForge) = Version
                Case "net.fabricmc.fabric-loader" : Info.LoaderVersions(ModLoaders.Fabric) = Version
                Case "org.quiltmc.quilt-loader" : Info.UnsupportedLoaders.Add("quilt-" & Version)
            End Select
        Next
    End Sub

    ''' <summary>解析 MCBBS packmeta 的 addons 数组。</summary>
    Private Sub ParseMcbbsLoaders(Json As JObject, Info As McModpackInfo)
        If Json Is Nothing Then Return
        Dim Addons As JArray = TryCast(Json("addons"), JArray)
        If Addons Is Nothing Then Return
        For Each Addon As JToken In Addons
            If Addon("id") Is Nothing Then Continue For
            Dim Version As String = If(Addon("version") Is Nothing, "", Addon("version").ToString().Trim())
            Select Case Addon("id").ToString().ToLowerInvariant()
                Case "game" : Info.MinecraftVersion = Version
                Case "forge" : Info.LoaderVersions(ModLoaders.Forge) = Version
                Case "neoforge" : Info.LoaderVersions(ModLoaders.NeoForge) = Version
                Case "fabric" : Info.LoaderVersions(ModLoaders.Fabric) = Version
                Case "quilt" : Info.UnsupportedLoaders.Add("quilt-" & Version)
            End Select
        Next
    End Sub

    ''' <summary>读取压缩包内某个文本文件（忽略大小写）；失败返回空字符串。</summary>
    Private Function ReadText(Archive As ZipArchive, Name As String) As String
        Try
            Dim Entry As ZipArchiveEntry = Archive.Entries.FirstOrDefault(
                Function(E) String.Equals(E.FullName.Replace("\"c, "/"c), Name.Replace("\"c, "/"c), StringComparison.OrdinalIgnoreCase))
            If Entry Is Nothing Then Return ""
            Return Entry.Open().ReadString(Encoding.UTF8)
        Catch ex As Exception
            Return ""
        End Try
    End Function

    ''' <summary>压缩包根目录（或指定子目录）下是否存在某个条目（忽略大小写与分隔符差异）。</summary>
    Private Function HasEntry(Archive As ZipArchive, Name As String) As Boolean
        Return Archive.Entries.Any(Function(E) String.Equals(E.FullName.Replace("\"c, "/"c), Name, StringComparison.OrdinalIgnoreCase))
    End Function

    ''' <summary>读取压缩包内某个 JSON（忽略大小写）；失败返回 Nothing。</summary>
    Private Function ReadJson(Archive As ZipArchive, Name As String) As JObject
        Try
            Dim Entry As ZipArchiveEntry = Archive.Entries.FirstOrDefault(
                Function(E) String.Equals(E.FullName.Replace("\"c, "/"c), Name.Replace("\"c, "/"c), StringComparison.OrdinalIgnoreCase))
            If Entry Is Nothing Then Return Nothing
            Return Entry.Open().ReadString(Encoding.UTF8).DeserializeJson()
        Catch ex As Exception
            Logger.Warn(ex, $"读取整合包内 JSON 失败：{Name}")
            Return Nothing
        End Try
    End Function

    ''' <summary>整合包类型编号 → 展示名。</summary>
    Private Function ModpackTypeName(PackType As Integer) As String
        Select Case PackType
            Case 0 : Return "CurseForge"
            Case 1 : Return "HMCL"
            Case 2 : Return "MultiMC"
            Case 3 : Return "MCBBS"
            Case 4 : Return "Modrinth"
            Case 9 : Return "启动器整合包"
            Case Else : Return ModLang.L("未知类型")
        End Select
    End Function

    ''' <summary>把压缩包内的图标解压到临时目录并返回路径；没有图标时返回空字符串。</summary>
    Private Function ExtractModpackIcon(Archive As ZipArchive, Base As String) As String
        Try
            For Each Candidate In {"icon.png", "pack.png", "logo.png", "icon.jpg", "logo.jpg"}
                Dim Entry As ZipArchiveEntry = Archive.Entries.FirstOrDefault(
                    Function(E) String.Equals(E.FullName.Replace("\"c, "/"c), Base & Candidate, StringComparison.OrdinalIgnoreCase))
                If Entry Is Nothing OrElse Entry.Length <= 0 OrElse Entry.Length > 8 * 1024 * 1024 Then Continue For
                Dim Folder As String = PathTemp & "ModpackIcon\"
                If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
                Dim Target As String = Folder & GetUuid() & "." & PathUtils.GetExtension(Candidate)
                Using InStream = Entry.Open()
                    Using OutStream = IO.File.Create(Target)
                        InStream.CopyTo(OutStream)
                    End Using
                End Using
                Return Target
            Next
        Catch ex As Exception
            Logger.Warn(ex, "提取整合包图标失败")
        End Try
        Return ""
    End Function

    ''' <summary>
    ''' 构建并启动安装给定的整合包文件的加载器，并返回该加载器。若失败则抛出异常。
    ''' 必须在工作线程执行。
    ''' </summary>
    Public Function ModpackInstall(File As String, Optional InstanceName As String = Nothing, Optional Logo As String = Nothing) As LoaderCombo(Of String)
        Logger.Info($"整合包安装请求：{If(File, "null")}")
        Dim Archive As ZipArchive = Nothing
        Dim ArchiveBaseFolder As String = ""
        Try
            '字符校验
            Dim TargetFolder As String = $"{McFolderSelected}versions\{InstanceName}\"
            If TargetFolder.Contains("!") OrElse TargetFolder.Contains(";") Then Hint(ModLang.L("游戏路径中不能含有感叹号或分号：") & TargetFolder, HintType.Red) : Throw New OperationCanceledException
            '获取整合包种类与关键 Json
            Dim PackType As Integer = -1
            Try
                Archive = FileUtils.OpenZip(File)
                '从根目录判断整合包类型
                If Archive.GetEntry("mcbbs.packmeta") IsNot Nothing Then PackType = 3 : Exit Try 'MCBBS 整合包（优先于 manifest.json 判断）
                If Archive.GetEntry("mmc-pack.json") IsNot Nothing Then PackType = 2 : Exit Try 'MMC 整合包（优先于 manifest.json 判断）
                If Archive.GetEntry("modrinth.index.json") IsNot Nothing Then PackType = 4 : Exit Try 'Modrinth 整合包
                If Archive.GetEntry("manifest.json") IsNot Nothing Then
                    Dim Json As JObject = Archive.GetEntry("manifest.json").Open().ReadString(Encoding.UTF8).DeserializeJson()
                    If Json("addons") Is Nothing Then
                        PackType = 0 : Exit Try 'CurseForge 整合包
                    Else
                        PackType = 3 : Exit Try 'MCBBS 整合包
                    End If
                End If
                If Archive.GetEntry("modpack.json") IsNot Nothing Then PackType = 1 : Exit Try 'HMCL 整合包
                If Archive.GetEntry("modpack.zip") IsNot Nothing OrElse Archive.GetEntry("modpack.mrpack") IsNot Nothing Then PackType = 9 : Exit Try '带启动器的压缩包
                '从一级目录判断整合包类型
                For Each Entry In Archive.Entries
                    Dim PathSplits As String() = Entry.FullName.Split("/")
                    ArchiveBaseFolder = PathSplits(0) & "/"
                    '确定为一级目录下
                    If PathSplits.Count <> 2 Then Continue For
                    '判断是否为关键文件
                    If PathSplits(1) = "mcbbs.packmeta" Then PackType = 3 : Exit Try 'MCBBS 整合包（优先于 manifest.json 判断）
                    If PathSplits(1) = "mmc-pack.json" Then PackType = 2 : Exit Try 'MMC 整合包（优先于 manifest.json 判断）
                    If PathSplits(1) = "modrinth.index.json" Then PackType = 4 : Exit Try 'Modrinth 整合包
                    If PathSplits(1) = "manifest.json" Then
                        Dim Json As JObject = Entry.Open().ReadString(Encoding.UTF8).DeserializeJson()
                        If Json("addons") Is Nothing Then
                            PackType = 0 : Exit Try 'CurseForge 整合包
                        Else
                            PackType = 3 : ArchiveBaseFolder = "overrides/" : Exit Try 'MCBBS 整合包
                        End If
                    End If
                    If PathSplits(1) = "modpack.json" Then PackType = 1 : Exit Try 'HMCL 整合包
                    If PathSplits(1) = "modpack.zip" OrElse PathSplits(1) = "modpack.mrpack" Then PackType = 9 : Exit Try '带启动器的压缩包
                Next
            Catch ex As Exception
                If ex.GetDisplay(True).Contains("Error.WinIOError") Then
                    Throw New Exception("打开整合包文件失败", ex)
                ElseIf PathUtils.GetExtension(File) = "rar" Then
                    Throw New Exception("TCL 无法处理 rar 格式的压缩包，请在解压后重新压缩为 zip 格式再试", ex)
                Else
                    Throw New Exception("打开整合包文件失败，文件可能损坏或为不支持的压缩包格式", ex)
                End If
            End Try
            '执行对应的安装方法
            Select Case PackType
                Case 0
                    Logger.Info("整合包种类：CurseForge")
                    Return InstallPackCurseForge(File, Archive, ArchiveBaseFolder, InstanceName, Logo)
                Case 1
                    Logger.Info("整合包种类：HMCL")
                    Return InstallPackHMCL(File, Archive, ArchiveBaseFolder)
                Case 2
                    Logger.Info("整合包种类：MMC")
                    Return InstallPackMMC(File, Archive, ArchiveBaseFolder)
                Case 3
                    Logger.Info("整合包种类：MCBBS")
                    Return InstallPackMCBBS(File, Archive, ArchiveBaseFolder, InstanceName)
                Case 4
                    Logger.Info("整合包种类：Modrinth")
                    Return InstallPackModrinth(File, Archive, ArchiveBaseFolder, InstanceName, Logo)
                Case 9
                    Logger.Info("整合包种类：带启动器的压缩包")
                    Return InstallPackLauncherPack(File, Archive, ArchiveBaseFolder)
                Case Else
                    Logger.Info("整合包种类：未能识别，假定为压缩包")
                    Return InstallPackCompress(File, Archive)
            End Select
        Finally
            If Archive IsNot Nothing Then Archive.Dispose()
        End Try
    End Function

    Private Sub ExtractModpackFiles(InstallTemp As String, FileAddress As String, Optional c As CancellationToken = Nothing, Optional p As ProgressProvider = Nothing)
        DirectoryUtils.Delete(InstallTemp)
        c.ThrowIfCancellationRequested()
        FileUtils.ExtractToDirectory(FileAddress, InstallTemp, c, p)
    End Sub
    ''' <summary>
    ''' 从整合包的 override 目录复制文件，同时设置 TCL 的配置文件与版本隔离。
    ''' 对路径末尾是否为 \ 没有要求。
    ''' </summary>
    Private Sub CopyOverrideDirectory(OverridesFolder As String, VersionFolder As String, Loader As LoaderBase)
        OverridesFolder = PathUtils.AddSlashSuffix(OverridesFolder)
        VersionFolder = PathUtils.AddSlashSuffix(VersionFolder)
        '复制文件
        If DirectoryUtils.Exists(OverridesFolder) Then
            Logger.Info($"处理整合包覆写文件夹：{OverridesFolder} → {VersionFolder}")
            DirectoryUtils.Copy(OverridesFolder, VersionFolder)
        Else
            Logger.Info($"整合包中没有覆写文件夹：{OverridesFolder}")
        End If
        '设置 ini
        Dim OverridesIni As String = $"{OverridesFolder}TCL\Setup.ini"
        Dim VersionIni As String = $"{VersionFolder}TCL\Setup.ini"
        If FileUtils.Exists(OverridesIni) Then
            WriteIni(OverridesIni, "VersionArgumentIndie", 1) '开启版本隔离
            WriteIni(OverridesIni, "VersionArgumentIndieV2", True)
            WriteIni(OverridesIni, "IsStar", False)
            FileUtils.Copy(OverridesIni, VersionIni) '覆写已有的 ini
        Else
            WriteIni(VersionIni, "VersionArgumentIndie", 1) '开启版本隔离
            WriteIni(VersionIni, "VersionArgumentIndieV2", True)
        End If
        IniClearCache(VersionIni) '重置缓存，避免被安装过程中写入的 ini 覆盖
    End Sub
    ''' <summary>
    ''' 弹窗提示整合包中存在不兼容的加载器。
    ''' 如果取消，则抛出 CancelledException。
    ''' </summary>
    Private Sub NotifyIncompatibleLoader(LoaderName As String)
        If MyMsgBox(ModLang.L("整合包中存在不兼容的加载器：") & LoaderName & vbCrLf & ModLang.L("如果你知道如何手动安装它，可以先选择跳过，然后在整合包下载结束后手动安装。"),
            ModLang.L("不兼容的加载器"), ModLang.L("取消"), ModLang.L("不安装 ") & LoaderName & ModLang.L(" 并继续")) = 1 Then Throw New OperationCanceledException
    End Sub

#Region "不同类型整合包的安装方法"

    'CurseForge
    Private Function InstallPackCurseForge(FileAddress As String, Archive As ZipArchive, ArchiveBaseFolder As String,
                                           Optional InstanceName As String = Nothing, Optional Logo As String = Nothing) As LoaderCombo(Of String)

        '读取 Json 文件
        Dim Json As JObject
        Try
            Json = Archive.GetEntry(ArchiveBaseFolder & "manifest.json").Open().ReadString().DeserializeJson()
        Catch ex As Exception
            Throw New Exception("CurseForge 整合包安装信息存在问题", ex)
        End Try
        If Json("minecraft") Is Nothing OrElse Json("minecraft")("version") Is Nothing Then Throw New Exception("CurseForge 整合包未提供 Minecraft 版本信息")

        '获取版本名
        If InstanceName Is Nothing Then
            InstanceName = If(Json("name"), "")
            Dim Validate As New ValidateFolderName(McFolderSelected & "versions")
            If Validate.Validate(InstanceName) <> "" Then InstanceName = ""
            If InstanceName = "" Then InstanceName = MyMsgBoxInput(ModLang.L("输入版本名称"), "", "", New Collection(Of Validate) From {Validate})
            If String.IsNullOrEmpty(InstanceName) Then Throw New OperationCanceledException
        End If
        Dim VersionFolder As String = PathUtils.AddSlashSuffix(Path.Combine(McFolderSelected, "versions", InstanceName) & "\")

        '获取 Mod API 版本信息
        '改用与拖入弹窗同一份解析（ParsePackInfo），避免「弹窗显示的版本」与「实际安装的版本」两套逻辑
        Dim PackInfo As New McModpackInfo
        ParsePackInfo(Archive, PackInfo)
        If String.IsNullOrWhiteSpace(PackInfo.MinecraftVersion) Then Throw New Exception("CurseForge 整合包未提供 Minecraft 版本信息")
        If PackInfo.UnsupportedLoaders.Any() Then
            Hint(ModLang.L("该整合包使用了 TCL 不支持的加载器：") & PackInfo.UnsupportedLoaders.Join("、"), HintType.Red)
            Throw New OperationCanceledException
        End If
        Logger.Info($"整合包：Minecraft {PackInfo.MinecraftVersion}，{If(PackInfo.LoaderText = "", "无加载器", PackInfo.LoaderText)}")
        Dim TargetVersion As New McVersion(PackInfo.MinecraftVersion)
        For Each LoaderPair In PackInfo.LoaderVersions
            TargetVersion.ModLoaderVersions(LoaderPair.Key) = LoaderPair.Value
        Next
        '解压
        Dim InstallTemp As String = RequestTaskTempFolder()
        Dim InstallLoaders As New List(Of LoaderBase)
        Dim OverrideHome As String = If(Json("overrides"), "")
        If OverrideHome <> "" Then
                Dim OverridesFolder As String = InstallTemp & ArchiveBaseFolder & If(OverrideHome = "." OrElse OverrideHome = "./", "", OverrideHome)
            If Not PathUtils.IsParentOf(InstallTemp, OverridesFolder) Then Throw New Exception("整合包的 overrides 路径超出了解压目录，请向整合包作者反馈此问题！")
            InstallLoaders.Add(New LoaderTask(Of String, Integer)("解压整合包文件",
            Sub(Task As LoaderTask(Of String, Integer))
                ExtractModpackFiles(InstallTemp, FileAddress, Task.CreateCancellationToken(), Task.CreateSyncProgressProvider(ToPercentage:=0.6))
                CopyOverrideDirectory(OverridesFolder, VersionFolder, Task)
            End Sub) With {
            .ProgressWeight = FileUtils.GetInfo(FileAddress).Length / 1024 / 1024 / 6, .Block = False}) '每 6M 需要 1s
        End If
        '获取 Mod 列表
        Dim ModList As New List(Of Integer)
        Dim ModOptionalList As New List(Of Integer)
        For Each ModEntry In If(Json("files"), {})
            If ModEntry("projectID") Is Nothing OrElse ModEntry("fileID") Is Nothing Then
                Hint(ModLang.L("某项 Mod 缺少必要信息，已跳过：") & ModEntry.ToString)
                Continue For
            End If
            ModList.Add(ModEntry("fileID"))
            If ModEntry("required") IsNot Nothing AndAlso Not ModEntry("required").ToObject(Of Boolean) Then ModOptionalList.Add(ModEntry("fileID"))
        Next
        If ModList.Any Then
            Dim ModDownloadLoaders As New List(Of LoaderBase)
            '获取 Mod 下载信息
            ModDownloadLoaders.Add(New LoaderTask(Of Integer, JArray)("获取 Mod 下载信息",
            Sub(Task As LoaderTask(Of Integer, JArray))
                Task.Output = DlModRequest("https://api.curseforge.com/v1/mods/files", HttpMethod.Post, "{""fileIds"": [" & ModList.Join(",") & "]}", "application/json")("data")
                If ModList.Count = Task.Output.Count Then Return
                '如果文件已被删除，则 API 会跳过那一项，导致返回的列表数量小于请求的数量。后面的都是对此的错误处理。
                '获取缺失的 fileId 列表
                Dim MissingIds = ModList.Except(Task.Output.Select(Function(j) j("id").ToObject(Of Integer))).ToList
                Logger.Info($"缺失的 fileIds：{MissingIds.Join("、")}")
                '将 fileId 映射到 projectId
                Dim Missings = MissingIds.Select(Of (ProjectId As Integer, FileId As Integer))(
                    Function(id) (CType(Json("files").AsEnumerable.First(Function(f) f("fileID").ToObject(Of Integer) = id), JObject)("projectID").ToObject(Of Integer), id)).ToList
                Logger.Info($"缺失项：{Missings.Join("、")}")
                '尝试获取对应的工程信息
                Dim Projects As New List(Of ResourceProject)
                Try
                    Projects = ResourceProject.FromProjectIds(Missings.Select(Function(e) e.ProjectId.ToString), ResourcePlatforms.CurseForge).ToList
                Catch ex As Exception
                    Logger.Warn(ex, "获取缺失的工程信息失败")
                End Try
                Dim MissingDesc As New List(Of String)
                For Each Missing In Missings
                    Dim Project = Projects.FirstOrDefault(Function(p) p.Id = Missing.ProjectId)
                    If Project Is Nothing Then
                        MissingDesc.Add($"未知 CurseForge 项目 (项目 ID {Missing.ProjectId}，文件 ID {Missing.FileId})")
                    Else
                        MissingDesc.Add($"{Project.TranslatedName} (文件 ID {Missing.FileId})")
                    End If
                Next
                If MyMsgBox(ModLang.L("未找到该整合包所需的以下内容，它们可能已被原作者删除：") & vbCrLf & "- " & MissingDesc.Join(vbCrLf & "- ") & vbCrLf & vbCrLf & ModLang.L("是否继续安装？缺失这些内容可能导致整合包出现异常！"),
                    ModLang.L("整合包内容缺失"), ModLang.L("继续"), ModLang.L("取消"), IsWarn:=True) = 2 Then
                    Task.Cancel()
                End If
            End Sub) With {.ProgressWeight = ModList.Count / 10}) '每 10 Mod 需要 1s
            '构造 NetFile
            ModDownloadLoaders.Add(New LoaderTask(Of JArray, List(Of NetFile))("构造 Mod 下载信息",
            Sub(Task As LoaderTask(Of JArray, List(Of NetFile)))
                Dim FileList As New Dictionary(Of Integer, NetFile)
                For Each ModJson In Task.Input
                    Dim Id As Integer = ModJson("id").ToObject(Of Integer)
                    '跳过重复的 Mod（疑似 CurseForge Bug）
                    If FileList.ContainsKey(Id) Then Continue For
                    '可选 Mod 提示
                    If ModOptionalList.Contains(Id) Then
                        If MyMsgBox(ModLang.L("是否要下载整合包中的可选文件 ") & ModJson("displayName").ToString & ModLang.L("？"), ModLang.L("下载可选文件"), ModLang.L("是"), ModLang.L("否")) = 2 Then
                            Continue For
                        End If
                    End If
                    '根据 modules 和文件名后缀判断资源类型（统一走 ResourceKit，避免各页面各自重写；modules 为 null/空 时落到默认 mods）
                    Dim ModuleToken As JToken = ModJson("modules")
                    Dim ModuleNames As IReadOnlyCollection(Of String) =
                        If(TypeOf ModuleToken Is JArray AndAlso ModuleToken.Any(),
                           CType(ModuleToken, JArray).Select(Function(l) l("name").ToString).ToList,
                           Nothing)
                    Dim ModuleTarget = ResourceKit.InferModuleTarget(ModuleNames, ModJson?("FileName")?.ToString())
                    Dim TargetFolder As String = ModuleTarget.Folder
                    Dim Type As ResourceTypes = ModuleTarget.Type
                    '建立 CompFile
                    Dim File = ResourceVersion.FromPlatformJson(ModJson, Type)
                    If Not File.DownloadAvailable Then Continue For
                    '实际的添加
                    FileList.Add(Id, File.ToNetFile(PathUtils.AddSlashSuffix(Path.Combine(VersionFolder, TargetFolder)),
                        ResourceVersion.DownloadReason.ModPack, TargetVersion.VanillaName, TargetVersion.ModLoaders))
                    Task.Progress += 1 / (1 + ModList.Count)
                Next
                Task.Output = FileList.Values.ToList
            End Sub) With {.ProgressWeight = ModList.Count / 200, .Show = False}) '每 200 Mod 需要 1s
            '下载 Mod 文件
            ModDownloadLoaders.Add(New LoaderDownload("下载 Mod", New List(Of NetFile)) With {.ProgressWeight = ModList.Count * 1.5}) '每个 Mod 需要 1.5s
            '构造加载器
            InstallLoaders.Add(New LoaderCombo(Of Integer)("下载 Mod（主加载器）", ModDownloadLoaders) With
                {.Show = False, .ProgressWeight = ModDownloadLoaders.Sum(Function(l) l.ProgressWeight)})
        End If

        '构造加载器
        Dim Target As New McComponents(TargetVersion.VanillaName, TargetVersion.ModLoaderVersions)
        Dim MergeLoaders = McInstallLoadersGet(New McInstallRequest With {
            .NewInstanceName = InstanceName, .InstanceFolder = VersionFolder, .Components = Target})
        '构造总加载器
        Dim Loaders As New List(Of LoaderBase)
        Loaders.Add(New LoaderCombo(Of String)("整合包安装", InstallLoaders) With {.Show = False, .Block = False, .ProgressWeight = InstallLoaders.Sum(Function(l) l.ProgressWeight)})
        Loaders.Add(New LoaderCombo(Of String)("游戏安装", MergeLoaders) With {.Show = False, .ProgressWeight = MergeLoaders.Sum(Function(l) l.ProgressWeight)})
        Loaders.Add(New LoaderTask(Of String, String)("最终整理文件",
        Sub(Task As LoaderTask(Of String, String))
            '设置图标
            If Logo IsNot Nothing AndAlso FileUtils.Exists(Logo) Then
                FileUtils.Copy(Logo, VersionFolder & "TCL\Logo.png")
                WriteIni(VersionFolder & "TCL\Setup.ini", "Logo", "TCL\Logo.png")
                WriteIni(VersionFolder & "TCL\Setup.ini", "LogoCustom", "True")
                Logger.Info($"已设置整合包 Logo：{Logo}")
            End If
            '解压地图文件
            Dim SaveFolder As String = $"{VersionFolder}saves\"
            For Each MapFile In DirectoryUtils.EnumerateFiles(SaveFolder, searchPattern:="*.zip")
                FileUtils.ExtractToDirectory(MapFile, SaveFolder, Task.CreateCancellationToken())
                FileUtils.Delete(MapFile)
            Next
            '删除原始整合包文件
            For Each FileToDelete As String In {VersionFolder & "原始整合包.zip", VersionFolder & "原始整合包.mrpack"}
                If FileUtils.Exists(FileToDelete) Then
                    Logger.Info($"删除原始整合包文件：{FileToDelete}")
                    FileUtils.Delete(FileToDelete)
                End If
            Next
            If FileUtils.Exists(FileAddress) AndAlso PathUtils.GetFileNameWithoutExtension(FileAddress) = "modpack" Then
                Logger.Info($"删除安装整合包文件：{FileAddress}")
                FileUtils.Delete(FileAddress)
            End If
        End Sub) With {.ProgressWeight = 0.1, .Show = False})

        '重复任务检查
        Dim LoaderName As String = "CurseForge 整合包安装：" & InstanceName & " "
        If LoaderTaskbar.Any(Function(l) l.Name = LoaderName) Then
            Hint(ModLang.L("该整合包正在安装中！"), HintType.Red)
            Throw New OperationCanceledException
        End If

        '启动
        Dim Loader As New LoaderCombo(Of String)(LoaderName, Loaders) With {.OnStateChanged = AddressOf McInstallState}
        Loader.Start(VersionFolder)
        LoaderTaskbarAdd(Loader)
        FrmMain.BtnExtraDownload.ShowRefresh()
        Return Loader
    End Function

    'Modrinth
    Private Function InstallPackModrinth(FileAddress As String, Archive As ZipArchive, ArchiveBaseFolder As String, Optional InstanceName As String = Nothing, Optional Logo As String = Nothing) As LoaderCombo(Of String)

        '读取 Json 文件
        Dim Json As JObject
        Try
            Json = Archive.GetEntry(ArchiveBaseFolder & "modrinth.index.json").Open().ReadString().DeserializeJson()
        Catch ex As Exception
            Throw New Exception("Modrinth 整合包安装信息存在问题", ex)
        End Try
        If Json("dependencies") Is Nothing OrElse Json("dependencies")("minecraft") Is Nothing Then Throw New Exception("Modrinth 整合包未提供 Minecraft 版本信息")
        '获取 Mod API 版本信息
        '与拖入弹窗共用 ParsePackInfo，不再用 "Unknown" 占位再回填
        Dim PackInfo As New McModpackInfo
        ParsePackInfo(Archive, PackInfo)
        If String.IsNullOrWhiteSpace(PackInfo.MinecraftVersion) Then Throw New Exception("Modrinth 整合包未提供 Minecraft 版本信息")
        If PackInfo.UnsupportedLoaders.Any() Then
            Hint(ModLang.L("该整合包使用了 TCL 不支持的加载器：") & PackInfo.UnsupportedLoaders.Join("、"), HintType.Red)
            Throw New OperationCanceledException
        End If
        Logger.Info($"整合包：Minecraft {PackInfo.MinecraftVersion}，{If(PackInfo.LoaderText = "", "无加载器", PackInfo.LoaderText)}")
        Dim TargetVersion As New McVersion(PackInfo.MinecraftVersion)
        For Each LoaderPair In PackInfo.LoaderVersions
            TargetVersion.ModLoaderVersions(LoaderPair.Key) = LoaderPair.Value
        Next
        '获取版本名
        If InstanceName Is Nothing Then
            InstanceName = If(Json("name"), "")
            Dim Validate As New ValidateFolderName(McFolderSelected & "versions")
            If Validate.Validate(InstanceName) <> "" Then InstanceName = ""
            If InstanceName = "" Then InstanceName = MyMsgBoxInput(ModLang.L("输入版本名称"), "", "", New Collection(Of Validate) From {Validate})
            If String.IsNullOrEmpty(InstanceName) Then Throw New OperationCanceledException
        End If
        Dim VersionFolder As String = Path.Combine(McFolderSelected, "versions", InstanceName) & "\"
        '解压
        Dim InstallTemp As String = RequestTaskTempFolder()
        Dim InstallLoaders As New List(Of LoaderBase)
        InstallLoaders.Add(New LoaderTask(Of String, Integer)("解压整合包文件",
        Sub(Task As LoaderTask(Of String, Integer))
            ExtractModpackFiles(InstallTemp, FileAddress, Task.CreateCancellationToken(), Task.CreateSyncProgressProvider(ToPercentage:=0.5))
            CopyOverrideDirectory(InstallTemp & ArchiveBaseFolder & "overrides", VersionFolder, Task)
            CopyOverrideDirectory(InstallTemp & ArchiveBaseFolder & "client-overrides", VersionFolder, Task)
        End Sub) With {.ProgressWeight = FileUtils.GetInfo(FileAddress).Length / 1024 / 1024 / 6, .Block = False}) '每 6M 需要 1s
        '获取下载文件列表
        Dim FileList As New List(Of NetFile)
        For Each File In If(Json("files"), {})
            '检查是否需要该文件
            If File("env") IsNot Nothing Then
                Select Case File("env")("client").ToString
                    Case "optional"
                        If MyMsgBox(ModLang.L("是否要下载可选文件 ") & PathUtils.GetLastPart(File("path").ToString) & ModLang.L("？"),
                                    ModLang.L("下载可选文件"), ModLang.L("是"), ModLang.L("否")) = 2 Then
                            Continue For
                        End If
                    Case "unsupported"
                        Continue For
                End Select
            End If
            '添加下载文件
            Dim Urls = File("downloads").SelectMany(Function(t) ResourceVersion.ParseCurseForgeDownloadUrls(t.ToString)).ToList
            Urls.AddRange(Urls.Select(Function(u) DlSourceModGet(u)).ToList)
            Urls = ResourceVersion.ParseModrinthTrackArguments(Urls,
                ResourceVersion.DownloadReason.ModPack, TargetVersion.VanillaName, TargetVersion.ModLoaders).ToList 'Modrinth 来源追踪信息
            Urls = Urls.Distinct.ToList()
            Dim TargetPath As String = Path.Combine(VersionFolder, File("path").ToString().TrimStart(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar))
            If Not PathUtils.IsParentOf(VersionFolder, TargetPath) Then
                MyMsgBox(ModLang.L("整合包的文件路径超出了版本文件夹，请向整合包作者反馈此问题！") & vbCrLf & ModLang.L("目标：") & Path.GetFullPath(TargetPath) & vbCrLf & ModLang.L("错误的文件：") & TargetPath, ModLang.L("文件路径校验失败"), IsWarn:=True)
                Throw New OperationCanceledException
            End If
            FileList.Add(New NetFile(Urls, TargetPath,
                New FileChecker With {.Hash = File("hashes")("sha1").ToString}, True)) 'Modrinth 整合包不应校验文件大小（他们官方自己说的）
        Next
        If FileList.Any Then
            InstallLoaders.Add(New LoaderDownload("下载额外文件", FileList) With {.ProgressWeight = FileList.Count * 1.5}) '每个 Mod 需要 1.5s
        End If

        '构造加载器
        Dim Target As New McComponents(TargetVersion.VanillaName, TargetVersion.ModLoaderVersions)
        Dim MergeLoaders = McInstallLoadersGet(New McInstallRequest With {
            .NewInstanceName = InstanceName, .InstanceFolder = VersionFolder,
            .Components = Target})
        '构造总加载器
        Dim Loaders As New List(Of LoaderBase)
        Loaders.Add(New LoaderCombo(Of String)("整合包安装", InstallLoaders) With {.Show = False, .Block = False, .ProgressWeight = InstallLoaders.Sum(Function(l) l.ProgressWeight)})
        Loaders.Add(New LoaderCombo(Of String)("游戏安装", MergeLoaders) With {.Show = False, .ProgressWeight = MergeLoaders.Sum(Function(l) l.ProgressWeight)})
        Loaders.Add(New LoaderTask(Of String, String)("最终整理文件",
        Sub(Task As LoaderTask(Of String, String))
            '设置图标
            If Logo IsNot Nothing AndAlso FileUtils.Exists(Logo) Then
                FileUtils.Copy(Logo, VersionFolder & "TCL\Logo.png")
                WriteIni(VersionFolder & "TCL\Setup.ini", "Logo", "TCL\Logo.png")
                WriteIni(VersionFolder & "TCL\Setup.ini", "LogoCustom", "True")
                Logger.Info($"已设置整合包 Logo：{Logo}")
            End If
            '删除原始整合包文件
            For Each FileToDelete As String In {VersionFolder & "原始整合包.zip", VersionFolder & "原始整合包.mrpack"}
                If FileUtils.Exists(FileToDelete) Then
                    Logger.Info($"删除原始整合包文件：{FileToDelete}")
                    FileUtils.Delete(FileToDelete)
                End If
            Next
            If FileUtils.Exists(FileAddress) AndAlso PathUtils.GetFileNameWithoutExtension(FileAddress) = "modpack" Then
                Logger.Info($"删除安装整合包文件：{FileAddress}")
                FileUtils.Delete(FileAddress)
            End If
        End Sub) With {.ProgressWeight = 0.1, .Show = False})

        '重复任务检查
        Dim LoaderName As String = $"Modrinth 整合包安装：{InstanceName} "
        If LoaderTaskbar.Any(Function(l) l.Name = LoaderName) Then
            Hint(ModLang.L("该整合包正在安装中！"), HintType.Red)
            Throw New OperationCanceledException
        End If

        '启动
        Dim Loader As New LoaderCombo(Of String)(LoaderName, Loaders) With {.OnStateChanged = AddressOf McInstallState}
        Loader.Start(VersionFolder)
        LoaderTaskbarAdd(Loader)
        FrmMain.BtnExtraDownload.ShowRefresh()
        Return Loader
    End Function

    'HMCL
    Private Function InstallPackHMCL(FileAddress As String, Archive As ZipArchive, ArchiveBaseFolder As String) As LoaderCombo(Of String)
        '读取 Json 文件
        Dim Json As JObject
        Try
            Json = Archive.GetEntry(ArchiveBaseFolder & "modpack.json").Open().ReadString(Encoding.UTF8).DeserializeJson()
        Catch ex As Exception
            Throw New Exception("HMCL 整合包安装信息存在问题", ex)
        End Try
        '获取版本名
        Dim InstanceName As String = If(Json("name"), "")
        Dim Validate As New ValidateFolderName(McFolderSelected & "versions")
        If Validate.Validate(InstanceName) <> "" Then InstanceName = ""
        If InstanceName = "" Then InstanceName = MyMsgBoxInput(ModLang.L("输入版本名称"), "", "", New Collection(Of Validate) From {Validate})
        If String.IsNullOrEmpty(InstanceName) Then Throw New OperationCanceledException
        Dim VersionFolder As String = Path.Combine(McFolderSelected, "versions", InstanceName) & "\"
        '解压
        Dim InstallTemp As String = RequestTaskTempFolder()
        Dim InstallLoaders As New List(Of LoaderBase)
        InstallLoaders.Add(New LoaderTask(Of String, Integer)("解压整合包文件",
        Sub(Task As LoaderTask(Of String, Integer))
            ExtractModpackFiles(InstallTemp, FileAddress, Task.CreateCancellationToken(), Task.CreateSyncProgressProvider(ToPercentage:=0.6))
            CopyOverrideDirectory(InstallTemp & ArchiveBaseFolder & "minecraft", VersionFolder, Task)
        End Sub) With {.ProgressWeight = FileUtils.GetInfo(FileAddress).Length / 1024 / 1024 / 6, .Block = False}) '每 6M 需要 1s
        '构造游戏本体安装加载器
        If Json("gameVersion") Is Nothing Then Throw New Exception("该 HMCL 整合包未提供游戏版本信息，无法安装！")
        Dim Target As New McComponents(Json("gameVersion").ToString, New Dictionary(Of ModLoaders, String))
        Dim MergeLoaders = McInstallLoadersGet(New McInstallRequest With {
            .NewInstanceName = InstanceName, .InstanceFolder = VersionFolder,
            .Components = Target})
        '构造总加载器
        Dim Loaders As New List(Of LoaderBase) From {
            New LoaderCombo(Of String)("整合包安装", InstallLoaders) With {.Show = False, .Block = False, .ProgressWeight = InstallLoaders.Sum(Function(l) l.ProgressWeight)},
            New LoaderCombo(Of String)("游戏安装", MergeLoaders) With {.Show = False, .ProgressWeight = MergeLoaders.Sum(Function(l) l.ProgressWeight)}
        }
        '重复任务检查
        Dim LoaderName As String = "HMCL 整合包安装：" & InstanceName & " "
        If LoaderTaskbar.Any(Function(l) l.Name = LoaderName) Then
            Hint(ModLang.L("该整合包正在安装中！"), HintType.Red)
            Throw New OperationCanceledException
        End If
        '启动
        Dim Loader As New LoaderCombo(Of String)(LoaderName, Loaders) With {.OnStateChanged = AddressOf McInstallState}
        Loader.Start(VersionFolder)
        LoaderTaskbarAdd(Loader)
        FrmMain.BtnExtraDownload.ShowRefresh()
        Return Loader
    End Function

    'MMC
    Private Function InstallPackMMC(FileAddress As String, Archive As ZipArchive, ArchiveBaseFolder As String) As LoaderCombo(Of String)
        '读取 Json 文件
        Dim PackJson As JObject, PackInstance As String
        Try
            PackJson = Archive.GetEntry(ArchiveBaseFolder & "mmc-pack.json").Open().ReadString(Encoding.UTF8).DeserializeJson()
            PackInstance = Archive.GetEntry(ArchiveBaseFolder & "instance.cfg").Open().ReadString(Encoding.UTF8)
        Catch ex As Exception
            Throw New Exception("MMC 整合包安装信息存在问题", ex)
        End Try
        '获取版本名
        Dim InstanceName As String = If(PackInstance.RegexSeek("(?<=\nname\=)[^\n]+"), "")
        Dim Validate As New ValidateFolderName(McFolderSelected & "versions")
        If Validate.Validate(InstanceName) <> "" Then InstanceName = ""
        If InstanceName = "" Then InstanceName = MyMsgBoxInput(ModLang.L("输入版本名称"), "", "", New Collection(Of Validate) From {Validate})
        If String.IsNullOrEmpty(InstanceName) Then Throw New OperationCanceledException
        Dim VersionFolder As String = Path.Combine(McFolderSelected, "versions", InstanceName) & "\"
        '解压
        Dim InstallTemp As String = RequestTaskTempFolder()
        Dim SetupFile As String = VersionFolder & "TCL\Setup.ini"
        Dim InstallLoaders As New List(Of LoaderBase)
        InstallLoaders.Add(New LoaderTask(Of String, Integer)("解压整合包文件",
        Sub(Task As LoaderTask(Of String, Integer))
            ExtractModpackFiles(InstallTemp, FileAddress, Task.CreateCancellationToken(), Task.CreateSyncProgressProvider(ToPercentage:=0.55))
            'Prism 优先使用 minecraft/ 而非 .minecraft/
            Dim MmcGameRoot As String
            If DirectoryUtils.Exists(InstallTemp & ArchiveBaseFolder & ".minecraft") AndAlso Not DirectoryUtils.Exists(InstallTemp & ArchiveBaseFolder & "minecraft") Then
                MmcGameRoot = InstallTemp & ArchiveBaseFolder & ".minecraft"
            Else
                MmcGameRoot = InstallTemp & ArchiveBaseFolder & "minecraft"
            End If
            CopyOverrideDirectory(MmcGameRoot, VersionFolder, Task)
            '读取 MMC 设置文件
            Try
                Dim MmcSetupFile As String = InstallTemp & ArchiveBaseFolder & "instance.cfg"
                If FileUtils.Exists(MmcSetupFile) Then
                    '将其中的等号替换为冒号，以符合 ini 文件格式
                    Dim Lines As New List(Of String)
                    For Each Line In FileUtils.ReadAsLines(MmcSetupFile, True)
                        If Not Line.Contains("=") Then Continue For
                        Lines.Add(Line.BeforeFirst("=") & ":" & Line.AfterFirst("="))
                    Next
                    FileUtils.Write(MmcSetupFile, Lines.Join(vbCrLf))
                    '读取文件
                    If ReadIni(MmcSetupFile, "OverrideCommands", False) Then
                        Dim PreLaunchCommand As String = ReadIni(MmcSetupFile, "PreLaunchCommand")
                        If PreLaunchCommand <> "" Then
                            PreLaunchCommand = PreLaunchCommand.Replace("\""", """").
                                Replace("$INST_JAVA", "{java}java.exe").
                                Replace("$INST_MC_DIR\", "{minecraft}").Replace("$INST_MC_DIR", "{minecraft}").
                                Replace("$INST_DIR\", "{verpath}").Replace("$INST_DIR", "{verpath}").
                                Replace("$INST_ID", "{name}").Replace("$INST_NAME", "{name}")
                            WriteIni(SetupFile, "VersionAdvanceRun", PreLaunchCommand)
                            Logger.Info($"迁移 MultiMC 版本独立设置：启动前执行命令：{PreLaunchCommand}")
                        End If
                    End If
                    If ReadIni(MmcSetupFile, "JoinServerOnLaunch", False) Then
                        Dim ServerAddress As String = ReadIni(MmcSetupFile, "JoinServerOnLaunchAddress").Replace("\""", """")
                        WriteIni(SetupFile, "VersionServerEnter", ServerAddress)
                        Logger.Info($"迁移 MultiMC 版本独立设置：自动进入服务器：{ServerAddress}")
                    End If
                    Dim Logo As String = ReadIni(MmcSetupFile, "iconKey", "")
                    If Logo <> "" AndAlso FileUtils.Exists($"{InstallTemp}{ArchiveBaseFolder}{Logo}.png") Then
                        WriteIni(SetupFile, "LogoCustom", True)
                        WriteIni(SetupFile, "Logo", "TCL\Logo.png")
                        FileUtils.Copy($"{InstallTemp}{ArchiveBaseFolder}{Logo}.png", VersionFolder & "TCL\Logo.png")
                        Logger.Info($"迁移 MultiMC 版本独立设置：版本图标（{Logo}.png）")
                    End If
                    'JVM 参数
                    Dim JvmArgs As String = ReadIni(MmcSetupFile, "JvmArgs", "")
                    If JvmArgs <> "" Then
                        If ReadIni(MmcSetupFile, "OverrideJavaArgs", False) Then
                            WriteIni(SetupFile, "VersionAdvanceJvm", JvmArgs)
                            Logger.Info($"迁移 MultiMC 版本独立设置：JVM 参数（覆盖）：{JvmArgs}")
                        Else
                            JvmArgs += " " & Settings.Get(Of String)("LaunchAdvanceJvm")
                            WriteIni(SetupFile, "VersionAdvanceJvm", JvmArgs)
                            Logger.Info($"迁移 MultiMC 版本独立设置：JVM 参数（追加）：{JvmArgs}")
                        End If
                    End If
                End If
            Catch ex As Exception
                Logger.Warn(ex, $"读取 MMC 配置文件失败（{InstallTemp}{ArchiveBaseFolder}instance.cfg）")
            End Try
        End Sub) With {.ProgressWeight = FileUtils.GetInfo(FileAddress).Length / 1024 / 1024 / 6, .Block = False}) '每 6M 需要 1s
        '构造版本安装请求
        If PackJson("components") Is Nothing Then Throw New Exception("该 MMC 整合包未提供游戏版本信息，无法安装！")
        '与拖入弹窗共用 ParsePackInfo
        Dim PackInfo As New McModpackInfo
        ParsePackInfo(Archive, PackInfo)
        If String.IsNullOrWhiteSpace(PackInfo.MinecraftVersion) Then Throw New Exception("该 MMC 整合包未提供游戏版本信息，无法安装！")
        If PackInfo.UnsupportedLoaders.Any() Then
            Hint(ModLang.L("该整合包使用了 TCL 不支持的加载器：") & PackInfo.UnsupportedLoaders.Join("、"), HintType.Red)
            Throw New OperationCanceledException
        End If
        Logger.Info($"整合包：Minecraft {PackInfo.MinecraftVersion}，{If(PackInfo.LoaderText = "", "无加载器", PackInfo.LoaderText)}")
        '构造加载器
        Dim Target As New McComponents(PackInfo.MinecraftVersion, PackInfo.LoaderVersions)
        Dim MergeLoaders = McInstallLoadersGet(New McInstallRequest With {
            .NewInstanceName = InstanceName, .InstanceFolder = VersionFolder,
            .Components = Target})
        '构造总加载器
        Dim Loaders As New List(Of LoaderBase)
        Loaders.Add(New LoaderCombo(Of String)("整合包安装", InstallLoaders) With {.Show = False, .Block = False, .ProgressWeight = InstallLoaders.Sum(Function(l) l.ProgressWeight)})
        Loaders.Add(New LoaderCombo(Of String)("游戏安装", MergeLoaders) With {.Show = False, .ProgressWeight = MergeLoaders.Sum(Function(l) l.ProgressWeight)})

        '重复任务检查
        Dim LoaderName As String = "MMC 整合包安装：" & InstanceName & " "
        If LoaderTaskbar.Any(Function(l) l.Name = LoaderName) Then
            Hint(ModLang.L("该整合包正在安装中！"), HintType.Red)
            Throw New OperationCanceledException
        End If

        '启动
        Dim Loader As New LoaderCombo(Of String)(LoaderName, Loaders) With {.OnStateChanged = AddressOf McInstallState}
        Loader.Start(VersionFolder)
        LoaderTaskbarAdd(Loader)
        FrmMain.BtnExtraDownload.ShowRefresh()
        Return Loader
    End Function

    'MCBBS
    Private Function InstallPackMCBBS(FileAddress As String, Archive As ZipArchive, ArchiveBaseFolder As String,
                                      Optional InstanceName As String = Nothing) As LoaderCombo(Of String)
        '读取 Json 文件
        Dim Json As JObject
        Try
            Dim Entry = If(Archive.GetEntry(ArchiveBaseFolder & "mcbbs.packmeta"), Archive.GetEntry(ArchiveBaseFolder & "manifest.json"))
            Json = Entry.Open().ReadString(Encoding.UTF8).DeserializeJson()
        Catch ex As Exception
            Throw New Exception("MCBBS 整合包安装信息存在问题", ex)
        End Try
        '获取版本名
        If InstanceName Is Nothing Then
            InstanceName = If(Json("name"), "")
            Dim Validate As New ValidateFolderName(McFolderSelected & "versions")
            If Validate.Validate(InstanceName) <> "" Then InstanceName = ""
            If InstanceName = "" Then InstanceName = MyMsgBoxInput(ModLang.L("输入版本名称"), "", "", New Collection(Of Validate) From {Validate})
            If String.IsNullOrEmpty(InstanceName) Then Throw New OperationCanceledException
        End If
        Dim VersionFolder As String = Path.Combine(McFolderSelected, "versions", InstanceName) & "\"
        '解压
        Dim InstallTemp As String = RequestTaskTempFolder()
        Dim SetupFile As String = VersionFolder & "TCL\Setup.ini"
        Dim InstallLoaders As New List(Of LoaderBase)
        InstallLoaders.Add(New LoaderTask(Of String, Integer)("解压整合包文件",
        Sub(Task As LoaderTask(Of String, Integer))
            ExtractModpackFiles(InstallTemp, FileAddress, Task.CreateCancellationToken(), Task.CreateSyncProgressProvider(ToPercentage:=0.6))
            CopyOverrideDirectory(InstallTemp & ArchiveBaseFolder & "overrides", VersionFolder, Task)
            'JVM 参数
            If Json("launchInfo") IsNot Nothing Then
                Dim LaunchInfo As JObject = Json("launchInfo")
                If LaunchInfo.ContainsKey("javaArgument") Then WriteIni(SetupFile, "VersionAdvanceJvm", String.Join(" ", LaunchInfo("javaArgument")))
                If LaunchInfo.ContainsKey("launchArgument") Then WriteIni(SetupFile, "VersionAdvanceGame", String.Join(" ", LaunchInfo("launchArgument")))
            End If
        End Sub) With {.ProgressWeight = FileUtils.GetInfo(FileAddress).Length / 1024 / 1024 / 6, .Block = False}) '每 6M 需要 1s
        '构造加载器
        If Json("addons") Is Nothing Then Throw New Exception("该 MCBBS 整合包未提供游戏版本附加信息，无法安装！")
        Dim Addons As New Dictionary(Of String, String)
        For Each Entry In Json("addons")
            Addons.Add(Entry("id"), Entry("version"))
        Next
        If Not Addons.ContainsKey("game") Then Throw New Exception("该 MCBBS 整合包未提供游戏版本信息，无法安装！")
        '游戏版本与加载器走 ParsePackInfo（与拖入弹窗一致），这里只用 Addons 处理 OptiFine
        Dim PackInfo As New McModpackInfo
        ParsePackInfo(Archive, PackInfo)
        If String.IsNullOrWhiteSpace(PackInfo.MinecraftVersion) Then Throw New Exception("该 MCBBS 整合包未提供游戏版本信息，无法安装！")
        If PackInfo.UnsupportedLoaders.Any() Then
            Hint(ModLang.L("该整合包使用了 TCL 不支持的加载器：") & PackInfo.UnsupportedLoaders.Join("、"), HintType.Red)
            Throw New OperationCanceledException
        End If
        Logger.Info($"整合包：Minecraft {PackInfo.MinecraftVersion}，{If(PackInfo.LoaderText = "", "无加载器", PackInfo.LoaderText)}")
        Dim OptiFineEntry As JObject = Nothing
        If Addons.ContainsKey("optifine") Then
            Dim MinecraftVersion = Addons("game")
            If MinecraftVersion.EndsWithF(".0") Then MinecraftVersion = MinecraftVersion.Substring(0, MinecraftVersion.Length - 2)
            Dim OptiFineFileSuffix = "_HD_" & Addons("optifine").AfterLast("HD_") & ".jar"
            '从镜像源拉取 OptiFine 列表
            If DlOptiFineListBmclapiLoader.State <> LoadState.Finished Then DlOptiFineListBmclapiLoader.WaitForExit()
            Dim Entry = DlOptiFineListBmclapiLoader.Output.Value.FirstOrDefault(
                Function(v) v.Inherit = MinecraftVersion AndAlso v.FileName.EndsWithF(OptiFineFileSuffix))
            If Entry Is Nothing Then Throw New Exception($"OptiFine 列表中没有整合包所需的 OptiFine {Addons("optifine")}（Minecraft {Addons("game")}）")
            OptiFineEntry = JObject.FromObject(Entry)
        End If
        Dim Target As New McComponents(PackInfo.MinecraftVersion, PackInfo.LoaderVersions, OptiFineEntry)
        Dim MergeLoaders = McInstallLoadersGet(New McInstallRequest With {
            .NewInstanceName = InstanceName, .InstanceFolder = VersionFolder,
            .Components = Target})
        '构造总加载器
        Dim Loaders As New List(Of LoaderBase)
        Loaders.Add(New LoaderCombo(Of String)("整合包安装", InstallLoaders) With {.Show = False, .Block = False, .ProgressWeight = InstallLoaders.Sum(Function(l) l.ProgressWeight)})
        Loaders.Add(New LoaderCombo(Of String)("游戏安装", MergeLoaders) With {.Show = False, .ProgressWeight = MergeLoaders.Sum(Function(l) l.ProgressWeight)})

        '重复任务检查
        Dim LoaderName As String = "MCBBS 整合包安装：" & InstanceName & " "
        If LoaderTaskbar.Any(Function(l) l.Name = LoaderName) Then
            Hint(ModLang.L("该整合包正在安装中！"), HintType.Red)
            Throw New OperationCanceledException
        End If

        '启动
        Dim Loader As New LoaderCombo(Of String)(LoaderName, Loaders) With {.OnStateChanged = AddressOf McInstallState}
        'If Archive IsNot Nothing Then Archive.Dispose '解除占用，以免在加载器中触发 “正由另一进程使用，因此该进程无法访问此文件”
        Loader.Start(VersionFolder)
        LoaderTaskbarAdd(Loader)
        FrmMain.BtnExtraDownload.ShowRefresh()
        Return Loader
    End Function

    '带启动器的压缩包
    Private Function InstallPackLauncherPack(FileAddress As String, Archive As ZipArchive, ArchiveBaseFolder As String) As LoaderCombo(Of String)
        '获取解压路径
        MyMsgBox(ModLang.L("接下来请选择一个空文件夹，它会被安装到这个文件夹里。"), ModLang.L("安装"), ModLang.L("继续"), ForceWait:=True)
        Dim TargetFolder As String = Dialogs.SelectFolder("选择安装目标（必须是一个空文件夹）", False).FirstOrDefault
        If TargetFolder Is Nothing Then Throw New OperationCanceledException
        If Not DirectoryUtils.IsEmpty(TargetFolder) Then Hint(ModLang.L("请选择一个空文件夹作为安装目标！"), HintType.Red) : Throw New OperationCanceledException
        '解压
        Dim Loader As New LoaderCombo(Of String)("解压压缩包", {
            New LoaderTask(Of String, Integer)("解压压缩包",
            Sub(Task As LoaderTask(Of String, Integer))
                ExtractModpackFiles(TargetFolder, FileAddress, Task.CreateCancellationToken(), Task.CreateSyncProgressProvider(ToPercentage:=0.9))
                OpenExplorer(TargetFolder)
                Thread.Sleep(400) '避免文件争用
                '查找解压后的 exe 文件
                Dim Launcher As String = Nothing
                For Each ExeFile In DirectoryUtils.EnumerateFiles(TargetFolder, searchPattern:="*.exe")
                    Dim Info = FileVersionInfo.GetVersionInfo(ExeFile)
                    Logger.Info($"文件 {ExeFile} 的产品名标识为 {Info.ProductName}")
                    If Info.ProductName = "TCL" Then
                        Launcher = ExeFile
                        Logger.Info($"发现整合包附带的 TCL 启动器：{ExeFile}")
                    ElseIf (Info.ProductName.ContainsIgnoreCase("Launcher") OrElse Info.ProductName.Contains("启动")) AndAlso
                        Not Info.ProductName = "TCL Admin Manager" Then
                        If Launcher Is Nothing Then
                            Launcher = ExeFile
                            Logger.Info($"发现整合包附带的疑似第三方启动器：{ExeFile}")
                        End If
                    End If
                Next
                Task.Progress = 0.95
                '尝试使用附带的启动器打开
                If Launcher IsNot Nothing Then
                    Logger.Info($"找到压缩包中附带的启动器：{Launcher}")
                    If MyMsgBox(ModLang.L("整合包里似乎自带了启动器，是否换用它继续安装？") & vbCrLf & ModLang.L("即将打开：") & Launcher, ModLang.L("换用整合包启动器？"), ModLang.L("换用"), ModLang.L("不换用")) = 1 Then
                        StartProcess(Launcher, "--wait") '要求等待已有的 TCL 退出
                        Logger.Info("为换用整合包中的启动器启动，强制结束程序")
                        FrmMain.EndProgram(False)
                        Return
                    End If
                Else
                    Logger.Info("未找到压缩包中附带的启动器")
                End If
                '加入文件夹列表
                Dim InstanceName As String = PathUtils.GetLastPart(TargetFolder)
                DirectoryUtils.Create(TargetFolder & ".minecraft\")
                PageSelectLeft.AddFolder(
                    TargetFolder & ".minecraft\" & ArchiveBaseFolder.Replace("/", "\").TrimStart("\"), '格式例如：包裹文件夹\.minecraft\（最短为空字符串）
                    InstanceName, False)
                '调用 modpack 文件进行安装
                Dim ModpackFile = DirectoryUtils.EnumerateFiles(TargetFolder, True, "modpack.*").First
                Logger.Info($"调用 modpack 文件继续安装：{ModpackFile}")
                ModpackInstall(ModpackFile)
            End Sub)
        })
        Loader.Start(TargetFolder)
        LoaderTaskbarAdd(Loader)
        FrmMain.BtnExtraDownload.ShowRefresh()
        FrmMain.BtnExtraDownload.Ribble()
        Return Loader
    End Function

    '普通压缩包
    Private Function InstallPackCompress(FileAddress As String, Archive As ZipArchive) As LoaderCombo(Of String)
        '尝试定位 .minecraft 文件夹：寻找形如 “/versions/XXX/XXX.json” 的路径
        Dim Match As Match = Nothing
        Dim Regex As New Regex("^.*\/(?=versions\/(?<ver>[^\/]+)\/(\k<ver>)\.json$)", RegexOptions.IgnoreCase)
        For Each Entry In Archive.Entries
            Dim EntryMatch = Regex.Match("/" & Entry.FullName)
            If EntryMatch.Success Then
                Match = EntryMatch
                Exit For
            End If
        Next
        If Match Is Nothing Then Throw New Exception("文件结构不匹配，这可能不是 Minecraft 客户端压缩包？") '没有匹配
        Dim ArchiveBaseFolder As String = Match.Value.Replace("/", "\").TrimStart("\") '格式例如：包裹文件夹\.minecraft\（最短为空字符串）
        Dim DetectedVersion As String = Match.Groups(1).Value
        Logger.Info($"检测到压缩包的 .minecraft 根目录：{ArchiveBaseFolder}，命中的版本名：{DetectedVersion}")
        '普通（懒人）包本身就是一个独立的 .minecraft 根目录，注册为一个独立 Minecraft 文件夹最安全，
        '避免把内容合并进当前活跃的 MC 文件夹造成版本互相覆盖（对齐主流启动器的处理）。
        MyMsgBox(ModLang.L("接下来请选择一个空文件夹，它会被安装到这个文件夹里，并注册为一个独立的 Minecraft 文件夹。"), ModLang.L("安装"), ModLang.L("继续"), ForceWait:=True)
        Dim TargetFolder As String = Dialogs.SelectFolder("选择安装目标（必须是一个空文件夹）", False).FirstOrDefault
        If TargetFolder Is Nothing Then Throw New OperationCanceledException
        If TargetFolder.Contains("!") OrElse TargetFolder.Contains(";") Then Hint(ModLang.L("Minecraft 文件夹路径中不能含有感叹号或分号！"), HintType.Red) : Throw New OperationCanceledException
        If Not DirectoryUtils.IsEmpty(TargetFolder) Then Hint(ModLang.L("请选择一个空文件夹作为安装目标！"), HintType.Red) : Throw New OperationCanceledException
        Dim Loader As New LoaderCombo(Of String)("解压压缩包", {
            New LoaderTask(Of String, Integer)("解压压缩包",
            Sub(Task As LoaderTask(Of String, Integer))
                ExtractModpackFiles(TargetFolder, FileAddress, Task.CreateCancellationToken(), Task.CreateSyncProgressProvider(ToPercentage:=0.95))
                '注册为独立文件夹并强刷其版本列表，使新装版本可被发现与启动
                PageSelectLeft.AddFolder(TargetFolder & ArchiveBaseFolder, PathUtils.GetLastPart(TargetFolder), False)
                LoaderFolderRun(McInstanceListLoader, TargetFolder & ArchiveBaseFolder, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
                Thread.Sleep(400) '避免文件争用
                RunInUi(Sub()
                            FrmMain.PageChange(FormMain.PageType.InstanceSelect)
                            Hint(ModLang.L("懒人包已安装为独立 Minecraft 文件夹，版本列表中已出现该版本。"), HintType.Green)
                        End Sub)
            End Sub)
        }) With {.OnStateChanged = AddressOf McInstallState}
        Loader.Start(TargetFolder)
        LoaderTaskbarAdd(Loader)
        FrmMain.BtnExtraDownload.ShowRefresh()
        FrmMain.BtnExtraDownload.Ribble()
        Return Loader
    End Function

#End Region

End Module
