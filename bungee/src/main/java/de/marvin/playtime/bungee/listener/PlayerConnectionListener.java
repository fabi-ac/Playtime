package de.marvin.playtime.bungee.listener;

import de.marvin.playtime.core.database.DatabaseHandler;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import de.marvin.playtime.core.session.Session;

/**
 * Listens to connection events to load and save {@link Session}.
 */
public class PlayerConnectionListener implements Listener {

    private final DatabaseHandler databaseHandler;

    public PlayerConnectionListener(
            DatabaseHandler databaseHandler
    ) {
        this.databaseHandler = databaseHandler;
    }

    /**
     * Caches user's {@link Session}.
     *
     * @param event login event
     */
    @EventHandler
    public void handle(
            LoginEvent event
    ) {
        if (event.isCancelled()) return;
        this.databaseHandler.cache(event.getConnection().getUniqueId());
    }

    /**
     * Saves and uncaches user's {@link Session}.
     *
     * @param event player disconnect event
     */
    @EventHandler
    public void handle(
            PlayerDisconnectEvent event
    ) {
        this.databaseHandler.saveAndUncache(event.getPlayer().getUniqueId());
    }

}
