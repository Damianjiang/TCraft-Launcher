package tcraft;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 微软账户登录：Xbox Live、XSTS 到 Minecraft 服务。 */
public final class MsAuth {

    /** 构建时注入的 client id，开源版为空。 */
    private static final String CLIENT_ID = Build.MICROSOFT_ID;
    private static final String SCOPE = "XboxLive.signin offline_access";

    private MsAuth() {
    }

    public static class Result {
        public String accessToken = "";
        public String refreshToken = "";
        public String uuid = "";
        public String name = "";
        public String skinUrl = "";
        public String capeUrl = "";
    }

    /** 桌面应用的固定回调地址，手动粘贴流程用。 */
    private static final String REDIRECT = "https://login.live.com/oauth20_desktop.srf";

    /** 这个构建里有没有微软登录凭据。 */
    public static boolean available() {
        return !CLIENT_ID.isEmpty();
    }

    /** 手动粘贴流程的回调地址。 */
    public static String redirectUri() {
        return REDIRECT;
    }

    /** 授权页地址。client id 只注册了 oauth20_desktop.srf。 */
    public static String authorizeUrl() {
        if (CLIENT_ID.isEmpty()) return "";
        return "https://login.live.com/oauth20_authorize.srf"
                + "?client_id=" + CLIENT_ID
                + "&response_type=code"
                + "&redirect_uri=" + enc(REDIRECT)
                + "&scope=" + enc(SCOPE)
                + "&prompt=select_account";
    }

