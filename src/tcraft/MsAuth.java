package tcraft;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** 微软账户登录：设备码流程 + Xbox Live、XSTS 到 Minecraft 服务。 */
public final class MsAuth {

    /** 构建时注入的 client id，开源版为空。 */
    private static final String CLIENT_ID = Build.MICROSOFT_ID;
    /** 16 位压缩形式走 Live Connect 旧接口，GUID 形式走 consumers v2。 */
    private static final boolean LEGACY = !CLIENT_ID.isEmpty() && !CLIENT_ID.contains("-");
    private static final String SCOPE = LEGACY
            ? "service::user.auth.xboxlive.com::MBI_SSL"
            : "XboxLive.signin offline_access";
    private static final String DEVICE_CODE_URL = LEGACY
            ? "https://login.live.com/oauth20_connect.srf"
            : "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = LEGACY
            ? "https://login.live.com/oauth20_token.srf"
            : "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String AGENT = "TCraftLauncher-JavaFX/" + Build.VERSION;
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_STORE_URL =
            "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String MC_LICENSE_URL =
            "https://api.minecraftservices.com/entitlements/license";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private MsAuth() {
    }

    /** 设备码登录第一步返回的信息。 */
    public static final class DeviceCode {
        public String deviceCode = "";
        public String userCode = "";
        public String verificationUri = "";
        public String message = "";
        public int interval = 5;
        public int expiresIn = 900;
    }

    public static final class Result {
        public String accessToken = "";
        public String refreshToken = "";
        public String uuid = "";
        public String name = "";
        public String skinUrl = "";
        public String capeUrl = "";
        /** Minecraft 令牌过期时间（毫秒），0 表示没拿到。 */
        public long expiresAt = 0;
    }

    /** 用户关闭登录窗口或取消流程。 */
    public static final class CancelledException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public interface Cancel {
        boolean cancelled();
    }

    /** 这个构建里有没有微软登录凭据。 */
    public static boolean available() {
        return !CLIENT_ID.isEmpty();
    }

    /** 请求设备码，界面拿到后展示给用户。 */
    public static DeviceCode requestDeviceCode() throws Exception {
        if (!available()) throw new Exception(Lang.t("这个版本没有内置微软登录凭据"));
        String body = "client_id=" + enc(CLIENT_ID) + "&scope=" + enc(SCOPE)
                + (LEGACY ? "&response_type=device_code" : "");
        Resp r = postForm(DEVICE_CODE_URL, body, 20000);
        if (r.code != 200) throw new Exception(Lang.tf("获取微软登录代码失败：%s", errorText(r)));
        Map<String, Object> m = parse(r.body);
        DeviceCode d = new DeviceCode();
        d.userCode = Json.str(m, "user_code", "");
        d.deviceCode = Json.str(m, "device_code", "");
        d.verificationUri = Json.str(m, "verification_uri", "");
        d.message = Json.str(m, "message", "");
        d.interval = Math.max(1, (int) Json.num(m, "interval", 5));
        d.expiresIn = Math.max(60, (int) Json.num(m, "expires_in", 900));
        if (d.userCode.isEmpty() || d.deviceCode.isEmpty()) {
            throw new Exception(Lang.t("微软没有返回设备代码，请稍后重试"));
        }
        if (d.verificationUri.isEmpty()) d.verificationUri = "https://www.microsoft.com/link";
        return d;
    }

