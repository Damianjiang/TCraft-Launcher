''' <summary>
''' 下载源（镜像 / 官方 / 社区资源站）的连通性测速。
''' 所有请求都通过 ModProxy.CreateHandler() 建立，因此测速本身也会走用户设置的代理。
''' 只测量「当前网络到这个源的连通质量」，不代表下载速度，也不影响已下载的内容。
''' </summary>
Public Module ModSourceTest

    ''' <summary>单个源的测速结果。</summary>
    Public Class SourceResult
        ''' <summary>内部标识（bmclapi / mojang / curseforge / modrinth），用于回填界面行与匹配下载源设置。</summary>
        Public Tag As String
        Public Name As String
        Public Url As String
        Public Success As Boolean
        Public LatencyMs As Integer
        Public Message As String
    End Class

    ''' <summary>内置源的元信息。</summary>
    Public Class SourceInfo
        Public Tag As String
        Public Name As String
        Public Url As String
        ''' <summary>界面上显示的默认说明。</summary>
        Public Note As String
        ''' <summary>该源是否对应「下载源」设置（BMCLAPI / Mojang 官方）。</summary>
        Public IsGameMirror As Boolean
    End Class

    Private Const TimeoutMs As Integer = 5000
    Private Const Samples As Integer = 3

    ''' <summary>一种探针结果（内部使用）。</summary>
    Private Structure ProbeOutcome
        Public Ok As Boolean
        Public Latency As Long
        Public Message As String
        Public Note As String
    End Structure

    ''' <summary>内置的 4 个源。每次返回新集合，调用方可以自行修改。</summary>
    Public Function GetSources() As List(Of SourceInfo)
        Return New List(Of SourceInfo) From {
            New SourceInfo With {
                .Tag = "bmclapi",
                .Name = "BMCLAPI",
                .Url = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json",
                .Note = ModLang.L("国内镜像，版本列表与文件下载"),
                .IsGameMirror = True},
            New SourceInfo With {
                .Tag = "mojang",
                .Name = ModLang.L("Mojang 官方"),
                .Url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
                .Note = ModLang.L("官方源，海外网络质量依赖线路"),
                .IsGameMirror = True},
            New SourceInfo With {
                .Tag = "curseforge",
                .Name = "CurseForge",
                .Url = "https://api.curseforge.com/v1/games",
                .Note = ModLang.L("社区资源，403 也算连通"),
                .IsGameMirror = False},
            New SourceInfo With {
                .Tag = "modrinth",
                .Name = "Modrinth",
                .Url = "https://api.modrinth.com/v2/tag/game_version",
                .Note = ModLang.L("社区资源，开放 API"),
                .IsGameMirror = False}
        }
    End Function

    ''' <summary>
    ''' 测试所有内置源，回调在后台线程逐个返回（按 GetSources 的顺序）。
    ''' 本方法立即返回，内部自行开线程；调用方需要用 RunInUi 回填界面。
    ''' </summary>
    Public Sub TestAll(OnResult As Action(Of SourceResult))
        If OnResult Is Nothing Then Return
        RunInNewThread(Sub()
                           For Each Src In GetSources()
                               Dim Item As SourceResult = Nothing
                               Try
                                   Item = TestOne(Src)
                               Catch ex As Exception
                                   Logger.Warn(ex, "测速时出现异常：" & Src.Url)
                                   Item = New SourceResult With {
                                       .Tag = Src.Tag, .Name = Src.Name, .Url = Src.Url,
                                       .Success = False, .LatencyMs = -1,
                                       .Message = ModLang.L("测速时出现异常")}
                               End Try
                               Try
                                   OnResult.Invoke(Item)
                               Catch ex As Exception
                                   Logger.Warn(ex, "回填测速结果失败：" & Src.Name)
                               End Try
                           Next
                       End Sub, "Source Test")
    End Sub

    ''' <summary>测一个源：采样 3 次取平均；全部失败时再重试 1 次取最好成绩。</summary>
    Private Function TestOne(Src As SourceInfo) As SourceResult
        Dim Item As New SourceResult With {
            .Tag = Src.Tag, .Name = Src.Name, .Url = Src.Url,
            .Success = False, .LatencyMs = -1, .Message = ""}
        Dim Latencies As New List(Of Long)
        Dim LastMessage As String = ""
        Dim Note As String = ""
        For i = 1 To Samples
            Dim One As ProbeOutcome = TryProbe(Src.Url)
            If One.Ok Then
                Latencies.Add(One.Latency)
                If One.Note.Length > 0 Then Note = One.Note
            Else
                LastMessage = One.Message
            End If
        Next
        If Latencies.Count = 0 Then
            '全部失败，重试一次取最好成绩
            Dim Retry As ProbeOutcome = TryProbe(Src.Url)
            If Retry.Ok Then
                Latencies.Add(Retry.Latency)
                If Retry.Note.Length > 0 Then Note = Retry.Note
            Else
                LastMessage = Retry.Message
            End If
        End If
        If Latencies.Count > 0 Then
            Item.Success = True
            Item.LatencyMs = CInt(Math.Round(Latencies.Average()))
            Item.Message = ModLang.L("平均延迟 ") & Item.LatencyMs & " ms" &
                If(Note.Length > 0, "  " & Note, "")
        Else
            Item.Success = False
            Item.LatencyMs = -1
            Item.Message = If(LastMessage.Length > 0, LastMessage, ModLang.L("无法连接"))
        End If
        Return Item
    End Function

    ''' <summary>
    ''' 发一次轻量 GET（只取响应头，不下载正文）。5 秒超时。
    ''' 401 / 403 视为「网络已连通」：CurseForge 在没有 API Key 时会返回 403，
    ''' 这里只关心能不能到达该服务器。
    ''' </summary>
    Private Function TryProbe(Url As String) As ProbeOutcome
        Dim Outcome As New ProbeOutcome With {.Ok = False, .Latency = -1, .Message = "", .Note = ""}
        Dim Client As HttpClient = Nothing
        Try
            Client = New HttpClient(ModProxy.CreateHandler(), True)
            Client.Timeout = TimeSpan.FromMilliseconds(TimeoutMs)
            Dim Watch As Stopwatch = Stopwatch.StartNew()
            Dim Response As HttpResponseMessage = Client.GetAsync(Url, HttpCompletionOption.ResponseHeadersRead).GetAwaiter().GetResult()
            Try
                Watch.Stop()
                Dim Code As Integer = CInt(Response.StatusCode)
                Outcome.Latency = Watch.ElapsedMilliseconds
                Outcome.Ok = Response.IsSuccessStatusCode OrElse Code = 401 OrElse Code = 403
                If Not Response.IsSuccessStatusCode Then
                    If Code = 403 Then
                        Outcome.Note = ModLang.L("（返回 403，无 API Key 属正常）")
                    Else
                        Outcome.Note = ModLang.L("（返回状态码 ") & Code & "）"
                    End If
                End If
            Finally
                Response.Dispose()
            End Try
        Catch ex As TaskCanceledException
            Outcome.Message = ModLang.L("请求超时（5 秒）")
        Catch ex As OperationCanceledException
            Outcome.Message = ModLang.L("请求超时（5 秒）")
        Catch ex As HttpRequestException
            Outcome.Message = ModLang.L("连接失败") & "：" & ex.Message
        Catch ex As Exception
            Outcome.Message = ex.Message
        Finally
            If Client IsNot Nothing Then Client.Dispose()
        End Try
        Return Outcome
    End Function

End Module
