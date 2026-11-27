package tcraft;

import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class EasyTier {

    public static final String VERSION = "v2.6.4";

    private static final Map<String, String> SHA256 = Map.ofEntries(
            Map.entry("windows-x86_64", "27af91e270e554709b048bd32327fefd2dfce5062ae1e8701af7550c6f525f84"),
            Map.entry("windows-arm64", "37023f8a3451c9234b17ee2089a03dc344ce90d803b5b359cb6c46682b0549b4"),
            Map.entry("windows-i686", "bf557daeccc5525d95b8a230c339d75554fb52d82d1e050e9a5202c92c02e09e"),
            Map.entry("macos-x86_64", "89fc28a6e6995259d76ce3f11775220e8a21c760e94df91a6a9db30a69b6982e"),
            Map.entry("macos-aarch64", "4be1882d1aa36d31c1d6ba0596f2cf8a097e371f8da124212324b2e0f8df7e4b"),
            Map.entry("linux-x86_64", "61b659eaedba658fa66fe47d17e1426cdd77e5d02fa15fed447bb4357c09dfd6"),
            Map.entry("linux-aarch64", "f533ec25a7ea714e09f645615012200278058525795cc3bb690ff011aec1a70f"),
            Map.entry("linux-loongarch64", "40c2cd76c0fe6d4a518181ec70d750fbbba36f1a24b3b4c44d4dfc58cb233cf8"),
            Map.entry("linux-riscv64", "58393357ff31770a45f922289874ac784f7f7be5cab50002115d29ab5bcaa64d"),
            Map.entry("linux-armv7", "93b1d2831e45db1fd3ca1d8d68c191b300bd69d331ca1858394a0cf884363cc3"),
            Map.entry("freebsd-13.2-x86_64", "a34d52a8890c181054d9d8cfb775a2a2c8940a7b606c262e3059e6ecd542c8fb")
    );

    private static final String[] PREFIXES = {
            "https://gh-proxy.com/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://gh.monlor.com/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://gh-proxy.org/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://github.cnxiaobai.com/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://gh.padao.fun/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://30006000.xyz/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://gh.acmsz.top/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://ghfast.top/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://ghproxy.net/https://github.com/EasyTier/EasyTier/releases/download/",
            "https://gitee.com/easytier/EasyTier/releases/download/",
            "https://github.com/EasyTier/EasyTier/releases/download/"
    };

    public static final String[] PUBLIC_PEERS = {
            "https://etnode.zkitefly.eu.org/node1",
            "tcp://225284.xyz:11010",
            "tcp://et1.fuis.top:11010"
    };

    private static volatile Process core;

    private EasyTier() {
    }

    public interface Progress {
        void update(String stage, double value);
    }

    public static boolean isWindows() {
        return os().contains("windows");
    }

    public static boolean isMac() {
        return os().contains("mac") || os().contains("darwin");
    }

    private static String os() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    public static String osTag() {
        if (isWindows()) return "windows";
        if (isMac()) return "macos";
        if (os().contains("freebsd")) return "freebsd-13.2";
        return "linux";
    }

    public static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("loongarch64")) return "loongarch64";
        if (a.contains("riscv64")) return "riscv64";
        if (a.contains("mips64")) return "mips64";
        if (a.contains("mipsel")) return "mipsel";
        if (a.contains("mips")) return "mips";
        if (a.contains("aarch64") || a.contains("arm64")) {
                return isWindows() ? "arm64" : "aarch64";
        }
        if (a.contains("arm")) return "armv7";
        if (a.contains("x86") || a.contains("i386") || a.contains("i686")) {
            return is64Bit() ? "x86_64" : "i686";
        }
        return "x86_64";
    }

    private static boolean is64Bit() {
        // os.arch 必带 64 sun.arch.data.model 不可靠
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return a.contains("64");
    }

    public static String coreName() {
        return isWindows() ? "easytier-core.exe" : "easytier-core";
    }

    public static String cliName() {
        return isWindows() ? "easytier-cli.exe" : "easytier-cli";
    }

    public static Path folder() {
        return Resources.home().resolve("EasyTier");
    }

    public static Path corePath() {
        return folder().resolve(coreName());
    }

    public static Path cliPath() {
        return folder().resolve(cliName());
    }

    public static boolean installed() {
        return Files.isRegularFile(corePath()) && Files.isRegularFile(cliPath());
    }

    public static String[] mirrorHosts() {
        // 标签仅展示 下载用原始值
        String[] out = new String[PREFIXES.length];
        for (int i = 0; i < PREFIXES.length; i++) {
            String host = Lang.t("节点 ") + (i + 1);
            try {
                host = java.net.URI.create(PREFIXES[i]).getHost();
            } catch (Exception ignored) {
            }
            out[i] = host == null ? Lang.t("节点 ") + (i + 1) : host;
        }
        return out;
    }

    private static volatile int cachedBestNode = -2;

    public static synchronized int measureFastestNode(boolean force) {
        Data d = Data.get();
        if (!force && cachedBestNode != -2) return cachedBestNode;
        if (!force) {
            int cached = d.getInt("LinkBestNode", -1);
            if (cached >= 0 && cached < PREFIXES.length) {
                cachedBestNode = cached;
                return cached;
            }
        }
        String file = "easytier-" + osTag() + "-" + arch() + "-" + VERSION + ".zip";
        int threads = Math.min(6, PREFIXES.length);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "TCL-NodeSpeed");
                    t.setDaemon(true);
                    return t;
                });
        List<java.util.concurrent.Future<int[]>> futures = new ArrayList<>();
        for (int i = 0; i < PREFIXES.length; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) URI.create(PREFIXES[idx] + VERSION + "/" + file)
                            .toURL().openConnection();
                    c.setConnectTimeout(2500);
                    c.setReadTimeout(3500);
                    c.setRequestProperty("Range", "bytes=0-262143");
                    c.setInstanceFollowRedirects(true);
                    long t0 = System.nanoTime();
                    int code = c.getResponseCode();
                    if (code != 200 && code != 206) return new int[]{idx, Integer.MAX_VALUE};
                    try (InputStream in = c.getInputStream()) {
                        byte[] buf = new byte[8192];
                        int total = 0, r;
                        while (total < 262144 && (r = in.read(buf)) > 0) total += r;
                        if (total <= 0) return new int[]{idx, Integer.MAX_VALUE};
                    }
                    return new int[]{idx, (int) Math.min(Integer.MAX_VALUE,
                            (System.nanoTime() - t0) / 1_000_000)};
                } catch (Exception e) {
                    return new int[]{idx, Integer.MAX_VALUE};
                } finally {
                    if (c != null) c.disconnect();
                }
            }));
        }
        pool.shutdown();
        // 等待上限含任务与排队余量
        long perTaskMs = 7000;
        int rounds = (PREFIXES.length + threads - 1) / threads;
        long getTimeoutMs = perTaskMs * (rounds + 1);
        int best = -1, bestMs = Integer.MAX_VALUE;
        for (java.util.concurrent.Future<int[]> f : futures) {
            try {
                int[] res = f.get(getTimeoutMs, TimeUnit.MILLISECONDS);
                if (res[1] < bestMs) {
                    bestMs = res[1];
                    best = res[0];
                }
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        cachedBestNode = best;
        if (best >= 0) {
            String host = mirrorHosts()[best];
            d.set("LinkBestNode", String.valueOf(best));
            d.save();
            d.log("联机下载节点测速：" + host + " " + bestMs + " ms");
        } else {
            d.log("联机下载节点测速：全部节点不可用，按默认顺序尝试");
        }
        return best;
    }

    public static List<String> downloadUrls() {
        String file = "easytier-" + osTag() + "-" + arch() + "-" + VERSION + ".zip";
        List<String> prefixes = new ArrayList<>();
        if (Data.get().getInt("LinkDownloadMode", 0) == 1) {
            int idx = Data.get().getInt("LinkDownloadNode", 0);
            if (idx >= 0 && idx < PREFIXES.length) prefixes.add(PREFIXES[idx]);
        } else {
            int best = measureFastestNode(false);
            if (best >= 0 && best < PREFIXES.length) prefixes.add(PREFIXES[best]);
        }
        for (String prefix : PREFIXES) if (!prefixes.contains(prefix)) prefixes.add(prefix);
        List<String> urls = new ArrayList<>();
        for (String prefix : prefixes) urls.add(prefix + VERSION + "/" + file);
        return urls;
    }

    public static void ensureInstalled(boolean force, Progress p) throws Exception {
        Path dir = folder();
        Files.createDirectories(dir);
        Path marker = dir.resolve(".version");
        if (!force && installed()) {
            String v = "";
            if (Files.isRegularFile(marker) && Files.size(marker) <= 4096) {
                try (InputStream in = Files.newInputStream(marker)) {
                    v = new String(Io.read(in, 4096), java.nio.charset.StandardCharsets.UTF_8).trim();
                }
            }
            if (VERSION.equals(v)) {
                p.update(Lang.t("联机模块已就绪"), 0.35);
                return;
            }
        }

        Path zip = Files.createTempFile("TCL_EasyTier_", ".zip");
        Exception last = null;
        boolean ok = false;
        List<String> urls = downloadUrls();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            try {
                p.update(Lang.t("正在下载联机模块…"), 0.05 + 0.55 * i / urls.size());
                download(url, zip, p);
                ok = true;
                break;
            } catch (InterruptedException ie) {
                Files.deleteIfExists(zip);
                throw ie;
            } catch (Exception e) {
                last = e;
            }
        }
        if (!ok) {
            Files.deleteIfExists(zip);
            throw new Exception(Lang.tf("联机模块下载失败：%s",
                    last == null || last.getMessage() == null ? Lang.t("未知网络错误") : last.getMessage()));
        }

        p.update(Lang.t("正在解压联机模块…"), 0.7);
        try {
            extract(zip, dir);
        } finally {
            Files.deleteIfExists(zip);
        }
        Files.writeString(marker, VERSION);
        if (!installed()) throw new Exception(Lang.t("联机模块解压后缺少必要文件，请重试"));
        markExecutable();
        p.update(Lang.t("联机模块准备完成"), 0.75);
    }

    private static void download(String url, Path target, Progress p) throws Exception {
        // core/dll 会被执行 无校验值不下载
        String expected = SHA256.getOrDefault(osTag() + "-" + arch(), "");
        if (expected.isEmpty()) {
            throw new Exception(Lang.tf("当前平台（%s-%s）没有内置的联机模块校验值，出于安全考虑已取消下载",
                    osTag(), arch()));
        }
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", Http.UA);
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        long total = c.getContentLengthLong();
        // 联机核心固定 SHA-256 先限体积 防坏镜像写满磁盘
        long maxBytes = 256L * 1024 * 1024;
        if (total > maxBytes) throw new Exception(Lang.t("联机模块体积异常"));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = c.getInputStream();
             DigestOutputStream out = new DigestOutputStream(Files.newOutputStream(target), digest)) {
            byte[] buf = new byte[65536];
            long read = 0;
            int n;
            int lastPercent = -1;
            while ((n = in.read(buf)) > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException(Lang.t("联机模块下载已取消"));
                }
                read += n;
                if (read > maxBytes) throw new Exception(Lang.t("联机模块体积异常"));
                out.write(buf, 0, n);
                if (total > 0) {
                    int percent = (int) (read * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        p.update(Lang.tf("正在下载联机模块… %d%%", percent), 0.05 + 0.65 * percent / 100.0);
                    }
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(target);
            throw e;
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!expected.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(target);
            throw new Exception(Lang.t("联机模块校验失败，请更换下载节点后重试"));
        }
    }

    private static void extract(Path zip, Path dir) throws Exception {
        List<Path> temp = new ArrayList<>();
        Path outDir = dir.toAbsolutePath().normalize();
        Files.createDirectories(outDir);
        long total = 0;
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (++count > 4096) throw new Exception(Lang.t("联机模块压缩包条目过多"));
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                String base = name.substring(name.lastIndexOf('/') + 1);
                String lower = base.toLowerCase(Locale.ROOT);
                if (lower.startsWith("easytier-web") || lower.startsWith("easytier-gui")) continue;
                if (!lower.startsWith("easytier-core") && !lower.startsWith("easytier-cli")
                        && !lower.endsWith(".dll") && !lower.endsWith(".so") && !lower.endsWith(".dylib")) {
                    continue;
                }
                Path out = outDir.resolve(base).normalize();
                if (base.isEmpty() || !out.startsWith(outDir)) continue;
                try (OutputStream os = Files.newOutputStream(out,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    total += Io.copy(in, os, 512L * 1024 * 1024 - total);
                }
                temp.add(out);
            }
        }
        if (temp.isEmpty()) throw new Exception(Lang.t("压缩包内容为空"));
        for (Path f : temp) {
            String base = f.getFileName().toString();
            String lower = base.toLowerCase(Locale.ROOT);
            if (lower.equals("easytier-core") || lower.equals("easytier-core.exe")) {
                Path target = corePath();
                if (!f.equals(target)) Files.move(f, target, StandardCopyOption.REPLACE_EXISTING);
            } else if (lower.equals("easytier-cli") || lower.equals("easytier-cli.exe")) {
                Path target = cliPath();
                if (!f.equals(target)) Files.move(f, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static int freePort() {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (Exception e) {
            for (int p = 25000; p < 27000; p++) {
                try (java.net.ServerSocket probe = new java.net.ServerSocket(p)) {
                    probe.setReuseAddress(true);
                    return p;
                } catch (Exception ignored) {
                }
            }
            throw new IllegalStateException(Lang.t("找不到可用的联机端口（25000~27000 均被占用）"), e);
        }
    }

    public static boolean portAvailable(int port) {
        if (port <= 0 || port > 65535) return false;
        try (java.net.ServerSocket tcp = new java.net.ServerSocket(port);
             java.net.DatagramSocket udp = new java.net.DatagramSocket(port)) {
            return tcp.getLocalPort() == port && udp.getLocalPort() == port;
        } catch (Exception e) {
            return false;
        }
    }

    public static int measureNode(String address, int timeoutMs) {
        if (address == null) return -1;
        String raw = address.trim();
        if (raw.isEmpty()) return -1;
        String scheme = "";
        String rest = raw;
        int sep = raw.indexOf("://");
        if (sep > 0) {
            scheme = raw.substring(0, sep).toLowerCase(Locale.ROOT);
            rest = raw.substring(sep + 3);
        }
        // HTTP 节点直接测首响应
        if (scheme.equals("http") || scheme.equals("https")) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) URI.create(raw).toURL().openConnection();
                c.setConnectTimeout(timeoutMs);
                c.setReadTimeout(timeoutMs);
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod("HEAD");
                long t0 = System.nanoTime();
                int code = c.getResponseCode();
                return code > 0 ? (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - t0) / 1_000_000) : -1;
            } catch (Exception e) {
                return -1;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        int slash = rest.indexOf('/');
        String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
        return tcpPing(hostPort, scheme, timeoutMs);
    }

    public static Map<String, Integer> measureNodes(List<String> addresses, int timeoutMs) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        List<String> unique = new ArrayList<>();
        for (String one : addresses) {
            if (one == null || one.isBlank()) continue;
            String trimmed = one.trim();
            if (!unique.contains(trimmed)) unique.add(trimmed);
        }
        if (unique.isEmpty()) return out;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                Math.min(8, unique.size()), r -> {
                    Thread t = new Thread(r, "TCL-PeerSpeed");
                    t.setDaemon(true);
                    return t;
                });
        List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();
        for (String one : unique) futures.add(pool.submit(() -> measureNode(one, timeoutMs)));
        pool.shutdown();
        for (int i = 0; i < unique.size(); i++) {
            int ms = -1;
            try {
                ms = futures.get(i).get(timeoutMs + 800L, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            out.put(unique.get(i), ms);
        }
        pool.shutdownNow();
        return out;
    }

    private static int tcpPing(String hostPort, String scheme, int timeoutMs) {
        String host = hostPort;
        int port = switch (scheme) {
            case "http", "ws" -> 80;
            case "https", "wss" -> 443;
            default -> 11010;
        };
        if (hostPort.startsWith("[")) {
            int end = hostPort.indexOf(']');
            if (end < 0) return -1;
            host = hostPort.substring(1, end);
            if (end + 2 <= hostPort.length() && hostPort.charAt(end + 1) == ':') {
                try {
                    port = Integer.parseInt(hostPort.substring(end + 2));
                } catch (Exception e) {
                    return -1;
                }
            }
        } else {
            int colon = hostPort.lastIndexOf(':');
            if (colon > 0 && hostPort.indexOf(':') == colon) {
                host = hostPort.substring(0, colon);
                try {
                    port = Integer.parseInt(hostPort.substring(colon + 1));
                } catch (Exception e) {
                    return -1;
                }
            }
        }
        if (host.isEmpty() || port <= 0 || port > 65535) return -1;
        long t0 = System.nanoTime();
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception e) {
            return -1;
        }
    }

    public static synchronized Process start(String argument) throws Exception {
        stop();
        Path exe = corePath();
        if (!Files.isRegularFile(exe)) throw new Exception(Lang.t("联机模块未安装"));
        // 拷贝可能丢执行位
        if (!isWindows()) markExecutable();
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.toString());
        cmd.addAll(Shell.splitArgs(argument));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(folder().toFile());
        pb.redirectErrorStream(true);
        Path log = folder().resolve("easytier.log");
        try {
            if (Files.isRegularFile(log) && Files.size(log) > 5L * 1024 * 1024) Files.delete(log);
        } catch (Exception ignored) {
        }
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        core = pb.start();
        return core;
    }

    public static synchronized void stop() {
        Process p = core;
        if (p == null) return;
        core = null;
        try {
            // Snapshot children first: once the parent exits they may be reparented.
            List<ProcessHandle> kids = new ArrayList<>();
            try {
                p.descendants().forEach(kids::add);
            } catch (Exception ignored) {
            }
            kids.forEach(ProcessHandle::destroy);
            p.destroy();
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly();
            for (ProcessHandle h : kids) {
                if (h.isAlive()) h.destroyForcibly();
            }
            // Also catch children spawned after the snapshot.
            try {
                p.descendants().forEach(h -> {
                    if (h.isAlive()) h.destroyForcibly();
                });
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    public static void stopAsync() {
        Thread t = new Thread(EasyTier::stop, "TCL-EasyTierStop");
        t.setDaemon(true);
        t.start();
    }

    public static String logTail(int lines) {
        try {
            Path log = folder().resolve("easytier.log");
            if (!Files.isRegularFile(log)) return "";
            java.util.List<String> all = Files.readAllLines(log, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - Math.max(1, lines));
            return String.join("\n", all.subList(from, all.size()));
        } catch (Exception e) {
            return "";
        }
    }

    public static String runCli(String argument, int timeoutMs) throws Exception {
        Path exe = cliPath();
        if (!Files.isRegularFile(exe)) throw new Exception(Lang.t("联机模块 CLI 未安装"));
        if (!isWindows() && !exe.toFile().canExecute()) markExecutable();
        ProcessBuilder pb = new ProcessBuilder(exe.toString());
        for (String part : Shell.splitArgs(argument)) pb.command().add(part);
        pb.directory(folder().toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (Exception ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly();
            throw new Exception(Lang.t("联机模块 CLI 在 ") + timeoutMs + Lang.t("ms 内没有响应"));
        }
        reader.join(1000);
        return out.toString(StandardCharsets.UTF_8);
    }

    public static final int PEER_SELF = 0, PEER_CLIENT = 1, PEER_SERVER = 2, PEER_MISC = 3;

    public static class Peer {
        public int type = PEER_MISC;
        public String hostname = "";
        public String ipv4 = "";
        public String cost = "";
        public double ping;
        public boolean relay;
        public int natType = 10;

        public boolean self() {
            return type == PEER_SELF;
        }
    }

    public static List<Peer> peers(int port) throws Exception {
        String text = runCli("-o json -p 127.0.0.1:" + port + " peer", 4000);
        List<Peer> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;
        Object root = Json.parse(text);
        List<Object> items = root instanceof List ? Json.arr(root) : Json.arr(root, "peers");
        for (Object item : items) {
            java.util.Map<String, Object> m = Json.obj(item);
            Peer p = new Peer();
            p.hostname = Json.str(m, "hostname", "");
            p.ipv4 = Json.str(m, "ipv4", "");
            p.cost = Json.str(m, "cost", "");
            p.relay = p.cost.toLowerCase(Locale.ROOT).contains("relay");
            if ("local".equalsIgnoreCase(p.cost)) {
                p.type = PEER_SELF;
            } else if (p.hostname.startsWith("Client")) {
                p.type = PEER_CLIENT;
            } else if ("10.114.114.114".equals(p.ipv4)) {
                p.type = PEER_SERVER;
            } else {
                p.type = PEER_MISC;
            }
            p.natType = natTypeOf(Json.str(m, "nat_type", ""));
            Object lat = m.get("lat_ms");
            if (lat instanceof Number) p.ping = ((Number) lat).doubleValue();
            else if (lat != null) {
                try {
                    p.ping = Double.parseDouble(lat.toString().trim());
                } catch (Exception ignored) {
                }
            }
            list.add(p);
        }
        return list;
    }

    public static boolean crashed() {
        return core != null && !core.isAlive();
    }

    public static void killLeftovers() {
        try {
            String own = folder().toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            ProcessHandle.allProcesses()
                    .filter(h -> h.info().command().isPresent())
                    .filter(h -> {
                        String cmd = h.info().command().orElse("").toLowerCase(Locale.ROOT);
                        return cmd.contains("easytier-core") && cmd.startsWith(own);
                    })
                    // Only orphans: never kill a core owned by another live launcher.
                    .filter(h -> h.parent().map(parent -> !parent.isAlive()).orElse(true))
                    .forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
        }
    }

    private static String runCommand(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        // 进程不关 stdout 时 transferTo 永远阻塞 先等退出
        if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static final String NODES_API = "https://uptime.easytier.cn/api/nodes?page=1&per_page=1000";

    public static class Node {
        public int id;
        public String address = "";
        public boolean allowRelay;
        public double load;
    }

    public static List<Node> fetchNodes(int preferId, List<String> blacklist) throws Exception {
        String text = httpGet(NODES_API);
        Map<String, Object> root = Json.obj(Json.parse(text));
        List<Object> items = Json.arr(Json.obj(root.get("data")), "items");
        List<Node> out = new ArrayList<>();
        java.util.Random random = new java.util.Random();
        for (Object item : items) {
            Map<String, Object> m = Json.obj(item);
            int id = (int) Json.num(m, "id", -1);
            if (!Json.bool(m, "is_active", false) || !Json.bool(m, "is_approved", false)) continue;
            String address = Json.str(m, "address", "");
            if (address.isEmpty()) continue;
            boolean allowRelay = Json.bool(m, "allow_relay", false);
            if (id != preferId) {
                if (blacklist != null && blacklist.contains(address)) continue;
                double usage = Json.num(m, "usage_percentage", 0);
                if (usage <= 0 && random.nextInt(100) != 0) continue;
                boolean tagCn = false, tagMc = false;
                for (Object tag : Json.arr(m, "tags")) {
                    String t = String.valueOf(tag);
                    if (t.contains("国内")) tagCn = true;
                    if (t.contains("MC中继")) tagMc = true;
                }
                if (!tagCn || !tagMc) continue;
            }
            Node n = new Node();
            n.id = id;
            n.address = address;
            n.allowRelay = allowRelay;
            n.load = Json.num(m, "usage_percentage", 0)
                    * (110 - Json.num(m, "health_percentage_24h", 0));
            out.add(n);
        }
        out.sort((a, b) -> Double.compare(a.load, b.load));
        return out;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", Http.UA);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    public static void markExecutable() {
        try {
            corePath().toFile().setExecutable(true, false);
            cliPath().toFile().setExecutable(true, false);
        } catch (Exception ignored) {
        }
        if (!isWindows()) {
            try {
                runCommand("chmod", "755", corePath().toString(), cliPath().toString());
            } catch (Exception ignored) {
            }
        }
        if (isMac()) {
            try {
                runCommand("xattr", "-dr", "com.apple.quarantine", folder().toString());
            } catch (Exception ignored) {
            }
        }
    }

    public static int natTypeOf(String name) {
        if (name == null) return 10;
        String n = name.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return switch (n) {
            case "unknown" -> 0;
            case "openinternet" -> 1;
            case "nopat" -> 2;
            case "fullcone" -> 3;
            case "restricted" -> 4;
            case "portrestricted" -> 5;
            case "symmetric" -> 6;
            case "symudpfirewall" -> 7;
            case "symmetriceasyinc" -> 8;
            case "symmetriceasydec" -> 9;
            default -> 10;
        };
    }
}
