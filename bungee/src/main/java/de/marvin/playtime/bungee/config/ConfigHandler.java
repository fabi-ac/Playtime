package de.marvin.playtime.bungee.config;

import de.marvin.playtime.core.config.ConfigurationValues;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

public class ConfigHandler {

    private final Plugin plugin;
    private final Logger logger;
    private final Configuration configuration;

    public ConfigHandler(
            @NotNull final Plugin plugin
    ) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.createIfNotExists();
        this.configuration = this.load();
    }

    /**
     * Converts this config to {@link ConfigurationValues} format.
     *
     * @return {@link ConfigHandler} in {@link ConfigurationValues} format.
     */
    public ConfigurationValues toConfigurationValues() {
        return new ConfigurationValues(
                this.databaseTable(),
                this.redisPrefix(),
                null,
                null
        );
    }

    /**
     * Gets the level database name from the config.
     *
     * @return Playtime database name.
     */
    public String databaseTable() {
        return this.configuration.getString("database-table", "playtime");
    }

    /**
     * Gets the Redis prefix from the config.
     *
     * @return Redis prefix.
     */
    public String redisPrefix() {
        return this.configuration.getString("redis-prefix", "playtime");
    }

    /**
     * Creates the configuration file if it does not exist.
     */
    public void createIfNotExists() {
        try {
            // Create plugin config folder if it does not exist
            if (!this.plugin.getDataFolder().exists()) this.plugin.getDataFolder().mkdir();

            var configFile = new File(this.plugin.getDataFolder(), "config.yml");
            if (configFile.exists()) return;

            // Place default config in plugin folder
            try (var inputStream = this.getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (inputStream == null) {
                    this.logger.warning("Default configuration file not found. Please contact the developer.");
                    return;
                }

                try (var outputStream = new FileOutputStream(configFile)) {
                    inputStream.transferTo(outputStream);
                }

                this.logger.info("Created default configuration file.");
            }
        } catch (Exception exception) {
            this.logger.warning("Could not create configuration files: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    /**
     * Loads the configuration from file.
     *
     * @return Loaded {@link Configuration}.
     */
    private Configuration load() {
        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(this.plugin.getDataFolder(), "config.yml"));
        } catch (IOException exception) {
            this.logger.warning("Failed to access configuration files: " + exception.getMessage());
            return new Configuration();
        }
    }

}