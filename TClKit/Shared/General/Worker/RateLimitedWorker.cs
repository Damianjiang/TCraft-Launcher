namespace TClKit.Concurrency;

public enum RateLimitMode {
    /// <summary>Throttle：立即执行一次，然后一段时间内合并为一次尾随执行。</summary>
    ImmediateThenMerge,
    /// <summary>Debounce (leading)：立即执行一次，然后忽略一段时间内的后续调用。</summary>
    ImmediateThenIgnore,
    /// <summary>Debounce (trailing)：不立即执行，直到一段时间内不再有任何新调用后再执行一次。</summary>
    WaitUnillNoNewInvoke,
}

/// <summary>
/// 对高频调用进行限制。
/// 详见：<see href="https://aldaviva.com/portfolio.html#ratelimiting" />
/// <para/>第 130 轮：底层改用自写的 <see cref="SimpleRateLimiter"/>，不再依赖 ThrottleDebounce 包。
/// </summary>
public class RateLimitedWorker(Action workload, double minimalIntervalMs, RateLimitMode mode) : IDisposable {

    private readonly SimpleRateLimiter action = mode switch {
        RateLimitMode.ImmediateThenMerge => new SimpleRateLimiter(workload, minimalIntervalMs, leading: true, trailing: true),
        RateLimitMode.WaitUnillNoNewInvoke => new SimpleRateLimiter(workload, minimalIntervalMs, leading: false, trailing: true),
        _ => new SimpleRateLimiter(workload, minimalIntervalMs, leading: true, trailing: false)
    };

    public void Start() {
        if (!disposed) action.Invoke();
    }

    private bool disposed = false;
    public virtual void Dispose() {
        if (disposed) return;
        disposed = true;
        action.Dispose();
    }

}
