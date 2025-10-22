package de.marvin.playtime.core.database;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.playtime.core.config.ConfigurationValues;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.logging.Logger;

public class DatabaseHandler {

    private final SQLConnection sqlConnection;
    private final RedisConnection redisConnection;

    public DatabaseHandler(
            @NotNull Logger logger,
            @NotNull ConfigurationValues configurationValues
    ) {
        this.sqlConnection = new SQLConnection(logger, configurationValues.databaseTable());
        this.redisConnection = new RedisConnection(configurationValues.redisPrefix());
    }

    /**
     * Caches the {@link Session} of a player found in the database.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the player's {@link Session},
     * or a new {@link Session} with default values if not found.
     */
    public CloudFuture<Session> cache(
            @NotNull UUID uniqueId
    ) {
        return this.sqlConnection.session(uniqueId).map(session -> {
            this.redisConnection.cache(session);
            return session;
        });
    }

    /**
     * Retrieves the cached {@link Session} of given {@link UUID}
     * from Redis. If not cached, a default {@link Session} is returned.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of given {@link UUID},
     * or a default {@link Session} if not cached.
     */
    public Session session(
            @NotNull UUID uniqueId
    ) {
        var session = this.redisConnection.session(uniqueId);
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
        var session = this.redisConnection.session(uniqueId);
        if (session != null) return new CloudFuture<>(session);
        return this.sqlConnection.session(uniqueId);
    }

    /**
     * Saves and uncaches the {@link Session} of given {@link UUID}
     * from Redis to the database if cached.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void saveAndUncache(
            @NotNull UUID uniqueId
    ) {
        var session = this.redisConnection.getAndUncache(uniqueId);
        if (session == null) return;
        this.sqlConnection.update(
                uniqueId,
                session.onlinetimeInMillis(),
                session.playtimeInMillis()
        );
    }

    /**
     * Updates the {@link Session} of given {@link UUID}
     * either in Redis if cached or in the database directly
     * if no cached data was found.
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
        if (this.redisConnection.exists(uniqueId)) {
            var currentSession = this.redisConnection.session(uniqueId);
            if (currentSession == null)
                currentSession = Session.defaultSession(uniqueId);
            this.redisConnection.cache(
                    new Session(
                            uniqueId,
                            onlinetimeInMillis != null
                                    ? onlinetimeInMillis
                                    : currentSession.onlinetimeInMillis(),
                            playtimeInMillis != null
                                    ? playtimeInMillis
                                    : currentSession.playtimeInMillis()
                    )
            );
            return;
        }
        this.sqlConnection.update(
                uniqueId,
                onlinetimeInMillis,
                playtimeInMillis
        );
    }

    /**
     * Resets the {@link Session} of given {@link UUID}
     * either in Redis if cached or in the database directly
     * if no cached data was found.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void reset(
            @NotNull UUID uniqueId
    ) {
        if (this.redisConnection.exists(uniqueId)) {
            var defaultSession = Session.defaultSession(uniqueId);
            this.update(
                    uniqueId,
                    defaultSession.onlinetimeInMillis(),
                    defaultSession.playtimeInMillis()
            );
            return;
        }
        this.sqlConnection.delete(uniqueId);
    }

}
