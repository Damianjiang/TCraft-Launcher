package tcraft;

import java.io.InputStream;
import java.util.Properties;

public final class Build {

    public static final String VERSION;
    public static final String EDITION;
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
}
