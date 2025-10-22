package de.marvin.playtime.core;

import de.marvin.playtime.core.config.ConfigurationValues;
import de.marvin.playtime.core.database.DatabaseHandler;
import de.marvin.playtime.core.session.SessionHandler;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public final class Playtime {

    private static boolean initialized = false;

    private ConfigurationValues configurationValues;
    private DatabaseHandler databaseHandler;

    private static PlaytimeAPI api;

    /**
     * Initializes {@link Playtime} core.
     *
     * @param logger {@link Logger} instance to use
     * @param configurationValues {@link ConfigurationValues} to use
     */
    public void setup(
            @NotNull Logger logger,
            @NotNull ConfigurationValues configurationValues
    ) {
        if (initialized) throw new IllegalStateException("PlaytimeAPI core is already initialized.");
        initialized = true;

        this.configurationValues = configurationValues;

        this.databaseHandler = new DatabaseHandler(
                logger,
                this.configurationValues
        );

        api = new SessionHandler(
                this.databaseHandler,
                this.configurationValues
        );

        logger.info("Initialized playtime system core.");
    }

    /**
     * Gets {@link PlaytimeAPI} instance.
     *
     * @return {@link PlaytimeAPI} instance
     */
    public static PlaytimeAPI api() {
        if (!initialized) throw new IllegalStateException("PlaytimeAPI core is not initialized yet.");
        return api;
    }

    /**
     * Gets {@link DatabaseHandler} instance.
     *
     * @return {@link DatabaseHandler} instance
     */
    public DatabaseHandler databaseHandler() {
        if (!initialized) throw new IllegalStateException("PlaytimeAPI core is not initialized yet.");
        return this.databaseHandler;
    }

    /**
     * Gets {@link ConfigurationValues} instance.
     *
     * @return {@link ConfigurationValues} instance
     */
    public ConfigurationValues configurationValues() {
        if (!initialized) throw new IllegalStateException("PlaytimeAPI core is not initialized yet.");
        return this.configurationValues;
    }

}
