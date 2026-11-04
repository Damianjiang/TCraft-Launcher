''' <summary>
''' 实例的资源包管理页：列出 resourcepacks 文件夹里的内容，并通过 options.txt 的 resourcePacks 行控制启用状态。
''' </summary>
Public Class PageInstanceResourcePack

#Region "数据"

    Private ReadOnly Property Instance As McInstance
        Get
            Return PageInstanceLeft.Instance
        End Get
    End Property

    ''' <summary>当前实例的 resourcepacks 文件夹（带结尾斜杠，不存在则创建）。</summary>
    Private ReadOnly Property PackFolder As String
        Get
            Return ModInstanceContent.GetPacksFolder(Instance, "resourcepacks")
        End Get
    End Property

    ''' <summary>当前列出的全部资源包（未筛选）。</summary>
    Private ReadOnly AllPacks As New List(Of ModInstanceContent.PackEntry)

    Private IsInit As Boolean = False

#End Region

#Region "初始化"

    Private Sub PageInstanceResourcePack_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
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
        HintRuntime.Text = ModLang.L("游戏运行时修改可能被游戏覆盖，建议关闭游戏后再调整。")
        BtnRefresh.Text = ModLang.L("刷新")
        BtnOpen.Text = ModLang.L("打开文件夹")
        BtnEnableAll.Text = ModLang.L("全部启用")
        BtnDisableAll.Text = ModLang.L("全部停用")
        TxtSearch.HintText = ModLang.L("搜索资源包")
        CardPacks.Title = ModLang.L("资源包")
    End Sub

    Private Sub InitEvents()
        AddHandler TxtSearch.TextChanged, Sub(snd, args) Rebuild()
        AddHandler BtnRefresh.Click, Sub(snd, args) RefreshWithHint()
        AddHandler BtnOpen.Click, Sub(snd, args) OpenPackFolder()
        AddHandler BtnEnableAll.Click, Sub(snd, args) SetAllPacks(True)
        AddHandler BtnDisableAll.Click, Sub(snd, args) SetAllPacks(False)
    End Sub

    ''' <summary>整页接受 .zip 拖入安装。</summary>
    Private Sub InitDragDrop()
        For Each Target As UIElement In New UIElement() {Me, PanBack, PanMain, PanList, CardPacks}
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

    ''' <summary>重新读取磁盘列表。</summary>
    Public Sub Refresh()
        Try
            AllPacks.Clear()
            PanList.Children.Clear()
            If Instance Is Nothing Then
                CardPacks.Title = ModLang.L("资源包")
                LabPath.Text = ""
                AddNotice(ModLang.L("请先选择一个 Minecraft 版本。"))
                Return
            End If
            Dim Folder As String = PackFolder
            If Folder Is Nothing Then
                CardPacks.Title = ModLang.L("资源包")
                LabPath.Text = ""
                AddNotice(ModLang.L("无法定位这个实例的 resourcepacks 文件夹，请检查版本隔离设置。"))
                Return
            End If
            LabPath.Text = Folder
            AllPacks.AddRange(ModInstanceContent.ListPacks(Folder))
            Rebuild()
        Catch ex As Exception
            Logger.Error(ex, "刷新资源包列表失败")
        End Try
    End Sub

    Private Sub RefreshWithHint()
        Refresh()
        Hint(ModLang.L("资源包列表已刷新。"), HintType.Green)
    End Sub

    ''' <summary>按搜索词重建列表。</summary>
    Private Sub Rebuild()
        Try
            If PanList Is Nothing Then Return
            PanList.Children.Clear()
            If Instance Is Nothing Then Return
            Dim Keyword As String = If(TxtSearch Is Nothing, "", If(TxtSearch.Text, "")).Trim()
            If AllPacks.Count = 0 Then
                AddNotice(ModLang.L("这个实例还没有资源包。把 .zip 拖进来或放到 resourcepacks 文件夹。"))
                CardPacks.Title = ModLang.L("资源包")
                Return
            End If
            Dim Shown As List(Of ModInstanceContent.PackEntry) = AllPacks
            If Keyword.Length > 0 Then
                Shown = AllPacks.Where(Function(One) One.Name.IndexOf(Keyword, StringComparison.OrdinalIgnoreCase) >= 0).ToList()
            End If
            If Shown.Count = 0 Then
                AddNotice(ModLang.L("没有匹配当前搜索的资源包。"))
                CardPacks.Title = ModLang.L("资源包（") & AllPacks.Count & ModLang.L(" 个）")
                Return
            End If
            Dim EnabledNames As New HashSet(Of String)(StringComparer.OrdinalIgnoreCase)
            For Each One In ModInstanceContent.GetEnabledResourcePacks(Instance)
                EnabledNames.Add(One)
            Next
            For Each PackOne In Shown
                PanList.Children.Add(BuildRow(PackOne, EnabledNames.Contains(PackOne.Name)))
            Next
            CardPacks.Title = ModLang.L("资源包（") & Shown.Count & ModLang.L(" 个）")
        Catch ex As Exception
            Logger.Error(ex, "重建资源包列表失败")
        End Try
    End Sub

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

    ''' <summary>生成一行：条目信息 + 右侧启用勾选。</summary>
    Private Function BuildRow(Entry As ModInstanceContent.PackEntry, IsEnabled As Boolean) As UIElement
        Dim Row As New Grid With {.Margin = New Thickness(0, 0, 0, 2)}
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = New GridLength(1, GridUnitType.Star)})
        Row.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})

        Dim TypeText As String = If(Entry.IsFolder, ModLang.L("文件夹"), "zip")
        Dim Item As New MyListItem With {
            .Type = MyListItem.CheckType.Clickable,
            .Height = 46,
            .Title = Entry.Name,
            .Info = ModInstanceContent.FormatSize(Entry.SizeBytes) & " · " &
                    Entry.Modified.ToString("yyyy'/'MM'/'dd HH':'mm") & " · " & TypeText,
            .SvgIcon = If(Entry.IsFolder, "lucide/folder-open", "lucide/package"),
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

        Dim Cb As New MyCheckBox With {
            .Width = 32,
            .Height = 30,
            .VerticalAlignment = VerticalAlignment.Center,
            .Margin = New Thickness(10, 0, 6, 0),
            .Checked = IsEnabled
        }
        AddHandler Cb.Change, Sub(snd, user) TogglePack(Entry, CType(snd, MyCheckBox).Checked, user)
        '点整行也能切换
        AddHandler Item.Click, Sub(snd, args) Cb.SetChecked(Not Cb.Checked, True)

        Grid.SetColumn(Item, 0)
        Grid.SetColumn(Cb, 1)
        Row.Children.Add(Item)
        Row.Children.Add(Cb)
        Return Row
    End Function

#End Region

#Region "操作"

    ''' <summary>切换单个资源包的启用状态，并立即写回 options.txt。</summary>
    Private Sub TogglePack(Entry As ModInstanceContent.PackEntry, NewValue As Boolean, user As Boolean)
        If Not user Then Return
        Try
            ModInstanceContent.SetResourcePackEnabled(Instance, Entry.Name, NewValue)
            Hint(If(NewValue, ModLang.L("已启用："), ModLang.L("已停用：")) & Entry.Name, HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "切换资源包启用状态失败")
            Hint(ModLang.L("切换资源包状态失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>批量启用 / 停用全部资源包。</summary>
    Private Sub SetAllPacks(Enabled As Boolean)
        If Instance Is Nothing Then
            Hint(ModLang.L("请先选择一个 Minecraft 版本！"), HintType.Red)
            Return
        End If
        If AllPacks.Count = 0 Then
            Hint(ModLang.L("这个实例还没有资源包。"), HintType.Blue)
            Return
        End If
        Try
            For Each One In AllPacks
                ModInstanceContent.SetResourcePackEnabled(Instance, One.Name, Enabled)
            Next
            Rebuild()
            Hint(If(Enabled, ModLang.L("已启用全部资源包。"), ModLang.L("已停用全部资源包。")), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "批量修改资源包状态失败")
            Hint(ModLang.L("批量修改失败：") & ex.Message, HintType.Red)
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
            Logger.Warn(ex, "在文件夹中显示资源包失败")
        End Try
    End Sub

    ''' <summary>删除资源包（二次确认，移入回收站）。</summary>
    Private Sub DeletePack(Entry As ModInstanceContent.PackEntry)
        If Entry Is Nothing Then Return
        Try
            If MyMsgBox(ModLang.L("确定要删除资源包「") & Entry.Name & ModLang.L("」吗？") & vbCrLf &
                        ModLang.L("删除后会被移入回收站，可以自行恢复。"),
                        ModLang.L("删除资源包"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
            If Entry.IsFolder Then
                DirectoryUtils.Delete(Entry.FullPath, True)
            Else
                FileUtils.Delete(Entry.FullPath, True)
            End If
            Hint(ModLang.L("已删除：") & Entry.Name, HintType.Green)
            Refresh()
        Catch ex As Exception
            Logger.Error(ex, "删除资源包失败")
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
                        Logger.Warn(ex, $"复制资源包失败：{One}")
                    End Try
                Next
                RunInUi(
                Sub()
                    Hint(ModLang.L("已导入 ") & Copied & ModLang.L(" 个资源包。"), HintType.Green)
                    Refresh()
                End Sub)
            End Sub, "Import ResourcePack", ThreadPriority.BelowNormal)
        Catch ex As Exception
            Logger.Error(ex, "拖入安装资源包失败")
        End Try
    End Sub

#End Region

End Class
