package tcraft;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CurseForge {

    private static final String API = "https://api.curseforge.com/v1";
    // Keyless requests use the public mirror because CurseForge rejects anonymous
    // calls. A configured key always takes priority and talks to the official API.
    private static final String PUBLIC_API = Mirrors.PRESET + "/curseforge/v1";
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

    /// Returns the CurseForge API root for the current configuration.
    ///
    /// Official requests need a user-provided key; without one the public
    /// mirror is used so browsing and installs keep working out of the box.
    private static String base() {
        return apiKey().isEmpty() ? PUBLIC_API : API;
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

    private static String section(int classId) {
        return switch (classId) {
            case 12 -> "resourcepacks";
            case 17 -> "worlds";
            case 4471 -> "modpacks";
            case 6552 -> "shaders";
            default -> "mods";
        };
    }

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
        return search(query, classId, limit, categoryId, offset, "", -1);
    }

    public static List<Project> search(String query, int classId, int limit, int categoryId, int offset,
                                       String gameVersion, int modLoaderType) throws IOException {
        String key = apiKey();
        StringBuilder url = new StringBuilder(base()).append("/mods/search?gameId=432&classId=").append(classId)
                .append("&sortField=2&sortOrder=desc&pageSize=").append(limit)
                .append("&offset=").append(Math.max(0, offset));
        if (categoryId > 0) url.append("&categoryId=").append(categoryId);
        if (gameVersion != null && !gameVersion.isEmpty()) {
            url.append("&gameVersion=").append(URLEncoder.encode(gameVersion, StandardCharsets.UTF_8));
        }
        if (modLoaderType > 0) url.append("&modLoaderType=").append(modLoaderType);
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
                p.website = "https://www.curseforge.com/minecraft/" + section(p.classId) + "/" + p.slug;
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
        Map<String, Object> root = Json.obj(Json.parse(get(base() + "/mods/" + id + "/files?pageSize=10000", key)));
        List<Object> data = Json.arr(root.get("data"));
        FILES_CACHE.put(id, data);
        return data;
    }

    public static Project projectInfo(long id) throws IOException {
        Project cached = INFO_CACHE.get(id);
        if (cached != null) return cached;
        String key = apiKey();
        Map<String, Object> root = Json.obj(Json.parse(get(base() + "/mods/" + id, key)));
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
            p.website = "https://www.curseforge.com/minecraft/" + section(p.classId) + "/" + p.slug;
        }
        INFO_CACHE.put(id, p);
        return p;
    }

    public static List<Object> filesByIds(List<Long> ids) throws IOException {
        return filesByIds(ids, Map.of());
    }

    /// Resolves file metadata by id. The official batch endpoint needs a key,
    /// while the public mirror only exposes files per project.
    ///
    /// @param ids file ids from a CurseForge manifest
    /// @param projectIds file id to project id mapping, required for keyless mode
    /// @return matching file objects; unknown ids are omitted
    public static List<Object> filesByIds(List<Long> ids, Map<Long, Long> projectIds) throws IOException {
        String key = apiKey();
        if (!key.isEmpty()) {
            StringBuilder body = new StringBuilder("{\"fileIds\": [");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) body.append(",");
                body.append(ids.get(i));
            }
            body.append("]}");
            try {
                Map<String, Object> root = Json.obj(Json.parse(post(API + "/mods/files", body.toString(), key)));
                return Json.arr(root.get("data"));
            } catch (IOException e) {
                if (projectIds.isEmpty()) throw e;
                // An official outage should not block an install that can be
                // completed through the public mirror.
            }
        }
        return filesByProject(ids, projectIds);
    }

    private static List<Object> filesByProject(List<Long> ids, Map<Long, Long> projectIds) throws IOException {
        java.util.Set<Long> wanted = new java.util.HashSet<>(ids);
        Map<Long, java.util.List<Long>> grouped = new java.util.LinkedHashMap<>();
        for (Long fileId : ids) {
            Long projectId = projectIds.get(fileId);
            if (projectId == null || projectId <= 0) continue;
            grouped.computeIfAbsent(projectId, ignored -> new java.util.ArrayList<>()).add(fileId);
        }
        List<Object> out = new ArrayList<>();
        IOException last = null;
        for (Map.Entry<Long, java.util.List<Long>> entry : grouped.entrySet()) {
            try {
                for (Object o : projectFiles(entry.getKey())) {
                    long id = (long) Json.num(Json.obj(o), "id", -1);
                    if (wanted.contains(id)) out.add(o);
                }
            } catch (IOException e) {
                last = e;
            }
        }
        if (out.isEmpty() && last != null) throw last;
        return out;
    }

    private static String post(String url, String body, String key) throws IOException {
        for (String candidate : Mirrors.apiCandidates(url)) {
            try {
                return Http.postOnce(candidate, body, headers(candidate, key), candidate.equals(url) ? 4 : 10);
            } catch (IOException ignored) {
            }
        }
        return Http.post(url, body, Map.of("Accept", "application/json", "x-api-key", key));
    }

    private static String get(String url, String key) throws IOException {
        IOException last = null;
        // The public mirror occasionally returns a transient 500 for the first
        // request after a cold start; a short retry avoids a false "offline".
        for (int attempt = 0; attempt < 3; attempt++) {
            for (String candidate : Mirrors.apiCandidates(url)) {
                try {
                    return Http.getOnce(candidate, headers(candidate, key), candidate.equals(url) ? 4 : 10);
                } catch (IOException e) {
                    last = e;
                }
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(300L * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (last != null) throw last;
        return Http.get(url, headers(url, key));
    }

    // API Key 只发给 CurseForge 官方域 镜像不接触个人凭据
    private static Map<String, String> headers(String url, String key) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        out.put("Accept", "application/json");
        try {
            String host = java.net.URI.create(url).getHost();
            if (host != null && host.equalsIgnoreCase("api.curseforge.com")) out.put("x-api-key", key);
        } catch (Exception ignored) {
        }
        return out;
    }
}
