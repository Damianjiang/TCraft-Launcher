Imports System.IO.Compression
Imports Newtonsoft.Json.Linq

Public Class PageInstanceCompResource

    Private IsLoad As Boolean = False
    Private PackPath As String = ""
    Private InstanceFolder As String = ""

    Private Sub PageInstanceCompResource_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If PageInstanceLeft.Instance Is Nothing Then Return
        InstanceFolder = PageInstanceLeft.Instance.PathIndie
        If IsLoad Then Return
        IsLoad = True
        Reload()
    End Sub

    Public Sub Reload()
        PanFiles.Children.Clear()
        PackPath = ""
        For Each PackFileName In {"原始整合包.mrpack", "原始整合包.zip"}
            Dim Candidate = IO.Path.Combine(InstanceFolder, PackFileName)
            If File.Exists(Candidate) Then
                PackPath = Candidate
                Exit For
            End If
        Next
        If PackPath = "" AndAlso Directory.Exists(InstanceFolder) Then
            PackPath = Directory.GetFiles(InstanceFolder, "*.mrpack").FirstOrDefault()
            If PackPath Is Nothing Then PackPath = ""
        End If
        If PackPath = "" OrElse Not File.Exists(PackPath) Then
            PanNoPack.Visibility = Visibility.Visible
            PanContent.Visibility = Visibility.Collapsed
            Return
        End If
        PanNoPack.Visibility = Visibility.Collapsed
        PanContent.Visibility = Visibility.Visible
        Try
            Using Archive As New ZipArchive(New FileStream(PackPath, FileMode.Open, FileAccess.Read), ZipArchiveMode.Read)
                If IO.Path.GetExtension(PackPath).ToLower() = ".mrpack" Then
                    LoadModrinthPack(Archive)
                Else
                    LoadCurseForgePack(Archive)
                End If
            End Using
        Catch ex As Exception
            Logger.Error(ex, "读取整合包清单失败", LogBehavior.Toast)
            LabTip.Text = ModLang.L("读取失败：") & ex.Message
        End Try
    End Sub

    Private Sub LoadModrinthPack(Archive As ZipArchive)
        Dim Index = Archive.GetEntry("modrinth.index.json")
        If Index Is Nothing Then
            LabTip.Text = ModLang.L("整合包内缺少 modrinth.index.json。")
            Return
        End If
        Dim Json As JObject
        Using Reader As New StreamReader(Index.Open(), Text.Encoding.UTF8)
            Json = JObject.Parse(Reader.ReadToEnd())
        End Using
        LabPackName.Text = If(Json("name")?.ToString(), IO.Path.GetFileName(PackPath))
        Dim VersionId = If(Json("versionId")?.ToString(), "")
        Dim McVersion = If(Json("dependencies")?("minecraft")?.ToString(), "")
        Dim LoaderName = ""
        Dim Loaders = Json("dependencies")
        If Loaders IsNot Nothing Then
            For Each Prop In Loaders.Children(Of JProperty)()
                If Prop.Name <> "minecraft" Then LoaderName = $"{Prop.Name} {Prop.Value}"
            Next
        End If
        LabPackInfo.Text = ModLang.L("Modrinth 整合包 · 版本 ") & VersionId & " · Minecraft " & McVersion & " " & LoaderName
        Dim Files = Json("files")
        If Files Is Nothing Then
            LabTip.Text = ModLang.L("清单中没有文件。")
            Return
        End If
        LabTip.Text = ModLang.L("共 ") & (CType(Files, JArray).Count) & ModLang.L(" 个文件，绿色表示已存在：")
        For Each Item In Files
            Dim RelativePath = Item("path")?.ToString()
            Dim Size As Long = 0
            Long.TryParse(If(Item("fileSize")?.ToString(), "0"), Size)
            Dim Downloads = Item("downloads")
            Dim Url = If(Downloads IsNot Nothing AndAlso Downloads.HasValues, Downloads.First.ToString(), Nothing)
            AddFileRow(RelativePath, Size, Url)
        Next
    End Sub

    Private Sub LoadCurseForgePack(Archive As ZipArchive)
        Dim Manifest = Archive.GetEntry("manifest.json")
        If Manifest Is Nothing Then
            LabTip.Text = ModLang.L("整合包内缺少 manifest.json。")
            Return
        End If
        Dim Json As JObject
        Using Reader As New StreamReader(Manifest.Open(), Text.Encoding.UTF8)
            Json = JObject.Parse(Reader.ReadToEnd())
        End Using
        LabPackName.Text = If(Json("name")?.ToString(), IO.Path.GetFileName(PackPath))
        Dim Version = If(Json("version")?.ToString(), "")
        Dim Author = If(Json("author")?.ToString(), "")
        Dim McVersion = If(Json("minecraft")?("version")?.ToString(), "")
        LabPackInfo.Text = ModLang.L("CurseForge 整合包 · 版本 ") & Version & ModLang.L(" · 作者 ") & Author & " · Minecraft " & McVersion
        Dim Files = Json("files")
        If Files Is Nothing Then
            LabTip.Text = ModLang.L("清单中没有文件。")
            Return
        End If
        LabTip.Text = ModLang.L("共 ") & (CType(Files, JArray).Count) & ModLang.L(" 个文件（CurseForge 仅记录项目与文件 ID）：")
        For Each Item In Files
            Dim ProjectId = If(Item("projectID")?.ToString(), "")
            Dim FileId = If(Item("fileID")?.ToString(), "")
            Dim RelativePath = $"project {ProjectId} / file {FileId}"
            AddFileRow(RelativePath, 0, Nothing)
        Next
    End Sub

    Private Sub AddFileRow(RelativePath As String, Size As Long, Url As String)
        If String.IsNullOrEmpty(RelativePath) Then Return
        Dim Target = IO.Path.Combine(InstanceFolder, RelativePath.Replace("/", "\"))
        Dim Exists = File.Exists(Target)
        Dim Card As New MyCard With {.Margin = New Thickness(0, 0, 0, 6)}
        Dim Grid As New Grid With {.Margin = New Thickness(15, 8, 15, 8)}
        Grid.ColumnDefinitions.Add(New ColumnDefinition)
        Grid.ColumnDefinitions.Add(New ColumnDefinition With {.Width = GridLength.Auto})
        Card.Children.Add(Grid)

        Dim PanText As New StackPanel
        Grid.Children.Add(PanText)
        PanText.Children.Add(New TextBlock With {
            .Text = RelativePath,
            .TextTrimming = TextTrimming.CharacterEllipsis,
            .Foreground = If(Exists, CType(FindResource("ColorBrush1"), Brush), CType(FindResource("ColorBrush3"), Brush))
        })
        PanText.Children.Add(New TextBlock With {
            .Text = If(Size > 0, $"{Size \ 1024} KB · ", "") & If(Exists, ModLang.L("已存在"), ModLang.L("缺失")),
            .Opacity = 0.6,
            .FontSize = 11,
            .Margin = New Thickness(0, 2, 0, 0)
        })

        If Not Exists AndAlso Not String.IsNullOrEmpty(Url) Then
            Dim BtnDownload As New MyButton With {.Text = ModLang.L("下载"), .MinWidth = 90, .Padding = New Thickness(13, 0, 13, 0)}
            Grid.SetColumn(BtnDownload, 1)
            AddHandler BtnDownload.Click, Sub(sender As Object, e As MouseButtonEventArgs) DownloadFile(Url, Target)
            Grid.Children.Add(BtnDownload)
        End If
        PanFiles.Children.Add(Card)
    End Sub

    Private Sub DownloadFile(Url As String, Target As String)
        Try
            Dim Folder = IO.Path.GetDirectoryName(Target)
            If Not Directory.Exists(Folder) Then Directory.CreateDirectory(Folder)
            Dim Loader As New LoaderCombo(Of String)("下载整合包资源 " & IO.Path.GetFileName(Target),
                {New LoaderDownload("下载文件", {New NetFile({Url}, Target)}) With {.Block = True}}) With {
                .OnStateChanged = Sub(Task)
                                       If Task.State = LoadState.Finished Then
                                           Hint(ModLang.L("下载完成：") & IO.Path.GetFileName(Target), HintType.Green)
                                       ElseIf Task.State = LoadState.Failed Then
                                           Hint(ModLang.L("下载失败：") & IO.Path.GetFileName(Target), HintType.Red)
                                       End If
                                   End Sub
            }
            Loader.Start()
        Catch ex As Exception
            Logger.Error(ex, "下载整合包资源失败", LogBehavior.Toast)
        End Try
    End Sub

End Class
