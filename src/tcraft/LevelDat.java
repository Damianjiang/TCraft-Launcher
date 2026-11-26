package tcraft;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class LevelDat {

    private LevelDat() {
    }

    // 世界详情用 只读根复合标签
    public static Map<String, Object> read(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) throw new IOException(Lang.t("找不到 level.dat"));
        if (Files.size(file) > 64L * 1024 * 1024) throw new IOException(Lang.t("level.dat 过大"));
        try (InputStream raw = Files.newInputStream(file);
             InputStream gz = new GZIPInputStream(new BufferedInputStream(raw));
             DataInputStream in = new DataInputStream(gz)) {
            byte type = in.readByte();
            if (type != 10) throw new IOException(Lang.t("不是 NBT 复合标签"));
            // text/data budgets bound the decompressed size, not just the file size
            readName(in, new long[]{16L * 1024 * 1024});
            Object root = readPayload(in, type, 0, new int[]{0}, new long[]{16L * 1024 * 1024},
                    new long[]{64L * 1024 * 1024});
            if (root instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> value = (Map<String, Object>) map;
                return value;
            }
            throw new IOException(Lang.t("level.dat 结构无效"));
        } catch (java.io.EOFException e) {
            // a truncated gzip stream must not be reported as a valid world
            throw new IOException(Lang.t("level.dat 结构无效"));
        }
    }

    public static String text(Map<String, Object> data, String key, String def) {
        Object value = data.get(key);
        return value == null ? def : String.valueOf(value);
    }

    // level.dat 的根下是 Data 复合标签
    @SuppressWarnings("unchecked")
    public static Map<String, Object> data(Map<String, Object> root) {
        Object value = root.get("Data");
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : root;
    }

    public static long number(Map<String, Object> data, String key, long def) {
        Object value = data.get(key);
        if (value instanceof Number n) return n.longValue();
        return def;
    }

    private static String readName(DataInputStream in, long[] textBudget) throws IOException {
        int length = in.readShort() & 0xFFFF;
        if (length > 1024) throw new IOException(Lang.t("标签名过长"));
        textBudget[0] -= length;
        if (textBudget[0] < 0) throw new IOException(Lang.t("NBT 文本过多"));
        return new String(readBytes(in, length), StandardCharsets.UTF_8);
    }

    // readNBytes returns fewer bytes on EOF, which would silently accept a truncated file
    private static byte[] readBytes(DataInputStream in, int length) throws IOException {
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException(Lang.t("level.dat 结构无效"));
        return bytes;
    }

    private static void charge(long[] budget, long amount, String message) throws IOException {
        budget[0] -= amount;
        if (budget[0] < 0) throw new IOException(message);
    }

    private static Object readPayload(DataInputStream in, byte type, int depth, int[] nodes,
                                      long[] textBudget, long[] dataBudget) throws IOException {
        if (++nodes[0] > 1_000_000) throw new IOException(Lang.t("NBT 节点过多"));
        if (depth > 12) throw new IOException(Lang.t("NBT 嵌套过深"));
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                int n = in.readInt();
                if (n < 0 || n > 8 * 1024 * 1024) throw new IOException(Lang.t("字节数组过大"));
                charge(dataBudget, n, Lang.t("NBT 数据过多"));
                return readBytes(in, n);
            }
            case 8: {
                int n = in.readShort() & 0xFFFF;
                textBudget[0] -= n;
                if (textBudget[0] < 0) throw new IOException(Lang.t("NBT 文本过多"));
                return new String(readBytes(in, n), StandardCharsets.UTF_8);
            }
            case 9: {
                byte elem = in.readByte();
                int n = in.readInt();
                if (n < 0 || n > 1_000_000) throw new IOException(Lang.t("列表过大"));
                List<Object> list = new ArrayList<>(Math.min(n, 1024));
                for (int i = 0; i < n; i++) list.add(readPayload(in, elem, depth + 1, nodes, textBudget, dataBudget));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    byte child = in.readByte();
                    if (child == 0) break;
                    if (map.size() >= 65536) throw new IOException(Lang.t("复合标签字段过多"));
                    String name = readName(in, textBudget);
                    map.put(name, readPayload(in, child, depth + 1, nodes, textBudget, dataBudget));
                }
                return map;
            }
            case 11: {
                int n = in.readInt();
                if (n < 0 || n > 4 * 1024 * 1024) throw new IOException(Lang.t("整型数组过大"));
                charge(dataBudget, 4L * n, Lang.t("NBT 数据过多"));
                for (int i = 0; i < n; i++) in.readInt();
                return null;
            }
            case 12: {
                int n = in.readInt();
                if (n < 0 || n > 4 * 1024 * 1024) throw new IOException(Lang.t("长整型数组过大"));
                charge(dataBudget, 8L * n, Lang.t("NBT 数据过多"));
                for (int i = 0; i < n; i++) in.readLong();
                return null;
            }
            default:
                throw new IOException(Lang.t("未知标签类型 ") + type);
        }
    }
}
