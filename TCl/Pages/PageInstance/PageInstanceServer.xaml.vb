Imports System.Windows.Media.Imaging

Public Class PageInstanceServer

    Private IsLoad As Boolean = False
    Private ServerPath As String = ""
    Private ServerList As New List(Of ModServer.McServerEntry)
    Private ReadOnly StatusTexts As New Dictionary(Of ModServer.McServerEntry, TextBlock)
    Private PingLock As Boolean = False

    Private Sub PageInstanceServer_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If PageInstanceLeft.Instance Is Nothing Then Return
        ServerPath = PageInstanceLeft.Instance.PathIndie
        If IsLoad Then Return
        IsLoad = True
        Reload()
    End Sub

    Public Sub Reload()
        StatusTexts.Clear()
        PanServers.Children.Clear()
        Try
            ServerList = ModServer.McServerListRead(ServerPath)
        Catch ex As Exception
            Logger.Error(ex, "读取服务器列表失败", LogBehavior.Toast)
            ServerList = New List(Of ModServer.McServerEntry)
        End Try
        RefreshTip()
        For Each Server In ServerList
            PanServers.Children.Add(CreateCard(Server))
        Next
        PingAll()
    End Sub

    Private Sub RefreshTip()
        If ServerList.Count = 0 Then
            PanNoServer.Visibility = Visibility.Visible
            PanContent.Visibility = Visibility.Collapsed
        Else
            PanNoServer.Visibility = Visibility.Collapsed
            PanContent.Visibility = Visibility.Visible
        End If
    End Sub

    Private Sub BtnAdd_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnAdd.Click, BtnAddTop.Click
        Dim NewName = MyMsgBoxInput(ModLang.L("添加服务器"), ModLang.L("请输入服务器名称："), ModLang.L("新的服务器"))
        If String.IsNullOrWhiteSpace(NewName) Then Return
        Dim NewIp = MyMsgBoxInput(ModLang.L("添加服务器"), ModLang.L("请输入服务器地址（IP 或 域名，可带端口）："), "")
        If String.IsNullOrWhiteSpace(NewIp) Then Return
        ServerList.Add(New ModServer.McServerEntry With {.Name = NewName.Trim(), .Ip = NewIp.Trim()})
        Save()
        Reload()
        Hint(ModLang.L("服务器已添加！"), HintType.Green)
    End Sub

    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnRefresh.Click
        PingAll()
    End Sub

    Private Sub Save()
        Try
            ModServer.McServerListWrite(ServerPath, ServerList)
        Catch ex As Exception
            Logger.Error(ex, "保存服务器列表失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub EditServer(Server As ModServer.McServerEntry)
        Dim NewName = MyMsgBoxInput(ModLang.L("编辑服务器"), ModLang.L("请输入服务器名称："), Server.Name)
        If String.IsNullOrWhiteSpace(NewName) Then Return
        Dim NewIp = MyMsgBoxInput(ModLang.L("编辑服务器"), ModLang.L("请输入服务器地址（IP 或 域名，可带端口）："), Server.Ip)
        If String.IsNullOrWhiteSpace(NewIp) Then Return
        Server.Name = NewName.Trim()
        Server.Ip = NewIp.Trim()
        Save()
        Reload()
        Hint(ModLang.L("服务器信息已更新！"), HintType.Green)
    End Sub

    Private Sub DeleteServer(Server As ModServer.McServerEntry)
        If MyMsgBox(ModLang.L("确定要删除服务器“") & Server.Name & ModLang.L("”吗？"), ModLang.L("删除服务器"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        ServerList.Remove(Server)
        Save()
        Reload()
        Hint(ModLang.L("服务器已删除！"), HintType.Green)
    End Sub

    ''' <summary>启动游戏并直连这个服务器（对齐 LumilioCL 世界页的「进入服务器」）。</summary>
    Private Sub LaunchServer(Server As ModServer.McServerEntry)
        Try
            Dim Instance As McInstance = PageInstanceLeft.Instance
            If Instance Is Nothing Then Return
            If String.IsNullOrWhiteSpace(Server.Ip) Then
                Hint(ModLang.L("这个服务器没有填写地址。"), HintType.Red)
                Return
            End If
            If McLaunchStart(New McLaunchOptions With {.Instance = Instance, .ServerIp = Server.Ip}) Then
                FrmMain.PageChange(FormMain.PageType.Launch)
            End If
        Catch ex As Exception
            Logger.Error(ex, "进入服务器失败")
            Hint(ModLang.L("进入服务器失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>复制服务器地址到剪贴板。</summary>
    Private Sub CopyServerAddress(Server As ModServer.McServerEntry)
        Try
            If String.IsNullOrWhiteSpace(Server.Ip) Then Return
            Clipboard.SetText(Server.Ip)
            Hint(ModLang.L("已复制地址：") & Server.Ip, HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "复制服务器地址失败")
            Hint(ModLang.L("复制失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>调整服务器在列表中的顺序并保存 servers.dat。</summary>
    Private Sub MoveServer(Server As ModServer.McServerEntry, Delta As Integer)
        Try
            Dim Index As Integer = ServerList.IndexOf(Server)
            If Index < 0 Then Return
            Dim Target As Integer = Index + Delta
            If Target < 0 OrElse Target >= ServerList.Count Then Return
            ServerList.RemoveAt(Index)
            ServerList.Insert(Target, Server)
            Save()
            Reload()
        Catch ex As Exception
            Logger.Error(ex, "调整服务器顺序失败")
            Hint(ModLang.L("调整顺序失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    Private Function CreateCard(Server As ModServer.McServerEntry) As MyCard
        Dim Card As New MyCard With {
            .Margin = New Thickness(15, 0, 15, 12),
            .UseAnimation = False, '不给卡片做高度动画：列表项高度变化会与滚动测量互相触发
            .Tag = Server
        }
        Dim Grid As New Grid With {.Margin = New Thickness(20, 15, 20, 15)}
        Grid.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(58)})
        Grid.ColumnDefinitions.Add(New ColumnDefinition)
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = GridLength.Auto})
        Card.Children.Add(Grid)

        Dim IconHost As New Grid With {
            .Width = 48, .Height = 48,
            .VerticalAlignment = VerticalAlignment.Top
        }
        Dim IconImage = TryDecodeIcon(Server.Icon)
        If IconImage IsNot Nothing Then
            IconHost.Children.Add(New Image With {.Source = IconImage, .Stretch = Stretch.Uniform})
        Else
            IconHost.Children.Add(New Global.TClUI.Controls.SvgIcon.SvgIcon With {
                .Icon = "lucide/server",
                .Width = 32, .Height = 32,
                .HorizontalAlignment = HorizontalAlignment.Center,
                .VerticalAlignment = VerticalAlignment.Center,
                .IconBrush = CType(FindResource("ColorBrush2"), Brush)
            })
        End If
        Grid.SetColumn(IconHost, 0)
        Grid.SetRowSpan(IconHost, 2)
        Grid.Children.Add(IconHost)

        Dim PanInfo As New StackPanel With {.Margin = New Thickness(6, 0, 0, 0)}
        Grid.SetColumn(PanInfo, 1)
        Grid.Children.Add(PanInfo)
        PanInfo.Children.Add(New TextBlock With {
            .Text = Server.Name,
            .FontSize = 16,
            .FontWeight = FontWeights.Bold,
            .TextTrimming = TextTrimming.CharacterEllipsis
        })
        PanInfo.Children.Add(New TextBlock With {
            .Text = Server.Ip,
            .Opacity = 0.7,
            .Margin = New Thickness(0, 3, 0, 0),
            .TextTrimming = TextTrimming.CharacterEllipsis
        })

        Dim PanBottom As New Grid With {.Margin = New Thickness(0, 10, 0, 0)}
        PanBottom.ColumnDefinitions.Add(New ColumnDefinition)
        PanBottom.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
        Grid.SetColumn(PanBottom, 1)
        Grid.SetRow(PanBottom, 1)
        Grid.Children.Add(PanBottom)

        Dim StatusText As New TextBlock With {
            .Text = ModLang.L("查询中……"),
            .Opacity = 0.75,
            .VerticalAlignment = VerticalAlignment.Center,
            .TextTrimming = TextTrimming.CharacterEllipsis
        }
        StatusTexts(Server) = StatusText
        PanBottom.Children.Add(StatusText)

        Dim PanBtn As New StackPanel With {
            .Orientation = Orientation.Horizontal,
            .HorizontalAlignment = HorizontalAlignment.Right
        }
        Grid.SetColumn(PanBtn, 1)
        PanBottom.Children.Add(PanBtn)

        Dim BtnPing As New MyIconTextButton With {.Text = ModLang.L("查询"), .LogoScale = 0.8, .SvgIcon = "lucide/activity"}
        AddHandler BtnPing.Click, Sub(sender As Object, e As RouteEventArgs) PingSingle(Server, StatusText)
        PanBtn.Children.Add(BtnPing)

        '第 121 轮：进入服务器（对齐 LumilioCL 世界页的服务器操作）
        Dim BtnLaunch As New MyIconTextButton With {.Text = ModLang.L("进入"), .LogoScale = 0.8, .SvgIcon = "lucide/play"}
        AddHandler BtnLaunch.Click, Sub(sender As Object, e As RouteEventArgs) LaunchServer(Server)
        PanBtn.Children.Add(BtnLaunch)

        '其余操作收进「更多」菜单：7 个按钮平铺会把 480px 宽的右栏撑爆，卡片被挤出可视区
        Dim BtnMore As New MyIconButton With {.LogoScale = 1.0, .SvgIcon = "lucide/more-vertical", .ToolTip = ModLang.L("更多操作")}
        AddHandler BtnMore.Click,
            Sub(sender As Object, e As EventArgs)
                '第 131 轮：改用动画菜单卡片（原 Separator 分组以行距体现）
                MenuCard.Show(BtnMore, {
                    New MenuCard.MenuCardRow("复制服务器地址", "lucide/copy", Sub() CopyServerAddress(Server)),
                    New MenuCard.MenuCardRow("上移", "lucide/arrow-up", Sub() MoveServer(Server, -1)),
                    New MenuCard.MenuCardRow("下移", "lucide/arrow-down", Sub() MoveServer(Server, 1)),
                    New MenuCard.MenuCardRow("编辑", "lucide/pencil", Sub() EditServer(Server)),
                    New MenuCard.MenuCardRow("删除", "lucide/trash-2", Sub() DeleteServer(Server))})
            End Sub
        PanBtn.Children.Add(BtnMore)

        Card.Opacity = 0
        AniStart(AaOpacity(Card, 1, 200), "Server Fade " & GetUuid())
        Return Card
    End Function

    Private Sub PingSingle(Server As ModServer.McServerEntry, StatusText As TextBlock)
        StatusText.Text = ModLang.L("查询中……")
        Task.Run(Sub()
                     Dim Result = ModServer.McServerPing(Server.Ip, 3000)
                     ModBase.RunInUi(Sub() ApplyPingResult(StatusText, Result))
                 End Sub)
    End Sub

    Private Sub PingAll()
        If PingLock Then Return
        PingLock = True
        Dim Items As New List(Of PingJob)
        For Each Server In ServerList
            Dim Target As TextBlock = Nothing
            If StatusTexts.TryGetValue(Server, Target) Then
                Target.Text = ModLang.L("查询中……")
                Items.Add(New PingJob With {.Server = Server, .StatusText = Target})
            End If
        Next
        If Items.Count = 0 Then
            PingLock = False
            Return
        End If
        Dim Snapshot = Items.ToArray()
        RunInNewThread(
            Sub()
                Try
                    '用 Parallel.ForEach 自带的并发上限。
                    '原来那套「每个服务器一个 Task.Run + SemaphoreSlim.Wait」在线程池里制造了大量阻塞线程，
                    '服务器一多线程池就要慢慢爬坡（Win7/低配机器最小线程数只有 2~4），表现就是点开页面卡住。
                    Threading.Tasks.Parallel.ForEach(Snapshot,
                        New Threading.Tasks.ParallelOptions With {.MaxDegreeOfParallelism = 5},
                        Sub(Job)
                            Try
                                Dim Result = ModServer.McServerPing(Job.Server.Ip, 3000)
                                ModBase.RunInUi(Sub() ApplyPingResult(Job.StatusText, Result))
                            Catch ex As Exception
                                Logger.Warn(ex, $"查询服务器状态失败：{Job.Server.Ip}")
                                ModBase.RunInUi(Sub() ApplyPingFailed(Job.StatusText))
                            End Try
                        End Sub)
                Finally
                    ModBase.RunInUi(Sub() PingLock = False)
                End Try
            End Sub, "Server Ping All", ThreadPriority.BelowNormal)
    End Sub

    ''' <summary>一次批量查询里的一项。</summary>
    Private Class PingJob
        Public Server As ModServer.McServerEntry
        Public StatusText As TextBlock
    End Class

    Private Sub ApplyPingFailed(StatusText As TextBlock)
        Try
            StatusText.Text = ModLang.L("离线") & ModLang.L("（查询异常）")
            StatusText.Foreground = CType(FindResource("ColorBrush3"), Brush)
            StatusText.Opacity = 0.8
        Catch ex As Exception
            Logger.Warn(ex, "更新服务器状态显示失败")
        End Try
    End Sub

    Private Sub ApplyPingResult(StatusText As TextBlock, Result As ModServer.McServerPingResult)
        If Result.Online Then
            Dim Motd = Result.Motd.Replace(vbCr, " ").Replace(vbLf, " ").Trim()
            If Motd.Length > 40 Then Motd = Motd.Substring(0, 40) & "…"
            StatusText.Text = ModLang.L("在线 · ") & Result.Players & "/" & Result.MaxPlayers & " · " & Result.Latency & "ms · " & Result.Version & "  " & Motd
            StatusText.Foreground = CType(FindResource("ColorBrush1"), Brush)
            StatusText.Opacity = 1
        Else
            StatusText.Text = ModLang.L("离线") & If(String.IsNullOrEmpty(Result.ErrorMessage), "", $"（{Result.ErrorMessage}）")
            StatusText.Foreground = CType(FindResource("ColorBrush3"), Brush)
            StatusText.Opacity = 0.8
        End If
    End Sub

    Private Shared Function TryDecodeIcon(Icon As String) As BitmapImage
        If String.IsNullOrWhiteSpace(Icon) Then Return Nothing
        Try
            Dim Base64 = Icon
            Dim CommaIndex = Base64.IndexOf(","c)
            If CommaIndex >= 0 Then Base64 = Base64.Substring(CommaIndex + 1)
            Dim Buffer = Convert.FromBase64String(Base64)
            Using Stream As New MemoryStream(Buffer)
                Dim Bitmap As New BitmapImage
                Bitmap.BeginInit()
                Bitmap.CacheOption = BitmapCacheOption.OnLoad
                Bitmap.StreamSource = Stream
                Bitmap.EndInit()
                Bitmap.Freeze()
                Return Bitmap
            End Using
        Catch
            Return Nothing
        End Try
    End Function

End Class
