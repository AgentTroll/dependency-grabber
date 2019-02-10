package com.gmail.woodyc40.dependencygrabber;

import lombok.Getter;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class DependencyGrabber {
    public static void premain(String string, Instrumentation instrument) {
        DependencyGrabber grabber = new DependencyGrabber();
        grabber.grabDependencies(instrument);
    }

    @Getter
    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());
    @Getter
    private final File dataFolder = new File(System.getProperty("user.dir"), "DependencyGrabber");
    private final File configFile = new File(this.dataFolder, "config.yml");
    private FileConfiguration fileConfiguration;

    public void saveDefaultConfig() {
        if (!this.configFile.exists()) {
            try {
                Files.copy(this.getClass().getResourceAsStream("/config.yml"), this.configFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public FileConfiguration getConfig() {
        if (this.fileConfiguration == null) {
            this.fileConfiguration = YamlConfiguration.loadConfiguration(this.configFile);
        }

        return this.fileConfiguration;
    }

    private Config config;

    public DependencyGrabber() {
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
    }

    public void grabDependencies(Instrumentation instrumentation) {
        this.saveDefaultConfig();
        this.config = new Config(this);

        this.getLogger().info("Loaded config.");
        this.getLogger().info("Includes: " + this.config.getIncludes());
        this.getLogger().info("Excludes: " + this.config.getExcludes());
        this.getLogger().info("Soft depends? " + this.config.isIncludeSoftdependencies());

        Collection<String> additionalIncludes = getAdditionalIncludes();
        this.config.getIncludes().addAll(additionalIncludes);
        this.getLogger().info("Additional includes: " + additionalIncludes);

        Map<String, PluginDescriptionFile> plugins = this.listPlugins();
        plugins.keySet().removeAll(this.config.getExcludes());

        Set<String> processedPlugins = new HashSet<>();
        Set<String> finalLoadSet = new HashSet<>();
        for (String pluginName : this.config.getIncludes()) {
            PluginDescriptionFile pdf = plugins.get(pluginName);
            if (pdf == null) {
                this.getLogger().warning("Unable to load plugin by name: " + pluginName);
                continue;
            }

            if (processedPlugins.contains(pdf.getName())) {
                continue;
            }

            this.recurseDependencies(pluginName, pdf, plugins, processedPlugins, finalLoadSet);
        }

        this.getLogger().info("Final load set: " + finalLoadSet);

        Transformer transformer = new Transformer(this, finalLoadSet, instrumentation);
        transformer.doTransformation();
    }

    private Map<String, PluginDescriptionFile> listPlugins() {
        // NOTE: Copied off of the paperspigot source for 1.8.8, minor edits added
        // Quieted output
        // Dry run
        // Removed certain catches related to loading plugins
        // Remove a unneeded warning if-checks
        // Return String collection instead of array of Plugin
        // Do not include soft-dependencies if configured
        // Use PDF as the value in plugins/loadedPlugins maps
        // Add a masterMap to avoid deletion while scanning dependencies

        Map<String, PluginDescriptionFile> masterMap = new HashMap<>();
        Map<String, PluginDescriptionFile> loadedPlugins = new HashMap<>();

        Map<String, PluginDescriptionFile> plugins = new HashMap<>();
        Map<String, Collection<String>> dependencies = new HashMap<>();
        Map<String, Collection<String>> softDependencies = new HashMap<>();

        Server server = Bukkit.getServer();

        // Hopefully this is the right directory...
        File directory = new File(this.getDataFolder().getParentFile(), "plugins");
        for (File file : directory.listFiles()) {
            PluginDescriptionFile description;
            try {
                description = getPluginDescription(file);
                String name = description.getName();
                if (name.equalsIgnoreCase("bukkit") || name.equalsIgnoreCase("minecraft") || name.equalsIgnoreCase("mojang")) {
                    continue;
                }
            } catch (InvalidDescriptionException ex) {
                continue;
            }

            plugins.put(description.getName(), description);
            masterMap.put(description.getName(), description);

            Collection<String> softDependencySet = description.getSoftDepend();
            if (this.config.isIncludeSoftdependencies() && softDependencySet != null && !softDependencySet.isEmpty()) {
                if (softDependencies.containsKey(description.getName())) {
                    // Duplicates do not matter, they will be removed together if applicable
                    softDependencies.get(description.getName()).addAll(softDependencySet);
                } else {
                    softDependencies.put(description.getName(), new LinkedList<>(softDependencySet));
                }
            }

            Collection<String> dependencySet = description.getDepend();
            if (dependencySet != null && !dependencySet.isEmpty()) {
                dependencies.put(description.getName(), new LinkedList<>(dependencySet));
            }

            Collection<String> loadBeforeSet = description.getLoadBefore();
            if (loadBeforeSet != null && !loadBeforeSet.isEmpty()) {
                for (String loadBeforeTarget : loadBeforeSet) {
                    if (softDependencies.containsKey(loadBeforeTarget)) {
                        softDependencies.get(loadBeforeTarget).add(description.getName());
                    } else {
                        // softDependencies is never iterated, so 'ghost' plugins aren't an issue
                        Collection<String> shortSoftDependency = new LinkedList<>();
                        shortSoftDependency.add(description.getName());
                        softDependencies.put(loadBeforeTarget, shortSoftDependency);
                    }
                }
            }
        }

        while (!plugins.isEmpty()) {
            boolean missingDependency = true;
            Iterator<String> pluginIterator = plugins.keySet().iterator();

            while (pluginIterator.hasNext()) {
                String plugin = pluginIterator.next();

                if (dependencies.containsKey(plugin)) {
                    Iterator<String> dependencyIterator = dependencies.get(plugin).iterator();

                    while (dependencyIterator.hasNext()) {
                        String dependency = dependencyIterator.next();

                        // Dependency loaded
                        if (loadedPlugins.containsKey(dependency)) {
                            dependencyIterator.remove();

                            // We have a dependency not found
                        } else if (!plugins.containsKey(dependency)) {
                            missingDependency = false;
                            pluginIterator.remove();
                            softDependencies.remove(plugin);
                            dependencies.remove(plugin);
                            break;
                        }
                    }

                    if (dependencies.containsKey(plugin) && dependencies.get(plugin).isEmpty()) {
                        dependencies.remove(plugin);
                    }
                }
                if (softDependencies.containsKey(plugin)) {
                    Iterator<String> softDependencyIterator = softDependencies.get(plugin).iterator();

                    while (softDependencyIterator.hasNext()) {
                        String softDependency = softDependencyIterator.next();

                        // Soft depend is no longer around
                        if (!plugins.containsKey(softDependency)) {
                            softDependencyIterator.remove();
                        }
                    }

                    if (softDependencies.get(plugin).isEmpty()) {
                        softDependencies.remove(plugin);
                    }
                }
                if (!(dependencies.containsKey(plugin) || softDependencies.containsKey(plugin)) && plugins.containsKey(plugin)) {
                    // We're clear to load, no more soft or hard dependencies left
                    pluginIterator.remove();
                    missingDependency = false;

                    loadedPlugins.put(plugin, masterMap.get(plugin));
                    continue;
                }
            }

            if (missingDependency) {
                // We now iterate over plugins until something loads
                // This loop will ignore soft dependencies
                pluginIterator = plugins.keySet().iterator();

                while (pluginIterator.hasNext()) {
                    String plugin = pluginIterator.next();

                    if (!dependencies.containsKey(plugin)) {
                        softDependencies.remove(plugin);
                        missingDependency = false;
                        pluginIterator.remove();

                        loadedPlugins.put(plugin, masterMap.get(plugin));
                        break;
                    }
                }
                // We have no plugins left without a depend
                if (missingDependency) {
                    softDependencies.clear();
                    dependencies.clear();
                    Iterator<PluginDescriptionFile> failedPluginIterator = plugins.values().iterator();

                    while (failedPluginIterator.hasNext()) {
                        failedPluginIterator.remove();
                    }
                }
            }
        }

        return loadedPlugins;
    }

    private void recurseDependencies(String pluginName, PluginDescriptionFile root, Map<String, PluginDescriptionFile> masterMap, Set<String> processedPlugins, Set<String> finalLoadSet) {
        processedPlugins.add(pluginName);
        finalLoadSet.add(pluginName);

        if (root.getDepend() != null && !root.getDepend().isEmpty()) {
            for (String dependency : root.getDepend()) {
                if (processedPlugins.contains(dependency)) {
                    continue;
                }

                PluginDescriptionFile pdf = masterMap.get(dependency);
                if (pdf == null) {
                    this.getLogger().warning("Unable to load dependency for " + pluginName + ": " + dependency);
                    continue;
                }

                this.recurseDependencies(dependency, pdf, masterMap, processedPlugins, finalLoadSet);
            }
        }

        if (this.config.isIncludeSoftdependencies() && root.getSoftDepend() != null && !root.getSoftDepend().isEmpty()) {
            for (String softDependency : root.getSoftDepend()) {
                if (processedPlugins.contains(softDependency)) {
                    continue;
                }

                PluginDescriptionFile pdf = masterMap.get(softDependency);
                if (pdf == null) {
                    continue;
                }

                this.recurseDependencies(softDependency, pdf, masterMap, processedPlugins, finalLoadSet);
            }
        }
    }

    private static Collection<String> getAdditionalIncludes() {
        Set<String> includes = new HashSet<>();

        String property = System.getProperty("dgIncludes");
        if (property != null) {
            String[] split = property.split(",");
            for (String pluginName : split) {
                includes.add(pluginName.trim());
            }
        }

        return includes;
    }

    private static PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
        // Copied from PaperSpigot source with no modifications

        Validate.notNull(file, "File cannot be null");

        JarFile jar = null;
        InputStream stream = null;

        try {
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry("plugin.yml");

            if (entry == null) {
                throw new InvalidDescriptionException(new FileNotFoundException("Jar does not contain plugin.yml"));
            }

            stream = jar.getInputStream(entry);

            return new PluginDescriptionFile(stream);

        } catch (IOException ex) {
            throw new InvalidDescriptionException(ex);
        } catch (YAMLException ex) {
            throw new InvalidDescriptionException(ex);
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException e) {
                }
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
