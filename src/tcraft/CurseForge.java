package tcraft;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CurseForge API 客户端，世界和地图类用它。 */
public final class CurseForge {

    private static final String API = "https://api.curseforge.com/v1";
    public static final int CLASS_MOD = 6;
    public static final int CLASS_WORLD = 17;
    public static final int CLASS_RESOURCEPACK = 12;
    public static final int CLASS_SHADER = 6552;
    public static final int CLASS_DATAPACK = 6945;
    public static final int CLASS_MODPACK = 4471;

    private CurseForge() {
    }

    public static String apiKey() {
        return Data.get().get("SystemCurseKey", "").trim();
    }

    public static class Project {
        public long id;
        public String name = "";
        public String slug = "";
        public String summary = "";
        public String icon = "";
        public String website = "";
        public long downloads;
    }

    public static List<Project> search(String query, int classId, int limit) throws IOException {
        return search(query, classId, limit, 0, 0);
    }

    /** 带分类和偏移的搜索。 */
    public static List<Project> search(String query, int classId, int limit, int categoryId, int offset) throws IOException {
        String key = apiKey();
        if (key.isEmpty()) throw new IOException("尚未填写 CurseForge API Key");
        StringBuilder url = new StringBuilder(API + "/mods/search?gameId=432&classId=").append(classId)
                .append("&sortField=2&sortOrder=desc&pageSize=").append(limit)
                .append("&offset=").append(Math.max(0, offset));
        if (categoryId > 0) url.append("&categoryId=").append(categoryId);
        if (query != null && !query.isEmpty()) {
            url.append("&searchFilter=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        }
        Map<String, Object> root = Json.obj(Json.parse(get(url.toString(), key)));
        List<Project> out = new ArrayList<>();
        for (Object o : Json.arr(root.get("data"))) {
            Map<String, Object> m = Json.obj(o);
            Project p = new Project();
            p.id = (long) Json.num(m, "id", 0);
            p.name = Json.str(m, "name", "");
            p.slug = Json.str(m, "slug", "");
            p.summary = Json.str(m, "summary", "");
            p.downloads = (long) Json.num(m, "downloadCount", 0);
            Map<String, Object> logo = m.get("logo") instanceof Map ? Json.obj(m.get("logo")) : null;
            if (logo != null) p.icon = Json.str(logo, "thumbnailUrl", Json.str(logo, "url", ""));
            Map<String, Object> links = m.get("links") instanceof Map ? Json.obj(m.get("links")) : null;
            if (links != null) p.website = Json.str(links, "websiteUrl", "");
            if (p.website.isEmpty() && !p.slug.isEmpty()) {
                p.website = "https://www.curseforge.com/minecraft/worlds/" + p.slug;
            }
            out.add(p);
        }
        return out;
    }

    /** 取项目全部文件的原始 JSON。 */
    public static List<Object> projectFiles(long id) throws IOException {
        String key = apiKey();
        if (key.isEmpty()) throw new IOException("尚未填写 CurseForge API Key");
        Map<String, Object> root = Json.obj(Json.parse(get(API + "/mods/" + id + "/files?pageSize=10000", key)));
        return Json.arr(root.get("data"));
    }

    /** 单个项目信息，前置展示用。 */
    public static Project projectInfo(long id) throws IOException {
        String key = apiKey();
        if (key.isEmpty()) throw new IOException("尚未填写 CurseForge API Key");
        Map<String, Object> root = Json.obj(Json.parse(get(API + "/mods/" + id, key)));
        Map<String, Object> m = Json.obj(root.get("data"));
        Project p = new Project();
        p.id = (long) Json.num(m, "id", 0);
        p.name = Json.str(m, "name", "");
        p.slug = Json.str(m, "slug", "");
        p.summary = Json.str(m, "summary", "");
        p.downloads = (long) Json.num(m, "downloadCount", 0);
        Map<String, Object> logo = m.get("logo") instanceof Map ? Json.obj(m.get("logo")) : null;
        if (logo != null) p.icon = Json.str(logo, "thumbnailUrl", Json.str(logo, "url", ""));
        Map<String, Object> links = m.get("links") instanceof Map ? Json.obj(m.get("links")) : null;
        if (links != null) p.website = Json.str(links, "websiteUrl", "");
        if (p.website.isEmpty() && !p.slug.isEmpty()) {
            p.website = "https://www.curseforge.com/minecraft/mods/" + p.slug;
        }
        return p;
    }

    /** 下载文件到 target。 */
    private static void downloadFile(Tasks.Task task, String url, Path target) throws IOException {
        // 走 Fetch，支持续传和重试
        Fetch.file(task, url, target, null, 0, 0.99);
    }

    /** 按 fileId 批量查文件信息，整合包安装用。 */
    public static List<Object> filesByIds(List<Long> ids) throws IOException {
        String key = apiKey();
        if (key.isEmpty()) throw new IOException("尚未填写 CurseForge API Key");
        StringBuilder body = new StringBuilder("{\"fileIds\": [");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) body.append(",");
            body.append(ids.get(i));
        }
        body.append("]}");
        Map<String, Object> root = Json.obj(Json.parse(post(API + "/mods/files", body.toString(), key)));
        return Json.arr(root.get("data"));
    }

    private static String post(String url, String body, String key) throws IOException {
        // 共享连接池，少握手
        return Http.post(url, body, Map.of("Accept", "application/json", "x-api-key", key));
    }

    private static String get(String url, String key) throws IOException {
        return Http.get(url, Map.of("Accept", "application/json", "x-api-key", key));
    }
}
