package tcraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 外置登录：Yggdrasil 协议，配合 authlib-injector 使用。 */
public final class Yggdrasil {

    /** 认证服务器。apiRoot 末尾一定带斜杠。 */
    public static final class Server {
        public final String apiRoot;
        public final String name;

        Server(String apiRoot, String name) {
            this.apiRoot = apiRoot;
            this.name = name == null ? "" : name;
        }

        public String display() {
            return name.isEmpty() ? apiRoot : name;
        }
    }

    /** 游戏角色。id 可能是 32 位无连字符，也可能是带连字符的 UUID。 */
    public static final class Profile {
        public final String id;
        public final String name;

        Profile(String id, String name) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
        }
    }

    /** 登录或刷新的结果。 */
    public static final class Session {
        public String accessToken = "";
        public String clientToken = "";
        public Profile selected;
        public final List<Profile> available = new ArrayList<>();
    }

    /** 服务器拒绝，message 可以直接显示给用户。 */
    public static final class AuthException extends Exception {
        private static final long serialVersionUID = 1L;
        public final int status;

        AuthException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private Yggdrasil() {
    }

    /** 解析服务器根地址与 API 根路径，优先采用响应头给出的真实地址。 */
    public static Server locate(String input) throws IOException, AuthException {
        String url = input == null ? "" : input.trim();
        if (url.isEmpty()) throw new AuthException(0, "请填写认证服务器地址");
        if (!url.contains("://")) url = "https://" + url;
        try {
            URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new AuthException(0, "认证服务器地址格式不正确：" + url);
        }

        HttpResponse<String> resp = request("GET", url, null);
        resp = followRedirect(resp, url);
        String moved = resp.headers().firstValue("x-authlib-injector-api-location").orElse("");
        if (!moved.isEmpty()) {
            String absolute = URI.create(url).resolve(moved).toString();
            if (!sameUrl(absolute, url)) {
                url = absolute;
                resp = request("GET", absolute, null);
                resp = followRedirect(resp, absolute);
            }
        }

        Map<String, Object> root = parseObject(resp.body(), resp.statusCode());
        boolean looksServer = root.containsKey("meta") || root.containsKey("skinDomains")
                || root.containsKey("signaturePublickey");
        if (!looksServer) throw new AuthException(resp.statusCode(), "这不是 Yggdrasil API 地址：" + url);

        String name = Json.str(Json.obj(root.get("meta")), "serverName", "");
        if (!url.endsWith("/")) url += "/";
        return new Server(url, name);
    }

    /** 用户名密码登录。clientToken 为空就生成一个。 */
    public static Session authenticate(Server server, String username, String password, String clientToken)
            throws IOException, AuthException {
        requireSecure(server);
        String token = clientToken == null || clientToken.isEmpty()
                ? UUID.randomUUID().toString() : clientToken;
        String body = "{\"agent\":{\"name\":\"Minecraft\",\"version\":1},"
                + "\"username\":" + Json.quote(username) + ","
                + "\"password\":" + Json.quote(password) + ","
                + "\"clientToken\":" + Json.quote(token) + ","
                + "\"requestUser\":true}";
        return send(server, "authserver/authenticate", body, token);
    }

    /** 用 accessToken 换新 token。选中角色时带上 selectedProfile。 */
    public static Session refresh(Server server, String accessToken, String clientToken, Profile select)
            throws IOException, AuthException {
        StringBuilder body = new StringBuilder("{\"accessToken\":")
                .append(Json.quote(accessToken == null ? "" : accessToken))
                .append(",\"clientToken\":").append(Json.quote(clientToken == null ? "" : clientToken))
                .append(",\"requestUser\":true");
        if (select != null && !select.id.isEmpty()) {
            body.append(",\"selectedProfile\":{\"id\":").append(Json.quote(compact(select.id)))
                    .append(",\"name\":").append(Json.quote(select.name)).append('}');
        }
        body.append('}');
        return send(server, "authserver/refresh", body.toString(), clientToken);
    }

    /** token 还有效吗。网络异常会抛 IOException，交给调用方提示。 */
    public static boolean validate(Server server, String accessToken, String clientToken) throws IOException {
        if (accessToken == null || accessToken.isEmpty()) return false;
        String body = "{\"accessToken\":" + Json.quote(accessToken)
                + ",\"clientToken\":" + Json.quote(clientToken == null ? "" : clientToken) + "}";
        HttpResponse<String> resp = request("POST", server.apiRoot + "authserver/validate", body);
        return resp.statusCode() == 200 || resp.statusCode() == 204;
    }

    /** 启动前确保 token 可用：失效则刷新，并把新 clientToken 写回账户。 */
    public static void ensureFresh(Data.Account account) throws IOException, AuthException {
        if (account == null || account.server == null || account.server.isEmpty()) return;
        Server server = new Server(account.server.endsWith("/") ? account.server : account.server + "/", "");
        String client = account.clientToken == null ? "" : account.clientToken;
        if (client.isEmpty()) {
            client = UUID.randomUUID().toString();
            account.clientToken = client;
        }
        if (account.accessToken != null && !account.accessToken.isEmpty()
                && validate(server, account.accessToken, client)) return;

        Profile self = new Profile(compact(account.uuid), account.name);
        Session session = refresh(server, account.accessToken, client, self);
        account.accessToken = session.accessToken;
        if (!session.clientToken.isEmpty()) account.clientToken = session.clientToken;
        Data.get().save();
    }

    private static Session send(Server server, String path, String body, String clientToken)
            throws IOException, AuthException {
        HttpResponse<String> resp = request("POST", server.apiRoot + path, body);
        Map<String, Object> m = parseObject(resp.body(), resp.statusCode());
        String error = Json.str(m, "error", "");
        if (resp.statusCode() != 200 || !error.isEmpty()) {
            throw new AuthException(resp.statusCode(),
                    friendly(error, Json.str(m, "errorMessage", ""), resp.statusCode()));
        }
        Session out = new Session();
        out.accessToken = Json.str(m, "accessToken", "");
        String returned = Json.str(m, "clientToken", "");
        out.clientToken = returned.isEmpty() ? (clientToken == null ? "" : clientToken) : returned;
        Map<String, Object> selected = Json.obj(m.get("selectedProfile"));
        if (!selected.isEmpty()) out.selected = profile(selected);
        for (Object o : Json.arr(m.get("availableProfiles"))) {
            Map<String, Object> p = Json.obj(o);
            if (!p.isEmpty()) out.available.add(profile(p));
        }
        if (out.accessToken.isEmpty()) throw new AuthException(resp.statusCode(), "服务器没有返回 accessToken");
        return out;
    }

    private static Profile profile(Map<String, Object> m) {
        return new Profile(Json.str(m, "id", ""), Json.str(m, "name", ""));
    }

    /** 密码只能发给 HTTPS 或本机地址，避免明文传输。 */
    private static void requireSecure(Server server) throws AuthException {
        try {
            URI uri = URI.create(server.apiRoot);
            if ("https".equalsIgnoreCase(uri.getScheme())) return;
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host)) return;
        } catch (Exception ignored) {
        }
        throw new AuthException(0, Lang.t("认证服务器不是 HTTPS 地址，密码会明文传输，请改用 https:// 开头的地址"));
    }

    /** 把服务器的英文错误翻成一句能看懂的话。 */
    private static String friendly(String error, String remote, int status) {
        String text = remote == null ? "" : remote.trim();
        String low = text.toLowerCase();
        if (low.contains("invalid credentials") || low.contains("invalid username or password")) {
            return "用户名或密码错误";
        }
        if (low.contains("invalid token") || low.contains("expired")) {
            return "登录已过期，请重新登录";
        }
        if (!text.isEmpty()) return text;
        if (status == 401 || status == 403) return "用户名或密码错误，或登录已过期";
        if ("ForbiddenOperationException".equals(error)) return "服务器拒绝了这次登录，请检查账号状态";
        return "认证服务器返回 HTTP " + status;
    }

    private static HttpResponse<String> request(String method, String url, String body) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "TCraftLauncher")
                .header("Accept", "application/json")
                .header("Accept-Language", "zh-CN");
        if (body == null) {
            b.GET();
        } else {
            b.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        try {
            return Http.client().send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    /** 跟随一次 301/302/307/308，http 地址写到 https 时用得上。 */
    private static HttpResponse<String> followRedirect(HttpResponse<String> resp, String url) throws IOException {
        int code = resp.statusCode();
        if (code != 301 && code != 302 && code != 307 && code != 308) return resp;
        String location = resp.headers().firstValue("location").orElse("");
        if (location.isEmpty()) return resp;
        String next = URI.create(url).resolve(location).toString();
        return request("GET", next, null);
    }

    private static Map<String, Object> parseObject(String text, int status) throws AuthException {
        String body = text == null ? "" : text.trim();
        if (!body.startsWith("{")) {
            throw new AuthException(status, "服务器返回的不是 JSON，请检查地址是否为 Yggdrasil API");
        }
        try {
            return Json.obj(Json.parse(body));
        } catch (Exception e) {
            throw new AuthException(status, "服务器返回的 JSON 无法解析");
        }
    }

    private static boolean sameUrl(String a, String b) {
        return stripSlash(a).equalsIgnoreCase(stripSlash(b));
    }

    private static String stripSlash(String s) {
        String out = s == null ? "" : s.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    static String compact(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "");
    }
}
