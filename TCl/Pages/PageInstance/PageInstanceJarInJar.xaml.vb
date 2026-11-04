Imports System.IO.Compression
Imports Newtonsoft.Json.Linq

Public Class PageInstanceJarInJar

    Private IsLoad As Boolean = False
    Private ModsPath As String = ""
    Private CurrentMod As String = ""

    Private Sub PageInstanceJarInJar_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If PageInstanceLeft.Instance Is Nothing Then Return
        ModsPath = PageInstanceLeft.Instance.PathIndie & "mods\"
        If IsLoad Then Return
        IsLoad = True
        Reload()
    End Sub

    Public Sub Reload()
        ComboMods.Items.Clear()
        PanJars.Children.Clear()
        LabTip.Text = ModLang.L("请选择一个 Mod。")
        BtnExportAll.Visibility = Visibility.Collapsed
        CurrentMod = ""
        If Not Directory.Exists(ModsPath) Then
            PanNoMod.Visibility = Visibility.Visible
            PanContent.Visibility = Visibility.Collapsed
            Return
        End If
        Dim Files = Directory.GetFiles(ModsPath, "*.jar").OrderBy(Function(f) IO.Path.GetFileName(f)).ToList()
        If Files.Count = 0 Then
            PanNoMod.Visibility = Visibility.Visible
            PanContent.Visibility = Visibility.Collapsed
            Return
        End If
        PanNoMod.Visibility = Visibility.Collapsed
        PanContent.Visibility = Visibility.Visible
        For Each FilePath In Files
            Dim Item As New MyComboBoxItem With {
                .Content = IO.Path.GetFileName(FilePath),
                .Tag = FilePath
            }
            ComboMods.Items.Add(Item)
        Next
        ComboMods.SelectedIndex = 0
    End Sub

    Private Sub BtnRefresh_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnRefresh.Click
        Reload()
    End Sub

    Private Sub ComboMods_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles ComboMods.SelectionChanged
        Dim Item = TryCast(ComboMods.SelectedItem, MyComboBoxItem)
        If Item Is Nothing Then Return
        CurrentMod = Convert.ToString(Item.Tag)
        LoadJars()
    End Sub

    Private Sub LoadJars()
        PanJars.Children.Clear()
        BtnExportAll.Visibility = Visibility.Collapsed
        If String.IsNullOrEmpty(CurrentMod) OrElse Not File.Exists(CurrentMod) Then
            LabTip.Text = ModLang.L("请选择一个 Mod。")
            Return
        End If
        Try
            Using Archive As New ZipArchive(New FileStream(CurrentMod, FileMode.Open, FileAccess.Read), ZipArchiveMode.Read)
                Dim Jars = Archive.Entries.Where(Function(entry) entry.FullName.StartsWith("META-INF/jars/") AndAlso entry.FullName.EndsWith(".jar")).ToList()
                If Jars.Count = 0 Then
                    LabTip.Text = ModLang.L("该 Mod 不包含内嵌依赖（Jar-in-Jar）。")
                    Return
                End If
                LabTip.Text = ModLang.L("共找到 ") & Jars.Count & ModLang.L(" 个内嵌依赖：")
                For Each Entry In Jars
                    Dim Info = ReadNestedInfo(Entry)
                    Dim Card As New MyCard With {.Margin = New Thickness(0, 0, 0, 8)}
                    Dim Grid As New Grid With {.Margin = New Thickness(15, 10, 15, 10)}
                    Grid.ColumnDefinitions.Add(New ColumnDefinition)
                    Grid.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
                    Card.Children.Add(Grid)

                    Dim PanText As New StackPanel
                    Grid.Children.Add(PanText)
                    PanText.Children.Add(New TextBlock With {
                        .Text = If(Info.Name, IO.Path.GetFileName(Entry.FullName)),
                        .FontWeight = FontWeights.Bold,
                        .TextTrimming = TextTrimming.CharacterEllipsis
                    })
                    PanText.Children.Add(New TextBlock With {
                        .Text = $"{IO.Path.GetFileName(Entry.FullName)} · {Entry.Length \ 1024} KB" &
                                If(String.IsNullOrEmpty(Info.Version), "", $" · v{Info.Version}"),
                        .Opacity = 0.65,
                        .FontSize = 11,
                        .Margin = New Thickness(0, 3, 0, 0)
                    })

                    Dim BtnExport As New MyButton With {.Text = ModLang.L("导出"), .MinWidth = 90, .Padding = New Thickness(13, 0, 13, 0)}
                    Grid.SetColumn(BtnExport, 1)
                    Dim TargetEntry = Entry
                    AddHandler BtnExport.Click, Sub(sender2 As Object, e2 As MouseButtonEventArgs) ExportEntry(TargetEntry)
                    Grid.Children.Add(BtnExport)

                    PanJars.Children.Add(Card)
                Next
                BtnExportAll.Visibility = Visibility.Visible
            End Using
        Catch ex As Exception
            Logger.Error(ex, "读取 Jar-in-Jar 失败", LogBehavior.Toast)
            LabTip.Text = ModLang.L("读取失败：") & ex.Message
        End Try
    End Sub

    Private Function ReadNestedInfo(Entry As ZipArchiveEntry) As (Name As String, Version As String)
        Try
            Using Stream = Entry.Open()
                Using Buffer As New MemoryStream()
                    Stream.CopyTo(Buffer)
                    Buffer.Position = 0
                    Using Nested As New ZipArchive(Buffer, ZipArchiveMode.Read)
                        Dim Meta = Nested.GetEntry("fabric.mod.json")
                        If Meta Is Nothing Then Return (Nothing, "")
                        Using Reader As New StreamReader(Meta.Open(), Text.Encoding.UTF8)
                            Dim Json = JObject.Parse(Reader.ReadToEnd())
                            Return (If(Json("name")?.ToString(), Json("id")?.ToString()), If(Json("version")?.ToString(), ""))
                        End Using
                    End Using
                End Using
            End Using
        Catch
            Return (Nothing, "")
        End Try
    End Function

    Private Sub ExportEntry(Entry As ZipArchiveEntry)
        Try
            Dim Folder = IO.Path.Combine(IO.Path.GetDirectoryName(CurrentMod), "JiJ导出", IO.Path.GetFileNameWithoutExtension(CurrentMod))
            If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
            Dim Target = IO.Path.Combine(Folder, IO.Path.GetFileName(Entry.FullName))
            Using Source = Entry.Open()
                Using Dest As New FileStream(Target, FileMode.Create, FileAccess.Write)
                    Source.CopyTo(Dest)
                End Using
            End Using
            Hint(ModLang.L("已导出：") & IO.Path.GetFileName(Target), HintType.Green)
            OpenExplorer(Folder)
        Catch ex As Exception
            Logger.Error(ex, "导出内嵌依赖失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub BtnExportAll_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnExportAll.Click
        If String.IsNullOrEmpty(CurrentMod) Then Return
        Try
            Dim Folder = IO.Path.Combine(IO.Path.GetDirectoryName(CurrentMod), "JiJ导出", IO.Path.GetFileNameWithoutExtension(CurrentMod))
            If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
            Dim Count = 0
            Using Archive As New ZipArchive(New FileStream(CurrentMod, FileMode.Open, FileAccess.Read), ZipArchiveMode.Read)
                For Each Entry In Archive.Entries
                    If Not (Entry.FullName.StartsWith("META-INF/jars/") AndAlso Entry.FullName.EndsWith(".jar")) Then Continue For
                    Dim Target = IO.Path.Combine(Folder, IO.Path.GetFileName(Entry.FullName))
                    Using Source = Entry.Open()
                        Using Dest As New FileStream(Target, FileMode.Create, FileAccess.Write)
                            Source.CopyTo(Dest)
                        End Using
                    End Using
                    Count += 1
                Next
            End Using
            Hint(ModLang.L("已导出 ") & Count & ModLang.L(" 个内嵌依赖！"), HintType.Green)
            OpenExplorer(Folder)
        Catch ex As Exception
            Logger.Error(ex, "导出全部内嵌依赖失败", LogBehavior.Toast)
        End Try
    End Sub

End Class
