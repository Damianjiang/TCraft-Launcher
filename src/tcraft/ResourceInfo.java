package tcraft;

public class ResourceInfo {

    public String platform = "modrinth";
    public String projectId = "";
    public String slug = "";
    public String name = "";
    public String desc = "";
    public String icon = "";
    public String website = "";
    public long downloads;
    public String projectType = "mod";
    public String resourceType = "mod";
    public String targetVersion = "";
    public String targetLoader = "";
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
