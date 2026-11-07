Public Module ModMusic

#Region "播放列表"

    ''' <summary>
    ''' 接下来要播放的音乐文件路径。未初始化时为 Nothing。
    ''' </summary>
    Public MusicWaitingList As List(Of String) = Nothing
    ''' <summary>
    ''' 全部音乐文件路径。未初始化时为 Nothing。
    ''' </summary>
    Public MusicAllList As List(Of String) = Nothing
    ''' <summary>
    ''' 初始化音乐播放列表。
    ''' </summary>
    ''' <param name="ForceReload">强制全部重新载入列表。</param>
    ''' <param name="PreventFirst">在重载列表时避免让某项成为第一项。</param>
    Private Sub MusicListInit(ForceReload As Boolean, Optional PreventFirst As String = Nothing)
        If ForceReload Then MusicAllList = Nothing
        Try
            '初始化全部可用音乐列表
            If MusicAllList Is Nothing Then
                MusicAllList = New List(Of String)
                DirectoryUtils.Create(Paths.Base & "TCL\Musics\")
                For Each File In DirectoryUtils.EnumerateFiles(Paths.Base & "TCL\Musics\", True)
                    '文件夹可能会被加入 .ini 文件夹配置文件、一些乱七八糟的 .jpg 文件啥的
                    If {"ini", "jpg", "txt", "cfg", "lrc", "db", "png"}.Contains(PathUtils.GetExtension(File)) Then Continue For
                    MusicAllList.Add(File)
                Next
            End If
            '打乱顺序播放
            MusicWaitingList = If(Settings.Get(Of Boolean)("UiMusicRandom"), New List(Of String)(MusicAllList).Shuffle().ToList, New List(Of String)(MusicAllList))
            If PreventFirst IsNot Nothing AndAlso MusicWaitingList.FirstOrDefault = PreventFirst Then
                '若需要避免成为第一项的为第一项，则将它放在最后
                MusicWaitingList.RemoveAt(0)
                MusicWaitingList.Add(PreventFirst)
            End If
        Catch ex As Exception
            Logger.Error(ex, "初始化音乐列表失败")
        End Try
    End Sub
    ''' <summary>
    ''' 获取下一首播放的音乐路径并将其从列表中移除。
    ''' 如果没有，可能会返回 Nothing。
    ''' </summary>
    Private Function DequeueNextMusicAddress() As String
        Dim Result As String
        '初始化，确保存在音乐
        If MusicAllList Is Nothing OrElse Not MusicAllList.Any() OrElse Not MusicWaitingList.Any() Then MusicListInit(False)
        '出列下一个音乐，如果出列结束则生成新列表
        If MusicWaitingList.Any() Then
            Result = MusicWaitingList(0)
            MusicWaitingList.RemoveAt(0)
        Else
            Result = Nothing
        End If
        If Not MusicWaitingList.Any() Then MusicListInit(False, Result)
        Return Result
    End Function

#End Region

#Region "UI 控制"

    ''' <summary>
    ''' 刷新背景音乐按钮 UI 与设置页 UI。
    ''' </summary>
    Private Sub MusicRefreshUI()
        RunInUi(
        Sub()
            Try

                If MusicAllList Is Nothing OrElse Not MusicAllList.Any() Then
                    '无背景音乐
                    FrmMain.BtnExtraMusic.Show = False
                Else
                    '有背景音乐
                    FrmMain.BtnExtraMusic.Show = True
                    Dim ToolTipText As String
                    If MusicState = MusicStates.Pause Then
                        FrmMain.BtnExtraMusic.Logo = Logo.IconPlay
                        FrmMain.BtnExtraMusic.LogoScale = 0.8
                        ToolTipText = "已暂停：" & PathUtils.GetFileNameWithoutExtension(MusicCurrent)
                        If MusicAllList.Count > 1 Then
                            ToolTipText += vbCrLf & ModLang.L("左键恢复播放，右键播放下一曲。")
                        Else
                            ToolTipText += vbCrLf & ModLang.L("左键恢复播放，右键重新从头播放。")
                        End If
                    Else
                        FrmMain.BtnExtraMusic.Logo = Logo.IconMusic
                        FrmMain.BtnExtraMusic.LogoScale = 1
                        ToolTipText = ModLang.L("正在播放：") & PathUtils.GetFileNameWithoutExtension(MusicCurrent)
                        If MusicAllList.Count > 1 Then
                            ToolTipText += vbCrLf & ModLang.L("左键暂停，右键播放下一曲。")
                        Else
                            ToolTipText += vbCrLf & ModLang.L("左键暂停，右键重新从头播放。")
                        End If
                    End If
                    FrmMain.BtnExtraMusic.ToolTip = ToolTipText
                End If
                If FrmSetupUI IsNot Nothing Then FrmSetupUI.MusicRefreshUI()

            Catch ex As Exception
                Logger.Error(ex, "刷新背景音乐 UI 失败")
            End Try
        End Sub)
    End Sub

    ''' <summary>
    ''' 让音乐在暂停、播放间切换，并显示提示文本。
    ''' </summary>
    Public Sub MusicControlPause()
        If MusicPlayer Is Nothing Then
            Hint(ModLang.L("音乐播放尚未开始！"), HintType.Red)
        Else
            Select Case MusicState
                Case MusicStates.Pause
                    MusicResume()
                Case MusicStates.Play
                    MusicPause()
                Case Else
                    Logger.Warn("音乐目前为停止状态，已强制尝试开始播放")
                    MusicRefreshPlay(False)
            End Select
        End If
    End Sub

    ''' <summary>
    ''' 播放下一曲，并显示提示文本。
    ''' </summary>
    Public Sub MusicControlNext()
        If MusicAllList IsNot Nothing AndAlso MusicAllList.IsSingle Then
            MusicStartPlay(MusicCurrent)
            Hint(ModLang.L("重新播放：") & PathUtils.GetLastPart(MusicCurrent), HintType.Green)
        Else
            Dim Address As String = DequeueNextMusicAddress()
            If Address Is Nothing Then
                Hint(ModLang.L("没有可以播放的音乐！"), HintType.Red)
            Else
                MusicStartPlay(Address)
                Hint(ModLang.L("正在播放：") & PathUtils.GetLastPart(Address), HintType.Green)
            End If
        End If
        MusicRefreshUI()
    End Sub

