Public Class PageInstanceLeft
    Implements IRefreshable

    ''' <summary>
    ''' 当前显示设置的 MC 版本。
    ''' </summary>
    Public Shared Instance As McInstance = Nothing

    Private Sub PageInstanceLeft_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PageSetupUI.HiddenRefresh()
    End Sub

    Public Sub RefreshModDisabled() Handles Me.Loaded
        If Instance IsNot Nothing AndAlso Instance.Modable Then
            ItemMod.Visibility = Visibility.Visible
            ItemModDisabled.Visibility = Visibility.Collapsed
        Else
            ItemMod.Visibility = Visibility.Collapsed
            ItemModDisabled.Visibility = Visibility.Visible
        End If
    End Sub

#Region "页面切换"

    ''' <summary>
    ''' 当前页面的编号。从 0 开始计算。
    ''' </summary>
    Public PageID As FormMain.PageSubType = FormMain.PageSubType.Default

    ''' <summary>
    ''' 勾选事件改变页面。
    ''' </summary>
    Private Sub PageCheck(sender As FrameworkElement, e As RouteEventArgs) Handles ItemOverall.Check, ItemMod.Check, ItemModDisabled.Check, ItemSetup.Check, ItemModify.Check, ItemExport.Check, ItemSaves.Check, ItemScreenshot.Check, ItemServer.Check, ItemJarInJar.Check, ItemCompResource.Check, ItemSavesDatapack.Check, ItemDiagnostics.Check, ItemOptions.Check, ItemHistory.Check, ItemResourcePack.Check, ItemShader.Check, ItemMap.Check
        '尚未初始化控件属性时，sender.Tag 为 Nothing，会导致切换到页面 0
        '若使用 IsLoaded，则会导致模拟点击不被执行（模拟点击切换页面时，控件的 IsLoaded 为 False）
        If sender.Tag IsNot Nothing Then PageChange(Val(sender.Tag))
    End Sub

    Public Function PageGet(Optional ID As FormMain.PageSubType = -1)
        If ID = -1 Then ID = PageID
        Select Case ID
            Case FormMain.PageSubType.InstanceOverall
                If FrmInstanceOverall Is Nothing Then FrmInstanceOverall = New PageInstanceOverall
                Return FrmInstanceOverall
            Case FormMain.PageSubType.InstanceMod
                If FrmInstanceMod Is Nothing Then FrmInstanceMod = New PageInstanceMod
                Return FrmInstanceMod
            Case FormMain.PageSubType.InstanceModDisabled
                If FrmInstanceModDisabled Is Nothing Then FrmInstanceModDisabled = New PageInstanceModDisabled
                Return FrmInstanceModDisabled
            Case FormMain.PageSubType.InstanceSetup
                If FrmInstanceSetup Is Nothing Then FrmInstanceSetup = New PageInstanceSetup
                Return FrmInstanceSetup
            Case FormMain.PageSubType.InstanceExport
                If FrmInstanceExport Is Nothing Then FrmInstanceExport = New PageInstanceExport
                Return FrmInstanceExport
            Case FormMain.PageSubType.InstanceModify
                If FrmDownloadModify Is Nothing Then FrmDownloadModify = New PageDownloadModify
                Return FrmDownloadModify
            Case FormMain.PageSubType.InstanceSaves
                If FrmInstanceSaves Is Nothing Then FrmInstanceSaves = New PageInstanceSaves
                Return FrmInstanceSaves
            Case FormMain.PageSubType.InstanceScreenshot
                If FrmInstanceScreenshot Is Nothing Then FrmInstanceScreenshot = New PageInstanceScreenshot
                Return FrmInstanceScreenshot
            Case FormMain.PageSubType.InstanceServer
                If FrmInstanceServer Is Nothing Then FrmInstanceServer = New PageInstanceServer
                Return FrmInstanceServer
            Case FormMain.PageSubType.InstanceJarInJar
                If FrmInstanceJarInJar Is Nothing Then FrmInstanceJarInJar = New PageInstanceJarInJar
                Return FrmInstanceJarInJar
            Case FormMain.PageSubType.InstanceCompResource
                If FrmInstanceCompResource Is Nothing Then FrmInstanceCompResource = New PageInstanceCompResource
                Return FrmInstanceCompResource
            Case FormMain.PageSubType.InstanceSavesDatapack
                If FrmInstanceSavesDatapack Is Nothing Then FrmInstanceSavesDatapack = New PageInstanceSavesDatapack
                Return FrmInstanceSavesDatapack
            Case FormMain.PageSubType.InstanceDiagnostics
                If FrmInstanceDiagnostics Is Nothing Then FrmInstanceDiagnostics = New PageInstanceDiagnostics
                Return FrmInstanceDiagnostics
            Case FormMain.PageSubType.InstanceOptions
                If FrmInstanceOptions Is Nothing Then FrmInstanceOptions = New PageInstanceOptions
                Return FrmInstanceOptions
            Case FormMain.PageSubType.InstanceHistory
                If FrmInstanceHistory Is Nothing Then FrmInstanceHistory = New PageInstanceHistory
                Return FrmInstanceHistory
            Case FormMain.PageSubType.InstanceResourcePack
                If FrmInstanceResourcePack Is Nothing Then FrmInstanceResourcePack = New PageInstanceResourcePack
                Return FrmInstanceResourcePack
            Case FormMain.PageSubType.InstanceShader
                If FrmInstanceShader Is Nothing Then FrmInstanceShader = New PageInstanceShader
                Return FrmInstanceShader
            Case FormMain.PageSubType.InstanceMap
                If FrmInstanceMap Is Nothing Then FrmInstanceMap = New PageInstanceMap
                Return FrmInstanceMap
            Case Else
                Throw New Exception(ModLang.L("未知的版本设置子页面种类：") & ID)
        End Select
    End Function

    ''' <summary>
    ''' 切换现有页面。
    ''' </summary>
    Public Sub PageChange(ID As FormMain.PageSubType)
        If PageID = ID Then Return
        If Instance IsNot Nothing AndAlso McInstanceLock.IsLocked(Instance) Then
            Hint(ModLang.L("该版本正在被修改，请稍后再切换页面！"), HintType.Red)
            Return
        End If
        AniControlEnabled += 1
        Try
            PageChangeRun(PageGet(ID))
            PageID = ID
        Catch ex As Exception
            Logger.Error(ex, $"切换分页面失败（ID {ID}）")
        Finally
            AniControlEnabled -= 1
        End Try
    End Sub
    Private Shared Sub PageChangeRun(Target As MyPageRight)
        AniStop("FrmMain PageChangeRight") '停止主页面的右页面切换动画，防止它与本动画一起触发多次 PageOnEnter
        If Target.Parent IsNot Nothing Then Target.SetValue(ContentPresenter.ContentProperty, Nothing)
        FrmMain.PageRight = Target
        CType(FrmMain.PanMainRight.Child, MyPageRight).PageOnExit()
        AniStart({
            AaCode(Sub()
                       CType(FrmMain.PanMainRight.Child, MyPageRight).PageOnForceExit()
                       FrmMain.PanMainRight.Child = FrmMain.PageRight
                       FrmMain.PageRight.Opacity = 0
                   End Sub, 130),
            AaCode(Sub()
                       '延迟触发页面通用动画，以使得在 Loaded 事件中加载的控件得以处理
                       FrmMain.PageRight.Opacity = 1
                       FrmMain.PageRight.PageOnEnter()
                   End Sub, 30, True)
        }, "PageLeft PageChange")
    End Sub

