package tcraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 各加载器的版本列表，来自官方元数据。 */
public final class Loaders {

    private Loaders() {
    }

    private static final Map<String, List<String>> CACHE = new LinkedHashMap<>();
    private static final int CACHE_MAX = 24;

    public static final String FORGE = "Forge";
    public static final String NEOFORGE = "NeoForge";
    public static final String FABRIC = "Fabric";
    public static final String OPTIFINE = "OptiFine";
    public static final String LITELOADER = "LiteLoader";
    public static final String LEGACYFABRIC = "LegacyFabric";
    public static final String FABRICAPI = "Fabric API";
    public static final String OPTIFABRIC = "OptiFabric";
    public static final String CLEANROOM = "Cleanroom";
    public static final String LABYMOD = "LabyMod";

    /** Cleanroom / LabyMod 的官方安装器地址。 */
    public static String installerUrl(String loader, String channel) {
        switch (loader) {
            case CLEANROOM:
                // Cleanroom 的安装器在 GitHub Releases
                return "https://github.com/CleanroomMC/Cleanroom/releases/download/" + channel + "/cleanroom-" + channel + "-installer.jar";
            case LABYMOD:
                // LabyMod 安装器地址
                return "https://releases.labymod.net/api/v1/installer/" + channel + "/java";
            default:
                return "";
        }
    }

    /** 安装器文件名。 */
    public static String installerName(String loader, String channel) {
        if (CLEANROOM.equals(loader)) return "cleanroom-" + channel + "-installer.jar";
        if (LABYMOD.equals(loader)) return "LabyMod4" + ("snapshot".equals(channel) ? "Snapshot" : "Production") + "Installer.jar";
        return "";
    }

