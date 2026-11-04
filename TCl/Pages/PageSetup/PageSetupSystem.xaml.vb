Imports Microsoft.Win32

Public Class PageSetupSystem


    Private Sub PageSetupSystem_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded

        '重复加载部分
        PanBack.ScrollToHome()

        If BuildType = BuildTypes.Release Then
            PanDonate.Visibility = Visibility.Collapsed
        Else
            PanDonate.Visibility = Visibility.Visible
        End If

        '非重复加载部分
        Static IsLoaded As Boolean = False
        If IsLoaded Then Return
        IsLoaded = True

        AniControlEnabled += 1
        Reload()
        SliderLoad()
        AniControlEnabled -= 1

    End Sub
    Public Sub Reload()
        SettingService.RefreshSettings(Me)
    End Sub
    Public Sub Reset()
        Try
            SettingService.ResetSettings(Me)
            Logger.Info("已初始化启动器页设置")
            Hint(ModLang.L("已初始化启动器页设置！"), HintType.Green, False)
        Catch ex As Exception
            Logger.Error(ex, "初始化启动器页设置失败", LogBehavior.Alert)
        End Try
        Reload()
    End Sub

    '滑动条
    Private Sub SliderLoad()
        SliderDownloadThread.GetHintText = Function(v) v + 1
        SliderDownloadSpeed.GetHintText =
        Function(v)
            Select Case v
                Case Is <= 14
                    Return (v + 1) * 0.1 & " M/s"
                Case Is <= 31
                    Return (v - 11) * 0.5 & " M/s"
                Case Is <= 41
                    Return (v - 21) & " M/s"
                Case Else
                    Return "无限制"
            End Select
        End Function
        SliderDebugAnim.GetHintText = Function(v) If(v > 29, ModLang.L("关闭"), (v / 10 + 0.1) & "x")
    End Sub
    Private Sub SliderDownloadThread_PreviewChange(sender As Object, e As RouteEventArgs) Handles SliderDownloadThread.PreviewChange
        If SliderDownloadThread.Value < 100 Then Return
        If Not Settings.Get(Of Boolean)("HintDownloadThread") Then
            Settings.Set("HintDownloadThread", True)
            MyMsgBox(ModLang.L("如果设置过多的下载线程，可能会导致下载时出现非常严重的卡顿。") & vbCrLf &
                     ModLang.L("一般设置 64 线程即可满足大多数下载需求，除非你知道你在干什么，否则不建议设置更多的线程数！"), ModLang.L("警告"), ModLang.L("我知道了"), IsWarn:=True)
        End If
    End Sub

    '识别码/番茄码替代入口
    Private Sub BtnSystemIdentify_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSystemIdentify.Click
        PageOtherAbout.CopyIdentify()
    End Sub
    Private Sub BtnSystemUnlock_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSystemUnlock.Click
        InputTomatoCode(False)
    End Sub

    '调试模式
    Private Sub CheckDebugMode_Change() Handles CheckDebugMode.Change
        If AniControlEnabled = 0 Then Hint(ModLang.L("部分调试信息将在刷新或启动器重启后切换显示！"),, False)
    End Sub

    '翻译文件：导出 / 重载（i18n.json，用户可编辑，改后即时生效）
    Private Sub BtnExportI18n_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnExportI18n.Click
        Try
            Dim Path_ = ModLang.ExportBuiltinTranslations()
            If Path_ Is Nothing Then
                Hint(ModLang.L("导出翻译文件失败，请查看日志。"), HintType.Red)
            Else
                Hint(ModLang.L("已导出翻译文件，可用文本编辑器修改后点「重载」：") & Path_, HintType.Green)
                OpenExplorer(Path_)
            End If
        Catch ex As Exception
            Logger.Error(ex, "导出翻译文件失败", LogBehavior.Alert)
        End Try
    End Sub
    Private Sub BtnReloadI18n_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnReloadI18n.Click
        Try
            If ModLang.ReloadTranslations() Then
                Hint(ModLang.L("已重新加载翻译文件，界面已即时套用。"), HintType.Green)
            Else
                Hint(ModLang.L("未找到 i18n.json（请先点「导出」生成），或文件解析失败。"), HintType.Blue)
            End If
        Catch ex As Exception
            Logger.Error(ex, "重载翻译文件失败", LogBehavior.Alert)
        End Try
    End Sub
    '用系统默认文本编辑器打开 i18n.json（可直接编辑，改完点「重载」即时生效）
    Private Sub BtnOpenI18n_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnOpenI18n.Click
        Try
            Dim Path_ = ModLang.EnsureI18nFile()
            If Path_ Is Nothing Then
                Hint(ModLang.L("找不到 i18n.json，请点「导出」先生成。"), HintType.Red)
                Return
            End If
            ModBase.StartProcess(Path_)
        Catch ex As Exception
            Logger.Error(ex, "打开翻译文件失败", LogBehavior.Alert)
        End Try
    End Sub

    Private Sub ComboSystemActivity_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles ComboSystemActivity.SelectionChanged
        If AniControlEnabled <> 0 Then Return
        If ComboSystemActivity.SelectedIndex <> 2 Then Return
        If MyMsgBox(ModLang.L("若选择此项，即使在将来出现严重问题时，你也无法获取相关通知。" & vbCrLf & ModLang.L("例如，如果发现某个版本游戏存在严重 Bug，你可能就会因为无法得到通知而导致无法预知的后果。")) & vbCrLf & vbCrLf &
                    ModLang.L("一般选择 仅在有重要通知时显示公告 就可以让你尽量不受打扰了。") & vbCrLf &
                    ModLang.L("除非你在制作服务器整合包，或时常手动更新启动器，否则极度不推荐选择此项！"), ModLang.L("警告"), ModLang.L("我知道我在做什么"), ModLang.L("取消"), IsWarn:=True) = 2 Then
            If e.RemovedItems.Count > 0 Then ComboSystemActivity.SelectedItem = e.RemovedItems(0)
        End If
    End Sub

