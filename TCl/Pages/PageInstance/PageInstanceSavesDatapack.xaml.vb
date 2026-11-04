Imports Microsoft.Win32
Imports Microsoft.VisualBasic.FileIO

Public Class PageInstanceSavesDatapack

    Private IsLoad As Boolean = False
    Private SavesPath As String = ""
    Private CurrentSave As String = ""

    Private Sub PageInstanceSavesDatapack_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If PageInstanceLeft.Instance Is Nothing Then Return
        SavesPath = PageInstanceLeft.Instance.PathIndie & "saves\"
        If IsLoad Then Return
        IsLoad = True
        Reload()
    End Sub

    Public Sub Reload()
        ComboSaves.Items.Clear()
        PanPacks.Children.Clear()
        LabTip.Text = ModLang.L("请选择一个存档。")
        CurrentSave = ""
        If Not Directory.Exists(SavesPath) Then
            LabTip.Text = ModLang.L("该版本还没有任何存档。")
            Return
        End If
        For Each Folder In Directory.GetDirectories(SavesPath)
            Dim Item As New MyComboBoxItem With {
                .Content = IO.Path.GetFileName(Folder),
                .Tag = Folder
            }
            ComboSaves.Items.Add(Item)
        Next
        If ComboSaves.Items.Count = 0 Then
            LabTip.Text = ModLang.L("该版本还没有任何存档。")
            Return
        End If
        ComboSaves.SelectedIndex = 0
    End Sub

    Private Sub ComboSaves_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles ComboSaves.SelectionChanged
        Dim Item = TryCast(ComboSaves.SelectedItem, MyComboBoxItem)
        If Item Is Nothing Then Return
        CurrentSave = Convert.ToString(Item.Tag)
        LoadPacks()
    End Sub

    Private Function GetPacksPath() As String
        Return IO.Path.Combine(CurrentSave, "datapacks")
    End Function

    Private Sub LoadPacks()
        PanPacks.Children.Clear()
        If String.IsNullOrEmpty(CurrentSave) Then Return
        Dim PacksPath = GetPacksPath()
        If Not Directory.Exists(PacksPath) Then
            LabTip.Text = ModLang.L("该存档没有数据包文件夹。导入数据包时会自动创建。")
            Return
        End If
        Dim Entries As New List(Of String)
        Entries.AddRange(Directory.GetFiles(PacksPath))
        Entries.AddRange(Directory.GetDirectories(PacksPath))
        If Entries.Count = 0 Then
            LabTip.Text = ModLang.L("该存档还没有安装数据包。")
            Return
        End If
        LabTip.Text = ModLang.L("共 ") & Entries.Count & ModLang.L(" 个数据包：")
        For Each Entry In Entries.OrderBy(Function(p) IO.Path.GetFileName(p))
            Dim IsFolder = Directory.Exists(Entry)
            Dim Card As New MyCard With {.Margin = New Thickness(0, 0, 0, 6)}
            Dim Grid As New Grid With {.Margin = New Thickness(15, 8, 15, 8)}
            Grid.ColumnDefinitions.Add(New ColumnDefinition)
            Grid.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
            Card.Children.Add(Grid)

            Dim PanText As New StackPanel
            Grid.Children.Add(PanText)
            PanText.Children.Add(New TextBlock With {
                .Text = IO.Path.GetFileName(Entry),
                .TextTrimming = TextTrimming.CharacterEllipsis
            })
            Dim SizeText = If(IsFolder, "文件夹", $"{New FileInfo(Entry).Length \ 1024} KB")
            PanText.Children.Add(New TextBlock With {
                .Text = SizeText & If(IO.Path.GetFileName(Entry).EndsWith(".disabled", StringComparison.OrdinalIgnoreCase), ModLang.L(" · 已禁用"), ""),
                .Opacity = 0.6,
                .FontSize = 11,
                .Margin = New Thickness(0, 2, 0, 0)
            })

            Dim PanBtn As New StackPanel With {.Orientation = Orientation.Horizontal}
            Grid.SetColumn(PanBtn, 1)
            Grid.Children.Add(PanBtn)

            Dim BtnToggle As New MyIconTextButton With {.Text = If(IO.Path.GetFileName(Entry).EndsWith(".disabled", StringComparison.OrdinalIgnoreCase), ModLang.L("启用"), ModLang.L("禁用")), .LogoScale = 0.8, .SvgIcon = "lucide/power"}
            AddHandler BtnToggle.Click, Sub(sender2 As Object, e2 As RouteEventArgs) TogglePack(Entry)
            PanBtn.Children.Add(BtnToggle)

            Dim BtnDelete As New MyIconTextButton With {.Text = ModLang.L("删除"), .LogoScale = 0.8, .SvgIcon = "lucide/trash-2"}
            AddHandler BtnDelete.Click, Sub(sender2 As Object, e2 As RouteEventArgs) DeletePack(Entry)
            PanBtn.Children.Add(BtnDelete)

            PanPacks.Children.Add(Card)
        Next
    End Sub

    Private Sub TogglePack(Entry As String)
        Try
            If Entry.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase) Then
                Dim Target = Entry.Substring(0, Entry.Length - ".disabled".Length)
                If Directory.Exists(Entry) Then
                    Directory.Move(Entry, Target)
                Else
                    File.Move(Entry, Target)
                End If
            Else
                Dim Target = Entry & ".disabled"
                If Directory.Exists(Entry) Then
                    Directory.Move(Entry, Target)
                Else
                    File.Move(Entry, Target)
                End If
            End If
            LoadPacks()
        Catch ex As Exception
            Logger.Error(ex, "切换数据包状态失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub DeletePack(Entry As String)
        If MyMsgBox(ModLang.L("确定要删除数据包“") & IO.Path.GetFileName(Entry) & ModLang.L("”吗？"), ModLang.L("删除数据包"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            If Directory.Exists(Entry) Then
                FileSystem.DeleteDirectory(Entry, UIOption.OnlyErrorDialogs, RecycleOption.SendToRecycleBin)
            Else
                FileSystem.DeleteFile(Entry, UIOption.OnlyErrorDialogs, RecycleOption.SendToRecycleBin)
            End If
            LoadPacks()
            Hint(ModLang.L("数据包已删除！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "删除数据包失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub BtnImport_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnImport.Click
        If String.IsNullOrEmpty(CurrentSave) Then
            Hint(ModLang.L("请先选择一个存档！"), HintType.Red)
            Return
        End If
        Dim Dialog As New OpenFileDialog With {
            .Title = ModLang.L("选择数据包"),
            .Filter = "数据包文件|*.zip;*.jar|所有文件|*.*",
            .Multiselect = True
        }
        If Dialog.ShowDialog() <> True Then Return
        Try
            Dim PacksPath = GetPacksPath()
            If Not Directory.Exists(PacksPath) Then Directory.CreateDirectory(PacksPath)
            For Each FilePath In Dialog.FileNames
                Dim Target = IO.Path.Combine(PacksPath, IO.Path.GetFileName(FilePath))
                File.Copy(FilePath, Target, True)
            Next
            LoadPacks()
            Hint(ModLang.L("数据包已导入！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "导入数据包失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub BtnOpenFolder_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnOpenFolder.Click
        If String.IsNullOrEmpty(CurrentSave) Then Return
        Dim PacksPath = GetPacksPath()
        If Not Directory.Exists(PacksPath) Then Directory.CreateDirectory(PacksPath)
        OpenExplorer(PacksPath)
    End Sub

End Class
