''' <summary>
''' 离线账户皮肤配置管理。
''' <para/>
''' 背景：原先皮肤设置（LaunchSkinType / LaunchSkinID / LaunchSkinSlim）是全局的，
''' 导致所有离线账户共享同一套皮肤——切换一个账户的皮肤会影响到其它全部账户，
''' 这是「多用户管理」最核心的缺陷。本模块把皮肤配置改为按玩家名存储，
''' 每个离线账户拥有独立的皮肤类型、正版皮肤名、模型胖瘦与自定义皮肤文件。
''' </summary>
Public Module ModLegacySkin

    ''' <summary>
    ''' 单个离线账户的皮肤配置。
    ''' </summary>
    Public Class SkinConfig
        ''' <summary>皮肤类型：0 默认 / 1 Steve / 2 Alex / 3 正版 / 4 本地文件 / 5 LittleSkin / 6 Blessing Skin。</summary>
        Public Type As Integer = 0
        ''' <summary>正版皮肤对应的玩家名（Type = 3 时有效）。</summary>
        Public Id As String = ""
        ''' <summary>是否为 Alex（细手臂）模型（Type = 4 时有效）。</summary>
        Public Slim As Boolean = False
        ''' <summary>本地皮肤文件路径（Type = 4 时有效）。</summary>
        Public File As String = ""
        ''' <summary>自定义皮肤站地址（Type = 6 时有效，如 Blessing Skin 服务器）。</summary>
        Public Api As String = ""
    End Class

    ''' <summary>
    ''' 皮肤弹窗的入参：多账号管理页对微软 / 第三方账户只要「3D 看一眼」，
    ''' 不能改写它们的皮肤配置，因此用 PreviewOnly 区分只读预览。
    ''' </summary>
    Public Class SkinEditorArgs
        ''' <summary>要编辑（或预览）的账户名。</summary>
        Public PlayerName As String = ""
        ''' <summary>True = 只读预览：不写配置，按钮显示「关闭」。</summary>
        Public PreviewOnly As Boolean = False
        ''' <summary>打开时默认选中的来源（-1 表示按已有配置）。</summary>
        Public PresetType As Integer = -1
        ''' <summary>默认来源对应的玩家名（正版 ID / 皮肤站角色名）。</summary>
        Public PresetId As String = ""
    End Class

    ''' <summary>
    ''' 读取全部离线账户的皮肤配置。
    ''' </summary>
    Public Function ReadAll() As JObject
        Try
            Dim Raw As String = Settings.Get(Of String)("LoginLegacySkinJson")
            If Raw Is Nothing OrElse Raw = "" Then Return New JObject
            Dim Json = Raw.DeserializeJson()
            If TypeOf Json Is JObject Then Return CType(Json, JObject)
            Return New JObject
        Catch ex As Exception
            Logger.Warn(ex, "读取离线账户皮肤配置失败")
            Return New JObject
        End Try
    End Function

    ''' <summary>
    ''' 写入全部离线账户的皮肤配置。
    ''' </summary>
    Public Sub WriteAll(Json As JObject)
        Settings.Set("LoginLegacySkinJson", Json.ToString(Newtonsoft.Json.Formatting.None))
    End Sub

    ''' <summary>
    ''' 获取指定离线账户的皮肤配置；未设置时返回默认配置（默认皮肤）。
    ''' </summary>
    Public Function GetConfig(PlayerName As String) As SkinConfig
        Dim Result As New SkinConfig
        If PlayerName Is Nothing OrElse PlayerName = "" Then Return Result
        Try
            Dim Token As JToken = ReadAll()(PlayerName)
            If Token Is Nothing Then Return Result
            Dim Json = CType(Token, JObject)
            Result.Type = If(Json("Type"), 0)
            Result.Id = If(Json("Id"), "")
            Result.Slim = CBool(If(Json("Slim"), False))
            Result.File = If(Json("File"), "")
            Result.Api = If(Json("Api"), "")
        Catch ex As Exception
            Logger.Warn(ex, $"读取离线账户 {PlayerName} 的皮肤配置失败")
        End Try
        Return Result
    End Function

    ''' <summary>
    ''' 保存指定离线账户的皮肤配置。
    ''' </summary>
    Public Sub SetConfig(PlayerName As String, Config As SkinConfig)
        If PlayerName Is Nothing OrElse PlayerName = "" Then Return
        Try
            Dim Json = ReadAll()
            Json(PlayerName) = New JObject(New JProperty("Type", Config.Type),
                                          New JProperty("Id", Config.Id),
                                          New JProperty("Slim", Config.Slim),
                                          New JProperty("File", Config.File),
                                          New JProperty("Api", Config.Api))
            WriteAll(Json)
        Catch ex As Exception
            Logger.Error(ex, $"保存离线账户 {PlayerName} 的皮肤配置失败")
        End Try
    End Sub

    ''' <summary>
    ''' 清除指定离线账户的皮肤配置（删除账户时调用）。
    ''' </summary>
    Public Sub RemoveConfig(PlayerName As String)
        If PlayerName Is Nothing OrElse PlayerName = "" Then Return
        Try
            Dim Json = ReadAll()
            If Json.Remove(PlayerName) Then WriteAll(Json)
        Catch ex As Exception
            Logger.Warn(ex, $"清除离线账户 {PlayerName} 的皮肤配置失败")
        End Try
    End Sub

    ''' <summary>
    ''' 离线账户自定义皮肤文件的存放路径（按玩家名隔离，避免多账户互相覆盖）。
    ''' </summary>
    Public Function CustomSkinPath(PlayerName As String) As String
        Dim SafeName As String = PlayerName
        For Each C As Char In IO.Path.GetInvalidFileNameChars()
            SafeName = SafeName.Replace(C, "_"c)
        Next
        Return Paths.AppDataThenName & "Skins\" & SafeName & ".png"
    End Function

    ''' <summary>
    ''' 内置皮肤（Steve / Alex / Kai …）的**真实文件**路径。
    ''' <para/>
    ''' HMCL 是把内置皮肤复制到自己的资源目录再使用，这里照同样做法——
    ''' 内置皮肤在 TCL 里是 pack:// 资源，只有 WPF 自己能读；3D 预览、头像裁切、外部工具
    ''' 都需要普通文件路径，所以首次使用时把资源解出来落盘，之后直接命中缓存。
    ''' 解不出来（磁盘不可写等）时回退到 pack:// 路径，WPF 内部读取仍然可用。
    ''' </summary>
    Public Function BuiltinSkinPath(Name As String) As String
        Dim PackPath As String = PathImage & "Skins/" & Name & ".png"
        Try
            If Name = "" Then Return PackPath
            '放 Builtin 子目录：Skins 根目录留给用户自定义皮肤（按玩家名存），同名玩家不会覆盖内置皮肤
            Dim Target As String = Paths.AppDataThenName & "Skins\Builtin\" & Name & ".png"
            If FileUtils.Exists(Target) Then Return Target
            Dim Folder As String = Paths.AppDataThenName & "Skins\Builtin"
            If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
            Dim Info = System.Windows.Application.GetResourceStream(New Uri(PackPath, UriKind.Absolute))
            If Info Is Nothing Then Return PackPath
            Using Src = Info.Stream
                Using Dst = IO.File.Create(Target)
                    Src.CopyTo(Dst)
                End Using
            End Using
            Return Target
        Catch ex As Exception
            Logger.Warn(ex, $"释放内置皮肤 {Name} 失败，改用内嵌资源路径")
            Return PackPath
        End Try
    End Function

    ''' <summary>
    ''' 按账户 UUID 选出的默认皮肤（Steve/Alex/Kai 等九个原版皮肤之一）的真实文件路径。
    ''' </summary>
    Public Function DefaultSkinPath(PlayerName As String) As String
        Return BuiltinSkinPath(McSkinSex(McLoginLegacyUuid(PlayerName)))
    End Function

End Module
