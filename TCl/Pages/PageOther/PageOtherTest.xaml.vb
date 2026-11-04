Imports System.Drawing
Imports System.Drawing.Drawing2D
Imports System.Drawing.Imaging
Imports System.Net.Sockets

Public Class PageOtherTest

#Region "基础"

    Private Sub PageOtherTest_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        '默认下载文件夹
        If String.IsNullOrEmpty(TextDownloadFolder.Text) Then
            TextDownloadFolder.Text = Paths.Base & "TCL\MyDownload\"
        End If
    End Sub

#End Region

#Region "实用工具"

    ''' <summary>
    ''' 打开联机页面，创建或加入房间。
    ''' </summary>
    Private Sub BtnGameLink_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnGameLink.Click
        FrmMain.PageChange(FormMain.PageType.Link)
    End Sub

    ''' <summary>
    ''' 清理各个 Minecraft 文件夹中的垃圾文件。
    ''' </summary>
    Private Sub BtnClear_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnClear.Click
        RubbishClear()
    End Sub
    Public Shared Sub RubbishClear()
        RunInNewThread(
        Sub()
            Try
                '检查是否有正在运行的游戏或下载任务
                If McLaunchLoader.State = LoadState.Loading Then
                    Hint(ModLang.L("请先关闭运行中的 Minecraft 再进行清理！"), HintType.Red)
                    Return
                End If
                If ModNet.HasDownloadingTask() Then
                    Hint(ModLang.L("请等待下载任务结束后再进行清理！"), HintType.Red)
                    Return
                End If
                If MyMsgBox(ModLang.L("即将清理各个 Minecraft 文件夹中的崩溃报告、日志、Natives 等缓存文件。") & vbCrLf &
                            ModLang.L("此操作不可撤销，是否确定？"), ModLang.L("清理垃圾"), ModLang.L("确定"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
                '确保文件夹列表已加载
                If Not McFolderList.Any() Then
                    McFolderListLoader.Start(0)
                    McFolderListLoader.WaitForExit()
                End If
                Dim Count As Integer = 0
                '遍历所有 Minecraft 文件夹及其版本文件夹
                Dim FolderList As New List(Of String)
                For Each Folder In McFolderList
                    FolderList.Add(Folder.Location)
                    Dim VersionDir As String = Folder.Location & "versions\"
                    If DirectoryUtils.Exists(VersionDir) Then
                        For Each SubDir In DirectoryUtils.EnumerateDirectories(VersionDir, False)
                            FolderList.Add(PathUtils.AddSlashSuffix(SubDir))
                        Next
                    End If
                Next
                For Each Folder In FolderList
                    '删除崩溃报告与日志文件夹
                    Count += DeleteFolderCount(Folder & "crash-reports\")
                    Count += DeleteFolderCount(Folder & "logs\")
                    '删除根目录的日志与错误文件
                    If DirectoryUtils.Exists(Folder) Then
                        For Each File In DirectoryUtils.EnumerateFiles(Folder, False)
                            Dim Name As String = PathUtils.GetLastPart(File)
                            If Name.StartsWithF("hs_err_pid") OrElse Name.EndsWithF(".log") OrElse Name = "WailaErrorOutput.txt" Then
                                If FileUtils.Delete(File) Then Count += 1
                            End If
                        Next
                        '删除 Natives 文件夹
                        For Each SubDir In DirectoryUtils.EnumerateDirectories(Folder, False)
                            Dim DirName As String = PathUtils.GetLastPart(SubDir)
                            If DirName.EndsWithF("-natives") OrElse DirName = "natives-windows-x86_64" Then
                                Count += DeleteFolderCount(PathUtils.AddSlashSuffix(SubDir))
                            End If
                        Next
                    End If
                Next
                If Count > 0 Then
                    MyMsgBox(ModLang.L("清理完成，共删除了") & " " & Count & " " & ModLang.L("项垃圾文件。"), ModLang.L("清理完成"), ModLang.L("好的"))
                Else
                    Hint(ModLang.L("没有找到需要清理的垃圾文件。"))
                End If
            Catch ex As Exception
                Logger.Error(ex, ModLang.L("清理垃圾失败"))
            End Try
        End Sub, "Rubbish Clear", ThreadPriority.BelowNormal)
    End Sub
    ''' <summary>
    ''' 删除文件夹并返回其中文件的数量。
    ''' </summary>
    Private Shared Function DeleteFolderCount(Folder As String) As Integer
        If Not DirectoryUtils.Exists(Folder) Then Return 0
        Dim Count As Integer = 0
        Try
            For Each File In DirectoryUtils.EnumerateFiles(Folder, True)
                Count += 1
            Next
            DirectoryUtils.Delete(Folder)
        Catch ex As Exception
            Logger.Warn(ex, $"删除文件夹失败：{Folder}")
        End Try
        Return Count
    End Function

    ''' <summary>
    ''' 今日人品。
    ''' </summary>
    Private Sub BtnLuck_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnLuck.Click
        Jrrp()
    End Sub
    Public Shared Sub Jrrp()
        Dim Rand As New Random(GetDailySeed())
        Dim LuckValue As Integer = Rand.Next(0, 101)
        Dim Rating As String = GetLuckRating(LuckValue)
        '日期按当前界面语言的文化显示（zh-CN: 2026/10/28，en-US: 10/28/2026），避免硬编码「年月日」只出现简中（第 67 轮）
        Dim Title As String = ModLang.L("今日人品") & " · " & Date.Today.ToString("d", Globalization.CultureInfo.CurrentCulture) & " (◕‿◕)"
        If LuckValue >= 60 Then
            MyMsgBox(ModLang.L("今日人品：") & LuckValue & " " & ModLang.L("分！") & vbCrLf & ModLang.L("评价：") & Rating, Title)
        Else
            MyMsgBox(ModLang.L("今日人品：") & LuckValue & " " & ModLang.L("分……") & vbCrLf & ModLang.L("评价：") & Rating, Title, ModLang.L("我知道了"), IsWarn:=(LuckValue <= 30))
        End If
    End Sub
    ''' <summary>
    ''' 获取当日种子（DJB2 哈希）。
    ''' </summary>
    Public Shared Function GetDailySeed() As Integer
        Dim DatePart As String = Date.Today.ToString("yyyyMMdd", Globalization.CultureInfo.InvariantCulture)
        Return Djb2Hash(DatePart & Identify)
    End Function
    Private Shared Function Djb2Hash(Str As String) As Integer
        Dim Hash As Long = 5381
        Const Prime As Long = 33
        For Each C As Char In Str
            Hash = (Hash * Prime + AscW(C)) Mod &H100000000L
        Next
        Return CInt(Hash And &H7FFFFFFF)
    End Function
    Private Shared Function GetLuckRating(LuckValue As Integer) As String
        Select Case LuckValue
            Case 100
                Return ModLang.L("满分！天选之子非你莫属，欧气拉满、掉啥爆啥！(๑•̀ㅂ•́)و✧")
            Case >= 95
                Return ModLang.L("鸿运当头，连挖都是稀有货，今天掉块钻石都不奇怪！(≧▽≦)")
            Case >= 90
                Return ModLang.L("运气爆棚，下界见龙蛋、刷怪塔爆满，冲就完事了！(•̀ᴗ•́)و")
            Case >= 60
                Return ModLang.L("运势不错，今天是个好日子，正常肝基本不会亏。٩(ˊᗜˋ*)۶")
            Case >= 40
                Return ModLang.L("平平淡淡，掉率回到平均线，稳住就好、别指望逆天。(•̀_•́)")
            Case >= 30
                Return ModLang.L("运气偏背，抽卡、开盲盒先缓缓，动手前深呼吸。(；´д｀)")
            Case >= 10
                Return ModLang.L("今天掉的全是沙子和煤炭，背到连刷怪塔都在坑你……(╯°□°)╯")
            Case Else
                Return ModLang.L("大凶之日，宜躺平、宜频繁存档，出门前多存几份档。Orz")
        End Select
    End Function

    ''' <summary>
    ''' 创建快捷方式。
    ''' </summary>
    Public Shared Sub CreateShortcut()
        Try
            Dim ShortcutName As String = "TCL.lnk"
            Dim DesktopPath As String = Environment.GetFolderPath(Environment.SpecialFolder.Desktop) & "\" & ShortcutName
            Dim StartMenuPath As String = Environment.GetFolderPath(Environment.SpecialFolder.StartMenu) & "\Programs\" & ShortcutName
            Dim Choice As Integer = MyMsgBox(
                ModLang.L("即将创建启动器的快捷方式，请选择创建位置：") & vbCrLf & vbCrLf & ModLang.L("· 桌面：") & DesktopPath & vbCrLf & ModLang.L("· 开始菜单：") & StartMenuPath,
                ModLang.L("创建快捷方式"), ModLang.L("取消"), ModLang.L("桌面"), ModLang.L("开始菜单"))
            If Choice = 1 Then Return
            Dim TargetPath As String = If(Choice = 2, DesktopPath, StartMenuPath)
            Dim LocationName As String = If(Choice = 2, ModLang.L("桌面"), ModLang.L("开始菜单"))
            CreateShortcutTo(TargetPath, PathExe, Paths.Base)
            Hint(ModLang.L("快捷方式已创建到") & LocationName & ModLang.L("！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, ModLang.L("创建快捷方式失败"))
            Hint(ModLang.L("创建快捷方式失败") & ModLang.L("：") & ex.Message, HintType.Red)
        End Try
    End Sub
    '使用 WScript.Shell 创建快捷方式。
    Private Shared Sub CreateShortcutTo(ShortcutPath As String, TargetPath As String, WorkingDirectory As String)
        Dim Shell As Object = CreateObject("WScript.Shell")
        Dim Shortcut As Object = Shell.CreateShortcut(ShortcutPath)
        Shortcut.TargetPath = TargetPath
        Shortcut.WorkingDirectory = WorkingDirectory
        Shortcut.Description = "TCL"
        Shortcut.Save()
    End Sub

    Private Sub BtnCreateShortcut_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnCreateShortcut.Click
        CreateShortcut()
    End Sub

    ''' <summary>
    ''' 显示 Minecraft 启动次数。
    ''' </summary>
    Private Sub BtnLaunchCount_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnLaunchCount.Click
        MyMsgBox(ModLang.L("你已经启动了") & " " & Settings.Get(Of Integer)("SystemLaunchCount") & " " & ModLang.L("次 Minecraft！"), ModLang.L("启动次数"))
    End Sub

    ''' <summary>
    ''' 内存优化。
    ''' </summary>
    Public Shared Sub MemoryOptimize(ShowHint As Boolean)
        Try
            MemoryOptimizeInternal(ShowHint)
        Catch ex As Exception
            Logger.Error(ex, ModLang.L("内存优化失败"))
        End Try
    End Sub
    Public Shared Sub MemoryOptimizeInternal(ShowHint As Boolean)
        GC.Collect()
        GC.WaitForPendingFinalizers()
        GC.Collect()
        If ShowHint Then Hint(ModLang.L("内存优化完成！"), HintType.Green)
    End Sub

#End Region

#Region "自定义下载"

    Private Sub BtnDownloadSelect_Click(sender As Object, e As EventArgs) Handles BtnDownloadSelect.Click
        Dim Result As List(Of String) = Dialogs.SelectFolder(ModLang.L("选择保存位置"), False)
        If Result.Any() Then TextDownloadFolder.Text = Result.First()
    End Sub
    Private Sub BtnDownloadOpen_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnDownloadOpen.Click
        Try
            Dim Folder As String = TextDownloadFolder.Text
            If String.IsNullOrEmpty(Folder) Then Folder = Paths.Base & "TCL\MyDownload\"
            If Not Folder.EndsWithF("\") Then Folder &= "\"
            DirectoryUtils.Create(Folder)
            OpenExplorer(Folder)
        Catch ex As Exception
            Logger.Error(ex, ModLang.L("打开下载文件夹失败"))
        End Try
    End Sub
    Private Sub BtnDownloadStart_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnDownloadStart.Click
        Dim Url As String = TextDownloadUrl.Text.Trim()
        Dim Name As String = TextDownloadName.Text.Trim()
        StartCustomDownload(Url, Name, TextDownloadFolder.Text.Trim())
        '清空输入
        TextDownloadUrl.Text = ""
        TextDownloadName.Text = ""
    End Sub
    ''' <summary>
    ''' 从指定网址下载文件到目标文件夹。
    ''' </summary>
    Public Shared Sub StartCustomDownload(Url As String, FileName As String, Optional Folder As String = Nothing)
        If String.IsNullOrEmpty(Url) Then
            Hint(ModLang.L("请输入下载网址！"), HintType.Red)
            Return
        End If
        If String.IsNullOrEmpty(FileName) Then
            Hint(ModLang.L("请输入文件名！"), HintType.Red)
            Return
        End If
        If String.IsNullOrEmpty(Folder) Then Folder = Paths.Base & "TCL\MyDownload\"
        If Not Folder.EndsWithF("\") Then Folder &= "\"
        RunInNewThread(
        Sub()
            Try
                DirectoryUtils.Create(Folder)
                Logger.Info($"自定义下载：{Url} → {Folder & FileName}")
                NetDownloadByClient(Url, Folder & FileName)
                Hint(ModLang.L("下载完成：") & FileName, HintType.Green)
            Catch ex As Exception
                Logger.Error(ex, ModLang.L("自定义下载失败"))
                Hint(ModLang.L("下载失败：") & ex.Message, HintType.Red)
            End Try
        End Sub, "Custom Download", ThreadPriority.BelowNormal)
    End Sub

#End Region

#Region "正版皮肤保存"

    Private Sub BtnSkinSave_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSkinSave.Click
        Dim Name As String = TextSkinID.Text.Trim()
        If Name.Length < 3 Then
            Hint(ModLang.L("玩家名至少需要 3 个字符！"), HintType.Red)
            Return
        End If
        Hint(ModLang.L("正在获取正版皮肤……"))
        RunInNewThread(
        Sub()
            Try
                Dim Uuid As String = McLoginMojangUuid(Name, True)
                Dim Address As String = McSkinGetAddress(Uuid, "Mojang")
                Dim LocalFile As String = McSkinDownload(Address)
                RunInUi(
                Sub()
                    '先弹预览，用户看过 3D 模型再点「保存」才真正落盘
                    Hint(ModLang.L("皮肤已获取，请在预览窗口确认后保存"), HintType.Green)
                    MyMsgBoxSkinPreview(Name, LocalFile, ModLang.L("正版玩家皮肤"))
                End Sub)
            Catch ex As Exception
                If ex.Message.Contains("429") Then
                    Hint(ModLang.L("获取皮肤太过频繁，请 5 分钟后再试！"), HintType.Red)
                Else
                    Logger.Error(ex, $"获取正版皮肤失败（{Name}）")
                    Hint(ModLang.L("获取正版皮肤失败：") & ex.Message, HintType.Red)
                End If
            End Try
        End Sub, "Skin Save", ThreadPriority.BelowNormal)
    End Sub

#End Region

#Region "服务器查询"

    Private Sub BtnServerQuery_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnServerQuery.Click
        Dim Address As String = TextServerAddress.Text.Trim()
        If String.IsNullOrEmpty(Address) Then
            Hint(ModLang.L("请输入服务器地址！"), HintType.Red)
            Return
        End If
        LabServerResult.Text = ModLang.L("正在查询服务器……")
        RunInNewThread(
        Sub()
            Try
                Dim Json As String = QueryServerListPing(Address)
                Dim Result As String = ParseServerPing(Json)
                RunInUi(Sub() LabServerResult.Text = Result)
            Catch ex As Exception
                Logger.Error(ex, $"服务器查询失败（{Address}）")
                RunInUi(Sub() LabServerResult.Text = ModLang.L("查询失败：") & ex.Message)
            End Try
        End Sub, "Server Query", ThreadPriority.BelowNormal)
    End Sub
    ''' <summary>
    ''' 使用 Minecraft 服务器列表 Ping（SLP）协议查询服务器，返回原始 JSON。
    ''' </summary>
    Private Shared Function QueryServerListPing(Address As String) As String
        Dim Host As String = Address
        Dim Port As Integer = 25565
        If Address.Contains(":") Then
            Dim Parts() As String = Address.Split(":"c)
            Host = Parts(0)
            Integer.TryParse(Parts(1), Port)
        End If
        Using Client As New TcpClient()
            Client.SendTimeout = 5000
            Client.ReceiveTimeout = 5000
            Client.Connect(Host, Port)
            Dim Stream As NetworkStream = Client.GetStream()
            '握手包
            Using Ms As New IO.MemoryStream()
                WriteVarInt(Ms, 0)
                WriteVarInt(Ms, -1)
                WriteString(Ms, Host)
                WriteUShort(Ms, CUShort(Port))
                WriteVarInt(Ms, 1)
                SendPacket(Stream, Ms.ToArray())
            End Using
            '状态请求包
            Using Ms As New IO.MemoryStream()
                WriteVarInt(Ms, 0)
                SendPacket(Stream, Ms.ToArray())
            End Using
            '读取响应
            Dim PacketLength As Integer = ReadVarInt(Stream)
            If PacketLength <= 0 Then Throw New Exception("服务器响应长度无效")
            ReadVarInt(Stream) 'PacketId，忽略
            Dim JsonLength As Integer = ReadVarInt(Stream)
            If JsonLength <= 0 Then Throw New Exception("服务器响应内容为空")
            Dim Buffer(JsonLength - 1) As Byte
            Dim Read As Integer = 0
            While Read < JsonLength
                Dim N As Integer = Stream.Read(Buffer, Read, JsonLength - Read)
                If N <= 0 Then Throw New Exception("读取服务器响应失败")
                Read += N
            End While
            Return Encoding.UTF8.GetString(Buffer)
        End Using
    End Function
    Private Shared Sub SendPacket(Stream As NetworkStream, Payload As Byte())
        Using Ms As New IO.MemoryStream()
            WriteVarInt(Ms, Payload.Length)
            Ms.Write(Payload, 0, Payload.Length)
            Dim Data() As Byte = Ms.ToArray()
            Stream.Write(Data, 0, Data.Length)
            Stream.Flush()
        End Using
    End Sub
    Private Shared Sub WriteVarInt(Ms As IO.MemoryStream, Value As Integer)
        Dim V As UInteger = CUInt(Value)
        Do
            Dim Temp As Byte = CByte(V And &H7F)
            V >>= 7
            If V <> 0 Then Temp = CByte(Temp Or &H80)
            Ms.WriteByte(Temp)
        Loop While V <> 0
    End Sub
    Private Shared Function ReadVarInt(Stream As NetworkStream) As Integer
        Dim Result As Integer = 0
        Dim Shift As Integer = 0
        Do
            Dim B As Integer = Stream.ReadByte()
            If B < 0 Then Throw New Exception("读取 VarInt 失败")
            If Shift >= 32 Then Throw New Exception("VarInt 过长")
            Result = Result Or (CByte(B And &H7F) << Shift)
            Shift += 7
            If (B And &H80) = 0 Then Exit Do
        Loop
        Return Result
    End Function
    Private Shared Sub WriteString(Ms As IO.MemoryStream, Value As String)
        Dim Data() As Byte = Encoding.UTF8.GetBytes(Value)
        WriteVarInt(Ms, Data.Length)
        Ms.Write(Data, 0, Data.Length)
    End Sub
    Private Shared Sub WriteUShort(Ms As IO.MemoryStream, Value As UShort)
        Ms.WriteByte(CByte(Value >> 8))
        Ms.WriteByte(CByte(Value And &HFF))
    End Sub
    ''' <summary>
    ''' 解析 SLP 响应 JSON 为可读文本。
    ''' </summary>
    Private Shared Function ParseServerPing(Json As String) As String
        Dim Data As JObject = Json.DeserializeJson()
        Dim Description As String = ExtractDescription(Data("description"))
        Dim VersionName As String = Data("version")("name").ToString
        Dim Online As String = Data("players")("online").ToString
        Dim Max As String = Data("players")("max").ToString
        Return $"描述：{Description}{vbCrLf}版本：{VersionName}{vbCrLf}人数：{Online} / {Max}"
    End Function
    ''' <summary>
    ''' 提取 MOTD 文本（可能是字符串或含 extra 数组的对象）。
    ''' </summary>
    Private Shared Function ExtractDescription(Token As JToken) As String
        If Token Is Nothing Then Return "（无描述）"
        If Token.Type = JTokenType.String Then Return Token.ToString
        Dim Result As New System.Text.StringBuilder()
        Try
            If Token("text") IsNot Nothing Then Result.Append(Token("text").ToString)
            If Token("extra") IsNot Nothing Then
                For Each Item In Token("extra")
                    If Item.Type = JTokenType.String Then
                        Result.Append(Item.ToString)
                    ElseIf Item("text") IsNot Nothing Then
                        Result.Append(Item("text").ToString)
                    End If
                Next
            End If
        Catch ex As Exception
            Logger.Warn(ex, ModLang.L("解析服务器描述失败"))
        End Try
        If Result.Length = 0 Then Return Token.ToString
        Return Result.ToString()
    End Function

#End Region

#Region "成就图片生成"

    Private Function GetAchievementUrl() As String
        Dim Block As String = TextAchievementBlock.Text.Trim()
        Dim Title As String = TextAchievementTitle.Text.Replace(" ", "..")
        Dim Line1 As String = TextAchievementLine1.Text.Replace(" ", "..")
        Dim Line2 As String = TextAchievementLine2.Text.Replace(" ", "..")
        Dim Url As String = $"https://minecraft-api.com/api/achivements/{Uri.EscapeDataString(Block)}/{Uri.EscapeDataString(Title)}/{Uri.EscapeDataString(Line1)}"
        If Not String.IsNullOrEmpty(Line2) Then Url &= $"/{Uri.EscapeDataString(Line2)}"
        Return Url
    End Function
    Private Sub BtnAchievementPreview_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnAchievementPreview.Click
        If String.IsNullOrEmpty(TextAchievementBlock.Text.Trim()) Then
            Hint(ModLang.L("请输入物品 ID！"), HintType.Red)
            Return
        End If
        Dim Url As String = GetAchievementUrl()
        RunInNewThread(
        Sub()
            Try
                Dim TempFile As String = PathTemp & "Download\" & GetUuid() & ".png"
                DirectoryUtils.Create(PathTemp & "Download\")
                NetDownloadByClient(Url, TempFile)
                RunInUi(
                Sub()
                    Try
                        Dim Image As New BitmapImage()
                        Image.BeginInit()
                        Image.CacheOption = BitmapCacheOption.OnLoad
                        Image.UriSource = New Uri(TempFile)
                        Image.EndInit()
                        Image.Freeze()
                        ImgAchievement.Source = Image
                        ImgAchievement.Visibility = Visibility.Visible
                    Catch exImg As Exception
                        Logger.Error(exImg, ModLang.L("显示成就图片失败"))
                        Hint(ModLang.L("显示成就图片失败，文件可能不是有效的图片。"), HintType.Red)
                    End Try
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, ModLang.L("获取成就图片失败"))
                Hint(ModLang.L("获取成就图片失败，请检查物品 ID 是否正确。"), HintType.Red)
            End Try
        End Sub, "Achievement Preview", ThreadPriority.BelowNormal)
    End Sub
    Private Sub BtnAchievementSave_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnAchievementSave.Click
        If String.IsNullOrEmpty(TextAchievementBlock.Text.Trim()) Then
            Hint(ModLang.L("请输入物品 ID！"), HintType.Red)
            Return
        End If
        Dim Url As String = GetAchievementUrl()
        RunInNewThread(
        Sub()
            Try
                Dim TempFile As String = PathTemp & "Download\" & GetUuid() & ".png"
                DirectoryUtils.Create(PathTemp & "Download\")
                NetDownloadByClient(Url, TempFile)
                RunInUi(
                Sub()
                    Dim DefaultName As String = If(String.IsNullOrEmpty(TextAchievementTitle.Text.Trim()), "achievement", TextAchievementTitle.Text.Trim())
                    Dim SavePath As String = Dialogs.SaveFile(ModLang.L("保存成就图片"), DefaultName & ".png", filter:={("png", ModLang.L("PNG 图片"))})
                    If String.IsNullOrEmpty(SavePath) Then Return
                    FileUtils.Copy(TempFile, SavePath)
                    Hint(ModLang.L("成就图片已保存！"), HintType.Green)
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, ModLang.L("保存成就图片失败"))
                Hint(ModLang.L("获取成就图片失败，请检查物品 ID 是否正确。"), HintType.Red)
            End Try
        End Sub, "Achievement Save", ThreadPriority.BelowNormal)
    End Sub

#End Region

#Region "头像生成"

    Private CurrentSkin As System.Drawing.Bitmap = Nothing
    Private Sub BtnSelectSkin_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSelectSkin.Click
        Dim Result As List(Of String) = Dialogs.SelectFile(ModLang.L("选择皮肤文件（64x64）"), False,
            filter:={({"png", "jpg", "jpeg"}, ModLang.L("皮肤文件"))})
        If Not Result.Any() Then Return
        LoadAndGenerateHead(Result.First())
    End Sub
    Private Sub LoadAndGenerateHead(SkinPath As String)
        Try
            CurrentSkin = New System.Drawing.Bitmap(SkinPath)
            If CurrentSkin.Width <> CurrentSkin.Height Then
                Hint(ModLang.L("皮肤文件必须是正方形（如 64x64）！"), HintType.Red)
                PanSkinPreview.Visibility = Visibility.Collapsed
                Return
            End If
            RefreshHeadPreview()
            PanSkinPreview.Visibility = Visibility.Visible
            Hint(ModLang.L("头像已生成！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, ModLang.L("生成头像失败"))
            Hint(ModLang.L("生成头像失败：") & ex.Message, HintType.Red)
            PanSkinPreview.Visibility = Visibility.Collapsed
        End Try
    End Sub
    Private Sub RefreshHeadPreview()
        If CurrentSkin Is Nothing Then Return
        Dim Head As System.Drawing.Bitmap = GenerateHeadFromSkin(CurrentSkin, GetSelectedHeadSize())
        ImgFace.Source = CType(New MyBitmap(Head), ImageSource)
        ImgHair.Source = Nothing
    End Sub
    Private Function GetSelectedHeadSize() As Integer
        Select Case ComboHeadSize.SelectedIndex
            Case 1
                Return 96
            Case 2
                Return 128
            Case Else
                Return 64
        End Select
    End Function
    Private Sub ComboHeadSize_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles ComboHeadSize.SelectionChanged
        If CurrentSkin IsNot Nothing Then RefreshHeadPreview()
    End Sub
    ''' <summary>
    ''' 从皮肤文件中提取头部，生成头像（脸 + 头发层）。
    ''' </summary>
    Private Shared Function GenerateHeadFromSkin(Skin As System.Drawing.Bitmap, Size As Integer) As System.Drawing.Bitmap
        Dim Scale As Integer = Skin.Width \ 64
        If Scale < 1 Then Scale = 1
        Dim Head As New System.Drawing.Bitmap(Size, Size)
        Using G As Graphics = Graphics.FromImage(Head)
            G.InterpolationMode = InterpolationMode.NearestNeighbor
            G.PixelOffsetMode = PixelOffsetMode.Half
            '脸
            Dim FaceSize As Integer = Size - Size \ 8
            Dim FaceScaled As New System.Drawing.Bitmap(FaceSize, FaceSize)
            Using GF As Graphics = Graphics.FromImage(FaceScaled)
                GF.InterpolationMode = InterpolationMode.NearestNeighbor
                GF.PixelOffsetMode = PixelOffsetMode.Half
                GF.DrawImage(Skin, New System.Drawing.Rectangle(0, 0, FaceSize, FaceSize),
                             New System.Drawing.Rectangle(8 * Scale, 8 * Scale, 8 * Scale, 8 * Scale), GraphicsUnit.Pixel)
            End Using
            Dim Offset As Integer = Size \ 16
            G.DrawImage(FaceScaled, Offset, Offset, FaceSize, FaceSize)
            '头发（第二层，仅 64x64 皮肤有）
            If Skin.Width >= 64 Then
                Dim HairScaled As New System.Drawing.Bitmap(Size, Size)
                Using GH As Graphics = Graphics.FromImage(HairScaled)
                    GH.InterpolationMode = InterpolationMode.NearestNeighbor
                    GH.PixelOffsetMode = PixelOffsetMode.Half
                    GH.DrawImage(Skin, New System.Drawing.Rectangle(0, 0, Size, Size),
                                 New System.Drawing.Rectangle(40 * Scale, 8 * Scale, 8 * Scale, 8 * Scale), GraphicsUnit.Pixel)
                End Using
                For X As Integer = 0 To Size - 1
                    For Y As Integer = 0 To Size - 1
                        Dim Pixel As Color = HairScaled.GetPixel(X, Y)
                        If Pixel.A > 0 Then Head.SetPixel(X, Y, Pixel)
                    Next
                Next
            End If
        End Using
        Return Head
    End Function
    Private Sub BtnSaveHead_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSaveHead.Click
        If CurrentSkin Is Nothing Then
            Hint(ModLang.L("请先选择皮肤文件！"), HintType.Red)
            Return
        End If
        Dim Head As System.Drawing.Bitmap = GenerateHeadFromSkin(CurrentSkin, GetSelectedHeadSize())
        Dim SavePath As String = Dialogs.SaveFile(ModLang.L("保存头像"), "Head.png", filter:={("png", ModLang.L("PNG 图片"))})
        If String.IsNullOrEmpty(SavePath) Then Return
        Try
            Head.Save(SavePath, ImageFormat.Png)
            Hint(ModLang.L("头像已保存！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, ModLang.L("保存头像失败"))
            Hint(ModLang.L("保存头像失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

#End Region

#Region "你知道吗 / 回声洞"

    ''' <summary>
    ''' 随机你知道吗提示。
    ''' </summary>
    Public Shared Function GetRandomHint() As String
        Return RandomOne(HintList)
    End Function
    ''' <summary>
    ''' 随机回声洞语句。
    ''' </summary>
    Public Shared Function GetRandomCave() As String
        Return RandomOne(CaveList)
    End Function
    ''' <summary>
    ''' 随机预设你知道吗提示。
    ''' </summary>
    Public Shared Function GetRandomPresetHint() As String
        Return RandomOne(HintList)
    End Function
    Private Shared ReadOnly HintList As String() = {
        "你知道吗：Minecraft 的钻石剑其实是参考了经典传说中石中剑的造型！(๑•̀ㅂ•́)و",
"你知道吗：苦力怕是因为代码 Bug 而诞生的，原本作者只是想制作一头猪……",
"你知道吗：Minecraft 最初只是一个洞穴游戏，后来才变成了沙盒生存游戏。",
"你知道吗：末影人英文名 Enderman，名字来源于 Slender Man 和 Minecraft 的合成。",
"你知道吗：金苹果曾经需要八个金块来合成，后来才改为八个金锭。",
"你知道吗：村庄与掠夺更新让村庄拥有了职业与工作站，这一切都源于村民 AI 重写。",
"你知道吗：红石电路的设计灵感来自于现实中的数字逻辑电路。",
"你知道吗：Minecraft 的世界生成基于柏林噪声，这就是地形看起来自然的原因……",
"你知道吗：史蒂夫的默认名字其实来自社区投票，而亚历克斯则来自另一场投票。",
"你知道吗：在基岩版中，玩家可以利用一些特性实现快速移动，社区称之为管道飞行。",
"你知道吗：1.16 下界更新重做了整个下界群系，是史上最大的一次维度更新之一。",
"你知道吗：Minecraft 是全球销量最高的电子游戏，没有之一。٩(ˊᗜˋ*)۶",
"你知道吗：创造模式的物品栏曾经非常简陋，连搜索框都没有。",
"你知道吗：潜影贝的设定参考了现实中的装饰性贝壳与收纳箱的混合体。",
"你知道吗：养蜂人村民的蜂箱会吸引蜜蜂，而且蜜蜂会记住自己的家。(•̀ᴗ•́)"
    }
    Private Shared ReadOnly CaveList As String() = {
        "洞穴……深处有回声……",
        "洞……洞穴……回洞……",
        "你onlinedin在洞穴里喊了一声，传来了自己的回声。",
        "洞中无岁月，地上已千年。",
        "有些洞穴，进去了就再也找不到出口。",
        "回声说：别再喊了，我听得见。",
        "洞穴尽头，往往还是洞穴。",
        "在洞穴里，火把是最忠实的伙伴。",
        "山洞里的水滴，比时间更有耐心。",
        "每一个洞穴，都是一段等待被书写的故事。",
        "回声洞里没有秘密，只有重复。",
        "洞穴深处，连 silence 都有回音。",
        "你以为洞穴的尽头是出口，其实是另一个洞穴的开始。",
        "矿洞里的每一块石头，都记得镐子的敲击声。",
        "洞穴是大地的伤口，也是冒险者的起点。",
        "回声在洞穴里跑了一圈，又回到了你身边。",
        "洞穴里最珍贵的不是钻石，而是走出去的方向。"
    }

#End Region

End Class
