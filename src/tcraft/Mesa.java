package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Mesa {

    private static final String URL =
            "https://repo1.maven.org/maven2/org/glavo/mesa-loader-windows/26.2.6/"
                    + "mesa-loader-windows-26.2.6-x64.jar";
    private static final String SHA1 = "f3400d97cb312a9bf1b7c8430a8d0c2849383245";
    private static final String FILE = "mesa-loader-windows-26.2.6-x64.jar";

    private Mesa() {
    }

    public static String driverName(int renderer) {
        return switch (renderer) {
            case 1 -> "llvmpipe";
            case 2 -> "d3d12";
            case 3 -> "zink";
            default -> "";
        };
    }

    public static synchronized Path ensure() throws Exception {
        Path dir = Resources.home().resolve("dependencies").resolve("mesa");
        Path jar = dir.resolve(FILE);
        if (Files.isRegularFile(jar)) {
            // 半截文件不能当 javaagent
            try {
                if (Files.size(jar) > 0 && SHA1.equalsIgnoreCase(sha1(jar))) return jar;
            } catch (Exception ignored) {
            }
            Files.deleteIfExists(jar);
        }
        Files.createDirectories(dir);
        Path tmp = dir.resolve(FILE + ".tmp");
        Files.deleteIfExists(tmp);
        Downloader.downloadFileTo(null, URL, tmp, "mesa-loader");
        if (!SHA1.equalsIgnoreCase(sha1(tmp))) {
            Files.deleteIfExists(tmp);
            throw new IOException("渲染器组件下载校验失败，请重试");
        }
        Files.move(tmp, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    private static String sha1(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
