Public Class PageSetupLink

    Private NodeLabelsFilled As Boolean = False

    Private Sub PageSetupLink_Loaded(sender As Object, e As RoutedEventArgs) Handles Me.Loaded

        '重复加载部分
        PanBack.ScrollToHome()

        '非重复加载部分
        Static IsLoaded As Boolean = False
        If IsLoaded Then Return
        IsLoaded = True

        FillDownloadNodeCombo()
        UpdateDownloadNodeEnabled()

        AniControlEnabled += 1
        Reload()
        AniControlEnabled -= 1

    End Sub

    '填充「指定节点」下拉框：按节点池顺序列出常用 GitHub 加速镜像，下标与 LinkDownloadNode 对应。
    Private Sub FillDownloadNodeCombo()
        If NodeLabelsFilled Then Return
        NodeLabelsFilled = True
        Dim Pool = ModLink.GetNodePool()
        For i = 0 To Pool.Count - 1
            Dim Host As String
            Try
                Host = New Uri(Pool(i)).Host
            Catch
                Host = "节点 " & (i + 1)
            End Try
            Dim Item As New MyComboBoxItem With {.Content = Host}
            SettingService.SetValue(Item, i.ToString())
            ComboDownloadNode.Items.Add(Item)
        Next
    End Sub

    '指定模式外禁用节点下拉框
    Private Sub UpdateDownloadNodeEnabled()
        If ComboDownloadNode Is Nothing Then Return
        ComboDownloadNode.IsEnabled = Settings.Get(Of Integer)("LinkDownloadMode") = 1
    End Sub

    Private Sub ComboDownloadMode_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles ComboDownloadMode.SelectionChanged
        UpdateDownloadNodeEnabled()
    End Sub

    Public Sub Reload()
        SettingService.RefreshSettings(Me)
    End Sub

    '初始化
    Public Sub Reset()
        Try
            SettingService.ResetSettings(Me)
            Logger.Info("已初始化联机页设置")
            Hint(ModLang.L("已初始化联机页设置！"), HintType.Green, False)
        Catch ex As Exception
            Logger.Error(ex, "初始化联机页设置失败", LogBehavior.Alert)
        End Try
        Reload()
    End Sub

End Class
