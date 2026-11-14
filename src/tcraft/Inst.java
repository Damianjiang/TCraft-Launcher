package tcraft;

/** 一次「开始下载」的安装请求，vanillaId 必填。 */
public final class Inst {
    public String name;  // 实例名称，必填
    public String vanillaId;   // 原版版本 id
    public String vanillaIcon; // 原版图标文件名
    // 主加载器，互斥
    public String forge = "";
    public String neoforge = "";
    public String fabric = "";
    public String legacyFabric = "";
    public String liteLoader = "";
    public String optiFine = "";
    // 附加载器，可以跟主加载器叠加
    public String fabricApi = "";
    public String optiFabric = "";
    // 只下载安装器，不自动安装
    public String cleanroom = "";
    public String labyMod = "";

    /** 主加载器名，给任务标题用。 */
    public String primaryLoader() {
        if (!fabric.isEmpty()) return "Fabric";
        if (!forge.isEmpty()) return "Forge";
        if (!neoforge.isEmpty()) return "NeoForge";
        if (!legacyFabric.isEmpty()) return "LegacyFabric";
        if (!liteLoader.isEmpty()) return "LiteLoader";
        if (!optiFine.isEmpty()) return "OptiFine";
        if (!cleanroom.isEmpty() || !labyMod.isEmpty()) return "原版";
        return "原版";
    }

    /** 有没有选需要版本隔离的加载器。 */
    public boolean hasLoader() {
        return !fabric.isEmpty() || !forge.isEmpty() || !neoforge.isEmpty() || !legacyFabric.isEmpty()
                || !liteLoader.isEmpty() || !optiFine.isEmpty();
    }
}
