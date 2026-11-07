''' <summary>
''' 弹窗关闭动画的统一实现。
''' <para/>
''' 8 个 MyMsg 弹窗原先各自复制同一段「淡出 + 上浮 + 缩放 + 从父容器移除」，
''' 改一次动画要改 8 份、还容易改漏。这里收拢成一处，行为与原来逐帧一致。
''' </summary>
Friend Module MyMsgCloseAnimation

    ''' <summary>
    ''' 播放关闭动画。Uuid 用于给动画组命名，避免多个弹窗的动画互相覆盖。
    ''' </summary>
    ''' <param name="Control">要关闭的弹窗控件（通常传 Me）。</param>
    ''' <param name="Uuid">弹窗实例编号（各弹窗自己的 Uuid 字段）。</param>
    ''' <param name="Pos">弹窗的位移动画变换。</param>
    ''' <param name="Scale">弹窗的缩放动画变换。</param>
    Public Sub Play(Control As FrameworkElement, Uuid As Integer, Pos As TranslateTransform, Scale As ScaleTransform)
        AniStart({
            AaCode(Sub()
                       '最后一个弹窗关掉时，把整块遮罩的黑色背景淡回透明
                       If Not WaitingMyMsgBox.Any() Then
                           AniStart(AaColor(FrmMain.PanMsg, Panel.BackgroundProperty,
                                            New MyColor(0, 0, 0, 0) - FrmMain.PanMsg.Background, 200,
                                            Ease:=New AniEaseOutFluent(AniEasePower.Weak)))
                       End If
                   End Sub, 30),
            AaOpacity(Control, -Control.Opacity, 90, 10),
            AaDouble(Sub(i) Pos.Y += i, 12 - Pos.Y, 130, 0, New AniEaseOutFluent),
            AaDouble(Sub(i) Scale.ScaleX += i, 1.04 - Scale.ScaleX, 130, 0, New AniEaseInFluent(AniEasePower.Weak)),
            AaDouble(Sub(i) Scale.ScaleY += i, 1.04 - Scale.ScaleY, 130, 0, New AniEaseInFluent(AniEasePower.Weak)),
            AaCode(Sub()
                       If Control.Parent IsNot Nothing Then CType(Control.Parent, Grid).Children.Remove(Control)
                   End Sub, , True)
        }, "MyMsgBox " & Uuid)
    End Sub

End Module