#End Region

#Region "主状态控制"

    ''' <summary>
    ''' 获取当前的音乐播放状态。
    ''' </summary>
    Public ReadOnly Property MusicState As MusicStates
        Get
            If MusicPlayer Is Nothing OrElse MusicEndedFlag OrElse MusicFailedFlag Then Return MusicStates.Stop
            Return If(MusicPausedFlag, MusicStates.Pause, MusicStates.Play)
        End Get
    End Property
    Public Enum MusicStates
        [Stop]
        Play
        Pause
    End Enum

    '重载与开始

    ''' <summary>
    ''' 重载播放列表并尝试开始播放背景音乐。
    ''' </summary>
    ''' <param name="ShowHint">是否显示刷新提示。</param>
    Public Sub MusicRefreshPlay(ShowHint As Boolean, Optional IsFirstLoad As Boolean = False)
        Try

            MusicListInit(True)
            If Not MusicAllList.Any() Then
                If MusicPlayer Is Nothing Then
                    If ShowHint Then Hint(ModLang.L("未检测到可用的背景音乐！"), HintType.Red)
                Else
                    MusicStopAll()
                    If ShowHint Then Hint(ModLang.L("背景音乐已清除！"), HintType.Green)
                End If
            Else
                Dim Address As String = DequeueNextMusicAddress()
                If Address Is Nothing Then
                    If ShowHint Then Hint(ModLang.L("没有可以播放的音乐！"), HintType.Red)
                Else
                    Try
                        MusicStartPlay(Address, IsFirstLoad)
                        If ShowHint Then Hint(ModLang.L("背景音乐已刷新：") & PathUtils.GetLastPart(Address), HintType.Green, False)
                    Catch ex As Exception
                        Logger.Warn(ex, "开始播放背景音乐失败")
                    End Try
                End If
            End If
            MusicRefreshUI()

        Catch ex As Exception
            Logger.Error(ex, "刷新背景音乐播放失败")
        End Try
    End Sub

    ''' <summary>
    ''' 开始播放音乐。全部播放器操作都汇总到 UI 线程（WPF MediaPlayer 为 DispatcherObject），
    ''' 播放本身由媒体管线异步进行，不再有后台轮询线程。
    ''' </summary>
    Private Sub MusicStartPlay(Address As String, Optional IsFirstLoad As Boolean = False)
        If Address Is Nothing Then Return
        Logger.Info($"播放开始：{Address}")
        MusicCurrent = Address
        RunInUi(
        Sub()
            Try
                EnsureMusicPlayer()
                Dim Player = MusicPlayer
                MusicEventsArmed = False '切换曲目期间忽略上一曲的迟到事件
                MusicPausedFlag = False
                MusicEndedFlag = False
                MusicFailedFlag = False
                Player.Open(New Uri(Path.GetFullPath(Address)))
                Player.Volume = Math.Max(0, Math.Min(1, Settings.Get(Of Integer)("UiMusicVolume") / 1000))
                Player.Play()
                '第一次打开的暂停
                If IsFirstLoad AndAlso Not Settings.Get(Of Boolean)("UiMusicAuto") Then
                    Player.Pause()
                    MusicPausedFlag = True
                End If
                MusicEventsArmed = True
                EnsureMusicSyncTimer().Start()
            Catch ex As Exception
                MusicEventsArmed = False
                Logger.Warn(ex, "开始播放背景音乐失败")
                HandleMusicError(ex, IsFirstLoad)
            End Try
        End Sub)
    End Sub

    '播放与暂停

    ''' <summary>
    ''' 暂停音乐播放，返回是否成功切换了状态。
    ''' </summary>
    Public Function MusicPause() As Boolean
        If MusicState = MusicStates.Play Then
            RunInThread(
            Sub()
                Logger.Info("已暂停播放")
                '标志与播放器操作在同一 UI 派发块中完成，避免后台线程置位的竞态
                RunInUi(Sub()
                            MusicPausedFlag = True
                            MusicPlayer?.Pause()
                        End Sub)
                MusicRefreshUI()
            End Sub)
            Return True
        Else
            Logger.Info($"无需暂停播放，当前状态为 {MusicState}")
            Return False
        End If
    End Function
    ''' <summary>
    ''' 继续音乐播放，返回是否成功切换了状态。
    ''' </summary>
    Public Function MusicResume() As Boolean
        If MusicState = MusicStates.Play OrElse MusicAllList Is Nothing OrElse Not MusicAllList.Any() Then
            Logger.Info($"无需继续播放，当前状态为 {MusicState}")
            Return False
        Else
            RunInThread(
            Sub()
                Logger.Info("已恢复播放")
                RunInUi(Sub()
                            MusicPausedFlag = False
                            MusicPlayer?.Play()
                        End Sub)
                MusicRefreshUI()
            End Sub)
            Return True
        End If
    End Function

    ''' <summary>
    ''' 彻底停止播放并停掉同步定时器（清空音乐列表 / 删除音乐时调用）。
    ''' </summary>
    Public Sub MusicStopAll()
        MusicPausedFlag = False
        MusicEndedFlag = True
        MusicFailedFlag = False
        MusicEventsArmed = False
        RunInUi(
        Sub()
            Try
                StopMusicSync()
                If MusicPlayer IsNot Nothing Then
                    MusicPlayer.Stop()
                    MusicPlayer.Close()
                End If
            Catch ex As Exception
                Logger.Warn(ex, "停止背景音乐失败")
            End Try
        End Sub)
    End Sub

