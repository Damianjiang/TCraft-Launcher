package tcraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 解析器，不引第三方依赖。 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object o = p.value();
        p.ws();
        return o;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    public static Map<String, Object> obj(Object o, String key) {
        return obj(obj(o).get(key));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static List<Object> arr(Object o, String key) {
        return arr(obj(o).get(key));
    }

    public static String str(Object o, String key, String def) {
        Object v = obj(o).get(key);
        return v == null ? def : String.valueOf(v);
    }

    /** 序列化成 JSON 字符串字面量。 */
    public static String quote(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    public static double num(Object o, String key, double def) {
        Object v = obj(o).get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public static boolean bool(Object o, String key, boolean def) {
        Object v = obj(o).get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    private Object value() {
        ws(); // 先跳空白，否则空格会被当数字
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                i += 4;
                return Boolean.TRUE;
            case 'f':
                i += 5;
                return Boolean.FALSE;
            case 'n':
                i += 4;
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;
        ws();
        if (peek() == '}') {
            i++;
            return map;
        }
        while (i < s.length()) {
            ws();
            String key = string();
            ws();
            if (peek() == ':') i++;
            ws();
            map.put(key, value());
            ws();
            char c = peek();
            i++;
            if (c == '}') break;
        }
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (i < s.length()) {
            list.add(value());
            ws();
            char c = peek();
            i++;
            if (c == ']') break;
        }
        return list;
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        if (peek() == '"') i++;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                // 畸形 JSON 不能抛越界异常
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 <= s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                            } catch (NumberFormatException ex) {
                                sb.append("\\u");
                            }
                        } else {
                            sb.append("\\u");
                        }
                    }
                    default -> sb.append(e);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(start, i);
        try {
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            return Long.parseLong(n);
        } catch (Exception e) {
            return 0L;
        }
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : 0;
    }
}
