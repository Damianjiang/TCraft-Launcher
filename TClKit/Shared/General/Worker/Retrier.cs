using System;
using System.Threading;

namespace TClKit.Concurrency;

/// <summary>
/// 自写的重试工具（第 130 轮替代 ThrottleDebounce 包，减少内嵌体积与依赖面）。
/// 失败时按 <paramref name="delay"/> 计算等待后重试；超过 <paramref name="maxAttempts"/>
/// 或 <paramref name="isRetryAllowed"/> 拒绝重试时抛出最后的异常。
/// </summary>
public static class Retrier {

    /// <summary>
    /// 重试执行一个无返回值操作。action 的参数为当前尝试序号（从 0 开始）。
    /// </summary>
    public static void Attempt(Action<int> action, int maxAttempts = 2, Func<int, TimeSpan>? delay = null,
                               Func<Exception, bool>? isRetryAllowed = null, CancellationToken cancellationToken = default) {
        for (int attempt = 0; ; attempt++) {
            cancellationToken.ThrowIfCancellationRequested();
            try {
                action(attempt);
                return;
            } catch (Exception ex) when (attempt + 1 < maxAttempts && (isRetryAllowed?.Invoke(ex) ?? true)) {
                Delay(attempt + 1, delay, cancellationToken);
            }
        }
    }

    /// <summary>
    /// 重试执行一个有返回值的函数。func 的参数为当前尝试序号（从 0 开始）。
    /// </summary>
    public static T Attempt<T>(Func<int, T> func, int maxAttempts = 2, Func<int, TimeSpan>? delay = null,
                               Func<Exception, bool>? isRetryAllowed = null, CancellationToken cancellationToken = default) {
        for (int attempt = 0; ; attempt++) {
            cancellationToken.ThrowIfCancellationRequested();
            try {
                return func(attempt);
            } catch (Exception ex) when (attempt + 1 < maxAttempts && (isRetryAllowed?.Invoke(ex) ?? true)) {
                Delay(attempt + 1, delay, cancellationToken);
            }
        }
    }

    private static void Delay(int attempt, Func<int, TimeSpan>? delay, CancellationToken cancellationToken) {
        if (delay is null) return;
        var span = delay(attempt);
        if (span <= TimeSpan.Zero) return;
        if (cancellationToken.CanBeCanceled) {
            // 可取消时用 WaitHandle 等待，取消即刻唤醒
            if (cancellationToken.WaitHandle.WaitOne(span)) throw new OperationCanceledException(cancellationToken);
        } else {
            Thread.Sleep(span);
        }
    }

}
