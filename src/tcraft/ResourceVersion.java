package tcraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 资源版本文件模型。 */
public class ResourceVersion {

    public String id = "";
    /** 展示名：Modrinth 是 name，CurseForge 是 displayName。 */
    public String display = "";
    /** 版本号：Modrinth 是 version_number。 */
    public String version = "";
    public String fileName = "";
    public List<String> downloadUrls = new ArrayList<>();
    public String hash = "";
    public long size = -1;
    /** ISO 时间。 */
    public String releaseDate = "";
    /** 0 正式版 / 1 测试版 / 2 抢先体验版。 */
    public int releaseType = 0;
    public long downloadCount;
    public List<String> gameVersions = new ArrayList<>();
    public List<String> loaders = new ArrayList<>();
    /** 原始前置项目 ID，已排除 Fabric/Quilt API。 */
    public List<String> rawDependencies = new ArrayList<>();
    /** 解析后的前置项目 ID。 */
    public List<String> dependencies = new ArrayList<>();
    /** mod / modpack / datapack / resourcepack / shader / world / plugin。 */
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
        // 取 primary 文件，files[0] 可能是 dev jar
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
        // 备用地址跳过 sources 和 dev 文件
        for (Object o : files) {
            Map<String, Object> f = Json.obj(o);
            String fn = Json.str(f, "filename", "").toLowerCase();
            if (fn.contains("source") || fn.contains("javadoc") || fn.endsWith("-dev.jar")) continue;
            String u = Json.str(f, "url", "");
            if (!u.isEmpty() && !r.downloadUrls.contains(u)) r.downloadUrls.add(u);
        }
        // 结果里可能混着插件和数据包
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
            if (!r.loaders.isEmpty() && !"plugin".equals(r.resourceType)) r.resourceType = "mod";
        } else {
            r.resourceType = expectedType;
        }
        // 只取 required，排除 Fabric 和 Quilt API
        for (Object d : Json.arr(data.get("dependencies"))) {
            Map<String, Object> dep = Json.obj(d);
            if (!"required".equals(Json.str(dep, "dependency_type", ""))) continue;
            String pid = Json.str(dep, "project_id", "");
            if (pid.isEmpty() || pid.equals("P7dR8mSH") || pid.equals("qvIfYCYJ")) continue;
            r.rawDependencies.add(pid);
        }
        // 只留标准版本号，快照另算
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
            standards.sort((a, b) -> compareVersion(b, a)); // 新版本在前
            if (r.resourceType.equals("modpack")) r.gameVersions = new ArrayList<>(standards.subList(0, 1)); // 整合包只「支持」一个版本
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
        r.size = len == 0 ? -1 : len; // CurseForge 可能返回 0 尺寸
        for (Object h : Json.arr(data.get("hashes"))) {
            Map<String, Object> hm = Json.obj(h);
            int algo = (int) Json.num(hm, "algo", 0);
            if (algo == 1) { r.hash = Json.str(hm, "value", "").toLowerCase(); break; }
        }
        // 没地址就按文件 ID 拼 CDN 地址
        String url = Json.str(data, "downloadUrl", "");
        if (url.isEmpty() && r.id.length() > 4) {
            url = "https://edge.forgecdn.net/files/" + r.id.substring(0, 4) + "/" + r.id.substring(4) + "/" + r.fileName;
        }
        if (!url.isEmpty()) {
            r.downloadUrls.add(url.replace("-service.overwolf.wtf", ".forgecdn.net")
                    .replace("://edge.", "://mediafilez.").replace("://media.", "://mediafilez."));
            r.downloadUrls.add(url.replace("://edge.", "://mediafilez.").replace("://media.", "://mediafilez."));
            r.downloadUrls.add(url.replace("-service.overwolf.wtf", ".forgecdn.net"));
            String direct = url.replace("://media.", "://edge.");
            if (!r.downloadUrls.contains(direct)) r.downloadUrls.add(direct);
        }
        // relationType 3 是依赖，排除 Fabric 和 Quilt API
        for (Object d : Json.arr(data.get("dependencies"))) {
            Map<String, Object> dep = Json.obj(d);
            if ((int) Json.num(dep, "relationType", 0) != 3) continue;
            long mid = (long) Json.num(dep, "modId", 0);
            if (mid == 306612 || mid == 634179) continue;
            r.rawDependencies.add(String.valueOf(mid));
        }
        // 只留标准格式，整合包只留一个
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
        // 加载器名在原始列表里就算支持
        for (String l : java.util.List.of("fabric", "forge", "quilt", "neoforge")) {
            if (rawVersions.contains(l)) r.loaders.add(l);
        }
        r.resourceType = expectedType;
        return r;
    }

    /** 文件信息行。 */
    public String infoLine(boolean useFileNameTitle) {
        List<String> parts = new ArrayList<>();
        if (!loaders.isEmpty()) parts.add(displayLoaders(loaders));
        if (!fileName.isEmpty() && !fileName.equals(display)) {
            parts.add(stripExtension(fileName));
        }
        if (!dependencies.isEmpty()) parts.add(dependencies.size() + " 项前置");
        boolean allNonStandard = !gameVersions.isEmpty();
        for (String v : gameVersions) {
            if (v.contains(".") && !v.contains("w") && !v.toLowerCase().contains("snapshot")
                    && !v.contains("rc") && !v.contains("pre") && !v.contains("experimental")
                    && !v.contains("-") && !v.equals("远古版本")) {
                allNonStandard = false;
                break;
            }
        }
        if (allNonStandard) parts.add("游戏版本 " + String.join("、", gameVersions));
        if (downloadCount > 0) {
            parts.add("下载 " + (downloadCount > 100000 ? Math.round(downloadCount / 10000d) + " 万" : downloadCount + " ") + "次");
        }
        String ago = timeAgo(releaseDate);
        if (!ago.isEmpty()) parts.add("更新于 " + ago);
        if (releaseType != 0) parts.add(releaseTypeDisplay(releaseType));
        return String.join("，", parts);
    }

    /** 列表标题，去掉压缩包后缀。 */
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

    /** 加载器显示名，首字母大写。 */
    public static String displayLoaders(List<String> loaders) {
        List<String> names = new ArrayList<>();
        for (String l : loaders) {
            if (l == null || l.isEmpty()) continue;
            names.add(Character.toUpperCase(l.charAt(0)) + l.substring(1).toLowerCase());
        }
        return String.join("、", names);
    }

    /** 标准格式：1.x 或 1.x.y，都是纯数字段。 */
    public static boolean isStandard(String name) {
        if (name == null || !name.startsWith("1.")) return false;
        String body = name.substring(2);
        for (String seg : body.split("[.-]")) {
            if (seg.isEmpty() || !seg.chars().allMatch(Character::isDigit)) return false;
        }
        return true;
    }

    /** 版本代号：1.20 得 120，非标准返回 209。 */
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

    /** 按代号还原版本名。 */
    public static String dropToVersion(int drop) {
        return "1." + (drop - 100);
    }

    /** 版本归类：快照、远古版或大版本分组。 */
    public static String groupedName(String name, boolean byDrop, boolean foldOld) {
        if (name == null || name.isEmpty()) return "其他";
        int drop = versionDrop(name);
        if (name.contains("w") || drop == 209) return "快照版";
        if (!isStandard(name) || (foldOld && drop < 120)) return "远古版";
        if (byDrop) return dropToVersion(drop);
        return name;
    }

    /** 版本号比较，a 更新返回正数。 */
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
                long va = Long.parseLong(xa), vb = Long.parseLong(xb);
                if (va != vb) return va > vb ? 1 : -1;
            } else {
                int c = xa.compareTo(xb);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    /** 发行类型显示名。 */
    public static String releaseTypeDisplay(int type) {
        return switch (type) {
            case 0 -> "正式版";
            case 1 -> "测试版";
            case 2 -> "抢先体验版";
            default -> "未知类型";
        };
    }

    /** 发行类型排序权重：Release < Beta < Alpha < 其他。 */
    public static int releaseTypeRank(int type) {
        return type <= 2 ? type : 3;
    }

    /** 时间差文案。 */
    public static String timeAgo(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            java.time.Instant t = java.time.Instant.parse(iso);
            long seconds = (System.currentTimeMillis() - t.toEpochMilli()) / 1000;
            if (seconds < 0) seconds = 0;
            if (seconds < 60) return "1 分钟内";
            if (seconds < 3600) return seconds / 60 + " 分钟前";
            if (seconds < 86400) return seconds / 3600 + " 小时前";
            if (seconds < 86400L * 30) return seconds / 86400 + " 天前";
            if (seconds < 86400L * 365) return seconds / 86400 / 30 + " 个月前";
            return seconds / 86400 / 365 + " 年前";
        } catch (Exception e) {
            return "";
        }
    }

    /** 文件大小显示。 */
    public static String fmtSize(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024d);
        return String.format("%.2f MB", bytes / 1024d / 1024d);
    }

    private static List<String> strList(Object arr) {
        List<String> out = new ArrayList<>();
        for (Object o : Json.arr(arr)) {
            if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }
}
