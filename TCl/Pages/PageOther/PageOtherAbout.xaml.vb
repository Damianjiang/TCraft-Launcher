Public Class PageOtherAbout

    Private Shadows IsLoaded As Boolean = False
    Private Sub PageOtherAbout_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded

        '重复加载部分
        PanBack.ScrollToHome()

        '非重复加载部分
        If IsLoaded Then Return
        IsLoaded = True

    End Sub

    ''' <summary>
    ''' 复制当前启动器识别码（供 设置 → 其他 → 启动器 使用）。
    ''' </summary>
    Public Shared Sub CopyIdentify()
        ClipboardSet(Identify)
    End Sub

End Class
