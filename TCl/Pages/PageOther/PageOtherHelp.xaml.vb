Public Class PageOtherHelp

    Private Shadows IsLoaded As Boolean = False
    Private Sub PageOtherHelp_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded
        '重复加载部分
        PanBack.ScrollToHome()

        '非重复加载部分
        If IsLoaded Then Return
        IsLoaded = True
    End Sub

    ''' <summary>
    ''' 打开官方帮助中心。
    ''' </summary>
    Public Shared Sub OpenHelpCenter()
        Try
            Hint(ModLang.L("帮助中心：加入 TCraft Launcher 启动器交流群（群号 1131373915，https://qm.qq.com/q/WaG64YE9uo），可获取使用帮助、更新公告与问题反馈。"), HintType.Blue)
        Catch ex As Exception
            Logger.Error(ex, "打开帮助中心失败")
        End Try
    End Sub

End Class
