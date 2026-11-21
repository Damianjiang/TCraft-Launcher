package tcraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static String installerUrl(String loader, String channel) {
        switch (loader) {
            case CLEANROOM:
                return "https://github.com/CleanroomMC/Cleanroom/releases/download/" + channel + "/cleanroom-" + channel + "-installer.jar";
            case LABYMOD:
                return "https://releases.labymod.net/api/v1/installer/" + channel + "/java";
            default:
                return "";
        }
    }

    public static String installerName(String loader, String channel) {
        if (CLEANROOM.equals(loader)) return "cleanroom-" + channel + "-installer.jar";
        if (LABYMOD.equals(loader)) return "LabyMod4" + ("snapshot".equals(channel) ? "Snapshot" : "Production") + "Installer.jar";
        return "";
    }

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
        Map<String, Object> entry = Json.obj(versions.get(mc));
        Object node = firstValue(Json.obj(entry, "artefacts"), Json.obj(entry, "snapshots"), "com.mumfrey:liteloader");
        if (node == null) {
            throw new Exception(Lang.t("LiteLoader 没有适用于 Minecraft ") + mc + Lang.t(" 的版本"));
        }
        List<String> out = new ArrayList<>();
        out.add(mc);
        return out;
    }

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
        return out;
    }

    private static List<String> optifine(String mc) throws Exception {
        String json = get("https://bmclapi2.bangbang93.com/optifine/versionList");
        List<Object> arr = Json.arr(Json.parse(json));
        List<String> out = new ArrayList<>();
        for (Object o : arr) {
            Map<String, Object> m = Json.obj(o);
            String mcversion = Json.str(m, "mcversion", "");
            if (!mcversion.equals(mc)) continue;
            String type = Json.str(m, "type", "").replace("HD_U", "").replace("_", " ");
            String patch = Json.str(m, "patch", "");
            String display = (mcversion + " " + type + " " + patch).replace(".0 ", " ").trim();
            out.add(display.replaceFirst(java.util.regex.Pattern.quote(mc) + "\\s+", ""));
        }
        if (out.isEmpty()) {
            throw new Exception(Lang.t("OptiFine 没有适用于 Minecraft ") + mc + Lang.t(" 的版本"));
        }
        return out;
    }

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
        if (out.isEmpty()) {
            throw new Exception(Lang.t("OptiFine 没有适用于 Minecraft ") + mc + Lang.t(" 的版本"));
        }
        return out;
    }

    private static List<String> optiFabric(String mc) throws Exception {
        List<Object> files = CurseForge.projectFiles(322385);
        List<String> out = new ArrayList<>();
        for (Object o : files) {
            Map<String, Object> m = Json.obj(o);
            Map<String, Object> download = Json.obj(m, "download");
            String fileName = Json.str(download, "filename", "");
            if (fileName.toLowerCase(java.util.Locale.ROOT).contains("optifabric") && fileName.endsWith(".jar")) {
                out.add(fileName.toLowerCase(java.util.Locale.ROOT).replace("optifabric-", "").replace(".jar", "").trim());
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
        if (out.isEmpty()) {
            throw new Exception(Lang.t("Fabric API 没有适用于 Minecraft ") + mc + Lang.t(" 的版本"));
        }
        return out;
    }

    public static String[] modFile(String loader, String mc, String version) throws Exception {
        if (FABRICAPI.equals(loader)) {
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
            for (Object o : CurseForge.projectFiles(322385)) {
                Map<String, Object> m = Json.obj(o);
                Map<String, Object> dl = Json.obj(m, "download");
                String fn = Json.str(dl, "filename", "");
                String derived = fn.toLowerCase(java.util.Locale.ROOT).replace("optifabric-", "").replace(".jar", "").trim();
                if (!version.equals(derived)) continue;
                String url = Json.str(dl, "url", "");
                return url.isEmpty() ? null : new String[]{url, fn};
            }
            return null;
        }
        return null;
    }

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

    private static List<String> labyMod(String mc) throws Exception {
        List<String> out = new ArrayList<>();
        out.add("production");
        try {
            get("https://releases.r2.labymod.net/api/v1/manifest/snapshot/latest.json");
            out.add("snapshot");
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<String> forge(String mc) throws Exception {
        List<String> out = new ArrayList<>();
        try {
            String json = get(Downloader.bmcl() + "/forge/minecraft/" + mc);
            for (Object o : Json.arr(Json.parse(json))) {
                Map<String, Object> m = Json.obj(o);
                if (!mc.equals(Json.str(m, "mcversion", mc))) continue;
                String v = Json.str(m, "version", "");
                if (!v.isEmpty() && !out.contains(v)) out.add(v);
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty()) {
            String xml = get("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
                    Downloader.bmcl() + "/maven/net/minecraftforge/forge/maven-metadata.xml");
            Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
            while (m.find()) {
                String v = m.group(1);
                if (v.startsWith(mc + "-")) out.add(v.substring(mc.length() + 1));
            }
        }
        if (out.isEmpty()) {
            throw new Exception(Lang.t("Forge 没有适用于 Minecraft ") + mc + Lang.t(" 的版本"));
        }
        out.sort(Loaders::compareVersions);
        return out;
    }

    private static List<String> neoforge(String mc) throws Exception {
        String prefix = neoforgePrefix(mc);
        if (prefix == null) throw new Exception("NeoForge 只支持 1.20.2 及之后的版本");
        List<String> out = new ArrayList<>();
        try {
            String xml = get(Downloader.bmcl() + "/maven/net/neoforged/neoforge/maven-metadata.xml",
                    "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");
            Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
            while (m.find()) {
                String v = m.group(1);
                if (v.startsWith(prefix)) out.add(v);
            }
            if (out.isEmpty()) {
                // 官方 API 是 JSON
                for (Object o : Json.arr(Json.obj(Json.parse(xml)), "versions")) {
                    String v = String.valueOf(o);
                    if (v.startsWith(prefix)) out.add(v);
                }
            }
        } catch (Exception e) {
            for (Object o : Json.arr(Json.obj(Json.parse(get(
                    "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"))), "versions")) {
                String v = String.valueOf(o);
                if (v.startsWith(prefix)) out.add(v);
            }
        }
        if (out.isEmpty()) {
            throw new Exception(Lang.t("NeoForge 没有适用于 Minecraft ") + mc + Lang.t(" 的版本"));
        }
        out.sort(Loaders::compareVersions);
        return out;
    }

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
        String json = get(Downloader.bmcl() + "/fabric-meta/v2/versions/loader/" + mc,
                "https://meta.fabricmc.net/v2/versions/loader/" + mc);
        List<Object> items = Json.arr(Json.parse(json));
        List<String> out = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> loader = Json.obj(Json.obj(item), "loader");
            String v = Json.str(loader, "version", "");
            if (!v.isEmpty()) out.add(v);
        }
        if (out.isEmpty()) throw new Exception("Fabric 没有适用于 Minecraft " + mc + " 的加载器版本");
        Collections.reverse(out);
        return out;
    }

    private static String get(String... urls) throws Exception {
        return Downloader.getText(urls);
    }

    private static int compareVersions(String a, String b) {
        String[] x = a.split("[^0-9]+");
        String[] y = b.split("[^0-9]+");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int av = i < x.length && !x[i].isEmpty() ? parseInt(x[i]) : 0;
            int bv = i < y.length && !y[i].isEmpty() ? parseInt(y[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
