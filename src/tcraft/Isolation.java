package tcraft;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 版本隔离：每个版本用 versions/&lt;版本&gt; 当游戏目录，mods、saves、config 各自一份；
 * libraries 与 assets 仍然共用，不会把整份游戏复制一遍。
 */
public final class Isolation {

    /** 跟随全局设置。 */
    public static final int FOLLOW = 0;
    /** 强制隔离。 */
    public static final int ON = 1;
    /** 不隔离。 */
    public static final int OFF = 2;

    private Isolation() {
    }

    /** 实例所在的基础游戏目录（.minecraft 根）。 */
    public static Path base(Data.Instance ins) {
        String folder = ins == null ? "" : ins.folder;
        return Paths.get(folder == null || folder.isEmpty() ? Data.get().gameDir : folder);
    }

    /** 游戏真正使用的目录：隔离时是 versions/&lt;版本&gt;，否则就是基础目录。 */
    public static Path run(Data.Instance ins) {
        if (ins == null) return Paths.get(Data.get().gameDir);
        Path base = base(ins);
        if (!on(ins)) return base;
        String version = ins.version == null ? "" : ins.version.trim();
        if (version.isEmpty()) return base;
        return base.resolve("versions").resolve(version);
    }

    /** 这个实例要不要隔离。 */
    public static boolean on(Data.Instance ins) {
        if (ins == null) return false;
        if (ins.isolation == ON) return true;
        if (ins.isolation == OFF) return false;
        int mode = Data.get().getInt("LaunchIsolation", 4);
        return switch (mode) {
            case 1 -> hasLoader(ins);
            case 2 -> !isRelease(ins.version);
            case 3 -> hasLoader(ins) || !isRelease(ins.version);
            case 0 -> false;
            default -> true;
        };
    }

    /** 隔离目录不存在就建出来。 */
    public static Path ensureRun(Data.Instance ins) throws java.io.IOException {
        Path run = run(ins);
        java.nio.file.Files.createDirectories(run);
        return run;
    }

    /** 版本是不是正式版，清单里没有就按版本号形状判断。 */
    public static boolean isRelease(String version) {
        if (version == null || version.isEmpty()) return true;
        for (Data.McVersion v : Data.get().versions) {
            if (v.id.equals(version)) return "正式版".equals(v.group());
        }
        // Forge / Fabric 的版本号形如 1.20.1-forge-47.4.18，取前缀再查一次。
        String vanilla = vanillaOf(version);
        for (Data.McVersion v : Data.get().versions) {
            if (v.id.equals(vanilla)) return "正式版".equals(v.group());
        }
        return vanilla.matches("\\d+\\.\\d+(\\.\\d+)?");
    }

    /** 从加载器版本号里取出原版号：1.20.1-forge-47.4.18 → 1.20.1。 */
    public static String vanillaOf(String version) {
        if (version == null) return "";
        String v = version.trim();
        int dash = v.indexOf('-');
        if (dash > 0 && v.substring(0, dash).matches("\\d+\\.\\d+(\\.\\d+)?")) {
            return v.substring(0, dash);
        }
        return v;
    }

    private static boolean hasLoader(Data.Instance ins) {
        return ins.loader != null && !ins.loader.isEmpty() && !"原版".equals(ins.loader);
    }

    /** 设置页里显示用的一句话。 */
    public static String describe(Data.Instance ins) {
        if (!on(ins)) return "不隔离（多个版本共用 mods、saves）";
        Path run = run(ins);
        Path base = base(ins);
        String rel;
        try {
            rel = base.relativize(run).toString().replace('\\', '/');
        } catch (Exception e) {
            rel = run.toString();
        }
        return "已隔离（" + rel + "）";
    }
}
