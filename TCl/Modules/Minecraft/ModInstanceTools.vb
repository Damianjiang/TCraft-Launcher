Imports System.IO
Imports Newtonsoft.Json.Linq

''' <summary>
''' 实例级工具：复制实例、完整备份、从备份恢复（对齐 LumilioCL 游戏页的「复制游戏 / 完整备份 / 从备份恢复」）。
''' 全部按「不覆盖已有实例」处理：重名自动加序号。
''' </summary>
Public Module ModInstanceTools

    ''' <summary>校验新实例名：不能为空、不能含路径非法字符、不能与现有版本重名。</summary>
    Public Function ValidateInstanceName(Name As String, VersionsRoot As String) As String
        Dim Trimmed As String = If(Name, "").Trim()
        If Trimmed.Length = 0 Then Return "名称不能为空。"
        If Trimmed.Length > 64 Then Return "名称不能超过 64 个字符。"
        For Each C In IO.Path.GetInvalidFileNameChars()
            If Trimmed.IndexOf(C) >= 0 Then Return "名称不能包含字符：" & C
        Next
        If Trimmed = "." OrElse Trimmed = ".." Then Return "名称不合法。"
        If DirectoryUtils.Exists(PathUtils.AddSlashSuffix(VersionsRoot) & Trimmed) Then Return "已存在同名实例。"
        Return ""
    End Function

    ''' <summary>版本目录的上一级（即 .minecraft\versions）。</summary>
    Public Function VersionsRootOf(Instance As McInstance) As String
        Dim Folder As String = Instance.PathVersion.TrimEnd("\"c)
        Dim Parent As String = IO.Path.GetDirectoryName(Folder)
        Return PathUtils.AddSlashSuffix(Parent)
    End Function

    ''' <summary>
    ''' 复制实例为独立副本。会重写版本 json 的 id 与文件名，保证新实例能被识别。
    ''' 返回新实例的版本目录（以 \ 结尾）。
    ''' </summary>
    Public Function CopyInstance(Source As McInstance, NewName As String) As String
        If Source Is Nothing Then Throw New ArgumentNullException(NameOf(Source))
        Dim Root As String = VersionsRootOf(Source)
        Dim ErrorText As String = ValidateInstanceName(NewName, Root)
        If ErrorText <> "" Then Throw New InvalidOperationException(ErrorText)
        Dim Target As String = PathUtils.AddSlashSuffix(Root) & NewName
        DirectoryUtils.Create(Target)
        DirectoryUtils.Copy(Source.PathVersion, Target)
        RewriteVersionJson(Target, Source.Name, NewName)
        Logger.Info($"已复制实例：{Source.PathVersion} → {Target}")
        Return PathUtils.AddSlashSuffix(Target)
    End Function

    ''' <summary>把复制/恢复出来的版本 json 改名并把 id 改成新实例名。</summary>
    Private Sub RewriteVersionJson(TargetDir As String, OldName As String, NewName As String)
        Dim Target As String = PathUtils.AddSlashSuffix(TargetDir)
        Dim OldJson As String = Target & OldName & ".json"
        Dim NewJson As String = Target & NewName & ".json"
        If Not FileUtils.Exists(OldJson) Then
            '有的格式 json 名与文件夹名不一致，退化为只找唯一 json
            Dim Candidates = DirectoryUtils.EnumerateFiles(Target, False).
                Where(Function(F) F.EndsWith(".json", StringComparison.OrdinalIgnoreCase)).ToList()
            If Candidates.Count <> 1 Then Return
            OldJson = Candidates.First()
        End If
        Try
            Dim Text As String = IO.File.ReadAllText(OldJson, Encoding.UTF8)
            Dim Obj As JObject = JObject.Parse(Text)
            Obj("id") = NewName
            IO.File.WriteAllText(NewJson, Obj.ToString(Newtonsoft.Json.Formatting.Indented), New UTF8Encoding(False))
            If Not PathUtils.IsSame(OldJson, NewJson) AndAlso FileUtils.Exists(OldJson) Then FileUtils.Delete(OldJson)
        Catch ex As Exception
            Logger.Warn(ex, $"重写版本 json 失败（{OldJson}）")
            '至少保证文件名与文件夹名一致，否则启动器认不出
            Try
                If FileUtils.Exists(OldJson) AndAlso Not PathUtils.IsSame(OldJson, NewJson) Then
                    IO.File.Copy(OldJson, NewJson, True)
                    FileUtils.Delete(OldJson)
                End If
            Catch ex2 As Exception
                Logger.Warn(ex2, "复制版本 json 作为兜底也失败")
            End Try
        End Try
    End Sub

    ''' <summary>
    ''' 完整备份实例：打包实例目录（含存档，不含日志、崩溃报告与截图）。
    ''' </summary>
    Public Sub BackupInstance(Instance As McInstance, TargetZip As String)
        If Instance Is Nothing Then Throw New ArgumentNullException(NameOf(Instance))
        ModArchive.CreateZip(Instance.PathIndie, TargetZip,
                             {"session.lock", "usercache.json"},
                             {"logs", "crash-reports", "screenshots"})
        Logger.Info($"已完整备份实例：{Instance.Name} → {TargetZip}")
    End Sub

    ''' <summary>
    ''' 从备份恢复成新实例（不覆盖已有实例）。返回新实例的版本目录。
    ''' ZipPath 里应当包含完整的实例目录（version json + mods/saves 等）。
    ''' </summary>
    Public Function RestoreBackup(ZipPath As String, NewName As String) As String
        If Not FileUtils.Exists(ZipPath) Then Throw New FileNotFoundException(ZipPath)
        Dim Temp As String = PathUtils.AddSlashSuffix(PathTemp) & "InstanceRestore\" & Guid.NewGuid().ToString("N")
        Try
            ModArchive.ExtractZip(ZipPath, Temp)
            '找到版本 json 所在的最浅目录
            Dim Root As String = FindVersionRoot(Temp)
            If Root Is Nothing Then Throw New InvalidOperationException("备份里没有找到版本 json，可能不是本启动器导出的实例备份。")
            Dim VersionsRoot As String = PathUtils.AddSlashSuffix(McFolderSelected) & "versions\"
            Dim ErrorText As String = ValidateInstanceName(NewName, VersionsRoot)
            If ErrorText <> "" Then Throw New InvalidOperationException(ErrorText)
            Dim Target As String = PathUtils.AddSlashSuffix(VersionsRoot) & NewName
            DirectoryUtils.Create(Target)
            DirectoryUtils.Copy(PathUtils.AddSlashSuffix(Root), Target)
            Dim OldName As String = IO.Path.GetFileNameWithoutExtension(
                DirectoryUtils.EnumerateFiles(Root, False).
                FirstOrDefault(Function(F) F.EndsWith(".json", StringComparison.OrdinalIgnoreCase)))
            RewriteVersionJson(Target, If(String.IsNullOrEmpty(OldName), NewName, OldName), NewName)
            Logger.Info($"已从备份恢复实例：{ZipPath} → {Target}")
            Return PathUtils.AddSlashSuffix(Target)
        Finally
            Try
                If DirectoryUtils.Exists(Temp) Then DirectoryUtils.Delete(Temp)
            Catch ex As Exception
                Logger.Warn(ex, $"清理恢复临时目录失败：{Temp}")
            End Try
        End Try
    End Function

    ''' <summary>递归统计目录占用字节数（读不到的文件跳过）。</summary>
    Public Function FolderSize(Folder As String) As Long
        Dim Total As Long = 0
        Try
            If Not DirectoryUtils.Exists(Folder) Then Return 0
            For Each FilePath In DirectoryUtils.EnumerateFiles(Folder, True)
                Try
                    Total += New IO.FileInfo(FilePath).Length
                Catch ex As Exception
                    '单个文件读不到就跳过
                End Try
            Next
        Catch ex As Exception
            Logger.Warn(ex, $"统计目录大小失败：{Folder}")
        End Try
        Return Total
    End Function

    ''' <summary>
    ''' 从另一个 .minecraft 目录导入所有版本（对齐 LumilioCL 游戏库的「导入其他启动器的游戏」）。
    ''' 只导入 versions 下自带同名 json 的版本，同名实例跳过，原目录不动。返回导入数量。
    ''' </summary>
    Public Function ImportVersionsFrom(SourceMcFolder As String) As Integer
        Dim SourceVersions As String = PathUtils.AddSlashSuffix(SourceMcFolder) & "versions\"
        If Not DirectoryUtils.Exists(SourceVersions) Then
            Throw New InvalidOperationException("所选文件夹下没有 versions 目录，无法作为 .minecraft 导入。")
        End If
        Dim TargetVersions As String = PathUtils.AddSlashSuffix(McFolderSelected) & "versions\"
        DirectoryUtils.Create(TargetVersions)
        Dim Count As Integer = 0
        For Each Folder In DirectoryUtils.EnumerateDirectories(SourceVersions, False)
            Try
                Dim Name As String = PathUtils.GetLastPart(PathUtils.AddSlashSuffix(Folder))
                If String.IsNullOrWhiteSpace(Name) Then Continue For
                Dim JsonFile As String = PathUtils.AddSlashSuffix(Folder) & Name & ".json"
                If Not FileUtils.Exists(JsonFile) Then Continue For
                Dim Target As String = PathUtils.AddSlashSuffix(TargetVersions) & Name
                If DirectoryUtils.Exists(Target) Then Continue For
                DirectoryUtils.Copy(PathUtils.AddSlashSuffix(Folder), Target)
                Count += 1
                Logger.Info($"已导入实例：{Folder} → {Target}")
            Catch ex As Exception
                Logger.Warn(ex, $"导入实例失败（跳过）：{Folder}")
            End Try
        Next
        Return Count
    End Function

    ''' <summary>在解压目录里找最浅的、含 "id" 字段 json 的目录。</summary>
    Private Function FindVersionRoot(Folder As String, Optional MaxDepth As Integer = 3) As String
        Dim CurrentLevel As New List(Of String) From {Folder}
        For Depth As Integer = 1 To MaxDepth
            Dim NextLevel As New List(Of String)
            For Each ParentDir In CurrentLevel
                Try
                    For Each JsonFile In DirectoryUtils.EnumerateFiles(ParentDir, False)
                        If Not JsonFile.EndsWith(".json", StringComparison.OrdinalIgnoreCase) Then Continue For
                        Try
                            Dim Obj As JObject = JObject.Parse(IO.File.ReadAllText(JsonFile, Encoding.UTF8))
                            If Obj("id") IsNot Nothing Then Return ParentDir
                        Catch
                            '不是版本 json，跳过
                        End Try
                    Next
                    For Each ChildDir In DirectoryUtils.EnumerateDirectories(ParentDir, False)
                        NextLevel.Add(ChildDir)
                    Next
                Catch ex As Exception
                    Logger.Warn(ex, $"扫描备份目录失败：{ParentDir}")
                End Try
            Next
            CurrentLevel = NextLevel
            If CurrentLevel.Count = 0 Then Exit For
        Next
        Return Nothing
    End Function

End Module
