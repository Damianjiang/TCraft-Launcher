package tcraft.api;

import javafx.application.Platform;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/// Per-plugin background scheduler. All tasks stop when the plugin is disabled.
///
/// `ui` and `async` do not block the JavaFX thread; callbacks run on worker threads.
public final class Scheduler {

    private final PluginContext ctx;
    private final ScheduledThreadPoolExecutor pool;
    private volatile boolean closed;

    Scheduler(PluginContext ctx) {
        this.ctx = ctx;
        this.pool = new ScheduledThreadPoolExecutor(2, runnable -> {
            Thread thread = new Thread(runnable, "TCL-Plugin-" + ctx.id());
            thread.setDaemon(true);
            return thread;
        });
        this.pool.setRemoveOnCancelPolicy(true);
        this.pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /// Runs a task once on a worker thread.
    public Task async(Runnable task) {
        if (closed) return Task.CANCELLED;
        return new Task(pool.submit(wrap(task)));
    }

    /// Runs a task once after the supplied delay.
    public Task delay(Duration delay, Runnable task) {
        if (closed) return Task.CANCELLED;
        long ms = delay == null ? 0 : Math.max(0, delay.toMillis());
        return new Task(pool.schedule(wrap(task), ms, TimeUnit.MILLISECONDS));
    }

    /// Runs a task repeatedly at a fixed delay; intervals below 100 ms are raised to 100 ms.
    public Task repeat(Duration interval, Runnable task) {
        if (closed) return Task.CANCELLED;
        long ms = interval == null ? 100 : Math.max(100, interval.toMillis());
        return new Task(pool.scheduleWithFixedDelay(wrap(task), ms, ms, TimeUnit.MILLISECONDS));
    }

    /// Runs a task on the JavaFX application thread.
    public void ui(Runnable task) {
        if (task == null || closed) return;
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(() -> {
                // a task queued before close() must not touch the unloaded plugin
                if (!closed) task.run();
            });
        }
    }

    /// Indicates whether the scheduler has been shut down.
    public boolean isShutdown() {
        return closed || pool.isShutdown();
    }

    /// Stops all pending and repeating tasks.
    public void close() {
        if (closed) return;
        closed = true;
        pool.shutdownNow();
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            if (closed) return;
            try {
                task.run();
            } catch (Throwable t) {
                ctx.log("后台任务异常：" + t);
            }
        };
    }

    /// A cancellable task handle.
    public static final class Task {

        private static final Task CANCELLED = new Task(null);
        private final Future<?> future;

        Task(Future<?> future) {
            this.future = future;
        }

        /// Cancels the task; repeating tasks stop after the current run.
        public void cancel() {
            if (future != null) future.cancel(false);
        }

        /// Cancels the task and interrupts a running worker when `true`.
        public void cancel(boolean interrupt) {
            if (future != null) future.cancel(interrupt);
        }

        /// Indicates whether the task has been cancelled.
        public boolean isCancelled() {
            return future == null || future.isCancelled();
        }
    }
}
