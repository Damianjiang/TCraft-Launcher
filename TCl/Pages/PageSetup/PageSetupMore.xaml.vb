''' <summary>
''' 设置 - 更多。提供社区入口与卸载功能。
''' </summary>
Public Class PageSetupMore

    ''' <summary>
    ''' TCraft Launcher 启动器交流群链接。
    ''' </summary>
    Private Const CommunityUrl As String = "https://qm.qq.com/q/UONlv4VgwE"

    ''' <summary>
    ''' 打开社区 QQ 群链接。
    ''' </summary>
    Private Sub BtnJoinQQ_Click(sender As Object, e As MouseButtonEventArgs)
        Try
            OpenWebsite(CommunityUrl)
        Catch ex As Exception
            Logger.Error(ex, "打开社区 QQ 群链接失败")
            Hint(ModLang.L("打开链接失败：") & ex.Message, HintType.Red)
        End Try
    End Sub

    ''' <summary>
    ''' 卸载：先生成清理清单让用户确认，再写临时脚本、以分离进程启动脚本，最后结束自身。
    ''' <para/>脚本会等本进程退出、再等约 3 秒后开始删除，因此运行中的文件不会被占用。
    ''' <para/>清单里的每一项都经过白名单校验，且不会触碰 .minecraft 或任何 Minecraft 数据。
    ''' </summary>
    Private Sub BtnUninstall_Click(sender As Object, e As MouseButtonEventArgs)
        ' 1. 扫描并校验，得到“将要删除什么”的清单（此步骤不删除任何东西）
        Dim Plan As UninstallPlan
        Try
            Plan = ModUninstall.BuildPlan()
        Catch ex As Exception
            Logger.Error(ex, "生成卸载清单失败")
            Hint(ModLang.L("生成卸载清单失败：") & ex.Message, HintType.Red)
            Return
        End Try

        If Not Plan.CanUninstall Then
            MyMsgBox(ModLang.L("没有找到可以删除的启动器文件，可能已经被清理过了。"), ModLang.L("卸载 TCraft Launcher"))
            Return
        End If

        ' 2. 先把路径与注册表项逐条列出来，用户确认后才继续
        Dim Choice As Integer = MyMsgBox(
            ModUninstall.Describe(Plan),
            ModLang.L("确认卸载 TCraft Launcher"), ModLang.L("取消"), ModLang.L("确认卸载"), IsWarn:=True)
        If Choice <> 2 Then Return

        Try
            ' 3. 生成临时清理脚本，把清单作为参数传给脚本
            ModUninstall.PrepareScript(Plan)
            ' 4. 以分离进程启动脚本：启动后立即返回，不阻塞、也不随本进程退出而结束
            ModUninstall.Run(Plan)
        Catch ex As Exception
            Logger.Error(ex, "启动卸载脚本失败")
            Hint(ModLang.L("启动卸载脚本失败：") & ex.Message, HintType.Red)
            Return
        End Try

        ' 5. 结束自身，把运行中的文件让给脚本处理
        Hint(ModLang.L("正在卸载，程序即将退出……"), HintType.Green)
        IsProgramEnding = True
        Logger.Info("卸载脚本已启动，启动器即将退出")
        Try
            Logger.Instance.Flush()
        Catch ex As Exception
            Logger.Warn(ex, "退出前刷新日志失败")
        End Try
        RunInThread(Sub()
                        Thread.Sleep(400)
                        RunInUi(Sub() Environment.Exit(0))
                    End Sub)
    End Sub

End Class
