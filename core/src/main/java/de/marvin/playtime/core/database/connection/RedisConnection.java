package de.marvin.playtime.core.database.connection;

import de.marvin.api.core.Cloud;
import de.marvin.api.core.memory.Memory;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Handles the communication with the Redis database.
 */
public class RedisConnection {

    /**
     * Separator used in Redis keys.
     */
    private static final String SEPARATOR = ":";
    /**
     * Key used to store the session in Redis.
     */
    private static final String SESSION_KEY = "session";

    private final Memory memory;
    private final String redisPrefix;

    /**
     * Creates a Redis connection.
     *
     * @param redisPrefix Prefix used for all session keys
     */
    public RedisConnection(
            @NotNull String redisPrefix
    ) {
        this.memory = Cloud.memory();
        this.redisPrefix = redisPrefix;
    }

    /**
     * Retrieves the cached {@link Session} of the given player.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Cached {@link Session}, or {@code null} if no session is cached
     */
    public @Nullable Session session(
            @NotNull UUID uniqueId
    ) {
        var value = this.memory.get(this.sessionKey(uniqueId));
        return value != null
                ? StoredSession.deserialize(value).toSession(uniqueId)
                : null;
    }

    /**
     * Caches the given {@link Session}.
     *
     * @param session {@link Session} to cache
     */
    public void cache(
            @NotNull Session session
    ) {
        this.memory.set(
                this.sessionKey(session.uniqueId()),
                StoredSession.fromSession(session).serialize()
        );
    }

    /**
     * Updates the specified values of a cached {@link Session}.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis New onlinetime, or {@code null} to retain the current value
     * @param playtimeInMillis   New playtime, or {@code null} to retain the current value
     * @return {@code true} if a cached {@link Session} was updated, otherwise {@code false}
     */
    public boolean update(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis
    ) {
        var key = this.sessionKey(uniqueId);
        while (true) {
            var currentValue = this.memory.get(key);
            if (currentValue == null) return false;

            var currentSession = StoredSession.deserialize(currentValue);
            var updatedSession = new StoredSession(
                    onlinetimeInMillis != null
                            ? onlinetimeInMillis
                            : currentSession.onlinetimeInMillis(),
                    playtimeInMillis != null
                            ? playtimeInMillis
                            : currentSession.playtimeInMillis()
            );
            if (this.memory.compareAndSet(key, currentValue, updatedSession.serialize())) return true;
        }
    }

    /**
     * Retrieves and removes the cached {@link Session} of the given player. If the value changes between
     * reading and deleting it, the operation retries with the newer value instead of deleting unseen
     * data.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Removed {@link Session}, or {@code null} if no session is cached
     */
    public @Nullable Session getAndUncache(
            @NotNull UUID uniqueId
    ) {
        var key = this.sessionKey(uniqueId);
        while (true) {
            var currentValue = this.memory.get(key);
            if (currentValue == null) return null;

            var currentSession = StoredSession.deserialize(currentValue);
            if (this.memory.compareAndDelete(key, currentValue))
                return currentSession.toSession(uniqueId);
        }
    }

    /**
     * Returns the key where the {@link Session} of the given player is stored in Redis.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Key of the given player's {@link Session}
     */
    private String sessionKey(
            @NotNull UUID uniqueId
    ) {
        return this.redisPrefix + SEPARATOR + uniqueId + SEPARATOR + SESSION_KEY;
    }

    /**
     * Immutable representation of all persisted session values stored together in a single Redis value.
     *
     * @param onlinetimeInMillis Persisted onlinetime in milliseconds
     * @param playtimeInMillis   Persisted playtime in milliseconds
     */
    private record StoredSession(
            long onlinetimeInMillis,
            long playtimeInMillis
    ) {

        /**
         * Separator used in Redis session values.
         */
        private static final String SEPARATOR = ";";

        /**
         * Creates a stored representation from a snapshot of the given {@link Session}.
         *
         * @param session {@link Session} whose persisted values should be captured
         * @return Stored representation of the given {@link Session}
         */
        private static StoredSession fromSession(
                @NotNull Session session
        ) {
            var snapshot = session.snapshot();
            return new StoredSession(
                    snapshot.onlinetimeInMillis(),
                    snapshot.playtimeInMillis()
            );
        }

        /**
         * Deserializes a Redis value into its stored session values.
         *
         * @param value Serialized Redis value
         * @return Deserialized session values
         * @throws IllegalArgumentException If the value does not contain exactly two valid numeric components
         */
        private static StoredSession deserialize(
                @NotNull String value
        ) {
            var separatorIndex = value.indexOf(SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex != value.lastIndexOf(SEPARATOR)
                    || separatorIndex == value.length() - 1) {
                throw new IllegalArgumentException("Invalid cached session value: " + value);
            }
            return new StoredSession(
                    Long.parseLong(value.substring(0, separatorIndex)),
                    Long.parseLong(value.substring(separatorIndex + 1))
            );
        }

        /**
         * Serializes both session values into the representation stored in Redis.
         *
         * @return Serialized Redis value
         */
        private String serialize() {
            return this.onlinetimeInMillis + SEPARATOR + this.playtimeInMillis;
        }

        /**
         * Creates a {@link Session} for the given player from the stored values.
         *
         * @param uniqueId {@link UUID} of the player
         * @return {@link Session} containing the stored values
         */
        private Session toSession(
                @NotNull UUID uniqueId
        ) {
            return new Session(
                    uniqueId,
                    this.onlinetimeInMillis,
                    this.playtimeInMillis
            );
        }

    }

}