#Region "导出 / 导入全部数据（第 129 轮升级：并入实例独立设置与合集，导入带加载动画与即时刷新）"

    ''' <summary>导入进行中标记，防止重复点击。</summary>
    Private IsImporting As Boolean = False

    Private Sub BtnSystemSettingExp_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSystemSettingExp.Click
        Try
            '读取注册表设置的原始值（含加密项与识别码，导入时按原样恢复）
            Dim RegistryData As New JObject
            For Each Entry In Settings.Entries.Values
                If Entry.Source <> Settings.Sources.Registry Then Continue For
                Dim Raw As Object = Nothing
                Try
                    Raw = RegistryUtils.TryRead($"HKEY_CURRENT_USER\Software\{RegFolder}", Entry.Key, Nothing)
                Catch ex As Exception
                    Logger.Warn(ex, $"读取注册表设置 {Entry.Key} 失败")
                End Try
                If Raw IsNot Nothing Then RegistryData(Entry.Key) = Raw.ToString
            Next
            '读取全局 Setup.ini 的原始值
            Dim IniData As New JObject
            For Each Entry In Settings.Entries.Values
                If Entry.Source <> Settings.Sources.Normal Then Continue For
                Dim Raw = ReadIni("Setup", Entry.Key, Nothing)
                If Raw IsNot Nothing Then IniData(Entry.Key) = Raw
            Next
            '读取每个实例的独立设置（{实例}\TCL\Setup.ini），键值按原始行拆开
            Dim InstanceData As New JArray
            Dim SeenPaths As New HashSet(Of String)(StringComparer.OrdinalIgnoreCase)
            Try
                If ModMinecraft.McInstanceList IsNot Nothing Then
                    For Each Pair In ModMinecraft.McInstanceList
                        For Each Instance In Pair.Value
                            Dim PathVersion = If(Instance?.PathVersion, "")
                            If String.IsNullOrEmpty(PathVersion) OrElse SeenPaths.Contains(PathVersion) Then Continue For
                            SeenPaths.Add(PathVersion)
                            Dim IniPath = PathVersion & "TCL\Setup.ini"
                            If Not FileUtils.Exists(IniPath) Then Continue For
                            Dim Obj As New JObject
                            For Each Line In FileUtils.ReadAsLines(IniPath, True)
                                Dim Index = Line.IndexOfF(":")
                                If Index > 0 Then Obj(Line.Substring(0, Index)) = Line.Substring(Index + 1)
                            Next
                            If Obj.Count > 0 Then
                                Dim Item As New JObject
                                Item("Path") = PathVersion
                                Item("Ini") = Obj
                                InstanceData.Add(Item)
                            End If
                        Next
                    Next
                End If
            Catch ex As Exception
                Logger.Warn(ex, "导出实例独立设置失败（跳过）")
            End Try
            '读取合集清单（Collections.json 原文）
            Dim CollectionsText As String = Nothing
            Try
                If FileUtils.Exists(ModInstanceCollections.StorePath) Then CollectionsText = IO.File.ReadAllText(ModInstanceCollections.StorePath, Encoding.UTF8)
            Catch ex As Exception
                Logger.Warn(ex, "导出合集清单失败（跳过）")
            End Try
            Dim ExportData As New JObject()
            ExportData("Version") = VersionCode
            ExportData("Date") = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")
            ExportData("Registry") = RegistryData
            ExportData("Ini") = IniData
            ExportData("Instances") = InstanceData
            If CollectionsText IsNot Nothing Then ExportData("Collections") = CollectionsText
            Dim Dialog As New SaveFileDialog With {
                .Title = ModLang.L("导出 TCL 全部数据"),
                .Filter = "TCL 设置文件|*.tcl",
                .FileName = $"TCL-Data-{DateTime.Now:yyyyMMdd-HHmmss}.tcl"
            }
            If Not CBool(Dialog.ShowDialog()) Then Return
            FileUtils.Write(Dialog.FileName, ExportData.ToString(Newtonsoft.Json.Formatting.None))
            Hint(ModLang.L("已导出全部数据：全局设置、") & InstanceData.Count & ModLang.L(" 个实例的独立设置与合集清单（不包含背景图、皮肤等媒体文件）。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "导出数据失败")
            MyMsgBox(ModLang.L("导出数据失败：") & ex.GetDisplay(True), ModLang.L("导出数据失败"), IsWarn:=True)
        End Try
    End Sub

    Private Sub BtnSystemSettingImp_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnSystemSettingImp.Click
        If IsImporting Then Return
        Dim Dialog As New OpenFileDialog With {
            .Title = ModLang.L("导入 TCL 全部数据"),
            .Filter = "TCL 设置文件|*.tcl"
        }
        If Not CBool(Dialog.ShowDialog()) Then Return
        Dim ImportData As JObject
        Try
            ImportData = FileUtils.ReadAsString(Dialog.FileName).DeserializeJson()
        Catch ex As Exception
            ImportData = Nothing
            Logger.Warn(ex, "导入文件解析失败")
        End Try
        If ImportData Is Nothing OrElse (ImportData("Registry") Is Nothing AndAlso ImportData("Ini") Is Nothing AndAlso ImportData("Instances") Is Nothing) Then
            MyMsgBox(ModLang.L("导入失败：文件并非有效的 TCL 数据导出文件。"), ModLang.L("导入数据失败"), IsWarn:=True)
            Return
        End If
        If MyMsgBox(ModLang.L("导入将覆盖当前全部数据，且不可撤销：") & vbCrLf & ModLang.L("· 全局设置（含登录缓存、皮肤、网络代理等）") & vbCrLf & ModLang.L("· 各实例的独立设置（仅路径仍存在的实例会被恢复）") & vbCrLf & ModLang.L("· 合集清单") & vbCrLf & vbCrLf & ModLang.L("是否继续？"), ModLang.L("导入数据"), ModLang.L("导入"), ModLang.L("取消"), IsWarn:=True) = 2 Then Return
        '进入导入态：加载动画 + 禁用按钮，恢复在后台线程执行
        IsImporting = True
        BtnSystemSettingExp.IsEnabled = False
        BtnSystemSettingImp.IsEnabled = False
        LoadingImport.Visibility = Visibility.Visible
        RunInThread(Sub()
                        Dim Summary As JObject = Nothing
                        Dim FailEx As Exception = Nothing
                        Try
                            Summary = ImportRun(ImportData)
                        Catch ex As Exception
                            FailEx = ex
                            Logger.Error(ex, "导入数据失败")
                        End Try
                        RunInUi(Sub()
                                    IsImporting = False
                                    LoadingImport.Visibility = Visibility.Collapsed
                                    BtnSystemSettingExp.IsEnabled = True
                                    BtnSystemSettingImp.IsEnabled = True
                                    If FailEx IsNot Nothing Then
                                        MyMsgBox(ModLang.L("导入数据失败：") & FailEx.GetDisplay(True), ModLang.L("导入数据失败"), IsWarn:=True)
                                        Return
                                    End If
                                    RefreshAfterImport()
                                    MyMsgBox(ModLang.L("数据导入完成：恢复设置 ") & CInt(Summary("Count")) & ModLang.L(" 项、实例独立设置 ") & CInt(Summary("Instances")) & ModLang.L(" 个，合集清单") & If(CBool(Summary("Collections")), ModLang.L("已恢复"), ModLang.L("无变化")) & ModLang.L("。") & vbCrLf & ModLang.L("大部分设置已即时生效；少数（如缓存文件夹路径）将在重启 TCL 后完全生效。"), ModLang.L("导入数据"))
                                End Sub)
                    End Sub)
    End Sub

    ''' <summary>
    ''' 在后台线程执行数据恢复：注册表 / 全局 ini / 实例独立设置 / 合集。
    ''' 返回汇报对象（Count / Instances / Collections）；致命错误时抛出异常。
    ''' </summary>
    Private Function ImportRun(Data As JObject) As JObject
        Dim Count As Integer = 0
        '注册表设置（原值恢复，加密项依赖随之恢复的识别码解密）
        Dim RegObj = TryCast(Data("Registry"), JObject)
        If RegObj IsNot Nothing Then
            For Each Pair As JProperty In RegObj.Properties
                Dim Entry As Settings.Setting = Nothing
                If Settings.Entries.TryGetValue(Pair.Name, Entry) AndAlso Entry.Source = Settings.Sources.Registry Then
                    RegistryUtils.Write($"HKEY_CURRENT_USER\Software\{RegFolder}", Entry.Key, Pair.Value.ToString)
                    Count += 1
                End If
            Next
        End If
        '全局 Setup.ini
        Dim IniObj = TryCast(Data("Ini"), JObject)
        If IniObj IsNot Nothing Then
            For Each Pair As JProperty In IniObj.Properties
                Dim Entry As Settings.Setting = Nothing
                If Settings.Entries.TryGetValue(Pair.Name, Entry) AndAlso Entry.Source = Settings.Sources.Normal Then
                    WriteIni("Setup", Entry.Key, Pair.Value.ToString)
                    Count += 1
                End If
            Next
        End If
        '实例独立设置：仅当实例路径仍存在时恢复
        Dim InstanceRestored As Integer = 0
        Dim Instances = TryCast(Data("Instances"), JArray)
        If Instances IsNot Nothing Then
            For Each Item As JToken In Instances
                Try
                    Dim PathVersion = Item("Path")?.ToString()
                    Dim OneIni = TryCast(Item("Ini"), JObject)
                    If String.IsNullOrEmpty(PathVersion) OrElse OneIni Is Nothing Then Continue For
                    If Not PathVersion.EndsWithF("\", True) Then PathVersion &= "\"
                    If Not DirectoryUtils.Exists(PathVersion) Then Continue For
                    For Each Pair As JProperty In OneIni.Properties
                        WriteIni(PathVersion & "TCL\Setup.ini", Pair.Name, Pair.Value.ToString)
                    Next
                    IniClearCache(PathVersion & "TCL\Setup.ini")
                    InstanceRestored += 1
                Catch ex As Exception
                    Logger.Warn(ex, "恢复实例独立设置失败（跳过）")
                End Try
            Next
        End If
        '合集清单
        Dim HadCollections As Boolean = False
        If Data("Collections") IsNot Nothing AndAlso Data("Collections").Type <> JTokenType.Null Then
            Try
                Dim Text = Data("Collections").ToString()
                If Text.TrimStart().StartsWithF("{") Then
                    DirectoryUtils.Create(IO.Path.GetDirectoryName(ModInstanceCollections.StorePath))
                    IO.File.WriteAllText(ModInstanceCollections.StorePath, Text, Encoding.UTF8)
                    HadCollections = True
                End If
            Catch ex As Exception
                Logger.Warn(ex, "恢复合集清单失败（跳过）")
            End Try
        End If
        '清除设置缓存，使下次读取时重新加载
        For Each Entry In Settings.Entries.Values
            Entry.ValueCache.Clear()
        Next
        Dim Summary As New JObject
        Summary("Count") = Count
        Summary("Instances") = InstanceRestored
        Summary("Collections") = HadCollections
        Return Summary
    End Function

    ''' <summary>导入完成后即时刷新界面：已打开的设置页与主题、代理、语言、背景等全局外观。</summary>
    Private Sub RefreshAfterImport()
        Try
            For Each Page In {FrmSetupLaunch, FrmSetupUI, FrmSetupSystem, FrmSetupLink, FrmSetupJava, FrmSetupLog, FrmSetupCache, FrmSetupNetwork, FrmSetupMore}
                If Page IsNot Nothing Then SettingService.RefreshSettings(Page)
            Next
        Catch ex As Exception
            Logger.Warn(ex, "导入后刷新设置页失败")
        End Try
        Try
            ThemeRefresh()
        Catch ex As Exception
            Logger.Warn(ex, "导入后刷新主题失败")
        End Try
        Try
            ModNet.UpdateHttpProxy()
        Catch ex As Exception
            Logger.Warn(ex, "导入后刷新代理失败")
        End Try
        Try
            ModBase.ApplyToolLanguage(Nothing)
        Catch ex As Exception
            Logger.Warn(ex, "导入后刷新语言失败")
        End Try
        Try
            FormMain.UpdateBackgroundAndTitleBar(Nothing)
        Catch ex As Exception
            Logger.Warn(ex, "导入后刷新背景失败")
        End Try
        Try
            PageSetupUI.HiddenRefresh()
        Catch ex As Exception
            Logger.Warn(ex, "导入后刷新页面隐藏状态失败")
        End Try
    End Sub

