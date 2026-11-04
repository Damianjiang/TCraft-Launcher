Imports System.Net
Imports System.Net.Http

''' <summary>
''' 正版皮肤上传（第 133 轮）。
'''
''' 原先这段逻辑只写在 PageLoginMsSkin 里，而那个页面从旧的登录页改版后就再没被实例化过——
''' 「修改皮肤」实际没有任何可用入口。这里抽成模块，账户管理页与（保留的）皮肤页共用一份实现。
''' </summary>
Public Module ModMsSkin

    ''' <summary>
    ''' 上传皮肤到 Mojang（含 401 自动重新登录后重试一次）。
    ''' 成功后写皮肤地址缓存并重载所有皮肤加载器，头像 / 标题栏徽章会一起刷新。
    ''' </summary>
    Public Sub UploadSkin(SkinInfo As McSkinInfo)
        If Not SkinInfo.IsVaild Then Return
        RunInNewThread(
        Sub()
            Do While True
                Dim RetryAgain As Boolean = False
                Try
                    Do While McLoginMsLoader.State = LoadState.Loading '等待登录结束
                        Thread.Sleep(10)
                    Loop
                    If McLoginMsLoader.State = LoadState.Failed Then Throw New Exception(ModLang.L("登录失败"), McLoginMsLoader.Error)
                    Dim AccessToken As String = Settings.Get(Of String)("CacheMsV2Access")
                    Dim Result As String = NetRequestByClientRetry("https://api.minecraftservices.com/minecraft/profile/skins", HttpMethod.Post,
                        Content:=New Net.Http.MultipartFormDataContent From {
                            {New Net.Http.StringContent(If(SkinInfo.IsSlim, "slim", "classic")), "variant"},
                            {New Net.Http.ByteArrayContent(FileUtils.ReadAsBytes(SkinInfo.LocalFile)), "file", PathUtils.GetLastPart(SkinInfo.LocalFile)}
                        },
                        Headers:={{"Authorization", "Bearer " & AccessToken}, {"Accept", "*/*"}, {"User-Agent", "MojangSharp/0.1"}})
                    If Result.Contains("request requires user authentication") Then
                        Hint(ModLang.L("正在重新登录，将在登录后自动更改皮肤……"))
                        McLoginMsLoader.Start(PageLoginMsSkin.GetLoginData(), IsForceRestart:=True)
                        RetryAgain = True
                    Else
                        Logger.Info($"皮肤修改返回值：{vbCrLf}{Result}")
                        Dim ResultJson As JObject = Result.DeserializeJson()
                        If ResultJson.ContainsKey("errorMessage") Then Throw New Exception(ResultJson("errorMessage").ToString)
                        For Each Skin As JObject In ResultJson("skins")
                            If Skin("state").ToString = "ACTIVE" Then
                                MySkin.ReloadCache(Skin("url"))
                                Return
                            End If
                        Next
                        Throw New Exception(ModLang.L("未知错误（") & Result & ModLang.L("）"))
                    End If
                Catch ex As Exception
                    If TypeOf ex Is HttpRequestCodeException Then
                        Dim RequestException As HttpRequestCodeException = CType(ex, HttpRequestCodeException)
                        Select Case RequestException.StatusCode
                            Case HttpStatusCode.BadRequest
                                Logger.Warn(ex, "更改皮肤时遭遇 400 错误")
                                If RequestException.Response?.Contains("""error""") Then
                                    Hint(ModLang.L("更改皮肤失败：") & RequestException.Response.DeserializeJson()("error").ToString, HintType.Red)
                                    Return
                                ElseIf RequestException.Response?.Contains("""errorMessage""") Then
                                    Hint(ModLang.L("更改皮肤失败：") & RequestException.Response.DeserializeJson()("errorMessage").ToString, HintType.Red)
                                    Return
                                End If
                            Case HttpStatusCode.Unauthorized
                                Logger.Warn(ex, "更改皮肤时遭遇 401 错误")
                                Hint(ModLang.L("正在重新登录，将在登录后自动更改皮肤……"))
                                McLoginMsLoader.Start(PageLoginMsSkin.GetLoginData(), IsForceRestart:=True)
                                RetryAgain = True
                        End Select
                    ElseIf ex.IsBadNetwork Then
                        Hint(ModLang.L("更改皮肤失败：连接 Mojang 服务器超时，请稍后再试，或使用 VPN 改善网络环境"), HintType.Red)
                        Return
                    Else
                        Logger.Error(ex, "更改皮肤失败", LogBehavior.Toast)
                        Return
                    End If
                End Try
                If Not RetryAgain Then Exit Do
            Loop
        End Sub, "Ms Skin Upload")
    End Sub

End Module
