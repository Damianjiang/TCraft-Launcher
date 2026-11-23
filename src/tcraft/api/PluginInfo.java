package tcraft.api;

import java.nio.file.Path;

// plugin.json 只读信息
public final class PluginInfo {

    public final String id;
    public final String name;
    public final String version;
    public final String author;
    public final String description;
    public final String icon;
    public final String main;
    public final String website;
    public final int api;
    public final Path dir;

    public PluginInfo(String id, String name, String version, String author,
                      String description, String icon, String main, String website,
                      int api, Path dir) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.icon = icon;
        this.main = main;
        this.website = website;
        this.api = api;
        this.dir = dir;
    }
}
