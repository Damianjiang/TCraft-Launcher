using System;
using System.Threading;

namespace TClKit.Concurrency;

/// <summary>
/// 自写的限频器（第 130 轮替代 ThrottleDebounce 库，减少内嵌体积约 13KB）。
/// 支持两种基础语义的组合：<c>leading</c>（进入窗口立即执行一次）与
/// <c>trailing</c>（窗口结束时若期间有新调用则补执行一次）；
/// 两者皆关时为「节流忽略」：窗口内的调用直接丢弃。
/// 线程安全；执行在工作在锁外进行。
/// </summary>
public sealed class SimpleRateLimiter(Action workload, double intervalMs, bool leading, bool trailing) : IDisposable {

    private readonly object gate = new();
    private Timer? timer;
    private bool disposed;
    private int lastRunTicks = int.MinValue;
    private bool pendingTrailing;

    public void Invoke() {
        lock (gate) {
            if (disposed) return;
            if (!leading && !trailing) return;
            int now = Environment.TickCount;
            bool windowOpen = lastRunTicks != int.MinValue && now - lastRunTicks < intervalMs;
            if (!windowOpen) {
                // 窗口空闲
                if (leading) {
                    lastRunTicks = now;
                    pendingTrailing = false;
                    RunOutsideLock();
                    return;
                }
                // 纯尾随：延迟到静默期后执行
                pendingTrailing = true;
                EnsureTimer(intervalMs);
            } else if (trailing) {
                pendingTrailing = true;
                if (leading) EnsureTimer(Math.Max(1, intervalMs - (now - lastRunTicks)));
                else EnsureTimer(intervalMs);
            }
            // 节流忽略：窗口内的调用不安排执行
        }
    }

    private void TimerFire(object? state) {
        bool run = false;
        lock (gate) {
            if (disposed) return;
            int now = Environment.TickCount;
            if (leading) {
                if (pendingTrailing && now - lastRunTicks >= intervalMs - 5) {
                    lastRunTicks = now;
                    pendingTrailing = false;
                    run = true;
                } else if (pendingTrailing) {
                    EnsureTimer(Math.Max(1, intervalMs - (now - lastRunTicks)));
                }
            } else {
                // 纯尾随防抖：定时器在每次 Invoke 时被重置，触发即执行
                run = true;
            }
        }
        if (run) RunOutsideLock();
    }

    private void RunOutsideLock() {
        try {
            workload();
        } catch (Exception ex) {
            // 限频器不能让后台定时线程带异常退出：记录后继续（第 130 轮体检 P2）
            Logger.Warn(ex, "RateLimitedWorker 执行失败");
        }
    }

    private void EnsureTimer(double dueMs) {
        if (timer is null) timer = new Timer(TimerFire, null, Timeout.Infinite, Timeout.Infinite);
        _ = timer.Change((int)Math.Ceiling(dueMs), Timeout.Infinite);
    }

    public void Dispose() {
        lock (gate) {
            if (disposed) return;
            disposed = true;
            timer?.Dispose();
        }
    }

}
