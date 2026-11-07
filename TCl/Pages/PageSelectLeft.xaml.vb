Public Class PageSelectLeft
    Implements IRefreshable

    Private Sub PageSelectLeft_Initialized(sender As Object, e As EventArgs) Handles Me.Initialized
        AddHandler McFolderListLoader.PreviewFinish, Sub() If FrmSelectLeft IsNot Nothing Then RunInUiWait(AddressOf McFolderListUI)
    End Sub
    Private IsFirstLoad As Boolean = True
    Private Sub PageSelectLeft_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        If IsFirstLoad Then McFolderListUI() '若已经执行完成，触发首次加载
        IsFirstLoad = False
    End Sub
    Private Sub McFolderListUI()
        Try

            '确认数据有变化
            If McFolderListLast IsNot Nothing AndAlso McFolderListLast.SequenceEqual(McFolderList) Then
                Dim IsEqual As Boolean = True
                For i = 0 To McFolderListLast.Count - 1
                    If Not McFolderListLast(i).Equals(McFolderList(i)) Then
                        IsEqual = False
                        Exit For
                    End If
                Next
                If IsEqual Then Return
            End If
            McFolderListLast = McFolderList

            '创建 UI
            FrmSelectLeft.PanList.Children.Clear()

            '文件夹列表
            FrmSelectLeft.PanList.Children.Add(New TextBlock With {.Text = ModLang.L("文件夹列表"), .Margin = New Thickness(13, 18, 5, 4), .Opacity = 0.6, .FontSize = 12})
            For Each Folder As McFolder In McFolderList.ToArray
                '构建框架与图表按钮
                Dim NewItem As New MyListItem With {.IsScaleAnimationEnabled = False, .Type = MyListItem.CheckType.RadioBox, .MinPaddingRight = 30, .Title = Folder.Name, .Info = Folder.Location, .Height = 40, .Tag = Folder}
                AddHandler NewItem.Changed, AddressOf FrmSelectLeft.Folder_Change
                '右键：菜单开在光标处（与原来的 ContextMenu 手感一致）
                AddHandler NewItem.MouseRightButtonUp,
                    Sub(sender, e)
                        FrmSelectLeft.OpenFolderMenu(Folder, NewItem, e.GetPosition(NewItem), False)
                        e.Handled = True
                    End Sub
                Dim NewIconButton As New MyIconButton With {.SvgIcon = "lucide/settings", .LogoScale = 1.1}
                '齿轮：菜单贴在齿轮下方、右边缘对齐（以前锚在整行左边缘，看起来「位置不对」）
                AddHandler NewIconButton.Click,
                    Sub(sender, e)
                        Dim BtnW As Double = If(NewIconButton.ActualWidth > 0, NewIconButton.ActualWidth, 22)
                        Dim BtnH As Double = If(NewIconButton.ActualHeight > 0, NewIconButton.ActualHeight, 22)
                        FrmSelectLeft.OpenFolderMenu(Folder, NewIconButton, New Point(BtnW, BtnH + 2), True)
                    End Sub
                NewItem.Buttons = {NewIconButton}
                FrmSelectLeft.PanList.Children.Add(NewItem)
                Logger.Info($"有效的 Minecraft 文件夹：{Folder.Name} > {Folder.Location}")
            Next

            '标题文本
            FrmSelectLeft.PanList.Children.Add(New TextBlock With {.Text = ModLang.L("添加或导入"), .Margin = New Thickness(13, 18, 5, 4), .Opacity = 0.6, .FontSize = 12})

            '确认创建按钮状态
            If Not DirectoryUtils.Exists(Paths.Base & ".minecraft\") Then
                Dim ItemCreate As New MyListItem With {.IsScaleAnimationEnabled = False, .Type = MyListItem.CheckType.Clickable, .Title = ModLang.L("新建 .minecraft 文件夹"), .Height = 34,
                    .ToolTip = ModLang.L("在 TCL 当前所在文件夹下创建新的 .minecraft 文件夹"),
                    .LogoScale = 0.9,
                    .Logo = "M103.331925 384.978025l25.805736 0L129.137661 161.847132c0-18.313088 14.905478-33.718963 33.718963-33.718963l0.969071 0 253.006318 0c10.82044 0 20.218484 4.797259 26.500561 12.257162l117.579929 126.753869 297.819966 0c18.297738 0 33.736359 15.179724 33.736359 33.977859l0 0.952698 0 82.909292 25.547863 0c18.538215 0 34.187637 15.179724 34.187637 33.977859 0 2.163269-0.469698 3.617387-0.469698 5.539156l-54.437843 432.971086c-1.210571 10.382465-7.007601 19.056008-14.968923 24.352641-6.249331 5.765307-14.680351 9.624195-23.595394 9.624195l-0.969071 0-694.906773 0c-9.155521 0-17.344017-3.858888-23.626094-9.155521-8.67252-5.765307-14.453177-14.939247-15.389502-25.758664L69.597613 423.040922c-2.165316-18.313088 10.868535-35.414581 29.665647-38.062897L103.331925 384.978025 103.331925 384.978025zM196.576609 384.978025 196.576609 384.978025l627.938546 0 0-49.625234L546.461371 335.352791l0 0c-9.400091 0-18.329461-4.117784-25.048489-11.110035L402.363486 196.067514 196.576609 196.067514 196.576609 384.978025 196.576609 384.978025zM879.469767 452.916347 879.469767 452.916347l-20.267603 0-0.469698 0-0.969071 0-694.906773 0-0.984421 0-20.218484 0 45.781696 366.728382 646.218888 0L879.469767 452.916347 879.469767 452.916347z"}
                FrmSelectLeft.PanList.Children.Add(ItemCreate)
                AddHandler ItemCreate.Click, AddressOf FrmSelectLeft.Create_Click
            End If

            '添加按钮
            Dim ItemAdd As New MyListItem With {.IsScaleAnimationEnabled = False, .Type = MyListItem.CheckType.Clickable, .Title = ModLang.L("添加已有文件夹"), .Height = 34,
                .ToolTip = ModLang.L("将一个已有的 Minecraft 文件夹添加到列表"),
                .Logo = "M512.277 954.412c-118.89 0-230.659-46.078-314.73-129.73S67.12 629.666 67.12 511.222s46.327-229.744 130.398-313.427 195.82-129.73 314.73-129.73 230.659 46.078 314.72 129.73S957.397 392.81 957.397 511.183 911.078 740.96 826.97 824.642s-195.8 129.77-314.692 129.77z m0-822.784c-101.972 0-197.809 39.494-269.865 111.222s-111.7 166.997-111.7 268.373 39.653 196.695 111.67 268.335S410.246 890.78 512.248 890.78s197.809-39.484 269.865-111.222 111.7-166.998 111.67-268.374c-0.03-101.375-39.654-196.665-111.67-268.303S614.22 131.628 512.277 131.628z m222.585 347.8H544.073V288.64c-0.76-17.561-15.613-31.18-33.173-30.419-16.495 0.714-29.704 13.924-30.419 30.419v190.787H289.703c-17.56 0.761-31.179 15.614-30.419 33.174 0.715 16.494 13.924 29.703 30.42 30.418H480.48v190.788c0.761 17.56 15.614 31.179 33.174 30.419 16.494-0.715 29.703-13.925 30.418-30.42V543.02h190.788c17.56 0.762 32.413-12.857 33.173-30.418 0.762-17.561-12.858-32.414-30.419-33.174a31.683 31.683 0 0 0-2.753 0z"}
            FrmSelectLeft.PanList.Children.Add(ItemAdd)
            AddHandler ItemAdd.Click, AddressOf FrmSelectLeft.Add_Click

            '安装按钮
            Dim ItemInstall As New MyListItem With {.IsScaleAnimationEnabled = False, .Type = MyListItem.CheckType.Clickable, .Title = ModLang.L("导入整合包"), .Height = 34,
                .ToolTip = ModLang.L("在当前选择的 Minecraft 文件夹下安装整合包"),
                .Logo = "M512 40.96C249.344 40.96 35.84 252.416 35.84 512s213.504 471.04 476.16 471.04c103.424 0 202.752-33.28 286.72-96.256l1.536-1.536c5.12-5.632 7.68-12.8 7.68-19.968 0-16.896-13.824-30.208-30.72-30.208-7.68 0-15.36 2.56-20.992 7.68h-0.512c-71.68 52.224-155.648 79.36-243.712 79.36-227.328 0-412.16-182.784-412.16-407.552 0-224.768 184.832-407.552 412.16-407.552s412.16 182.784 412.16 407.552c0 68.608-15.872 132.608-46.592 190.464-0.512 1.024-1.024 2.048-1.024 3.072-0.512 2.048-1.536 4.608-1.536 8.192 0 16.896 13.824 30.208 30.72 30.208 12.288 0 23.04-7.168 28.16-18.432 35.84-68.608 53.76-141.312 53.76-216.064 0.512-259.584-212.992-471.04-475.648-471.04z M812.032 483.328c-31.744-20.992-71.68 1.536-78.848 6.144-1.024 0.512-104.448 61.44-128 74.752-8.192 4.608-22.528-0.512-27.136-4.096-31.232-36.352-54.272-70.656-68.608-102.4-13.312-29.184 0.512-41.472 3.072-43.52 7.168-4.608 114.688-68.608 143.36-83.456 24.064-12.288 40.96-25.088 46.08-45.056 3.072-13.312 0-27.136-9.216-39.936-22.016-31.744-172.544-84.992-311.296-3.584-157.184 91.648-152.064 242.688-150.528 292.352v9.216c0 18.944-12.8 37.376-14.848 40.448l-20.992 21.504c-6.144 6.144-9.216 13.824-9.216 22.528 0 8.704 3.584 16.384 9.728 22.528 12.8 12.288 32.768 11.776 45.056-0.512l22.528-23.552 0.512-0.512c3.072-3.584 30.208-38.4 30.208-81.92l-0.512-11.264c-1.536-44.544-5.632-162.816 119.296-235.52 88.064-51.2 173.056-32.256 208.896-19.968-36.864 19.456-143.36 83.456-144.896 84.48-22.016 14.336-55.808 58.88-26.112 122.88 17.408 37.376 43.52 76.8 80.896 120.32 14.336 17.408 62.976 37.376 103.424 15.36 24.576-13.312 125.44-73.216 130.048-75.776 2.048-1.024 4.608-2.56 7.68-3.584 0 2.56-0.512 6.144-1.024 10.752-5.632 35.84-35.328 155.136-191.488 181.76-49.664 8.704-89.6 3.584-121.856-0.512h-0.512c-37.888-4.608-73.216-9.216-101.888 14.336-31.232 26.112-40.96 34.304-35.84 54.272 3.584 14.336 16.384 24.064 30.72 24.064 2.56 0 5.12-0.512 7.68-1.024 6.656-1.536 12.8-5.632 16.896-10.752 2.048-2.048 7.68-6.656 20.992-18.432 6.656-5.632 25.088-3.584 52.736 0 34.816 4.608 81.92 10.24 141.312 0.512 157.184-26.624 228.864-138.752 243.2-234.496 7.68-38.912 0-64.512-21.504-78.336z"}
            FrmSelectLeft.PanList.Children.Add(ItemInstall)
            AddHandler ItemInstall.Click, AddressOf ModpackInstall

            '边距
            FrmSelectLeft.PanList.Children.Add(New FrameworkElement With {.Height = 10, .IsHitTestVisible = False})

            '确认勾选状态
            For i = 0 To McFolderList.Count - 1
                If PathUtils.IsSame(McFolderList(i).Location, McFolderSelected) Then
                    CType(FrmSelectLeft.PanList.Children(i + 1), MyListItem).Checked = True '去掉第一个标题
                    Return
                End If
            Next
            If Not McFolderList.Any() Then
                Throw New ArgumentNullException("没有可用的 Minecraft 文件夹")
            Else
                McFolderSelected = McFolderList.First.Location
                CType(FrmSelectLeft.PanList.Children(1), MyListItem).Checked = True
            End If

        Catch ex As Exception
            Logger.Error(ex, "构建 Minecraft 文件夹列表 UI 出错")
        Finally
            LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.RunOnUpdated, MaxDepth:=1, ExtraPath:="versions\") '刷新版本列表
        End Try
    End Sub
    Private McFolderListLast As List(Of McFolder)

    '添加文件夹
    Public Sub Add_Click()
        Dim NewFolder As String = ""
        '下载或版本修改期间禁止更改游戏文件夹
        If McInstanceLock.IsAnyLocked OrElse HasDownloadingTask() Then
            Hint(ModLang.L("请在其他任务完成后，再添加游戏文件夹！"), HintType.Red)
            Return
        End If
        Try
            '获取输入
            Dim Selected As List(Of String) = Dialogs.SelectFolder(ModLang.L("选择 Minecraft 文件夹"), False)
            If Selected Is Nothing OrElse Not Selected.Any() Then
                Dim Reason As String = Dialogs.LastFolderError
                If Not String.IsNullOrEmpty(Reason) Then Hint(ModLang.L("打开文件夹选择框失败：") & Reason, HintType.Red)
                Return
            End If
            NewFolder = Selected.FirstOrDefault
            If NewFolder.Contains("!") OrElse NewFolder.Contains(";") Then Hint(ModLang.L("Minecraft 文件夹路径中不能含有感叹号或分号！"), HintType.Red) : Return
            '要求输入显示名称
            Dim DefaultName = PathUtils.GetLastPart(NewFolder)
            If DefaultName = ".minecraft" Then
                Dim ParentFolder = PathUtils.RemoveLastPart(NewFolder)
                DefaultName = If(ParentFolder.EndsWithF(":"), "", PathUtils.GetLastPart(ParentFolder))
            End If
            If DefaultName.Length > 40 Then DefaultName = DefaultName.Substring(0, 39)
            Dim NewName As String = MyMsgBoxInput(ModLang.L("输入显示名称"), ModLang.L("输入该文件夹在左边栏列表中显示的名称。"), DefaultName,
                                              New ObjectModel.Collection(Of Validate) From {New ValidateNullOrWhiteSpace, New ValidateLength(1, 30), New ValidateExcept({">", "|"})})
            If String.IsNullOrWhiteSpace(NewName) Then Return
            '添加文件夹
            AddFolder(NewFolder, NewName, True)
        Catch ex As Exception
            Logger.Error(ex, $"添加文件夹失败（{NewFolder}）")
        End Try
    End Sub
    ''' <summary>
    ''' 判断拖入的目录是否是（或包含）一个 Minecraft 游戏文件夹，返回真正的 .minecraft 目录（带分隔符）。
    ''' 找不到时返回空字符串。供「把文件夹拖进窗口」的自动识别使用。
    ''' </summary>
    Public Shared Function DetectMinecraftFolder(Source As String) As String
        Try
            If String.IsNullOrWhiteSpace(Source) Then Return ""
            Dim FolderPath As String = PathUtils.AddSlashSuffix(Source)
            '1. 自身就是 .minecraft
            If DirectoryUtils.Exists(FolderPath & "versions\") Then Return FolderPath
            '2. 目录下套了一层 .minecraft
            If DirectoryUtils.Exists(FolderPath & ".minecraft\versions\") Then Return FolderPath & ".minecraft\"
            '3. 一级子目录里找（例如整合包解压目录、多版本合集目录）
            '只查一层：拖进来的可能是一个大目录，递归枚举会卡住界面
            For Each SubFolder In DirectoryUtils.EnumerateDirectories(FolderPath)
                If DirectoryUtils.Exists(PathUtils.AddSlashSuffix(SubFolder) & "versions\") Then Return PathUtils.AddSlashSuffix(SubFolder)
                If DirectoryUtils.Exists(PathUtils.AddSlashSuffix(SubFolder) & ".minecraft\versions\") Then Return PathUtils.AddSlashSuffix(SubFolder) & ".minecraft\"
            Next
        Catch ex As Exception
            Logger.Warn(ex, "检测 Minecraft 文件夹失败")
        End Try
        Return ""
    End Function

    ''' <summary>
    ''' 为一个 Minecraft 目录生成左侧列表的默认显示名：优先上一级目录名，其次固定为「已导入的版本」。
    ''' </summary>
    Public Shared Function SuggestFolderName(Target As String) As String
        Try
            Dim Trimmed As String = PathUtils.RemoveSlashSuffix(Target)
            Dim Last As String = PathUtils.GetLastPart(Trimmed)
            If String.Equals(Last, ".minecraft", StringComparison.OrdinalIgnoreCase) Then
                Dim Parent As String = PathUtils.RemoveLastPart(Trimmed)
                If Not String.IsNullOrEmpty(Parent) AndAlso Not Parent.EndsWithF(":") Then Last = PathUtils.GetLastPart(PathUtils.RemoveSlashSuffix(Parent))
            End If
            If String.IsNullOrWhiteSpace(Last) Then Last = ModLang.L("已导入的版本")
            If Last.Length > 30 Then Last = Last.Substring(0, 30)
            Return Last
        Catch ex As Exception
            Return ModLang.L("已导入的版本")
        End Try
    End Function

    ''' <summary>
    ''' 将指定文件夹添加到 Minecraft 文件夹列表，并选中它。
    ''' </summary>
    Public Shared Sub AddFolder(FolderPath As String, DisplayName As String, ShowHint As Boolean)
        RunInThread(
        Sub()
            Try
                FolderPath = PathUtils.AddSlashSuffix(FolderPath)
                '检查文件夹权限
                If Not CheckPermission(FolderPath) Then
                    If ShowHint Then
                        Hint(ModLang.L("添加文件夹失败：TCL 没有访问该文件夹的权限！"), HintType.Red)
                        Return
                    Else
                        Throw New Exception(ModLang.L("TCL 没有访问文件夹的权限：") & FolderPath)
                    End If
                End If
                '检查实际的 Minecraft 文件夹位置（没有问题，或是在子文件夹中）
                If Not CheckPermission(FolderPath & "versions\") Then
                    For Each Folder In DirectoryUtils.EnumerateDirectories(FolderPath, True)
                        If CheckPermission(Folder & "\versions\") Then
                            FolderPath = Folder & "\"
                            Exit For
                        End If
                    Next
                End If
                '判断是否已经添加过，若添加过则直接修改自定义名
                Dim Folders As New List(Of String)(Settings.Get(Of String)("LaunchFolders").Split("|"))
                Dim IsAdded As Boolean = False
                Dim IsReplace As Boolean = False
                For i = 0 To Folders.Count - 1
                    Dim Folder As String = Folders(i)
                    If Folder = "" Then Continue For
                    If PathUtils.IsSame(Folder.Split(">")(1), FolderPath) Then
                        IsAdded = True
                        If Folder.Split(">")(0) = DisplayName Then
                            If ShowHint Then Hint(ModLang.L("此文件夹已在列表中！"), HintType.Blue)
                            Return
                        Else
                            Folders(i) = DisplayName & ">" & FolderPath
                            IsReplace = True
                            If ShowHint Then Hint(ModLang.L("文件夹名称已更新为 ") & DisplayName & ModLang.L(" ！"), HintType.Green)
                        End If
                        Exit For
                    End If
                Next
                '如果没有添加过，则添加进去
                If Not IsAdded Then Folders.Add(DisplayName & ">" & FolderPath)
                '保存
                Settings.Set("LaunchFolders", Folders.Join("|"))
                '切换选择并更新列表
                McFolderSelected = FolderPath
                McFolderListLoader.Start(IsForceRestart:=True)
                '提示
                If IsReplace Then Return
                If ShowHint Then Hint(ModLang.L("文件夹 ") & DisplayName & ModLang.L(" 已添加！"), HintType.Green)
                '检查是否为根目录整合包，自动关闭版本隔离
                '1. 根目录中存在数个 Mod
                Dim ModFolder = DirectoryUtils.GetInfo(FolderPath & "mods\")
                If Not (ModFolder.Exists AndAlso ModFolder.EnumerateFiles.Count >= 3) Then Return
                '2. 版本数较少，可能为整合包
                Dim VersionFolderInfo = DirectoryUtils.GetInfo(FolderPath & "versions\")
                If Not (VersionFolderInfo.Exists AndAlso VersionFolderInfo.EnumerateDirectories.Count <= 3) Then Return
                '3. 能够找到可安装 Mod 的版本
                For Each VersionFolder In VersionFolderInfo.EnumerateDirectories
                    Dim Instance As New McInstance(VersionFolder.FullName)
                    Instance.Load()
                    If Not Instance.Modable Then Continue For
                    '4. 该版本的隔离文件夹下不存在 mods
                    Dim ModIndieFolder = DirectoryUtils.GetInfo(Instance.PathVersion & "mods\")
                    If ModIndieFolder.Exists AndAlso ModIndieFolder.EnumerateFiles.Any Then Return
                    '满足以上全部条件则视为根目录整合包
                    Settings.Set("VersionArgumentIndie", 2, Instance:=Instance)
                    Settings.Set("VersionArgumentIndieV2", False, Instance:=Instance)
                    Logger.Warn($"已自动关闭单版本隔离：{Instance.Name}")
                Next
            Catch ex As Exception
                Logger.Error(ex, "向文件夹列表中添加新文件夹失败")
            End Try
        End Sub)
    End Sub

    '创建文件夹
    Public Sub Create_Click()
        '下载或版本修改期间禁止更改游戏文件夹
        If McInstanceLock.IsAnyLocked OrElse HasDownloadingTask() Then
            Hint(ModLang.L("请在其他任务完成后，再创建游戏文件夹！"), HintType.Red)
            Return
        End If
        CreateMcFolderInCurrentPath()
        Hint(ModLang.L("新建 .minecraft 文件夹成功！"), HintType.Green)
    End Sub
    Public Shared Sub CreateMcFolderInCurrentPath()
        McFolderSelected = Paths.Base & ".minecraft\"
        If Not DirectoryUtils.Exists(McFolderSelected) Then
            DirectoryUtils.Create(McFolderSelected & "versions\")
            McFolderLauncherProfilesJsonCreate(McFolderSelected)
        End If
        AddFolder(McFolderSelected, PathUtils.GetLastPart(Paths.Base), False)
    End Sub

    '文件夹操作动画卡片
    ''' <summary>当前菜单对应的文件夹。</summary>
    Private MenuFolder As McFolder = Nothing
    ''' <summary>上次关闭时间，250ms 重点守卫。</summary>
    Private FolderMenuClosedTick As Long = 0

    ''' <summary>
    ''' 打开文件夹操作菜单。
    ''' <para/>
    ''' Anchor = Target 内部坐标系的锚点（右键 = 光标位置，齿轮 = 齿轮右下角）；
    ''' AlignRight = True 时卡片右边贴锚点，False 时卡片左边贴锚点。
    ''' </summary>
    Private Sub OpenFolderMenu(Folder As McFolder, Target As FrameworkElement, Anchor As Point, AlignRight As Boolean)
        If PopupFolderMenu.IsOpen AndAlso MenuFolder Is Folder Then
            CloseFolderMenu() : Return
        End If
        '同 250ms 守卫只挡「同一文件夹刚被这次点击关掉」的情况；换一行立刻就能开
        If GetTimeMs() - FolderMenuClosedTick < 250 AndAlso MenuFolder Is Folder Then Return
        Try
            MenuFolder = Folder
            PanFolderMenu.Children.Clear()
            '按文件夹类型构建行（行序与原 ContextMenu 一致）
            If Folder.Type = McFolder.Types.RenamedVanilla Then MenuAddRow("lucide/undo", "复原名称", AddressOf Remove_Click)
            MenuAddRow("lucide/pencil", "重命名", AddressOf Rename_Click)
            MenuAddRow("lucide/folder-open", "打开", AddressOf Open_Click)
            MenuAddRow("lucide/refresh-cw", "刷新", AddressOf Refresh_Click)
            If Folder.Type = McFolder.Types.Custom Then MenuAddRow("lucide/circle-minus", "移出列表", AddressOf Remove_Click)
            MenuAddRow("lucide/trash-2", If(Folder.Type <> McFolder.Types.Custom AndAlso PathUtils.IsSame(Folder.Location, Paths.Base & ".minecraft\") AndAlso McFolderList.IsSingle, "清空", "删除"), AddressOf Delete_Click)
            '复位动画初值（Popup 关着时改值不会闪）；真正的展开动画在 Opened 里跑
            TransformFolderMenuScale.ScaleX = 0.94
            TransformFolderMenuScale.ScaleY = 0.94
            TransformFolderMenuPos.Y = 10
            '定位按锚点算：卡片 Border 的 14px 投影留白要扣掉，否则本体整整偏 14px
            MenuCard.ApplyAnchor(PopupFolderMenu, Target, Anchor, AlignRight, 14)
            '条目多时按锚点这一侧的可用高度封顶，超出用 TCL 滚动条
            ScrollFolderMenu.MaxHeight = MenuCard.CalcMaxMenuHeight(Target)
            PopupFolderMenu.IsOpen = True
        Catch ex As Exception
            Logger.Error(ex, "打开文件夹菜单失败")
        End Try
    End Sub

    Private Sub MenuAddRow(Icon As String, Title As String, Action As EventHandler)
        Dim Row As New MyListItem With {
            .Title = ModLang.L(Title), .SvgIcon = Icon, .Type = MyListItem.CheckType.Clickable,
            .Height = 34, .MinPaddingRight = 10, .IsScaleAnimationEnabled = False}
        AddHandler Row.Click, Sub(sender, e)
                                  CloseFolderMenu()
                                  Action.Invoke(sender, e)
                              End Sub
        PanFolderMenu.Children.Add(Row)
    End Sub

    Private Sub CloseFolderMenu()
        Try
            If Not PopupFolderMenu.IsOpen Then Return
            If Not MenuCard.PopupAnimEnabled Then
                PopupFolderMenu.IsOpen = False
                Return
            End If
            AniStop("Folder Menu Popup")
            AniStart({
                AaOpacity(CardFolderMenu, -CardFolderMenu.Opacity, 80),
                AaDouble(Sub(i) TransformFolderMenuScale.ScaleX += i, 0.96 - TransformFolderMenuScale.ScaleX, 90),
                AaDouble(Sub(i) TransformFolderMenuScale.ScaleY += i, 0.96 - TransformFolderMenuScale.ScaleY, 90),
                AaCode(Sub() PopupFolderMenu.IsOpen = False, , True)
            }, "Folder Menu Popup")
        Catch ex As Exception
            Logger.Warn(ex, "关闭文件夹菜单失败")
        End Try
    End Sub

    Private Sub PopupFolderMenu_Opened(sender As Object, e As EventArgs) Handles PopupFolderMenu.Opened
        If Not MenuCard.PopupAnimEnabled Then
            TransformFolderMenuScale.ScaleX = 1
            TransformFolderMenuScale.ScaleY = 1
            TransformFolderMenuPos.Y = 0
            CardFolderMenu.Opacity = MenuCard.PopupBaseOpacity
            Return
        End If
        AniStart({
            AaOpacity(CardFolderMenu, MenuCard.PopupBaseOpacity - CardFolderMenu.Opacity, 130),
            AaDouble(Sub(i) TransformFolderMenuScale.ScaleX += i, 1 - TransformFolderMenuScale.ScaleX, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformFolderMenuScale.ScaleY += i, 1 - TransformFolderMenuScale.ScaleY, 220, , New AniEaseOutBack(AniEasePower.Middle)),
            AaDouble(Sub(i) TransformFolderMenuPos.Y += i, 0 - TransformFolderMenuPos.Y, 220, , New AniEaseOutFluent(AniEasePower.Weak))
        }, "Folder Menu Popup")
    End Sub

    Private Sub PopupFolderMenu_Closed(sender As Object, e As EventArgs) Handles PopupFolderMenu.Closed
        FolderMenuClosedTick = GetTimeMs()
    End Sub

    '操作处理（目标取自 MenuFolder）
    Public Sub Remove_Click(sender As Object, e As RoutedEventArgs)
        Try

            Dim Target As McFolder = MenuFolder
            '若为 “移除”，则提醒是否删除 TCL 的配置文件
            If Target.Type = McFolder.Types.Custom Then
                If McInstanceLock.IsAnyLocked OrElse HasDownloadingTask() Then
                    Hint(ModLang.L("请在其他任务完成后，再移除游戏文件夹！"), HintType.Red)
                    Return
                End If
                Select Case MyMsgBox(ModLang.L("是否需要清理 TCL 在该文件夹中的配置文件？") & vbCrLf & ModLang.L("这包括各个版本的独立设置（如自定义图标、第三方登录配置）等，对游戏本身没有影响。"), ModLang.L("配置文件清理"), ModLang.L("删除"), ModLang.L("保留"), ModLang.L("取消"))
                    Case 1
                        '删除配置文件
                        FileUtils.Delete(Target.Location & "TCL.ini")
                        For Each Instance In DirectoryUtils.EnumerateDirectories(Target.Location & "versions\")
                            DirectoryUtils.Delete(Path.Combine(Instance, "TCL"), True)
                        Next
                    Case 2
                        '不删除
                    Case 3
                        '取消
                        Return
                End Select
            End If
            Dim DeletedName As String = RemoveFolderFromSetup(Target)
            Hint(If(Target.Type = McFolder.Types.Custom, ModLang.L("文件夹 ") & DeletedName & ModLang.L(" 已从列表中移除！"), ModLang.L("文件夹名称已复原！")), HintType.Green)
            McFolderListLoader.Start(IsForceRestart:=True)
        Catch ex As Exception
            Logger.Error(ex, "从列表中移除游戏文件夹失败")
        End Try
    End Sub
    Public Sub Delete_Click(sender As Object, e As RoutedEventArgs)
        If McInstanceLock.IsAnyLocked OrElse HasDownloadingTask() Then
            Hint(ModLang.L("请在其他任务完成后，再删除游戏文件夹！"), HintType.Red)
            Return
        End If
        Dim Target As McFolder = MenuFolder
        Dim DeleteText As String = If((Target.Type = McFolder.Types.Vanilla OrElse Target.Type = McFolder.Types.RenamedVanilla) AndAlso Target.Location = Paths.Base & ".minecraft\" AndAlso McFolderList.IsSingle, "清空", "删除")
        If MyMsgBox(ModLang.L("你确定要" & DeleteText) & ModLang.L("这个文件夹吗？") & vbCrLf &
                    ModLang.L("目标文件夹：") & Target.Location & vbCrLf & vbCrLf &
                    ModLang.L("该文件夹中的游戏存档、游戏版本，以及 MC 之外的其他文件，都会永久丢失，不可恢复！"), ModLang.L("警告"), ModLang.L("取消"), ModLang.L("确认"), ModLang.L("取消")) <> 2 Then Return
        If MyMsgBoxInput(ModLang.L("删除确认"),
                         ModLang.L("该文件夹中的游戏存档、游戏版本，以及 MC 之外的其他文件，都会永久丢失，不可恢复！") & vbCrLf &
                         ModLang.L("目标文件夹：") & Target.Location & vbCrLf & vbCrLf &
                         ModLang.L("如果确实要删除，请在下面输入【Tomato】这个单词以继续。"),
                         ValidateRules:=New ObjectModel.Collection(Of Validate) From {New ValidateSame("Tomato", $"请输入 {vbLQ}Tomato{vbRQ}", IgnoreCase:=True)},
                         Button1:="永久删除文件夹", Button2:="取消", IsWarn:=True) Is Nothing Then Return
        RemoveFolderFromSetup(Target)
        RunInNewThread(
        Sub()
            '删除文件夹
            Try
                Hint(ModLang.L("正在" & DeleteText) & ModLang.L("文件夹 ") & Target.Name & ModLang.L("！"), HintType.Blue)
                DirectoryUtils.Delete(Target.Location)
                If DeleteText = "清空" Then DirectoryUtils.Create(Target.Location)
                Hint(ModLang.L("已" & DeleteText) & ModLang.L("文件夹 ") & Target.Name & ModLang.L("！"), HintType.Green)
            Catch ex As Exception
                Logger.Error(ex, $"{DeleteText}文件夹 {Target.Name} 失败", LogBehavior.Toast)
            Finally
                '刷新列表
                McFolderListLoader.Start(IsForceRestart:=True)
            End Try
        End Sub, "Folder Delete " & GetUuid(), ThreadPriority.BelowNormal)
    End Sub
    ''' <summary>
    ''' 从设置的文件夹列表中删除指定的文件夹。
    ''' 返回被删除的文件夹的显示名称。如果没有，返回 Nothing。
    ''' </summary>
    Private Shared Function RemoveFolderFromSetup(Target As McFolder) As String
        Dim DeletedName As String = Nothing
        Dim Folders = Settings.Get(Of String)("LaunchFolders").Split("|"c, True).ToList
        For Each Folder In Folders.ToList
            If Not Folder.Contains(">") OrElse Not PathUtils.IsSame(Folder.AfterFirst(">"), Target.Location) Then Continue For
            DeletedName = Folder.BeforeFirst(">")
            Folders.Remove(Folder)
            Exit For
        Next
        Settings.Set("LaunchFolders", Folders.Join("|"))
        Return DeletedName
    End Function

    Public Sub Open_Click(sender As Object, e As RoutedEventArgs)
        OpenExplorer(MenuFolder.Location)
    End Sub
    Public Sub Refresh_Click(sender As Object, e As RoutedEventArgs)
        RefreshCurrent(MenuFolder.Location)
    End Sub
    Public Sub RefreshCurrent() Implements IRefreshable.Refresh
        RefreshCurrent(McFolderSelected)
    End Sub
    Public Shared Sub RefreshCurrent(Folder As String)
        WriteIni(Folder & "TCL.ini", "InstanceCache", "") '删除缓存以强制要求下一次加载时更新列表
        If PathUtils.IsSame(Folder, McFolderSelected) Then LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.ForceRun, MaxDepth:=1, ExtraPath:="versions\")
    End Sub
    Public Sub Rename_Click(sender As Object, e As RoutedEventArgs)
        Dim Folder As McFolder = MenuFolder
        Try
            '获取输入
            Dim NewName As String =
                MyMsgBoxInput(ModLang.L("输入新名称"), "", Folder.Name,
                              New ObjectModel.Collection(Of Validate) From {New ValidateNullOrWhiteSpace, New ValidateLength(1, 30), New ValidateExcept({">", "|"})})
            If String.IsNullOrWhiteSpace(NewName) Then Return
            '修改自定义名
            Dim Folders As New List(Of String)(Settings.Get(Of String)("LaunchFolders").Split("|"))
            Dim IsAdded As Boolean = False
            For i = 0 To Folders.Count - 1
                Dim FolderCurrent As String = Folders(i)
                If FolderCurrent = "" Then Continue For
                If PathUtils.IsSame(FolderCurrent.Split(">")(1), Folder.Location) Then
                    IsAdded = True
                    If FolderCurrent.Split(">")(0) = NewName Then
                        '名称未修改
                        Return
                    Else
                        Folders(i) = NewName & ">" & Folder.Location
                    End If
                    Exit For
                End If
            Next
            '如果没有添加过，则添加进去（因为修改了默认项的名称）
            If Not IsAdded Then Folders.Add(NewName & ">" & Folder.Location)
            Hint(ModLang.L("文件夹名称已更新为 ") & NewName & ModLang.L(" ！"), HintType.Green)
            '保存
            Settings.Set("LaunchFolders", Folders.Join("|"))
            McFolderListLoader.Start(IsForceRestart:=True)
        Catch ex As Exception
            Logger.Error(ex, "重命名文件夹失败")
        End Try
    End Sub

    '点击选项
    Public Sub Folder_Change(sender As MyListItem, e As RouteEventArgs)
        If Not e.RaiseByMouse OrElse Not sender.Checked Then Return
        '下载或版本修改期间禁止更改游戏文件夹
        If McInstanceLock.IsAnyLocked OrElse HasDownloadingTask(True) Then
            Hint(ModLang.L("请在其他任务完成后，再切换游戏文件夹！"), HintType.Red)
            e.Handled = True
            Return
        End If
        '更换
        McFolderSelected = CType(sender.Tag, McFolder).Location
        McFolderListLoader.Start(IsForceRestart:=True)
        LoaderFolderRun(McInstanceListLoader, McFolderSelected, LoaderFolderRunType.RunOnUpdated, MaxDepth:=1, ExtraPath:="versions\") '刷新版本列表
    End Sub

End Class