    /** 某 MC 版本下可用的加载器版本，旧的在前。 */
    public static List<String> versions(String loader, String mc) throws Exception {
        String key = loader + "@" + mc;
        synchronized (CACHE) {
            List<String> hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        List<String> list;
        switch (loader) {
            case FORGE -> list = forge(mc);
            case NEOFORGE -> list = neoforge(mc);
            case FABRIC -> list = fabric(mc);
            case LITELOADER -> list = liteloader(mc);
            case LEGACYFABRIC -> list = legacyfabric(mc);
            case FABRICAPI -> list = fabricApi(mc);
            case OPTIFINE -> list = optifine(mc);
            case OPTIFABRIC -> list = optiFabric(mc);
            case CLEANROOM -> list = cleanroom(mc);
            case LABYMOD -> list = labyMod(mc);
            default -> list = new ArrayList<>();
        }
        synchronized (CACHE) {
            if (CACHE.size() >= CACHE_MAX) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(key, list);
        }
        return list;
    }

    /** 某加载器在指定 MC 版本下有没有可用版本。 */
    public static boolean available(String loader, String mc) {
        try {
            return !versions(loader, mc).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> liteloader(String mc) throws Exception {
        String json = get("https://dl.liteloader.com/versions/versions.json");
        Map<String, Object> versions = Json.obj(Json.parse(json), "versions");
        // 每个 MC 版本只有一条 LiteLoader。
        Map<String, Object> entry = Json.obj(versions.get(mc));
        Object node = firstValue(Json.obj(entry, "artefacts"), Json.obj(entry, "snapshots"), "com.mumfrey:liteloader");
        if (node == null) throw new Exception("LiteLoader 没有适用于 Minecraft " + mc + " 的版本");
        List<String> out = new ArrayList<>();
        out.add(mc);
        return out;
    }

    /** 从 artefacts 或 snapshots 取节点。 */
    private static Object firstValue(Map<String, Object> a, Map<String, Object> b, String key) {
        return a.containsKey(key) ? a.get(key) : b.get(key);
    }

    private static List<String> legacyfabric(String mc) throws Exception {
        String json = get("https://meta.legacyfabric.net/v2/versions");
        Map<String, Object> root = Json.obj(Json.parse(json));
        List<String> out = new ArrayList<>();
        for (Object item : Json.arr(root, "loader")) {
            Map<String, Object> obj = Json.obj(item);
            if (mc.equals(Json.str(obj, "game", ""))) {
                String v = Json.str(obj, "version", "");
                if (!v.isEmpty()) out.add(v);
            }
        }
        // 没有适用版本就返回空列表，网络失败才抛异常。
        return out;
    }

    private static List<String> optifine(String mc) throws Exception {
        String json = get("https://bmclapi2.bangbang93.com/optifine/versionList");
        List<Object> arr = Json.arr(Json.parse(json));
        List<String> out = new ArrayList<>();
        for (Object o : arr) {
            Map<String, Object> m = Json.obj(o);
            // 显示名去掉 HD_U 和末尾的 .0
            String mcversion = Json.str(m, "mcversion", "");
            if (!mcversion.equals(mc)) continue;
            String type = Json.str(m, "type", "").replace("HD_U", "").replace("_", " ");
            String patch = Json.str(m, "patch", "");
            String display = (mcversion + " " + type + " " + patch).replace(".0 ", " ").trim();
            out.add(display.replaceFirst(java.util.regex.Pattern.quote(mc) + "\\s+", ""));
        }
        if (out.isEmpty()) throw new Exception("OptiFine 没有适用于 Minecraft " + mc + " 的版本");
        return out;
    }

    /** OptiFine 各版本的真实文件名。 */
    public static Map<String, String> optifineFiles(String mc) throws Exception {
        String json = get("https://bmclapi2.bangbang93.com/optifine/versionList");
        List<Object> arr = Json.arr(Json.parse(json));
        Map<String, String> out = new LinkedHashMap<>();
        for (Object o : arr) {
            Map<String, Object> m = Json.obj(o);
            String mcversion = Json.str(m, "mcversion", "");
            if (!mcversion.equals(mc)) continue;
            String type = Json.str(m, "type", "").replace("HD_U", "").replace("_", " ");
            String patch = Json.str(m, "patch", "");
            String display = (mcversion + " " + type + " " + patch).replace(".0 ", " ").trim();
            display = display.replaceFirst(java.util.regex.Pattern.quote(mc) + "\\s+", "");
            out.put(display, Json.str(m, "filename", ""));
        }
        if (out.isEmpty()) throw new Exception("OptiFine 没有适用于 Minecraft " + mc + " 的版本");
        return out;
    }

    private static List<String> optiFabric(String mc) throws Exception {
        List<Object> files = CurseForge.projectFiles(322385);
        List<String> out = new ArrayList<>();
        for (Object o : files) {
            Map<String, Object> m = Json.obj(o);
            Map<String, Object> download = Json.obj(m, "download");
            String fileName = Json.str(download, "filename", "");
            if (fileName.toLowerCase().contains("optifabric") && fileName.endsWith(".jar")) {
                out.add(fileName.toLowerCase().replace("optifabric-", "").replace(".jar", "").trim());
            }
        }
        if (out.isEmpty()) throw new Exception("OptiFabric 没有可下载的版本（需填写 CurseForge Key）");
        return out;
    }

    private static List<String> fabricApi(String mc) throws Exception {
        List<Object> versions = Modrinth.projectVersions("fabric-api");
        List<String> out = new ArrayList<>();
        for (Object o : versions) {
            Map<String, Object> obj = Json.obj(o);
            for (Object gv : Json.arr(obj, "game_versions")) {
                if (mc.equals(String.valueOf(gv))) {
                    out.add(Json.str(obj, "version_number", ""));
                    break;
                }
            }
        }
        if (out.isEmpty()) throw new Exception("Fabric API 没有适用于 Minecraft " + mc + " 的版本");
        return out;
    }

    /** 附加载器 Mod 的下载地址，找不到返回 null。 */
    public static String[] modFile(String loader, String mc, String version) throws Exception {
        if (FABRICAPI.equals(loader)) {
            // 取 Modrinth 的 primary jar
            for (Object o : Modrinth.projectVersions("fabric-api")) {
                Map<String, Object> obj = Json.obj(o);
                if (!version.equals(Json.str(obj, "version_number", ""))) continue;
                boolean gameOk = false;
                for (Object gv : Json.arr(obj, "game_versions")) {
                    if (mc.equals(String.valueOf(gv))) { gameOk = true; break; }
                }
                if (!gameOk) continue;
                String[] fallback = null;
                for (Object f : Json.arr(obj, "files")) {
                    Map<String, Object> file = Json.obj(f);
                    String url = Json.str(file, "url", "");
                    String fn = Json.str(file, "filename", "");
                    if (url.isEmpty() || !fn.endsWith(".jar")) continue;
                    if (fallback == null) fallback = new String[]{url, fn};
                    if (Boolean.TRUE.equals(file.get("primary"))) return new String[]{url, fn};
                }
                if (fallback != null) return fallback;
            }
            return null;
        }
        if (OPTIFABRIC.equals(loader)) {
            // CurseForge 项目 322385，需要 API Key
            for (Object o : CurseForge.projectFiles(322385)) {
                Map<String, Object> m = Json.obj(o);
                Map<String, Object> dl = Json.obj(m, "download");
                String fn = Json.str(dl, "filename", "");
                String derived = fn.toLowerCase().replace("optifabric-", "").replace(".jar", "").trim();
                if (!version.equals(derived)) continue;
                String url = Json.str(dl, "url", "");
                return url.isEmpty() ? null : new String[]{url, fn};
            }
            return null;
        }
        return null;
    }

    /** Cleanroom 版本列表，来自 GitHub Releases。 */
    private static List<String> cleanroom(String mc) throws Exception {
        String json = get("https://api.github.com/repos/CleanroomMC/Cleanroom/releases");
        List<Object> arr = Json.arr(Json.parse(json));
        List<String> out = new ArrayList<>();
        for (Object o : arr) {
            String tag = Json.str(Json.obj(o), "tag_name", "");
            if (!tag.isEmpty()) out.add(tag);
        }
        if (out.isEmpty()) throw new Exception("Cleanroom 获取到的列表为空");
        return out;
    }

    /** LabyMod 两个渠道，mc 参数用不上。 */
    private static List<String> labyMod(String mc) throws Exception {
        List<String> out = new ArrayList<>();
        out.add("production");
        try {
            get("https://releases.r2.labymod.net/api/v1/manifest/snapshot/latest.json");
            out.add("snapshot");
        } catch (Exception ignored) {
            // 快照不可用就只列 production
        }
        return out;
    }

    private static List<String> forge(String mc) throws Exception {
        String xml = get("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
        while (m.find()) {
            String v = m.group(1);
            // 版本形如 1.21.1-52.0.2，只留当前 MC 版本的
            if (v.startsWith(mc + "-")) out.add(v.substring(mc.length() + 1));
        }
        if (out.isEmpty()) throw new Exception("Forge 没有适用于 Minecraft " + mc + " 的版本");
        Collections.reverse(out); // 保持旧到新
        return out;
    }

    private static List<String> neoforge(String mc) throws Exception {
        String json = get("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");
        String prefix = neoforgePrefix(mc);
        if (prefix == null) throw new Exception("NeoForge 只支持 1.20.2 及之后的版本");
        List<String> out = new ArrayList<>();
        for (Object o : Json.arr(Json.obj(Json.parse(json)), "versions")) {
            String v = String.valueOf(o);
            if (v.startsWith(prefix)) out.add(v);
        }
        if (out.isEmpty()) throw new Exception("NeoForge 没有适用于 Minecraft " + mc + " 的版本");
        return out;
    }

    /** MC 版本转 NeoForge 版本前缀。 */
    static String neoforgePrefix(String mc) {
        if (mc == null) return null;
        String[] p = mc.split("\\.");
        if (p.length < 2) return null;
        int minor;
        try {
            minor = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (minor < 20) return null;
        String tail = p.length > 2 ? p[2] : "0";
        return minor + "." + tail + ".";
    }

    private static List<String> fabric(String mc) throws Exception {
        String json = get("https://meta.fabricmc.net/v2/versions/loader/" + mc);
        List<Object> items = Json.arr(Json.parse(json));
        List<String> out = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> loader = Json.obj(Json.obj(item), "loader");
            String v = Json.str(loader, "version", "");
            if (!v.isEmpty()) out.add(v);
        }
        if (out.isEmpty()) throw new Exception("Fabric 没有适用于 Minecraft " + mc + " 的加载器版本");
        Collections.reverse(out); // 按旧到新显示
        return out;
    }

    private static String get(String url) throws Exception {
        // 版本清单都不小，走 gzip
        return Http.get(url);
    }
}
