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

    private static final Map<String, Object[]> SEARCH_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) {
                    return size() > 32;
                }
            });
    private static final long SEARCH_TTL_MS = 90_000L;

    public static void clearCache() {
        SEARCH_CACHE.clear();
    }

    public static List<Object> search(String query, String projectType, int limit,
                                      List<String> versions, List<String> loaders,
                                      String category, int offset, String index) throws IOException {
        boolean hasQuery = query != null && !query.isEmpty();
        // 拼 JSON 前先剥双引号
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
    private static final int UPDATE_CACHE_LIMIT = 300;
    private static final long UPDATE_TTL_MS = 600_000L;

    private static final Map<String, Object[]> UPDATE_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) {
                    return size() > UPDATE_CACHE_LIMIT;
                }
            });

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

    // null 仅表示未收录
    public static String[] checkUpdateBySha1(String sha1) {
        String key = sha1 == null ? "" : sha1.toLowerCase();
        Object[] cached = UPDATE_CACHE.get(key);
        if (cached != null && System.currentTimeMillis() - (Long) cached[1] < UPDATE_TTL_MS) {
            String[] value = (String[]) cached[0];
            return value.length == 0 ? null : value;
        }
        String text;
        try {
            String body = "{\"hashes\": [\"{h}\"], \"algorithm\": \"sha1\"}".replace("{h}", key);
            text = post(API + "/version_files", body);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Map<String, Object> root = Json.obj(Json.parse(text));
        Map<String, Object> hit = Json.obj(root.get(key));
        if (hit.isEmpty()) {
            UPDATE_CACHE.put(key, new Object[]{new String[0], System.currentTimeMillis()});
            return null;
        }
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
        String[] result = projectId.isEmpty() || url.isEmpty()
                ? null : new String[]{projectId, versionNumber, fileName, url, hash};
        UPDATE_CACHE.put(key, new Object[]{result == null ? new String[0] : result, System.currentTimeMillis()});
        return result;
    }

    public static String post(String url, String body) throws IOException {
        Map<String, String> headers = Map.of("Accept", "application/json");
        for (String candidate : Mirrors.apiCandidates(url)) {
            try {
                return Http.postOnce(candidate, body, headers, candidate.equals(url) ? 4 : 10);
            } catch (IOException ignored) {
            }
        }
        return Http.post(url, body, headers);
    }

    public static void downloadUrl(Tasks.Task task, String url, Path target, String sha1) throws IOException {
        downloadUrl(task, url, target, sha1, true);
    }

    static void downloadUrl(Tasks.Task task, String url, Path target, String sha1, boolean report) throws IOException {
        // 资源镜像同样走候选 并按域名测速排序
        Fetch.fileCandidates(task, Sources.rank(Mirrors.images(url)), target, sha1,
                0, report ? 0.98 : 0, report);
        if (report && task != null) task.remainingFiles = 1;
    }

    private static String get(String url) throws IOException {
        for (String candidate : Mirrors.apiCandidates(url)) {
            try {
                return Http.getOnce(candidate, candidate.equals(url) ? 4 : 10);
            } catch (IOException ignored) {
            }
        }
        return Http.get(url);
    }
}
