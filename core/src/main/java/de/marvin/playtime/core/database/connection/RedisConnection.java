package de.marvin.playtime.core.database.connection;

import de.marvin.api.core.Cloud;
import de.marvin.api.core.memory.Memory;
import de.marvin.playtime.core.database.result.SessionClaimResult;
import de.marvin.playtime.core.database.result.SessionTransferResult;
import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

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
     * Checks whether a {@link Session} of the given player currently exists in the Redis cache.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@code true} if a cached {@link Session} exists, {@code false} otherwise
     */
    public boolean exists(
            @NotNull UUID uniqueId
    ) {
        return this.memory.get(this.sessionKey(uniqueId)) != null;
    }

    /**
     * Retrieves the cached {@link Session} of the given player.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Cached {@link Session}, or {@code null} if not found
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
     * Attempts to claim ownership of a cached {@link Session}. An unowned {@link Session} or a session whose
     * previous owning service no longer exists is claimed. A {@link Session} owned by another active claim is
     * left unchanged, including a different claim on the same service.
     *
     * @param playerUniqueId  {@link UUID} of the player
     * @param serviceUniqueId {@link UUID} of the service trying to claim the {@link Session}
     * @param claimUniqueId   {@link UUID} distinguishing this claim from other claims of the same service
     * @param serviceExists   Checks whether a foreign owning service still exists
     * @return {@link SessionClaimResult Result} of the ownership claim
     */
    public @NotNull SessionClaimResult claim(
            @NotNull UUID playerUniqueId,
            @NotNull UUID serviceUniqueId,
            @NotNull UUID claimUniqueId,
            @NotNull Predicate<UUID> serviceExists
    ) {
        var key = this.sessionKey(playerUniqueId);
        return this.claim(
                playerUniqueId,
                serviceUniqueId,
                claimUniqueId,
                serviceExists,
                key,
                this.memory.get(key)
        );
    }

    /**
     * Attempts to claim ownership of a cached {@link Session}, starting with an already retrieved Redis
     * value. If a concurrent update prevents the ownership change, the current value is read again and
     * reevaluated.
     *
     * @param playerUniqueId  {@link UUID} of the player
     * @param serviceUniqueId {@link UUID} of the service trying to claim the {@link Session}
     * @param claimUniqueId   {@link UUID} distinguishing this claim from other claims of the same service
     * @param serviceExists   Checks whether a foreign owning service still exists
     * @param key             Redis key of the {@link Session}
     * @param initialValue    Redis value to evaluate first
     * @return {@link SessionClaimResult Result} of the ownership claim
     */
    private @NotNull SessionClaimResult claim(
            @NotNull UUID playerUniqueId,
            @NotNull UUID serviceUniqueId,
            @NotNull UUID claimUniqueId,
            @NotNull Predicate<UUID> serviceExists,
            @NotNull String key,
            @Nullable String initialValue
    ) {
        var currentValue = initialValue;
        while (true) {
            if (currentValue == null) return SessionClaimResult.missing();
            var currentSession = StoredSession.deserialize(currentValue);

            // Only the exact claim which owns the session may use or release it
            if (serviceUniqueId.equals(currentSession.serviceUniqueId())
                    && claimUniqueId.equals(currentSession.claimUniqueId()))
                return SessionClaimResult.claimed(currentSession.toSession(playerUniqueId));

            // Another claim of this same service must wait
            if (serviceUniqueId.equals(currentSession.serviceUniqueId()))
                return SessionClaimResult.occupied(currentSession.serviceUniqueId());

            // A claim owned by another service remains occupied while that service exists
            if (currentSession.serviceUniqueId() != null
                    && !serviceUniqueId.equals(currentSession.serviceUniqueId())
                    && serviceExists.test(currentSession.serviceUniqueId()))
                return SessionClaimResult.occupied(currentSession.serviceUniqueId());

            // Attempt to claim ownership by updating the Redis entry. If the value has changed since it was
            // read, retry with the new value
            var claimedSession = currentSession.withOwner(serviceUniqueId, claimUniqueId);
            if (this.memory.compareAndSet(key, currentValue, claimedSession.serialize()))
                return SessionClaimResult.claimed(claimedSession.toSession(playerUniqueId));

            currentValue = this.memory.get(key);
        }
    }

    /**
     * Caches and claims the given {@link Session} if no Redis entry exists yet. If an entry was inserted
     * concurrently, its returned value is used directly for an ownership claim attempt. If the session is
     * cached already, an ownership claim attempt is made.
     *
     * @param session         {@link Session} to cache
     * @param serviceUniqueId {@link UUID} of the service claiming the {@link Session}
     * @param claimUniqueId   {@link UUID} distinguishing this claim from other claims of the same service
     * @param serviceExists   Checks whether a foreign owning service still exists
     * @return {@link SessionClaimResult Result} of caching or claiming the {@link Session}
     * @see #claim(UUID, UUID, UUID, Predicate, String, String)
     */
    public @NotNull SessionClaimResult cacheOrClaim(
            @NotNull Session session,
            @NotNull UUID serviceUniqueId,
            @NotNull UUID claimUniqueId,
            @NotNull Predicate<UUID> serviceExists
    ) {
        var key = this.sessionKey(session.uniqueId());
        var result = this.memory.setIfAbsent(
                key,
                StoredSession.fromSession(session, serviceUniqueId, claimUniqueId).serialize()
        );
        if (result.inserted()) return SessionClaimResult.claimed(session);
        return this.claim(
                session.uniqueId(),
                serviceUniqueId,
                claimUniqueId,
                serviceExists,
                key,
                result.value()
        );
    }

    /**
     * Caches the given values of a {@link Session} and releases its ownership. The update is rejected if the
     * Redis entry is missing or no longer belongs to the expected service and claim.
     *
     * @param playerUniqueId     {@link UUID} of the player
     * @param serviceUniqueId    {@link UUID} of the expected owning service
     * @param claimUniqueId      {@link UUID} of the expected session claim
     * @param onlinetimeInMillis Final onlinetime in milliseconds
     * @param playtimeInMillis   Final playtime in milliseconds
     * @return {@code true} if the {@link Session} was released, {@code false} otherwise
     */
    public boolean release(
            @NotNull UUID playerUniqueId,
            @NotNull UUID serviceUniqueId,
            @NotNull UUID claimUniqueId,
            long onlinetimeInMillis,
            long playtimeInMillis
    ) {
        var key = this.sessionKey(playerUniqueId);
        while (true) {
            var currentValue = this.memory.get(key);
            if (currentValue == null) return false;

            var currentSession = StoredSession.deserialize(currentValue);
            if (!serviceUniqueId.equals(currentSession.serviceUniqueId())
                    || !claimUniqueId.equals(currentSession.claimUniqueId())) return false;

            var releasedSession = new StoredSession(
                    onlinetimeInMillis,
                    playtimeInMillis,
                    null,
                    null
            );
            if (this.memory.compareAndSet(key, currentValue, releasedSession.serialize())) return true;
        }
    }

    /**
     * Updates the specified values of a cached {@link Session}.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis New onlinetime, or {@code null} to keep the current value
     * @param playtimeInMillis   New playtime, or {@code null} to keep the current value
     * @return {@code true} if a cached {@link Session} was updated, {@code false} otherwise
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
                            : currentSession.playtimeInMillis(),
                    currentSession.serviceUniqueId(),
                    currentSession.claimUniqueId()
            );
            if (this.memory.compareAndSet(key, currentValue, updatedSession.serialize())) return true;
        }
    }

    /**
     * Retrieves the cached {@link Session} of the given player for a database transfer if it has no active
     * owning server. The Redis entry thereby remains unchanged until {@link #uncache(UUID, String)} is called.
     * A {@link Session} whose owning service no longer exists may be recovered.
     *
     * @param uniqueId    {@link UUID} of the player
     * @param ownerExists Checks whether the current owning service still exists
     * @return {@link SessionTransferResult Result} of retrieving a cached {@link Session} for transfer
     */
    public @NotNull SessionTransferResult transferCandidate(
            @NotNull UUID uniqueId,
            @NotNull Predicate<UUID> ownerExists
    ) {
        var currentValue = this.memory.get(this.sessionKey(uniqueId));
        if (currentValue == null) return SessionTransferResult.missing();

        var currentSession = StoredSession.deserialize(currentValue);
        if (currentSession.serviceUniqueId() != null && ownerExists.test(currentSession.serviceUniqueId()))
            return SessionTransferResult.occupied(currentSession.serviceUniqueId());
        return SessionTransferResult.transferable(currentSession.toSession(uniqueId), currentValue);
    }

    /**
     * Removes a cached {@link Session} only if its Redis value still equals the given cache token.
     *
     * @param uniqueId      {@link UUID} of the player
     * @param expectedToken Cache token identifying the Redis snapshot
     * @return {@code true} if the cached value was removed, {@code false} if it changed or disappeared
     */
    public boolean uncache(
            @NotNull UUID uniqueId,
            @NotNull String expectedToken
    ) {
        return this.memory.compareAndDelete(this.sessionKey(uniqueId), expectedToken);
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
     * Immutable representation of all persisted {@link Session} values stored together in a single Redis value.
     *
     * @param onlinetimeInMillis Persisted onlinetime in milliseconds
     * @param playtimeInMillis   Persisted playtime in milliseconds
     * @param serviceUniqueId    {@link UUID} of the service currently owning the session, or {@code null} if it
     *                           is unowned
     * @param claimUniqueId      {@link UUID} of the current session claim, or {@code null} if unowned
     */
    private record StoredSession(
            long onlinetimeInMillis,
            long playtimeInMillis,
            @Nullable UUID serviceUniqueId,
            @Nullable UUID claimUniqueId
    ) {

        /**
         * Separator used in Redis session values.
         */
        private static final String SEPARATOR = ";";

        /**
         * Creates a stored representation from a snapshot of the given {@link Session}.
         *
         * @param session         {@link Session} whose persisted values should be captured
         * @param serviceUniqueId {@link UUID} of the service owning the {@link StoredSession}
         * @param claimUniqueId   {@link UUID} of the session claim
         * @return Stored representation of the given {@link Session}
         */
        private static StoredSession fromSession(
                @NotNull Session session,
                @NotNull UUID serviceUniqueId,
                @NotNull UUID claimUniqueId
        ) {
            var snapshot = session.snapshot();
            return new StoredSession(
                    snapshot.onlinetimeInMillis(),
                    snapshot.playtimeInMillis(),
                    serviceUniqueId,
                    claimUniqueId
            );
        }

        /**
         * Returns a copy of this {@link StoredSession} with the given owning service.
         *
         * @param serviceUniqueId {@link UUID} of the owning service
         * @param claimUniqueId   {@link UUID} of the session claim
         * @return {@link StoredSession} owned by the given service
         */
        private @NotNull StoredSession withOwner(
                @NotNull UUID serviceUniqueId,
                @NotNull UUID claimUniqueId
        ) {
            return new StoredSession(
                    this.onlinetimeInMillis,
                    this.playtimeInMillis,
                    serviceUniqueId,
                    claimUniqueId
            );
        }

        /**
         * Deserializes a Redis value into its {@link StoredSession}.
         *
         * @param value Serialized Redis value
         * @return Deserialized {@link StoredSession}
         * @throws IllegalArgumentException If the value does not contain valid time and owner components
         */
        private static StoredSession deserialize(
                @NotNull String value
        ) {
            var components = value.split(SEPARATOR, -1);
            if (components.length != 4
                    || components[0].isEmpty()
                    || components[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid cached session value: " + value);
            }
            var serviceUniqueId = !components[2].isEmpty()
                    ? UUID.fromString(components[2])
                    : null;
            var claimUniqueId = !components[3].isEmpty()
                    ? UUID.fromString(components[3])
                    : null;
            if ((serviceUniqueId == null) != (claimUniqueId == null))
                throw new IllegalArgumentException("Cached session owner and claim must both be present: " + value);
            return new StoredSession(
                    Long.parseLong(components[0]),
                    Long.parseLong(components[1]),
                    serviceUniqueId,
                    claimUniqueId
            );
        }

        /**
         * Serializes {@link StoredSession} values into the representation stored in Redis.
         *
         * @return Serialized Redis value
         */
        private String serialize() {
            return this.onlinetimeInMillis
                    + SEPARATOR + this.playtimeInMillis
                    + SEPARATOR + (this.serviceUniqueId != null ? this.serviceUniqueId : "")
                    + SEPARATOR + (this.claimUniqueId != null ? this.claimUniqueId : "");
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
