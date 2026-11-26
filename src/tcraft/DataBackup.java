package tcraft;

import javafx.application.Platform;
import javafx.stage.FileChooser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class DataBackup {

    private static final String MARKER = "tcl-backup.json";
    private static final String IMPORT_MARKER = "import.json";
    private static final String DATA_PREFIX = "data/";
    private static final int MAX_ENTRIES = 300_000;
    private static final long MAX_TOTAL = 8L * 1024 * 1024 * 1024;
    private static final long MAX_FILE = 4L * 1024 * 1024 * 1024;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // 运行库、缓存和游戏文件不进启动器数据备份，避免备份变成几个 GB
    private static final Set<String> SKIP_DIRS = new HashSet<>(List.of(
            "cache", "log", "logs", "update", "feedback",
            "dependencies", "java", "EasyTier",
            "assets", "libraries", "versions", "mods", "saves",
            "resourcepacks", "shaderpacks", "crash-reports", "screenshots",
            "replay_recordings", "backups", "backup"));

    private DataBackup() {
    }

    public static void chooseExport(MainWindow win) {
        if (isBusy(win)) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("导出启动器数据"));
        chooser.setInitialFileName("TCraftLauncher-backup-"
                + OffsetDateTime.now().format(STAMP) + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("TCraft 备份"), "*.zip"));
        File chosen = chooser.showSaveDialog(win.stage);
        if (chosen == null) return;
        Path target = chosen.toPath().toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            target = target.resolveSibling(target.getFileName() + ".zip");
        }
        final Path finalTarget = target;
        if (Files.exists(finalTarget)) {
            Dialogs.confirm(win, Lang.t("覆盖备份"),
                    Lang.tf("目标文件已存在：\n%s\n\n继续会覆盖这个文件，是否继续？", finalTarget),
                    () -> exportTo(win, finalTarget), true, () -> {
                    });
        } else {
            exportTo(win, finalTarget);
        }
    }

    public static void chooseImport(MainWindow win) {
        if (isBusy(win)) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("导入启动器数据"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("TCraft 备份"), "*.zip"));
        File chosen = chooser.showOpenDialog(win.stage);
        if (chosen == null) return;
        Path file = chosen.toPath().toAbsolutePath().normalize();
        ZipInfo info = inspect(win, file);
        if (info == null) return;
        Dialogs.confirm(win, Lang.t("导入启动器数据"),
                Lang.tf("将用备份覆盖当前的配置、实例、插件、皮肤和预设，导入完成后会自动重启启动器。\n\n"
                        + "备份包含 %d 个文件（%s）；当前数据会保留为 .tcl.pre-import-*，确认导出内容无误后再清理。\n"
                        + "备份可能包含账户令牌与代理密码，请只在可信设备上导入。\n\n确定要导入吗？",
                        info.files, sizeText(info.bytes)),
                () -> importTo(win, file, info), true, () -> {
                });
    }

    private static boolean isBusy(MainWindow win) {
        if (win.importPanel != null && win.importPanel.isBusy()) {
            win.toast(Lang.t("请稍后"), Lang.t("已有导入或导出任务正在进行"), 0);
            return true;
        }
        if (Tasks.INSTANCE.hasActive()) {
            win.toast(Lang.t("请稍后"), Lang.t("请等待下载或安装任务结束后再操作数据备份"), 0);
            return true;
        }
        if (win.isGameRunning()) {
            win.toast(Lang.t("请先退出游戏"), Lang.t("游戏运行期间不能导入或导出启动器数据"), 0);
            return true;
        }
        return false;
    }

    private static void exportTo(MainWindow win, Path target) {
        Path base = Data.get().baseDir().toAbsolutePath().normalize();
        Path part = target.resolveSibling(target.getFileName() + ".part");
        int generation = win.importPanel.begin(Lang.t("正在导出数据"), target.getFileName().toString(), 0);
        win.importPanel.note(Lang.t("导出期间请勿关闭启动器；备份包含配置、实例、插件、皮肤和预设。"));
        Threads.start("TCL-DataExport", () -> {
            try {
                ZipInfo info = exportArchive(base, target, part,
                        (done, total, label) -> Platform.runLater(
                                () -> win.importPanel.progress(generation, done, total, label)));
                Platform.runLater(() -> win.importPanel.finish(generation, Lang.t("导出完成"),
                        () -> win.toast(Lang.t("导出完成"),
                                Lang.tf("已保存到 %s（%d 个文件，%s）", target, info.files, sizeText(info.bytes)), 1)));
            } catch (Throwable t) {
                try {
                    Files.deleteIfExists(part);
                } catch (Throwable ignored) {
                }
                Platform.runLater(() -> {
                    win.importPanel.close();
                    Dialogs.message(win, Lang.t("导出失败"), message(t), true, Lang.t("确定"));
                });
            }
        });
    }

    // 不依赖窗口，便于在隔离目录里直接验证导出结果
    static ZipInfo exportArchive(Path base, Path target, Path part, Progress progress) throws IOException {
        Files.deleteIfExists(part);
        List<Path> files = collect(base, target, part);
        long totalBytes = 0;
        for (Path file : files) {
            long size = Files.size(file);
            if (size > MAX_FILE) throw new IOException("单个文件超过 4 GB：" + file.getFileName());
            totalBytes += size;
            if (totalBytes > MAX_TOTAL) throw new IOException("数据超过 8 GB，无法导出");
        }
        final int count = files.size();
        final long bytes = totalBytes;
        if (progress != null) {
            progress.accept(0, count, Lang.tf("共 %d 个文件，约 %s", count, sizeText(bytes)));
        }
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(
                Files.newOutputStream(part), 64 * 1024))) {
            // 速度优先：备份/导入的瓶颈是磁盘 IO 和单线程压缩，低压缩比换更快吞吐
            zos.setLevel(1);
            putText(zos, MARKER, marker(count, bytes));
            int done = 0;
            for (Path file : files) {
                String rel = base.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(DATA_PREFIX + rel);
                entry.setTime(Files.getLastModifiedTime(file).toMillis());
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
                done++;
                if (done == 1 || done % 25 == 0 || done == count) {
                    final int current = done;
                    if (progress != null) progress.accept(current, count, Lang.tf("正在写入 %s", rel));
                }
            }
        }
        move(part, target);
        return new ZipInfo(count, bytes);
    }

    private static List<Path> collect(Path base, Path target, Path part) throws IOException {
        List<Path> out = new ArrayList<>();
        final Path absTarget = target.toAbsolutePath().normalize();
        final Path absPart = part.toAbsolutePath().normalize();
        // 用 walkFileTree 直接剪掉 cache/游戏目录，不让导出扫描整个资源树
        Files.walkFileTree(base, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                                                                   java.nio.file.attribute.BasicFileAttributes attrs) {
                if (dir.equals(base)) return java.nio.file.FileVisitResult.CONTINUE;
                String rel = base.relativize(dir).toString().replace('\\', '/');
                return skipExport(rel)
                        ? java.nio.file.FileVisitResult.SKIP_SUBTREE
                        : java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                                                           java.nio.file.attribute.BasicFileAttributes attrs) {
                Path abs = file.toAbsolutePath().normalize();
                if (!attrs.isRegularFile() || Files.isSymbolicLink(file)
                        || abs.equals(absTarget) || abs.equals(absPart)) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                String rel = base.relativize(file).toString().replace('\\', '/');
                if (!skipExport(rel)) out.add(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        if (out.size() > MAX_ENTRIES) throw new IOException("文件数量超过 30 万，无法导出");
        return out;
    }

    private static boolean skipExport(String rel) {
        if (rel.isEmpty()) return true;
        if (rel.equals("launcher.lock") || rel.equals(Data.CACHE_MARKER)
                || rel.equals("TCraftLauncher.jar")) return true;
        if (rel.endsWith(".part") || rel.endsWith(".tmp") || rel.endsWith(".import.tmp")
                || rel.endsWith(".old") || rel.endsWith(".bak")) return true;
        String first = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
        return SKIP_DIRS.contains(first);
    }

    private static void importTo(MainWindow win, Path zip, ZipInfo info) {
        Path base = Data.get().baseDir().toAbsolutePath().normalize();
        String name = "." + base.getFileName() + "-import-" + OffsetDateTime.now().format(STAMP)
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path staging = base.resolveSibling(name);
        int generation = win.importPanel.begin(Lang.t("正在导入数据"), zip.getFileName().toString(), info.files);
        win.importPanel.note(Lang.t("导入完成后会自动重启启动器，旧数据保留为 .tcl.pre-import-*。"));
        Threads.start("TCL-DataImport", () -> {
            try {
                extractArchive(zip, info, staging,
                        (done, total, label) -> Platform.runLater(
                                () -> win.importPanel.progress(generation, done, total, label)));
                Platform.runLater(() -> win.importPanel.finish(generation, Lang.t("导入完成，正在重启"),
                        () -> restart(win, staging)));
            } catch (Throwable t) {
                deleteTree(staging);
                Platform.runLater(() -> {
                    win.importPanel.close();
                    Dialogs.message(win, Lang.t("导入失败"), message(t), true, Lang.t("确定"));
                });
            }
        });
    }

    // 解压到 data/ 子目录并写导入标记，不负责重启，便于隔离验证
    static void extractArchive(Path zip, ZipInfo info, Path staging, Progress progress) throws IOException {
        Path dataRoot = staging.resolve("data").toAbsolutePath().normalize();
        Files.createDirectories(dataRoot);
        try (ZipFile zf = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            int done = 0;
            long totalBytes = 0;
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String entryName = entry.getName().replace('\\', '/');
                if (!entryName.startsWith(DATA_PREFIX)) continue;
                String rel = entryName.substring(DATA_PREFIX.length());
                if (rel.isEmpty() || skipImport(rel)) continue;
                Path out = dataRoot.resolve(rel).normalize();
                if (!out.startsWith(dataRoot)) throw new IOException("备份包含非法路径：" + rel);
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(entry);
                     OutputStream os = Files.newOutputStream(out)) {
                    long written = copyLimited(in, os, MAX_FILE);
                    totalBytes += written;
                    if (totalBytes > MAX_TOTAL) throw new IOException("备份解压后超过 8 GB");
                }
                done++;
                final int current = done;
                if (progress != null) progress.accept(current, info.files, Lang.tf("正在解压 %s", rel));
            }
        }
        if (!Files.isRegularFile(dataRoot.resolve("config.properties"))) {
            throw new IOException("备份里没有 config.properties，不是有效的 TCraft 数据备份");
        }
        Files.writeString(staging.resolve(IMPORT_MARKER), importMarker(), StandardCharsets.UTF_8);
    }

    private static boolean skipImport(String rel) {
        if (rel.equals("launcher.lock") || rel.equals(Data.CACHE_MARKER)
                || rel.equals("TCraftLauncher.jar")) return true;
        if (rel.endsWith(".part") || rel.endsWith(".tmp") || rel.endsWith(".import.tmp")
                || rel.endsWith(".old") || rel.endsWith(".bak")) return true;
        String first = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
        return SKIP_DIRS.contains(first);
    }

    private static void restart(MainWindow win, Path staging) {
        Path base = Data.get().baseDir().toAbsolutePath().normalize();
        Path pending = base.resolveSibling(base.getFileName() + ".import-pending");
        try {
            // 单文件外壳不保证把参数传给内嵌 jar，用标记文件交接，Windows / macOS / Linux 同一套
            // write then rename: a half-written marker is silently dropped by Boot
            Path tmp = pending.resolveSibling(pending.getFileName() + ".tmp");
            Files.writeString(tmp, ProcessHandle.current().pid() + System.lineSeparator()
                    + staging.toAbsolutePath().normalize(), StandardCharsets.UTF_8);
            move(tmp, pending);
            SelfUpdate.restartSelf();
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(pending);
            } catch (Throwable ignored) {
                // 标记清不掉时下一次启动会重试导入
            }
            Dialogs.message(win, Lang.t("重启失败"),
                    Lang.tf("数据已解压到临时目录，但新进程启动失败：\n%s\n请手动重启。", message(t)),
                    true, Lang.t("确定"));
            return;
        }
        win.closeWindow();
    }

    private static ZipInfo inspect(MainWindow win, Path zip) {
        try {
            return inspectArchive(zip);
        } catch (Throwable t) {
            Dialogs.message(win, Lang.t("导入失败"), message(t), true, Lang.t("确定"));
            return null;
        }
    }

    // 只读校验，不落盘
    static ZipInfo inspectArchive(Path zip) throws IOException {
        if (!Files.isRegularFile(zip)) throw new IOException("找不到备份文件。");
        if (Files.size(zip) > MAX_TOTAL) throw new IOException("备份文件超过 8 GB");
        try (ZipFile zf = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry marker = zf.getEntry(MARKER);
            if (marker == null) throw new IOException("缺少 " + MARKER + "，不是 TCraft 数据备份");
            String text = readText(zf, marker, 64 * 1024);
            var root = Json.obj(Json.parse(text));
            if (!"tcraft-backup".equals(Json.str(root, "type", ""))) {
                throw new IOException("备份类型不正确");
            }
            int entries = 0;
            long total = 0;
            var list = zf.entries();
            while (list.hasMoreElements()) {
                ZipEntry entry = list.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith(DATA_PREFIX)) continue;
                String rel = name.substring(DATA_PREFIX.length());
                if (rel.isEmpty() || skipImport(rel)) continue;
                if (hasBadPath(rel)) throw new IOException("备份包含非法路径：" + rel);
                long size = entry.getSize();
                if (size < 0 || size > MAX_FILE) throw new IOException("备份文件大小异常：" + rel);
                total += size;
                if (total > MAX_TOTAL) throw new IOException("备份解压后超过 8 GB");
                entries++;
                if (entries > MAX_ENTRIES) throw new IOException("备份文件数量过多");
            }
            if (entries == 0) throw new IOException("备份里没有可导入的数据");
            return new ZipInfo(entries, total);
        }
    }

    private static boolean hasBadPath(String rel) {
        String name = rel.replace('\\', '/');
        if (name.startsWith("/") || name.contains(":")) return true;
        Path p = Paths.get(name).normalize();
        if (p.isAbsolute()) return true;
        for (Path part : p) {
            if ("..".equals(part.toString())) return true;
        }
        return false;
    }

    private static String readText(ZipFile zf, ZipEntry entry, int max) throws IOException {
        try (InputStream in = zf.getInputStream(entry)) {
            byte[] buf = in.readNBytes(max + 1);
            if (buf.length > max) throw new IOException("备份清单过大");
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static long copyLimited(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("单个文件超过 4 GB");
            out.write(buf, 0, n);
        }
        return total;
    }

    private static void putText(ZipOutputStream zos, String name, String text) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(text.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String marker(int files, long bytes) {
        return "{\n"
                + "  \"type\": \"tcraft-backup\",\n"
                + "  \"format\": 1,\n"
                + "  \"launcher\": " + Json.quote(Build.VERSION) + ",\n"
                + "  \"created\": " + Json.quote(OffsetDateTime.now().toString()) + ",\n"
                + "  \"files\": " + files + ",\n"
                + "  \"bytes\": " + bytes + "\n"
                + "}\n";
    }

    private static String importMarker() {
        return "{\n"
                + "  \"type\": \"tcraft-backup-import\",\n"
                + "  \"created\": " + Json.quote(OffsetDateTime.now().toString()) + "\n"
                + "}\n";
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static String sizeText(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.2f GB", bytes / 1024.0 / 1024 / 1024);
        }
        if (bytes >= 1024L * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024);
        }
        if (bytes >= 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private static String message(Throwable t) {
        if (t == null) return "未知错误";
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.getClass().getSimpleName() : msg;
    }

    @FunctionalInterface
    interface Progress {
        void accept(int done, int total, String label);
    }

    static record ZipInfo(int files, long bytes) {
    }
}
