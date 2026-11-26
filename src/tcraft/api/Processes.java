package tcraft.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/// Starts subprocesses and runs commands with captured output.
///
/// Shell commands use `cmd.exe /c` on Windows and `/bin/sh -c` on macOS/Linux.
/// Output is decoded as UTF-8 on macOS/Linux and with `native.encoding` on Windows.
/// `run` blocks the calling thread. Use `ctx.scheduler().async(...)` when the caller
/// is the JavaFX application thread. Processes started through this class are
/// terminated when the plugin is disabled or reloaded.
public final class Processes {

    private static final long OUTPUT_LIMIT = 1024L * 1024;
    // Windows 的 cmd 子进程按宿主代码页输出，不能固定按 UTF-8 解码
    private static final java.nio.charset.Charset OUTPUT_CHARSET = outputCharset();

    private final PluginContext ctx;
    private final List<Process> running = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    Processes(PluginContext ctx) {
        this.ctx = ctx;
    }

    /// Starts a shell command line and returns the live process.
    public Process start(String commandLine) throws IOException {
        return start(shellCommand(commandLine), null, null);
    }

    /// Starts a command list without a shell.
    public Process start(List<String> command) throws IOException {
        return start(command, null, null);
    }

    /// Starts a command list with an optional working directory and environment.
    public Process start(List<String> command, Path workDir, Map<String, String> environment) throws IOException {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("command is empty");
        // a disabled plugin must not spawn new processes
        if (closed) throw new IllegalStateException("processes API is closed");
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        if (workDir != null) builder.directory(workDir.toFile());
        if (environment != null) builder.environment().putAll(environment);
        Process process = builder.start();
        running.add(process);
        process.onExit().thenRun(() -> running.remove(process));
        return process;
    }

    /// Runs a shell command and waits up to 60 seconds.
    public Result run(String commandLine) throws IOException {
        return run(commandLine, Duration.ofSeconds(60));
    }

    /// Runs a shell command and waits for the supplied timeout.
    public Result run(String commandLine, Duration timeout) throws IOException {
        return run(shellCommand(commandLine), timeout);
    }

    /// Runs a command list and waits for the supplied timeout.
    public Result run(List<String> command, Duration timeout) throws IOException {
        long started = System.nanoTime();
        Process process = start(command);
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outReader = reader(process.getInputStream(), stdout);
        Thread errReader = reader(process.getErrorStream(), stderr);
        boolean finished;
        try {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                process.waitFor();
                finished = true;
            } else {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            finished = false;
        }
        if (!finished) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        join(outReader);
        join(errReader);
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        return new Result(finished ? process.exitValue() : -1, stdout.toString(), stderr.toString(), !finished, elapsed);
    }

    /// Terminates every process started by this plugin.
    public void close() {
        closed = true;
        for (Process process : running) {
            try {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
        running.clear();
    }

    private static List<String> shellCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) throw new IllegalArgumentException("command is empty");
        if (tcraft.Shell.windows()) {
            return List.of("cmd.exe", "/c", commandLine);
        }
        return List.of("/bin/sh", "-c", commandLine);
    }

    private static Thread reader(InputStream in, StringBuilder target) {
        Thread thread = new Thread(() -> {
            try (InputStream stream = in;
                 java.io.Reader reader = new java.io.InputStreamReader(stream, OUTPUT_CHARSET)) {
                char[] buffer = new char[8192];
                int n;
                long total = 0;
                while ((n = reader.read(buffer)) > 0) {
                    total += n;
                    if (total <= OUTPUT_LIMIT) {
                        target.append(buffer, 0, n);
                    } else {
                        int room = (int) Math.max(0, OUTPUT_LIMIT - (total - n));
                        if (room > 0) target.append(buffer, 0, Math.min(n, room));
                    }
                }
            } catch (IOException ignored) {
            }
        }, "TCL-Plugin-Process-Reader");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static java.nio.charset.Charset outputCharset() {
        if (!tcraft.Shell.windows()) return StandardCharsets.UTF_8;
        String name = System.getProperty("native.encoding", "");
        if (name.isBlank()) return StandardCharsets.UTF_8;
        try {
            return java.nio.charset.Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /// Captured result of a finished or timed-out command.
    public static final class Result {

        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;
        public final long durationMs;

        Result(int exitCode, String stdout, String stderr, boolean timedOut, long durationMs) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
            this.durationMs = durationMs;
        }

        /// Returns `true` when the command exited with code `0` and did not time out.
        public boolean success() {
            return !timedOut && exitCode == 0;
        }
    }
}
