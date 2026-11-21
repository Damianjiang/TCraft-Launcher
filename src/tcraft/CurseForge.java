package tcraft;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        public int classId;
        public String name = "";
        public String slug = "";
        public String summary = "";
        public String icon = "";
        public String website = "";
        public long downloads;
    }

    private static final int CACHE_LIMIT = 200;

    private static final Map<Long, List<Object>> FILES_CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<Object>> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });
    private static final Map<Long, Project> INFO_CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Project> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    private static final Map<String, Object[]> SEARCH_CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object[]> eldest) {
                    return size() > 32;
                }
            });

    public static void clearCache() {
        FILES_CACHE.clear();
        INFO_CACHE.clear();
        SEARCH_CACHE.clear();
    }

    private static final long SEARCH_TTL_MS = 90_000L;

    public static List<Project> search(String query, int classId, int limit) throws IOException {
        return search(query, classId, limit, 0, 0);
    }

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
        String cacheKey = url.toString();
        Object[] hit = SEARCH_CACHE.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - (Long) hit[1] < SEARCH_TTL_MS) {
            @SuppressWarnings("unchecked")
            List<Project> cachedList = (List<Project>) hit[0];
            return cachedList;
        }
        Map<String, Object> root = Json.obj(Json.parse(get(url.toString(), key)));
        List<Project> out = new ArrayList<>();
        for (Object o : Json.arr(root.get("data"))) {
            Map<String, Object> m = Json.obj(o);
            Project p = new Project();
            p.id = (long) Json.num(m, "id", 0);
            p.classId = (int) Json.num(m, "classId", 0);
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
        SEARCH_CACHE.put(cacheKey, new Object[]{out, System.currentTimeMillis()});
        return out;
    }

    public static List<Object> projectFiles(long id) throws IOException {
        List<Object> cached = FILES_CACHE.get(id);
        if (cached != null) return cached;
        String key = apiKey();
        if (key.isEmpty()) throw new IOException("尚未填写 CurseForge API Key");
        Map<String, Object> root = Json.obj(Json.parse(get(API + "/mods/" + id + "/files?pageSize=10000", key)));
        List<Object> data = Json.arr(root.get("data"));
        FILES_CACHE.put(id, data);
        return data;
    }

    public static Project projectInfo(long id) throws IOException {
        Project cached = INFO_CACHE.get(id);
        if (cached != null) return cached;
        String key = apiKey();
        if (key.isEmpty()) throw new IOException("尚未填写 CurseForge API Key");
        Map<String, Object> root = Json.obj(Json.parse(get(API + "/mods/" + id, key)));
        Map<String, Object> m = Json.obj(root.get("data"));
        Project p = new Project();
        p.id = (long) Json.num(m, "id", 0);
        p.classId = (int) Json.num(m, "classId", 0);
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
        INFO_CACHE.put(id, p);
        return p;
    }

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
        return Http.post(url, body, Map.of("Accept", "application/json", "x-api-key", key));
    }

    private static String get(String url, String key) throws IOException {
        Map<String, String> headers = Map.of("Accept", "application/json", "x-api-key", key);
        for (String candidate : Mirrors.apiCandidates(url)) {
            try {
                return Http.getOnce(candidate, headers, candidate.equals(url) ? 4 : 10);
            } catch (IOException ignored) {
            }
        }
        return Http.get(url, headers);
    }
}
