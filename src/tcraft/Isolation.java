package tcraft;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Isolation {

    public static final int FOLLOW = 0;
    public static final int ON = 1;
    public static final int OFF = 2;

    private Isolation() {
    }

    public static Path base(Data.Instance ins) {
        String folder = ins == null ? "" : ins.folder;
        return Paths.get(folder == null || folder.isEmpty() ? Data.get().gameDir : folder);
    }

    public static Path run(Data.Instance ins) {
        if (ins == null) return Paths.get(Data.get().gameDir);
        Path base = base(ins);
        if (!on(ins)) return base;
        String version = ins.version == null ? "" : ins.version.trim();
        if (version.isEmpty()) return base;
        Path versions = base.resolve("versions").normalize();
        Path run = versions.resolve(version).normalize();
        if (run.equals(versions) || !run.startsWith(versions)) return base;
        return run;
    }

    // 删除实例时可以清理的目录：只有版本隔离真正把数据落在独立目录里才返回，共享游戏目录一律不动
    public static Path deletableDir(Data.Instance ins) {
        if (ins == null || !on(ins)) return null;
        Path base = base(ins).toAbsolutePath().normalize();
        Path versions = base.resolve("versions").normalize();
        Path dir = run(ins).toAbsolutePath().normalize();
        if (dir.equals(versions) || !dir.startsWith(versions)) return null;
        String version = ins.version == null ? "" : ins.version.trim();
        if (version.isEmpty()) return null;
        return dir;
    }

    public static void deleteTree(Path dir) {
        if (dir == null || !java.nio.file.Files.exists(dir)) return;
        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (java.io.IOException ignored) {
                }
            });
        } catch (java.io.IOException ignored) {
        }
    }

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

    private static final java.util.regex.Pattern VANILLA_P =
            java.util.regex.Pattern.compile("\\d+\\.\\d+(\\.\\d+)?");

    public static boolean isRelease(String version) {
        if (version == null || version.isEmpty()) return true;
        for (Data.McVersion v : Data.get().versions) {
            if (v.id.equals(version)) return "正式版".equals(v.group());
        }
        String vanilla = vanillaOf(version);
        for (Data.McVersion v : Data.get().versions) {
            if (v.id.equals(vanilla)) return "正式版".equals(v.group());
        }
        return VANILLA_P.matcher(vanilla).matches();
    }

    public static String vanillaOf(String version) {
        if (version == null) return "";
        String v = version.trim();
        int dash = v.indexOf('-');
        if (dash > 0 && VANILLA_P.matcher(v.substring(0, dash)).matches()) {
            return v.substring(0, dash);
        }
        return v;
    }

    private static boolean hasLoader(Data.Instance ins) {
        return ins.loader != null && !ins.loader.isEmpty() && !"原版".equals(ins.loader);
    }

    public static String describe(Data.Instance ins) {
        if (!on(ins)) return Lang.t("不隔离（多个版本共用 mods、saves）");
        Path run = run(ins);
        Path base = base(ins);
        String rel;
        try {
            rel = base.relativize(run).toString().replace('\\', '/');
        } catch (Exception e) {
            rel = run.toString();
        }
        return Lang.tf("已隔离（%s）", rel);
    }
}
