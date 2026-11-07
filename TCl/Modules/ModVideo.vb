Imports System.Windows
Imports System.Windows.Controls
Imports System.IO

''' <summary>
''' 背景视频。把 TCL\Videos 文件夹里的视频文件（mp4/wmv/mov/mkv/avi/webm）作为动态背景循环播放，
''' 与「组件半透明 / 背景不透明度」配合，透出磨砂动态背景。默认静音（背景画面），可取消静音并调音量。
''' </summary>
Public Module ModVideo

#Region "播放列表"

    ''' <summary>接下来要播放的视频文件路径。未初始化时为 Nothing。</summary>
    Public VideoWaitingList As List(Of String) = Nothing
    ''' <summary>全部视频文件路径。未初始化时为 Nothing。</summary>
    Public VideoAllList As List(Of String) = Nothing

    ''' <summary>视频背景文件夹。与 Musics 同级，位于 配置根\TCL\Videos。</summary>
    Public Function VideoFolder() As String
        Return Paths.Base & "TCL\Videos\"
    End Function

    Private Sub VideoListInit(ForceReload As Boolean, Optional PreventFirst As String = Nothing)
        If ForceReload Then VideoAllList = Nothing
        Try
            If VideoAllList Is Nothing Then
                VideoAllList = New List(Of String)
                DirectoryUtils.Create(VideoFolder())
                For Each File In DirectoryUtils.EnumerateFiles(VideoFolder(), True)
                    '只保留视频文件，跳过配置/说明/图片等
                    If Not {"mp4", "wmv", "mov", "mkv", "avi", "webm"}.Contains(PathUtils.GetExtension(File)) Then Continue For
                    VideoAllList.Add(File)
                Next
            End If
            '打乱顺序播放
            VideoWaitingList = If(Settings.Get(Of Boolean)("UiVideoRandom"), New List(Of String)(VideoAllList).Shuffle().ToList, New List(Of String)(VideoAllList))
            If PreventFirst IsNot Nothing AndAlso VideoWaitingList.FirstOrDefault = PreventFirst Then
                '若需要避免成为第一项的为第一项，则把它放到最后
                VideoWaitingList.RemoveAt(0)
                VideoWaitingList.Add(PreventFirst)
            End If
        Catch ex As Exception
            Logger.Error(ex, "初始化视频列表失败")
        End Try
    End Sub

    Private Function DequeueNextVideoAddress() As String
        Dim Result As String
        If VideoAllList Is Nothing OrElse Not VideoAllList.Any() OrElse Not VideoWaitingList.Any() Then VideoListInit(False)
        If VideoWaitingList.Any() Then
            Result = VideoWaitingList(0)
            VideoWaitingList.RemoveAt(0)
        Else
            Result = Nothing
        End If
        If Not VideoWaitingList.Any() Then VideoListInit(False, Result)
        Return Result
    End Function

#End Region

#Region "主状态控制"

    ''' <summary>重载播放列表并尝试开始播放背景视频。ShowHint=True 时给出提示。</summary>
    Public Sub VideoRefreshPlay(ShowHint As Boolean, Optional IsFirstLoad As Boolean = False)
        Try
            '图片/视频二选一（UiBackgroundType，）：非视频模式时一律停播并隐藏视频层，
            '把展示权交还给背景图片，避免「有视频就自动压过图片」的隐式优先级。
            If Settings.Get(Of Integer)("UiBackgroundType") <> 1 Then
                VideoStopAndHide()
                If ShowHint Then Hint(ModLang.L("当前背景类型为图片，视频已停用。"), HintType.Blue, False)
                Return
            End If
            VideoListInit(True)
            If Not VideoAllList.Any() Then
                '无视频：确保视频层隐藏
                RunInUi(Sub()
                           If FrmMain IsNot Nothing Then
                               Dim vid = FrmMain.VidBack
                               vid.Stop()
                               vid.Source = Nothing
                               vid.Visibility = Visibility.Collapsed
                               FrmMain.UpdateBackgroundAndTitleBar()
                           End If
                           If Not IsNothing(FrmSetupUI) Then FrmSetupUI.VideoRefreshUI()
                       End Sub)
                If ShowHint Then Hint(ModLang.L("未检测到可用的背景视频！"), HintType.Red)
                Return
            End If
            Dim Address As String = DequeueNextVideoAddress()
            If Address Is Nothing Then
                If ShowHint Then Hint(ModLang.L("没有可以播放的背景视频！"), HintType.Red)
                Return
            End If
            VideoStartPlay(Address)
            If ShowHint Then Hint(ModLang.L("背景视频已刷新：") & PathUtils.GetLastPart(Address), HintType.Green, False)
            VideoRefreshUI()
        Catch ex As Exception
            Logger.Error(ex, "刷新背景视频播放失败")
        End Try
    End Sub

    ''' <summary>开始播放某个视频文件。单片循环；多片则播完切换下一片。</summary>
    Public Sub VideoStartPlay(Address As String)
        If Address Is Nothing Then Return
        If Not File.Exists(Address) Then
            '文件被删/移动：移出列表
            If VideoAllList IsNot Nothing Then VideoAllList.Remove(Address)
            If VideoWaitingList IsNot Nothing Then VideoWaitingList.Remove(Address)
            Logger.Warn($"背景视频文件不存在，已跳过：{Address}")
            Return
        End If
        RunInUi(Sub()
                    Dim vid = FrmMain.VidBack
                    Try
                        If FrmMain Is Nothing OrElse vid Is Nothing Then Return
                        vid.Stop()
                        vid.Source = New Uri(Address, UriKind.Absolute)
                        vid.IsMuted = Settings.Get(Of Boolean)("UiVideoMute")
                        vid.Volume = Settings.Get(Of Integer)("UiVideoVolume") / 1000
                        vid.Visibility = Visibility.Visible
                        '视频层跟随「背景图片不透明度」，让半透明组件能透出
                        vid.Opacity = Settings.Get(Of Integer)("UiBackgroundOpacity") / 1000
                        vid.Play()
                        FrmMain.UpdateBackgroundAndTitleBar()
                        If Not IsNothing(FrmSetupUI) Then FrmSetupUI.VideoRefreshUI()
                    Catch ex As Exception
                        Logger.Warn(ex, $"播放背景视频失败（{PathUtils.GetLastPart(Address)}）")
                        If vid IsNot Nothing Then vid.Visibility = Visibility.Collapsed
                    End Try
                End Sub)
    End Sub

    ''' <summary>视频播完：单片则从头重播（循环）；多片则切到下一片。</summary>
    Public Sub VideoOnMediaEnded()
        Try
            If VideoAllList Is Nothing OrElse Not VideoAllList.Any() Then Return
            If VideoAllList.Count = 1 Then
                '单片：重播实现循环
                VideoStartPlay(VideoAllList(0))
            Else
                Dim Address As String = DequeueNextVideoAddress()
                If Address Is Nothing Then Return
                VideoStartPlay(Address)
            End If
        Catch ex As Exception
            Logger.Warn(ex, "切换下一段背景视频失败")
        End Try
    End Sub

    ''' <summary>
    ''' 窗口隐藏时暂停背景视频。MediaElement 的解码独立于渲染管线，
    ''' 窗口 Visibility=Hidden 后仍会持续解码，必须显式暂停。
    ''' </summary>
    Public Sub VideoPauseForHidden()
        RunInUi(Sub()
                    Try
                        If FrmMain Is Nothing OrElse Not FrmMain.HasVidBack Then Return
                        Dim vid = FrmMain.VidBack
                        If vid.Source IsNot Nothing Then vid.Pause()
                    Catch ex As Exception
                        Logger.Warn(ex, "隐藏窗口时暂停背景视频失败")
                    End Try
                End Sub)
    End Sub

    ''' <summary>窗口重新显示后恢复背景视频播放（与 VideoPauseForHidden 成对）。</summary>
    Public Sub VideoResumeFromHidden()
        RunInUi(Sub()
                    Try
                        If FrmMain Is Nothing OrElse Not FrmMain.HasVidBack Then Return
                        Dim vid = FrmMain.VidBack
                        If vid.Source IsNot Nothing Then vid.Play()
                    Catch ex As Exception
                        Logger.Warn(ex, "显示窗口时恢复背景视频失败")
                    End Try
                End Sub)
    End Sub

    ''' <summary>按当前设置同步音量与静音状态（设置项 OnChanged 调用，委托只传新值）。</summary>
    Public Sub VideoUpdateAudio(Value As Object)
        RunInUi(Sub()
                    Try
                        If FrmMain Is Nothing OrElse Not FrmMain.HasVidBack Then Return
                        Dim vid = FrmMain.VidBack
                        vid.IsMuted = Settings.Get(Of Boolean)("UiVideoMute")
                        vid.Volume = Settings.Get(Of Integer)("UiVideoVolume") / 1000
                    Catch ex As Exception
                        Logger.Warn(ex, "同步背景视频音量状态失败")
                    End Try
                End Sub)
    End Sub

    ''' <summary>清空视频文件夹并停止播放。</summary>
    Public Sub VideoClear()
        RunInNewThread(Sub()
                           Try
                               VideoAllList = Nothing
                               VideoWaitingList = Nothing
                               RunInUi(Sub() If FrmMain IsNot Nothing AndAlso FrmMain.HasVidBack Then FrmMain.VidBack.Visibility = Visibility.Collapsed)
                               If Directory.Exists(VideoFolder()) Then
                                   DirectoryUtils.Delete(Paths.Base & "TCL\Videos") '与音乐约定一致：Delete 目标不带尾斜杠
                               End If
                               DirectoryUtils.Create(VideoFolder())
                               RunInUi(Sub()
                                           If FrmMain IsNot Nothing Then
                                               FrmMain.VidBack.Stop()
                                               FrmMain.VidBack.Source = Nothing
                                               FrmMain.UpdateBackgroundAndTitleBar()
                                           End If
                                           If Not IsNothing(FrmSetupUI) Then FrmSetupUI.VideoRefreshUI()
                                           Hint(ModLang.L("背景视频已清空！"), HintType.Green)
                                       End Sub)
                           Catch ex As Exception
                               Logger.Error(ex, "清空背景视频失败")
                           End Try
                       End Sub, "Video", ThreadPriority.BelowNormal)
    End Sub

#End Region

#Region "UI 控制"

    Private Sub VideoRefreshUI()
        RunInUi(Sub()
                    Try
                        If IsNothing(FrmSetupUI) Then Return
                        FrmSetupUI.VideoRefreshUI()
                    Catch ex As Exception
                        Logger.Error(ex, "刷新背景视频 UI 失败")
                    End Try
                End Sub)
    End Sub

    ''' <summary>
    ''' 立即应用背景类型设置（UiBackgroundType：0=图片，1=视频）：按需启停视频层并刷新主界面背景。
    ''' 由设置页的类型切换与设置项 OnChanged 共同调用。
    ''' </summary>
    Public Sub ApplyBackgroundType()
        Try
            If Settings.Get(Of Integer)("UiBackgroundType") = 1 Then
                VideoRefreshPlay(False, True)
            Else
                VideoStopAndHide()
                '图片模式：还原背景图片的显示
                RunInUi(Sub() If FrmMain IsNot Nothing Then FrmMain.UpdateBackgroundAndTitleBar())
            End If
            RunInUi(Sub()
                        If Not IsNothing(FrmSetupUI) Then
                            FrmSetupUI.VideoRefreshUI()
                            FrmSetupUI.UpdateBackgroundCardUI()
                        End If
                    End Sub)
        Catch ex As Exception
            Logger.Error(ex, "应用背景类型设置失败")
        End Try
    End Sub

    ''' <summary>停止播放并隐藏视频背景层（不改变设置）。</summary>
    Public Sub VideoStopAndHide()
        RunInUi(Sub()
                    Try
                        If FrmMain IsNot Nothing Then
                            Dim vid = FrmMain.VidBack
                            vid.Stop()
                            vid.Source = Nothing
                            vid.Visibility = Visibility.Collapsed
                            FrmMain.UpdateBackgroundAndTitleBar()
                        End If
                        If Not IsNothing(FrmSetupUI) Then FrmSetupUI.VideoRefreshUI()
                    Catch ex As Exception
                        Logger.Warn(ex, "停止背景视频失败")
                    End Try
                End Sub)
    End Sub

#End Region

End Module
