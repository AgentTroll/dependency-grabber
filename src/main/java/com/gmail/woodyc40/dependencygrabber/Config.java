package com.gmail.woodyc40.dependencygrabber;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Set;

@Getter
public class Config {
    private final Set<String> includes;
    private final Set<String> excludes;
    private final boolean includeSoftdependencies;

    public Config(DependencyGrabber plugin) {
        FileConfiguration config = plugin.getConfig();

        this.includes = new HashSet<>(config.getStringList("includes"));
        this.excludes = new HashSet<>(config.getStringList("excludes"));
        this.includeSoftdependencies = config.getBoolean("include-soft-dependencies");
    }
}
