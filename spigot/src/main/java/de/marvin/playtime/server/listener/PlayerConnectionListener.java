package de.marvin.playtime.server.listener;

import de.marvin.playtime.core.PlaytimeAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import de.marvin.playtime.core.session.Session;

public class PlayerConnectionListener implements Listener {

    private final PlaytimeAPI playtimeAPI;

    public PlayerConnectionListener(
            PlaytimeAPI playtimeAPI
    ) {
        this.playtimeAPI = playtimeAPI;
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
        this.playtimeAPI.cacheSession(event.getPlayer().getUniqueId());
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
