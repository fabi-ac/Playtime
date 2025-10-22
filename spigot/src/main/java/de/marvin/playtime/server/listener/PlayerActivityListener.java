package de.marvin.playtime.server.listener;

import com.google.common.collect.Maps;
import de.marvin.playtime.core.PlaytimeAPI;
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

import java.util.Map;
import java.util.UUID;

public class PlayerActivityListener implements Listener {

    private final JavaPlugin plugin;
    private final PlaytimeAPI playtimeAPI;

    private int movementCheckTaskId = -1;
    private final Map<UUID, Location> lastPlayerLocations = Maps.newConcurrentMap();

    public PlayerActivityListener(
            JavaPlugin plugin,
            PlaytimeAPI playtimeAPI
    ) {
        this.plugin = plugin;
        this.playtimeAPI = playtimeAPI;

        this.checkMovement();
    }

    // Movement Check

    /**
     * Checks for player movement every 2 seconds and updates their
     * last activity if they have moved.
     */
    public void checkMovement() {
        this.movementCheckTaskId =
                this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, () ->
                        this.plugin.getServer().getOnlinePlayers().forEach(player -> {
                            var uniqueId = player.getUniqueId();
                            var lastLocation = this.lastPlayerLocations.get(uniqueId);
                            var currentLocation = player.getLocation();
                            if (lastLocation != null && lastLocation.equals(currentLocation)) return;
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
        this.lastPlayerLocations.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Shuts down the movement check task and clears stored locations.
     */
    public void shutdown() {
        if (this.movementCheckTaskId == -1) return;
        this.plugin.getServer().getScheduler().cancelTask(this.movementCheckTaskId);
        this.movementCheckTaskId = -1;
        this.lastPlayerLocations.clear();
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
        this.playtimeAPI.updateLastActivity(event.getPlayer().getUniqueId());
    }

}
