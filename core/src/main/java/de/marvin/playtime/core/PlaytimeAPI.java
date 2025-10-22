package de.marvin.playtime.core;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface PlaytimeAPI {

    /**
     * Caches the {@link Session} of a player found in the database.
     *
     * @param uniqueId {@link UUID} of the player
     */
    void cacheSession(@NotNull UUID uniqueId);

    /**
     * Retrieves the cached {@link Session} of given {@link UUID}.
     * If not cached, a default {@link Session} is returned.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of given {@link UUID} or a default
     * {@link Session} if not cached.
     */
    Session session(@NotNull UUID uniqueId);

    /**
     * Forces retrieval of the {@link Session} of given {@link UUID}
     * first from Redis, then from the database if not cached and
     * only returns {@code null} if not found in both.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the player's {@link Session}.
     */
    CloudFuture<Session> forceSession(@NotNull UUID uniqueId);

    /**
     * Sets whether playtime should be currently counted for the
     * {@link Session} of given {@link UUID}.
     *
     * @param uniqueId      {@link UUID} of the player
     * @param countPlaytime {@code true} to count playtime,
     *                      {@code false} otherwise
     */
    void setCountPlaytime(@NotNull UUID uniqueId, boolean countPlaytime);

    /**
     * Sets whether the {@link Session} of given {@link UUID} is
     * away from keyboard.
     *
     * @param uniqueId {@link UUID} of the player
     * @param away     {@code true} if the player is afk,
     *                 {@code false} otherwise
     */
    void setAwayStatus(@NotNull UUID uniqueId, boolean away);

    /**
     * Updates the last activity timestamp of the {@link Session}
     * of given {@link UUID}.
     *
     * @param uniqueId {@link UUID} of the player
     */
    void updateLastActivity(@NotNull UUID uniqueId);

    /**
     * Saves and uncaches the {@link Session} of given {@link UUID}
     * to the database if cached.
     *
     * @param uniqueId {@link UUID} of the player
     */
    void saveAndUncacheSession(@NotNull UUID uniqueId);

    /**
     * Updates the {@link Session} of given {@link UUID} in
     * service's sessions if cached, otherwise tries to update cached
     * Redis session and if not found there, updates it in the database
     * directly.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis onlinetime in milliseconds
     * @param playtimeInMillis   playtime in milliseconds
     */
    void update(@NotNull UUID uniqueId, @Nullable Long onlinetimeInMillis, @Nullable Long playtimeInMillis);

    /**
     * Resets the {@link Session} of given {@link UUID} in
     * service's sessions if cached, otherwise tries to reset cached
     * Redis session and if not found there, resets it in the database
     * directly.
     *
     * @param uniqueId {@link UUID} of the player
     */
    void reset(@NotNull UUID uniqueId);

    /**
     * Starts the session updater task that updates all sessions
     * of online players every second.
     */
    void startSessionUpdater();

    /**
     * Shuts down the session handler, saving and clearing all
     * sessions.
     */
    void shutdown();

}
