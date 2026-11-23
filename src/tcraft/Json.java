package tcraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    // 防恶意深嵌套
    private static final int MAX_DEPTH = 128;

    public static class MalformedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MalformedException(String message) {
            super(message);
        }
    }

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        if (text == null) throw new MalformedException("JSON 输入为空");
        Json p = new Json(text);
        p.ws();
        if (p.i >= p.s.length()) throw new MalformedException("JSON 输入为空");
        Object o = p.value(0);
        p.ws();
        if (p.i < p.s.length()) throw new MalformedException("JSON 结束后有多余字符 '" + p.s.charAt(p.i) + "'");
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

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw new MalformedException("JSON 嵌套超过 " + MAX_DEPTH + " 层");
        ws(); // 先跳空白 否则空格会被当数字
        if (i >= s.length()) throw new MalformedException("JSON 意外结束");
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;
        ws();
        if (peek() == '}') {
            i++;
            return map;
        }
        while (true) {
            ws();
            if (peek() != '"') throw new MalformedException("JSON 对象的键必须是字符串");
            String key = string();
            ws();
            if (peek() != ':') throw new MalformedException("JSON 对象缺少冒号");
            i++;
            map.put(key, value(depth + 1));
            ws();
            char c = peek();
            if (c == '}') {
                i++;
                return map;
            }
            if (c != ',') throw new MalformedException("JSON 对象缺少逗号");
            i++;
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (true) {
            if (i >= s.length()) throw new MalformedException("JSON 数组提前结束");
            list.add(value(depth + 1));
            ws();
            char c = peek();
            if (c == ']') {
                i++;
                return list;
            }
            if (c != ',') throw new MalformedException("JSON 数组缺少逗号");
            i++;
        }
    }

    private String string() {
        if (peek() != '"') throw new MalformedException("JSON 字符串应以引号开始");
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new MalformedException("JSON 字符串未闭合");
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                // 转义符后无内容按未闭合处理
                if (i >= s.length()) throw new MalformedException("JSON 字符串未闭合");
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
        } catch (NumberFormatException e) {
            throw new MalformedException(n.isEmpty()
                    ? "JSON 意外字符 '" + s.charAt(start) + "'"
                    : "JSON 数字格式错误：" + n);
        }
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw new MalformedException("JSON 字面量不是 " + word);
        i += word.length();
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : 0;
    }
}
