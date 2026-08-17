package de.marvin.playtime.core.session;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.api.dependencies.guava.collect.Maps;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.config.ConfigurationValues;
import de.marvin.playtime.core.database.DatabaseHandler;
import de.marvin.playtime.core.database.SessionLoadResult;
import de.marvin.playtime.core.util.TaskScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Handles the asynchronous loading, local caching, updating and persistence of player {@link Session Sessions}.
 */
public class SessionHandler implements PlaytimeAPI {

    private final DatabaseHandler databaseHandler;
    private final Logger logger;

    private static Long afkThreshold = 300000L;

    /**
     * Holds the loading or loaded state of currently online players.
     */
    private final ConcurrentMap<UUID, SessionState> sessions = Maps.newConcurrentMap();

    /**
     * Scheduled task that updates sessions periodically.
     */
    private ScheduledFuture<?> sessionUpdater;

    /**
     * Indicates whether the session handler is shutting down. If set to {@code true}, no new session loads will
     * be initiated.
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    /**
     * Creates a session handler.
     *
     * @param databaseHandler     Database and Redis access
     * @param configurationValues Playtime configuration values
     * @param logger              Logger used for asynchronous load failures
     */
    public SessionHandler(
            @NotNull DatabaseHandler databaseHandler,
            @NotNull ConfigurationValues configurationValues,
            @NotNull Logger logger
    ) {
        this.databaseHandler = databaseHandler;
        this.logger = logger;

        SessionHandler.afkThreshold = configurationValues.afkThreshold();
    }

    /**
     * Loads a player's {@link Session} asynchronously from Redis or SQL and saves it in the local cache.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void cacheSession(
            @NotNull UUID uniqueId
    ) {
        if (this.shuttingDown.get()) return;

        var loadingSession = new LoadingSession();
        this.sessions.put(uniqueId, loadingSession);
        if (this.shuttingDown.get()) {
            this.sessions.remove(uniqueId, loadingSession);
            return;
        }

        var loadTask = TaskScheduler.executeTask(() -> this.loadSession(uniqueId, loadingSession));
        if (loadTask == null) this.sessions.remove(uniqueId, loadingSession);
    }

    /**
     * Tries to load a player's {@link Session} first from Redis and, if not present, from SQL and completes the
     * load process by publishing it to the local cache if the {@link SessionState} is still current.
     *
     * @param uniqueId       {@link UUID} of the player
     * @param loadingSession {@link LoadingSession} that identifies this load attempt
     */
    private void loadSession(
            @NotNull UUID uniqueId,
            @NotNull LoadingSession loadingSession
    ) {
        try {
            this.databaseHandler.session(uniqueId)
                    .onSuccess(session -> this.completeSessionLoad(uniqueId, loadingSession, session))
                    .onFailure(exception -> {
                        if (this.sessions.remove(uniqueId, loadingSession))
                            this.logLoadFailure(uniqueId, exception);
                    });
        } catch (RuntimeException exception) {
            if (this.sessions.remove(uniqueId, loadingSession))
                this.logLoadFailure(uniqueId, exception);
        }
    }

    /**
     * Publishes a loaded session if its {@link SessionState} is still current.
     *
     * @param uniqueId       {@link UUID} of the player
     * @param loadingSession {@link LoadingSession} that initiated the request
     * @param result         {@link SessionLoadResult} returned by Redis or SQL
     */
    private void completeSessionLoad(
            @NotNull UUID uniqueId,
            @NotNull LoadingSession loadingSession,
            @NotNull SessionLoadResult result
    ) {
        this.sessions.computeIfPresent(uniqueId, (ignored, currentState) -> {
            if (currentState != loadingSession) return currentState;
            var resolvedSession = loadingSession.resolve(uniqueId, result.session());
            var loadedSession = new LoadedSession(resolvedSession);
            if (result.source() == SessionLoadResult.DataSource.REDIS) return loadedSession;
            try {
                this.databaseHandler.cache(resolvedSession);
            } catch (RuntimeException exception) {
                this.logger.warning(
                        "Failed to cache session data for player " + uniqueId + ": " + exception.getMessage()
                );
            }
            return loadedSession;
        });
    }

    /**
     * Retrieves the loaded {@link Session} of given {@link UUID} from {@link SessionHandler#sessions}. If not
     * loaded, a {@link Session#defaultSession(UUID)} is returned.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of given {@link UUID} or a default {@link Session} if not cached
     */
    public Session session(
            @NotNull UUID uniqueId
    ) {
        var session = Session.fromState(this.sessions.get(uniqueId));
        return session != null ? session : Session.defaultSession(uniqueId);
    }

    /**
     * Forces retrieval of the {@link Session} of given {@link UUID} first from Redis, then from the database
     * if not cached and only returns {@code null} if not found in both.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the player's {@link Session}
     */
    public CloudFuture<Session> forceSession(
            @NotNull UUID uniqueId
    ) {
        var session = Session.fromState(this.sessions.get(uniqueId));
        if (session != null) return new CloudFuture<>(session);
        return this.databaseHandler.forceSession(uniqueId).map(SessionLoadResult::session);
    }

