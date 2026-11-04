Imports Newtonsoft.Json

''' <summary>
''' 游戏选项预设（复刻 LumilioCL 的「预设」核心用途：把一套 options 存下来，套用到别的实例）。
''' 相对 LumilioCL 简化：不做「共享范围」逐项勾选，应用时只覆盖预设里存在的键，其余键保持不动。
''' 存储为 启动器数据目录\OptionPresets\&lt;预设名&gt;.json，一个预设一个文件，方便手工备份与分享。
''' </summary>
Public Module ModOptionPresets

    Public Class OptionPreset
        Public Property Name As String = ""
        Public Property Note As String = ""
        Public Property CreatedUtc As String = ""
        Public Property Options As New Dictionary(Of String, String)
    End Class

    Private ReadOnly Gate As New Object

    ''' <summary>预设目录，以 \ 结尾。</summary>
    Public ReadOnly Property Dir As String
        Get
            Return PathUtils.AddSlashSuffix(Paths.BaseThenName) & "OptionPresets\"
        End Get
    End Property

    Private Function SafeFileName(Name As String) As String
        Dim Result As String = If(Name, "")
        For Each C In IO.Path.GetInvalidFileNameChars()
            Result = Result.Replace(C, "_"c)
        Next
        If Result.Length > 60 Then Result = Result.Substring(0, 60)
        Return Result & ".json"
    End Function

    ''' <summary>校验预设名，返回空字符串表示合法。</summary>
    Public Function ValidateName(Name As String, Optional IgnoreName As String = "") As String
        Dim Trimmed As String = If(Name, "").Trim()
        If Trimmed.Length = 0 Then Return ModLang.L("预设名不能为空。")
        If Trimmed.Length > 40 Then Return ModLang.L("预设名不能超过 40 个字符。")
        If Not String.Equals(Trimmed, IgnoreName, StringComparison.OrdinalIgnoreCase) AndAlso
           ListAll().Any(Function(P) String.Equals(P.Name, Trimmed, StringComparison.OrdinalIgnoreCase)) Then
            Return ModLang.L("已存在同名预设。")
        End If
        Return ""
    End Function

    ''' <summary>读取全部预设，按名称排序。</summary>
    Public Function ListAll() As List(Of OptionPreset)
        Dim Result As New List(Of OptionPreset)
        SyncLock Gate
            Try
                If Not DirectoryUtils.Exists(Dir) Then Return Result
                For Each FilePath In DirectoryUtils.EnumerateFiles(Dir, False)
                    If Not FilePath.EndsWith(".json", StringComparison.OrdinalIgnoreCase) Then Continue For
                    Try
                        Dim One = JsonConvert.DeserializeObject(Of OptionPreset)(IO.File.ReadAllText(FilePath, Encoding.UTF8))
                        If One IsNot Nothing AndAlso Not String.IsNullOrWhiteSpace(One.Name) Then
                            If One.Options Is Nothing Then One.Options = New Dictionary(Of String, String)
                            Result.Add(One)
                        End If
                    Catch ex As Exception
                        Logger.Warn(ex, $"读取选项预设失败：{FilePath}")
                    End Try
                Next
            Catch ex As Exception
                Logger.Warn(ex, "枚举选项预设失败")
            End Try
        End SyncLock
        Return Result.OrderBy(Function(P) P.Name, StringComparer.OrdinalIgnoreCase).ToList()
    End Function

    ''' <summary>保存预设。返回空字符串表示成功，否则是错误说明。</summary>
    Public Function Save(Name As String, Options As Dictionary(Of String, String), Optional Note As String = "") As String
        Dim Trimmed As String = If(Name, "").Trim()
        Dim ErrorText As String = ValidateName(Trimmed)
        If ErrorText <> "" Then Return ErrorText
        If Options Is Nothing OrElse Options.Count = 0 Then Return ModLang.L("当前没有任何选项可保存。")
        Try
            DirectoryUtils.Create(Dir)
            Dim Data As New OptionPreset With {
                .Name = Trimmed,
                .Note = If(Note, "").Trim(),
                .CreatedUtc = Date.UtcNow.ToString("o"),
                .Options = New Dictionary(Of String, String)(Options)
            }
            IO.File.WriteAllText(Dir & SafeFileName(Trimmed), JsonConvert.SerializeObject(Data, Formatting.Indented), Encoding.UTF8)
            Logger.Info($"已保存选项预设：{Trimmed}（{Data.Options.Count} 项）")
            Return ""
        Catch ex As Exception
            Logger.Error(ex, "保存选项预设失败", LogBehavior.Toast)
            Return ModLang.L("保存预设失败：") & ex.Message
        End Try
    End Function

    Public Sub Delete(Name As String)
        Try
            Dim Target As String = Dir & SafeFileName(Name)
            If FileUtils.Exists(Target) Then FileUtils.Delete(Target)
            Logger.Info($"已删除选项预设：{Name}")
        Catch ex As Exception
            Logger.Warn(ex, $"删除选项预设失败：{Name}")
        End Try
    End Sub

End Module
