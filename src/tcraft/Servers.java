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

/** servers.dat 的 NBT 读写和 SLP 状态查询。 */
public final class Servers {

    public static class Entry {
        public String name = "";
        public String ip = "";
        public String icon = "";
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

    /** 读 servers.dat，自动识别 gzip。 */
    public static List<Entry> listRead(Path indie) throws Exception {
        List<Entry> result = new ArrayList<>();
        Path file = indie.resolve("servers.dat");
        if (!Files.isRegularFile(file)) return result;
        try (InputStream raw = new java.io.BufferedInputStream(Files.newInputStream(file))) {
            // 先看魔数判断 gzip
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
                e.name = str(tag.get("name"));
                e.ip = str(tag.get("ip"));
                e.icon = str(tag.get("icon"));
                result.add(e);
            }
        }
        return result;
    }

    /** 写 servers.dat，gzip 压缩。 */
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
            if (s.icon != null && !s.icon.isEmpty()) {
                w.writeByte(8);
                w.writeString("icon");
                w.writeString(s.icon);
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

    /** 最小 NBT 读取器。 */
    private static final class NbtReader {
        private final InputStream in;
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
            return (short) (((in.read() & 0xff) << 8) | (in.read() & 0xff));
        }

        int readInt() throws java.io.IOException {
            return (in.read() & 0xff) << 24 | (in.read() & 0xff) << 16 | (in.read() & 0xff) << 8 | (in.read() & 0xff);
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

        /** 读指定类型的负载，不含类型和名称。 */
        Object readPayload(int type) throws Exception {
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
                    if (len < 0) throw new java.io.EOFException("NBT 字节数组长度异常");
                    return in.readNBytes(len);
                }
                case 8 -> {
                    return readString();
                }
                case 9 -> {
                    int elemType = readByte();
                    int count = readInt();
                    List<Object> out = new ArrayList<>();
                    for (int i = 0; i < count; i++) out.add(readPayload(elemType));
                    return out;
                }
                case 10 -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    while (true) {
                        int t = readByte();
                        if (t == 0) break;
                        String name = readString();
                        map.put(name, readPayload(t));
                    }
                    return map;
                }
                case 11 -> {
                    int len = readInt();
                    int[] arr = new int[len];
                    for (int i = 0; i < len; i++) arr[i] = readInt();
                    return arr;
                }
                case 12 -> {
                    int len = readInt();
                    long[] arr = new long[len];
                    for (int i = 0; i < len; i++) arr[i] = readLong();
                    return arr;
                }
                default -> throw new Exception("未知的 NBT 标签类型：" + type);
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
        // 长度异常直接失败
        if (length > 1048576) throw new java.io.IOException("服务器返回的字符串长度异常：" + length);
        return new String(s.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static void sendPacket(OutputStream s, byte[] payload) throws java.io.IOException {
        writeVarInt(s, payload.length);
        s.write(payload);
        s.flush();
    }

    /** 提取 MOTD，递归拼 extra。 */
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

    /** 查询服务器状态，没端口时先查 SRV。 */
    public static PingResult ping(String address, int timeout) {
        PingResult result = new PingResult();
        String host = address.trim();
        int port = 25565;
        if (host.contains(":") && !host.contains("]")) {
            String[] parts = host.split(":");
            if (parts.length == 2) {
                try {
                    port = Integer.parseInt(parts[1]);
                    host = parts[0];
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (host.contains("]") && host.contains("[:")) {
            int i = host.indexOf("[:");
            try {
                port = Integer.parseInt(host.substring(i + 2, host.length() - 1));
                host = host.substring(0, i);
            } catch (Exception ignored) {
            }
        } else if (host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        // 没端口时查 _minecraft._tcp
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
            int packetId = readVarInt(stream);
            if (packetId != 0) throw new Exception("意外的状态响应包 ID：" + packetId);
            String json = readMcString(stream);
            result.latency = (int) ((System.nanoTime() - start) / 1_000_000);
            Map<String, Object> root = Json.obj(Json.parse(json));
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

    /** 查 SRV 记录，失败返回 null 表示直连。 */
    private static String[] srvResolve(String host) {
        java.net.HttpURLConnection c = null;
        try {
            String name = java.net.URLEncoder.encode("_minecraft._tcp." + host, StandardCharsets.UTF_8);
            String url = "https://dns.google/resolve?name=" + name + "&type=SRV";
            c = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            int code = c.getResponseCode();
            if (code != 200) return null;
            String body = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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

    /** 连接失败转成中文短语。 */
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
