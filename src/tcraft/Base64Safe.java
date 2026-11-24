package tcraft;

public final class Base64Safe {

    private Base64Safe() {
    }

    // 标准字母表 允许省略结尾填充
    public static byte[] decode(String text) {
        return decode(text, '+', '/');
    }

    // URL 安全字母表 用于 OAuth state
    public static byte[] decodeUrl(String text) {
        return decode(text, '-', '_');
    }

    // 自实现解码 避开 JDK 在 AVX-512 平台上的 Base64 缺陷
    private static byte[] decode(String text, char c62, char c63) {
        if (text == null) throw new IllegalArgumentException("输入为空");
        int length = text.length();
        int digits = length;
        if (length > 0 && text.charAt(length - 1) == '=') {
            if ((length & 3) != 0) throw new IllegalArgumentException("填充不合法");
            digits -= length >= 2 && text.charAt(length - 2) == '=' ? 2 : 1;
        }
        int tail = digits % 4;
        if (tail == 1) throw new IllegalArgumentException("长度不合法");
        byte[] out = new byte[digits / 4 * 3 + (tail == 0 ? 0 : tail - 1)];
        int bits = 0;
        int buffer = 0;
        int offset = 0;
        for (int i = 0; i < digits; i++) {
            buffer = (buffer << 6) | digit(text.charAt(i), c62, c63);
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[offset++] = (byte) ((buffer >> bits) & 0xFF);
            }
        }
        return out;
    }

    private static int digit(char c, char c62, char c63) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == c62) return 62;
        if (c == c63) return 63;
        throw new IllegalArgumentException("字符不合法");
    }
}
