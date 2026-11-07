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

    Private IsLoadingJars As Boolean = False

    Private Sub LoadJars()
        PanJars.Children.Clear()
        BtnExportAll.Visibility = Visibility.Collapsed
        If String.IsNullOrEmpty(CurrentMod) OrElse Not File.Exists(CurrentMod) Then
            LabTip.Text = ModLang.L("请选择一个 Mod。")
            Return
        End If
        If IsLoadingJars Then Return
        IsLoadingJars = True
        PanLoad.Visibility = Visibility.Visible
        Dim ModPath As String = CurrentMod
        RunInNewThread(
        Sub()
            Dim ErrorText As String = Nothing
            Dim Jars As New List(Of Tuple(Of String, Long, String, String)) 'FullName, Length, Name, Version
            Try
                Using Archive As New ZipArchive(New FileStream(ModPath, FileMode.Open, FileAccess.Read), ZipArchiveMode.Read)
                    For Each Entry In Archive.Entries
                        If Not (Entry.FullName.StartsWith("META-INF/jars/") AndAlso Entry.FullName.EndsWith(".jar")) Then Continue For
                        Dim Info = ReadNestedInfo(Entry)
                        Jars.Add(Tuple.Create(Entry.FullName, Entry.Length, Info.Name, Info.Version))
                    Next
                End Using
            Catch ex As Exception
                Logger.Error(ex, "读取 Jar-in-Jar 失败", LogBehavior.Toast)
                ErrorText = ex.Message
            End Try
            RunInUi(Sub() ApplyJars(ModPath, ErrorText, Jars))
        End Sub, "JarInJar Refresh")
    End Sub

    ''' <summary>把读取结果构建为界面卡片（只在 UI 线程调用）。</summary>
    Private Sub ApplyJars(ModPath As String, ErrorText As String, Jars As List(Of Tuple(Of String, Long, String, String)))
        Try
            PanLoad.Visibility = Visibility.Collapsed
            IsLoadingJars = False
            If ErrorText IsNot Nothing Then
                LabTip.Text = ModLang.L("读取失败：") & ErrorText
                Return
            End If
            If Jars.Count = 0 Then
                LabTip.Text = ModLang.L("该 Mod 不包含内嵌依赖（Jar-in-Jar）。")
                Return
            End If
            LabTip.Text = ModLang.L("共找到 ") & Jars.Count & ModLang.L(" 个内嵌依赖：")
            For Each Jar In Jars
                Dim Card As New MyCard With {.Margin = New Thickness(0, 0, 0, 8)}
                Dim Grid As New Grid With {.Margin = New Thickness(15, 10, 15, 10)}
                Grid.ColumnDefinitions.Add(New ColumnDefinition)
                Grid.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
                Card.Children.Add(Grid)

                Dim PanText As New StackPanel
                Grid.Children.Add(PanText)
                PanText.Children.Add(New TextBlock With {
                    .Text = If(Jar.Item3, IO.Path.GetFileName(Jar.Item1)),
                    .FontWeight = FontWeights.Bold,
                    .TextTrimming = TextTrimming.CharacterEllipsis
                })
                PanText.Children.Add(New TextBlock With {
                    .Text = $"{IO.Path.GetFileName(Jar.Item1)} · {Jar.Item2 \ 1024} KB" &
                            If(String.IsNullOrEmpty(Jar.Item4), "", $" · v{Jar.Item4}"),
                    .Opacity = 0.65,
                    .FontSize = 11,
                    .Margin = New Thickness(0, 3, 0, 0)
                })

                Dim BtnExport As New MyButton With {.Text = ModLang.L("导出"), .MinWidth = 90, .Padding = New Thickness(13, 0, 13, 0)}
                Grid.SetColumn(BtnExport, 1)
                Dim TargetName As String = Jar.Item1
                AddHandler BtnExport.Click, Sub(sender2 As Object, e2 As MouseButtonEventArgs) ExportEntryByName(ModPath, TargetName)
                Grid.Children.Add(BtnExport)

                PanJars.Children.Add(Card)
            Next
            BtnExportAll.Visibility = Visibility.Visible
        Catch ex As Exception
            Logger.Error(ex, "显示内嵌依赖列表失败")
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

    ''' <summary>按条目名从当前 Mod 导出（列表卡片跨线程后不再持有 ZipArchiveEntry，改用此重载）。</summary>
    Private Sub ExportEntryByName(ModPath As String, EntryFullName As String)
        Try
            Using Archive As New ZipArchive(New FileStream(ModPath, FileMode.Open, FileAccess.Read), ZipArchiveMode.Read)
                Dim Entry = Archive.GetEntry(EntryFullName)
                If Entry Is Nothing Then
                    Hint(ModLang.L("导出失败：条目已不存在。"), HintType.Red)
                    Return
                End If
                Dim Folder = IO.Path.Combine(IO.Path.GetDirectoryName(ModPath), "JiJ导出", IO.Path.GetFileNameWithoutExtension(ModPath))
                If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
                Dim Target = IO.Path.Combine(Folder, IO.Path.GetFileName(Entry.FullName))
                Using Source = Entry.Open()
                    Using Dest As New FileStream(Target, FileMode.Create, FileAccess.Write)
                        Source.CopyTo(Dest)
                    End Using
                End Using
                Hint(ModLang.L("已导出：") & IO.Path.GetFileName(Target), HintType.Green)
                OpenExplorer(Folder)
            End Using
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
