package tcraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class JavaInstaller {

    private static final String ALL_JSON =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final String ALL_JSON_BMCL =
            "https://bmclapi2.bangbang93.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    public static final String[][] COMPONENTS = {
            {"Java 8（旧版）", "jre-legacy"},
            {"Java 17（1.18+）", "java-runtime-gamma"},
            {"Java 21（1.20.5+）", "java-runtime-delta"},
            {"Java 25（最新）", "java-runtime-epsilon"},
    };

    public static String platformKey() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x86 = arch.equals("x86") || arch.equals("i386");
        if (os.contains("win")) return arm ? "windows-arm64" : x86 ? "windows-x86" : "windows-x64";
        if (os.contains("mac") || os.contains("darwin")) return arm ? "mac-os-arm64" : "mac-os";
        // Mojang 没有发布 Linux ARM64 运行时，走手动安装
        if (arm) return "";
        if (x86) return "linux-i386";
        return "linux";
    }

    public static Path install(String component, java.util.function.Consumer<String> status,
                               java.util.function.BooleanSupplier cancelled) throws Exception {
        return install(component, status, cancelled, null);
    }

    public static Path install(String component, java.util.function.Consumer<String> status,
                               java.util.function.BooleanSupplier cancelled,
                               java.util.function.DoubleConsumer progress) throws Exception {
        String key = platformKey();
        if (key.isEmpty()) throw new IOException(Lang.t("当前平台不受 Mojang Java 运行时支持"));
        // source 同 Downloader.mirrors：1=仅官方
        String text = Data.get().getInt("SystemDownloadSource", 0) == 1
                ? Downloader.getText(ALL_JSON)
                : Downloader.getText(ALL_JSON_BMCL, ALL_JSON);
        Map<String, Object> all = Json.obj(Json.parse(text));
        List<Object> entries = Json.arr(Json.obj(all.get(key)), component);
        if (entries.isEmpty()) {
            throw new IOException(Lang.t("官方清单里没有 ") + component + "（" + key + "）");
        }
        Map<String, Object> entry = Json.obj(entries.get(0));
        String manifestUrl = Json.str(Json.obj(entry.get("manifest")), "url", "");
        if (manifestUrl.isEmpty()) throw new IOException(Lang.t("Java 运行时清单缺少下载地址"));

        Map<String, Object> manifest = Json.obj(Json.parse(Downloader.getText(manifestUrl)));
        Map<String, Object> files = Json.obj(manifest.get("files"));
        Path base = Resources.home().resolve("java").resolve(key);
        Path target = base.resolve(component);
        Path temp = base.resolve("." + component + ".tmp");
        deleteDir(temp);
        Files.createDirectories(temp);

        int total = files.size();
        if (status != null) status.accept(Lang.t("正在准备 Java 运行时…"));
        if (progress != null) progress.accept(0);
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        Path backup = base.resolve("." + component + ".old");
        boolean installed = false;
        try {
            List<Map.Entry<String, Map<String, Object>>> fileEntries = new java.util.ArrayList<>();
            for (Map.Entry<String, Object> e : files.entrySet()) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new java.util.concurrent.CancellationException("已取消");
                }
                String rel = e.getKey();
                Map<String, Object> file = Json.obj(e.getValue());
                String type = Json.str(file, "type", "file");
                // 清单路径不可信，../ 会越出临时目录
                Path dst = temp.resolve(rel).normalize();
                if (!dst.startsWith(temp)) throw new IOException(Lang.t("清单包含非法路径：") + rel);
                if ("directory".equals(type)) {
                    Files.createDirectories(dst);
                    done.incrementAndGet();
                } else if ("link".equals(type)) {
                    String linkTarget = Json.str(file, "target", "");
                    if (linkTarget.startsWith("/") || linkTarget.startsWith("\\")) continue;
                    try {
                        // 相对链接允许 ../，但须仍落在临时目录内
                        Path resolved = dst.getParent().resolve(linkTarget).normalize();
                        if (!resolved.startsWith(temp)) continue;
                        Files.createDirectories(dst.getParent());
                        Files.createSymbolicLink(dst, Paths.get(linkTarget));
                    } catch (Exception ignored) {
                    }
                    done.incrementAndGet();
                } else {
                    fileEntries.add(new java.util.AbstractMap.SimpleEntry<>(rel, file));
                }
            }
            if (!fileEntries.isEmpty()) {
                java.util.concurrent.atomic.AtomicBoolean stop =
                        new java.util.concurrent.atomic.AtomicBoolean();
                java.util.concurrent.atomic.AtomicReference<Exception> firstError =
                        new java.util.concurrent.atomic.AtomicReference<>();
                ExecutorService pool = Executors.newFixedThreadPool(
                        Math.max(1, Math.min(8, fileEntries.size())), r -> {
                            Thread t = new Thread(r, "TCL-JavaFile");
                            t.setDaemon(true);
                            return t;
                        });
                List<Future<?>> futures = new java.util.ArrayList<>();
                try {
                    for (Map.Entry<String, Map<String, Object>> fe : fileEntries) {
                        futures.add(pool.submit(() -> {
                            try {
                                if (stop.get()) return;
                                if (cancelled != null && cancelled.getAsBoolean()) {
                                    firstError.compareAndSet(null,
                                            new java.util.concurrent.CancellationException("已取消"));
                                    stop.set(true);
                                    return;
                                }
                                String rel = fe.getKey();
                                Map<String, Object> file = fe.getValue();
                                Map<String, Object> raw = Json.obj(Json.obj(file.get("downloads")).get("raw"));
                                String url = Json.str(raw, "url", "");
                                if (!url.isEmpty()) {
                                    Path dst = temp.resolve(rel).normalize();
                                    Files.createDirectories(dst.getParent());
                                    String sha1 = Json.str(raw, "sha1", "");
                                    Downloader.downloadFileTo(null, url, dst, rel, sha1);
                                    if (Boolean.parseBoolean(String.valueOf(file.get("executable")))) {
                                        dst.toFile().setExecutable(true, false);
                                    }
                                    int n = done.incrementAndGet();
                                    if (status != null && n % 5 == 0) {
                                        status.accept(Lang.tf("正在下载 Java 运行时 %d / %d", n, total));
                                    }
                                    if (progress != null && n % 5 == 0 && total > 0) {
                                        progress.accept(Math.min(0.95, 0.90 * n / total));
                                    }
                                }
                            } catch (Exception e) {
                                firstError.compareAndSet(null, e);
                                stop.set(true);
                            }
                        }));
                    }
                    for (Future<?> f : futures) {
                        try {
                            f.get();
                        } catch (Exception ignored) {
                        }
                    }
                } finally {
                    pool.shutdownNow();
                }
                if (status != null) {
                    status.accept(Lang.tf("正在下载 Java 运行时 %d / %d", Math.min(done.get(), total), total));
                }
                if (firstError.get() != null) throw firstError.get();
            }

            if (status != null) status.accept(Lang.t("正在解压 Java 运行时…"));
            if (progress != null) progress.accept(0.96);
            deleteDir(backup);
            if (Files.exists(target)) {
                try {
                    Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    deleteDir(target);
                }
            }
            Files.createDirectories(base);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                copyDir(temp, target);
                deleteDir(temp);
            }
            Path exe = target.resolve("bin").resolve(Shell.windows() ? "java.exe" : "java");
            if (!Files.isRegularFile(exe) && Shell.mac()) {
                // macOS 的 JRE 通常在 jre.bundle/Contents/Home 下
                Path bundled = target.resolve("jre.bundle").resolve("Contents").resolve("Home")
                        .resolve("bin").resolve("java");
                if (Files.isRegularFile(bundled)) exe = bundled;
                Path macExe = target.resolve("Contents").resolve("Home").resolve("bin").resolve("java");
                if (Files.isRegularFile(macExe)) exe = macExe;
            }
            if (!Files.isRegularFile(exe)) throw new IOException(Lang.t("安装完成但找不到 java 可执行文件"));
            exe.toFile().setExecutable(true, false);
            installed = true;
            deleteDir(backup);
            if (progress != null) progress.accept(1);
            return exe;
        } catch (Exception e) {
            deleteDir(temp);
            if (!installed && Files.exists(backup)) {
                deleteDir(target);
                try {
                    Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }
            }
            throw e;
        }
    }

    private static void deleteDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void copyDir(Path src, Path dst) throws IOException {
        try (var walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path out = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(out);
                else Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void register(Path exe) {
        int major = Data.javaMajor(exe.toString());
        Data.get().setJavaRuntime(exe.toString(), major);
    }

    public static void startInstallAsync(MainWindow win, String component) {
        if (platformKey().isEmpty()) {
            win.toast("当前平台不支持自动下载", "请手动安装 Java，然后用「导入 Java…」添加。", 3);
            return;
        }
        Tasks.Task task = new Tasks.Task("下载 " + component);
        task.stage = Lang.t("连接 Mojang 清单…");
        Tasks.INSTANCE.add(task);
        Thread t = new Thread(() -> {
            try {
                Path exe = install(component, s -> {
                    task.stage = s;
                    Tasks.INSTANCE.changed();
                }, () -> task.canceled);
                task.state = Tasks.State.DONE;
                task.progress = 1;
                register(exe);
                javafx.application.Platform.runLater(() -> {
                    win.toast("Java 已安装", exe.toString(), 1);
                    win.refreshRight();
                });
            } catch (java.util.concurrent.CancellationException c) {
                task.state = Tasks.State.CANCELED;
                javafx.application.Platform.runLater(() -> win.toast("已取消", "Java 下载已取消", 2));
            } catch (Throwable e) {
                task.state = Tasks.State.FAILED;
                task.error = e.getMessage();
                javafx.application.Platform.runLater(() -> win.toast("Java 下载失败", e.getMessage(), 3));
            } finally {
                Tasks.INSTANCE.changed();
            }
        }, "TCL-JavaInstall");
        t.setDaemon(true);
        t.start();
    }
}
