package tcraft;

/** 资源详情页的入参载体。 */
public class ResourceInfo {

    /** modrinth / curseforge。 */
    public String platform = "modrinth";
    /** Modrinth project_id 或 CurseForge mod id。 */
    public String projectId = "";
    public String slug = "";
    public String name = "";
    public String desc = "";
    public String icon = "";
    public String website = "";
    public long downloads;
    /** 搜索分类：mod / modpack / datapack / resourcepack / shader / world。 */
    public String projectType = "mod";
    /** 当前视图的资源类型。 */
    public String resourceType = "mod";
    /** 所选游戏版本。 */
    public String targetVersion = "";
    /** 所选加载器：fabric / forge / quilt / neoforge / 空。 */
    public String targetLoader = "";
    /** 工程支持的加载器，小写。 */
    public java.util.List<String> loaders = new java.util.ArrayList<>();

    public java.util.List<String> loaders() {
        return loaders;
    }

    public static ResourceInfo modrinth(String projectId, String slug, String name, String type) {
        ResourceInfo r = new ResourceInfo();
        r.platform = "modrinth";
        r.projectId = projectId;
        r.slug = slug;
        r.name = name;
        r.projectType = type;
        r.resourceType = type;
        return r;
    }

    public static ResourceInfo curseforge(long id, String slug, String name, String type) {
        ResourceInfo r = new ResourceInfo();
        r.platform = "curseforge";
        r.projectId = String.valueOf(id);
        r.slug = slug;
        r.name = name;
        r.projectType = type;
        r.resourceType = type;
        return r;
    }

    public boolean isCurseForge() {
        return "curseforge".equals(platform);
    }
}
