package tcraft;

import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** EasyTier 联机模块的安装与运行封装。 */
public final class EasyTier {

    public static final String VERSION = "v2.6.4";

    /** 下载镜像前缀池，最后两个是直连兜底。 */
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

    /** 公共节点。 */
    public static final String[] PUBLIC_PEERS = {
            "https://etnode.zkitefly.eu.org/node1",
            "tcp://225284.xyz:11010",
            "tcp://et1.fuis.top:11010"
    };

    private static Process core;
    private static int rpc = -1;

    private EasyTier() {
    }

    public interface Progress {
        void update(String stage, double value);
    }

    public static boolean isWindows() {
        return os().contains("win");
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
        return "linux";
    }

    /** 下载用的架构名，ARM64 在 Windows 叫 arm64。 */
    public static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("aarch64") || a.contains("arm64")) {
            // Windows 用 arm64，其他平台用 aarch64。
            return isWindows() ? "arm64" : "aarch64";
        }
        if (a.contains("arm")) return "armv7";
        if (a.contains("x86") || a.contains("i386") || a.contains("i686")) {
            return is64Bit() ? "x86_64" : "i686";
        }
        return "x86_64";
    }

    private static boolean is64Bit() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("64")) return true;
        return !"32".equals(System.getProperty("sun.arch.data.model", "64"));
    }

    public static String coreName() {
        return isWindows() ? "easytier-core.exe" : "easytier-core";
    }

    public static String cliName() {
        return isWindows() ? "easytier-cli.exe" : "easytier-cli";
    }

    /** 联机模块目录：~/.tcraft-launcher-javafx/EasyTier/ */
    public static Path folder() {
        return Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx", "EasyTier");
    }

    public static Path corePath() {
        return folder().resolve(coreName());
    }

    public static Path cliPath() {
        return folder().resolve(cliName());
    }

    /** core 和 cli 都在，才算装好。 */
    public static boolean installed() {
        return Files.isRegularFile(corePath()) && Files.isRegularFile(cliPath());
    }

    /** 下载节点下拉框用的镜像主机名。 */
    public static String[] mirrorHosts() {
        String[] out = new String[PREFIXES.length];
        for (int i = 0; i < PREFIXES.length; i++) {
            String host = "节点 " + (i + 1);
            try {
                host = java.net.URI.create(PREFIXES[i]).getHost();
            } catch (Exception ignored) {
            }
            out[i] = host == null ? "节点 " + (i + 1) : host;
        }
        return out;
    }

    /** 最快节点缓存，-2 表示还没测过。 */
    private static volatile int cachedBestNode = -2;

    /** 并发测速各镜像，最快的写进缓存，全挂返回 -1。 */
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
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(Math.min(6, PREFIXES.length), r -> {
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
        int best = -1, bestMs = Integer.MAX_VALUE;
        for (java.util.concurrent.Future<int[]> f : futures) {
            try {
                int[] res = f.get(6, TimeUnit.SECONDS);
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
            // 只记最快节点下标，下载时置顶。
            d.set("LinkBestNode", String.valueOf(best));
            d.save();
            d.log("联机下载节点测速：" + host + " " + bestMs + " ms");
        } else {
            d.log("联机下载节点测速：全部节点不可用，按默认顺序尝试");
        }
        return best;
    }

    /** 生成下载地址列表，最快节点置顶。 */
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

    /** 缺失或版本不符时下载解压，force 时强制重下。 */
    public static void ensureInstalled(boolean force, Progress p) throws Exception {
        Path dir = folder();
        Files.createDirectories(dir);
        Path marker = dir.resolve(".version");
        if (!force && installed()) {
            String v = Files.isRegularFile(marker) ? Files.readString(marker).trim() : "";
            if (VERSION.equals(v)) {
                p.update("联机模块已就绪", 0.35);
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
                p.update("正在下载联机模块…", 0.05 + 0.55 * i / urls.size());
                download(url, zip, p);
                ok = true;
                break;
            } catch (Exception e) {
                last = e;
            }
        }
        if (!ok) {
            Files.deleteIfExists(zip);
            throw new Exception("联机模块下载失败："
                    + (last == null || last.getMessage() == null ? "未知网络错误" : last.getMessage()));
        }

        p.update("正在解压联机模块…", 0.7);
        extract(zip, dir);
        Files.deleteIfExists(zip);
        Files.writeString(marker, VERSION);
        if (!installed()) throw new Exception("联机模块解压后缺少必要文件，请重试");
        markExecutable();
        p.update("联机模块准备完成", 0.75);
    }

    private static void download(String url, Path target, Progress p) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "TCraftLauncher-JavaFX/1.3.7");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[65536];
            long read = 0;
            int n;
            int lastPercent = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    int percent = (int) (read * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        p.update("正在下载联机模块… " + percent + "%", 0.05 + 0.65 * percent / 100.0);
                    }
                }
            }
        }
    }

    /** 只留 core 和 cli 相关文件，跳过 GUI。 */
    private static void extract(Path zip, Path dir) throws Exception {
        List<Path> temp = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                String base = name.substring(name.lastIndexOf('/') + 1);
                String lower = base.toLowerCase(Locale.ROOT);
                if (lower.startsWith("easytier-web") || lower.startsWith("easytier-gui")) continue;
                if (!lower.startsWith("easytier-core") && !lower.startsWith("easytier-cli")
                        && !lower.endsWith(".dll") && !lower.endsWith(".so") && !lower.endsWith(".dylib")) {
                    continue;
                }
                Path out = dir.resolve(base);
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                temp.add(out);
            }
        }
        if (temp.isEmpty()) throw new Exception("压缩包内容为空");
        // 带版本后缀的文件名统一改成固定名。
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

    /** 找一个空闲端口，分配失败就逐个探测。 */
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
            throw new IllegalStateException("找不到可用的联机端口（25000~27000 均被占用）", e);
        }
    }

    /** 启动核心进程，macOS 非 root 时用 osascript 提权。 */
    public static Process start(String argument) throws Exception {
        stop();
        Path exe = corePath();
        if (!Files.isRegularFile(exe)) throw new Exception("联机模块未安装");
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.toString());
        cmd.addAll(splitArgs(argument));
        ProcessBuilder pb;
        if (isMac() && !isElevated()) {
            // macOS 建 utun 要 root，只提权 core 进程，顺便清掉上次残留。
            String shell = "pkill -f easytier-core; cd " + shellQuote(folder().toString()) + " && "
                    + cmd.stream().map(EasyTier::shellQuote).collect(java.util.stream.Collectors.joining(" "));
            pb = new ProcessBuilder("osascript", "-e",
                    "do shell script " + applescriptString(shell) + " with administrator privileges");
        } else {
            pb = new ProcessBuilder(cmd);
            pb.directory(folder().toFile());
        }
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        core = pb.start();
        return core;
    }

    /** 双引号包裹单个 token，防止注入和展开。 */
    private static String shellQuote(String token) {
        return "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("$", "\\$").replace("`", "\\`") + "\"";
    }

    /** AppleScript 字符串转义，命令里还要再转一层。 */
    private static String applescriptString(String shellCmd) {
        return "\"" + shellCmd.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 停止核心进程和子进程，root 起的杀不掉。 */
    public static void stop() {
        rpc = -1;
        if (core == null) return;
        try {
            core.descendants().forEach(ProcessHandle::destroy);
            core.destroy();
            if (!core.waitFor(3, TimeUnit.SECONDS)) core.destroyForcibly();
        } catch (Exception ignored) {
        }
        core = null;
    }

        public static int rpcPort() {
        return rpc;
    }

    public static void rpcPort(int port) {
        rpc = port;
    }

    /** 调 CLI，阻塞带超时，返回标准输出。 */
    public static String runCli(String argument, int timeoutMs) throws Exception {
        Path exe = cliPath();
        if (!Files.isRegularFile(exe)) throw new Exception("联机模块 CLI 未安装");
        ProcessBuilder pb = new ProcessBuilder(exe.toString());
        for (String part : splitArgs(argument)) pb.command().add(part);
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
            throw new Exception("联机模块 CLI 在 " + timeoutMs + "ms 内没有响应");
        }
        reader.join(1000);
        return out.toString(StandardCharsets.UTF_8);
    }

    /** 把一串启动参数按空格切开，支持双引号包裹。 */
    private static List<String> splitArgs(String s) {
        List<String> list = new ArrayList<>();
        if (s == null) return list;
        StringBuilder cur = new StringBuilder();
        boolean quote = false;
        for (char ch : s.toCharArray()) {
            if (ch == '"') {
                quote = !quote;
            } else if (Character.isWhitespace(ch) && !quote) {
                if (cur.length() > 0) {
                    list.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) list.add(cur.toString());
        return list;
    }

    /** 自己 / 客户端 / 房主 / 其他。 */
    public static final int PEER_SELF = 0, PEER_CLIENT = 1, PEER_SERVER = 2, PEER_MISC = 3;

    public static class Peer {
        public int type = PEER_MISC;
        public String hostname = "";
        public String ipv4 = "";
        public String cost = "";
        /** 延迟毫秒，0 表示还没测出来。 */
        public double ping;
        public boolean relay;
        /** 0~9 是 EasyTier NAT 类型，6 是 Symmetric，10 表示还没获取。 */
        public int natType = 10;

        public boolean self() {
            return type == PEER_SELF;
        }

            }

    /** 查询当前连上的节点。 */
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
                p.type = PEER_SERVER; // 只认这个固定 IP 作为房主
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
            list.add(p); // 自己的节点也要留着
        }
        return list;
    }

    /** 联机模块进程还在不在，用来检测崩溃。 */
    public static boolean crashed() {
        return core != null && !core.isAlive();
    }

    /** 清理上次异常退出留下的核心进程。 */
    public static void killLeftovers() {
        try {
            ProcessHandle.allProcesses()
                    .filter(h -> h.info().command().isPresent())
                    .filter(h -> {
                        String cmd = h.info().command().orElse("").toLowerCase(Locale.ROOT);
                        return cmd.contains("easytier-core");
                    })
                    .forEach(ProcessHandle::destroy);
        } catch (Exception ignored) {
        }
    }

    /** 权限结果缓存，避免每次都跑 3 秒外部命令。 */
    private static volatile Boolean elevatedCache;

    /** 当前进程有没有管理员权限。 */
    public static boolean isElevated() {
        Boolean cached = elevatedCache;
        if (cached != null) return cached;
        boolean value;
        if (!isWindows()) {
            try {
                value = "0".equals(runCommand("id", "-u").trim());
            } catch (Exception e) {
                value = "root".equals(System.getProperty("user.name"));
            }
        } else {
            try {
                String out = runCommand("net", "session");
                value = out != null && !out.toLowerCase(Locale.ROOT).contains("拒绝访问")
                        && !out.toLowerCase(Locale.ROOT).contains("access is denied");
            } catch (Exception e) {
                value = false;
            }
        }
        elevatedCache = value;
        return value;
    }

    private static String runCommand(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        p.waitFor(3, TimeUnit.SECONDS);
        return out.toString(StandardCharsets.UTF_8);
    }

    /** 官方节点列表 API，邀请码里会带节点 ID。 */
    public static final String NODES_API = "https://uptime.easytier.cn/api/nodes?page=1&per_page=1000";

    /** 一个发现或中继节点。 */
    public static class Node {
        public int id;
        public String address = "";
        public boolean allowRelay;
        /** 负载，越小越好，已按 usage × 可用率折算。 */
        public double load;
    }

    /** 拉取官方节点，只留 active 且带国内 MC 中继标签的。 */
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
        c.setRequestProperty("User-Agent", "TCraftLauncher-JavaFX/1.3.7");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** 补可执行位，macOS 去 quarantine。 */
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
                // 没有 xattr 就忽略，命令行程序一般不会被拦
            }
        }
    }

    /** NAT 类型名转枚举序数，未知返回 10。 */
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

    /** 系统能否建虚拟网卡，不能则返回原因。 */
    public static String platformBlocker() {
        // 模块没装时跳过检查。
        if (!installed()) return null;
        if (isWindows()) {
            return Files.isRegularFile(folder().resolve("wintun.dll")) ? null
                    : "联机模块缺少 wintun.dll，请删除联机模块目录后重试。";
        }
        if (isMac()) {
            // 只确认 osascript 存在，正常 macOS 都有。
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v osascript").start();
                return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0 ? null
                        : "系统缺少 osascript，无法以管理员权限启动联机内核。";
            } catch (Exception e) {
                return null;
            }
        }
        if (!Files.isReadable(Paths.get("/dev/net/tun"))) {
            return "系统没有可用的 /dev/net/tun，联机模块无法创建虚拟网卡。";
        }
        return isElevated() ? null : "Linux 创建虚拟网卡需要 root 权限：请用 sudo 启动 TCL，"
                + "或给 easytier-core 授予 CAP_NET_ADMIN。";
    }

    /** 联机模块所在目录，方便打开文件夹。 */
    public static Path moduleFolder() {
        return folder();
    }
}
