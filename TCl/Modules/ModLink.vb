''' <summary>
''' 联机（陶瓦式虚拟局域网）的内置配置与工具。
''' <para/>原理与陶瓦联机一致：用 EasyTier 把房间里的电脑组进同一个虚拟局域网，
''' 房主把 Minecraft 的局域网端口暴露在虚拟网内，加入者通过本机端口转发连进去。
''' <para/>本构建不连接 TCL 官方配置服务器，因此在这里内置一份默认配置；
''' 若 <see cref="ServerConfig"/> 中存在 Link 节点（官方版联网配置），则以服务器下发的值覆盖内置值。
''' </summary>
Public Module ModLink

#Region "常量"

    ''' <summary>
    ''' 内置的 EasyTier 版本号，用于判断本地联机模块是否需要更新。
    ''' </summary>
    Public Const EasyTierVersion As Integer = 264
    ''' <summary>
    ''' 内置的 EasyTier 版本标签。
    ''' </summary>
    Public Const EasyTierTag As String = "v2.6.4"

    ''' <summary>
    ''' 联机模块主程序的文件名。
    ''' </summary>
    Public Const ModuleCoreName As String = "联机模块.exe"
    ''' <summary>
    ''' 联机模块 CLI 的文件名。
    ''' </summary>
    Public Const ModuleCliName As String = "联机模块 CLI.exe"
    ''' <summary>
    ''' 联机模块压缩包的文件名。
    ''' </summary>
    Public Const ModuleZipName As String = "EasyTier.zip"

#End Region