#End Region

    ''' <summary>
    ''' WPF 自带媒体播放器（在 UI 线程创建与操作；替代 NAudio，减少内嵌体积约 174KB）。
    ''' </summary>
    Public MusicPlayer As System.Windows.Media.MediaPlayer = Nothing
    ''' <summary>当前播放的音乐地址。</summary>
    Private MusicCurrent As String = ""
    Private MusicPausedFlag As Boolean = False
    Private MusicEndedFlag As Boolean = False
    Private MusicFailedFlag As Boolean = False
    ''' <summary>进度与音量的同步定时器（仅播放期间运行，500ms 一次，UI 线程）。</summary>
    Private MusicSyncTimer As System.Windows.Threading.DispatcherTimer = Nothing
    ''' <summary>事件闸门：切换曲目期间为 False，迟到的事件直接丢弃。</summary>
    Private MusicEventsArmed As Boolean = False

    ''' <summary>确保播放器已创建（必须在 UI 线程调用）。</summary>
    Private Sub EnsureMusicPlayer()
        If MusicPlayer IsNot Nothing Then Return
        MusicPlayer = New System.Windows.Media.MediaPlayer
        AddHandler MusicPlayer.MediaEnded, AddressOf MusicPlayerEnded
        AddHandler MusicPlayer.MediaFailed, AddressOf MusicPlayerFailed
    End Sub

    Private Sub MusicPlayerEnded(sender As Object, e As EventArgs)
        If Not MusicEventsArmed OrElse MusicEndedFlag Then Return
        MusicEndedFlag = True
        StopMusicSync()
        MusicRefreshUI()
        '播放完毕自动下一曲（列表 IO 在后台线程）
        If MusicAllList IsNot Nothing AndAlso MusicAllList.Any Then
            RunInNewThread(Sub() MusicStartPlay(DequeueNextMusicAddress()), "Music Next", ThreadPriority.BelowNormal)
        End If
    End Sub

    Private Sub MusicPlayerFailed(sender As Object, e As System.Windows.Media.ExceptionEventArgs)
        If Not MusicEventsArmed OrElse MusicFailedFlag Then Return
        MusicFailedFlag = True
        StopMusicSync()
        HandleMusicError(e.ErrorException, False)
    End Sub

    ''' <summary>播放出错：提示、把坏文件移出列表、稍后继续下一曲。</summary>
    Private Sub HandleMusicError(ex As Exception, IsFirstLoad As Boolean)
        Try
            If ex Is Nothing Then ex = New Exception("未知播放错误")
            Logger.Warn(ex, $"播放音乐出现内部错误（{MusicCurrent}）")
            If Not ({"wav", "mp3", "flac", "wma", "m4a"}.Contains(PathUtils.GetExtension(MusicCurrent))) OrElse
                ex.Message.Contains("0xC00D36C4") Then '不支持给定的 URL 的字节流类型。(异常来自 HRESULT:0xC00D36C4)
                Hint(ModLang.L("播放音乐失败（") & PathUtils.GetLastPart(MusicCurrent) & ModLang.L("）：TCL 可能不支持此音乐格式，请将格式转换为 .wav、.mp3 或 .flac 后再试"), HintType.Red)
            Else
                Logger.Error(ex, $"播放音乐失败（{PathUtils.GetLastPart(MusicCurrent)}）", LogBehavior.Toast)
            End If
            '将播放错误的音乐从列表中移除
            MusicAllList?.Remove(MusicCurrent)
            MusicWaitingList?.Remove(MusicCurrent)
            MusicRefreshUI()
            If TypeOf ex Is FileNotFoundException Then
                MusicRefreshPlay(True, IsFirstLoad)
            Else
                RunInNewThread(
                Sub()
                    Thread.Sleep(2000)
                    MusicStartPlay(DequeueNextMusicAddress(), IsFirstLoad)
                End Sub, "Music Retry", ThreadPriority.BelowNormal)
            End If
        Catch ex2 As Exception
            Logger.Warn(ex2, "处理音乐播放错误失败")
        End Try
    End Sub

    Private Function EnsureMusicSyncTimer() As System.Windows.Threading.DispatcherTimer
        If MusicSyncTimer Is Nothing Then
            MusicSyncTimer = New System.Windows.Threading.DispatcherTimer With {.Interval = TimeSpan.FromMilliseconds(500)}
            AddHandler MusicSyncTimer.Tick, AddressOf MusicSyncTick
        End If
        Return MusicSyncTimer
    End Function

    Private Sub StopMusicSync()
        If MusicSyncTimer IsNot Nothing Then MusicSyncTimer.Stop()
        If FrmMain IsNot Nothing Then RunInUi(Sub() FrmMain.BtnExtraMusic.Progress = 0)
    End Sub

    ''' <summary>播放期间每 0.5s 同步音量（设置更改即时生效）与进度条；其余时间无任何驻留。</summary>
    Private Sub MusicSyncTick(sender As Object, e As EventArgs)
        Try
            If MusicPlayer Is Nothing OrElse MusicEndedFlag OrElse MusicFailedFlag Then
                StopMusicSync()
                Return
            End If
            MusicPlayer.Volume = Math.Max(0, Math.Min(1, Settings.Get(Of Integer)("UiMusicVolume") / 1000))
            FrmMain.BtnExtraMusic.Progress = GetPlayerProgress()
        Catch ex As Exception
            Logger.Warn(ex, "同步背景音乐状态失败")
        End Try
    End Sub

    Private Function GetPlayerProgress() As Double
        Try
            If Not MusicPlayer.NaturalDuration.HasTimeSpan Then Return 0
            Dim Total = MusicPlayer.NaturalDuration.TimeSpan.TotalMilliseconds
            If Total <= 0 Then Return 0
            Return Math.Max(0, Math.Min(1, MusicPlayer.Position.TotalMilliseconds / Total))
        Catch ex As Exception
            Return 0
        End Try
    End Function

End Module