#End Region

    Public Sub Refresh_Click(sender As Object, e As EventArgs) '由边栏按钮匿名调用
        Refresh(Val(sender.Tag))
    End Sub
    Public Sub Refresh() Implements IRefreshable.Refresh
        Refresh(FrmMain.PageCurrentSub)
    End Sub
    Public Sub Refresh(SubType As FormMain.PageSubType)
        If Instance IsNot Nothing AndAlso McInstanceLock.IsLocked(Instance) Then
            Hint(ModLang.L("该版本正在被修改，请稍后再刷新！"), HintType.Red)
            Return
        End If
        Select Case SubType
            Case FormMain.PageSubType.InstanceMod
                PageInstanceMod.Refresh()
                ItemMod.Checked = True
            Case FormMain.PageSubType.InstanceExport
                FrmInstanceExport?.RefreshAll()
                ItemExport.Checked = True
            Case FormMain.PageSubType.InstanceModify
                FrmDownloadModify?.PageLoaderRestart()
                ItemModify.Checked = True
            Case FormMain.PageSubType.InstanceSaves
                FrmInstanceSaves?.Refresh()
                ItemSaves.Checked = True
            Case FormMain.PageSubType.InstanceScreenshot
                FrmInstanceScreenshot?.Reload()
                ItemScreenshot.Checked = True
            Case FormMain.PageSubType.InstanceServer
                FrmInstanceServer?.Reload()
                ItemServer.Checked = True
            Case FormMain.PageSubType.InstanceJarInJar
                FrmInstanceJarInJar?.Reload()
                ItemJarInJar.Checked = True
            Case FormMain.PageSubType.InstanceCompResource
                FrmInstanceCompResource?.Reload()
                ItemCompResource.Checked = True
            Case FormMain.PageSubType.InstanceSavesDatapack
                FrmInstanceSavesDatapack?.Reload()
                ItemSavesDatapack.Checked = True
            Case FormMain.PageSubType.InstanceDiagnostics
                FrmInstanceDiagnostics?.Reload()
                ItemDiagnostics.Checked = True
            Case FormMain.PageSubType.InstanceOptions
                FrmInstanceOptions?.Reload()
                ItemOptions.Checked = True
            Case FormMain.PageSubType.InstanceHistory
                FrmInstanceHistory?.Reload()
                ItemHistory.Checked = True
            Case FormMain.PageSubType.InstanceResourcePack
                FrmInstanceResourcePack?.Reload()
                ItemResourcePack.Checked = True
            Case FormMain.PageSubType.InstanceShader
                FrmInstanceShader?.Reload()
                ItemShader.Checked = True
            Case FormMain.PageSubType.InstanceMap
                FrmInstanceMap?.Reload()
                ItemMap.Checked = True
        End Select
    End Sub

    Private Sub ItemInstallNew_Click(sender As Object, e As MouseButtonEventArgs) Handles ItemInstallNew.Click
        FrmMain.PageChange(New FormMain.PageStackData With {.Page = FormMain.PageType.Download}, FormMain.PageSubType.DownloadInstall)
    End Sub

    ''' <summary>
    ''' 从另一个 .minecraft 目录导入所有版本（对齐 LumilioCL 游戏库的「导入其他启动器的游戏」）。
    ''' </summary>
    Private Sub ItemImportInstance_Click(sender As Object, e As MouseButtonEventArgs) Handles ItemImportInstance.Click
        Dim Result As List(Of String) = Dialogs.SelectFolder(ModLang.L("选择要导入的 .minecraft 文件夹"), False)
        If Result Is Nothing OrElse Not Result.Any() Then Return
        Dim Source As String = Result.First()
        If Not DirectoryUtils.Exists(PathUtils.AddSlashSuffix(Source) & "versions\") Then
            Hint(ModLang.L("所选文件夹下没有 versions 目录，无法导入。"), HintType.Red)
            Return
        End If
        Hint(ModLang.L("正在后台导入实例…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Try
                Dim Count As Integer = ModInstanceTools.ImportVersionsFrom(Source)
                RunInUi(
                Sub()
                    If Count = 0 Then
                        Hint(ModLang.L("没有新的实例可导入（同名实例会被跳过）。"), HintType.Blue)
                    Else
                        Hint(ModLang.L("已导入 ") & Count & ModLang.L(" 个实例，返回实例列表即可看到。"), HintType.Green)
                    End If
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "导入实例失败")
                RunInUi(Sub() Hint(ModLang.L("导入失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Import Instance", ThreadPriority.BelowNormal)
    End Sub

    ''' <summary>复制当前实例为独立副本（对齐 LumilioCL 游戏页的「复制游戏」）。</summary>
    Private Sub ItemCopyInstance_Click(sender As Object, e As MouseButtonEventArgs) Handles ItemCopyInstance.Click
        If Instance Is Nothing Then Return
        Dim Suggested As String = Instance.Name & " - 副本"
        Dim NewName As String = MyMsgBoxInput(ModLang.L("复制实例"),
            ModLang.L("新实例的名称（会复制 Mod、配置与存档）："), Suggested, hintText:=Suggested)
        If String.IsNullOrWhiteSpace(NewName) Then Return
        If NewName.Trim() = Instance.Name Then
            Hint(ModLang.L("新实例名称不能和当前实例相同。"), HintType.Red)
            Return
        End If
        Dim Source As McInstance = Instance
        Hint(ModLang.L("正在后台复制实例…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Try
                Dim Target As String = ModInstanceTools.CopyInstance(Source, NewName.Trim())
                RunInUi(Sub() Hint(ModLang.L("实例已复制为：") & PathUtils.GetLastPart(Target) & ModLang.L("，返回实例列表即可看到。"), HintType.Green))
            Catch ex As Exception
                Logger.Error(ex, "复制实例失败")
                RunInUi(Sub() Hint(ModLang.L("复制实例失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Copy Instance", ThreadPriority.BelowNormal)
    End Sub

    ''' <summary>完整备份 / 从备份恢复（对齐 LumilioCL 的「完整备份」与「从备份恢复」）。</summary>
    Private Sub ItemBackupInstance_Click(sender As Object, e As MouseButtonEventArgs) Handles ItemBackupInstance.Click
        If Instance Is Nothing Then Return
        Dim Choice As Integer = MyMsgBox(
            ModLang.L("要对这个实例做完整备份（打包成 zip），还是从备份恢复成一个新实例？") & vbCrLf & vbCrLf &
            ModLang.L("完整备份包含 Mod、配置与存档，不包含日志与截图。"),
            ModLang.L("备份与恢复"), ModLang.L("完整备份"), ModLang.L("从备份恢复"), IsWarn:=False)
        If Choice = 1 Then
            BackupInstance()
        ElseIf Choice = 2 Then
            RestoreInstance()
        End If
    End Sub

    Private Sub BackupInstance()
        Dim Target As String = Dialogs.SaveFile(ModLang.L("保存实例备份"),
            Instance.Name & "-" & Now.ToString("yyyyMMdd-HHmmss") & ".zip",
            filter:={("zip", ModLang.L("Zip 压缩包"))})
        If String.IsNullOrEmpty(Target) Then Return
        Dim Source As McInstance = Instance
        Hint(ModLang.L("正在后台打包实例…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Try
                ModInstanceTools.BackupInstance(Source, Target)
                RunInUi(
                Sub()
                    Hint(ModLang.L("实例已备份：") & IO.Path.GetFileName(Target), HintType.Green)
                    OpenExplorer(IO.Path.GetDirectoryName(Target))
                End Sub)
            Catch ex As Exception
                Logger.Error(ex, "备份实例失败")
                RunInUi(Sub() Hint(ModLang.L("备份失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Backup Instance", ThreadPriority.BelowNormal)
    End Sub

    Private Sub RestoreInstance()
        Dim Files As List(Of String) = Dialogs.SelectFile(ModLang.L("选择实例备份"), False, filter:="zip|Zip 压缩包")
        If Files Is Nothing OrElse Not Files.Any() Then Return
        Dim ZipPath As String = Files.First()
        Dim Suggested As String = IO.Path.GetFileNameWithoutExtension(ZipPath)
        Dim NewName As String = MyMsgBoxInput(ModLang.L("从备份恢复"),
            ModLang.L("恢复成的新实例名称（不会覆盖已有实例）："), Suggested, hintText:=Suggested)
        If String.IsNullOrWhiteSpace(NewName) Then Return
        Hint(ModLang.L("正在后台恢复实例…"), HintType.Blue, False)
        RunInNewThread(
        Sub()
            Try
                Dim Target As String = ModInstanceTools.RestoreBackup(ZipPath, NewName.Trim())
                RunInUi(Sub() Hint(ModLang.L("已恢复为实例：") & PathUtils.GetLastPart(Target) & ModLang.L("，返回实例列表即可看到。"), HintType.Green))
            Catch ex As Exception
                Logger.Error(ex, "从备份恢复实例失败")
                RunInUi(Sub() Hint(ModLang.L("恢复失败：") & ex.Message, HintType.Red))
            End Try
        End Sub, "Restore Instance", ThreadPriority.BelowNormal)
    End Sub

    Public Sub Reset(sender As Object, e As EventArgs)
        If MyMsgBox(ModLang.L("是否要初始化该版本的版本独立设置？该操作不可撤销。"), ModLang.L("初始化确认"),, ModLang.L("取消"), IsWarn:=True) = 1 Then
            If IsNothing(FrmInstanceSetup) Then FrmInstanceSetup = New PageInstanceSetup
            FrmInstanceSetup.Reset()
            ItemSetup.Checked = True
        End If
    End Sub

    '加载当前版本将要选择的 Java
    Public Shared CurrentJavaWorker As New RedoableWorker(Of Java)(Function(c, p) SelectOrDownloadJava(PageInstanceLeft.Instance, False, c, p))
    Public Shared Sub ReloadCurrentJava() Handles Me.Loaded
        If AniControlEnabled <> 0 Then Return
        If PageInstanceLeft.Instance Is Nothing Then Return
        CurrentJavaWorker.Start()
    End Sub

End Class
