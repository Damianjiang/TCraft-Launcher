package tcraft;

public final class Inst {
    public String name;
    public String vanillaId;
    public String vanillaIcon;
    public String forge = "";
    public String neoforge = "";
    public String fabric = "";
    public String legacyFabric = "";
    public String liteLoader = "";
    public String optiFine = "";
    public String fabricApi = "";
    public String optiFabric = "";
    public String cleanroom = "";
    public String labyMod = "";

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

    public boolean hasLoader() {
        return !fabric.isEmpty() || !forge.isEmpty() || !neoforge.isEmpty() || !legacyFabric.isEmpty()
                || !liteLoader.isEmpty() || !optiFine.isEmpty();
    }
}
