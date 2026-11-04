Imports System.IO
Imports System.Windows
Imports System.Windows.Media

''' <summary>
''' 自定义字体。把 .ttf / .otf 放进 字体文件夹（TCL\Fonts），TCL 启动时自动加载并应用；
''' 也可在「设置 → 个性化 → 自定义字体」里点「选择字体文件」挑选（自动复制到字体文件夹）。
''' 与背景图片 / 视频同范式：文件夹驱动 + 设置页按钮 + 预览。字体在进程内用 file:// 加载，
''' 不写入系统字体目录、无副作用；下次打开自动应用。
''' </summary>
Public Module ModFont

    ''' <summary>字体文件夹：配置根\TCL\Fonts（与 Pictures / Musics 同级）。</summary>
    Public Function FontFolder() As String
        Return Paths.Base & "TCL\Fonts\"
    End Function

    ''' <summary>枚举字体文件夹里全部 .ttf / .otf（自动建目录）。</summary>
    Public Function FontFiles() As List(Of String)
        Dim Result As New List(Of String)
        Try
            DirectoryUtils.Create(FontFolder())
            For Each File In DirectoryUtils.EnumerateFiles(FontFolder(), True)
                If {"ttf", "otf"}.Contains(PathUtils.GetExtension(File)) Then Result.Add(File)
            Next
        Catch ex As Exception
            Logger.Warn(ex, "枚举自定义字体失败")
        End Try
        Return Result
    End Function

    ''' <summary>把某个字体文件加载成 WPF FontFamily（进程内，file:/// 标准写法）。加载失败返回 Nothing。</summary>
    Public Function LoadFamily(FilePath As String) As FontFamily
        Try
            If FilePath Is Nothing OrElse Not File.Exists(FilePath) Then Return Nothing
            'WPF 加载本地字体文件的标准方式：file:/// 绝对路径 + 反斜杠转正斜杠 + 尾部 #
            Dim UriText As String = "file:///" + FilePath.Replace("\", "/") + "#"
            Return New FontFamily(UriText)
        Catch
            Return Nothing
        End Try
    End Function

    ''' <summary>应用某个字体文件到主窗口（FontFamily 继承级联到全部子控件）并刷新预览。</summary>
    Public Sub FontApply(FilePath As String)
        Dim ff = LoadFamily(FilePath)
        If ff Is Nothing Then Return
        '记录当前字体（预览标签 + 启动自动应用都靠它）
        Settings.Set("UiFontFile", FilePath)
        Settings.Set("UiFontFamily", Path.GetFileNameWithoutExtension(FilePath))
        RunInUi(Sub()
                   If FrmMain IsNot Nothing Then FrmMain.FontFamily = ff
                   If FrmSetupUI IsNot Nothing Then FrmSetupUI.FontPreview(ff)
               End Sub)
    End Sub

    ''' <summary>恢复默认系统字体（清掉主窗口字体 + 预览，并清空记录）。</summary>
    Public Sub FontReset()
        RunInUi(Sub()
                   'WPF FontFamily 依赖属性不接受 Nothing（走 SetValueCommon 校验会抛 ArgumentException），恢复默认用空族名 ""（= 系统默认字体）
                   If FrmMain IsNot Nothing Then FrmMain.FontFamily = New FontFamily("")
                   If FrmSetupUI IsNot Nothing Then FrmSetupUI.FontPreview(Nothing)
               End Sub)
        Settings.Set("UiFontFile", "")
        Settings.Set("UiFontFamily", "")
    End Sub

    ''' <summary>启动时自动应用：扫字体文件夹，取记录的字体（或第一个）加载。无字体则跳过。</summary>
    Public Sub FontInit()
        Try
            Dim files = FontFiles()
            If Not files.Any() Then Return
            Dim wantFile As String = Settings.Get(Of String)("UiFontFile")
            Dim target As String = If(String.IsNullOrWhiteSpace(wantFile) OrElse Not files.Contains(wantFile), files(0), wantFile)
            Dim ff = LoadFamily(target)
            If ff Is Nothing Then Return
            Settings.Set("UiFontFile", target)
            Settings.Set("UiFontFamily", Path.GetFileNameWithoutExtension(target))
            RunInUi(Sub()
                       If FrmMain IsNot Nothing Then FrmMain.FontFamily = ff
                       If FrmSetupUI IsNot Nothing Then FrmSetupUI.FontPreview(ff)
                   End Sub)
        Catch ex As Exception
            Logger.Warn(ex, "启动应用自定义字体失败")
        End Try
    End Sub

    ''' <summary>「选择字体文件」：Dialogs.SelectFile 选 ttf/otf → 复制到字体文件夹 → 立即应用。返回是否成功。</summary>
    Public Function FontPickAndSave() As Boolean
        Try
            Dim picked As String = Dialogs.SelectFile("选择字体文件（.ttf / .otf）", False, filter:={({"ttf", "otf"}, "字体文件")}).FirstOrDefault()
            If String.IsNullOrWhiteSpace(picked) Then Return False
            DirectoryUtils.Create(FontFolder())
            Dim dest As String = FontFolder() & Path.GetFileName(picked)
            If picked <> dest Then FileUtils.Copy(picked, dest)
            Settings.Set("UiFontFile", dest)
            Settings.Set("UiFontFamily", Path.GetFileNameWithoutExtension(picked))
            FontApply(dest)
            Hint(ModLang.L("已应用自定义字体：") & Path.GetFileName(picked) & ModLang.L("（已存到字体文件夹，下次自动生效）"), HintType.Green)
            Return True
        Catch ex As Exception
            Logger.Error(ex, "选择自定义字体失败")
            Hint(ModLang.L("选择字体失败：") & ex.Message, HintType.Red)
            Return False
        End Try
    End Function

    ''' <summary>「刷新字体」：重新扫描字体文件夹，应用记录项（或第一个）并提示。用于文件夹里新增字体后即时生效。</summary>
    Public Sub FontRefresh(ShowHint As Boolean)
        Try
            Dim files = FontFiles()
            If Not files.Any() Then
                If ShowHint Then Hint(ModLang.L("字体文件夹里没有 .ttf / .otf 字体文件"), HintType.Red)
                Return
            End If
            Dim wantFile As String = Settings.Get(Of String)("UiFontFile")
            Dim target As String = If(String.IsNullOrWhiteSpace(wantFile) OrElse Not files.Contains(wantFile), files(0), wantFile)
            FontApply(target)
            If ShowHint Then Hint(ModLang.L("已刷新字体：") & Path.GetFileName(target), HintType.Green)
        Catch ex As Exception
            Logger.Warn(ex, "刷新自定义字体失败")
        End Try
    End Sub

End Module
