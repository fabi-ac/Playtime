package de.marvin.playtime.server.config;

import de.marvin.api.dependencies.lang3.tuple.Pair;
import de.marvin.playtime.core.config.ConfigurationValues;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class Config extends ConfigHandler {

    public Config(
            @NotNull final JavaPlugin plugin
    ) {
        super(plugin);
    }

    /**
     * Converts this config to {@link ConfigurationValues} format.
     *
     * @return {@link Config} in {@link ConfigurationValues} format.
     */
    public ConfigurationValues toConfigurationValues() {
        return new ConfigurationValues(
                this.databaseTable(),
                this.redisPrefix(),
                this.afkThreshold(),
                this.cacheDelay()
        );
    }

    /**
     * Gets the level database name from the config.
     *
     * @return Playtime database name.
     */
    public String databaseTable() {
        return this.getString("database-table", "playtime");
    }

    /**
     * Gets the Redis prefix from the config.
     *
     * @return Redis prefix.
     */
    public String redisPrefix() {
        return this.getString("redis-prefix", "level");
    }

    /**
     * Gets threshold in milliseconds after which a player is considered AFK.
     *
     * @return Threshold in milliseconds.
     */
    public long afkThreshold() {
        return this.getLong("afk-threshold", 300000);
    }

    /**
     * Gets ticks after which playtime should be retrieved out
     * of Redis cache.
     *
     * @return Ticks after which playtime should be retrieved out
     * of Redis cache.
     */
    public int cacheDelay() {
        return this.getInt("cache-delay", -1);
    }

    /**
     * Gets a message from the config and replaces placeholders with provided arguments.
     *
     * @param message      message key to retrieve
     * @param placeholders placeholders to replace in the message
     * @return Message from the config with placeholders replaced, or error message if not found.
     */
    @SafeVarargs
    public final String message(
            String message,
            Pair<String, Object>... placeholders
    ) {
        var raw = this.getString("messages." + message, "Message not found: " + message);
        for (Pair<String, Object> placeholder : placeholders)
            raw = raw.replaceAll("<%s>".formatted(placeholder.getLeft()), String.valueOf(placeholder.getRight()));
        var formatted = ChatColor.translateAlternateColorCodes('&', raw);

        return formatted;
    }

}