#End Region

#Region "社区 / 卸载（自「更多」页并入）"

    Private Const CommunityUrl As String = "https://qm.qq.com/q/fzoInJrrMW"

    Private Sub BtnJoinQQ_Click(sender As Object, e As MouseButtonEventArgs)
        Try
            OpenWebsite(CommunityUrl)
        Catch ex As Exception
            Logger.Error(ex, "打开社区 QQ 群链接失败")
            Hint(ModLang.L("打开链接失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    Private Sub BtnUninstall_Click(sender As Object, e As MouseButtonEventArgs)
        Dim Plan As UninstallPlan
        Try
            Plan = ModUninstall.BuildPlan()
        Catch ex As Exception
            Logger.Error(ex, "生成卸载清单失败")
            Hint(ModLang.L("生成卸载清单失败：") & ex.Message, HintType.Red)
            Return
        End Try
        If Not Plan.CanUninstall Then
            MyMsgBox(ModLang.L("没有找到可以删除的启动器文件，可能已经被清理过了。"), ModLang.L("卸载 TCraft Launcher"))
            Return
        End If
        Dim Choice As Integer = MyMsgBox(ModUninstall.Describe(Plan), ModLang.L("确认卸载 TCraft Launcher"), ModLang.L("取消"), ModLang.L("确认卸载"), IsWarn:=True)
        If Choice <> 2 Then Return
        Try
            ModUninstall.PrepareScript(Plan)
            ModUninstall.Run(Plan)
        Catch ex As Exception
            Logger.Error(ex, "启动卸载脚本失败")
            Hint(ModLang.L("启动卸载脚本失败：") & ex.Message, HintType.Red)
            Return
        End Try
        Hint(ModLang.L("正在卸载，程序即将退出……"), HintType.Green)
        IsProgramEnding = True
        Logger.Info("卸载脚本已启动，启动器即将退出")
        Try
            Logger.Instance.Flush()
        Catch ex As Exception
            Logger.Warn(ex, "退出前刷新日志失败")
        End Try
        RunInThread(Sub()
                        Thread.Sleep(400)
                        RunInUi(Sub() Environment.Exit(0))
                    End Sub)
    End Sub

#End Region

End Class

