package tcraft.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Network access for plugins: HTTP, WebSocket, TCP and UDP.
///
/// Blocking calls must not run on the JavaFX application thread. Use
/// `ctx.scheduler().async(...)` or a plugin-owned background thread instead.
public final class Network {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final long TEXT_LIMIT = 16L * 1024 * 1024;
    private static final long BINARY_LIMIT = 64L * 1024 * 1024;

    private final PluginContext ctx;
    private final List<AutoCloseable> resources = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    Network(PluginContext ctx) {
        this.ctx = ctx;
    }

    /// Sends a GET request and returns the response body as UTF-8 text.
    public String get(String url) throws IOException {
        return request("GET", url, Map.of(), null);
    }

    /// Sends a GET request with custom headers.
    public String get(String url, Map<String, String> headers) throws IOException {
        return request("GET", url, headers, null);
    }

    /// Sends a POST request with an `application/json` body by default.
    public String post(String url, String body) throws IOException {
        return post(url, body, Map.of());
    }

    /// Sends a POST request with custom headers.
    public String post(String url, String body, Map<String, String> headers) throws IOException {
        return request("POST", url, headers, body);
    }

    /// Sends a form POST and URL-encodes the supplied fields.
    public String postForm(String url, Map<String, String> fields) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return request("POST", url, Map.of("Content-Type", "application/x-www-form-urlencoded"), body.toString());
    }

    /// Sends an arbitrary HTTP method. A non-empty body is sent as UTF-8 text.
    public String request(String method, String url, Map<String, String> headers, String body) throws IOException {
        return request(method, url, headers, body, "application/json; charset=UTF-8");
    }

    /// Sends an arbitrary HTTP method with an explicit content type.
    public String request(String method, String url, Map<String, String> headers, String body,
                          String contentType) throws IOException {
        checkOpen();
        URI uri = httpUri(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        Map<String, String> actual = new LinkedHashMap<>();
        if (headers != null) actual.putAll(headers);
        actual.putIfAbsent("User-Agent", "TCraftLauncher-Plugin/" + ctx.launcherVersion());
        if (body != null && !body.isEmpty() && contentType != null && !contentType.isBlank()) {
            actual.putIfAbsent("Content-Type", contentType);
        }
        actual.forEach((key, value) -> {
            if (key != null && value != null && !key.isBlank()) builder.header(key, value);
        });
        String verb = method == null || method.isBlank() ? "GET" : method.toUpperCase(Locale.ROOT);
        if (body == null || body.isEmpty()) {
            builder.method(verb, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(verb, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<InputStream> response = send(builder.build(),
                HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        try (InputStream in = response.body()) {
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " " + response.uri());
            }
            return readText(in, TEXT_LIMIT);
        }
    }

    /// Downloads a URL into a file and returns the written path.
    public Path download(String url, Path target) throws IOException {
        return download(url, target, null);
    }

    /// Downloads a URL into a file and reports `(bytesRead, contentLength)`.
    public Path download(String url, Path target, BiConsumer<Long, Long> progress) throws IOException {
        checkOpen();
        if (target == null) throw new IllegalArgumentException("target is null");
        URI uri = httpUri(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "TCraftLauncher-Plugin/" + ctx.launcherVersion())
                .GET()
                .build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            InputStream body = response.body();
            body.close();
            throw new IOException("HTTP " + status + " " + response.uri());
        }
        long total = response.headers().firstValueAsLong("content-length").orElse(-1L);
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            long read = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                read += n;
                if (progress != null) progress.accept(read, total);
            }
            out.flush();
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        } catch (RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /// Reads a URL as bytes. The response is capped at 64 MiB.
    public byte[] bytes(String url) throws IOException {
        checkOpen();
        URI uri = httpUri(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .header("User-Agent", "TCraftLauncher-Plugin/" + ctx.launcherVersion())
                .GET()
                .build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        try (InputStream in = response.body()) {
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status + " " + response.uri());
            return readBytes(in, BINARY_LIMIT);
        }
    }

    /// Opens a WebSocket. Callbacks run on the HTTP client executor, not the JavaFX thread.
    public WebSocketSession websocket(String url, Consumer<String> onText,
                                      Consumer<Throwable> onError) throws IOException {
        return websocket(url, onText, onError, null);
    }

    /// Opens a WebSocket with an optional close callback.
    public WebSocketSession websocket(String url, Consumer<String> onText,
                                      Consumer<Throwable> onError,
                                      Consumer<String> onClose) throws IOException {
        checkOpen();
        URI uri = websocketUri(url);
        WebSocketSession session = new WebSocketSession(onText, onError, onClose);
        try {
            WebSocket socket = tcraft.Http.client().newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .buildAsync(uri, session)
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            session.attach(socket);
        } catch (Exception e) {
            throw new IOException("WebSocket connect failed: " + e.getMessage(), e);
        }
        resources.add(session);
        return session;
    }

    /// Opens a TCP connection with a 10 second connect timeout.
    public TcpConnection tcp(String host, int port) throws IOException {
        return tcp(host, port, 10000);
    }

    /// Opens a TCP connection with a custom connect timeout in milliseconds.
    public TcpConnection tcp(String host, int port, int connectTimeoutMs) throws IOException {
        checkOpen();
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), Math.max(0, connectTimeoutMs));
        TcpConnection connection = new TcpConnection(socket);
        resources.add(connection);
        return connection;
    }

    /// Opens an unconnected UDP socket.
    public UdpSocket udp() throws IOException {
        return udp(0);
    }

    /// Opens a UDP socket bound to a local port; port `0` picks a free port.
    public UdpSocket udp(int localPort) throws IOException {
        checkOpen();
        DatagramSocket socket = new DatagramSocket(localPort);
        UdpSocket udp = new UdpSocket(socket);
        resources.add(udp);
        return udp;
    }

    /// URL-encodes a value with UTF-8.
    public static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /// Closes every WebSocket, TCP and UDP connection opened by this plugin.
    public void close() {
        if (closed) return;
        closed = true;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception ignored) {
            }
        }
        resources.clear();
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("network API is closed");
    }

    private static URI httpUri(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IOException("invalid URL: " + url, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("only http/https URLs are supported");
        }
        return uri;
    }

    private static URI websocketUri(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IOException("invalid WebSocket URL: " + url, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("ws") && !scheme.equals("wss")) {
            throw new IOException("only ws/wss URLs are supported");
        }
        return uri;
    }

    private static String readText(InputStream in, long limit) throws IOException {
        return new String(readBytes(in, limit), StandardCharsets.UTF_8);
    }

    private static <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException {
        try {
            return tcraft.Http.client().send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }

    private static byte[] readBytes(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) > 0) {
            total += n;
            if (total > limit) throw new IOException("response exceeds " + limit + " bytes");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /// A live WebSocket connection. Messages are delivered as complete text frames.
    public static final class WebSocketSession implements AutoCloseable, WebSocket.Listener {

        private final Consumer<String> onText;
        private final Consumer<Throwable> onError;
        private final Consumer<String> onClose;
        private final StringBuilder buffer = new StringBuilder();
        private volatile WebSocket socket;
        private volatile boolean closed;

        WebSocketSession(Consumer<String> onText, Consumer<Throwable> onError, Consumer<String> onClose) {
            this.onText = onText;
            this.onError = onError;
            this.onClose = onClose;
        }

        void attach(WebSocket socket) {
            this.socket = socket;
        }

        /// Sends a UTF-8 text frame and waits for the send operation to finish.
        public void send(String text) throws IOException {
            WebSocket ws = socket;
            if (ws == null || closed) throw new IOException("WebSocket is closed");
            try {
                ws.sendText(text == null ? "" : text, true).join();
            } catch (CompletionException e) {
                throw new IOException("WebSocket send failed", e.getCause());
            }
        }

        /// Indicates whether the connection can still send frames.
        public boolean isOpen() {
            return socket != null && !closed;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                // a plugin callback must not break the WebSocket reader thread
                if (onText != null) {
                    try {
                        onText.accept(message);
                    } catch (Throwable ignored) {
                    }
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (onError != null) {
                try {
                    onError.accept(error);
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            if (onClose != null) {
                try {
                    onClose.accept(statusCode + " " + reason);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        /// Sends a normal close frame and marks the session closed.
        @Override
        public void close() {
            WebSocket ws = socket;
            closed = true;
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "plugin closed").join();
                } catch (Exception ignored) {
                }
                try {
                    ws.abort();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /// A TCP connection with UTF-8 line and byte helpers.
    public static final class TcpConnection implements AutoCloseable {

        private final Socket socket;
        private final java.io.BufferedReader reader;
        private final java.io.BufferedWriter writer;

        TcpConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        /// Reads one line, or returns `null` when the peer closes the stream.
        public String readLine() throws IOException {
            return reader.readLine();
        }

        /// Reads up to `maxBytes` bytes and returns them as UTF-8 text.
        public String readAll(int maxBytes) throws IOException {
            return new String(readBytesToLimit(socket.getInputStream(), maxBytes), StandardCharsets.UTF_8);
        }

        /// Writes UTF-8 text and flushes immediately.
        public void write(String text) throws IOException {
            writer.write(text == null ? "" : text);
            writer.flush();
        }

        /// Writes one line followed by `\n`.
        public void writeLine(String text) throws IOException {
            writer.write(text == null ? "" : text);
            writer.newLine();
            writer.flush();
        }

        /// Sets the socket read timeout in milliseconds; `0` blocks forever.
        public void setReadTimeout(int timeoutMs) throws IOException {
            socket.setSoTimeout(Math.max(0, timeoutMs));
        }

        /// Indicates whether the socket is still connected.
        public boolean isOpen() {
            return socket.isConnected() && !socket.isClosed();
        }

        /// Closes the socket and its streams.
        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private static byte[] readBytesToLimit(InputStream in, int maxBytes) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > maxBytes) throw new IOException("TCP response exceeds " + maxBytes + " bytes");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    /// A UDP socket with a simple send/receive wrapper.
    public static final class UdpSocket implements AutoCloseable {

        private final DatagramSocket socket;

        UdpSocket(DatagramSocket socket) {
            this.socket = socket;
        }

        /// Sends UTF-8 text to a host and port.
        public void send(String host, int port, String text) throws IOException {
            byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(host), port);
            socket.send(packet);
        }

        /// Waits for one datagram; returns `null` when the timeout expires.
        public Packet receive(int timeoutMs) throws IOException {
            socket.setSoTimeout(Math.max(1, timeoutMs));
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (java.net.SocketTimeoutException e) {
                return null;
            }
            String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
            return new Packet(packet.getAddress().getHostAddress(), packet.getPort(), text);
        }

        /// Returns the local port currently bound to this socket.
        public int localPort() {
            return socket.getLocalPort();
        }

        /// Closes the UDP socket.
        @Override
        public void close() {
            socket.close();
        }
    }

    /// One received UDP datagram.
    public static final class Packet {

        public final String host;
        public final int port;
        public final String text;

        Packet(String host, int port, String text) {
            this.host = host;
            this.port = port;
            this.text = text;
        }
    }
}