#Region "配置"

    Private ReadOnly ConfigLock As New Object()

    ''' <summary>
    ''' 联机模块所在的文件夹，以 \ 结尾。
    ''' </summary>
    Public ReadOnly Property ModuleFolder As String
        Get
            Return Paths.AppDataThenName & "EasyTier\"
        End Get
    End Property

    ''' <summary>
    ''' 当前的联机配置：内置默认值，被服务器下发的 Link 节点覆盖。
    ''' <para/>每次都重新构建，避免在服务器配置到达之前就把结果缓存下来。
    ''' </summary>
    Public ReadOnly Property Config As JObject
        Get
            SyncLock ConfigLock
                Dim Result As JObject = BuildDefaultConfig()
                Try
                    Dim Remote As JObject = Nothing
                    If ServerConfig IsNot Nothing Then Remote = TryCast(ServerConfig("Link"), JObject)
                    If Remote IsNot Nothing Then
                        For Each Prop As JProperty In Remote.Properties()
                            Result(Prop.Name) = Prop.Value.DeepClone()
                        Next
                    End If
                Catch ex As Exception
                    Logger.Warn(ex, "联机：读取服务器配置失败，将使用内置配置")
                End Try
                Return Result
            End SyncLock
        End Get
    End Property

    ''' <summary>
    ''' 内置默认配置。
    ''' <para/>下载地址按顺序尝试：GitHub 直连 + 常用镜像，任一可用即可。
    ''' </summary>
    Private Function BuildDefaultConfig() As JObject
        Dim Urls As New JArray()
        For Each Prefix In {"https://gitee.com/easytier/EasyTier/releases/download/",
                            "https://github.com/EasyTier/EasyTier/releases/download/",
                            "https://ghfast.top/https://github.com/EasyTier/EasyTier/releases/download/",
                            "https://gh-proxy.com/https://github.com/EasyTier/EasyTier/releases/download/",
                            "https://ghproxy.net/https://github.com/EasyTier/EasyTier/releases/download/",
                            "https://cdn.crashmc.com/https://github.com/EasyTier/EasyTier/releases/download/"}
            Urls.Add(Prefix & EasyTierTag & "/easytier-windows-{arch}-" & EasyTierTag & ".zip")
        Next
        ' EasyTier 的公共参数；房间相关参数由启动器按房间动态追加
        Return New JObject From {
            {"EasyTierVersion", EasyTierVersion},
            {"Downloads", Urls},
            {"Argument", "--mtu 1380 --console-log-level info"},
            {"DiscoverPeerId", 0},
            {"DiscoverPeer", "https://etnode.zkitefly.eu.org/node1"},
            {"PeersBlackList", New JArray()},
            {"RandomPeer", 0},
            {"Peers", New JArray(
                "https://etnode.zkitefly.eu.org/node1",
                "tcp://225284.xyz:11010",
                "tcp://et1.fuis.top:11010")},
            {"MandatoryPeers", New JArray()}
        }
    End Function

    ''' <summary>
    ''' 读取一个字符串配置项，缺失时返回默认值。
    ''' </summary>
    Public Function GetString(Name As String, DefaultValue As String) As String
        Try
            Dim Token = Config(Name)
            If Token Is Nothing OrElse Token.Type = JTokenType.Null Then Return DefaultValue
            Return Token.ToString()
        Catch ex As Exception
            Logger.Warn(ex, $"联机：读取配置 {Name} 失败")
            Return DefaultValue
        End Try
    End Function

    ''' <summary>
    ''' 读取一个整数配置项，缺失时返回默认值。
    ''' </summary>
    Public Function GetInteger(Name As String, DefaultValue As Integer) As Integer
        Try
            Dim Token = Config(Name)
            If Token Is Nothing OrElse Token.Type = JTokenType.Null Then Return DefaultValue
            Return Token.ToObject(Of Integer)()
        Catch ex As Exception
            Logger.Warn(ex, $"联机：读取配置 {Name} 失败")
            Return DefaultValue
        End Try
    End Function

    ''' <summary>
    ''' 用户在联机设置中选择的优先节点；返回空字符串表示自动选择。
    ''' </summary>
    Public Function GetPreferredPeer() As String
        Try
            Select Case Settings.Get(Of Integer)("LinkPeer")
                Case 1
                    Return "https://etnode.zkitefly.eu.org/node1"
                Case 2
                    Return "tcp://225284.xyz:11010"
                Case 3
                    Return "tcp://et1.fuis.top:11010"
                Case Else
                    Return ""
            End Select
        Catch ex As Exception
            Logger.Warn(ex, "联机：读取所选节点失败")
            Return ""
        End Try
    End Function

    ''' <summary>
    ''' 读取一个字符串数组配置项，缺失时返回空列表。
    ''' </summary>
    Public Function GetStrings(Name As String) As List(Of String)
        Try
            Dim Token = Config(Name)
            If Token Is Nothing OrElse Token.Type <> JTokenType.Array Then Return New List(Of String)
            Return Token.Select(Function(t) t.ToString()).ToList()
        Catch ex As Exception
            Logger.Warn(ex, $"联机：读取配置 {Name} 失败")
            Return New List(Of String)
        End Try
    End Function

    Private ReadOnly NodePool As List(Of String) = New List(Of String) From {
        "https://gh-proxy.com/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://gh.monlor.com/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://gh-proxy.org/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://github.cnxiaobai.com/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://gh.padao.fun/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://30006000.xyz/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://gh.acmsz.top/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://ghfast.top/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://ghproxy.net/https://github.com/EasyTier/EasyTier/releases/download/",
        "https://gitee.com/easytier/EasyTier/releases/download/",
        "https://github.com/EasyTier/EasyTier/releases/download/"}

    ''' <summary>
    ''' 节点池前缀列表（末尾保留 Gitee / GitHub 直连兜底）。
    ''' <para/>前段取自 github.qqday.com 的实测速度榜（稳定性与速度较高者）。
    ''' </summary>
    Public Function GetNodePool() As List(Of String)
        Return NodePool
    End Function

    ''' <summary>
    ''' 拼接节点池前缀与易 Tier 下载片段，得到带 {arch} 占位符的下载 URL。
    ''' </summary>
    Public Function BuildDownloadUrl(Idx As Integer) As String
        Return NodePool(Idx) & EasyTierTag & "/easytier-windows-{arch}-" & EasyTierTag & ".zip"
    End Function

    ''' <summary>
    ''' 按当前「下载节点」设置生成易 Tier 下载源 URL 列表（保留 {arch} 占位符）。
    ''' <para/>指定模式：把用户选中的节点置顶，其余按节点池顺序兜底；
    ''' <para/>自动模式：首次下载时对候选节点并发测速并缓存最快节点，将其置顶，
    ''' 之后复用缓存；下载引擎仍会对全部源做多线程回退。
    ''' </summary>
    Public Function GetDownloadUrls() As List(Of String)
        Dim First As Integer
        Select Case Settings.Get(Of Integer)("LinkDownloadMode")
            Case 1 '指定节点
                First = Settings.Get(Of Integer)("LinkDownloadNode").Clamp(0, NodePool.Count - 1)
            Case Else '自动选择最快节点
                First = Settings.Get(Of Integer)("LinkBestNode")
                If First < 0 OrElse First >= NodePool.Count Then
                    Dim Tested As Integer = TestNodeSpeed()
                    If Tested >= 0 Then
                        First = Tested
                        Settings.Set("LinkBestNode", First)
                    Else
                        First = 0
                    End If
                End If
        End Select
        Return Enumerable.Range(0, NodePool.Count).
            OrderBy(Function(idx) If(idx = First, 0, 1)).
            Select(Function(idx) BuildDownloadUrl(idx)).
            ToList()
    End Function

    ''' <summary>
    ''' 并发测速节点池中的候选镜像，返回速度最快者的小写下标；全部失败返回 -1。
    ''' <para/>通过向各镜像请求 EasyTier x86_64 压缩包的 256KB 数据段并计时，以字节/秒衡量。
    ''' </summary>
    Public Function TestNodeSpeed() As Integer
        Dim ProbeCount As Integer = Math.Min(6, NodePool.Count)
        Dim Speeds As New System.Collections.Concurrent.ConcurrentDictionary(Of Integer, Long)
        Dim Tasks As New List(Of System.Threading.Tasks.Task)()
        For i = 0 To ProbeCount - 1
            Dim Probe As String = NodePool(i) & EasyTierTag & "/easytier-windows-x86_64-" & EasyTierTag & ".zip"
            Dim Idx As Integer = i '捕获循环变量，避免 lambda 共享
            Tasks.Add(System.Threading.Tasks.Task.Run(Sub()
                Try
                    Dim Speed As Long = MeasureDownloadSpeed(Probe)
                    If Speed > 0 Then Speeds(Idx) = Speed
                Catch ex As Exception
                    Logger.Warn(ex, $"联机：测速节点 {Idx} 失败")
                End Try
            End Sub))
        Next
        System.Threading.Tasks.Task.WaitAll(Tasks.ToArray())
        If Speeds.Count = 0 Then
            Logger.Info("联机：节点测速失败（全部不可用），沿用默认顺序")
            Return -1
        End If
        Dim Best As Integer = -1
        Dim BestSpeed As Long = -1
        For Each kv In Speeds
            If kv.Value > BestSpeed Then
                BestSpeed = kv.Value
                Best = kv.Key
            End If
        Next
        Dim KbPerSec As Long = BestSpeed \ 1024
        Logger.Info($"联机：节点测速完成，最快节点下标 {Best}（约 {KbPerSec} KB/s）")
        Return Best
    End Function

    ''' <summary>
    ''' 用 Range 请求测量单个 URL 的下载速度（字节/秒）；失败或不可用返回 0。
    ''' </summary>
    Private Function MeasureDownloadSpeed(Url As String) As Long
        Try
            Dim Request As System.Net.HttpWebRequest = System.Net.HttpWebRequest.Create(Url)
            Request.Method = "GET"
            Request.Headers.Add("Range", "bytes=0-262143")
            Request.UserAgent = "Mozilla/5.0 (TCL)"
            Request.Timeout = 8000
            Request.ReadWriteTimeout = 8000
            Dim Sw As New System.Diagnostics.Stopwatch()
            Sw.Start()
            Dim Read As Integer = 0
            Using Response As System.Net.HttpWebResponse = Request.GetResponse()
                Using Stream As System.IO.Stream = Response.GetResponseStream()
                    Dim Buffer(65535) As Byte
                    Do While Read < 262144
                        Dim N As Integer = Stream.Read(Buffer, 0, Buffer.Length)
                        If N <= 0 Then Exit Do
                        Read += N
                    Loop
                End Using
            End Using
            Sw.Stop()
            If Sw.ElapsedMilliseconds <= 0 Then Return 0
            Return CLng(Read) * 1000 \ Sw.ElapsedMilliseconds
        Catch
            Return 0
        End Try
    End Function

#End Region

#Region "平台"

    ''' <summary>
    ''' 获取用于拼接下载地址的 CPU 架构。
    ''' <para/>必须按操作系统架构判断：AnyCPU 程序集的 ProcessorArchitecture 是 MSIL，
    ''' 直接用它判断会错下成 arm64 的包。
    ''' </summary>
    Public Function GetArchitecture() As String
        Dim Raw As String = Environment.GetEnvironmentVariable("PROCESSOR_ARCHITECTURE")
        If String.IsNullOrWhiteSpace(Raw) Then Raw = Environment.GetEnvironmentVariable("PROCESSOR_ARCHITEW6432")
        Select Case If(Raw, "").Trim().ToUpperInvariant()
            Case "ARM64" : Return "arm64"
            Case "AMD64" : Return "x86_64"
            Case "X86"
                ' 32 位进程跑在 64 位系统上时，ARCHITEW6432 才是真实架构
                If Environment.Is64BitOperatingSystem Then Return "x86_64"
                Return "i686"
            Case Else : Return "x86_64"
        End Select
    End Function

