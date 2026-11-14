package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 统一 HTTP 客户端，共享连接池、自动重试，改代理后要 rebuild。 */
public final class Http {

    private Http() {
    }

    private static final String UA = "TCraftLauncher-JavaFX/1.3.7";

    /** 最多试 3 次，首发加两次重试。 */
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {300, 900};

    /** 网络任务线程池，守护线程。 */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors())), r -> {
                Thread t = new Thread(r, "TCL-Net");
                t.setDaemon(true);
                return t;
            });

    /** 图标下载池。 */
    private static final ExecutorService ICON_POOL = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "TCL-Icon");
        t.setDaemon(true);
        return t;
    });

    /** 重试延时调度线程。 */
    private static final ExecutorService ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "TCL-Http");
        t.setDaemon(true);
        return t;
    });

    private static volatile HttpClient client = build();

    private static HttpClient build() {
        HttpClient.Builder b = HttpClient.newBuilder()
                // 6s 够完成跨洋握手，再长会拖慢重试
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                // 服务端不支持 h2 时自动回落 HTTP/1.1
                .version(HttpClient.Version.HTTP_2)
                .executor(ASYNC);
        try {
            ProxySelector selector = proxySelector();
            if (selector != null) b.proxy(selector);
        } catch (Throwable ignored) {
        }
        try {
            // 带账号密码的代理要显式挂 authenticator。
            final String user = Data.get().get("SystemProxyUser", "");
            final String pass = Data.get().get("SystemProxyPass", "");
            if (!user.isEmpty() || !pass.isEmpty()) {
                b.authenticator(new java.net.Authenticator() {
                    @Override
                    protected java.net.PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() != RequestorType.PROXY) return null;
                        return new java.net.PasswordAuthentication(user, pass.toCharArray());
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        return b.build();
    }

    /** 代理变了就重建客户端。 */
    public static void rebuild() {
        client = build();
    }

    /** 有没有设 SOCKS 代理，JDK 不支持。 */
    public static boolean socksConfigured() {
        try {
            String h = System.getProperty("socksProxyHost", "");
            return h != null && !h.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 代理选择器，遇到 SOCKS 就退回直连。 */
    private static ProxySelector proxySelector() {
        final ProxySelector def = ProxySelector.getDefault();
        if (def == null) return null;
        return new ProxySelector() {
            @Override
            public java.util.List<java.net.Proxy> select(URI uri) {
                try {
                    java.util.List<java.net.Proxy> list = def.select(uri);
                    if (list == null || list.isEmpty()) return java.util.List.of(java.net.Proxy.NO_PROXY);
                    java.util.List<java.net.Proxy> out = new java.util.ArrayList<>(list.size());
                    for (java.net.Proxy p : list) {
                        if (p != null && p.type() == java.net.Proxy.Type.SOCKS) {
                            warnSocksOnce();
                            out.add(java.net.Proxy.NO_PROXY);
                        } else {
                            out.add(p == null ? java.net.Proxy.NO_PROXY : p);
                        }
                    }
                    return out;
                } catch (Exception e) {
                    return java.util.List.of(java.net.Proxy.NO_PROXY);
                }
            }

            @Override
            public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
                try {
                    def.connectFailed(uri, sa, ioe);
                } catch (Exception ignored) {
                }
            }
        };
    }

    private static volatile boolean socksWarned;

    private static void warnSocksOnce() {
        if (socksWarned) return;
        socksWarned = true;
        try {
            Data.get().log("检测到 SOCKS 代理：启动器内部下载不支持 SOCKS，已改为直连。"
                    + "如需让启动器也走代理，请在「设置 → 网络」改用 HTTP 代理模式。");
        } catch (Exception ignored) {
        }
    }

    public static HttpClient client() {
        return client;
    }

    public static ExecutorService pool() {
        return POOL;
    }

    public static ExecutorService iconPool() {
        return ICON_POOL;
    }

    /** 在共享池里跑一个任务，不等待。 */
    public static void run(Runnable task) {
        POOL.execute(task);
    }

    public static String get(String url) throws IOException {
        return get(url, Map.of());
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                // 清单几 MB，gzip 后小很多
                .header("Accept-Encoding", "gzip")
                .GET();
        headers.forEach(b::header);
        return send(b.build(), url);
    }

    public static String post(String url, String body, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build(), url);
    }

    /** 发请求并重试。 */
    private static String send(HttpRequest req, String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
            HttpResponse<InputStream> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("请求被中断", e);
            } catch (IOException e) {
                last = e; // 连接失败，重试
                continue;
            }
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                try {
                    resp.body().close();
                } catch (IOException ignored) {
                }
                // 4xx 除了 408 / 429 重试没意义。
                if (!retryable(code)) throw new IOException("HTTP " + code);
                last = new IOException("HTTP " + code);
                continue;
            }
            try (InputStream raw = resp.body()) {
                return readText(raw, resp);
            } catch (IOException e) {
                last = e; // 读 body 断流，重试
            }
        }
        throw last == null ? new IOException("请求失败：" + url) : last;
    }

    /** 按编码和 Content-Type 解码响应体。 */
    private static String readText(InputStream raw, HttpResponse<?> resp) throws IOException {
        InputStream in = raw;
        String encoding = resp.headers().firstValue("content-encoding").orElse("");
        if (encoding.toLowerCase().contains("gzip")) in = new java.util.zip.GZIPInputStream(raw);
        byte[] bytes = in.readAllBytes();
        String type = resp.headers().firstValue("content-type").orElse("");
        java.nio.charset.Charset cs = StandardCharsets.UTF_8;
        int idx = type.toLowerCase().indexOf("charset=");
        if (idx >= 0) {
            String name = type.substring(idx + 8).split("[;,\\s]")[0].replace("\"", "").trim();
            try {
                cs = java.nio.charset.Charset.forName(name);
            } catch (Exception ignored) {
            }
        }
        return new String(bytes, cs);
    }

    /** 取二进制，图标和小文件用，同步。 */
    public static byte[] getBytes(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<byte[]> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("请求被中断", e);
            } catch (IOException e) {
                last = e; // 连接失败，重试
                continue;
            }
            int code = resp.statusCode();
            if (code >= 200 && code < 300) return resp.body();
            if (!retryable(code)) throw new IOException("HTTP " + code);
            last = new IOException("HTTP " + code);
        }
        throw last == null ? new IOException("请求失败：" + url) : last;
    }

    /** 取二进制，异步不占线程。 */
    public static CompletableFuture<byte[]> getBytesAsync(String url) {
        return bytesAttempt(url, 0);
    }

    private static CompletableFuture<byte[]> bytesAttempt(String url, int attempt) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .GET()
                .build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                .handle((resp, err) -> {
                    if (err != null) throw new CompletionException(unwrap(err));
                    int code = resp.statusCode();
                    if (code >= 200 && code < 300) return resp.body();
                    long retryAfter = retryAfterMs(resp);
                    throw new CompletionException(new StatusException(code, retryAfter));
                })
                .exceptionallyCompose(e -> {
                    Throwable cause = unwrap(e);
                    if (attempt + 1 >= MAX_ATTEMPTS || !retryable(cause)) {
                        return CompletableFuture.failedFuture(cause);
                    }
                    long delay = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
                    if (cause instanceof StatusException se && se.retryAfterMs > 0) {
                        delay = Math.min(se.retryAfterMs, 5000);
                    }
                    CompletableFuture<byte[]> next = new CompletableFuture<>();
                    CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, ASYNC)
                            .execute(() -> bytesAttempt(url, attempt + 1).whenComplete((v, ex) -> {
                                if (ex != null) next.completeExceptionally(unwrap(ex));
                                else next.complete(v);
                            }));
                    return next;
                });
    }

    /** 流式响应体，close 后连接回池。 */
    public static final class Body implements AutoCloseable {
        public final InputStream in;
        public final long length;
        /** 状态码，206 表示接受 Range。 */
        public final int status;

        Body(InputStream in, long length, int status) {
            this.in = in;
            this.length = length;
            this.status = status;
        }

        @Override
        public void close() {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** 打开下载流，调用方负责 close。 */
    public static Body open(String url) throws IOException {
        return open(url, Duration.ofSeconds(30), -1, -1);
    }

    public static Body open(String url, Duration timeout) throws IOException {
        return open(url, timeout, -1, -1);
    }

    public static Body open(String url, Duration timeout, long rangeStart) throws IOException {
        return open(url, timeout, rangeStart, -1);
    }

    /** 打开下载流，rangeStart 用于续传。 */
    public static Body open(String url, Duration timeout, long rangeStart, long rangeEnd) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", UA)
                    .GET();
            if (rangeEnd >= 0) {
                builder.header("Range", "bytes=" + Math.max(0, rangeStart) + "-" + rangeEnd);
            } else if (rangeStart > 0) {
                builder.header("Range", "bytes=" + rangeStart + "-");
            }
            HttpResponse<InputStream> resp;
            try {
                resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("请求被中断", e);
            } catch (IOException e) {
                last = e;
                continue;
            }
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                long len = resp.headers().firstValueAsLong("content-length").orElse(-1L);
                return new Body(resp.body(), len, code);
            }
            try {
                resp.body().close();
            } catch (IOException ignored) {
            }
            if (!retryable(code)) throw new IOException("HTTP " + code + " " + url);
            last = new IOException("HTTP " + code);
        }
        throw last == null ? new IOException("请求失败：" + url) : last;
    }

    /** 启动预热，先做掉 DNS 和握手。 */
    public static void warmup(String... urls) {
        ASYNC.execute(() -> {
            for (String url : urls) {
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(6))
                            .header("User-Agent", UA)
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .build();
                    client.send(req, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {
                    // 预热失败无所谓
                }
            }
        });
    }

    private static boolean retryable(int code) {
        return code == 408 || code == 429 || code >= 500;
    }

    private static boolean retryable(Throwable t) {
        if (t instanceof StatusException se) return retryable(se.code);
        return t instanceof IOException;
    }

    private static long retryAfterMs(HttpResponse<?> resp) {
        try {
            return resp.headers().firstValue("retry-after")
                    .map(v -> (long) (Double.parseDouble(v.trim()) * 1000))
                    .orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 永久性失败，4xx 重试无效。 */
    public static boolean isPermanentFailure(Throwable t) {
        Throwable u = unwrap(t);
        return u instanceof StatusException se && se.code >= 400 && se.code < 500
                && se.code != 408 && se.code != 429;
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while ((cur instanceof CompletionException || cur instanceof java.util.concurrent.ExecutionException)
                && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    /** 非 2xx 状态，区分可重试和不可重试。 */
    private static final class StatusException extends IOException {
        final int code;
        final long retryAfterMs;

        StatusException(int code, long retryAfterMs) {
            super("HTTP " + code);
            this.code = code;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
