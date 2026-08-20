package de.marvin.playtime.bungee;

import de.marvin.playtime.bungee.config.ConfigHandler;
import de.marvin.playtime.bungee.listener.PlayerConnectionListener;
import de.marvin.playtime.core.Playtime;
import de.marvin.playtime.core.database.DatabaseHandler;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.ext.platforminject.api.PlatformEntrypoint;
import eu.cloudnetservice.ext.platforminject.api.stereotype.Dependency;
import eu.cloudnetservice.ext.platforminject.api.stereotype.PlatformPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

@Singleton
@PlatformPlugin(
        platform = "bungeecord",
        name = "PlaytimeAPI",
        authors = {"summervibing"},
        version = "1.0",
        pluginFileNames = {"bungee.yml"},
        description = "Playtime management system (proxy).",
        dependencies = {
                @Dependency(name = "CloudNet-Bridge"),
                @Dependency(name = "API")
        }
)
public class Bungee implements PlatformEntrypoint {

    private final Plugin plugin;
    private final PluginManager pluginManager;

    private final CloudServiceProvider cloudServiceProvider;

    private ConfigHandler config;

    private DatabaseHandler databaseHandler;

    @Inject
    public Bungee(
            @NotNull Plugin plugin,
            @NotNull PluginManager pluginManager,
            @NotNull CloudServiceProvider cloudServiceProvider
    ) {
        this.plugin = plugin;
        this.pluginManager = pluginManager;
        this.cloudServiceProvider = cloudServiceProvider;
    }

    @Override
    public void onLoad() {
        this.logger().info("Starting up playtime management system...");

        this.config = new ConfigHandler(this.plugin);

        // Initialize core
        var playtime = new Playtime();
        playtime.setup(
                this.logger(),
                this.config.toConfigurationValues(),
                this.cloudServiceProvider
        );

        // Get database instance
        this.databaseHandler = playtime.databaseHandler();

        // Register events
        this.registerEvents();

        this.logger().info("Successfully started up playtime management system.");
    }

    /**
     * Registers all necessary listeners.
     */
    private void registerEvents() {
        // BungeeCord events
        this.registerListener(new PlayerConnectionListener(this.databaseHandler));
    }

    /**
     * Registers a listener to the {@link PluginManager}.
     *
     * @param listener Listener to register
     */
    private void registerListener(
            @NotNull final Listener listener
    ) {
        this.pluginManager.registerListener(
                this.plugin,
                listener
        );
    }

    /**
     * Returns the plugin logger.
     *
     * @return {@link Logger} instance.
     */
    private Logger logger() {
        return this.plugin.getLogger();
    }

}
