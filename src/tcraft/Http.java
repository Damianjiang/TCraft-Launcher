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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Http {

    private Http() {
    }

    private static final String UA = "TCraftLauncher-JavaFX/" + Build.VERSION;

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {300, 900};

    private static ExecutorService idlePool(int size, String name) {
        java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
                size, size, 30, TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
                    Thread t = new Thread(r, name);
                    t.setDaemon(true);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static final ExecutorService POOL = idlePool(
            Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors())), "TCL-Net");

    private static final ExecutorService ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "TCL-Http");
        t.setDaemon(true);
        return t;
    });

    private static volatile HttpClient client = build();
    private static volatile HttpClient clientNoRedirect = build(HttpClient.Redirect.NEVER);

    private static HttpClient build() {
        return build(HttpClient.Redirect.NORMAL);
    }

    private static HttpClient build(HttpClient.Redirect redirects) {
        HttpClient.Builder b = HttpClient.newBuilder()
                // 6s 够完成跨洋握手，再长会拖慢重试
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(redirects)
                // 服务端不支持 h2 时自动回落 HTTP/1.1
                .version(HttpClient.Version.HTTP_2)
                .executor(ASYNC);
        try {
            ProxySelector selector = proxySelector();
            if (selector != null) b.proxy(selector);
        } catch (Throwable t) {
            try {
                Data.get().log("代理初始化失败：" + Log.describe(t));
            } catch (Exception ignored) {
            }
        }
        try {
            // 带账号密码的代理要显式挂 authenticator
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

    public static void rebuild() {
        // 先建好再落 volatile，不留下新旧并存的间隙
        HttpClient redirect = build();
        HttpClient noRedirect = build(HttpClient.Redirect.NEVER);
        clientNoRedirect = noRedirect;
        client = redirect;
    }

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

    public static String get(String url) throws IOException {
        return get(url, Map.of());
    }

    // 单次请求：镜像候选按顺序快速切换，不在这里做退避重试
    public static String getOnce(String url, int timeoutSec) throws IOException {
        return getOnce(url, Map.of(), timeoutSec);
    }

    public static String getOnce(String url, Map<String, String> headers, int timeoutSec) throws IOException {
        try {
            return follow("GET", url, null, Duration.ofSeconds(timeoutSec), headers, null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(Lang.t("请求被中断"), e);
        }
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        if (hasCredentialHeader(headers)) {
            // 自动重定向会原样带走凭证头，改手动跟，跨域时剥掉
            return sendRedirectAware("GET", url, null, Duration.ofSeconds(20), headers, null, true);
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Accept-Encoding", "gzip")
                .GET();
        headers.forEach(b::header);
        return send(b.build(), url);
    }

    public static String post(String url, String body, Map<String, String> headers) throws IOException {
        if (hasCredentialHeader(headers)) {
            return sendRedirectAware("POST", url, body, Duration.ofSeconds(25), headers, "application/json", false);
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build(), url);
    }

    private static final java.util.Set<String> CREDENTIAL_HEADERS =
            java.util.Set.of("x-api-key", "authorization", "cookie");

    private static boolean hasCredentialHeader(Map<String, String> headers) {
        for (String k : headers.keySet()) {
            if (k != null && CREDENTIAL_HEADERS.contains(k.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private static Map<String, String> stripCredentials(Map<String, String> headers) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String k = e.getKey();
            if (k == null || CREDENTIAL_HEADERS.contains(k.toLowerCase(java.util.Locale.ROOT))) continue;
            out.put(k, e.getValue());
        }
        return out;
    }

    private static boolean sameOrigin(String a, String b) {
        try {
            URI x = URI.create(a);
            URI y = URI.create(b);
            String as = x.getScheme() == null ? "" : x.getScheme().toLowerCase(java.util.Locale.ROOT);
            String bs = y.getScheme() == null ? "" : y.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!as.equals(bs)) return false;
            int ap = x.getPort();
            int bp = y.getPort();
            if (ap == -1) ap = "https".equals(as) ? 443 : "http".equals(as) ? 80 : -2;
            if (bp == -1) bp = "https".equals(bs) ? 443 : "http".equals(bs) ? 80 : -2;
            String ah = x.getHost() == null ? "" : x.getHost().toLowerCase(java.util.Locale.ROOT);
            String bh = y.getHost() == null ? "" : y.getHost().toLowerCase(java.util.Locale.ROOT);
            return ah.equals(bh) && ap == bp;
        } catch (Exception e) {
            return false;
        }
    }

    private static HttpRequest newRequest(String method, String url, String body, Duration timeout,
                                          Map<String, String> headers, String contentType, boolean gzip) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", UA);
        if (gzip) b.header("Accept-Encoding", "gzip");
        if (contentType != null) b.header("Content-Type", contentType);
        headers.forEach(b::header);
        if (body != null) {
            b.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            b.GET();
        }
        return b.build();
    }

    private static String sendRedirectAware(String method, String url, String body, Duration timeout,
                                            Map<String, String> headers, String contentType,
                                            boolean gzip) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
            try {
                return follow(method, url, body, timeout, headers, contentType, gzip);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("请求被中断", e);
            } catch (IOException e) {
                if (e instanceof StatusException se && !retryable(se.code)) throw e;
                last = e;
            }
        }
        throw last == null ? new IOException(Lang.t("请求失败：") + url) : last;
    }

    private static String follow(String method, String url, String body, Duration timeout,
                                 Map<String, String> headers, String contentType,
                                 boolean gzip) throws IOException, InterruptedException {
        String current = url;
        Map<String, String> hdrs = headers;
        String m = method;
        String b = body;
        for (int hop = 0; hop < 5; hop++) {
            HttpResponse<InputStream> resp = clientNoRedirect.send(
                    newRequest(m, current, b, timeout, hdrs, contentType, gzip),
                    HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = resp.headers().firstValue("location").orElse("");
                try {
                    resp.body().close();
                } catch (IOException ignored) {
                }
                if (location.isEmpty()) throw new IOException("HTTP " + code);
                String next = URI.create(current).resolve(location).toString();
                if (!sameOrigin(current, next)) {
                    hdrs = stripCredentials(hdrs);
                }
                if ("POST".equals(m) && code <= 303) {
                    // 301/302/303 按 RFC 惯例转 GET，body 不重放
                    m = "GET";
                    b = null;
                }
                current = next;
                continue;
            }
            if (code < 200 || code >= 300) {
                // 先关响应体再抛，429/5xx 逐次重试时连接才不会泄漏
                try {
                    resp.body().close();
                } catch (IOException ignored) {
                }
                if (!retryable(code)) throw new StatusException(code, 0, current);
                throw new IOException("HTTP " + code);
            }
            try (InputStream raw = resp.body()) {
                return readText(raw, resp);
            }
        }
        throw new IOException(Lang.t("重定向超过 5 跳：") + url);
    }

    private static String send(HttpRequest req, String url) throws IOException {
        IOException last = null;
        long nextDelay = BACKOFF_MS[0];
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleep(nextDelay);
            HttpResponse<InputStream> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("请求被中断", e);
            } catch (IOException e) {
                last = e;
                nextDelay = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
                continue;
            }
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                long retryAfter = retryAfterMs(resp);
                try {
                    resp.body().close();
                } catch (IOException ignored) {
                }
                // 4xx 除了 408 / 429 重试没意义
                if (!retryable(code)) throw new IOException("HTTP " + code);
                last = new IOException("HTTP " + code);
                // 429/503 服务端说了等多久就等多久，最多 10 秒
                nextDelay = retryAfter > 0 ? Math.min(retryAfter, 10_000)
                        : BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
                continue;
            }
            try (InputStream raw = resp.body()) {
                return readText(raw, resp);
            } catch (IOException e) {
                last = e;
                nextDelay = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
            }
        }
        throw last == null ? new IOException(Lang.t("请求失败：") + url) : last;
    }

    // 文本响应的解压上限，恶意源的大压缩比 gzip 会打满内存
    private static final long TEXT_MAX_BYTES = 20L * 1024 * 1024;

    private static String readText(InputStream raw, HttpResponse<?> resp) throws IOException {
        String encoding = resp.headers().firstValue("content-encoding").orElse("");
        byte[] bytes;
        if (encoding.toLowerCase(java.util.Locale.ROOT).contains("gzip")) {
            // gzip 流要显式关，释放 inflater 的本地内存
            try (InputStream in = new java.util.zip.GZIPInputStream(raw)) {
                bytes = Fetch.readAll(in, 20_000, TEXT_MAX_BYTES);
            }
        } else {
            // 20 秒无新数据视为假死，交给外层重试
            bytes = Fetch.readAll(raw, 20_000, TEXT_MAX_BYTES);
        }
        String type = resp.headers().firstValue("content-type").orElse("");
        java.nio.charset.Charset cs = StandardCharsets.UTF_8;
        int idx = type.toLowerCase(java.util.Locale.ROOT).indexOf("charset=");
        if (idx >= 0) {
            String name = type.substring(idx + 8).split("[;,\\s]")[0].replace("\"", "").trim();
            try {
                cs = java.nio.charset.Charset.forName(name);
            } catch (Exception ignored) {
            }
        }
        return new String(bytes, cs);
    }

    public static byte[] getBytes(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA)
                    .GET()
                    .build();
            HttpResponse<InputStream> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("请求被中断", e);
            } catch (IOException e) {
                last = e;
                continue;
            }
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                // 二进制响应也设上限，皮肤/图标等合法尺寸远小于 8MB
                try (InputStream body = resp.body()) {
                    return Fetch.readAll(body, 20_000, 8L * 1024 * 1024);
                }
            }
            if (!retryable(code)) throw new IOException("HTTP " + code);
            last = new IOException("HTTP " + code);
        }
        throw last == null ? new IOException(Lang.t("请求失败：") + url) : last;
    }

    public static CompletableFuture<byte[]> getBytesOnce(String url, int timeoutSec) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("User-Agent", UA)
                .GET()
                .build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(resp -> {
                    int code = resp.statusCode();
                    try (java.io.InputStream body = resp.body()) {
                        if (code >= 200 && code < 300) return Fetch.readAll(body, 20_000, 8L * 1024 * 1024);
                        throw new CompletionException(new StatusException(code, retryAfterMs(resp)));
                    } catch (java.io.IOException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    public static CompletableFuture<byte[]> getBytesAsync(String url) {
        return bytesAttempt(url, 0);
    }

    private static CompletableFuture<byte[]> bytesAttempt(String url, int attempt) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .GET()
                .build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
                .handle((resp, err) -> {
                    if (err != null) throw new CompletionException(unwrap(err));
                    int code = resp.statusCode();
                    // 与 getBytes 同一个 8MB 上限，恶意源不能吃满堆
                    try (java.io.InputStream body = resp.body()) {
                        if (code >= 200 && code < 300) return Fetch.readAll(body, 20_000, 8L * 1024 * 1024);
                        long retryAfter = retryAfterMs(resp);
                        throw new CompletionException(new StatusException(code, retryAfter));
                    } catch (java.io.IOException e) {
                        throw new CompletionException(e);
                    }
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

    public static final class Body implements AutoCloseable {
        public final InputStream in;
        public final long length;
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

    public static Body open(String url) throws IOException {
        // 有镜像先走镜像，失败再回原站
        List<String> candidates = Mirrors.images(url);
        IOException last = null;
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            boolean mirror = !candidate.equals(url);
            try {
                return openAttempt(candidate, Duration.ofSeconds(mirror ? 15 : 30), -1, -1,
                        mirror ? 1 : MAX_ATTEMPTS);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException(Lang.t("请求失败：") + url);
    }

    public static Body open(String url, Duration timeout) throws IOException {
        return open(url, timeout, -1, -1);
    }

    public static Body open(String url, Duration timeout, long rangeStart) throws IOException {
        return open(url, timeout, rangeStart, -1);
    }

    public static Body open(String url, Duration timeout, long rangeStart, long rangeEnd) throws IOException {
        return openAttempt(url, timeout, rangeStart, rangeEnd, MAX_ATTEMPTS);
    }

    private static Body openAttempt(String url, Duration timeout, long rangeStart, long rangeEnd,
                                    int maxAttempts) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
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
            // Fetch 靠类型识别 416（Range 起点超出文件末尾）
            if (!retryable(code)) throw new StatusException(code, 0, url);
            last = new IOException("HTTP " + code);
        }
        throw last == null ? new IOException(Lang.t("请求失败：") + url) : last;
    }

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

    static final class StatusException extends IOException {
        private static final long serialVersionUID = 1L;
        final int code;
        final long retryAfterMs;

        StatusException(int code, long retryAfterMs) {
            super("HTTP " + code);
            this.code = code;
            this.retryAfterMs = retryAfterMs;
        }

        StatusException(int code, long retryAfterMs, String url) {
            super("HTTP " + code + (url == null || url.isEmpty() ? "" : " " + url));
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
