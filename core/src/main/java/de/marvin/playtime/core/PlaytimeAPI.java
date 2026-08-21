package de.marvin.playtime.core;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.playtime.core.listener.AwayStatusChangeListener;
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
     * Attempts to retrieve the {@link Session} of the given player from the local cache, and if not present,
     * returns {@link Session#defaultSession(UUID)}.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of player with given {@link UUID} or {@link Session#defaultSession(UUID)} if not
     * found
     */
    Session session(@NotNull UUID uniqueId);

    /**
     * Attempts to retrieve the {@link Session} of the given player from the local cache, and if not present,
     * from the database. If not found in either, {@link Session#defaultSession(UUID)} is returned.
     * <p>
     * The order of retrieval is as follows:
     * <ol>
     *     <li>Check the local cache for the {@link Session}.</li>
     *     <li>If not found, search the Redis cache for the {@link Session}.</li>
     *     <li>If still not found, query the database for the {@link Session}.</li>
     *     <li>If the {@link Session} is not found in any of the above, return
     *     {@link Session#defaultSession(UUID)}.</li>
     * </ol>
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the player's {@link Session}, or
     * {@link Session#defaultSession(UUID)} if not found
     */
    CloudFuture<@NotNull Session> sessionOrDefault(@NotNull UUID uniqueId);

    /**
     * Attempts to retrieve the {@link Session} of the given player from the local cache, and if not present,
     * from the database. If not found in either, {@code null} is returned.
     * <p>
     * The order of retrieval is as follows:
     * <ol>
     *     <li>Check the local cache for the {@link Session}.</li>
     *     <li>If not found, search the Redis cache for the {@link Session}.</li>
     *     <li>If still not found, query the database for the {@link Session}.</li>
     *     <li>If the {@link Session} is not found in any of the above, return {@code null}.</li>
     * </ol>
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the player's {@link Session}, or {@code null} if not found
     */
    CloudFuture<@Nullable Session> sessionOrNull(@NotNull UUID uniqueId);

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
     * Toggles whether the {@link Session} of the given {@link UUID} is away from keyboard.
     *
     * @param uniqueId {@link UUID} of the player
     */
    void toggleAwayStatus(@NotNull UUID uniqueId);

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
     * Updates the {@link Session} of given {@link UUID} if it is neither cached locally nor in Redis.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis onlinetime in milliseconds
     * @param playtimeInMillis   playtime in milliseconds
     * @return {@code true} if the {@link Session} was updated, {@code false} if it is currently cached
     */
    boolean update(@NotNull UUID uniqueId, @Nullable Long onlinetimeInMillis, @Nullable Long playtimeInMillis);

    /**
     * Resets the {@link Session} of given {@link UUID} if it is neither cached locally nor in Redis.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@code true} if the {@link Session} was reset, {@code false} if it is currently cached
     */
    boolean reset(@NotNull UUID uniqueId);

    /**
     * Registers a listener for player AFK status changes.
     *
     * @param listener {@link AwayStatusChangeListener} to register
     */
    void registerAwayStatusChangeListener(@NotNull AwayStatusChangeListener listener);

    /**
     * Unregisters a listener for player AFK status changes.
     *
     * @param listener {@link AwayStatusChangeListener} to unregister
     */
    void unregisterAwayStatusChangeListener(@NotNull AwayStatusChangeListener listener);

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
