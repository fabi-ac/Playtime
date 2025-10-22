package de.marvin.playtime.server;

import de.marvin.api.core.Cloud;
import de.marvin.api.core.api.UserAPI;
import de.marvin.playtime.core.Playtime;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.database.DatabaseHandler;
import de.marvin.playtime.server.command.PlaytimeCommand;
import de.marvin.playtime.server.config.Config;
import de.marvin.playtime.server.listener.PlayerActivityListener;
import de.marvin.playtime.server.listener.PlayerConnectionListener;
import eu.cloudnetservice.ext.platforminject.api.PlatformEntrypoint;
import eu.cloudnetservice.ext.platforminject.api.stereotype.Command;
import eu.cloudnetservice.ext.platforminject.api.stereotype.Dependency;
import eu.cloudnetservice.ext.platforminject.api.stereotype.PlatformPlugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

@Singleton
@PlatformPlugin(
        platform = "bukkit",
        name = "PlaytimeAPI",
        authors = {"summervibing"},
        version = "1.0",
        pluginFileNames = {"plugin.yml"},
        description = "Playtime management system (server).",
        dependencies = {
                @Dependency(name = "CloudNet-Bridge"),
                @Dependency(name = "API")
        },
        commands = {
                @Command(
                        name = "playtime",
                        description = "Lookup current online- and playtime.",
                        aliases = {"onlinetime", "pt", "ot"}
                )
        }
)
public class Server implements PlatformEntrypoint {

    private final JavaPlugin plugin;
    private final PluginManager pluginManager;

    private Config config;

    private PlaytimeAPI api;
    private DatabaseHandler databaseHandler;

    private UserAPI userAPI;

    private PlayerActivityListener playerActivityListener;

    @Inject
    public Server(
            @NotNull JavaPlugin plugin,
            @NotNull PluginManager pluginManager
    ) {
        this.plugin = plugin;
        this.pluginManager = pluginManager;
    }

    @Override
    public void onLoad() {
        this.logger().info("Starting up playtime management system...");

        this.userAPI = Cloud.userAPI();

        this.config = new Config(this.plugin);

        // Initialize core
        var playtime = new Playtime();
        playtime.setup(
                this.logger(),
                this.config.toConfigurationValues()
        );

        // Get api and database instances
        this.api = Playtime.api();
        this.databaseHandler = playtime.databaseHandler();

        // Start session updater
        this.api.startSessionUpdater();

        // Register events and commands
        this.registerEvents();
        this.registerCommands();

        this.logger().info("Successfully started up playtime management system.");
    }

    @Override
    public void onDisable() {
        // Save all cached playtime data and shutdown api
        this.api.shutdown();
        this.playerActivityListener.shutdown();
    }

    /**
     * Registers all necessary listeners.
     */
    private void registerEvents() {
        this.registerListener(new PlayerConnectionListener(
                this.plugin,
                api,
                this.config
        ));

        this.registerListener(this.playerActivityListener = new PlayerActivityListener(
                this.plugin,
                this.api
        ));
    }

    /**
     * Registers all commands.
     */
    private void registerCommands() {
        this.registerCommand(
                "playtime",
                new PlaytimeCommand(
                        api,
                        this.config,
                        this.userAPI
                ),
                null
        );
    }

    /**
     * Registers a listener to the {@link PluginManager}.
     *
     * @param listener Listener to register
     */
    private void registerListener(
            @NotNull final Listener listener
    ) {
        this.pluginManager.registerEvents(
                listener,
                this.plugin
        );
    }

    /**
     * Registers a {@link CommandExecutor} and optionally
     * a {@link TabCompleter} to the given command.
     *
     * @param commandName          name of the command
     * @param commandInstance      command executor instance
     * @param tabCompleterInstance tab completer instance
     */
    private void registerCommand(
            @NotNull final String commandName,
            @NotNull final CommandExecutor commandInstance,
            @Nullable final TabCompleter tabCompleterInstance
    ) {
        // In case a command somehow is not defined in plugin.yml
        var command = this.plugin.getCommand(commandName);
        if (command == null) {
            this.plugin.getLogger().severe(
                    "Command '%s' is not defined in plugin.yml. Disabling plugin...".formatted(commandName)
            );
            this.plugin.getServer().getPluginManager().disablePlugin(this.plugin);
            return;
        }

        // Set command executor
        command.setExecutor(commandInstance);

        // Set tab completer if provided
        if (tabCompleterInstance == null) return;
        command.setTabCompleter(tabCompleterInstance);
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
