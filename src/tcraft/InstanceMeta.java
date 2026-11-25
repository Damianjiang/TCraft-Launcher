package tcraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InstanceMeta {

    private InstanceMeta() {
    }

    public static final String[][] COLOR_TAGS = {
            {"红色", "#e74c3c"},
            {"橙色", "#e67e22"},
            {"黄色", "#f1c40f"},
            {"绿色", "#2ecc71"},
            {"青色", "#1abc9c"},
            {"蓝色", "#3498db"},
            {"紫色", "#9b59b6"},
            {"灰色", "#95a5a6"}
    };

    private static final String INVALID_NAME_CHARS = ":|\r\n";

    public static Path versionDir(Data.Instance ins) {
        String folder = ins.folder != null && !ins.folder.isEmpty() ? ins.folder : Data.get().gameDir;
        Path base = Paths.get(folder).toAbsolutePath().normalize();
        Path versions = base.resolve("versions").normalize();
        String version = ins.version == null ? "" : ins.version.trim();
        Path dir = versions.resolve(version).normalize();
        if (version.isEmpty() || dir.equals(versions) || !dir.startsWith(versions)) {
            return versions.resolve("__invalid__");
        }
        return dir;
    }

    public static Path setupIni(Data.Instance ins) {
        return versionDir(ins).resolve("TCL").resolve("Setup.ini");
    }

    // 按修改时间缓存
    private static final java.util.concurrent.ConcurrentHashMap<String, CachedIni> INI_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class CachedIni {
        final long modified;
        final java.util.Map<String, String> values;

        CachedIni(long modified, java.util.Map<String, String> values) {
            this.modified = modified;
            this.values = values;
        }
    }

    private static List<String> readSetupLines(Path file) throws Exception {
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) lines.set(0, lines.get(0).substring(1));
        return lines;
    }

    private static java.util.Map<String, String> loadIni(Path file) throws Exception {
        if (Files.size(file) > 1024L * 1024) throw new java.io.IOException("Setup.ini 过大");
        long modified = Files.getLastModifiedTime(file).toMillis();
        String key = file.toAbsolutePath().toString();
        CachedIni cached = INI_CACHE.get(key);
        if (cached != null && cached.modified == modified) return cached.values;
        List<String> lines = readSetupLines(file);
        java.util.Map<String, String> values = new java.util.HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            values.put(trimmed.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT),
                    trimmed.substring(eq + 1).trim());
        }
        if (INI_CACHE.size() >= 512) INI_CACHE.clear();
        INI_CACHE.put(key, new CachedIni(modified, values));
        return values;
    }

    public static String read(Data.Instance ins, String key) {
        try {
            Path file = setupIni(ins);
            if (!Files.isRegularFile(file)) return "";
            String value = loadIni(file).get(key.toLowerCase(java.util.Locale.ROOT));
            return value == null ? "" : value;
        } catch (Exception e) {
            Errors.error("读取实例设置失败：" + ins.name + " / " + key, e, Errors.Behavior.NONE);
        }
        return "";
    }

    public static void write(Data.Instance ins, String key, String value) {
        try {
            Path file = setupIni(ins);
            if (Files.isRegularFile(file) && Files.size(file) > 1024L * 1024) {
                throw new java.io.IOException("Setup.ini 过大");
            }
            List<String> lines = Files.isRegularFile(file)
                    ? readSetupLines(file)
                    : new ArrayList<>();
            List<String> out = new ArrayList<>();
            boolean replaced = false;
            for (String line : lines) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).trim().equalsIgnoreCase(key)) {
                    if (!replaced && value != null && !value.isEmpty()) {
                        out.add(key + "=" + value);
                        replaced = true;
                    }
                    continue;
                }
                out.add(line);
            }
            if (!replaced && value != null && !value.isEmpty()) out.add(key + "=" + value);
            Files.createDirectories(file.getParent());
            Files.write(file, out, StandardCharsets.UTF_8);
            INI_CACHE.remove(file.toAbsolutePath().toString());
        } catch (Exception e) {
            // brief 可能被再次查表
            Errors.error(Lang.t("保存实例设置失败：") + ins.name + " / " + key, e);
        }
    }

    public static String colorTag(Data.Instance ins) {
        String raw = read(ins, "ColorTag");
        if (raw.isEmpty()) return "";
        return raw.startsWith("#") ? raw : "#" + raw;
    }

    public static void setColorTag(Data.Instance ins, String hex) {
        write(ins, "ColorTag", hex == null ? "" : hex.trim());
    }

    private static final int MAX_VERSION_JSON = 16 * 1024 * 1024;

    private static final class VersionMeta {
        final String loader;
        final String kind;

        VersionMeta(String loader, String kind) {
            this.loader = loader == null ? "" : loader;
            this.kind = kind == null ? "" : kind;
        }
    }

    private static final class CachedVersionMeta {
        final long modified;
        final long size;
        final VersionMeta meta;

        CachedVersionMeta(long modified, long size, VersionMeta meta) {
            this.modified = modified;
            this.size = size;
            this.meta = meta;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedVersionMeta> VERSION_META =
            new java.util.concurrent.ConcurrentHashMap<>();

    // 实例名可能被改 以版本 JSON 为准
    public static String loader(Data.Instance ins) {
        VersionMeta meta = versionMeta(versionDir(ins));
        if (!meta.loader.isEmpty()) return meta.loader;
        String saved = normalizeLoader(ins.loader);
        if (!saved.isEmpty() && !"原版".equals(saved)) return saved;
        return loaderFromText((ins.name == null ? "" : ins.name) + " " + (ins.version == null ? "" : ins.version));
    }

    public static String icon(Data.Instance ins) {
        String custom = customIcon(ins.icon);
        if (!custom.isEmpty()) return custom;
        String logo = localLogo(ins);
        if (!logo.isEmpty()) return logo;
        String loader = loader(ins);
        String icon = iconForLoader(loader);
        if (!icon.isEmpty()) return icon;
        return iconForKind(kind(ins));
    }

    // 整合包会在版本目录里放图标
    private static String localLogo(Data.Instance ins) {
        Path dir = versionDir(ins);
        if (dir == null || !Files.isDirectory(dir)) return "";
        String[] names = {"TCL/Logo.png", "TCL/logo.png", "PCL/Logo.png", "PCL/logo.png",
                "icon.png", "pack.png", "logo.png"};
        for (String name : names) {
            Path file = dir.resolve(name).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) continue;
            try {
                long size = Files.size(file);
                if (size > 0 && size <= 8L * 1024 * 1024) return file.toAbsolutePath().toString();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public static String detectLoader(Path versionDir) {
        VersionMeta meta = versionMeta(versionDir);
        if (!meta.loader.isEmpty()) return meta.loader;
        return loaderFromText(versionDir == null ? "" : versionDir.getFileName().toString());
    }

    private static String kind(Data.Instance ins) {
        VersionMeta meta = versionMeta(versionDir(ins));
        if (!meta.kind.isEmpty()) return meta.kind;
        return kindFromId((ins.version == null ? "" : ins.version) + " " + (ins.name == null ? "" : ins.name));
    }

    private static String customIcon(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) return "";
        try {
            if (name.startsWith("Icons/")) {
                Path base = Theme.iconDir().toAbsolutePath().normalize();
                Path file = base.resolve(name.substring(6)).normalize();
                return file.startsWith(base) && Files.isRegularFile(file) ? name : "";
            }
            if (name.startsWith("Skins/")) {
                Path base = Theme.skinDir().toAbsolutePath().normalize();
                Path file = base.resolve(name.substring(6)).normalize();
                return file.startsWith(base) && Files.isRegularFile(file) ? name : "";
            }
            if (!name.startsWith("\\\\") && !name.startsWith("//")) {
                Path file = Paths.get(name);
                if (file.isAbsolute() && Files.isRegularFile(file)) return name;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String iconForLoader(String loader) {
        if (loader == null) return "";
        return switch (loader) {
            case "Fabric", "LegacyFabric", "Quilt" -> "Fabric.png";
            case "Forge", "Cleanroom" -> "Anvil.png";
            case "NeoForge" -> "NeoForge.png";
            case "OptiFine" -> "GrassPath.png";
            case "LiteLoader" -> "Egg.png";
            default -> "";
        };
    }

    private static String iconForKind(String kind) {
        return switch (kind) {
            case "snapshot" -> "CommandBlock.png";
            case "old" -> "CobbleStone.png";
            case "special" -> "GoldBlock.png";
            default -> "Grass.png";
        };
    }

    private static String normalizeLoader(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge") || lower.contains("neo-forge")) return "NeoForge";
        if (lower.contains("cleanroom")) return "Cleanroom";
        if (lower.contains("legacyfabric") || lower.contains("legacy-fabric")) return "LegacyFabric";
        if (lower.contains("fabric")) return "Fabric";
        if (lower.contains("quilt")) return "Quilt";
        if (lower.contains("forge")) return "Forge";
        if (lower.contains("optifine")) return "OptiFine";
        if (lower.contains("liteloader")) return "LiteLoader";
        if ("原版".equals(text) || "vanilla".equals(lower)) return "原版";
        return text;
    }

    private static String loaderFromText(String raw) {
        String lower = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge") || lower.contains("neo-forge")) return "NeoForge";
        if (lower.contains("fabric")) return "Fabric";
        if (lower.contains("forge")) return "Forge";
        if (lower.contains("quilt")) return "Quilt";
        if (lower.contains("optifine")) return "OptiFine";
        if (lower.contains("liteloader")) return "LiteLoader";
        return "";
    }

    private static String kindFromId(String raw) {
        String id = raw == null ? "" : raw.trim();
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("∞") || lower.contains("shareware") || lower.contains("oneblockatatime")
                || lower.contains("potato") || lower.contains("craftmine") || lower.contains("23w13a_or_b")
                || lower.contains("15w14a") || lower.contains("20w14infinite") || lower.contains("love and hugs")) {
            return "special";
        }
        if (lower.startsWith("a1.") || lower.startsWith("b1.") || lower.startsWith("c0.")
                || lower.startsWith("inf-") || lower.startsWith("in-") || lower.startsWith("rd-")) {
            return "old";
        }
        if (lower.matches("^\\d{2}w\\d{2}[a-z].*") || lower.contains("snapshot")) return "snapshot";
        return "release";
    }

    private static VersionMeta versionMeta(Path dir) {
        if (dir == null) return new VersionMeta("", "");
        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
        Path json = dir.resolve(name + ".json");
        return readVersionMeta(json, 0, new java.util.HashSet<>());
    }

    private static VersionMeta readVersionMeta(Path file, int depth, java.util.Set<String> seen) {
        if (depth > 4 || file == null || !Files.isRegularFile(file)) return new VersionMeta("", "");
        try {
            String key = file.toAbsolutePath().normalize().toString();
            if (!seen.add(key)) return new VersionMeta("", "");
            long modified = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            if (size <= 0 || size > MAX_VERSION_JSON) return new VersionMeta("", "");
            CachedVersionMeta cached = VERSION_META.get(key);
            if (cached != null && cached.modified == modified && cached.size == size) return cached.meta;

            String text;
            try (java.io.InputStream in = Files.newInputStream(file)) {
                text = new String(Io.read(in, MAX_VERSION_JSON), StandardCharsets.UTF_8);
            }
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            java.util.Map<String, Object> root = Json.obj(Json.parse(text));
            String ownLoader = detectLoader(root);
            String type = Json.str(root, "type", "").trim().toLowerCase(Locale.ROOT);
            String ownKind = "";
            if (!type.isEmpty()) {
                if (type.startsWith("old_")) ownKind = "old";
                else if ("snapshot".equals(type)) ownKind = "snapshot";
                else if ("release".equals(type)) ownKind = "release";
                else ownKind = "release";
            }
            // 愚人节版本在清单里也是 snapshot 图标要用金块
            if ("special".equals(kindFromId(Json.str(root, "id", "")))) ownKind = "special";

            String inherit = Json.str(root, "inheritsFrom", "").trim();
            VersionMeta base = new VersionMeta("", "");
            if (!inherit.isEmpty() && depth < 4) {
                Path rootDir = file.getParent() == null ? null : file.getParent().getParent();
                if (rootDir != null) {
                    String safe = Paths.get(inherit).getFileName() == null ? "" : Paths.get(inherit).getFileName().toString();
                    Path baseDir = rootDir.resolve(safe).normalize();
                    if (baseDir.startsWith(rootDir) && !safe.isEmpty()) {
                        base = readVersionMeta(baseDir.resolve(safe + ".json"), depth + 1, seen);
                    }
                }
            }

            String loader = ownLoader;
            if (loader.isEmpty()) loader = base.loader;
            if (loader.isEmpty()) loader = loaderFromText(Json.str(root, "id", ""));
            String kind = base.kind;
            if (kind.isEmpty()) kind = ownKind;
            if (kind.isEmpty()) kind = kindFromId(Json.str(root, "id", ""));
            VersionMeta meta = new VersionMeta(loader, kind);
            if (VERSION_META.size() >= 512) VERSION_META.clear();
            VERSION_META.put(key, new CachedVersionMeta(modified, size, meta));
            return meta;
        } catch (Exception e) {
            return new VersionMeta("", "");
        }
    }

    private static String detectLoader(java.util.Map<String, Object> root) {
        boolean neo = false;
        boolean forge = false;
        boolean fabric = false;
        boolean quilt = false;
        boolean optifine = false;
        boolean lite = false;
        for (Object value : Json.arr(root.get("libraries"))) {
            String name = Json.str(Json.obj(value), "name", "").toLowerCase(Locale.ROOT);
            if (name.contains("net.neoforged:neoforge") || name.contains("net.neoforged:forge")) neo = true;
            else if (name.contains("net.minecraftforge:forge")) forge = true;
            else if (name.contains("net.fabricmc:fabric-loader")) fabric = true;
            else if (name.contains("org.quiltmc:quilt-loader")) quilt = true;
            else if (name.contains("optifine:optifine")) optifine = true;
            else if (name.contains("com.mumfrey:liteloader")) lite = true;
        }
        if (!neo && !forge && !fabric && !quilt && !optifine && !lite) {
            String mainClass = Json.str(root, "mainClass", "").toLowerCase(Locale.ROOT);
            if (mainClass.contains("net.fabricmc.loader")) fabric = true;
            else if (mainClass.contains("net.neoforged")) neo = true;
            else if (mainClass.contains("net.minecraft.launchwrapper")) forge = true;
        }
        if (neo) return "NeoForge";
        if (forge) return "Forge";
        if (fabric) return "Fabric";
        if (quilt) return "Quilt";
        if (optifine) return "OptiFine";
        if (lite) return "LiteLoader";
        return "";
    }

    public static List<String> collections(Data.Instance ins) {
        List<String> result = new ArrayList<>();
        String raw = read(ins, "Collections");
        if (raw.isEmpty()) return result;
        for (String part : raw.split("\\|")) {
            String one = part.trim();
            if (!one.isEmpty() && !result.contains(one)) result.add(one);
        }
        return result;
    }

    public static void setCollections(Data.Instance ins, List<String> items) {
        List<String> clean = new ArrayList<>();
        if (items != null) {
            for (String one : items) {
                String trimmed = one == null ? "" : one.trim();
                if (!trimmed.isEmpty() && !clean.contains(trimmed)) clean.add(trimmed);
            }
        }
        write(ins, "Collections", String.join("|", clean));
    }

    public static void addTo(Data.Instance ins, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return;
        CollectionsStore.ensure(trimmed);
        List<String> owned = collections(ins);
        if (owned.contains(trimmed)) return;
        owned.add(trimmed);
        setCollections(ins, owned);
    }

    public static void removeFrom(Data.Instance ins, String name) {
        List<String> owned = collections(ins);
        if (!owned.remove(name)) return;
        setCollections(ins, owned);
    }

    public static final class CollectionsStore {

        private CollectionsStore() {
        }

        public static Path storePath() {
            return Data.get().baseDir().resolve("Collections.json");
        }

        public static List<String> listAll() {
            List<String> out = new ArrayList<>();
            try {
                Path file = storePath();
                if (!Files.isRegularFile(file)) return out;
                String text;
                try (java.io.InputStream in = Files.newInputStream(file)) {
                    text = new String(Io.read(in, 1024L * 1024), StandardCharsets.UTF_8);
                }
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                Object root = Json.parse(text);
                for (Object one : Json.arr(root, "Collections")) {
                    String name = one == null ? "" : String.valueOf(one).trim();
                    if (!name.isEmpty() && !out.contains(name)) out.add(name);
                }
            } catch (Exception e) {
                Errors.error("读取实例合集失败", e, Errors.Behavior.NONE);
            }
            return out;
        }

        public static void saveAll(List<String> items) {
            try {
                List<String> clean = new ArrayList<>();
                if (items != null) {
                    for (String one : items) {
                        String trimmed = one == null ? "" : one.trim();
                        if (!trimmed.isEmpty() && !clean.contains(trimmed)) clean.add(trimmed);
                    }
                }
                StringBuilder sb = new StringBuilder("{\n  \"Version\": 1,\n  \"Collections\": [");
                for (int i = 0; i < clean.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(Json.quote(clean.get(i)));
                }
                sb.append("]\n}\n");
                Path file = storePath();
                Files.createDirectories(file.getParent());
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Errors.error("保存实例合集失败", e);
            }
        }

        public static boolean ensure(String name) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) return false;
            List<String> items = listAll();
            if (items.contains(trimmed)) return false;
            items.add(trimmed);
            saveAll(items);
            return true;
        }

        public static String validateName(String name, String ignore) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) return "合集名不能为空。";
            if (trimmed.length() > 32) return "合集名不能超过 32 个字符。";
            for (char c : INVALID_NAME_CHARS.toCharArray()) {
                if (trimmed.indexOf(c) >= 0) return "合集名不能包含 : 或 | 。";
            }
            if (!trimmed.equals(ignore) && listAll().contains(trimmed)) return "已存在同名合集。";
            return "";
        }

        public static void rename(String oldName, String newName) {
            String trimmed = newName == null ? "" : newName.trim();
            if (oldName == null || oldName.isEmpty() || trimmed.isEmpty()) return;
            List<String> items = listAll();
            int index = items.indexOf(oldName);
            if (index < 0) return;
            items.set(index, trimmed);
            saveAll(items);
            for (Data.Instance ins : new ArrayList<>(Data.get().instances)) {
                List<String> owned = collections(ins);
                if (owned.remove(oldName)) {
                    if (!owned.contains(trimmed)) owned.add(trimmed);
                    setCollections(ins, owned);
                }
            }
        }

        public static void delete(String name) {
            if (name == null || name.isEmpty()) return;
            List<String> items = listAll();
            if (!items.remove(name)) return;
            saveAll(items);
            for (Data.Instance ins : new ArrayList<>(Data.get().instances)) {
                List<String> owned = collections(ins);
                if (owned.remove(name)) setCollections(ins, owned);
            }
        }
    }
}
