package tcraft;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Modrinth 资源搜索与下载：Mod、整合包、资源包、光影、数据包。 */
public final class Modrinth {

    private static final String API = "https://api.modrinth.com/v2";

    private Modrinth() {
    }

    /** 搜 Modrinth，返回命中列表。 */
    public static List<Object> search(String query, String projectType, int limit) throws IOException {
        return search(query, projectType, limit, null, null, null, 0, null);
    }

    /** 带筛选的搜索，index 是排序方式。 */
    public static List<Object> search(String query, String projectType, int limit,
                                      List<String> versions, List<String> loaders,
                                      String category, int offset, String index) throws IOException {
        boolean hasQuery = query != null && !query.isEmpty();
        StringBuilder facets = new StringBuilder("[[\"project_type:" + projectType + "\"]");
        if (versions != null && !versions.isEmpty()) {
            facets.append(",[");
            for (int i = 0; i < versions.size(); i++) {
                if (i > 0) facets.append(",");
                facets.append("\"versions:").append(versions.get(i)).append("\"");
            }
            facets.append("]");
        }
        if (loaders != null && !loaders.isEmpty()) {
            facets.append(",[");
            for (int i = 0; i < loaders.size(); i++) {
                if (i > 0) facets.append(",");
                facets.append("\"loaders:").append(loaders.get(i)).append("\"");
            }
            facets.append("]");
        }
        if (category != null && !category.isEmpty()) {
            facets.append(",[\"categories:").append(category).append("\"]");
        }
        facets.append("]");
        StringBuilder url = new StringBuilder(API + "/search?limit=").append(limit)
                .append("&offset=").append(Math.max(0, offset))
                .append("&index=").append(index != null && !index.isEmpty() ? index : (hasQuery ? "relevance" : "downloads"))
                .append("&facets=").append(URLEncoder.encode(facets.toString(), StandardCharsets.UTF_8));
        if (hasQuery) url.append("&query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        String text = get(url.toString());
        // 结果在 hits 字段里。
        return Json.arr(Json.obj(Json.parse(text)).get("hits"));
    }

    /** 项目版本列表缓存，只在本次运行内有效。 */
    private static final Map<String, List<Object>> VERSIONS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** 项目信息缓存，前置展示和解析用。 */
    private static final Map<String, Map<String, Object>> PROJECT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 取项目全部版本的原始 JSON，带运行内缓存。 */
    public static List<Object> projectVersions(String projectId) throws IOException {
        List<Object> cached = VERSIONS_CACHE.get(projectId);
        if (cached != null) return cached;
        String text = get(API + "/project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8)
                + "/version?include_changelog=false");
        List<Object> arr = Json.arr(Json.parse(text));
        VERSIONS_CACHE.put(projectId, arr);
        return arr;
    }

    /** 单个项目信息。 */
    public static Map<String, Object> projectInfo(String projectId) throws IOException {
        Map<String, Object> cached = PROJECT_CACHE.get(projectId);
        if (cached != null) return cached;
        String text = get(API + "/project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8));
        Map<String, Object> obj = Json.obj(Json.parse(text));
        PROJECT_CACHE.put(projectId, obj);
        return obj;
    }

    /** 批量取项目信息，前置资源展示用。 */
    public static List<Object> projectsInfo(List<String> ids) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(ids.get(i).replace("\"", "")).append("\"");
        }
        json.append("]");
        String text = get(API + "/projects?ids=" + URLEncoder.encode(json.toString(), StandardCharsets.UTF_8));
        return Json.arr(Json.parse(text));
    }

    /** 按 SHA-1 查 Modrinth 上的最新版本，没收录返回 null。 */
    public static String[] checkUpdateBySha1(String sha1) {
        try {
            String body = "{\"hashes\": [\"{h}\"], \"algorithm\": \"sha1\"}".replace("{h}", sha1.toLowerCase());
            String text = post(API + "/version_files", body);
            Map<String, Object> root = Json.obj(Json.parse(text));
            Map<String, Object> hit = Json.obj(root.get(sha1.toLowerCase()));
            if (hit.isEmpty()) return null;
            String projectId = Json.str(hit, "project_id", "");
            String versionNumber = Json.str(hit, "version_number", "");
            String fileName = "", url = "", hash = "";
            for (Object f : Json.arr(hit.get("files"))) {
                Map<String, Object> fm = Json.obj(f);
                if (Json.bool(fm, "primary", false) || url.isEmpty()) {
                    fileName = Json.str(fm, "filename", fileName);
                    url = Json.str(fm, "url", url);
                    hash = Json.str(Json.obj(fm.get("hashes")), "sha1", hash).toLowerCase();
                }
            }
            if (projectId.isEmpty() || url.isEmpty()) return null;
            return new String[]{projectId, versionNumber, fileName, url, hash};
        } catch (Exception e) {
            return null;
        }
    }

    /** 通用 JSON POST。 */
    public static String post(String url, String body) throws IOException {
        // 走共享连接池，省掉重复握手
        return Http.post(url, body, Map.of("Accept", "application/json"));
    }

    /** 下载指定 URL 到目标文件。 */
    public static void downloadUrl(Tasks.Task task, String url, Path target, String sha1) throws IOException {
        // 走 Fetch，边下边校验
        Fetch.file(task, url, target, sha1, 0, 0.98);
        if (task != null) task.remainingFiles = 1;
    }

    /** 走 Http，共享连接池。 */
    private static String get(String url) throws IOException {
        return Http.get(url);
    }
}
