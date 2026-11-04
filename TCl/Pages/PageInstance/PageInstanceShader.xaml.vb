''' <summary>
''' 实例的光影包管理页：列出 shaderpacks 文件夹里的内容，并通过 options.txt 的 shaderPack 行切换当前光影。
''' 同时检测 mods 里是否装有 Iris / Oculus / OptiFine，没装时提示去下载。
''' </summary>
Public Class PageInstanceShader

#Region "数据"

    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property

    ''' <summary>当前实例的 shaderpacks 文件夹（带结尾斜杠，不存在则创建）。</summary>
    Private ReadOnly Property PackFolder As String
        Get
            Return ModInstanceContent.GetPacksFolder(Instance, "shaderpacks")
        End Get
    End Property

    ''' <summary>当前列出的全部光影包（未筛选）。</summary>
    Private ReadOnly AllShaders As New List(Of ModInstanceContent.PackEntry)

    Private IsInit As Boolean = False

#End Region

#Region "初始化"

    Private Sub PageInstanceShader_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If Not IsInit Then
            IsInit = True
            InitText()
            InitEvents()
            InitDragDrop()
        End If
        Refresh()
    End Sub

    ''' <summary>固定文案统一在这里套翻译。</summary>
    Private Sub InitText()
        HintLoader.Text = ModLang.L("当前实例没有检测到光影加载器。光影需要 Iris 或 OptiFine，先安装其中一个再来这里。")
        BtnDownloadLoader.Text = ModLang.L("去下载光影加载器")
        BtnRefreshLoader.Text = ModLang.L("重新检测")
        BtnRefresh.Text = ModLang.L("刷新")
        BtnOpen.Text = ModLang.L("打开文件夹")
        BtnClear.Text = ModLang.L("取消当前光影")
        TxtSearch.HintText = ModLang.L("搜索光影包")
        CardShaders.Title = ModLang.L("光影包")
    End Sub

    Private Sub InitEvents()
        AddHandler TxtSearch.TextChanged, Sub(snd, args) Rebuild()
        AddHandler BtnRefresh.Click, Sub(snd, args) RefreshWithHint()
        AddHandler BtnOpen.Click, Sub(snd, args) OpenPackFolder()
        AddHandler BtnClear.Click, Sub(snd, args) ClearCurrentShader()
        AddHandler BtnDownloadLoader.Click, Sub(snd, args) GoDownloadLoader()
        AddHandler BtnRefreshLoader.Click, Sub(snd, args) RefreshWithHint()
    End Sub

    ''' <summary>整页接受 .zip 拖入安装。</summary>
    Private Sub InitDragDrop()
        For Each Target As UIElement In New UIElement() {Me, PanBack, PanMain, PanList, CardShaders}
            Target.AllowDrop = True
            AddHandler Target.DragOver, AddressOf HandleDragOver
            AddHandler Target.Drop, AddressOf HandleDrop
        Next
    End Sub

#End Region

