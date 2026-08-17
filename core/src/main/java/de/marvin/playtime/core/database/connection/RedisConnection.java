package de.marvin.playtime.core.database.connection;

import de.marvin.api.core.Cloud;
import de.marvin.api.core.memory.Memory;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class RedisConnection {

    private final Memory memory;
    private final String redisPrefix;

    /**
     * Key suffix for onlinetime in Redis.
     */
    private static final String ONLINETIME_KEY = ":onlinetime";
    /**
     * Key suffix for playtime in Redis.
     */
    private static final String PLAYTIME_KEY = ":playtime";

    public RedisConnection(
            @NotNull String redisPrefix
    ) {
        this.memory = Cloud.memory();
        this.redisPrefix = redisPrefix + ":";
    }

    /**
     * Checks if the onlinetime and playtime of given {@link UUID}
     * is cached in Redis.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@code true} if both onlinetime and playtime are cached,
     * {@code false} otherwise.
     */
    public boolean exists(
            @NotNull UUID uniqueId
    ) {
        return this.memory.exists(this.onlinetimeKey(uniqueId))
                && this.memory.exists(this.playtimeKey(uniqueId));
    }

    /**
     * Retrieves the cached {@link Session} of given {@link UUID}
     * from Redis.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of given {@link UUID},
     * {@code null} if not cached.
     */
    public @Nullable Session session(
            @NotNull UUID uniqueId
    ) {
        if (!this.exists(uniqueId)) return null;

        var onlinetime = this.memory.get(this.onlinetimeKey(uniqueId));
        var playtime = this.memory.get(this.playtimeKey(uniqueId));

        return new Session(
                uniqueId,
                Long.parseLong(onlinetime),
                Long.parseLong(playtime)
        );
    }

    /**
     * Caches the given {@link Session} in Redis.
     *
     * @param session {@link Session} to cache
     */
    public void cache(
            @NotNull Session session
    ) {
        var snapshot = session.snapshot();
        this.memory.set(
                this.onlinetimeKey(session.uniqueId()),
                String.valueOf(snapshot.onlinetimeInMillis())
        );
        this.memory.set(
                this.playtimeKey(session.uniqueId()),
                String.valueOf(snapshot.playtimeInMillis())
        );
    }

    /**
     * Retrieves and removes the cached {@link Session}
     * of given {@link UUID} from Redis.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link Session} of given {@link UUID},
     *         {@code null} if not cached.
     */
    public @Nullable Session getAndUncache(
            @NotNull UUID uniqueId
    ) {
        var session = this.session(uniqueId);
        if (session != null) {
            this.memory.delete(this.onlinetimeKey(uniqueId));
            this.memory.delete(this.playtimeKey(uniqueId));
        }
        return session;
    }

    /**
     * Gets key for onlinetime of given {@link UUID}.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Key for onlinetime of given {@link UUID}.
     */
    private String onlinetimeKey(
            @NotNull UUID uniqueId
    ) {
        return this.redisPrefix + uniqueId + ONLINETIME_KEY;
    }

    /**
     * Gets key for playtime of given {@link UUID}.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Key for playtime of given {@link UUID}.
     */
    private String playtimeKey(
            @NotNull UUID uniqueId
    ) {
        return this.redisPrefix + uniqueId + PLAYTIME_KEY;
    }

}
