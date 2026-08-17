package de.marvin.playtime.bungee.listener;

import de.marvin.playtime.core.database.DatabaseHandler;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

/**
 * Persists session data from Redis when a player disconnects from the proxy.
 */
public class PlayerConnectionListener implements Listener {

    private final DatabaseHandler databaseHandler;

    public PlayerConnectionListener(
            DatabaseHandler databaseHandler
    ) {
        this.databaseHandler = databaseHandler;
    }

    /**
     * Saves and uncaches the player's session.
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
