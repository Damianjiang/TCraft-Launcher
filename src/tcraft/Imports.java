package tcraft;

import javafx.application.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class Imports {

    private Imports() {
    }

    public static void run(MainWindow win, String title, String targetLabel, Path target,
                           List<Path> sources, Predicate<String> allowed, boolean keepDisabled,
                           Runnable after) {
        if (sources == null || sources.isEmpty()) return;
        if (win.importPanel.isBusy()) {
            win.toast("正在导入", "请等待当前导入完成", 2);
            return;
        }
        List<Path> input = new ArrayList<>(sources);
        int gen = win.importPanel.begin(title, targetLabel, 0);
        Thread worker = new Thread(() -> {
            List<Path> files = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            try {
                for (Path source : input) {
                    if (Files.isDirectory(source)) {
                        try (var walk = Files.walk(source)) {
                            walk.filter(Files::isRegularFile).forEach(p -> {
                                if (accept(p, allowed)) files.add(p);
                                else rejected.add(p.getFileName().toString());
                            });
                        }
                    } else if (Files.isRegularFile(source)) {
                        if (accept(source, allowed)) files.add(source);
                        else rejected.add(source.getFileName().toString());
                    } else {
                        rejected.add(source.getFileName() == null ? source.toString() : source.getFileName().toString());
                    }
                }
            } catch (Exception e) {
                finish(win, gen, targetLabel, 0, 0, 0, rejected.size(), 1, 0, rejected,
                        List.of(Lang.t("读取文件列表失败：") + (e.getMessage() == null ? e.toString() : e.getMessage())), after);
                return;
            }
            int total = files.size() + rejected.size();
            update(win, gen, 0, total, Lang.tf("待导入 %d 个文件", files.size()));
            int imported = 0;
            int failed = 0;
            int same = 0;
            List<String> failures = new ArrayList<>();
            int done = 0;
            long last = 0;
            for (Path source : files) {
                String name = source.getFileName().toString();
                String targetName = name;
                if (!keepDisabled && targetName.toLowerCase(Locale.ROOT).endsWith(".disabled")) {
                    targetName = targetName.substring(0, targetName.length() - ".disabled".length());
                }
                Path destination = target.resolve(targetName).normalize();
                try {
                    if (!destination.toAbsolutePath().startsWith(target.toAbsolutePath().normalize())) {
                        failed++;
                        failures.add(name);
                    } else if (Files.exists(destination)
                            && source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
                        same++;
                    } else {
                        Files.createDirectories(target);
                        copyInto(source, destination);
                        imported++;
                    }
                } catch (Exception e) {
                    failed++;
                    failures.add(name);
                }
                done++;
                long now = System.currentTimeMillis();
                if (done == files.size() || now - last > 55) {
                    last = now;
                    update(win, gen, done, total, Lang.t("正在复制 ") + name);
                }
            }
            finish(win, gen, targetLabel, total, files.size(), imported, rejected.size(), failed, same, rejected,
                    failures, after);
        }, "TCL-Import");
        worker.setDaemon(true);
        worker.start();
    }

    // Copy to a sibling temp file first so a failed copy never leaves a truncated mod behind.
    private static void copyInto(Path source, Path destination) throws java.io.IOException {
        Path tmp = destination.resolveSibling(destination.getFileName() + ".tcl-import.tmp");
        try {
            Files.copy(source, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean accept(Path path, Predicate<String> allowed) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String base = name.endsWith(".disabled") ? name.substring(0, name.length() - 9) : name;
        return allowed.test(base);
    }

    private static void update(MainWindow win, int gen, int done, int total, String text) {
        Platform.runLater(() -> win.importPanel.progress(gen, done, total, text));
    }

    private static void finish(MainWindow win, int gen, String targetLabel,
                               int total, int accepted, int imported, int skipped, int failed, int same,
                               List<String> rejected, List<String> failures, Runnable after) {
        String summary = summary(targetLabel, total, accepted, imported, skipped, failed, same, rejected, failures);
        Platform.runLater(() -> win.importPanel.finish(gen, imported > 0 ? Lang.t("导入完成") : Lang.t("没有导入文件"), () -> {
            if (after != null) after.run();
            Dialogs.message(win, imported > 0 ? Lang.t("导入完成") : Lang.t("导入结果"), summary,
                    skipped > 0 || failed > 0, Lang.t("知道了"));
        }));
    }

    private static String summary(String targetLabel, int total, int accepted,
                                  int imported, int skipped, int failed, int same, List<String> rejected,
                                  List<String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.t("目标：")).append(targetLabel).append("\n");
        sb.append(Lang.tf("读取到 %d 个文件\n", total));
        sb.append(Lang.tf("可导入 %d 个\n", accepted));
        sb.append(Lang.tf("已导入 %d 个", imported));
        if (same > 0) sb.append("\n").append(Lang.tf("已在目录中 %d 个", same));
        if (skipped > 0) sb.append("\n").append(Lang.tf("已跳过 %d 个（后缀不支持）", skipped));
        if (failed > 0) sb.append("\n").append(Lang.tf("导入失败 %d 个", failed));
        appendNames(sb, Lang.t("跳过"), rejected);
        appendNames(sb, Lang.t("失败"), failures);
        return sb.toString();
    }

    private static void appendNames(StringBuilder sb, String label, List<String> names) {
        if (names.isEmpty()) return;
        sb.append("\n\n").append(Lang.tf("%s：", label));
        int limit = Math.min(8, names.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append("\n");
            sb.append("· ").append(names.get(i));
        }
        if (names.size() > limit) sb.append("\n").append(Lang.tf("… 还有 %d 个", names.size() - limit));
    }
}
