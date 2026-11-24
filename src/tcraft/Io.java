package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// 有界读取 防压缩包单条目撑爆内存或磁盘
final class Io {

    private Io() {
    }

    static byte[] read(InputStream in, long max) throws IOException {
        if (max <= 0 || max >= Integer.MAX_VALUE - 8) throw new IOException("读取上限无效");
        byte[] data = in.readNBytes((int) max + 1);
        if (data.length > max) throw new IOException("内容超过读取上限 " + max + " 字节");
        return data;
    }

    static long copy(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) >= 0) {
            if (n == 0) continue;
            if (total > max - n) throw new IOException("内容超过写入上限 " + max + " 字节");
            out.write(buffer, 0, n);
            total += n;
        }
        return total;
    }
}
