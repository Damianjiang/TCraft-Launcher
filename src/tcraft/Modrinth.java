package tcraft;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Modrinth {

    private static final String API = "https://api.modrinth.com/v2";

    private Modrinth() {
    }

    public static List<Object> search(String query, String projectType, int limit) throws IOException {
        return search(query, projectType, limit, null, null, null, 0, null);
    }

    // 搜索结果短缓存（90s）：切分类/翻页来回点不重复打 API
    private static final Map<String, Object[]> SEARCH_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) {
                    return size() > 32;
                }
            });
    private static final long SEARCH_TTL_MS = 90_000L;

    public static List<Object> search(String query, String projectType, int limit,
                                      List<String> versions, List<String> loaders,
                                      String category, int offset, String index) throws IOException {
        boolean hasQuery = query != null && !query.isEmpty();
        // 这些值直接拼进 JSON 数组，先剥双引号，截不断 JSON
        StringBuilder facets = new StringBuilder("[[\"project_type:")
                .append(facet(projectType)).append("\"]");
        if (versions != null && !versions.isEmpty()) {
            facets.append(",[");
            for (int i = 0; i < versions.size(); i++) {
                if (i > 0) facets.append(",");
                facets.append("\"versions:").append(facet(versions.get(i))).append("\"");
            }
            facets.append("]");
        }
        if (loaders != null && !loaders.isEmpty()) {
            facets.append(",[");
            for (int i = 0; i < loaders.size(); i++) {
                if (i > 0) facets.append(",");
                facets.append("\"loaders:").append(facet(loaders.get(i))).append("\"");
            }
            facets.append("]");
        }
        if (category != null && !category.isEmpty()) {
            facets.append(",[\"categories:").append(facet(category)).append("\"]");
        }
        facets.append("]");
        StringBuilder url = new StringBuilder(API + "/search?limit=").append(limit)
                .append("&offset=").append(Math.max(0, offset))
                .append("&index=").append(index != null && !index.isEmpty() ? index : (hasQuery ? "relevance" : "downloads"))
                .append("&facets=").append(URLEncoder.encode(facets.toString(), StandardCharsets.UTF_8));
        if (hasQuery) url.append("&query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        String cacheKey = url.toString();
        Object[] hit = SEARCH_CACHE.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - (Long) hit[1] < SEARCH_TTL_MS) {
            @SuppressWarnings("unchecked")
            List<Object> cachedList = (List<Object>) hit[0];
            return cachedList;
        }
        String text = get(url.toString());
        List<Object> hits = Json.arr(Json.obj(Json.parse(text)).get("hits"));
        SEARCH_CACHE.put(cacheKey, new Object[]{hits, System.currentTimeMillis()});
        return hits;
    }

    private static final int CACHE_LIMIT = 200;

    // 无界缓存在长会话里会吃内存，用访问序 LRU 逐出最旧
    private static final Map<String, List<Object>> VERSIONS_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Object>> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });
    private static final Map<String, Map<String, Object>> PROJECT_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    private static String facet(String v) {
        return v == null ? "" : v.replace("\"", "").trim();
    }

    public static List<Object> projectVersions(String projectId) throws IOException {
        List<Object> cached = VERSIONS_CACHE.get(projectId);
        if (cached != null) return cached;
        String text = get(API + "/project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8)
                + "/version?include_changelog=false");
        List<Object> arr = Json.arr(Json.parse(text));
        VERSIONS_CACHE.put(projectId, arr);
        return arr;
    }

    public static Map<String, Object> projectInfo(String projectId) throws IOException {
        Map<String, Object> cached = PROJECT_CACHE.get(projectId);
        if (cached != null) return cached;
        String text = get(API + "/project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8));
        Map<String, Object> obj = Json.obj(Json.parse(text));
        PROJECT_CACHE.put(projectId, obj);
        return obj;
    }

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

    // null 仅=未收录，网络/解析错误要抛出
    public static String[] checkUpdateBySha1(String sha1) {
        String text;
        try {
            String body = "{\"hashes\": [\"{h}\"], \"algorithm\": \"sha1\"}".replace("{h}", sha1.toLowerCase());
            text = post(API + "/version_files", body);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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
    }

    public static String post(String url, String body) throws IOException {
        return Http.post(url, body, Map.of("Accept", "application/json"));
    }

    public static void downloadUrl(Tasks.Task task, String url, Path target, String sha1) throws IOException {
        // sha1 边下边校验交给 Fetch
        Fetch.file(task, url, target, sha1, 0, 0.98);
        if (task != null) task.remainingFiles = 1;
    }

    private static String get(String url) throws IOException {
        return Http.get(url);
    }
}
