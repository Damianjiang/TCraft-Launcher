Public Class PageSpeedRight

    Private Sub Init() Handles Me.Loaded
        PanBack.ScrollToHome()
    End Sub

    '清除已成功完成的任务（失败与进行中的任务保留，便于重试；任务中心 C1 收尾）
    Private Sub BtnClearFinished_Click(sender As Object, e As EventArgs) Handles BtnClearFinished.Click
        If FrmSpeedLeft IsNot Nothing Then FrmSpeedLeft.TaskbarClearFinished()
    End Sub

End Class
