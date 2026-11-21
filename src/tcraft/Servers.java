package tcraft;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Servers {

    public static class Entry {
        public String name = "";
        public String ip = "";
        public String icon = "";
        // listRead 捕获的额外 NBT 字段，listWrite 原样写回
        public java.util.Map<String, Object> extra;
    }

    public static class PingResult {
        public boolean online = false;
        public int players = 0;
        public int maxPlayers = 0;
        public String motd = "";
        public String version = "";
        public int latency = -1;
        public String errorMessage = "";
    }

    private Servers() {
    }

    public static List<Entry> listRead(Path indie) throws Exception {
        List<Entry> result = new ArrayList<>();
        Path file = indie.resolve("servers.dat");
        if (!Files.isRegularFile(file)) return result;
        try (InputStream raw = new java.io.BufferedInputStream(Files.newInputStream(file))) {
            raw.mark(2);
            byte[] magic = raw.readNBytes(2);
            raw.reset();
            boolean gzip = magic.length == 2 && magic[0] == 0x1f && magic[1] == (byte) 0x8b;
            InputStream in = gzip ? new GZIPInputStream(raw) : raw;
            NbtReader reader = new NbtReader(in);
            int rootType = reader.readByte();
            if (rootType != 10) throw new Exception("servers.dat 的根标签不是 Compound");
            reader.readString();
            Object payload = reader.readPayload(10);
            Object servers = payload instanceof Map<?, ?> root ? root.get("servers") : null;
            if (!(servers instanceof List<?> list)) return result;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> tag)) continue;
                Entry e = new Entry();
                Map<String, Object> extra = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> f : tag.entrySet()) {
                    String key = String.valueOf(f.getKey());
                    Object value = f.getValue();
                    if ("name".equals(key)) {
                        e.name = str(value);
                    } else if ("ip".equals(key)) {
                        e.ip = str(value);
                    } else if ("icon".equals(key)) {
                        // icon 是 PNG 字节数组：base64 给界面，原样写回
                        if (value instanceof byte[] bytes) {
                            e.icon = java.util.Base64.getEncoder().encodeToString(bytes);
                            extra.put(key, value);
                        } else {
                            e.icon = str(value);
                        }
                    } else {
                        // hidden 等标记须保留，防游戏端设置被抹
                        extra.put(key, value);
                    }
                }
                e.extra = extra.isEmpty() ? null : extra;
                result.add(e);
            }
        }
        return result;
    }

    public static void listWrite(Path indie, List<Entry> servers) throws Exception {
        Path file = indie.resolve("servers.dat");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        NbtWriter w = new NbtWriter(buf);
        w.writeByte(10);
        w.writeString("");
        w.writeByte(9);
        w.writeString("servers");
        w.writeByte(10);
        w.writeInt(servers == null ? 0 : servers.size());
        for (Entry s : servers) {
            w.writeByte(8);
            w.writeString("name");
            w.writeString(s.name);
            w.writeByte(8);
            w.writeString("ip");
            w.writeString(s.ip);
            if (s.extra != null && s.extra.get("icon") instanceof byte[] bytes) {
                // MC 只认 ByteArray 类型的 icon，从文件里读出来的按原类型写回
                w.writeByte(7);
                w.writeString("icon");
                w.writeByteArray(bytes);
            } else if (s.icon != null && !s.icon.isEmpty()) {
                w.writeByte(8);
                w.writeString("icon");
                w.writeString(s.icon);
            }
            // 未知字段按捕获顺序写回，条目数设上限防调用方塞垃圾
            if (s.extra != null && s.extra.size() <= 64) {
                for (Map.Entry<String, Object> f : s.extra.entrySet()) {
                    if ("icon".equals(f.getKey())) continue;
                    int type = NbtWriter.typeOf(f.getValue());
                    if (type < 0) continue;
                    w.writeByte(type);
                    w.writeString(f.getKey());
                    w.writePayload(f.getValue());
                }
            }
            w.writeByte(0);
        }
        w.writeByte(0);
        try (OutputStream fs = new GZIPOutputStream(Files.newOutputStream(file))) {
            fs.write(buf.toByteArray());
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static final class NbtReader {
        private final InputStream in;
        private final byte[] len2 = new byte[2];
        private final byte[] len4 = new byte[4];
        private final byte[] len8 = new byte[8];

        NbtReader(InputStream in) {
            this.in = in;
        }

        int readByte() throws java.io.IOException {
            int b = in.read();
            if (b < 0) throw new java.io.EOFException("NBT 读取越界");
            return b;
        }

        short readShort() throws java.io.IOException {
            int n = in.readNBytes(len2, 0, 2);
            if (n < 2) throw new java.io.EOFException("NBT 读取越界");
            return (short) (((len2[0] & 0xff) << 8) | (len2[1] & 0xff));
        }

        int readInt() throws java.io.IOException {
            int n = in.readNBytes(len4, 0, 4);
            if (n < 4) throw new java.io.EOFException("NBT 读取越界");
            return (len4[0] & 0xff) << 24 | (len4[1] & 0xff) << 16 | (len4[2] & 0xff) << 8 | (len4[3] & 0xff);
        }

        long readLong() throws java.io.IOException {
            int n = in.readNBytes(len8, 0, 8);
            if (n < 8) throw new java.io.EOFException("NBT 读取越界");
            long v = 0;
            for (byte b : len8) v = (v << 8) | (b & 0xff);
            return v;
        }

        String readString() throws java.io.IOException {
            int len = readShort() & 0xffff;
            if (len <= 0) return "";
            byte[] buf = in.readNBytes(len);
            return new String(buf, StandardCharsets.UTF_8);
        }

        void skipBytes(long count) throws java.io.IOException {
            long skipped = 0;
            while (skipped < count) {
                long n = in.skip(count - skipped);
                if (n <= 0) {
                    if (in.read() < 0) throw new java.io.EOFException("NBT 读取越界");
                    n = 1;
                }
                skipped += n;
            }
        }

        Object readPayload(int type) throws Exception {
            return readPayload(type, 0);
        }

        Object readPayload(int type, int depth) throws Exception {
            // servers.dat 不该深嵌套，超限多为构造文件；消息片段各自 Lang.t
            if (depth > 128) throw new Exception(Lang.t("NBT 嵌套过深"));
            switch (type) {
                case 1 -> {
                    return (byte) readByte();
                }
                case 2 -> {
                    return readShort();
                }
                case 3 -> {
                    return readInt();
                }
                case 4 -> {
                    return readLong();
                }
                case 5 -> {
                    return Float.intBitsToFloat(readInt());
                }
                case 6 -> {
                    return Double.longBitsToDouble(readLong());
                }
                case 7 -> {
                    int len = readInt();
                    if (len < 0 || len > 1024 * 1024) throw new java.io.EOFException(Lang.t("NBT 字节数组长度异常：") + len);
                    return in.readNBytes(len);
                }
                case 8 -> {
                    return readString();
                }
                case 9 -> {
                    int elemType = readByte();
                    int count = readInt();
                    List<Object> out = new ArrayList<>();
                    for (int i = 0; i < count; i++) out.add(readPayload(elemType, depth + 1));
                    return out;
                }
                case 10 -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    while (true) {
                        int t = readByte();
                        if (t == 0) break;
                        String name = readString();
                        map.put(name, readPayload(t, depth + 1));
                    }
                    return map;
                }
                case 11 -> {
                    int len = readInt();
                    if (len < 0 || len > 1048576) throw new java.io.EOFException(Lang.t("NBT 数组长度异常：") + len);
                    int[] arr = new int[len];
                    for (int i = 0; i < len; i++) arr[i] = readInt();
                    return arr;
                }
                case 12 -> {
                    int len = readInt();
                    if (len < 0 || len > 1048576) throw new java.io.EOFException(Lang.t("NBT 数组长度异常：") + len);
                    long[] arr = new long[len];
                    for (int i = 0; i < len; i++) arr[i] = readLong();
                    return arr;
                }
                default -> throw new Exception(Lang.t("未知的 NBT 标签类型：") + type);
            }
        }
    }

    private static final class NbtWriter {
        private final OutputStream s;

        NbtWriter(OutputStream s) {
            this.s = s;
        }

        void writeByte(int v) throws java.io.IOException {
            s.write(v & 0xff);
        }

        void writeShort(int v) throws java.io.IOException {
            s.write((v >> 8) & 0xff);
            s.write(v & 0xff);
        }

        void writeInt(int v) throws java.io.IOException {
            s.write((v >> 24) & 0xff);
            s.write((v >> 16) & 0xff);
            s.write((v >> 8) & 0xff);
            s.write(v & 0xff);
        }

        void writeString(String value) throws java.io.IOException {
            byte[] buf = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            writeShort(buf.length);
            s.write(buf);
        }

        void writeLong(long v) throws java.io.IOException {
            writeInt((int) (v >> 32));
            writeInt((int) v);
        }

        void writeByteArray(byte[] b) throws java.io.IOException {
            writeInt(b.length);
            s.write(b);
        }

        // 一种 NBT 类型只映射一种 Java 类型，才能写回
        static int typeOf(Object v) {
            if (v instanceof Byte) return 1;
            if (v instanceof Short) return 2;
            if (v instanceof Integer) return 3;
            if (v instanceof Long) return 4;
            if (v instanceof Float) return 5;
            if (v instanceof Double) return 6;
            if (v instanceof byte[]) return 7;
            if (v instanceof String) return 8;
            if (v instanceof List) return 9;
            if (v instanceof Map) return 10;
            if (v instanceof int[]) return 11;
            if (v instanceof long[]) return 12;
            return -1;
        }

        // 写无名字段的负载（列表元素 / compound 内字段）
        void writePayload(Object v) throws java.io.IOException {
            if (v instanceof Byte b) writeByte(b);
            else if (v instanceof Short sh) writeShort(sh);
            else if (v instanceof Integer i) writeInt(i);
            else if (v instanceof Long l) writeLong(l);
            else if (v instanceof Float f) writeInt(Float.floatToIntBits(f));
            else if (v instanceof Double d) writeLong(Double.doubleToLongBits(d));
            else if (v instanceof byte[] b) writeByteArray(b);
            else if (v instanceof String str) writeString(str);
            else if (v instanceof List<?> list) {
                int elemType = 0;
                for (Object e : list) {
                    elemType = typeOf(e);
                    break;
                }
                writeByte(elemType);
                writeInt(list.size());
                for (Object e : list) writePayload(e);
            } else if (v instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    int type = typeOf(e.getValue());
                    if (type < 0) continue;
                    writeByte(type);
                    writeString(String.valueOf(e.getKey()));
                    writePayload(e.getValue());
                }
                writeByte(0);
            } else if (v instanceof int[] a) {
                writeInt(a.length);
                for (int x : a) writeInt(x);
            } else if (v instanceof long[] a) {
                writeInt(a.length);
                for (long x : a) writeLong(x);
            }
        }
    }

    private static void writeVarInt(OutputStream s, int value) throws java.io.IOException {
        int unsigned = value;
        while (true) {
            int temp = unsigned & 0x7f;
            unsigned >>>= 7;
            if (unsigned != 0) temp |= 0x80;
            s.write(temp);
            if (unsigned == 0) break;
        }
    }

    private static int readVarInt(InputStream s) throws java.io.IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            int current = s.read();
            if (current < 0) throw new java.io.EOFException("读取 VarInt 失败");
            result |= (current & 0x7f) << shift;
            shift += 7;
            if ((current & 0x80) == 0) break;
            if (shift > 35) throw new java.io.IOException("VarInt 过长");
        }
        return result;
    }

    private static void writeMcString(OutputStream s, String value) throws java.io.IOException {
        byte[] buf = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(s, buf.length);
        s.write(buf);
    }

    private static String readMcString(InputStream s) throws java.io.IOException {
        int length = readVarInt(s);
        if (length <= 0) return "";
        if (length > 1048576) throw new java.io.IOException("服务器返回的字符串长度异常：" + length);
        return new String(s.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static void sendPacket(OutputStream s, byte[] payload) throws java.io.IOException {
        writeVarInt(s, payload.length);
        s.write(payload);
        s.flush();
    }

    private static String motdFromJson(Object desc) {
        if (desc == null) return "";
        if (desc instanceof String s) return s;
        if (desc instanceof Map<?, ?> m) {
            String text = m.get("text") == null ? "" : String.valueOf(m.get("text"));
            if (m.get("extra") instanceof List<?> extra) {
                for (Object part : extra) text += motdFromJson(part);
            }
            return text;
        }
        return String.valueOf(desc);
    }

    // [v6]:port、[v6]、v4:port、裸 v6 统一拆开；port 为空串表示默认端口
    public static String[] splitAddress(String address) {
        String host = address == null ? "" : address.trim();
        String port = "";
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            if (close > 0) {
                String rest = host.substring(close + 1);
                host = host.substring(1, close);
                if (rest.startsWith(":")) port = rest.substring(1);
            }
        } else if (host.indexOf(':') == host.lastIndexOf(':')) {
            String[] parts = host.split(":");
            if (parts.length == 2) {
                port = parts[1];
                host = parts[0];
            }
        }
        return new String[]{host, port};
    }

    public static PingResult ping(String address, int timeout) {
        PingResult result = new PingResult();
        String[] hp = splitAddress(address);
        String host = hp[0];
        int port = 25565;
        try {
            if (!hp[1].isEmpty()) port = Integer.parseInt(hp[1]);
        } catch (NumberFormatException ignored) {
        }
        if (port <= 0 || port > 65535) port = 25565;
        if (port == 25565 && !host.matches("^(\\d{1,3}\\.){3}\\d{1,3}$") && !host.startsWith("localhost")) {
            String[] srv = srvResolve(host);
            if (srv != null) {
                try {
                    port = Integer.parseInt(srv[0]);
                    host = srv[1];
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Socket client = new Socket();
        try {
            client.connect(new InetSocketAddress(host, port), timeout);
            client.setSoTimeout(timeout);
            long start = System.nanoTime();
            java.io.ByteArrayOutputStream handshake = new java.io.ByteArrayOutputStream();
            writeVarInt(handshake, 0);
            writeVarInt(handshake, 47);
            writeMcString(handshake, host);
            handshake.write((port >> 8) & 0xff);
            handshake.write(port & 0xff);
            writeVarInt(handshake, 1);
            sendPacket(client.getOutputStream(), handshake.toByteArray());
            sendPacket(client.getOutputStream(), new byte[]{0});
            InputStream stream = client.getInputStream();
            int packetLength = readVarInt(stream);
            // 能解析的响应包长不超过 1MB+6，超过这个数的一定是垃圾数据
            if (packetLength <= 0 || packetLength > 2 * 1024 * 1024) {
                throw new Exception(Lang.t("意外的状态响应包长度：") + packetLength);
            }
            int packetId = readVarInt(stream);
            if (packetId != 0) throw new Exception(Lang.t("意外的状态响应包 ID：") + packetId);
            String json = readMcString(stream);
            result.latency = (int) ((System.nanoTime() - start) / 1_000_000);
            Map<String, Object> root;
            try {
                root = Json.obj(Json.parse(json));
            } catch (RuntimeException e) {
                result.errorMessage = Lang.t("服务器状态解析失败：") + (e.getMessage() == null || e.getMessage().isBlank()
                        ? Lang.t("响应不是有效的 JSON") : e.getMessage());
                return result;
            }
            if (root.isEmpty()) {
                result.errorMessage = Lang.t("服务器状态响应格式异常");
                return result;
            }
            result.online = true;
            result.version = Json.str(Json.obj(root.get("version")), "name", "");
            Map<String, Object> players = Json.obj(root.get("players"));
            result.players = (int) Json.num(players, "online", 0);
            result.maxPlayers = (int) Json.num(players, "max", 0);
            result.motd = motdFromJson(root.get("description")).trim();
            if (result.motd.length() > 40) result.motd = result.motd.substring(0, 40) + "…";
        } catch (Exception e) {
            result.errorMessage = friendly(e);
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    // SRV 结果缓存 10 分钟（含失败）
    private static final Map<String, Object[]> SRV_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
    private static final long SRV_TTL_MS = 600_000L;

    private static String[] srvResolve(String host) {
        Object[] cached = SRV_CACHE.get(host);
        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < SRV_TTL_MS) {
            return (String[]) cached[1];
        }
        String[] result = srvResolveRemote(host);
        SRV_CACHE.put(host, new Object[]{System.currentTimeMillis(), result});
        return result;
    }

    private static String[] srvResolveRemote(String host) {
        java.net.HttpURLConnection c = null;
        try {
            String name = java.net.URLEncoder.encode("_minecraft._tcp." + host, StandardCharsets.UTF_8);
            String url = "https://dns.google/resolve?name=" + name + "&type=SRV";
            c = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            int code = c.getResponseCode();
            if (code != 200) return null;
            String body;
            try (java.io.InputStream in = c.getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            java.util.Map<String, Object> root = Json.obj(Json.parse(body));
            if (!(root.get("Answer") instanceof List<?> answers) || answers.isEmpty()) return null;
            for (Object o : answers) {
                java.util.Map<String, Object> ans = Json.obj(o);
                if ((int) Json.num(ans, "type", 0) != 33) continue;
                String[] seg = Json.str(ans, "data", "").trim().split("\\s+");
                if (seg.length >= 4) {
                    String target = seg[3].endsWith(".") ? seg[3].substring(0, seg[3].length() - 1) : seg[3];
                    return new String[]{seg[2], target};
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String friendly(Exception e) {
        if (e instanceof java.net.ConnectException ce) {
            return "连接被拒绝";
        }
        if (e instanceof java.net.UnknownHostException) {
            return "无法解析服务器地址";
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return "连接超时";
        }
        if (e instanceof java.net.NoRouteToHostException || e instanceof java.net.SocketException se
                && String.valueOf(se.getMessage()).contains("unreachable")) {
            return "网络不可达";
        }
        if (e instanceof java.io.IOException && String.valueOf(e.getMessage()).contains("reset")) {
            return "连接被重置";
        }
        if (e instanceof java.io.IOException) {
            return "服务器没有按预期响应";
        }
        return e.getMessage() == null ? "未知错误" : e.getMessage();
    }
}
