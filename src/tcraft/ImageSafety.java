package tcraft;

// 解码前预检尺寸 小字节流也能解出大位图把内存打满
final class ImageSafety {

    private static final int MAX_DIM = 8192;
    private static final long MAX_PIXELS = 32L * 1024 * 1024;

    private ImageSafety() {
    }

    static boolean tooLarge(byte[] data) {
        long[] size = sizeOf(data);
        if (size == null) return false;
        // 宽高伪造为 0xFFFFFFFF 时不能靠 int 溢出绕过
        if (size[0] <= 0 || size[1] <= 0) return true;
        long pixels = size[0] * size[1];
        return size[0] > MAX_DIM || size[1] > MAX_DIM || pixels > MAX_PIXELS;
    }

    static long[] sizeOf(byte[] b) {
        if (b == null || b.length < 16) return null;
        try {
            if (b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
                return new long[]{u32(b, 16), u32(b, 20)};
            }
            if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
                return new long[]{le16(b, 6), le16(b, 8)};
            }
            if (b[0] == 'B' && b[1] == 'M' && b.length >= 26) {
                long w = u32(b, 18);
                long h = u32(b, 22);
                if (h > Integer.MAX_VALUE) h = 0x100000000L - h;
                return new long[]{w, h};
            }
            if (b[0] == (byte) 0xFF && b[1] == (byte) 0xD8) return jpegSize(b);
            if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return webpSize(b);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static long[] jpegSize(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            while (i < b.length && b[i] != (byte) 0xFF) i++;
            while (i < b.length && b[i] == (byte) 0xFF) i++;
            if (i >= b.length) return null;
            int marker = b[i++] & 0xFF;
            if (marker == 0xD8 || marker == 0xD9 || marker == 0xDA) return null;
            if (i + 2 > b.length) return null;
            int len = be16(b, i);
            if (len < 2 || i + len > b.length) return null;
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (i + 7 >= b.length) return null;
                return new long[]{be16(b, i + 5), be16(b, i + 3)};
            }
            i += len;
        }
        return null;
    }

    private static long[] webpSize(byte[] b) {
        if (b.length < 30) return null;
        String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunk)) {
            return new long[]{le24(b, 24) + 1L, le24(b, 27) + 1L};
        }
        if ("VP8L".equals(chunk) && b[20] == 0x2F) {
            long bits = (b[21] & 0xFF) | ((long) (b[22] & 0xFF) << 8)
                    | ((long) (b[23] & 0xFF) << 16) | ((long) (b[24] & 0xFF) << 24);
            return new long[]{(bits & 0x3FFF) + 1L, ((bits >> 14) & 0x3FFF) + 1L};
        }
        if ("VP8 ".equals(chunk)) {
            return new long[]{le16(b, 26) & 0x3FFF, le16(b, 28) & 0x3FFF};
        }
        return null;
    }

    private static long u32(byte[] b, int i) {
        return Integer.toUnsignedLong(be32(b, i));
    }

    private static int be16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static int be32(byte[] b, int i) {
        return ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16)
                | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    private static int le16(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
    }

    private static int le24(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) | ((b[i + 2] & 0xFF) << 16);
    }
}
