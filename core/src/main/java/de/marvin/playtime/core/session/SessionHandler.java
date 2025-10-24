package de.marvin.playtime.core.session;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.api.dependencies.guava.collect.Maps;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.config.ConfigurationValues;
import de.marvin.playtime.core.database.DatabaseHandler;
import de.marvin.playtime.core.util.TaskScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SessionHandler implements PlaytimeAPI {

    private final DatabaseHandler databaseHandler;

    private static Long afkThreshold = 300000L;

    /**
     * Holds cached {@link Session}s of currently online players.
     */
    private final ConcurrentMap<UUID, Session> cachedSessions = Maps.newConcurrentMap();

    /**
     * Scheduled task that updates sessions periodically.
     */
    private ScheduledFuture<?> sessionUpdater;

    public SessionHandler(
            @NotNull DatabaseHandler databaseHandler,
            @NotNull ConfigurationValues configurationValues
    ) {
        this.databaseHandler = databaseHandler;

        SessionHandler.afkThreshold = configurationValues.afkThreshold();
    }

    /**
     * Caches the {@link Session} of a player found in the database
     * into {@link SessionHandler#cachedSessions}.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void cacheSession(
            @NotNull UUID uniqueId
    ) {
        var session = this.databaseHandler.session(uniqueId);
        this.cachedSessions.put(session.uniqueId(), session);
    }

    /**
     * Retrieves the cached {@link Session} of given {@link UUID} from
     * {@link SessionHandler#cachedSessions}. If not cached, a default {@link Session}
     * is returned.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of given {@link UUID} or a default {@link Session} if
     * not cached.
     */
    public Session session(
            @NotNull UUID uniqueId
    ) {
        return this.cachedSessions.getOrDefault(
                uniqueId,
                Session.defaultSession(uniqueId)
        );
    }

    /**
     * Forces retrieval of the {@link Session} of given {@link UUID}
     * first from Redis, then from the database if not cached and
     * only returns {@code null} if not found in both.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the player's {@link Session}.
     */
    public CloudFuture<Session> forceSession(
            @NotNull UUID uniqueId
    ) {
        if (this.cachedSessions.containsKey(uniqueId))
            return new CloudFuture<>(this.session(uniqueId));
        return this.databaseHandler.forceSession(uniqueId);
    }

    /**
     * Sets whether playtime should be currently counted for the
     * {@link Session} of given {@link UUID} in {@link SessionHandler#cachedSessions}.
     *
     * @param uniqueId      {@link UUID} of the player
     * @param countPlaytime {@code true} to count playtime,
     *                      {@code false} otherwise
     */
    public void setCountPlaytime(
            @NotNull UUID uniqueId,
            boolean countPlaytime
    ) {
        var session = this.cachedSessions.get(uniqueId);
        if (session != null) session.setCountPlaytime(countPlaytime);
    }

    /**
     * Sets whether the {@link Session} of given {@link UUID}
     * in {@link SessionHandler#cachedSessions} is away from keyboard.
     *
     * @param uniqueId {@link UUID} of the player
     * @param away     {@code true} if the player is afk,
     *                 {@code false} otherwise
     */
    public void setAwayStatus(
            @NotNull UUID uniqueId,
            boolean away
    ) {
        var session = this.cachedSessions.get(uniqueId);
        if (session != null) session.setAwayFromKeyboard(away);
    }

    /**
     * Updates the last activity timestamp of the {@link Session}
     * of given {@link UUID} in {@link SessionHandler#cachedSessions}.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void updateLastActivity(
            @NotNull UUID uniqueId
    ) {
        var session = this.cachedSessions.get(uniqueId);
        if (session != null) session.updateLastActivity();
    }

    /**
     * Saves and uncaches the {@link Session} of given {@link UUID}
     * from {@link SessionHandler#cachedSessions} to the database if cached.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void saveAndUncacheSession(
            @NotNull UUID uniqueId
    ) {
        var session = this.cachedSessions.remove(uniqueId);
        if (session == null) return;
        this.databaseHandler.update(
                uniqueId,
                session.onlinetimeInMillis(),
                session.playtimeInMillis()
        );
    }

    /**
     * Updates the {@link Session} of given {@link UUID}
     * either in {@link SessionHandler#cachedSessions} if cached,
     * otherwise tries to update cached Redis session and if not found there,
     * update it in the database directly.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis onlinetime in milliseconds
     * @param playtimeInMillis   playtime in milliseconds
     */
    public void update(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis
    ) {
        var session = this.cachedSessions.get(uniqueId);
        if (session != null) {
            session.updateTimes(
                    onlinetimeInMillis,
                    playtimeInMillis
            );
            return;
        }
        this.databaseHandler.update(
                uniqueId,
                onlinetimeInMillis,
                playtimeInMillis
        );
    }

    /**
     * Resets the {@link Session} of given {@link UUID} in
     * {@link SessionHandler#cachedSessions} if cached, otherwise
     * tries to reset cached Redis session and if not found there,
     * resets it in the database directly.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void reset(
            @NotNull UUID uniqueId
    ) {
        var session = this.cachedSessions.get(uniqueId);
        if (session != null) {
            this.cachedSessions.put(
                    uniqueId,
                    Session.defaultSession(uniqueId)
            );
            return;
        }
        this.databaseHandler.reset(uniqueId);
    }

    /**
     * Starts the session updater task that updates all {@link SessionHandler#cachedSessions}
     * of online players every second.
     */
    public void startSessionUpdater() {
        this.sessionUpdater = TaskScheduler.scheduleTask(
                this::updateSessions,
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    /**
     * Updates all {@link SessionHandler#cachedSessions} of online players.
     */
    private void updateSessions() {
        this.cachedSessions.values().forEach(Session::update);
    }

    /**
     * Shuts down the session handler, saving and clearing all
     * {@link SessionHandler#cachedSessions}.
     */
    public void shutdown() {
        if (this.sessionUpdater != null) this.sessionUpdater.cancel(false);
        this.cachedSessions.values().forEach(session -> this.databaseHandler.update(
                session.uniqueId(),
                session.onlinetimeInMillis(),
                session.playtimeInMillis()
        ));
        this.cachedSessions.clear();
    }

    /**
     * Returns configured afk threshold in milliseconds if set,
     * otherwise returns default value of 300000 milliseconds (5 minutes).
     *
     * @return Afk threshold in milliseconds.
     */
    public static long afkThreshold() {
        return SessionHandler.afkThreshold;
    }

}
