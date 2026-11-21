package tcraft;

import java.util.ArrayList;
import java.util.List;

// 加速镜像：路径与官方一致，默认关闭，选镜像后优先走镜像、失败回退官方
public final class Mirrors {

    public static final String PRESET = "https://mod.mcimirror.top";

    private Mirrors() {
    }

    private static String base() {
        try {
            int mode = Data.get().getInt("SystemMirrorMode", 0);
            if (mode == 1) return PRESET;
            if (mode != 2) return null;
            String custom = Data.get().get("SystemMirrorUrl", "");
            if (custom == null) return null;
            custom = custom.trim();
            while (custom.endsWith("/")) custom = custom.substring(0, custom.length() - 1);
            if (!custom.startsWith("http://") && !custom.startsWith("https://")) return null;
            return custom.isEmpty() ? null : custom;
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<String> apiCandidates(String url) {
        List<String> out = new ArrayList<>(2);
        String mirrored = api(url);
        if (mirrored != null && !mirrored.equals(url)) out.add(mirrored);
        if (url != null && !url.equals(mirrored)) out.add(url);
        return out;
    }

    public static String api(String url) {
        String base = base();
        if (base == null || url == null) return url;
        if (url.startsWith("https://api.modrinth.com")) {
            return base + "/modrinth" + url.substring("https://api.modrinth.com".length());
        }
        if (url.startsWith("https://api.curseforge.com")) {
            return base + "/curseforge" + url.substring("https://api.curseforge.com".length());
        }
        return url;
    }

    public static List<String> images(String url) {
        List<String> out = new ArrayList<>(2);
        String mirrored = image(url);
        if (mirrored != null && !mirrored.equals(url)) out.add(mirrored);
        if (url != null && !url.equals(mirrored)) out.add(url);
        return out;
    }

    private static String image(String url) {
        String base = base();
        if (base == null || url == null) return null;
        if (url.startsWith("https://cdn.modrinth.com")) {
            return base + url.substring("https://cdn.modrinth.com".length());
        }
        if (url.startsWith("https://edge.forgecdn.net")) {
            return base + url.substring("https://edge.forgecdn.net".length());
        }
        if (url.startsWith("https://media.forgecdn.net")) {
            return base + url.substring("https://media.forgecdn.net".length());
        }
        return null;
    }
}
