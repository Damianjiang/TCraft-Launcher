''' <summary>
''' 设置 - 网络。集中放代理配置与下载源连通性测速。
''' 代理设置沿用 SystemHttpProxyType / SystemHttpProxy / SystemHttpProxyCustomUsername /
''' SystemHttpProxyCustomPassword 四个既有设置项（与「系统」页里那张网络卡片同源），
''' 通过 ModProxy 统一读写，保存后立即刷新全局 HttpProxyManager，不需要重启启动器。
''' </summary>
Public Class PageSetupNetwork

    ''' <summary>测速行的控件引用。</summary>
    Private Class SourceRow
        Public Dot As Border
        Public Latency As TextBlock
        Public Message As TextBlock
    End Class

    ''' <summary>Tag → 行控件。</summary>
    Private ReadOnly Rows As New Dictionary(Of String, SourceRow)

    ''' <summary>Tag → 最近一次测速结果（按 GetSources 的顺序回填，供汇总与「应用推荐源」使用）。</summary>
    Private ReadOnly Results As New Dictionary(Of String, ModSourceTest.SourceResult)

    Private Bound As Boolean = False
    Private Testing As Boolean = False
    Private Pending As Integer = 0

    '测速探测用的地址：BMCLAPI 的版本清单，体积小、国内可访问性通常最好
    Private Const ProbeUrl As String = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json"
    Private Const ProbeTimeoutMs As Integer = 5000

    Private Sub PageSetupNetwork_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        BindEvents()
        If Rows.Count = 0 Then BuildRows()
        Reload()
    End Sub

    ''' <summary>主控在切换页面时会调用，把当前设置重新刷到界面。</summary>
    Public Sub Reload()
        Try
            AniControlEnabled += 1
            Try
                Dim Mode As Integer = ModProxy.GetMode()
                If Mode < 0 OrElse Mode >= ComboProxyMode.Items.Count Then Mode = ModProxy.ModeSystem
                ComboProxyMode.SelectedIndex = Mode
            Finally
                AniControlEnabled -= 1
            End Try
            TextProxyHost.Text = ModProxy.GetHostText()
            Dim Port As Integer = ModProxy.GetPort()
            TextProxyPort.Text = If(Port > 0, Port.ToString(), "")
            TextProxyUser.Text = ModProxy.GetUserName()
            PassProxyPassword.Password = ModProxy.GetPassword()
            LabProxyTest.Text = ModProxy.Describe()
            UpdateModeState()
        Catch ex As Exception
            Logger.Error(ex, "刷新网络设置页失败")
        End Try
    End Sub

    ''' <summary>事件统一在这里挂，避免 XAML 里 Click= 的委托签名不匹配。</summary>
    Private Sub BindEvents()
        If Bound Then Return
        Bound = True
        AddHandler ComboProxyMode.SelectionChanged, AddressOf ComboProxyMode_SelectionChanged
        AddHandler BtnProxySave.Click, AddressOf BtnProxySave_Click
        AddHandler BtnProxyTest.Click, AddressOf BtnProxyTest_Click
        AddHandler BtnSourceTest.Click, AddressOf BtnSourceTest_Click
        AddHandler BtnApplySource.Click, AddressOf BtnApplySource_Click
    End Sub

#Region "代理"

    Private Sub ComboProxyMode_SelectionChanged(sender As Object, e As SelectionChangedEventArgs)
        UpdateModeState()
    End Sub

    ''' <summary>按模式启用 / 禁用地址输入框，并在需要时给出 SOCKS5 的真实支持程度提示。</summary>
    Private Sub UpdateModeState()
        Try
            Dim Mode As Integer = ComboProxyMode.SelectedIndex
            Dim IsCustom As Boolean = Mode = ModProxy.ModeHttp OrElse Mode = ModProxy.ModeSocks5
            TextProxyHost.IsEnabled = IsCustom
            TextProxyPort.IsEnabled = IsCustom
            TextProxyUser.IsEnabled = IsCustom
            PassProxyPassword.IsEnabled = IsCustom
            Select Case Mode
                Case ModProxy.ModeSocks5
                    LabProxyNote.Text = ModLang.L("当前 .NET Framework 版本对 SOCKS5 支持有限，建议使用 HTTP 代理或系统代理（可用 Clash / v2rayN 的 HTTP 端口）。")
                    LabProxyNote.Visibility = Visibility.Visible
                Case ModProxy.ModeHttp
                    LabProxyNote.Text = ModLang.L("请填写代理工具的 HTTP 端口，例如 Clash 默认的 7890。")
                    LabProxyNote.Visibility = Visibility.Visible
                Case Else
                    LabProxyNote.Text = ""
                    LabProxyNote.Visibility = Visibility.Collapsed
            End Select
        Catch ex As Exception
            Logger.Warn(ex, "刷新代理输入框状态失败")
        End Try
    End Sub

    ''' <summary>自定义模式下校验主机与端口；不通过时提示并返回 False。</summary>
    Private Function ValidateProxyInput() As Boolean
        Dim Mode As Integer = ComboProxyMode.SelectedIndex
        If Mode < 0 Then
            Hint(ModLang.L("请先选择代理模式"), HintType.Red)
            Return False
        End If
        If Mode <> ModProxy.ModeHttp AndAlso Mode <> ModProxy.ModeSocks5 Then Return True
        If TextProxyHost.Text.Trim().Length = 0 Then
            Hint(ModLang.L("请填写代理主机地址"), HintType.Red)
            Return False
        End If
        Dim Port As Integer = 0
        If Not Integer.TryParse(TextProxyPort.Text.Trim(), Port) OrElse Port < 1 OrElse Port > 65535 Then
            Hint(ModLang.L("端口必须是 1~65535 之间的整数"), HintType.Red)
            Return False
        End If
        Return True
    End Function

    Private Function GetPortInput() As Integer
        Dim Port As Integer = 0
        Integer.TryParse(TextProxyPort.Text.Trim(), Port)
        Return Port
    End Function

    ''' <summary>界面上的输入是否与已保存的设置不一致（用于提示「测试用的是已保存的设置」）。</summary>
    Private Function IsUiDirty() As Boolean
        If ComboProxyMode.SelectedIndex <> ModProxy.GetMode() Then Return True
        If TextProxyHost.Text.Trim() <> ModProxy.GetHostText() Then Return True
        If GetPortInput() <> ModProxy.GetPort() Then Return True
        If TextProxyUser.Text.Trim() <> ModProxy.GetUserName() Then Return True
        If PassProxyPassword.Password <> ModProxy.GetPassword() Then Return True
        Return False
    End Function

    Private Sub BtnProxySave_Click(sender As Object, e As MouseButtonEventArgs)
        If Not ValidateProxyInput() Then Return
        Try
            ModProxy.Save(ComboProxyMode.SelectedIndex, TextProxyHost.Text.Trim(), GetPortInput(),
                          TextProxyUser.Text.Trim(), PassProxyPassword.Password)
            LabProxyTest.Text = ModProxy.Describe()
            UpdateModeState()
            Hint(ModLang.L("已保存，之后的网络请求会立即使用该代理"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "保存代理设置失败")
            Hint(ModLang.L("保存失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>
    ''' 测试代理。使用 ModProxy.CreateHandler()，因此测的就是「已保存」的配置；
    ''' 界面上有未保存改动时会额外提醒。
    ''' </summary>
    Private Sub BtnProxyTest_Click(sender As Object, e As MouseButtonEventArgs)
        If Not ValidateProxyInput() Then Return
        Dim Dirty As Boolean = IsUiDirty()
        BtnProxyTest.IsEnabled = False
        LabProxyTest.Text = ModLang.L("测试中…")
        RunInNewThread(Sub()
                           Dim Ok As Boolean = False
                           Dim Elapsed As Long = -1
                           Dim Msg As String = ""
                           Dim Client As HttpClient = Nothing
                           Try
                               Client = New HttpClient(ModProxy.CreateHandler(), True)
                               Client.Timeout = TimeSpan.FromMilliseconds(ProbeTimeoutMs)
                               Dim Watch As Stopwatch = Stopwatch.StartNew()
                               Dim Response As HttpResponseMessage = Client.GetAsync(ProbeUrl, HttpCompletionOption.ResponseHeadersRead).GetAwaiter().GetResult()
                               Try
                                   Watch.Stop()
                                   Elapsed = Watch.ElapsedMilliseconds
                                   Ok = Response.IsSuccessStatusCode OrElse CInt(Response.StatusCode) = 403
                               Finally
                                   Response.Dispose()
                               End Try
                           Catch ex As TaskCanceledException
                               Msg = ModLang.L("请求超时（5 秒），代理可能不可用")
                           Catch ex As OperationCanceledException
                               Msg = ModLang.L("请求超时（5 秒），代理可能不可用")
                           Catch ex As HttpRequestException
                               Msg = ex.Message
                           Catch ex As Exception
                               Msg = ex.Message
                           Finally
                               If Client IsNot Nothing Then Client.Dispose()
                           End Try
                           RunInUi(Sub()
                                       Try
                                           BtnProxyTest.IsEnabled = True
                                           Dim Suffix As String = If(Dirty, ModLang.L("（用的是已保存的设置，改动请先保存）"), "")
                                           If Ok Then
                                               LabProxyTest.Text = ModLang.L("连接成功，耗时 ") & Elapsed & " ms" & Suffix
                                               Hint(ModLang.L("代理可用"), HintType.Green)
                                           Else
                                               LabProxyTest.Text = ModLang.L("连接失败：") & Msg &
                                                   ModLang.L("；当前代理：") & ModProxy.Describe() & Suffix
                                               Hint(ModLang.L("代理不可用，请检查地址、端口或代理软件是否在运行"), HintType.Red)
                                           End If
                                       Catch ex As Exception
                                           Logger.Warn(ex, "回填代理测试结果失败")
                                       End Try
                                   End Sub)
                       End Sub, "Proxy Test")
    End Sub

#End Region

#Region "下载源测速"

    ''' <summary>按内置源列表生成 4 行结果区。</summary>
    Private Sub BuildRows()
        Try
            PanTestRows.Children.Clear()
            Rows.Clear()
            For Each Src In ModSourceTest.GetSources()
                Dim Row As New Grid With {.Margin = New Thickness(0, 0, 0, 14)}
                Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(140)})
                Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(16)})
                Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(110)})
                Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(1, GridUnitType.Star)})

                Dim LabName As New TextBlock With {
                    .Text = Src.Name, .FontSize = 13, .VerticalAlignment = VerticalAlignment.Center,
                    .TextTrimming = TextTrimming.CharacterEllipsis}
                LabName.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
                Grid.SetColumn(LabName, 0)

                Dim Dot As New Border With {
                    .Width = 8, .Height = 8, .CornerRadius = New CornerRadius(4),
                    .HorizontalAlignment = HorizontalAlignment.Left, .VerticalAlignment = VerticalAlignment.Center}
                Dot.SetResourceReference(Border.BackgroundProperty, "ColorBrushGray5")
                Grid.SetColumn(Dot, 1)

                Dim Latency As New TextBlock With {.Text = "—", .FontSize = 13, .VerticalAlignment = VerticalAlignment.Center}
                Latency.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush2")
                Grid.SetColumn(Latency, 2)

                Dim Message As New TextBlock With {
                    .Text = Src.Note, .FontSize = 12, .Opacity = 0.6, .VerticalAlignment = VerticalAlignment.Center,
                    .TextTrimming = TextTrimming.CharacterEllipsis, .Margin = New Thickness(10, 0, 0, 0)}
                Message.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
                Grid.SetColumn(Message, 3)

                Row.Children.Add(LabName)
                Row.Children.Add(Dot)
                Row.Children.Add(Latency)
                Row.Children.Add(Message)
                PanTestRows.Children.Add(Row)

                Rows(Src.Tag) = New SourceRow With {.Dot = Dot, .Latency = Latency, .Message = Message}
            Next
            LabTestSummary.Text = ModLang.L("点「开始测速」检查各下载源的连通质量。")
        Catch ex As Exception
            Logger.Error(ex, "构建下载源测速列表失败")
        End Try
    End Sub

    Private Sub ResetRows()
        For Each Row In Rows.Values
            Row.Latency.Text = "…"
            Row.Latency.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush2")
            Row.Message.Text = ""
            Row.Dot.SetResourceReference(Border.BackgroundProperty, "ColorBrushGray5")
        Next
    End Sub

    Private Sub BtnSourceTest_Click(sender As Object, e As MouseButtonEventArgs)
        If Testing Then Return
        Testing = True
        Pending = Rows.Count
        BtnSourceTest.IsEnabled = False
        BtnApplySource.IsEnabled = False
        Results.Clear()
        ResetRows()
        LabTestSummary.Text = ModLang.L("测速中…（每个源最多 3 次采样，单次超时 5 秒）")
        Try
            ModSourceTest.TestAll(AddressOf OnSourceResult)
        Catch ex As Exception
            Logger.Error(ex, "启动下载源测速失败")
            Testing = False
            BtnSourceTest.IsEnabled = True
            LabTestSummary.Text = ModLang.L("测速启动失败：") & ex.Message
        End Try
    End Sub

    ''' <summary>单个源的结果回调（后台线程），内部切回 UI 线程回填。</summary>
    Private Sub OnSourceResult(Result As ModSourceTest.SourceResult)
        If Result Is Nothing Then Return
        RunInUi(Sub()
                    Try
                        Results(Result.Tag) = Result
                        Dim Row As SourceRow = Nothing
                        If Rows.TryGetValue(Result.Tag, Row) AndAlso Row IsNot Nothing Then
                            Dim Fg As Color = GetLatencyColor(Result)
                            Row.Dot.Background = New SolidColorBrush(Fg)
                            If Result.Success Then
                                Row.Latency.Text = Result.LatencyMs & " ms"
                            Else
                                Row.Latency.Text = ModLang.L("失败")
                            End If
                            Row.Latency.Foreground = New SolidColorBrush(Fg)
                            Row.Message.Text = Result.Message
                        End If
                        Pending -= 1
                        If Pending <= 0 Then
                            Testing = False
                            BtnSourceTest.IsEnabled = True
                        End If
                        UpdateSummary()
                    Catch ex As Exception
                        Logger.Warn(ex, "回填下载源测速结果失败")
                    End Try
                End Sub)
    End Sub

    ''' <summary>延迟着色：&lt;300ms 绿、&lt;1000ms 橙、其它或失败红。</summary>
    Private Function GetLatencyColor(Result As ModSourceTest.SourceResult) As Color
        If Result Is Nothing OrElse Not Result.Success Then Return Color.FromRgb(&HE7, &H4C, &H3C)
        If Result.LatencyMs < 300 Then Return Color.FromRgb(&H2E, &HCC, &H71)
        If Result.LatencyMs < 1000 Then Return Color.FromRgb(&HE6, &H7E, &H22)
        Return Color.FromRgb(&HE7, &H4C, &H3C)
    End Function

    ''' <summary>汇总一句推荐。</summary>
    Private Sub UpdateSummary()
        Try
            Dim OkList As List(Of ModSourceTest.SourceResult) =
                Results.Values.Where(Function(R) R.Success).OrderBy(Function(R) R.LatencyMs).ToList()
            If OkList.Count = 0 Then
                BtnApplySource.IsEnabled = False
                If Testing Then
                    LabTestSummary.Text = ModLang.L("测速中…")
                Else
                    LabTestSummary.Text = ModLang.L("所有源均不可达，请检查网络或代理设置")
                End If
                Return
            End If
            Dim Best As ModSourceTest.SourceResult = OkList(0)
            LabTestSummary.Text = ModLang.L("推荐使用：") & Best.Name &
                ModLang.L("（延迟最低，") & Best.LatencyMs & ModLang.L(" ms）")
            '只有 BMCLAPI 与 Mojang 官方对应「下载源」设置，社区资源站不参与
            Dim CanApply As Boolean = Best.Tag = "bmclapi" OrElse Best.Tag = "mojang"
            BtnApplySource.IsEnabled = Not Testing AndAlso CanApply
        Catch ex As Exception
            Logger.Warn(ex, "汇总下载源测速结果失败")
        End Try
    End Sub

    ''' <summary>
    ''' 应用推荐源：只对 BMCLAPI / Mojang 有意义——它们才是 Minecraft 本体文件的下载源。
    '''   ToolDownloadSource / ToolDownloadVersion：0=尽量用镜像 1=官方优先失败转镜像 2=尽量用官方
    ''' </summary>
    Private Sub BtnApplySource_Click(sender As Object, e As MouseButtonEventArgs)
        Dim Best As ModSourceTest.SourceResult = Nothing
        Try
            Dim OkList As List(Of ModSourceTest.SourceResult) =
                Results.Values.Where(Function(R) R.Success).OrderBy(Function(R) R.LatencyMs).ToList()
            If OkList.Count = 0 Then
                Hint(ModLang.L("还没有可用的测速结果，请先测速"), HintType.Blue)
                Return
            End If
            Best = OkList(0)
            Select Case Best.Tag
                Case "bmclapi"
                    Settings.Set("ToolDownloadSource", 0)
                    Settings.Set("ToolDownloadVersion", 0)
                    Hint(ModLang.L("已将文件下载源与版本列表源设为「尽量使用镜像源」"), HintType.Green)
                Case "mojang"
                    Settings.Set("ToolDownloadSource", 2)
                    Settings.Set("ToolDownloadVersion", 2)
                    Hint(ModLang.L("已将文件下载源与版本列表源设为「尽量使用官方源」"), HintType.Green)
                Case Else
                    Hint(ModLang.L("该源用于社区资源检索，与下载源设置无关，无需应用"), HintType.Blue)
            End Select
        Catch ex As Exception
            Logger.Error(ex, "应用推荐下载源失败")
            Hint(ModLang.L("应用推荐源失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

#End Region

End Class