    /** 从回调地址里提取 code。 */
    public static String parseCode(String pasted) {
        if (pasted == null) return "";
        String s = pasted.trim();
        int i = s.indexOf("code=");
        if (i < 0) return s;
        s = s.substring(i + 5);
        for (char c : new char[]{'&', '#'}) {
            int j = s.indexOf(c);
            if (j >= 0) s = s.substring(0, j);
        }
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /** 授权码换令牌，再走 Xbox Live 和 XSTS。 */
    public static Result loginWithCode(String code) throws Exception {
        if (code == null || code.isEmpty()) throw new Exception("先粘贴登录后浏览器地址栏里的整条链接");
        String body = "client_id=" + CLIENT_ID
                + "&code=" + enc(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + enc(REDIRECT)
                + "&scope=" + enc(SCOPE);
        Map<String, Object> m;
        try {
            m = Json.obj(Json.parse(post("https://login.live.com/oauth20_token.srf", body, null)));
        } catch (HttpError e) {
            Map<String, Object> err = e.body.isEmpty() ? Map.of() : Json.obj(Json.parse(e.body));
            String why = Json.str(err, "error_description", "");
            throw new Exception("换取令牌失败：" + (why.isEmpty() ? e.getMessage() : why));
        }
        String token = Json.str(m, "access_token", "");
        if (token.isEmpty()) throw new Exception("微软没有返回访问令牌");
        return loginMinecraft(token, Json.str(m, "refresh_token", ""));
    }

    private static Result loginMinecraft(String msToken, String refreshToken) throws Exception {
        // Xbox Live
        String xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"" + esc("d=" + msToken) + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
        Map<String, Object> xbl = Json.obj(Json.parse(post(
                "https://user.auth.xboxlive.com/user/authenticate", xblBody, "application/json")));
        String xblToken = Json.str(xbl, "Token", "");
        String uhs = displayClaim(xbl, "uhs");
        if (xblToken.isEmpty() || uhs.isEmpty()) {
            throw new Exception("Xbox Live 验证失败：账户可能未创建 Xbox 档案");
        }

        // XSTS
        String xstsBody = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                + esc(xblToken) + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\","
                + "\"TokenType\":\"JWT\"}";
        Map<String, Object> xsts;
        try {
            xsts = Json.obj(Json.parse(post("https://xsts.auth.xboxlive.com/xsts/authorize",
                    xstsBody, "application/json")));
        } catch (HttpError e) {
            Map<String, Object> m = e.body.isEmpty() ? Map.of() : Json.obj(Json.parse(e.body));
            String xerr = Json.str(m, "XErr", "");
            switch (xerr) {
                case "2148916233" -> throw new Exception("该微软账户还没有 Xbox 档案，请先在 xbox.com 创建");
                case "2148916235" -> throw new Exception("该账户所在地区不支持 Xbox Live");
                case "2148916238" -> throw new Exception("未成年人账户需要先加入家庭组");
                default -> throw new Exception("XSTS 授权失败" + (xerr.isEmpty() ? "" : "（" + xerr + "）"));
            }
        }
        String xstsToken = Json.str(xsts, "Token", "");
        uhs = displayClaim(xsts, "uhs");
        if (xstsToken.isEmpty()) throw new Exception("XSTS 授权失败");

        // Minecraft 服务
        String mcBody = "{\"identityToken\":\"XBL3.0 x=" + esc(uhs) + ";" + esc(xstsToken) + "\"}";
        Map<String, Object> mc = Json.obj(Json.parse(post(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                mcBody, "application/json")));
        String token = Json.str(mc, "access_token", "");
        if (token.isEmpty()) throw new Exception("Minecraft 登录失败：该账户可能未拥有游戏");

        Result r = new Result();
        r.accessToken = token;
        r.refreshToken = refreshToken;

        // 档案，含皮肤
        try {
            Map<String, Object> profile = Json.obj(Json.parse(get(
                    "https://api.minecraftservices.com/minecraft/profile", token)));
            r.uuid = formatUuid(Json.str(profile, "id", ""));
            r.name = Json.str(profile, "name", "");
            for (Object o : Json.arr(profile.get("skins"))) {
                Map<String, Object> skin = Json.obj(o);
                if ("ACTIVE".equalsIgnoreCase(Json.str(skin, "state", ""))) {
                    r.skinUrl = Json.str(skin, "url", "");
                }
            }
            for (Object o : Json.arr(profile.get("capes"))) {
                Map<String, Object> cape = Json.obj(o);
                if ("ACTIVE".equalsIgnoreCase(Json.str(cape, "state", ""))) {
                    r.capeUrl = Json.str(cape, "url", "");
                }
            }
        } catch (Exception ignored) {
            // 档案接口可能 404，token 仍然可用
        }
        return r;
    }

    /** 下载皮肤，返回头像能用的相对路径。 */
    public static String saveSkin(Result r, String gameDir) {
        if (r.skinUrl == null || r.skinUrl.isEmpty()) return "";
        try {
            java.nio.file.Path dir = Theme.res("images/Skins");
            java.nio.file.Files.createDirectories(dir);
            String file = (r.name.isEmpty() ? "skin" : r.name) + ".png";
            java.nio.file.Files.write(dir.resolve(file), download(r.skinUrl));
            return "Skins/" + file;
        } catch (Exception e) {
            return "";
        }
    }

    public interface Cancel {
        boolean cancelled();
    }

    private static String displayClaim(Map<String, Object> token, String key) {
        Map<String, Object> claims = Json.obj(token.get("DisplayClaims"));
        for (Object o : Json.arr(claims.get("xui"))) {
            Map<String, Object> ui = Json.obj(o);
            String v = Json.str(ui, key, "");
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

    private static final class HttpError extends Exception {
        private static final long serialVersionUID = 1L;
        final String body;
        final int code;

        HttpError(int code, String body) {
            super("HTTP " + code + " " + body);
            this.code = code;
            this.body = body;
        }
    }

    private static String post(String url, String body, String contentType) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", contentType == null
                ? "application/x-www-form-urlencoded" : contentType);
        c.setRequestProperty("User-Agent", "TCraftLauncher-JavaFX/1.3.7");
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String text = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new HttpError(code, text);
        return text;
    }

    private static String get(String url, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setRequestProperty("User-Agent", "TCraftLauncher-JavaFX/1.3.7");
        int code = c.getResponseCode();
        String text = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new HttpError(code, text);
        return text;
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "TCraftLauncher-JavaFX/1.3.7");
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
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else {
                    new ProcessBuilder("xdg-open", url).start();
                }
            } catch (Exception fallback) {
                // 两条路都失败就报错
                Errors.error("无法打开浏览器完成微软登录", fallback);
            }
        }
    }
}
