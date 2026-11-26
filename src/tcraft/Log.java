package tcraft;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// 异步日志 调用线程入队 守护线程写盘
public final class Log {

    private static final int QUEUE_CAPACITY = 4096;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int KEEP_ROTATIONS = 4;
    private static final long EXPORT_LIMIT = 32L * 1024 * 1024;

    private static final Object LOCK = new Object();
    private static final ArrayBlockingQueue<String[]> QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final AtomicBoolean SESSION = new AtomicBoolean();
    private static volatile boolean started;
    private static volatile boolean stopping;
    private static BufferedWriter mainWriter;
    private static BufferedWriter errWriter;
    private static long mainBytes;
    private static long errorBytes;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Log::flushAndClose, "TCL-Log-Flush"));
    }

    private Log() {
    }

    public static void info(String line) {
        if (line != null) enqueue(Redact.apply(line), false);
    }

    public static void error(String text) {
        if (text != null) enqueue(Redact.apply(text), true);
    }

    // 日志/报错共用环境信息
    public static String environment() {
        StringBuilder sb = new StringBuilder();
        sb.append("软件：TCraft Launcher ").append(Build.VERSION)
                .append("（").append(Build.EDITION).append("）\n");
        sb.append("系统：").append(System.getProperty("os.name", ""))
                .append(' ').append(System.getProperty("os.version", ""))
                .append(" (").append(System.getProperty("os.arch", "")).append(")\n");
        sb.append("Java：").append(System.getProperty("java.version", ""))
                .append(" (").append(System.getProperty("java.vendor", "")).append(")\n");
        sb.append("目录：").append(Resources.home());
        return sb.toString();
    }

    // 启动分隔并写环境信息
    public static void startSession() {
        if (!SESSION.compareAndSet(false, true)) return;
        try {
            // launch.log 超 1MB 归档
            rotateIfBig(Resources.logDir().resolve("launch.log"), 1024 * 1024, "launch");
        } catch (Throwable ignored) {
        }
        enqueue("===== 启动 " + LocalDateTime.now().withNano(0) + " =====\n" + environment(), true);
    }

    // 异常描述 类名 + message + 首帧
    public static String describe(Throwable t) {
        if (t == null) return "无异常对象";
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        for (int depth = 0; cur != null && depth < 4; depth++) {
            if (depth > 0) sb.append(" ← ");
            sb.append(cur.getClass().getName()).append(": ");
            String m = cur.getMessage();
            sb.append(m == null || m.isBlank() ? "(无消息)" : m.replace('\r', ' ').replace('\n', ' '));
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        StackTraceElement[] st = t.getStackTrace();
        if (st.length > 0) sb.append(" @ ").append(st[0]);
        return sb.toString();
    }

    public static Path crash(Throwable t) {
        if (t == null) return null;
        try {
            Path dir = Resources.logDir();
            Path file = dir.resolve("crash.txt");
            rotateIfBig(file, 512 * 1024, "crash");
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            String entry = "===== " + LocalDateTime.now().withNano(0) + " =====\n"
                    + environment() + "\n" + sw;
            Files.writeString(file, Redact.apply(entry), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return file;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // 运行日志全文 按时间排序
    public static String exportCurrent() {
        return readGroup("launcher.log", EXPORT_LIMIT);
    }

    // 错误日志全文
    public static String exportErrors() {
        return readGroup("errors.log", EXPORT_LIMIT);
    }

    // 全部日志按时间拼接
    public static String exportAll() {
        StringBuilder sb = new StringBuilder();
        long total = 0;
        for (Path p : allLogFiles()) {
            if (total >= EXPORT_LIMIT) break;
            String body;
            try {
                if (Files.size(p) > EXPORT_LIMIT - total) continue;
                body = Files.readString(p, StandardCharsets.UTF_8);
            } catch (Throwable t) {
                continue;
            }
            if (total + body.length() > EXPORT_LIMIT) {
                sb.append("\n… 日志总量超过 ").append(EXPORT_LIMIT / 1024 / 1024)
                        .append(" MB，其余部分已省略\n");
                break;
            }
            sb.append("===== ").append(p.getFileName()).append(" =====\n").append(body).append('\n');
            total += body.length();
        }
        return sb.toString();
    }

    public static int cleanup() {
        // 只关句柄不杀写线程
        synchronized (LOCK) {
            closeMain();
            closeError();
        }
        int count = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Resources.logDir(), p -> {
            String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            return n.startsWith("launcher") || n.startsWith("errors")
                    || n.startsWith("launch") || n.startsWith("bootstrap")
                    || n.equals("crash.txt") || n.startsWith("crash.");
        })) {
            for (Path p : ds) {
                try {
                    if (Files.isRegularFile(p)) {
                        Files.deleteIfExists(p);
                        count++;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    public static void flushNow() {
        synchronized (LOCK) {
            writePending();
            flushWriters();
        }
    }

    private static void enqueue(String text, boolean isError) {
        ensureStarted();
        // 单条日志限长 防异常消息带超大响应体写盘
        String line = text;
        if (line.length() > 8192) line = line.substring(0, 8192) + "…";
        if (!QUEUE.offer(new String[]{line, isError ? "1" : "0"})) DROPPED.incrementAndGet();
    }

    private static void ensureStarted() {
        if (started) return;
        synchronized (LOCK) {
            if (started) return;
            started = true;
            Thread t = new Thread(Log::loop, "TCL-Logger");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void loop() {
        List<String[]> batch = new ArrayList<>(256);
        while (!stopping) {
            try {
                String[] first = QUEUE.poll(700, TimeUnit.MILLISECONDS);
                if (first == null) {
                    synchronized (LOCK) {
                        flushWriters();
                    }
                    continue;
                }
                batch.add(first);
                QUEUE.drainTo(batch, 255);
                synchronized (LOCK) {
                    for (String[] e : batch) writeLocked(e[0], "1".equals(e[1]));
                    flushWriters();
                }
                batch.clear();
                long drop = DROPPED.getAndSet(0);
                if (drop > 0) {
                    synchronized (LOCK) {
                        writeLocked("[" + LocalDateTime.now().withNano(0)
                                + "] [日志] 写盘跟不上，丢弃 " + drop + " 条", false);
                        flushWriters();
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ignored) {
            }
        }
        flushAndClose();
    }

    private static void writePending() {
        List<String[]> rest = new ArrayList<>();
        QUEUE.drainTo(rest);
        for (String[] e : rest) writeLocked(e[0], "1".equals(e[1]));
    }

    private static void writeLocked(String text, boolean isError) {
        try {
            BufferedWriter w = main();
            if (w != null) {
                w.write(text);
                w.newLine();
                mainBytes += text.length() + 1;
                if (mainBytes > MAX_FILE_BYTES) {
                    closeMain();
                    main();
                }
            }
            if (isError) {
                BufferedWriter ew = errorWriter();
                if (ew != null) {
                    ew.write(text);
                    ew.newLine();
                    errorBytes += text.length() + 1;
                    if (errorBytes > MAX_FILE_BYTES) {
                        closeError();
                        errorWriter();
                    }
                }
            }
        } catch (Throwable t) {
            closeMain();
            closeError();
        }
    }

    private static BufferedWriter main() {
        if (mainWriter == null) {
            try {
                Path file = Resources.logDir().resolve("launcher.log");
                rotate("launcher.log");
                mainWriter = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                mainBytes = Files.size(file);
            } catch (Throwable t) {
                mainWriter = null;
            }
        }
        return mainWriter;
    }

    private static BufferedWriter errorWriter() {
        if (errWriter == null) {
            try {
                Path file = Resources.logDir().resolve("errors.log");
                rotate("errors.log");
                errWriter = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                errorBytes = Files.size(file);
            } catch (Throwable t) {
                errWriter = null;
            }
        }
        return errWriter;
    }

    private static void rotate(String base) {
        try {
            Path dir = Resources.logDir();
            Path file = dir.resolve(base);
            if (!Files.isRegularFile(file) || Files.size(file) <= MAX_FILE_BYTES) return;
            String stem = base.endsWith(".log") ? base.substring(0, base.length() - 4) : base;
            Files.deleteIfExists(dir.resolve(stem + "." + KEEP_ROTATIONS + ".log"));
            for (int i = KEEP_ROTATIONS - 1; i >= 1; i--) {
                Path from = dir.resolve(stem + "." + i + ".log");
                Path to = dir.resolve(stem + "." + (i + 1) + ".log");
                if (Files.isRegularFile(from)) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(file, dir.resolve(stem + ".1.log"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }

    private static void rotateIfBig(Path file, long limit, String stem) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) <= limit) return;
            Files.move(file, file.resolveSibling(stem + ".1.txt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }

    private static String readGroup(String base, long limit) {
        StringBuilder sb = new StringBuilder();
        try {
            Path dir = Resources.logDir();
            String stem = base.endsWith(".log") ? base.substring(0, base.length() - 4) : base;
            List<Path> files = new ArrayList<>();
            for (int i = KEEP_ROTATIONS; i >= 1; i--) files.add(dir.resolve(stem + "." + i + ".log"));
            files.add(dir.resolve(base));
            for (Path p : files) {
                if (!Files.isRegularFile(p)) continue;
                if (Files.size(p) > limit - sb.length()) {
                    sb.append("\n… 日志超过 ").append(limit / 1024 / 1024).append(" MB，其余部分已省略\n");
                    break;
                }
                String body = Files.readString(p, StandardCharsets.UTF_8);
                sb.append(body);
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private static List<Path> allLogFiles() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Resources.logDir())) {
            for (Path p : ds) {
                String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (Files.isRegularFile(p) && (n.endsWith(".log") || n.endsWith(".txt"))) files.add(p);
            }
        } catch (Throwable ignored) {
        }
        files.sort(Comparator.comparingLong(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (Throwable t) {
                return 0L;
            }
        }));
        return files;
    }

    private static void flushWriters() {
        try {
            if (mainWriter != null) mainWriter.flush();
        } catch (Throwable ignored) {
        }
        try {
            if (errWriter != null) errWriter.flush();
        } catch (Throwable ignored) {
        }
    }

    private static void flushAndClose() {
        stopping = true;
        synchronized (LOCK) {
            writePending();
            flushWriters();
            closeMain();
            closeError();
        }
    }

    private static void closeMain() {
        try {
            if (mainWriter != null) mainWriter.close();
        } catch (Throwable ignored) {
        }
        mainWriter = null;
    }

    private static void closeError() {
        try {
            if (errWriter != null) errWriter.close();
        } catch (Throwable ignored) {
        }
        errWriter = null;
    }
}
