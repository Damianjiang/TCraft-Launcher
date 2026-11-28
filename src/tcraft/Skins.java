package tcraft;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/// Skin storage and validation. Third-party skins are resolved through the
/// Yggdrasil profile first, then the site's public CustomSkinAPI when the
/// profile carries no signed texture property.
public final class Skins {

    private Skins() {
    }

    private static String safeName(String accountName) {
        String safe = accountName == null || accountName.isEmpty() ? "skin" : accountName;
        for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
        return safe;
    }

    public static String save(String accountName, byte[] png) throws IOException {
        int[] size = pngSize(png);
        if (size == null) throw new IOException(Lang.t("皮肤文件不是有效的 PNG 图片"));
        int w = size[0], h = size[1];
        // 解码前先拒超大 PNG
        if (w < 64 || w % 64 != 0 || (h != w && h != w / 2) || (long) w * h > 2048L * 2048L) {
            throw new IOException(Lang.tf("皮肤图片尺寸无效（%d×%d，应为 64×32、64×64 或它们的整数倍）", w, h));
        }
        String safe = safeName(accountName);
        Path dir = Theme.skinDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(safe + ".png");
        Path temp = dir.resolve(safe + ".png.tmp");
        try {
            Files.write(temp, png);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return "Skins/" + safe + ".png";
    }

    public static String download(String accountName, String url) throws IOException {
        // Hash the texture URL so equal role names on different skin sites
        // cannot share one cached file.
        return save(accountName + "-" + textureHash(url), Http.getBytes(url));
    }

    private static String textureHash(String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((url == null ? "" : url).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(12);
            for (int i = 0; i < 6; i++) out.append(String.format(java.util.Locale.ROOT, "%02x", digest[i]));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(url == null ? 0 : url.hashCode());
        }
    }

    /// Resolves and stores the skin for an external Yggdrasil account.
    ///
    /// The signed `textures` property is not present in every authlib-injector
    /// response, and some skin sites omit it from the session profile entirely.
    /// A public CustomSkinAPI lookup is used as the fallback so a valid profile
    /// still gets its uploaded skin instead of an empty 3D preview.
    ///
    /// @param server the account's authentication server
    /// @param profile the selected game profile
    /// @param accountName the local file name stem for the downloaded skin
    /// @return the `Skins/` resource path
    /// @throws IOException when neither source yields a valid skin PNG
    public static String fetchThirdParty(Yggdrasil.Server server, Yggdrasil.Profile profile,
                                         String accountName) throws IOException {
        IOException first = null;
        if (server != null && profile != null) {
            try {
                String url = Yggdrasil.skinUrlOf(server, profile);
                if (url != null && !url.isBlank()) return download(accountName, url);
            } catch (Exception e) {
                first = e instanceof IOException io ? io : new IOException(e.getMessage(), e);
            }
        }
        if (profile != null && !profile.name.isBlank()) {
            try {
                String url = cslSkinUrl(server, profile.name);
                if (url != null && !url.isBlank()) return download(accountName, url);
            } catch (Exception e) {
                if (first == null) {
                    first = e instanceof IOException io ? io : new IOException(e.getMessage(), e);
                }
            }
        }
        throw first != null ? first : new IOException(Lang.t("服务器没有返回该账户的皮肤"));
    }

    /// Reads the public CustomSkinAPI profile for a role name.
    private static String cslSkinUrl(Yggdrasil.Server server, String roleName) throws IOException {
        String site = siteRoot(server);
        if (site.isEmpty()) return null;
        String csl = site.toLowerCase(java.util.Locale.ROOT).endsWith("/csl") ? site : site + "/csl";
        String role = URLEncoder.encode(roleName, StandardCharsets.UTF_8).replace("+", "%20");
        String body = Http.get(csl + "/" + role + ".json");
        if (body == null || body.isBlank()) return null;
        Map<String, Object> json = Json.obj(Json.parse(body));
        if (Json.str(json, "username", "").isEmpty()) return null;

        Map<String, Object> textures = Json.obj(json, "textures");
        if (textures.isEmpty()) textures = Json.obj(json, "skins");
        String url = firstTextureUrl(textures);
        if (!url.isEmpty()) return absoluteTexture(site, url);

        String hash = firstTextureHash(textures);
        if (hash.isEmpty()) {
            Object raw = json.get("skin");
            if (raw instanceof String s) {
                if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("//")
                        || s.startsWith("/")) {
                    return absoluteTexture(site, s);
                }
                hash = s;
            } else if (raw != null) {
                String skinUrl = Json.str(Json.obj(raw), "url", "");
                if (!skinUrl.isBlank()) return absoluteTexture(site, skinUrl);
                hash = Json.str(Json.obj(raw), "hash", "");
            }
        }
        if (hash.isEmpty()) return null;
        return csl + "/textures/" + hash;
    }

    /// Resolves a root-relative texture path against the platform's site root.
    private static String absoluteTexture(String site, String value) {
        String url = value == null ? "" : value.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return site + url;
        return url;
    }

    /// Extracts a direct skin URL from the common CSL response shapes.
    private static String firstTextureUrl(Map<String, Object> textures) {
        for (Map.Entry<String, Object> entry : textures.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("cape")) continue;
            Object raw = entry.getValue();
            if (raw instanceof String s) {
                if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("/")) {
                    return s;
                }
                continue;
            }
            if (raw != null) {
                String url = Json.str(Json.obj(raw), "url", "");
                if (!url.isBlank()) return url;
            }
        }
        return "";
    }

    /// Extracts the texture hash used by `/csl/textures/<hash>`.
    private static String firstTextureHash(Map<String, Object> textures) {
        for (Map.Entry<String, Object> entry : textures.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("cape")) continue;
            Object raw = entry.getValue();
            if (raw instanceof String s && !s.isBlank() && !s.startsWith("http")
                    && !s.startsWith("/")) {
                return s;
            }
            if (raw != null) {
                String hash = Json.str(Json.obj(raw), "hash", "");
                if (!hash.isBlank()) return hash;
            }
        }
        return "";
    }

    /// Removes the Yggdrasil API suffix from a server root, yielding the site
    /// root used by CustomSkinAPI. Custom roots that do not use the standard
    /// suffix are returned unchanged.
    private static String siteRoot(Yggdrasil.Server server) {
        String root = server == null || server.apiRoot == null ? "" : server.apiRoot.trim();
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        int cut = root.toLowerCase(java.util.Locale.ROOT).lastIndexOf("/api/yggdrasil");
        if (cut > 0) root = root.substring(0, cut);
        return root;
    }

    // 只读签名与 IHDR
    private static int[] pngSize(byte[] data) {
        if (data == null || data.length < 24) return null;
        if ((data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') return null;
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') return null;
        int w = readInt(data, 16);
        int h = readInt(data, 20);
        return w <= 0 || h <= 0 ? null : new int[]{w, h};
    }

    private static int readInt(byte[] d, int offset) {
        return ((d[offset] & 0xFF) << 24) | ((d[offset + 1] & 0xFF) << 16)
                | ((d[offset + 2] & 0xFF) << 8) | (d[offset + 3] & 0xFF);
    }
}