    /**
     * Sets whether playtime should be currently counted for the {@link Session} of given {@link UUID} in
     * {@link SessionHandler#sessions}.
     *
     * @param uniqueId      {@link UUID} of the player
     * @param countPlaytime {@code true} to count playtime, {@code false} otherwise
     */
    public void setCountPlaytime(
            @NotNull UUID uniqueId,
            boolean countPlaytime
    ) {
        this.sessions.computeIfPresent(uniqueId, (ignored, state) -> {
            if (state instanceof LoadedSession(Session session)) {
                session.setCountPlaytime(countPlaytime);
            } else if (state instanceof LoadingSession loadingSession) {
                loadingSession.setCountPlaytime(countPlaytime);
            }
            return state;
        });
    }

    /**
     * Sets whether the {@link Session} of given {@link UUID} in {@link SessionHandler#sessions} is away
     * from keyboard.
     *
     * @param uniqueId {@link UUID} of the player
     * @param away     {@code true} if the player is afk, {@code false} otherwise
     */
    public void setAwayStatus(
            @NotNull UUID uniqueId,
            boolean away
    ) {
        this.sessions.computeIfPresent(uniqueId, (ignored, state) -> {
            if (state instanceof LoadedSession(Session session)) {
                session.setAwayFromKeyboard(away);
            } else if (state instanceof LoadingSession loadingSession) {
                loadingSession.setAwayFromKeyboard(away);
            }
            return state;
        });
    }

    /**
     * Updates the last activity timestamp of the {@link Session} of given {@link UUID} in
     * {@link SessionHandler#sessions}.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void updateLastActivity(
            @NotNull UUID uniqueId
    ) {
        this.sessions.computeIfPresent(uniqueId, (ignored, state) -> {
            if (state instanceof LoadedSession(Session session)) {
                session.updateLastActivity();
            } else if (state instanceof LoadingSession loadingSession) {
                loadingSession.updateLastActivity();
            }
            return state;
        });
    }

    /**
     * Saves and uncaches the {@link Session} of given {@link UUID} from {@link SessionHandler#sessions} to
     * the database if loaded.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void saveAndUncacheSession(
            @NotNull UUID uniqueId
    ) {
        var session = Session.fromState(this.sessions.remove(uniqueId));
        if (session == null) return;
        var snapshot = session.snapshot();
        this.databaseHandler.update(
                uniqueId,
                snapshot.onlinetimeInMillis(),
                snapshot.playtimeInMillis(),
                false
        );
    }

    /**
     * Updates the {@link Session} of given {@link UUID} either in {@link SessionHandler#sessions} if loaded,
     * otherwise tries to update cached Redis session and if not found there, update it in the database
     * directly.
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
        var state = this.sessions.computeIfPresent(uniqueId, (ignored, currentState) -> {
            if (currentState instanceof LoadedSession(Session session)) {
                session.update(
                        onlinetimeInMillis,
                        playtimeInMillis
                );
            } else if (currentState instanceof LoadingSession loadingSession) {
                loadingSession.update(
                        onlinetimeInMillis,
                        playtimeInMillis
                );
            }
            return currentState;
        });
        if (state != null) return;
        this.databaseHandler.update(
                uniqueId,
                onlinetimeInMillis,
                playtimeInMillis,
                true
        );
    }

    /**
     * Resets the {@link Session} of given {@link UUID} in {@link SessionHandler#sessions} if loaded, otherwise
     * tries to reset cached Redis session and if not found there, resets it in the database directly.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void reset(
            @NotNull UUID uniqueId
    ) {
        var state = this.sessions.computeIfPresent(uniqueId, (ignored, currentState) -> {
            if (currentState instanceof LoadingSession loadingSession) {
                loadingSession.reset();
                return loadingSession;
            }
            return new LoadedSession(Session.defaultSession(uniqueId));
        });
        if (state != null) return;
        this.databaseHandler.reset(uniqueId);
    }

    /**
     * Starts the session updater task that updates all loaded {@link SessionHandler#sessions} of online players
     * every second.
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
     * Updates all loaded {@link SessionHandler#sessions} of online players.
     */
    private void updateSessions() {
        this.sessions.forEach((uniqueId, ignored) ->
                this.sessions.computeIfPresent(uniqueId, (key, state) -> {
                    if (state instanceof LoadedSession(Session session))
                        session.update();
                    return state;
                })
        );
    }

    /**
     * Shuts down the session handler, saving and clearing all {@link SessionHandler#sessions}.
     */
    public void shutdown() {
        this.shuttingDown.set(true);
        if (this.sessionUpdater != null) this.sessionUpdater.cancel(false);
        TaskScheduler.shutdown();
        for (var uniqueId : this.sessions.keySet().toArray(UUID[]::new))
            this.saveAndUncacheSession(uniqueId);
        this.sessions.clear();
    }

    /**
     * Logs a session load failure {@link Exception}.
     *
     * @param uniqueId  {@link UUID} of the player
     * @param exception Load failure {@link Exception}
     */
    private void logLoadFailure(
            @NotNull UUID uniqueId,
            @NotNull Exception exception
    ) {
        this.logger.warning(
                "Failed to load session data for player " + uniqueId + ": " + exception.getMessage()
        );
    }

    /**
     * Returns configured afk threshold in milliseconds if set, otherwise returns default value of {@code 300000}
     * milliseconds ({@code 5} minutes).
     *
     * @return AFK threshold in milliseconds
     */
    public static long afkThreshold() {
        return SessionHandler.afkThreshold;
    }

}
