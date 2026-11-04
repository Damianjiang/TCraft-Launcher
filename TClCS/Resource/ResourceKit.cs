namespace TClCS;

/// <summary>
/// 社区资源类型 → 展示信息（显示名 / 落盘子目录 / 图标 / 模块文件归类）的集中映射库。
/// 各下载 / 收藏 / 整合包页面直接调用本类，避免在多个页面各自重写一套 switch。
/// </summary>
public static class ResourceKit {
    /// <summary>
    /// 完整描述名（下载 / 保存等场景使用，Mod 末尾带一个空格以区分 "Mod 文件" 与 "ModPack"）。
    /// </summary>
    public static string DisplayName(ResourceTypes type) => type switch {
        ResourceTypes.ModPack => "整合包",
        ResourceTypes.Mod => "Mod ",
        ResourceTypes.ResourcePack => "资源包",
        ResourceTypes.Shader => "光影包",
        ResourceTypes.DataPack => "数据包",
        ResourceTypes.Map => "世界",
        _ => "资源",
    };

    /// <summary>
    /// 短标签（收藏夹等紧凑卡片使用；多个类别命中时按 Mod → 整合包 → 资源包 → 光影 → 数据包 → 世界 的优先级取第一个）。
    /// </summary>
    public static string ShortName(ResourceTypes type) {
        if (type.HasFlag(ResourceTypes.Mod)) return "Mod";
        if (type.HasFlag(ResourceTypes.ModPack)) return "整合包";
        if (type.HasFlag(ResourceTypes.ResourcePack)) return "资源包";
        if (type.HasFlag(ResourceTypes.Shader)) return "光影";
        if (type.HasFlag(ResourceTypes.DataPack)) return "数据包";
        if (type.HasFlag(ResourceTypes.Map)) return "世界";
        return "资源";
    }

    /// <summary>资源类型对应的内置图标（lucide 图标路径）。世界用与「下载→世界」页一致的 earth。</summary>
    public static string Icon(ResourceTypes type) {
        if (type.HasFlag(ResourceTypes.Mod)) return "lucide/puzzle";
        if (type.HasFlag(ResourceTypes.ModPack)) return "lucide/package";
        if (type.HasFlag(ResourceTypes.ResourcePack)) return "lucide/palette";
        if (type.HasFlag(ResourceTypes.Shader)) return "lucide/sparkles";
        if (type.HasFlag(ResourceTypes.Map)) return "lucide/earth";
        return "lucide/box";
    }

    /// <summary>
    /// 资源落盘时相对版本 / 实例根目录的子目录（带尾斜杠；数据包直接落到版本根目录，返回空串）。
    /// 世界（Map）必须落到 saves 子目录才能被存档列表识别。
    /// </summary>
    public static string SaveSubFolder(ResourceTypes type) => type switch {
        ResourceTypes.Mod => "mods\\",
        ResourceTypes.ResourcePack => "resourcepacks\\",
        ResourceTypes.Shader => "shaderpacks\\",
        ResourceTypes.Map => "saves\\",
        _ => "",
    };

    /// <summary>整合包内单个模组文件的归类结果（落盘子目录，不带尾斜杠 + 资源类型）。</summary>
    public readonly struct ResourceModuleTarget {
        public string Folder { get; }
        public ResourceTypes Type { get; }
        public ResourceModuleTarget(string folder, ResourceTypes type) {
            Folder = folder;
            Type = type;
        }
    }

