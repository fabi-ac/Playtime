package de.marvin.playtime.server.listener;

import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.server.config.Config;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import de.marvin.playtime.core.session.Session;

public class PlayerConnectionListener implements Listener {

    private final JavaPlugin plugin;
    private final PlaytimeAPI playtimeAPI;
    private final Config config;

    public PlayerConnectionListener(
            JavaPlugin plugin,
            PlaytimeAPI playtimeAPI,
            Config config
    ) {
        this.plugin = plugin;
        this.playtimeAPI = playtimeAPI;
        this.config = config;
    }

    /**
     * Caches user's {@link Session} in service.
     *
     * @param event player join event
     */
    @EventHandler
    public void handle(
            PlayerJoinEvent event
    ) {
        var uniqueId = event.getPlayer().getUniqueId();
        var cacheDelay = this.config.cacheDelay();
        if (cacheDelay < 1) {
            this.playtimeAPI.cacheSession(uniqueId);
            return;
        }
        this.plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                this.plugin,
                () -> this.playtimeAPI.cacheSession(uniqueId),
                cacheDelay
        );
    }

    /**
     * Saves and uncaches user's {@link Session} out of service.
     *
     * @param event player quit event
     */
    @EventHandler
    public void handle(
            PlayerQuitEvent event
    ) {
        this.playtimeAPI.saveAndUncacheSession(event.getPlayer().getUniqueId());
    }

}