    /** 轮询到用户完成授权，再走 Xbox 与 Minecraft 的令牌链。 */
    public static Result login(DeviceCode code, Cancel cancel, Consumer<String> stage) throws Exception {
        long deadline = System.currentTimeMillis()
                + Math.min(code.expiresIn, 900) * 1000L;
        int interval = Math.max(1, code.interval);
        String grant = LEGACY ? "device_code" : "urn:ietf:params:oauth:grant-type:device_code";
        while (true) {
            if (!sleep(interval * 1000L, cancel)) throw new CancelledException();
            if (System.currentTimeMillis() > deadline) {
                throw new Exception(Lang.t("登录代码已过期，请重新获取。"));
            }
            String body = "client_id=" + enc(CLIENT_ID)
                    + "&device_code=" + enc(code.deviceCode)
                    + "&grant_type=" + enc(grant);
            Resp r = postForm(TOKEN_URL, body, 15000);
            if (r.code == 200) {
                Map<String, Object> m = parse(r.body);
                String access = Json.str(m, "access_token", "");
                String refresh = Json.str(m, "refresh_token", "");
                if (access.isEmpty()) throw new Exception(Lang.t("微软没有返回访问令牌"));
                if (stage != null) stage.accept("正在验证 Xbox 账户…");
                return authenticate(access, refresh, stage);
            }
            Map<String, Object> err = parse(r.body);
            String e = Json.str(err, "error", "");
            String desc = Json.str(err, "error_description", "");
            if ("authorization_pending".equals(e)) continue;
            if ("slow_down".equals(e)) {
                interval += 5;
                continue;
            }
            if ("expired_token".equals(e)) throw new Exception(Lang.t("登录代码已过期，请重新获取。"));
            if ("authorization_declined".equals(e) || "access_denied".equals(e)) {
                throw new Exception(Lang.t("已取消微软账号授权。"));
            }
            throw new Exception(Lang.tf("微软登录失败：%s", desc.isEmpty() ? "HTTP " + r.code : desc));
        }
    }

