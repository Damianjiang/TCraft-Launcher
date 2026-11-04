Imports System.IO
Imports System.IO.Compression

''' <summary>
''' 压缩包工具：安全解压（拒绝路径穿越）与按目录打包（可排除指定文件名）。
''' 供世界导入 / 导出、快照与备份复用，不依赖 System.IO.Compression.FileSystem。
''' </summary>
Public Module ModArchive

    ''' <summary>
    ''' 把 zip 解压到目标目录。条目路径越界（../）会被跳过，同名文件覆盖。
    ''' </summary>
    Public Sub ExtractZip(ZipPath As String, TargetDir As String)
        DirectoryUtils.Create(TargetDir)
        Dim RootFull As String = PathUtils.AddSlashSuffix(IO.Path.GetFullPath(TargetDir))
        Using Fs As New FileStream(ZipPath, FileMode.Open, FileAccess.Read, FileShare.Read)
            Using Archive As New ZipArchive(Fs, ZipArchiveMode.Read)
                For Each Entry In Archive.Entries
                    Try
                        Dim Name As String = Entry.FullName.Replace("/"c, "\"c)
                        If String.IsNullOrWhiteSpace(Name) Then Continue For
                        Dim Dest As String = IO.Path.GetFullPath(IO.Path.Combine(RootFull, Name))
                        If Not Dest.StartsWith(RootFull, StringComparison.OrdinalIgnoreCase) Then
                            Logger.Warn($"跳过越界的压缩包条目：{Entry.FullName}")
                            Continue For
                        End If
                        If Entry.Name = "" Then
                            DirectoryUtils.Create(Dest)
                        Else
                            DirectoryUtils.Create(IO.Path.GetDirectoryName(Dest))
                            Entry.ExtractToFile(Dest, True)
                        End If
                    Catch ex As Exception
                        Logger.Warn(ex, $"解压条目失败：{Entry.FullName}")
                    End Try
                Next
            End Using
        End Using
    End Sub

    ''' <summary>
    ''' 在解压后的目录里寻找最浅的、包含 level.dat 的世界根目录。找不到返回 Nothing。
    ''' </summary>
    Public Function FindWorldRoot(Folder As String, Optional MaxDepth As Integer = 4) As String
        If FileUtils.Exists(PathUtils.AddSlashSuffix(Folder) & "level.dat") Then Return Folder
        Dim CurrentLevel As New List(Of String) From {Folder}
        For Depth As Integer = 1 To MaxDepth
            Dim NextLevel As New List(Of String)
            For Each ParentDir In CurrentLevel
                Try
                    For Each ChildDir In DirectoryUtils.EnumerateDirectories(ParentDir, False)
                        If FileUtils.Exists(PathUtils.AddSlashSuffix(ChildDir) & "level.dat") Then Return ChildDir
                        NextLevel.Add(ChildDir)
                    Next
                Catch ex As Exception
                    Logger.Warn(ex, $"扫描世界压缩包目录失败：{ParentDir}")
                End Try
            Next
            CurrentLevel = NextLevel
            If CurrentLevel.Count = 0 Then Exit For
        Next
        Return Nothing
    End Function

    ''' <summary>
    ''' 把目录打包为 zip。ExcludeNames 里的文件名（不区分大小写）会被跳过（如 session.lock）；
    ''' ExcludeDirs 里的相对目录（如 "logs"）连同其内容一起跳过。
    ''' </summary>
    Public Sub CreateZip(SourceDir As String, ZipPath As String, Optional ExcludeNames As String() = Nothing,
                         Optional ExcludeDirs As String() = Nothing)
        Dim Root As String = PathUtils.AddSlashSuffix(IO.Path.GetFullPath(SourceDir))
        Dim Skip As New HashSet(Of String)(StringComparer.OrdinalIgnoreCase)
        If ExcludeNames IsNot Nothing Then
            For Each Name In ExcludeNames
                Skip.Add(Name)
            Next
        End If
        Dim SkipDirs As New List(Of String)
        If ExcludeDirs IsNot Nothing Then
            For Each DirName In ExcludeDirs
                Dim Clean As String = DirName.Replace("\"c, "/"c).Trim("/"c).ToLowerInvariant()
                If Clean.Length > 0 Then SkipDirs.Add(Clean & "/")
            Next
        End If
        Dim Parent As String = IO.Path.GetDirectoryName(IO.Path.GetFullPath(ZipPath))
        If Not String.IsNullOrEmpty(Parent) Then DirectoryUtils.Create(Parent)
        Using Fs As New FileStream(ZipPath, FileMode.Create, FileAccess.Write, FileShare.None)
            Using Archive As New ZipArchive(Fs, ZipArchiveMode.Create)
                For Each FilePath In DirectoryUtils.EnumerateFiles(Root, True)
                    Try
                        Dim Info As New IO.FileInfo(FilePath)
                        If Skip.Contains(Info.Name) Then Continue For
                        Dim Rel As String = FilePath.Substring(Root.Length).Replace("\"c, "/"c)
                        Dim RelLower As String = Rel.ToLowerInvariant()
                        Dim Skipped As Boolean = False
                        For Each Prefix In SkipDirs
                            If RelLower.StartsWith(Prefix, StringComparison.Ordinal) Then
                                Skipped = True
                                Exit For
                            End If
                        Next
                        If Skipped Then Continue For
                        Dim Entry As ZipArchiveEntry = Archive.CreateEntry(Rel, CompressionLevel.Optimal)
                        Using OutStream = Entry.Open()
                            Using InStream As New FileStream(FilePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite)
                                InStream.CopyTo(OutStream)
                            End Using
                        End Using
                    Catch ex As Exception
                        Logger.Warn(ex, $"打包文件失败（跳过）：{FilePath}")
                    End Try
                Next
            End Using
        End Using
    End Sub

    ''' <summary>
    ''' 生成不冲突的目标路径：若已存在则追加 " (2)"、" (3)"……
    ''' </summary>
    Public Function UniquePath(ParentDir As String, BaseName As String) As String
        Dim Parent As String = PathUtils.AddSlashSuffix(ParentDir)
        Dim Candidate As String = Parent & BaseName
        If Not DirectoryUtils.Exists(Candidate) AndAlso Not FileUtils.Exists(Candidate) Then Return Candidate
        For i As Integer = 2 To 999
            Candidate = Parent & BaseName & " (" & i & ")"
            If Not DirectoryUtils.Exists(Candidate) AndAlso Not FileUtils.Exists(Candidate) Then Return Candidate
        Next
        Return Parent & BaseName & " (" & Guid.NewGuid().ToString("N").Substring(0, 6) & ")"
    End Function

End Module
