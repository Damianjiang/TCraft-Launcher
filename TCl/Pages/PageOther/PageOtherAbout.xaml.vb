Public Class PageOtherAbout

    Private Shadows IsLoaded As Boolean = False
    Private Sub PageOtherAbout_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded

        '重复加载部分
        PanBack.ScrollToHome()

        '非重复加载部分
        If IsLoaded Then Return
        IsLoaded = True

        '构建类型标识：方便一眼确认手上的是完整版还是开源版
        LabBuildType.Text = If(String.IsNullOrEmpty(OAuthClientId),
                               ModLang.L("当前构建：开源版（微软正版登录未启用）"),
                               ModLang.L("当前构建：完整版（已内置微软正版登录）"))

    End Sub

    ''' <summary>
    ''' 复制当前启动器识别码（供 设置 → 其他 → 启动器 使用）。
    ''' </summary>
    Public Shared Sub CopyIdentify()
        ClipboardSet(Identify)
    End Sub

End Class
