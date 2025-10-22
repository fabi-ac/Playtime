package de.marvin.playtime.server.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class ConfigHandler {

    private final FileConfiguration configuration;

    private final Map<String, Object> cache = new HashMap<>();

    public ConfigHandler(
            @NotNull final JavaPlugin plugin
    ) {
        plugin.saveDefaultConfig();
        this.configuration = plugin.getConfig();
    }

    public String getString(
            @NotNull final String path,
            @NotNull final String def
    ) {
        if (this.cache.containsKey(path)) return (String) this.cache.get(path);
        String value = this.configuration.getString(path, def);
        this.cache.put(path, value);
        return value;
    }

    public int getInt(
            @NotNull final String path,
            final int def
    ) {
        if (this.cache.containsKey(path)) return (int) this.cache.get(path);
        int value = this.configuration.getInt(path, def);
        this.cache.put(path, value);
        return value;
    }

    public double getDouble(
            @NotNull final String path,
            final double def
    ) {
        if (this.cache.containsKey(path)) return (double) this.cache.get(path);
        double value = this.configuration.getDouble(path, def);
        this.cache.put(path, value);
        return value;
    }

    public long getLong(
            @NotNull final String path,
            final long def
    ) {
        if (this.cache.containsKey(path)) return (long) this.cache.get(path);
        long value = this.configuration.getLong(path, def);
        this.cache.put(path, value);
        return value;
    }

    public boolean getBoolean(
            @NotNull final String path,
            final boolean def
    ) {
        if (this.cache.containsKey(path)) return (boolean) this.cache.get(path);
        boolean value = this.configuration.getBoolean(path, def);
        this.cache.put(path, value);
        return value;
    }

    public void clearCache() {
        this.cache.clear();
    }

}