    /// <summary>
    /// 依据整合包条目里的 <c>modules</c> 名集合与文件后缀推断该文件应归入的子目录与资源类型。
    /// 与主流整合包安装器的归类规则一致：
    /// META-INF / mcmod.info / fabric.mod.json / .jar → mods；pack.mcmeta → resourcepacks；
    /// level.dat → saves（世界）；其余（多为光影）→ shaderpacks；modules 为空 → 默认 mods。
    /// </summary>
    public static ResourceModuleTarget InferModuleTarget(IReadOnlyCollection<string>? modules, string? fileName) {
        if (modules is { Count: > 0 }) {
            if (modules.Contains("META-INF") || modules.Contains("mcmod.info") || modules.Contains("fabric.mod.json")
                || (fileName?.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) ?? false)) {
                return new ResourceModuleTarget("mods", ResourceTypes.Mod);
            }
            if (modules.Contains("pack.mcmeta")) {
                return new ResourceModuleTarget("resourcepacks", ResourceTypes.ResourcePack);
            }
            if (modules.Contains("level.dat")) {
                return new ResourceModuleTarget("saves", ResourceTypes.Map);
            }
            return new ResourceModuleTarget("shaderpacks", ResourceTypes.Shader);
        }
        return new ResourceModuleTarget("mods", ResourceTypes.Mod);
    }

    /// <summary>
    /// 判断资源的若干支持版本中是否有任意一个与目标实例版本兼容。
    /// 用于资源下载页的默认落盘目录判定；任一版本匹配即返回 true。
    /// 入参均为原始值（字符串 / 整数），不依赖 VB 主工程类型，便于跨页面复用。
    /// </summary>
    public static bool IsVersionCompatible(IEnumerable<string> fileVersions, string instanceVanillaName, int instanceDrop) {
        if (fileVersions is null) return false;
        foreach (var fileVersion in fileVersions) {
            if (IsSingleVersionCompatible(fileVersion, instanceVanillaName, instanceDrop)) return true;
        }
        return false;
    }

    /// <summary>
    /// 单个资源版本与实例版本是否兼容。区分「预览版 / 新格式快照 / 旧格式 w 快照」三层。
    /// </summary>
    public static bool IsSingleVersionCompatible(string fileVersion, string instanceVanillaName, int instanceDrop) {
        if (string.IsNullOrEmpty(fileVersion) || string.IsNullOrEmpty(instanceVanillaName)) return false;
        // 「预览版」或不含 "." 的版本视为快照（旧版 w 快照形如 "w123" 无 "."）
        // .NET 4.8 无 string.Contains(string) 实例方法，用 IndexOf 判定
        var fileIsSnapshot = fileVersion.IndexOf("预览版", StringComparison.Ordinal) >= 0 || fileVersion.IndexOf('.') < 0;
        var instanceIsSnapshot = instanceVanillaName.IndexOf("snapshot", StringComparison.OrdinalIgnoreCase) >= 0 || instanceVanillaName.IndexOf('.') < 0;
        if (fileIsSnapshot != instanceIsSnapshot) {
            return false; // 只有一边是快照
        }
        if (!fileIsSnapshot && !instanceIsSnapshot) {
            return fileVersion == instanceVanillaName; // 都不是快照：精确匹配
        }
        // 两边都是快照
        var fileIsNewFormat = fileVersion.IndexOf('.') >= 0 && ParseLeadingNumber(fileVersion.BeforeFirst(".", ignoreCase: false)) > 1;
        var instanceIsNewFormat = instanceDrop > 250;
        if (fileIsNewFormat != instanceIsNewFormat) {
            return false; // 只有一边是新格式快照
        }
        if (fileIsNewFormat && instanceIsNewFormat) {
            // 都是新格式快照：按主版本前缀比较（"26.1 预览版" 取 "26.1"；实例名 "1.21-snapshot" 取 "1.21"）
            return fileVersion.BeforeFirst(" ", ignoreCase: false) == instanceVanillaName.BeforeFirst("-", ignoreCase: false);
        }
        // 都是旧格式快照
        if (fileVersion.IndexOf("w", StringComparison.Ordinal) >= 0 && instanceVanillaName.IndexOf("w", StringComparison.Ordinal) >= 0) {
            return fileVersion == instanceVanillaName; // 都是 w 快照：精确匹配
        }
        return true; // 不全是 w 快照：只能假定相同，无法判断
    }

    /// <summary>
    /// 取字符串开头的数值部分（等价 VB 的 Val：非数值返回 0），用于判定快照主版本号。
    /// </summary>
    private static double ParseLeadingNumber(string s) {
        if (double.TryParse(s, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var v)) {
            return v;
        }
        return 0;
    }
}
