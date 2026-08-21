package de.marvin.playtime.server.listener;

import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.listener.AwayStatusChangeListener;
import de.marvin.playtime.server.command.AFKCommand;
import de.marvin.playtime.server.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PlayerActivityListener implements Listener {

    private final JavaPlugin plugin;
    private final PlaytimeAPI playtimeAPI;
    private final Config config;

    /**
     * {@link AwayStatusChangeListener} that receives AFK status changes.
     */
    private final AwayStatusChangeListener awayStatusChangeListener = this::handleAwayStatusChange;

    private int movementCheckTaskId = -1;
    private final Map<UUID, Location> lastPlayerLocations = new HashMap<>();

    public PlayerActivityListener(
            JavaPlugin plugin,
            PlaytimeAPI playtimeAPI,
            Config config
    ) {
        this.plugin = plugin;
        this.playtimeAPI = playtimeAPI;
        this.config = config;

        this.playtimeAPI.registerAwayStatusChangeListener(this.awayStatusChangeListener);
        this.checkMovement();
    }

    // Movement Check

    /**
     * Checks for player movement every 2 seconds and updates their
     * last activity if they have moved.
     */
    public void checkMovement() {
        this.movementCheckTaskId =
                this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () ->
                        this.plugin.getServer().getOnlinePlayers().forEach(player -> {
                            var uniqueId = player.getUniqueId();
                            var lastLocation = this.lastPlayerLocations.get(uniqueId);
                            var currentLocation = player.getLocation();
                            this.lastPlayerLocations.put(uniqueId, currentLocation);
                            if (lastLocation == null || currentLocation.equals(lastLocation)) return;
                            this.playtimeAPI.updateLastActivity(uniqueId);
                        }), 0L, 40L
                ).getTaskId();
    }

    /**
     * Removes the player's last known location when they quit.
     *
     * @param event player quit event
     */
    @EventHandler
    public void handleMovementQuit(
            PlayerQuitEvent event
    ) {
        var uniqueId = event.getPlayer().getUniqueId();
        this.lastPlayerLocations.remove(uniqueId);
    }

    // Interaction Check

    /**
     * Updates player's last activity on interaction.
     *
     * @param event player interact event
     */
    @EventHandler
    public void handle(
            PlayerInteractEvent event
    ) {
        this.playtimeAPI.updateLastActivity(event.getPlayer().getUniqueId());
    }

    // Damage Check

    /**
     * Updates player's last activity on entity damage.
     *
     * @param event entity damage by entity event
     */
    @EventHandler
    public void handle(
            EntityDamageByEntityEvent event
    ) {
        if (!(event.getDamager() instanceof Player player)) return;
        this.playtimeAPI.updateLastActivity(player.getUniqueId());
    }

    // Chat Check

    /**
     * Updates player's last activity on chat.
     *
     * @param event async player chat event
     */
    @EventHandler
    public void handle(
            AsyncPlayerChatEvent event
    ) {
        this.playtimeAPI.updateLastActivity(event.getPlayer().getUniqueId());
    }

    /**
     * Updates player's last activity on command usage.
     *
     * @param event player command preprocess event
     */
    @EventHandler
    public void handle(
            PlayerCommandPreprocessEvent event
    ) {
        if (this.isAfkCommand(event.getMessage())) return;
        this.playtimeAPI.updateLastActivity(event.getPlayer().getUniqueId());
    }

    /**
     * Whether the given message is the plugins AFK command.
     *
     * @param message Message to check
     * @return {@code true} if the message is the AFK command, {@code false} otherwise
     */
    private boolean isAfkCommand(
            @NotNull String message
    ) {
        if (message.isBlank()) return false;

        var separatorIndex = message.indexOf(' ');
        var commandName = separatorIndex == -1
                ? message
                : message.substring(0, separatorIndex);

        if (commandName.startsWith("/")) commandName = commandName.substring(1);
        if (commandName.isBlank()) return false;

        var components = commandName.split(":", 2);

        // If "/afk" is used
        if (components.length == 1) return commandName.equalsIgnoreCase(AFKCommand.NAME);

        var namespace = components[0];
        var pluginName = this.plugin.getName().toLowerCase(Locale.ROOT);
        var command = components[1];

        // If "/playtimeapi:afk" is used
        return namespace.equalsIgnoreCase(pluginName) && command.equalsIgnoreCase(AFKCommand.NAME);
    }

    // Away Status Changes

    /**
     * Informs a player after their AFK status changed. As status changes can originate from the asynchronous
     * session updater, messages are dispatched on the server thread.
     *
     * @param uniqueId {@link UUID} of the player
     * @param status   New AFK status
     */
    private void handleAwayStatusChange(
            @NotNull UUID uniqueId,
            boolean status
    ) {
        var sendMessage = (Runnable) () -> {
            var player = this.plugin.getServer().getPlayer(uniqueId);
            if (player == null) return;

            var messageKey = status
                    ? "now-away-from-keyboard"
                    : "not-away-from-keyboard-anymore";
            player.sendMessage(this.config.message(messageKey));
        };

        if (Bukkit.isPrimaryThread()) {
            sendMessage.run();
            return;
        }
        this.plugin.getServer().getScheduler().runTask(this.plugin, sendMessage);
    }

    // Shutdown

    /**
     * Shuts down the movement check task, clears stored locations and removes the
     * {@link AwayStatusChangeListener}.
     */
    public void shutdown() {
        if (this.movementCheckTaskId != -1)
            this.plugin.getServer().getScheduler().cancelTask(this.movementCheckTaskId);
        this.movementCheckTaskId = -1;
        this.lastPlayerLocations.clear();
        this.playtimeAPI.unregisterAwayStatusChangeListener(this.awayStatusChangeListener);
    }

}
