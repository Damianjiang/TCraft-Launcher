package tcraft;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// 皮肤落盘与校验
public final class Skins {

    private Skins() {
    }

    private static String safeName(String accountName) {
        String safe = accountName == null || accountName.isEmpty() ? "skin" : accountName;
        for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
        return safe;
    }

    public static String save(String accountName, byte[] png) throws IOException {
        int[] size = pngSize(png);
        if (size == null) throw new IOException("皮肤文件不是有效的 PNG 图片");
        int w = size[0], h = size[1];
        // 先读 IHDR 再落盘，超大 PNG 在解码前就拒掉
        if (w < 64 || w % 64 != 0 || (h != w && h != w / 2) || (long) w * h > 2048L * 2048L) {
            throw new IOException("皮肤图片尺寸无效（" + w + "×" + h + "，应为 64×32、64×64 或它们的整数倍）");
        }
        String safe = safeName(accountName);
        Path dir = Theme.skinDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(safe + ".png");
        // 先写临时文件再原子替换
        Path temp = dir.resolve(safe + ".png.tmp");
        try {
            Files.write(temp, png);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return "Skins/" + safe + ".png";
    }

    public static String download(String accountName, String url) throws IOException {
        return save(accountName, Http.getBytes(url));
    }

    // 只解析签名与 IHDR，不解码整图
    private static int[] pngSize(byte[] data) {
        if (data == null || data.length < 24) return null;
        if ((data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') return null;
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') return null;
        int w = readInt(data, 16);
        int h = readInt(data, 20);
        return w <= 0 || h <= 0 ? null : new int[]{w, h};
    }

    private static int readInt(byte[] d, int offset) {
        return ((d[offset] & 0xFF) << 24) | ((d[offset + 1] & 0xFF) << 16)
                | ((d[offset + 2] & 0xFF) << 8) | (d[offset + 3] & 0xFF);
    }
}
