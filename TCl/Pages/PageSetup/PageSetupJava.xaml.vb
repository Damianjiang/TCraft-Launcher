Public Class PageSetupJava

    Private Sub PageSetupJava_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        '重复加载部分
        PanBack.ScrollToHome()
        AniControlEnabled += 1
        LoadList()
        AniControlEnabled -= 1

        '非重复加载部分
        Static Reloaded As Boolean = False
        If Reloaded Then Return
        Reloaded = True
    End Sub

    ''' <summary>
    ''' 加载时若正在搜索，则由 ModJava.UpdateJavaLists 自动刷新。
    ''' </summary>
    Public Sub Refresh()
        LoadList()
    End Sub

    ''' <summary>
    ''' 重新载入 Java 列表。
    ''' </summary>
    Public Sub LoadList()
        If PanList Is Nothing Then Return
        PanList.Children.Clear()
        Try
            Dim IsSearching = JavaListRefreshWorker.Running
            BtnSearch.IsEnabled = Not IsSearching
            LabStatus.Text = If(IsSearching, ModLang.L("正在搜索 Java …"), "")

            Dim JavaEntries As List(Of Java) = Configs.JavaList.Get()
            If JavaEntries.Count = 0 Then
                If Not IsSearching Then
                    PanList.Children.Add(New TextBlock With {
                        .Text = ModLang.L("未找到任何 Java。点击上方「导入 Java…」手动指定 java.exe，或点击「重新搜索」自动扫描。"),
                        .Margin = New Thickness(0, 5, 0, 5), .FontSize = 13,
                        .TextWrapping = TextWrapping.Wrap, .HorizontalAlignment = HorizontalAlignment.Left
                    })
                End If
                Return
            End If

            For i = 0 To JavaEntries.Count - 1
                PanList.Children.Add(ItemBuild(JavaEntries(i), i, JavaEntries.Count))
            Next
        Catch ex As Exception
            Logger.Error(ex, "加载 Java 列表失败")
        End Try
    End Sub

    ''' <summary>
    ''' 构建单个 Java 的列表项。
    ''' </summary>
    Private Function ItemBuild(JavaEntry As Java, Index As Integer, Total As Integer) As MyListItem
        Dim VersionDesc As String = If(JavaEntry.Version Is Nothing, ModLang.L("尚未检查"), "Java " & JavaEntry.Version.Major.ToString)
        Dim NewItem As New MyListItem With {
            .FontSize = 13, .Height = 42, .IsScaleAnimationEnabled = False,
            .Type = MyListItem.CheckType.Clickable,
            .Tag = JavaEntry,
            .Title = If(Index = 0, VersionDesc & ModLang.L("（默认）"), VersionDesc),
            .Info = JavaEntry.Folder
        }

        Dim Buttons As New List(Of MyIconButton)
        '提高优先级
        Dim UpButton As New MyIconButton With {.SvgIcon = "lucide/arrow-up", .LogoScale = 0.95, .Height = 30, .Width = 30}
        UpButton.IsEnabled = Index > 0
        UpButton.ToolTip = ModLang.L("提高优先级（更优先被选中）")
        ToolTipService.SetShowOnDisabled(UpButton, True)
        AddHandler UpButton.Click, Sub() MoveJavaInList(JavaEntry, -1)
        Buttons.Add(UpButton)
        '降低优先级
        Dim DownButton As New MyIconButton With {.SvgIcon = "lucide/arrow-down", .LogoScale = 0.95, .Height = 30, .Width = 30}
        DownButton.IsEnabled = Index < Total - 1
        DownButton.ToolTip = ModLang.L("降低优先级")
        ToolTipService.SetShowOnDisabled(DownButton, True)
        AddHandler DownButton.Click, Sub() MoveJavaInList(JavaEntry, 1)
        Buttons.Add(DownButton)
        '详细信息
        Dim InfoButton As New MyIconButton With {.SvgIcon = "lucide/info", .Height = 30, .Width = 30}
        InfoButton.ToolTip = ModLang.L("查看详细信息")
        AddHandler InfoButton.Click, Sub() ShowJavaDetail(JavaEntry)
        Buttons.Add(InfoButton)
        '打开文件夹
        Dim OpenButton As New MyIconButton With {.SvgIcon = "lucide/folder-open", .LogoScale = 1.1, .Height = 30, .Width = 30}
        OpenButton.ToolTip = ModLang.L("打开文件夹")
        AddHandler OpenButton.Click, Sub()
            Try
                OpenExplorer(JavaEntry.Folder)
            Catch ex As Exception
                Hint(ModLang.L("无法打开该文件夹，请检查 Java 是否仍存在。"), HintType.Red)
            End Try
        End Sub
        Buttons.Add(OpenButton)
        '从列表移除 / 卸载
        Dim IsOfficial As Boolean = PathUtils.IsParentOf($"{Paths.AppData}.minecraft\runtime\", JavaEntry.Folder)
        '第 72 轮修复：原用「停止」图标表示移除，语义不符 → 统一为删除图标
        '第 126 轮：官方运行时支持卸载（连同删除文件），本地 Java 仍仅从列表移除
        Dim DeleteButton As New MyIconButton With {.SvgIcon = "lucide/trash-2", .Height = 30, .Width = 30}
        DeleteButton.ToolTip = If(IsOfficial, ModLang.L("卸载并删除该官方 Java"), ModLang.L("从列表中移除"))
        AddHandler DeleteButton.Click,
            Sub()
                If IsOfficial Then
                    ManuallyUninstallJava(JavaEntry)
                Else
                    ManuallyRemoveJava(JavaEntry)
                End If
                LoadList()
            End Sub
        Buttons.Add(DeleteButton)

        NewItem.Buttons = Buttons
        AddHandler NewItem.MouseLeftButtonUp, Sub(sender As Object, e As MouseButtonEventArgs) e.Handled = True
        AddHandler NewItem.Click, Sub() ShowJavaDetail(JavaEntry)
        Return NewItem
    End Function

    ''' <summary>
    ''' 在列表中上下移动指定 Java，以调整优先级。
    ''' </summary>
    Private Sub MoveJavaInList(JavaEntry As Java, Offset As Integer)
        Try
            Configs.JavaList.Edit(
            Sub(ByRef List)
                Dim Index = List.IndexOf(JavaEntry)
                Dim NewIndex = Index + Offset
                If Index < 0 OrElse NewIndex < 0 OrElse NewIndex >= List.Count Then Return
                List.RemoveAt(Index)
                List.Insert(NewIndex, JavaEntry)
            End Sub)
            LoadList()
            Hint(ModLang.L("已调整该 Java 的优先级。"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "调整 Java 优先级失败")
        End Try
    End Sub

    ''' <summary>
    ''' 显示指定 Java 的详细信息。
    ''' </summary>
    Private Sub ShowJavaDetail(JavaEntry As Java)
        Try
            Dim VersionText As String = If(JavaEntry.Version Is Nothing, "尚未检查", JavaEntry.Version.ToString)
            Dim EncodingText As String = If(String.IsNullOrEmpty(JavaEntry.FileEncodingName), "未知", JavaEntry.FileEncodingName)
            Dim NativeText As String = If(String.IsNullOrEmpty(JavaEntry.NativeEncodingName), "未知", JavaEntry.NativeEncodingName)
            Dim VendorText As String = If(String.IsNullOrEmpty(JavaEntry.Vendor), "未知", JavaEntry.Vendor)
            Dim IsOfficial As Boolean = PathUtils.IsParentOf($"{Paths.AppData}.minecraft\runtime\", JavaEntry.Folder)
            '进入列表的 Java 都通过了 64 位检查（32 位会在检查时被拒绝），架构由此保证
            Dim Message As String =
                ModLang.L("Java 路径：") & JavaEntry.JavaExePath & vbCrLf &
                ModLang.L("版本：") & VersionText & vbCrLf &
                ModLang.L("供应商：") & VendorText & vbCrLf &
                ModLang.L("架构：") & ModLang.L("64 位（x64）") & vbCrLf & ModLang.L("字符编码：") & EncodingText & vbCrLf &
                ModLang.L("本地编码：") & NativeText & vbCrLf &
                "是否官方 Java：" & If(IsOfficial, "是（" & PathUtils.GetLastPart(JavaEntry.Folder.TrimEnd("\"c)) & "）", "否")
            MyMsgBox(Message, ModLang.L("Java 详细信息"))
        Catch ex As Exception
            Logger.Error(ex, "显示 Java 详细信息失败")
        End Try
    End Sub

#Region "按钮事件"

    Private Sub BtnImport_Click(sender As Object, e As MouseButtonEventArgs)
        ManuallyImportJava()
        LoadList()
    End Sub

    Private Sub BtnDownload_Click(sender As Object, e As MouseButtonEventArgs)
        DownloadJavaManually()
    End Sub

    Private Sub BtnSearch_Click(sender As Object, e As MouseButtonEventArgs)
        ManuallySearchJava()
        LoadList()
    End Sub

    Private Sub BtnOpenFolder_Click(sender As Object, e As MouseButtonEventArgs)
        Try
            Dim Folder = Paths.BaseThenName
            If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
            OpenExplorer(Folder)
        Catch ex As Exception
            Logger.Error(ex, "打开数据文件夹失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub BtnReset_Click(sender As Object, e As MouseButtonEventArgs)
        If MyMsgBox(ModLang.L("确定要初始化 Java 管理吗？") & vbCrLf & ModLang.L("这将清空已保存的 Java 列表，并重新搜索电脑上所有的 Java。该操作不可撤销。"),
                    ModLang.L("初始化确认"),, ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            Configs.JavaList.Reset()
            Configs.JavaRemovedList.Reset()
            ManuallySearchJava()
            Hint(ModLang.L("已初始化 Java 管理！"), HintType.Green)
            LoadList()
        Catch ex As Exception
            Logger.Error(ex, "初始化 Java 管理失败", LogBehavior.Toast)
        End Try
    End Sub

#End Region

End Class