#End Region

#Region "模块安装"

    ''' <summary>
    ''' 从下载到的压缩包里安装联机模块（EasyTier）。
    ''' <para/>压缩包内可能带一层子目录，按 easytier-core.exe 的实际位置定位；
    ''' 只安装主程序、CLI 与依赖（wintun.dll 等），跳过用不到的 GUI。
    ''' </summary>
    Public Sub InstallModuleFromZip(ZipPath As String, TargetFolder As String)
        Dim ExtractFolder As String = Path.Combine(Path.GetTempPath(), "TCL_EasyTier_" & Guid.NewGuid().ToString("N"))
        Try
            FileUtils.ExtractToDirectory(ZipPath, ExtractFolder)
            Dim CoreFile As String = DirectoryUtils.EnumerateFiles(ExtractFolder, True).
                FirstOrDefault(Function(f) Path.GetFileName(f).Equals("easytier-core.exe", StringComparison.OrdinalIgnoreCase))
            If CoreFile Is Nothing Then
                Throw New Exception("联机模块压缩包里找不到 easytier-core.exe，可能下载到了错误的文件")
            End If
            Dim CoreFolder As String = Path.GetDirectoryName(CoreFile)
            DirectoryUtils.Create(TargetFolder)
            For Each SourceFile In Directory.GetFiles(CoreFolder)
                Dim FileName As String = Path.GetFileName(SourceFile)
                Dim LowerName As String = FileName.ToLowerInvariant()
                '跳过用不到的 GUI 与 Web 面板（两者合计约 44MB）
                If LowerName.StartsWith("easytier-web") OrElse LowerName.StartsWith("easytier-gui") Then Continue For
                Select Case LowerName
                    Case "easytier-core.exe"
                        FileName = ModuleCoreName
                    Case "easytier-cli.exe"
                        FileName = ModuleCliName
                End Select
                FileUtils.Copy(SourceFile, TargetFolder & FileName)
            Next
        Finally
            Try
                DirectoryUtils.Delete(ExtractFolder)
            Catch ex As Exception
                Logger.Warn(ex, "联机：清理解压临时目录失败")
            End Try
        End Try
    End Sub

    ''' <summary>
    ''' 判断联机模块是否已经安装完整。
    ''' </summary>
    Public Function IsModuleInstalled(Folder As String) As Boolean
        Return FileUtils.Exists(Folder & ModuleCoreName) AndAlso FileUtils.Exists(Folder & ModuleCliName)
    End Function

