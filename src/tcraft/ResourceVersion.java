package tcraft;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResourceVersion {

    public String id = "";
    public String display = "";
    public String version = "";
    public String fileName = "";
    public List<String> downloadUrls = new ArrayList<>();
    public String hash = "";
    public long size = -1;
    public String releaseDate = "";
    public int releaseType = 0;
    public long downloadCount;
    public List<String> gameVersions = new ArrayList<>();
    public List<String> loaders = new ArrayList<>();
    public List<String> rawDependencies = new ArrayList<>();
    public List<String> dependencies = new ArrayList<>();
    public String resourceType = "mod";

    public static ResourceVersion fromModrinthJson(Map<String, Object> data, String expectedType) {
        ResourceVersion r = new ResourceVersion();
        r.id = Json.str(data, "id", "");
        r.display = Json.str(data, "name", "").replace("\t", "").trim();
        r.version = Json.str(data, "version_number", "");
        r.releaseDate = Json.str(data, "date_published", "");
        String vt = Json.str(data, "version_type", "release");
        r.releaseType = vt.equals("release") ? 0 : vt.equals("beta") ? 1 : 2;
        r.downloadCount = (long) Json.num(data, "downloads", 0);
        List<Object> files = Json.arr(data.get("files"));
        Map<String, Object> primary = null;
        for (Object o : files) {
            Map<String, Object> f = Json.obj(o);
            if (Json.bool(f, "primary", false)) {
                primary = f;
                break;
            }
        }
        if (primary == null && !files.isEmpty()) primary = Json.obj(files.get(0));
        if (primary != null) {
            r.fileName = Json.str(primary, "filename", "");
            String url = Json.str(primary, "url", "");
            if (!url.isEmpty()) r.downloadUrls.add(url);
            r.size = (long) Json.num(primary, "size", -1);
            Map<String, Object> hashes = Json.obj(primary.get("hashes"));
            String sha1 = Json.str(hashes, "sha1", "");
            if (!sha1.isEmpty()) r.hash = sha1.toLowerCase();
        }
        for (Object o : files) {
            Map<String, Object> f = Json.obj(o);
            String fn = Json.str(f, "filename", "").toLowerCase();
            if (fn.contains("source") || fn.contains("javadoc") || fn.endsWith("-dev.jar")) continue;
            String u = Json.str(f, "url", "");
            if (!u.isEmpty() && !r.downloadUrls.contains(u)) r.downloadUrls.add(u);
        }
        List<String> rawLoaders = strList(data.get("loaders"));
        for (String l : rawLoaders) {
            if (!r.loaders.contains(l)) r.loaders.add(l);
        }
        if (expectedType.equals("mod") || expectedType.equals("datapack")) {
            for (String l : rawLoaders) {
                if (java.util.List.of("bukkit", "folia", "paper", "purpur", "spigot").contains(l.toLowerCase())) {
                    r.resourceType = "plugin";
                    break;
                }
            }
            if (rawLoaders.contains("datapack")) r.resourceType = "datapack";
            if (!r.loaders.isEmpty() && !"plugin".equals(r.resourceType) && !"datapack".equals(r.resourceType)) {
                r.resourceType = "mod";
            }
        } else {
            r.resourceType = expectedType;
        }
        for (Object d : Json.arr(data.get("dependencies"))) {
            Map<String, Object> dep = Json.obj(d);
            if (!"required".equals(Json.str(dep, "dependency_type", ""))) continue;
            String pid = Json.str(dep, "project_id", "");
            if (pid.isEmpty() || pid.equals("P7dR8mSH") || pid.equals("qvIfYCYJ")) continue;
            r.rawDependencies.add(pid);
        }
        List<String> rawVersions = strList(data.get("game_versions"));
        List<String> standards = new ArrayList<>();
        for (String v : rawVersions) {
            String t = v.trim().toLowerCase();
            if (!t.contains(".")) continue;
            String mapped;
            if (t.contains("-")) mapped = t.substring(0, t.indexOf('-')) + " 预览版";
            else if (t.startsWith("b1.")) mapped = "远古版本";
            else mapped = t;
            if (!standards.contains(mapped)) standards.add(mapped);
        }
        if (standards.size() == 1) {
            r.gameVersions = standards;
        } else if (standards.size() > 1) {
            standards.sort((a, b) -> compareVersion(b, a));
            if (r.resourceType.equals("modpack")) r.gameVersions = new ArrayList<>(standards.subList(0, 1));
            else r.gameVersions = standards;
        } else {
            List<String> snaps = new ArrayList<>();
            for (String v : rawVersions) {
                if (v.trim().matches("[0-9]{2}w[0-9]{2}[a-z]")) snaps.add(v.trim());
            }
            r.gameVersions = snaps.isEmpty() ? new ArrayList<>(List.of("未知版本")) : snaps;
        }
        return r;
    }

    public static ResourceVersion fromCurseForgeJson(Map<String, Object> data, String expectedType) {
        ResourceVersion r = new ResourceVersion();
        r.id = String.valueOf((long) Json.num(data, "id", 0));
        r.display = Json.str(data, "displayName", "").replace("\t", "").trim();
        r.releaseDate = Json.str(data, "fileDate", "");
        int rt = (int) Json.num(data, "releaseType", 1);
        r.releaseType = rt == 1 ? 0 : rt == 2 ? 1 : 2;
        r.downloadCount = (long) Json.num(data, "downloadCount", 0);
        r.fileName = Json.str(data, "fileName", "");
        long len = (long) Json.num(data, "fileLength", -1);
        r.size = len == 0 ? -1 : len;
        for (Object h : Json.arr(data.get("hashes"))) {
            Map<String, Object> hm = Json.obj(h);
            int algo = (int) Json.num(hm, "algo", 0);
            if (algo == 1) { r.hash = Json.str(hm, "value", "").toLowerCase(); break; }
        }
        String url = Json.str(data, "downloadUrl", "");
        if (url.isEmpty() && r.id.length() > 4) {
            String encoded = java.net.URLEncoder.encode(r.fileName, StandardCharsets.UTF_8).replace("+", "%20");
            url = "https://edge.forgecdn.net/files/" + r.id.substring(0, 4) + "/" + r.id.substring(4) + "/" + encoded;
        }
        if (!url.isEmpty()) {
            r.downloadUrls.add(url.replace("-service.overwolf.wtf", ".forgecdn.net")
                    .replace("://edge.", "://mediafilez.").replace("://media.", "://mediafilez."));
            r.downloadUrls.add(url.replace("://edge.", "://mediafilez.").replace("://media.", "://mediafilez."));
            r.downloadUrls.add(url.replace("-service.overwolf.wtf", ".forgecdn.net"));
            String direct = url.replace("://media.", "://edge.");
            if (!r.downloadUrls.contains(direct)) r.downloadUrls.add(direct);
        }
        // relationType 3 是依赖
        for (Object d : Json.arr(data.get("dependencies"))) {
            Map<String, Object> dep = Json.obj(d);
            if ((int) Json.num(dep, "relationType", 0) != 3) continue;
            long mid = (long) Json.num(dep, "modId", 0);
            if (mid == 306612 || mid == 634179) continue;
            r.rawDependencies.add(String.valueOf(mid));
        }
        List<String> rawVersions = strList(data.get("gameVersions"));
        List<String> standards = new ArrayList<>();
        for (String v : rawVersions) {
            String t = v.trim().toLowerCase();
            if (!isStandard(t)) continue;
            String mapped = t.contains("-snapshot") ? t.replace("-snapshot", " 预览版") : t;
            if (!standards.contains(mapped)) standards.add(mapped);
        }
        if (standards.size() > 1) standards.sort((a, b) -> compareVersion(b, a));
        if (standards.size() > 1 && r.resourceType.equals("modpack")) {
            r.gameVersions = new ArrayList<>(standards.subList(0, 1));
        } else if (!standards.isEmpty()) {
            r.gameVersions = standards;
        } else {
            r.gameVersions = new ArrayList<>(List.of("未知版本"));
        }
        for (String l : java.util.List.of("fabric", "forge", "quilt", "neoforge")) {
            if (rawVersions.contains(l)) r.loaders.add(l);
        }
        r.resourceType = expectedType;
        return r;
    }

    public String infoLine(boolean useFileNameTitle) {
        // 拼接行逐段翻译
        List<String> parts = new ArrayList<>();
        if (!loaders.isEmpty()) parts.add(displayLoaders(loaders));
        if (!fileName.isEmpty() && !fileName.equals(display)) {
            parts.add(stripExtension(fileName));
        }
        if (!dependencies.isEmpty()) parts.add(dependencies.size() + Lang.t(" 项前置"));
        boolean allNonStandard = !gameVersions.isEmpty();
        for (String v : gameVersions) {
            if (v.contains(".") && !v.contains("w") && !v.toLowerCase().contains("snapshot")
                    && !v.contains("rc") && !v.contains("pre") && !v.contains("experimental")
                    && !v.contains("-") && !v.equals("远古版本")) {
                allNonStandard = false;
                break;
            }
        }
        if (allNonStandard) parts.add(Lang.t("游戏版本 ") + String.join("、", gameVersions));
        if (downloadCount > 0) {
            // 英文 k 为千位
            parts.add(Lang.t("下载 ") + (downloadCount > 100000
                    ? (Lang.mode == 1 ? Math.round(downloadCount / 1000d) + "k"
                    : Math.round(downloadCount / 10000d) + Lang.t(" 万")) : downloadCount + " ") + Lang.t("次"));
        }
        String ago = timeAgo(releaseDate);
        if (!ago.isEmpty()) parts.add(Lang.t("更新于 ") + ago);
        if (releaseType != 0) parts.add(Lang.t(releaseTypeDisplay(releaseType)));
        return String.join(Lang.t("，"), parts);
    }

    public String title(boolean useFileNameTitle) {
        String src = useFileNameTitle ? fileName : display;
        return stripExtension(src);
    }

    public static String stripExtension(String name) {
        if (name == null) return "";
        for (String ext : new String[]{".zip", ".jar", ".mrpack", ".litemod"}) {
            int idx = name.toLowerCase().lastIndexOf(ext);
            if (idx > 0) return name.substring(0, idx);
        }
        return name;
    }

    public static String displayLoaders(List<String> loaders) {
        List<String> names = new ArrayList<>();
        for (String l : loaders) {
            if (l == null || l.isEmpty()) continue;
            names.add(Character.toUpperCase(l.charAt(0)) + l.substring(1).toLowerCase());
        }
        return String.join("、", names);
    }

    public static boolean isStandard(String name) {
        if (name == null || !name.startsWith("1.")) return false;
        String body = name.substring(2);
        for (String seg : body.split("[.-]")) {
            if (seg.isEmpty() || !seg.chars().allMatch(Character::isDigit)) return false;
        }
        return true;
    }

    public static int versionDrop(String name) {
        if (!isStandard(name)) return 209;
        String body = name.substring(2);
        String head = body.contains(".") ? body.substring(0, body.indexOf('.')) : body;
        try {
            return 100 + Integer.parseInt(head);
        } catch (NumberFormatException e) {
            return 209;
        }
    }

    public static String dropToVersion(int drop) {
        return "1." + (drop - 100);
    }

    public static String groupedName(String name, boolean byDrop, boolean foldOld) {
        if (name == null || name.isEmpty()) return "其他";
        int drop = versionDrop(name);
        if (name.contains("w") || drop == 209) return "快照版";
        if (!isStandard(name) || (foldOld && drop < 120)) return "远古版";
        if (byDrop) return dropToVersion(drop);
        return name;
    }

    public static int compareVersion(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] sa = a.toLowerCase().split("[.\\s_+-]+");
        String[] sb = b.toLowerCase().split("[.\\s_+-]+");
        int n = Math.max(sa.length, sb.length);
        for (int i = 0; i < n; i++) {
            String xa = i < sa.length ? sa[i] : "";
            String xb = i < sb.length ? sb[i] : "";
            if (xa.equals(xb)) continue;
            boolean na = xa.chars().allMatch(Character::isDigit) && !xa.isEmpty();
            boolean nb = xb.chars().allMatch(Character::isDigit) && !xb.isEmpty();
            if (na && nb) {
                // 防 long 溢出
                long va, vb;
                try {
                    va = Long.parseLong(xa);
                    vb = Long.parseLong(xb);
                } catch (NumberFormatException nfe) {
                    continue;
                }
                if (va != vb) return va > vb ? 1 : -1;
            } else {
                int c = xa.compareTo(xb);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    public static String releaseTypeDisplay(int type) {
        return switch (type) {
            case 0 -> "正式版";
            case 1 -> "测试版";
            case 2 -> "抢先体验版";
            default -> "未知类型";
        };
    }

    public static int releaseTypeRank(int type) {
        return type <= 2 ? type : 3;
    }

    public static String timeAgo(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            java.time.Instant t = java.time.Instant.parse(iso);
            long seconds = (System.currentTimeMillis() - t.toEpochMilli()) / 1000;
            if (seconds < 0) seconds = 0;
            if (seconds < 60) return Lang.t("1 分钟内");
            if (seconds < 3600) return Lang.tf("%d 分钟前", seconds / 60);
            if (seconds < 86400) return Lang.tf("%d 小时前", seconds / 3600);
            if (seconds < 86400L * 30) return Lang.tf("%d 天前", seconds / 86400);
            if (seconds < 86400L * 365) return Lang.tf("%d 个月前", seconds / 86400 / 30);
            return Lang.tf("%d 年前", seconds / 86400 / 365);
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> strList(Object arr) {
        List<String> out = new ArrayList<>();
        for (Object o : Json.arr(arr)) {
            if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }
}