#Region "刷新"

    ''' <summary>切换页面时由主控调用。</summary>
    Public Sub Reload()
        Refresh()
    End Sub

    ''' <summary>重新读取磁盘列表与光影加载器状态。</summary>
    Public Sub Refresh()
        Try
            AllShaders.Clear()
            PanList.Children.Clear()
            If Instance Is Nothing Then
                PanLoaderHint.Visibility = Visibility.Collapsed
                CardShaders.Title = ModLang.L("光影包")
                LabCurrent.Text = ""
                LabPath.Text = ""
                AddNotice(ModLang.L("请先选择一个 Minecraft 版本。"))
                Return
            End If
            '光影加载器检测
            Dim HasLoader As Boolean = ModInstanceContent.HasShaderLoader(Instance)
            PanLoaderHint.Visibility = If(HasLoader, Visibility.Collapsed, Visibility.Visible)
            '列表
            Dim Folder As String = PackFolder
            If Folder Is Nothing Then
                CardShaders.Title = ModLang.L("光影包")
                LabCurrent.Text = ""
                LabPath.Text = ""
                AddNotice(ModLang.L("无法定位这个实例的 shaderpacks 文件夹，请检查版本隔离设置。"))
                Return
            End If
            LabPath.Text = Folder
            AllShaders.AddRange(ModInstanceContent.ListPacks(Folder))
            Rebuild()
        Catch ex As Exception
            Logger.Error(ex, "刷新光影包列表失败")
        End Try
    End Sub

    Private Sub RefreshWithHint()
        Refresh()
        Hint(ModLang.L("光影包列表已刷新。"), HintType.Green)
    End Sub

    ''' <summary>按搜索词重建列表。</summary>
    Private Sub Rebuild()
        Try
            If PanList Is Nothing Then Return
            PanList.Children.Clear()
            If Instance Is Nothing Then Return
            Dim Current As String = ModInstanceContent.GetCurrentShader(Instance)
            If String.IsNullOrEmpty(Current) Then
                LabCurrent.Text = ModLang.L("当前没有启用光影。")
            Else
                LabCurrent.Text = ModLang.L("当前光影：") & ShaderDisplayName(Current) & ModLang.L("（重启游戏后生效）")
            End If
            Dim Keyword As String = If(TxtSearch Is Nothing, "", If(TxtSearch.Text, "")).Trim()
            If AllShaders.Count = 0 Then
                AddNotice(ModLang.L("还没有光影包。光影需要 Iris 或 OptiFine，装好后把 .zip 放进 shaderpacks 文件夹。"))
                CardShaders.Title = ModLang.L("光影包")
                Return
            End If
            Dim Shown As List(Of ModInstanceContent.PackEntry) = AllShaders
            If Keyword.Length > 0 Then
                Shown = AllShaders.Where(Function(One) One.Name.IndexOf(Keyword, StringComparison.OrdinalIgnoreCase) >= 0).ToList()
            End If
            If Shown.Count = 0 Then
                AddNotice(ModLang.L("没有匹配当前搜索的光影包。"))
                CardShaders.Title = ModLang.L("光影包（") & AllShaders.Count & ModLang.L(" 个）")
                Return
            End If
            For Each One In Shown
                PanList.Children.Add(BuildRow(One, IsCurrent(Current, One)))
            Next
            CardShaders.Title = ModLang.L("光影包（") & Shown.Count & ModLang.L(" 个）")
        Catch ex As Exception
            Logger.Error(ex, "重建光影包列表失败")
        End Try
    End Sub

    ''' <summary>取 options.txt 中记录的光影名（容错，非法字符时原样返回）。</summary>
    Private Function ShaderDisplayName(Current As String) As String
        Try
            Dim Result As String = IO.Path.GetFileName(Current)
            Return If(String.IsNullOrEmpty(Result), Current, Result)
        Catch ex As Exception
            Logger.Warn(ex, "解析光影包名称失败")
            Return Current
        End Try
    End Function

    ''' <summary>判断某个条目是否为当前使用的光影。</summary>
    Private Function IsCurrent(Current As String, Entry As ModInstanceContent.PackEntry) As Boolean
        If String.IsNullOrEmpty(Current) OrElse Entry Is Nothing Then Return False
        Try
            Dim CurrentName As String = IO.Path.GetFileNameWithoutExtension(ShaderDisplayName(Current))
            Return String.Equals(CurrentName, Entry.Name, StringComparison.OrdinalIgnoreCase)
        Catch ex As Exception
            Logger.Warn(ex, "比较光影包名称失败")
            Return False
        End Try
    End Function

    ''' <summary>在列表里放一条灰色说明文本。</summary>
    Private Sub AddNotice(Text As String)
        Dim Block As New TextBlock With {
            .Text = Text,
            .FontSize = 13,
            .Margin = New Thickness(4, 4, 0, 10),
            .Opacity = 0.6,
            .TextWrapping = TextWrapping.Wrap
        }
        Block.SetResourceReference(TextBlock.ForegroundProperty, "ColorBrush1")
        PanList.Children.Add(Block)
    End Sub

