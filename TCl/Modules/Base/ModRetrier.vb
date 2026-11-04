''' <summary>
''' 自写的重试工具（第 130 轮替代 ThrottleDebounce.Retrier，减少内嵌体积约 13KB）。
''' 失败时按 delay 计算等待后重试；超过 maxAttempts 或 isRetryAllowed 拒绝重试时抛出最后异常。
''' </summary>
Public Module Retrier

    ''' <summary>
    ''' 重试执行一个 Sub。action 的参数为当前尝试序号（从 0 开始）。
    ''' maxAttempts 为最大总尝试次数，默认 2（与原 ThrottleDebounce 语义一致）；isRetryAllowed 返回 False 的异常立即抛出。
    ''' </summary>
    Public Sub Attempt(action As Action(Of Integer),
                       Optional maxAttempts As Integer = 2,
                       Optional delay As Func(Of Integer, TimeSpan) = Nothing,
                       Optional isRetryAllowed As Func(Of Exception, Boolean) = Nothing)
        Dim TryIndex As Integer = 0
        While True
            Try
                action(TryIndex)
                Return
            Catch ex As Exception
                TryIndex += 1
                If maxAttempts >= 0 AndAlso TryIndex >= maxAttempts Then Throw
                If isRetryAllowed IsNot Nothing AndAlso Not isRetryAllowed(ex) Then Throw
                If delay IsNot Nothing Then Thread.Sleep(delay(TryIndex))
            End Try
        End While
    End Sub

    ''' <summary>
    ''' 重试执行一个有返回值的函数。func 的参数为当前尝试序号（从 0 开始）。
    ''' </summary>
    Public Function Attempt(Of T)(func As Func(Of Integer, T),
                                  Optional maxAttempts As Integer = 2,
                                  Optional delay As Func(Of Integer, TimeSpan) = Nothing,
                                  Optional isRetryAllowed As Func(Of Exception, Boolean) = Nothing) As T
        Dim TryIndex As Integer = 0
        While True
            Try
                Return func(TryIndex)
            Catch ex As Exception
                TryIndex += 1
                If maxAttempts >= 0 AndAlso TryIndex >= maxAttempts Then Throw
                If isRetryAllowed IsNot Nothing AndAlso Not isRetryAllowed(ex) Then Throw
                If delay IsNot Nothing Then Thread.Sleep(delay(TryIndex))
            End Try
        End While
    End Function

End Module
