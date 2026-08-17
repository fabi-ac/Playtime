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
import java.util.concurrent.atomic.AtomicReference;

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
        this.cachedSessions.putIfAbsent(session.uniqueId(), session);
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
        var session = this.cachedSessions.get(uniqueId);
        return session != null ? session : Session.defaultSession(uniqueId);
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
        var session = this.cachedSessions.get(uniqueId);
        if (session != null) return new CloudFuture<>(session);
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
        this.cachedSessions.computeIfPresent(uniqueId, (ignored, session) -> {
            session.setCountPlaytime(countPlaytime);
            return session;
        });
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
        this.cachedSessions.computeIfPresent(uniqueId, (ignored, session) -> {
            session.setAwayFromKeyboard(away);
            return session;
        });
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
        this.cachedSessions.computeIfPresent(uniqueId, (ignored, session) -> {
            session.updateLastActivity();
            return session;
        });
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
        var snapshot = new AtomicReference<Session.Snapshot>();
        this.cachedSessions.computeIfPresent(uniqueId, (ignored, session) -> {
            snapshot.set(session.snapshot());
            return null;
        });
        var persisted = snapshot.get();
        if (persisted == null) return;
        this.databaseHandler.update(
                uniqueId,
                persisted.onlinetimeInMillis(),
                persisted.playtimeInMillis(),
                false
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
        var updatedCachedSession = this.cachedSessions.computeIfPresent(uniqueId, (ignored, session) -> {
            session.update(
                    onlinetimeInMillis,
                    playtimeInMillis
            );
            return session;
        });
        if (updatedCachedSession != null) return;
        this.databaseHandler.update(
                uniqueId,
                onlinetimeInMillis,
                playtimeInMillis,
                true
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
        var resetCachedSession = this.cachedSessions.computeIfPresent(
                uniqueId,
                (ignored, session) -> Session.defaultSession(uniqueId)
        );
        if (resetCachedSession != null) return;
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
        this.cachedSessions.forEach((uniqueId, ignored) ->
                this.cachedSessions.computeIfPresent(uniqueId, (key, session) -> {
                    session.update();
                    return session;
                })
        );
    }

    /**
     * Shuts down the session handler, saving and clearing all
     * {@link SessionHandler#cachedSessions}.
     */
    public void shutdown() {
        if (this.sessionUpdater != null) this.sessionUpdater.cancel(false);
        for (var uniqueId : this.cachedSessions.keySet().toArray(UUID[]::new))
            this.saveAndUncacheSession(uniqueId);
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