#End Region

#Region "列表项"

    ''' <summary>生成一行光影包。点击整行即设为当前光影。</summary>
    Private Function BuildRow(Entry As ModInstanceContent.PackEntry, InUse As Boolean) As UIElement
        Dim TypeText As String = If(Entry.IsFolder, ModLang.L("文件夹"), "zip")
        Dim InfoText As String = ModInstanceContent.FormatSize(Entry.SizeBytes) & " · " &
                                 Entry.Modified.ToString("yyyy'/'MM'/'dd HH':'mm") & " · " & TypeText
        If InUse Then InfoText = ModLang.L("（使用中） · ") & InfoText

        Dim Item As New MyListItem With {
            .Type = MyListItem.CheckType.Clickable,
            .Height = 46,
            .Title = Entry.Name,
            .Info = InfoText,
            .SvgIcon = "lucide/sparkles",
            .IsScaleAnimationEnabled = False,
            .Tag = Entry
        }
        Dim BtnShow As New MyIconButton With {
            .SvgIcon = "lucide/folder-open",
            .ToolTip = ModLang.L("在文件夹中显示")
        }
        AddHandler BtnShow.Click, Sub(snd, args) ShowInFolder(Entry)
        Dim BtnDelete As New MyIconButton With {
            .SvgIcon = "lucide/trash-2",
            .Theme = MyIconButton.Themes.Red,
            .ToolTip = ModLang.L("删除")
        }
        AddHandler BtnDelete.Click, Sub(snd, args) DeletePack(Entry)
        Item.Buttons = New MyIconButton() {BtnShow, BtnDelete}
        '使用中的条目：标题颜色走 ColorBrush2（与列表选中态一致）
        If InUse Then Item.Checked = True
        AddHandler Item.Click, Sub(snd, args) SetCurrent(Entry)
        Return Item
    End Function

#End Region

#Region "操作"

    ''' <summary>把某个光影设为当前光影，并写回 options.txt。</summary>
    Private Sub SetCurrent(Entry As ModInstanceContent.PackEntry)
        If Instance Is Nothing OrElse Entry Is Nothing Then Return
        Try
            Dim PackFileName As String = IO.Path.GetFileName(Entry.FullPath)
            If String.IsNullOrEmpty(PackFileName) Then Return
            ModInstanceContent.SetCurrentShader(Instance, PackFileName)
            Hint(ModLang.L("已切换为「") & Entry.Name & ModLang.L("」，重启游戏后生效。"), HintType.Green)
            Refresh()
        Catch ex As Exception
            Logger.Error(ex, "切换当前光影失败")
            Hint(ModLang.L("切换光影失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>取消当前光影。</summary>
    Private Sub ClearCurrentShader()
        If Instance Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        Try
            If String.IsNullOrEmpty(ModInstanceContent.GetCurrentShader(Instance)) Then
                Hint(ModLang.L("当前没有启用光影。"), HintType.Blue)
                Return
            End If
            ModInstanceContent.SetCurrentShader(Instance, "")
            Hint(ModLang.L("已取消当前光影，重启游戏后生效。"), HintType.Green)
            Refresh()
        Catch ex As Exception
            Logger.Error(ex, "取消当前光影失败")
            Hint(ModLang.L("取消光影失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>跳转到下载页（安装光影加载器）。</summary>
    Private Sub GoDownloadLoader()
        Try
            FrmMain.PageChange(FormMain.PageType.Download, FormMain.PageSubType.DownloadInstall)
        Catch ex As Exception
            Logger.Error(ex, "跳转下载页失败")
        End Try
    End Sub

    ''' <summary>在资源管理器中定位。</summary>
    Private Sub ShowInFolder(Entry As ModInstanceContent.PackEntry)
        Try
            If Entry Is Nothing OrElse String.IsNullOrEmpty(Entry.FullPath) Then Return
            If Entry.IsFolder Then
                If Not DirectoryUtils.Exists(Entry.FullPath) Then
                    Hint(ModLang.L("文件夹不存在！"), HintType.Red)
                    Return
                End If
                OpenExplorer(PathUtils.AddSlashSuffix(Entry.FullPath))
            Else
                If Not FileUtils.Exists(Entry.FullPath) Then
                    Hint(ModLang.L("文件不存在！"), HintType.Red)
                    Return
                End If
                OpenExplorer(Entry.FullPath)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "在文件夹中显示光影包失败")
        End Try
    End Sub

    ''' <summary>删除光影包（二次确认，移入回收站）。</summary>
    Private Sub DeletePack(Entry As ModInstanceContent.PackEntry)
        If Entry Is Nothing Then Return
        Try
            If MyMsgBox(ModLang.L("确定要删除光影包「") & Entry.Name & ModLang.L("」吗？") & vbCrLf &
                        ModLang.L("删除后会被移入回收站，可以自行恢复。"),
                        ModLang.L("删除光影包"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
            If Entry.IsFolder Then
                DirectoryUtils.Delete(Entry.FullPath, True)
            Else
                FileUtils.Delete(Entry.FullPath, True)
            End If
            Hint(ModLang.L("已删除：") & Entry.Name, HintType.Green)
            Refresh()
        Catch ex As Exception
            Logger.Error(ex, "删除光影包失败")
            Hint(ModLang.L("删除失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    Private Sub OpenPackFolder()
        Dim Folder As String = PackFolder
        If Folder Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        OpenExplorer(Folder)
    End Sub

#End Region

#Region "拖入安装"

    Private Sub HandleDragOver(sender As Object, e As DragEventArgs)
        Try
            If Instance Is Nothing Then Return
            If e.Data.GetDataPresent(DataFormats.FileDrop) Then
                Dim Files As String() = TryCast(e.Data.GetData(DataFormats.FileDrop), String())
                If Files IsNot Nothing AndAlso Files.Any(Function(One) One.EndsWith(".zip", StringComparison.OrdinalIgnoreCase)) Then
                    e.Effects = DragDropEffects.Copy
                    e.Handled = True
                    Return
                End If
            End If
        Catch ex As Exception
            Logger.Warn(ex, "判断拖入内容失败")
        End Try
    End Sub

    Private Sub HandleDrop(sender As Object, e As DragEventArgs)
        Try
            If Instance Is Nothing Then Return
            If Not e.Data.GetDataPresent(DataFormats.FileDrop) Then Return
            Dim Files As String() = TryCast(e.Data.GetData(DataFormats.FileDrop), String())
            If Files Is Nothing Then Return
            Dim Zips As List(Of String) = Files.Where(Function(One) One.EndsWith(".zip", StringComparison.OrdinalIgnoreCase)).ToList()
            If Zips.Count = 0 Then Return
            e.Handled = True
            Dim Folder As String = PackFolder
            If Folder Is Nothing Then Return
            RunInNewThread(
            Sub()
                Dim Copied As Integer = 0
                For Each One In Zips
                    Try
                        DirectoryUtils.Create(Folder)
                        FileUtils.Copy(One, PathUtils.AddSlashSuffix(Folder) & IO.Path.GetFileName(One))
                        Copied += 1
                    Catch ex As Exception
                        Logger.Warn(ex, $"复制光影包失败：{One}")
                    End Try
                Next
                RunInUi(
                Sub()
                    Hint(ModLang.L("已导入 ") & Copied & ModLang.L(" 个光影包。"), HintType.Green)
                    Refresh()
                End Sub)
            End Sub, "Import ShaderPack", ThreadPriority.BelowNormal)
        Catch ex As Exception
            Logger.Error(ex, "拖入安装光影包失败")
        End Try
    End Sub

#End Region

End Class
