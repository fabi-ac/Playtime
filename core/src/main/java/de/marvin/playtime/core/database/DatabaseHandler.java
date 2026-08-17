package de.marvin.playtime.core.database;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.playtime.core.config.ConfigurationValues;
import de.marvin.playtime.core.database.connection.RedisConnection;
import de.marvin.playtime.core.database.connection.SQLConnection;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles the storage and retrieval of {@link Session}.
 */
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
     * Loads the {@link Session} of a player from Redis, falling back to SQL when no cached session exists.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the {@link SessionLoadResult}
     */
    public CloudFuture<SessionLoadResult> session(
            @NotNull UUID uniqueId
    ) {
        var cachedSession = this.redisConnection.session(uniqueId);
        if (cachedSession != null) return new CloudFuture<>(SessionLoadResult.of(
                cachedSession,
                SessionLoadResult.DataSource.REDIS
        ));
        return this.sqlConnection.session(uniqueId).map(session -> SessionLoadResult.of(
                session,
                SessionLoadResult.DataSource.SQL
        ));
    }

    /**
     * Caches a loaded {@link Session} in Redis.
     *
     * @param session {@link Session} to cache
     */
    public void cache(
            @NotNull Session session
    ) {
        this.redisConnection.cache(session);
    }

    /**
     * Forces retrieval of the {@link Session} of given player first from Redis, then from the database if not
     * cached and only returns {@code null} if not found in both.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the {@link SessionLoadResult}
     */
    public CloudFuture<SessionLoadResult> forceSession(
            @NotNull UUID uniqueId
    ) {
        return this.session(uniqueId);
    }

    /**
     * Saves and uncaches the {@link Session} of given player from Redis to the database if cached.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void saveAndUncache(
            @NotNull UUID uniqueId
    ) {
        var session = this.redisConnection.getAndUncache(uniqueId);
        if (session == null) return;
        var snapshot = session.snapshot();
        this.sqlConnection.safeUpdate(
                uniqueId,
                snapshot.onlinetimeInMillis(),
                snapshot.playtimeInMillis()
        );
    }

    /**
     * Updates the {@link Session} of given player either in Redis if cached or in the database directly if no
     * cached data was found.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis Onlinetime in milliseconds
     * @param playtimeInMillis   Playtime in milliseconds
     * @param force              Whether to overwrite database update restrictions
     */
    public void update(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis,
            boolean force
    ) {
        if (this.redisConnection.update(uniqueId, onlinetimeInMillis, playtimeInMillis)) return;
        if (force) {
            this.sqlConnection.update(
                    uniqueId,
                    onlinetimeInMillis,
                    playtimeInMillis
            );
            return;
        }
        this.sqlConnection.safeUpdate(
                uniqueId,
                onlinetimeInMillis,
                playtimeInMillis
        );
    }

    /**
     * Resets the {@link Session} of given player either in Redis if cached or in the database directly if no
     * cached data was found.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void reset(
            @NotNull UUID uniqueId
    ) {
        if (this.redisConnection.update(uniqueId, 0L, 0L)) return;
        this.sqlConnection.delete(uniqueId);
    }

}