#End Region

#Region "CLI"

    ''' <summary>
    ''' 调用联机模块 CLI 并返回标准输出。
    ''' <para/>超时会结束进程并抛出异常，调用方负责捕获。
    ''' </summary>
    Public Function RunCli(Arguments As String, TimeoutMs As Integer) As String
        Dim Info As New ProcessStartInfo With {
            .FileName = ModuleFolder & ModuleCliName,
            .Arguments = Arguments,
            .UseShellExecute = False,
            .CreateNoWindow = True,
            .RedirectStandardOutput = True,
            .RedirectStandardError = True,
            .StandardOutputEncoding = Encoding.UTF8,
            .StandardErrorEncoding = Encoding.UTF8
        }
        Dim Proc As Process = StartProcess(Info)
        Try
            Dim OutputTask = Proc.StandardOutput.ReadToEndAsync()
            Dim ErrorTask = Proc.StandardError.ReadToEndAsync()
            If Not Proc.WaitForExit(TimeoutMs) Then
                Try
                    Proc.Kill()
                Catch ex As Exception
                    Logger.Warn(ex, "终止超时的联机 CLI 进程失败")
                End Try
                Throw New TimeoutException($"联机模块 CLI 在 {TimeoutMs}ms 内没有响应")
            End If
            Dim Output As String = OutputTask.GetAwaiter().GetResult()
            Dim ErrorOutput As String = ErrorTask.GetAwaiter().GetResult()
            If String.IsNullOrWhiteSpace(Output) AndAlso Not String.IsNullOrWhiteSpace(ErrorOutput) Then
                Logger.Warn($"联机模块 CLI 错误输出：{ErrorOutput.Trim()}")
            End If
            Return Output
        Finally
            Proc.Dispose()
        End Try
    End Function

#End Region

End Module
