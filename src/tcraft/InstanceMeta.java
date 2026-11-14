package tcraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** 实例元数据：颜色标签和合集归属，存在实例目录的 Setup.ini。 */
public final class InstanceMeta {

    private InstanceMeta() {
    }

    /** 颜色标签预设：中文名 + 十六进制值。 */
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

    /** 合集名不能出现的字符，| 会分隔合集。 */
    private static final String INVALID_NAME_CHARS = ":|\r\n";

    /** 实例版本目录。 */
    public static Path versionDir(Data.Instance ins) {
        String folder = ins.folder != null && !ins.folder.isEmpty() ? ins.folder : Data.get().gameDir;
        return Paths.get(folder).resolve("versions").resolve(ins.version);
    }

    /** 实例的 TCL\Setup.ini。 */
    public static Path setupIni(Data.Instance ins) {
        return versionDir(ins).resolve("TCL").resolve("Setup.ini");
    }

    /** 读一个键；文件或键不存在就返回空串。 */
    public static String read(Data.Instance ins, String key) {
        try {
            Path file = setupIni(ins);
            if (!Files.isRegularFile(file)) return "";
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            // 去掉 UTF-8 BOM。
            if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
                lines.set(0, lines.get(0).substring(1));
            }
            for (String line : lines) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                if (trimmed.substring(0, eq).trim().equalsIgnoreCase(key)) {
                    return trimmed.substring(eq + 1).trim();
                }
            }
        } catch (Exception e) {
            Errors.error("读取实例设置失败：" + ins.name + " / " + key, e, Errors.Behavior.NONE);
        }
        return "";
    }

    /** 写一个键，value 为空表示删除，文件里其他行保留。 */
    public static void write(Data.Instance ins, String key, String value) {
        try {
            Path file = setupIni(ins);
            List<String> lines = Files.isRegularFile(file)
                    ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
                lines.set(0, lines.get(0).substring(1));
            }
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
        } catch (Exception e) {
            Errors.error("保存实例设置失败：" + ins.name + " / " + key, e);
        }
    }

    public static String colorTag(Data.Instance ins) {
        String raw = read(ins, "ColorTag");
        if (raw.isEmpty()) return "";
        return raw.startsWith("#") ? raw : "#" + raw;
    }

    /** 设颜色标签，空串表示清除。 */
    public static void setColorTag(Data.Instance ins, String hex) {
        write(ins, "ColorTag", hex == null ? "" : hex.trim());
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

    /** 合集清单，存在启动器数据目录的 Collections.json。 */
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
                String text = Files.readString(file, StandardCharsets.UTF_8);
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

        /** 合集不存在时创建，返回是否新建。 */
        public static boolean ensure(String name) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) return false;
            List<String> items = listAll();
            if (items.contains(trimmed)) return false;
            items.add(trimmed);
            saveAll(items);
            return true;
        }

        /** 校验合集名，返回空串表示合法。 */
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

        /** 重命名合集，同时把各实例上的归属改掉。 */
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

        /** 删合集，只删标签，不动实例本身。 */
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
