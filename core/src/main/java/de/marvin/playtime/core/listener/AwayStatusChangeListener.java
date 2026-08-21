package de.marvin.playtime.core.listener;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Receives changes to a player's away from keyboard status.
 */
@FunctionalInterface
public interface AwayStatusChangeListener {

    /**
     * Called after a player's away from keyboard status changed.
     *
     * @param uniqueId {@link UUID} of the player
     * @param status   {@code true} if the player is now away from keyboard, {@code false} otherwise
     */
    void handleAwayStatusChange(@NotNull UUID uniqueId, boolean status);

}
