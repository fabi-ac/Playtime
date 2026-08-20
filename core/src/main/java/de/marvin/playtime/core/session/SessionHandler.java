package de.marvin.playtime.core.session;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.api.dependencies.guava.collect.Maps;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.config.ConfigurationValues;
import de.marvin.playtime.core.database.DatabaseHandler;
import de.marvin.playtime.core.database.result.SessionClaimResult;
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

    /**
     * Holds the delay in milliseconds between ownership claim retries when a session is already owned by
     * another service. Default value is {@code 100} milliseconds.
     */
    private static final long OWNERSHIP_CLAIM_RETRY_DELAY_MILLIS = 100L;

    private final DatabaseHandler databaseHandler;
    private final Logger logger;

    /**
     * Whether the {@link SessionHandler} is able to cache sessions locally.
     */
    private final boolean ableToCacheSessions;

    /**
     * Holds the threshold in milliseconds after which a player is considered away from keyboard. Default value
     * is {@code 300000} milliseconds ({@code 5} minutes).
     */
    private static long afkThreshold = 300000L;

    /**
     * Holds the loading or loaded state of currently online players.
     */
    private final ConcurrentMap<UUID, SessionState> sessions = Maps.newConcurrentMap();

    /**
     * Whether the {@link SessionHandler} is shutting down. If set to {@code true}, no new session loads will be
     * initiated.
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    /**
     * Scheduled task that updates sessions periodically.
     */
    private ScheduledFuture<?> sessionUpdater;

    /**
     * Creates a session handler.
     *
     * @param databaseHandler     Database and Redis access
     * @param configurationValues Playtime configuration values
     * @param logger              Logger used for asynchronous load failures
     * @param ableToCacheSessions Whether the session handler is able to cache sessions locally
     */
    public SessionHandler(
            @NotNull DatabaseHandler databaseHandler,
            @NotNull ConfigurationValues configurationValues,
            @NotNull Logger logger,
            boolean ableToCacheSessions
    ) {
        this.databaseHandler = databaseHandler;
        this.logger = logger;
        this.ableToCacheSessions = ableToCacheSessions;

        var configuredAfkThreshold = configurationValues.afkThreshold();
        if (configuredAfkThreshold != null)
            SessionHandler.afkThreshold = configuredAfkThreshold;
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
        if (!this.ableToCacheSessions) throw new IllegalStateException(
                "Sessions can only be cached by spigot services."
        );

        var loadingSession = new LoadingSession(uniqueId);
        if (this.sessions.putIfAbsent(uniqueId, loadingSession) != null) return;
        if (this.shuttingDown.get()) {
            this.sessions.remove(uniqueId, loadingSession);
            return;
        }

        var loadTask = TaskScheduler.executeTask(() -> this.loadSession(uniqueId, loadingSession));
        if (loadTask == null) this.sessions.remove(uniqueId, loadingSession);
    }

    /**
     * Attempts to load a player's {@link Session} first from Redis and, if not present, from SQL and completes the
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
            while (!this.shuttingDown.get()
                    && this.sessions.get(uniqueId) == loadingSession
                    && !Thread.currentThread().isInterrupted()) {
                var claim = this.databaseHandler.loadAndClaim(
                        uniqueId,
                        loadingSession.claimUniqueId()
                );
                SessionClaimResult result;
                try {
                    result = claim.get();
                } catch (InterruptedException exception) {
                    result = claim.getUninterruptibly();
                    if (result.claimed()) this.releaseDiscardedLoad(
                            uniqueId,
                            loadingSession,
                            result.session()
                    );
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!result.claimed()) {
                    Thread.sleep(OWNERSHIP_CLAIM_RETRY_DELAY_MILLIS);
                    continue;
                }

                var session = result.session();
                if (!this.completeSessionLoad(uniqueId, loadingSession, session))
                    this.releaseDiscardedLoad(uniqueId, loadingSession, session);
                return;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (!this.sessions.remove(uniqueId, loadingSession)) return;
            this.logLoadFailure(uniqueId, exception);
        }
    }

    /**
     * Publishes a {@link LoadedSession} based on the given {@link Session} if the {@link SessionState} of the
     * respective {@link LoadedSession} is still current.
     *
     * @param uniqueId       {@link UUID} of the player
     * @param loadingSession {@link LoadingSession} that initiated the request
     * @param session        {@link Session} returned by Redis or SQL
     * @return {@code true} if the {@link Session} was published, otherwise {@code false}
     */
    private boolean completeSessionLoad(
            @NotNull UUID uniqueId,
            @NotNull LoadingSession loadingSession,
            @NotNull Session session
    ) {
        var completed = new AtomicBoolean();
        this.sessions.computeIfPresent(uniqueId, (ignored, currentState) -> {
            if (currentState != loadingSession) return currentState;
            var resolvedSession = loadingSession.resolve(session);
            completed.set(true);
            return new LoadedSession(resolvedSession, loadingSession.claimUniqueId());
        });
        return completed.get();
    }

    /**
     * Releases a claimed {@link Session} that could no longer be published because the player disconnected or a
     * newer {@link LoadingSession} replaced the original one.
     *
     * @param uniqueId       {@link UUID} of the player
     * @param loadingSession {@link LoadingSession} that initiated the claim
     * @param session        Claimed cached {@link Session} values
     */
    private void releaseDiscardedLoad(
            @NotNull UUID uniqueId,
            @NotNull LoadingSession loadingSession,
            @NotNull Session session
    ) {
        var resolvedSession = loadingSession.resolve(session);
        var snapshot = resolvedSession.finishTracking();
        this.databaseHandler.release(
                uniqueId,
                loadingSession.claimUniqueId(),
                snapshot.onlinetimeInMillis(),
                snapshot.playtimeInMillis()
        );
    }

    /**
     * Attempts to retrieve the {@link Session} of the given player from the local cache, and if not present,
     * returns {@link Session#defaultSession(UUID)}.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of player with given {@link UUID} or {@link Session#defaultSession(UUID)} if not
     * found
     */
    public Session session(
            @NotNull UUID uniqueId
    ) {
        var session = Session.fromState(this.sessions.get(uniqueId));
        return session != null ? session : Session.defaultSession(uniqueId);
    }

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
    public CloudFuture<Session> sessionOrDefault(
            @NotNull UUID uniqueId
    ) {
        var cachedSession = Session.fromState(this.sessions.get(uniqueId));
        if (cachedSession != null) return new CloudFuture<>(cachedSession);

        var databaseSession = this.databaseHandler.session(uniqueId);
        return databaseSession != null
                ? databaseSession
                : new CloudFuture<>(Session.defaultSession(uniqueId));
    }

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
    public CloudFuture<Session> sessionOrNull(
            @NotNull UUID uniqueId
    ) {
        var cachedSession = Session.fromState(this.sessions.get(uniqueId));
        if (cachedSession != null) return new CloudFuture<>(cachedSession);

        return this.databaseHandler.session(uniqueId);
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
            if (state instanceof LoadedSession loadedSession) {
                loadedSession.session().setCountPlaytime(countPlaytime);
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
            if (state instanceof LoadedSession loadedSession) {
                loadedSession.session().setAwayFromKeyboard(away);
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
            if (state instanceof LoadedSession loadedSession) {
                loadedSession.session().updateLastActivity();
            } else if (state instanceof LoadingSession loadingSession) {
                loadingSession.updateLastActivity();
            }
            return state;
        });
    }

    /**
     * Updates the {@link Session} of given {@link UUID} if it is neither cached locally nor in Redis.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis onlinetime in milliseconds
     * @param playtimeInMillis   playtime in milliseconds
     * @return {@code true} if the {@link Session} was updated, {@code false} if it is currently cached
     */
    public boolean update(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis
    ) {
        if (this.sessions.containsKey(uniqueId)) return false;
        return this.databaseHandler.update(
                uniqueId,
                onlinetimeInMillis,
                playtimeInMillis,
                true
        );
    }

    /**
     * Resets the {@link Session} of given {@link UUID} if it is neither cached locally nor in Redis.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@code true} if the {@link Session} was reset, {@code false} if it is currently cached
     */
    public boolean reset(
            @NotNull UUID uniqueId
    ) {
        if (this.sessions.containsKey(uniqueId)) return false;
        return this.databaseHandler.reset(uniqueId);
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
                    if (state instanceof LoadedSession loadedSession)
                        loadedSession.session().update();
                    return state;
                })
        );
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
        var state = this.sessions.remove(uniqueId);
        var session = Session.fromState(state);
        if (session == null) return;
        var snapshot = session.finishTracking();
        this.databaseHandler.release(
                uniqueId,
                state.claimUniqueId(),
                snapshot.onlinetimeInMillis(),
                snapshot.playtimeInMillis()
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
