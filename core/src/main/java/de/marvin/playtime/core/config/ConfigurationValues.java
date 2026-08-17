package de.marvin.playtime.core.config;

import org.jetbrains.annotations.NotNull;

/**
 * Class that holds all configuration values passed
 * from either the BungeeCord or Spigot configuration.
 */
public class ConfigurationValues {

    // General
    private final String databaseTable;
    private final String redisPrefix;

    // Spigot-specific
    private final Long afkThreshold;

    /**
     * Creates an object that holds all configuration values.
     *
     * @param databaseTable      name of the database table
     * @param redisPrefix        prefix for redis keys
     * @param afkThreshold       threshold in seconds after which a player is considered AFK
     */
    public ConfigurationValues(
            @NotNull String databaseTable,
            @NotNull String redisPrefix,
            Long afkThreshold
    ) {
        this.databaseTable = databaseTable;
        this.redisPrefix = redisPrefix;
        this.afkThreshold = afkThreshold;
    }

    /**
     * Gets the name of the database table.
     *
     * @return Name of the database table.
     */
    public String databaseTable() {
        return this.databaseTable;
    }

    /**
     * Gets the prefix for redis keys.
     *
     * @return Prefix for redis keys.
     */
    public String redisPrefix() {
        return this.redisPrefix;
    }

    /**
     * Gets threshold in milliseconds after which a player is considered AFK.
     * <p>
     * <b>Note:</b> Is only set on Spigot services.
     *
     * @return Threshold in milliseconds.
     */
    public Long afkThreshold() {
        return this.afkThreshold;
    }

}