    /** 用 refreshToken 换新的 Minecraft 令牌，令牌过期后启动前调用。 */
    public static Result refresh(String refreshToken) throws Exception {
        if (!available()) throw new Exception(Lang.t("这个版本没有内置微软登录凭据"));
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new Exception(Lang.t("没有可用的续期令牌，请重新登录微软账号"));
        }
        String body = "client_id=" + enc(CLIENT_ID)
                + "&refresh_token=" + enc(refreshToken)
                + "&grant_type=refresh_token"
                + "&scope=" + enc(SCOPE);
        Resp r = postForm(TOKEN_URL, body, 20000);
        if (r.code != 200) {
            Map<String, Object> m = parse(r.body);
            String why = Json.str(m, "error_description", "");
            throw new Exception(Lang.tf("微软登录已过期：%s", why.isEmpty() ? Lang.t("请重新登录") : why));
        }
        Map<String, Object> m = parse(r.body);
        String token = Json.str(m, "access_token", "");
        if (token.isEmpty()) throw new Exception(Lang.t("微软没有返回访问令牌"));
        String next = Json.str(m, "refresh_token", refreshToken);
        return authenticate(token, next.isEmpty() ? refreshToken : next, null);
    }

    /** Minecraft 令牌还有效吗。401 / 404 算失效，其他错误照常抛出。 */
    public static boolean validate(String accessToken) throws Exception {
        if (accessToken == null || accessToken.isEmpty()) return false;
        Resp r = get(MC_PROFILE_URL, accessToken, 15000);
        if (r.code == 200) return true;
        if (r.code == 401 || r.code == 404) return false;
        throw new Exception(Lang.tf("验证 Minecraft 令牌失败：HTTP %d", r.code));
    }

    /** 微软令牌换 Minecraft 令牌，并读档案。 */
    private static Result authenticate(String msToken, String refreshToken,
                                       Consumer<String> stage) throws Exception {
        if (stage != null) stage.accept("正在验证 Xbox 账户…");
        // Live Connect 的是 opaque RPS ticket，不能加 d= 前缀
        String rps = LEGACY ? msToken : "d=" + msToken;
        String xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"" + esc(rps) + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
        Resp xbl = postJson(XBL_URL, xblBody, 15000);
        if (xbl.code != 200) {
            throw new Exception(Lang.tf("Xbox Live 验证失败：%s", errorText(xbl)));
        }
        Map<String, Object> xblMap = parse(xbl.body);
        String xblToken = Json.str(xblMap, "Token", "");
        String uhs = displayClaim(xblMap, "uhs");
        if (xblToken.isEmpty() || uhs.isEmpty()) {
            throw new Exception(Lang.t("Xbox Live 验证失败：账户可能未创建 Xbox 档案"));
        }

        String xstsBody = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                + esc(xblToken) + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\","
                + "\"TokenType\":\"JWT\"}";
        Resp xsts = postJson(XSTS_URL, xstsBody, 15000);
        if (xsts.code != 200) {
            Map<String, Object> m = parse(xsts.body);
            String xerr = Json.str(m, "XErr", "");
            String why = switch (xerr) {
                case "2148916227" -> Lang.t("该账户已被 Xbox Live 封禁");
                case "2148916233" -> Lang.t("该微软账户还没有 Xbox 档案，请先在 xbox.com 创建");
                case "2148916235" -> Lang.t("该账户所在地区不支持 Xbox Live");
                case "2148916236", "2148916237" -> Lang.t("该账户需要先完成成人验证");
                case "2148916238" -> Lang.t("未成年人账户需要先加入家庭组");
                default -> "";
            };
            if (!why.isEmpty()) throw new Exception(why);
            throw new Exception(xerr.isEmpty()
                    ? Lang.tf("XSTS 授权失败：HTTP %d", xsts.code)
                    : Lang.tf("XSTS 授权失败（错误码 %s）", xerr));
        }
        Map<String, Object> xstsMap = parse(xsts.body);
        String xstsToken = Json.str(xstsMap, "Token", "");
        uhs = displayClaim(xstsMap, "uhs");
        if (xstsToken.isEmpty() || uhs.isEmpty()) throw new Exception(Lang.t("XSTS 授权失败"));

        if (stage != null) stage.accept("正在登录 Minecraft…");
        String mcBody = "{\"identityToken\":\"XBL3.0 x=" + esc(uhs) + ";" + esc(xstsToken) + "\"}";
        Resp mc = postJson(MC_LOGIN_URL, mcBody, 15000);
        if (mc.code != 200) {
            String why = mc.code == 403
                    ? Lang.t("该账户或启动器没有 Minecraft 登录权限")
                    : errorText(mc);
            throw new Exception(Lang.tf("Minecraft 登录失败：%s", why));
        }
        Map<String, Object> mcMap = parse(mc.body);
        String token = Json.str(mcMap, "access_token", "");
        if (token.isEmpty()) throw new Exception(Lang.t("Minecraft 登录失败：该账户可能未拥有游戏"));

        Result r = new Result();
        r.accessToken = token;
        r.refreshToken = refreshToken;
        long expiresIn = (long) Json.num(mcMap, "expires_in", 86400);
        r.expiresAt = System.currentTimeMillis() + Math.max(60, expiresIn - 60) * 1000L;

        Resp store = get(MC_STORE_URL, token, 15000);
        if (store.code != 200) {
            throw new Exception(Lang.tf("无法确认游戏所有权：%s", errorText(store)));
        }

        if (stage != null) stage.accept("正在获取游戏档案…");
        Resp profile = get(MC_PROFILE_URL, token, 15000);
        if (profile.code == 404) {
            throw new Exception(missingGameMessage(token));
        }
        if (profile.code != 200) {
            throw new Exception(Lang.tf("获取 Minecraft 档案失败：%s", errorText(profile)));
        }
        Map<String, Object> pm = parse(profile.body);
        r.uuid = formatUuid(Json.str(pm, "id", ""));
        r.name = Json.str(pm, "name", "");
        for (Object o : Json.arr(pm.get("skins"))) {
            Map<String, Object> skin = Json.obj(o);
            if ("ACTIVE".equalsIgnoreCase(Json.str(skin, "state", ""))) {
                r.skinUrl = Json.str(skin, "url", "");
            }
        }
        for (Object o : Json.arr(pm.get("capes"))) {
            Map<String, Object> cape = Json.obj(o);
            if ("ACTIVE".equalsIgnoreCase(Json.str(cape, "state", ""))) {
                r.capeUrl = Json.str(cape, "url", "");
            }
        }
        if (r.skinUrl.isEmpty() && !Json.arr(pm.get("skins")).isEmpty()) {
            r.skinUrl = Json.str(Json.obj(Json.arr(pm.get("skins")).get(0)), "url", "");
        }
        return r;
    }

    /** 档案 404 时区分「没买游戏」和「没有 Java 档案」。 */
    private static String missingGameMessage(String token) {
        try {
            Resp r = get(MC_LICENSE_URL, token, 15000);
            for (Object o : Json.arr(Json.obj(Json.parse(r.body)), "items")) {
                if ("game_minecraft".equals(Json.str(Json.obj(o), "name", ""))) {
                    return "该账户没有 Minecraft: Java Edition 档案";
                }
            }
        } catch (Exception ignored) {
        }
        return "该账户还没有购买 Minecraft: Java Edition";
    }

    /** 下载皮肤，返回头像能用的相对路径。 */
    public static String saveSkin(Result r) {
        if (r.skinUrl == null || r.skinUrl.isEmpty()) return "";
        try {
            java.nio.file.Path dir = Theme.skinDir();
            java.nio.file.Files.createDirectories(dir);
            String file = (r.name.isEmpty() ? "skin" : r.name) + ".png";
            java.nio.file.Files.write(dir.resolve(file), download(r.skinUrl));
            return "Skins/" + file;
        } catch (Exception e) {
            return "";
        }
    }

    /** 分段睡眠，方便窗口关闭后立刻退出轮询。 */
    private static boolean sleep(long ms, Cancel cancel) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (cancel != null && cancel.cancelled()) return false;
            if (Thread.currentThread().isInterrupted()) return false;
            try {
                Thread.sleep(Math.min(200, Math.max(1, end - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return cancel == null || !cancel.cancelled();
    }

    private static String displayClaim(Map<String, Object> token, String key) {
        Map<String, Object> claims = Json.obj(token.get("DisplayClaims"));
        for (Object o : Json.arr(claims.get("xui"))) {
            String v = Json.str(Json.obj(o), key, "");
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatUuid(String u) {
        String s = u == null ? "" : u.replace("-", "");
        if (s.length() != 32) return u == null ? "" : u;
        return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                + "-" + s.substring(16, 20) + "-" + s.substring(20);
    }

    private static Map<String, Object> parse(String text) {
        if (text == null || text.isEmpty()) return Map.of();
        try {
            return Json.obj(Json.parse(text));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String errorText(Resp r) {
        if (r == null) return "未知错误";
        Map<String, Object> m = parse(r.body);
        String why = Json.str(m, "error_description", "");
        if (why.isEmpty()) why = Json.str(m, "Message", "");
        if (why.isEmpty()) why = Json.str(m, "errorMessage", "");
        if (!why.isEmpty()) return why;
        String body = r.body == null ? "" : r.body.trim();
        if (!body.isEmpty()) {
            return body.length() > 160 ? body.substring(0, 160) : body;
        }
        return "HTTP " + r.code;
    }

    private static final class Resp {
        final int code;
        final String body;

        Resp(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static Resp postForm(String url, String body, int timeout) throws Exception {
        return post(url, body, "application/x-www-form-urlencoded", timeout);
    }

    private static Resp postJson(String url, String body, int timeout) throws Exception {
        return post(url, body, "application/json", timeout);
    }

    private static Resp post(String url, String body, String contentType, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", contentType);
        c.setRequestProperty("User-Agent", AGENT);
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        return new Resp(code, readAll(code >= 400 ? c.getErrorStream() : c.getInputStream()));
    }

    private static Resp get(String url, String bearer, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setRequestProperty("User-Agent", AGENT);
        int code = c.getResponseCode();
        return new Resp(code, readAll(code >= 400 ? c.getErrorStream() : c.getInputStream()));
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", AGENT);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            i.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** 打开系统浏览器，跨平台。 */
    public static void openBrowser(String url) {
        try {
            Shell.openUrl(url);
        } catch (Exception e) {
            try {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else {
                    new ProcessBuilder("xdg-open", url).start();
                }
            } catch (Exception fallback) {
                Errors.error("无法打开浏览器完成微软登录", fallback);
            }
        }
    }
}
