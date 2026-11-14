package tcraft;

import java.io.InputStream;
import java.util.Properties;

/** 构建时写进 jar 的版本信息。 */
public final class Build {

    public static final String VERSION;
    /** open / full / dev。 */
    public static final String EDITION;
    /** 微软登录 client id，空串表示不支持正版登录。 */
    public static final String MICROSOFT_ID;

    static {
        Properties p = new Properties();
        try (InputStream in = Build.class.getResourceAsStream("/build.properties")) {
            if (in == null) {
                p.load(Build.class.getResourceAsStream("/tcraft/build.properties"));
            } else {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        VERSION = p.getProperty("version", "dev");
        EDITION = p.getProperty("edition", "dev");
        MICROSOFT_ID = p.getProperty("microsoft.auth.id", "").trim();
    }

    private Build() {
    }

    public static boolean hasMicrosoftAuth() {
        return !MICROSOFT_ID.isEmpty();
    }
}
