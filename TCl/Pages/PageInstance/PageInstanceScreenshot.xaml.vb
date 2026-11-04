Imports System.IO
Imports System.Windows.Media.Imaging
Imports Microsoft.VisualBasic.FileIO

Public Class PageInstanceScreenshot

    Private IsLoad As Boolean = False
    Private ScreenshotPath As String = ""
    Private FileList As New List(Of String)
    Private Offset As Integer = 0
    Private AppendLock As Boolean = False
    Private Const PageSize As Integer = 20

    Private Shared ReadOnly AllowedSuffix As String() = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.webp", "*.tiff"}

    Private Sub PageInstanceScreenshot_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        PanBack.ScrollToHome()
        If PageInstanceLeft.Instance Is Nothing Then Return
        ScreenshotPath = PageInstanceLeft.Instance.PathIndie & "screenshots\"
        If Not Directory.Exists(ScreenshotPath) Then Directory.CreateDirectory(ScreenshotPath)
        Reload()
        If IsLoad Then Return
        IsLoad = True
    End Sub

    Private Sub BtnOpenFolder_Click(sender As Object, e As MouseButtonEventArgs) Handles BtnOpenFolder.Click, BtnOpenFolderTop.Click
        If Not Directory.Exists(ScreenshotPath) Then Directory.CreateDirectory(ScreenshotPath)
        OpenExplorer(ScreenshotPath)
    End Sub

    Public Sub Reload()
        FileList.Clear()
        If Directory.Exists(ScreenshotPath) Then
            For Each Suffix In AllowedSuffix
                FileList.AddRange(Directory.GetFiles(ScreenshotPath, Suffix, IO.SearchOption.TopDirectoryOnly))
            Next
            FileList = FileList.OrderByDescending(Function(f) File.GetCreationTime(f)).ToList()
        End If
        PanList.Children.Clear()
        Offset = 0
        RefreshTip()
        Append()
    End Sub

    Private Sub RefreshTip()
        If FileList.Count = 0 Then
            PanNoPic.Visibility = Visibility.Visible
            PanContent.Visibility = Visibility.Collapsed
        Else
            PanNoPic.Visibility = Visibility.Collapsed
            PanContent.Visibility = Visibility.Visible
        End If
    End Sub

    Private Sub Append()
        If AppendLock Then Return
        AppendLock = True
        Try
            Dim StartIndex = Offset * PageSize
            For i = StartIndex To Math.Min(StartIndex + PageSize - 1, FileList.Count - 1)
                Dim FilePath = FileList(i)
                If Not File.Exists(FilePath) Then Continue For
                If File.GetAttributes(FilePath).HasFlag(FileAttributes.Hidden) Then Continue For
                If New FileInfo(FilePath).Length = 0 Then Continue For
                Try
                    PanList.Children.Add(CreateCard(FilePath))
                Catch ex As Exception
                    Logger.Error(ex, $"创建截图预览失败：{FilePath}", LogBehavior.Toast)
                End Try
            Next
            Offset += 1
        Finally
            AppendLock = False
        End Try
    End Sub

    Private Function CreateCard(FilePath As String) As MyCard
        Dim Card As New MyCard With {
            .Margin = New Thickness(7),
            .Tag = FilePath,
            .ToolTip = FilePath.Replace(ScreenshotPath, "")
        }
        Dim Grid As New Grid
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = New GridLength(9)})
        Grid.RowDefinitions.Add(New RowDefinition With {.Height = New GridLength(120)})
        Grid.RowDefinitions.Add(New RowDefinition)
        Card.Children.Add(Grid)

        Dim Preview As New Image With {
            .Stretch = Stretch.Uniform,
            .Cursor = Cursors.Hand,
            .Source = LoadPreview(FilePath)
        }
        AddHandler Preview.MouseLeftButtonDown, Sub(sender As Object, e As MouseButtonEventArgs)
                                                    ShowViewer(FilePath)
                                                End Sub
        Grid.SetRow(Preview, 1)
        Grid.Children.Add(Preview)

        Dim PanBtn As New StackPanel With {
            .Orientation = Orientation.Horizontal,
            .HorizontalAlignment = HorizontalAlignment.Center,
            .Margin = New Thickness(3, 5, 3, 5)
        }
        Grid.SetRow(PanBtn, 2)
        Grid.Children.Add(PanBtn)

        Dim BtnOpen As New MyIconTextButton With {.Text = ModLang.L("打开"), .LogoScale = 0.8, .SvgIcon = "lucide/folder-open", .Tag = FilePath}
        AddHandler BtnOpen.Click, Sub(sender As Object, e As RouteEventArgs) OpenExplorer(FilePath)
        PanBtn.Children.Add(BtnOpen)

        Dim BtnDelete As New MyIconTextButton With {.Text = ModLang.L("删除"), .LogoScale = 0.8, .SvgIcon = "lucide/trash-2", .Tag = FilePath}
        AddHandler BtnDelete.Click, Sub(sender As Object, e As RouteEventArgs) DeleteScreenshot(FilePath)
        PanBtn.Children.Add(BtnDelete)

        Dim BtnCopy As New MyIconTextButton With {.Text = ModLang.L("复制"), .LogoScale = 0.8, .SvgIcon = "lucide/copy", .Tag = FilePath}
        AddHandler BtnCopy.Click, Sub(sender As Object, e As RouteEventArgs) CopyScreenshot(FilePath)
        PanBtn.Children.Add(BtnCopy)

        Card.Opacity = 0
        AniStart(AaOpacity(Card, 1, 200), "Screenshot Fade " & GetUuid())
        Return Card
    End Function

    Private Shared Function LoadPreview(FilePath As String) As BitmapImage
        Using Fs As New FileStream(FilePath, FileMode.Open, FileAccess.Read)
            Dim Bitmap As New BitmapImage
            Bitmap.BeginInit()
            Bitmap.DecodePixelHeight = 200
            Bitmap.DecodePixelWidth = 400
            Bitmap.CacheOption = BitmapCacheOption.OnLoad
            Bitmap.StreamSource = Fs
            Bitmap.EndInit()
            Bitmap.Freeze()
            Return Bitmap
        End Using
    End Function

    Private Sub DeleteScreenshot(FilePath As String)
        Try
            FileSystem.DeleteFile(FilePath, UIOption.OnlyErrorDialogs, RecycleOption.SendToRecycleBin)
            FileList.Remove(FilePath)
            For Each Child As MyCard In PanList.Children.OfType(Of MyCard)().ToList()
                If Child.Tag IsNot Nothing AndAlso Child.Tag.ToString() = FilePath Then PanList.Children.Remove(Child)
            Next
            RefreshTip()
            Hint(ModLang.L("截图已删除！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "删除截图失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub CopyScreenshot(FilePath As String)
        If Not File.Exists(FilePath) Then
            Hint(ModLang.L("截图文件不存在！"), HintType.Red)
            Return
        End If
        For TryTime = 1 To 5
            Try
                Clipboard.SetImage(New BitmapImage(New Uri(FilePath)))
                Hint(ModLang.L("截图已复制到剪贴板！"), HintType.Green)
                Return
            Catch ex As Exception
                Logger.Error(ex, $"第 {TryTime} 次复制截图失败", LogBehavior.Toast)
            End Try
        Next
        Hint(ModLang.L("复制截图失败！"), HintType.Red)
    End Sub

#Region "大图查看"

    Private ViewerIndex As Integer = -1

    ''' <summary>当前查看的文件（没有时返回空字符串）。</summary>
    Private Function CurrentViewerFile() As String
        If ViewerIndex < 0 OrElse ViewerIndex >= FileList.Count Then Return ""
        Return FileList(ViewerIndex)
    End Function

    ''' <summary>打开大图查看器（复刻 LumilioCL 截图页的「查看截图」）。</summary>
    Private Sub ShowViewer(FilePath As String)
        If Not File.Exists(FilePath) Then Return
        ViewerIndex = FileList.IndexOf(FilePath)
        If ViewerIndex < 0 Then
            FileList.Insert(0, FilePath)
            ViewerIndex = 0
        End If
        PanViewer.Visibility = Visibility.Visible
        UpdateViewer()
        Try
            PanViewer.Focus()
            Keyboard.Focus(PanViewer)
        Catch ex As Exception
            Logger.Warn(ex, "大图查看器获取键盘焦点失败")
        End Try
    End Sub

    Private Sub UpdateViewer()
        If ViewerIndex < 0 OrElse ViewerIndex >= FileList.Count Then
            CloseViewer()
            Return
        End If
        Dim FilePath As String = FileList(ViewerIndex)
        If Not File.Exists(FilePath) Then
            FileList.RemoveAt(ViewerIndex)
            If FileList.Count = 0 Then
                CloseViewer()
                Return
            End If
            If ViewerIndex >= FileList.Count Then ViewerIndex = FileList.Count - 1
        Else
            Try
                Dim Bmp As New BitmapImage
                Bmp.BeginInit()
                Bmp.CacheOption = BitmapCacheOption.OnLoad
                Bmp.DecodePixelWidth = 1600
                Bmp.UriSource = New Uri(FilePath)
                Bmp.EndInit()
                Bmp.Freeze()
                ImgViewer.Source = Bmp
            Catch ex As Exception
                Logger.Error(ex, "加载截图大图失败")
                ImgViewer.Source = Nothing
            End Try
        End If
        LabViewer.Text = (ViewerIndex + 1) & " / " & FileList.Count & " · " & Path.GetFileName(CurrentViewerFile())
        BtnViewerPrev.IsEnabled = ViewerIndex > 0
        BtnViewerNext.IsEnabled = ViewerIndex < FileList.Count - 1
    End Sub

    Private Sub CloseViewer()
        PanViewer.Visibility = Visibility.Collapsed
        ImgViewer.Source = Nothing
        ViewerIndex = -1
    End Sub

    Private Sub MoveViewer(Delta As Integer)
        If PanViewer.Visibility <> Visibility.Visible Then Return
        Dim NextIndex As Integer = ViewerIndex + Delta
        If NextIndex < 0 OrElse NextIndex >= FileList.Count Then Return
        ViewerIndex = NextIndex
        UpdateViewer()
    End Sub

    Private Sub BtnViewerPrev_Click(sender As Object, e As MouseButtonEventArgs)
        MoveViewer(-1)
    End Sub

    Private Sub BtnViewerNext_Click(sender As Object, e As MouseButtonEventArgs)
        MoveViewer(1)
    End Sub

    Private Sub BtnViewerClose_Click(sender As Object, e As MouseButtonEventArgs)
        CloseViewer()
    End Sub

    Private Sub BtnViewerCopy_Click(sender As Object, e As MouseButtonEventArgs)
        Dim FilePath As String = CurrentViewerFile()
        If String.IsNullOrEmpty(FilePath) Then Return
        CopyScreenshot(FilePath)
    End Sub

    Private Sub BtnViewerOpen_Click(sender As Object, e As MouseButtonEventArgs)
        Dim FilePath As String = CurrentViewerFile()
        If String.IsNullOrEmpty(FilePath) Then Return
        Try
            OpenExplorer(FilePath)
        Catch ex As Exception
            Logger.Error(ex, "打开截图失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub BtnViewerDelete_Click(sender As Object, e As MouseButtonEventArgs)
        Dim FilePath As String = CurrentViewerFile()
        If String.IsNullOrEmpty(FilePath) Then Return
        If MyMsgBox(ModLang.L("删除这张截图？删除后无法找回。") & vbCrLf & Path.GetFileName(FilePath),
                    ModLang.L("删除截图"), ModLang.L("删除"), ModLang.L("取消"), IsWarn:=True) <> 1 Then Return
        Try
            FileSystem.DeleteFile(FilePath, UIOption.OnlyErrorDialogs, RecycleOption.SendToRecycleBin)
            FileList.Remove(FilePath)
            For Each Child As MyCard In PanList.Children.OfType(Of MyCard)().ToList()
                If Child.Tag IsNot Nothing AndAlso Child.Tag.ToString() = FilePath Then PanList.Children.Remove(Child)
            Next
            RefreshTip()
            If FileList.Count = 0 Then
                CloseViewer()
            Else
                If ViewerIndex >= FileList.Count Then ViewerIndex = FileList.Count - 1
                UpdateViewer()
            End If
            Hint(ModLang.L("截图已删除！"), HintType.Green)
        Catch ex As Exception
            Logger.Error(ex, "删除截图失败", LogBehavior.Toast)
        End Try
    End Sub

    Private Sub PanViewer_KeyDown(sender As Object, e As KeyEventArgs)
        Select Case e.Key
            Case Key.Escape
                CloseViewer()
                e.Handled = True
            Case Key.Left
                MoveViewer(-1)
                e.Handled = True
            Case Key.Right
                MoveViewer(1)
                e.Handled = True
        End Select
    End Sub

#End Region

    Private Sub PanBack_ScrollChanged(sender As Object, e As ScrollChangedEventArgs) Handles PanBack.ScrollChanged
        If FileList.Count = 0 OrElse AppendLock Then Return
        If PanBack.VerticalOffset + PanBack.ViewportHeight >= PanBack.ExtentHeight - 50 Then Append()
    End Sub

End Class
