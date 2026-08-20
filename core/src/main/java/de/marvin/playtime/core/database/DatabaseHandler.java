package de.marvin.playtime.core.database;

import de.marvin.api.core.utils.CloudFuture;
import de.marvin.playtime.core.config.ConfigurationValues;
import de.marvin.playtime.core.database.connection.RedisConnection;
import de.marvin.playtime.core.database.connection.SQLConnection;
import de.marvin.playtime.core.database.result.SessionClaimResult;
import de.marvin.playtime.core.database.result.SessionTransferResult;
import de.marvin.playtime.core.session.Session;
import de.marvin.playtime.core.util.TaskScheduler;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.modules.bridge.BridgeServiceHelper;
import eu.cloudnetservice.wrapper.configuration.WrapperConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles the storage and retrieval of {@link Session}.
 */
public class DatabaseHandler {

    /**
     * Holds the delay in milliseconds between attempts to transfer a released {@link Session} from Redis to the
     * SQL database. Default value is {@code 100} milliseconds.
     */
    private static final long TRANSFER_RETRY_DELAY_MILLIS = 100L;

    private final Logger logger;

    /**
     * The {@link UUID} of the service this plugin is currently running on, or {@code null} if it is not running
     * on a spigot service.
     */
    private final @Nullable UUID serviceUniqueId;
    /**
     * The {@link CloudServiceProvider} used to check other services.
     */
    private final @Nullable CloudServiceProvider cloudServiceProvider;

    private final SQLConnection sqlConnection;
    private final RedisConnection redisConnection;

    public DatabaseHandler(
            @NotNull Logger logger,
            @NotNull ConfigurationValues configurationValues,
            @Nullable WrapperConfiguration wrapperConfiguration,
            @Nullable CloudServiceProvider cloudServiceProvider
    ) {
        this.logger = logger;

        this.serviceUniqueId = wrapperConfiguration != null
                ? wrapperConfiguration.serviceInfoSnapshot().serviceId().uniqueId()
                : null;
        this.cloudServiceProvider = cloudServiceProvider;

        this.sqlConnection = new SQLConnection(logger, configurationValues.databaseTable());
        this.redisConnection = new RedisConnection(configurationValues.redisPrefix());
    }

    /**
     * Loads and claims ownership of a player's {@link Session} for the current spigot service if possible.
     * <p>
     * There are two possible outcomes:
     * <ul>
     *     <li>{@link SessionClaimResult.Status#CLAIMED}: The session either was already cached and claimed
     *     successfully, or it was loaded from SQL, cached and then claimed.</li>
     *     <li>{@link SessionClaimResult.Status#OCCUPIED}: The session is currently owned by
     *     another active service, which is returned as {@link SessionClaimResult#serviceUniqueId()} and
     *     therefore cannot be claimed.</li>
     *     <li>{@link SessionClaimResult.Status#MISSING}: The Redis entry disappeared during a concurrent
     *     ownership change.</li>
     * </ul>
     * <p>
     * An already cached {@link Session} is reclaimed when the owning service did not release it properly,
     * e.g. due to a server crash.
     *
     * @param playerUniqueId {@link UUID} of the player
     * @param claimUniqueId  {@link UUID} distinguishing this claim from other claims of the same service
     * @return {@link CloudFuture} containing either the claimed {@link Session} or the {@link UUID} of an
     * active owning service that currently blocks the claim
     * @throws IllegalStateException If the {@link #serviceUniqueId} is {@code null}
     */
    public @NotNull CloudFuture<SessionClaimResult> loadAndClaim(
            @NotNull UUID playerUniqueId,
            @NotNull UUID claimUniqueId
    ) {
        if (this.serviceUniqueId == null) throw new IllegalStateException(
                "Sessions can only be claimed by spigot services."
        );

        // Try to claim the session from Redis first, if it already is cached
        var cachedResult = this.claim(playerUniqueId, claimUniqueId);
        if (cachedResult.status() != SessionClaimResult.Status.MISSING)
            return new CloudFuture<>(cachedResult);

        // If the session is not cached, load it from SQL, then cache or try to claim the session which may have
        // been cached in the meantime
        return this.sqlConnection.session(playerUniqueId)
                .map(session -> {
                    if (session == null) session = Session.defaultSession(playerUniqueId);
                    return this.redisConnection.cacheOrClaim(
                            session,
                            this.serviceUniqueId,
                            claimUniqueId,
                            this::serviceExists
                    );
                });
    }

    /**
     * Loads the {@link Session} of given player first from Redis, then from the database if not cached and only
     * returns {@code null} if not found in both.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@link CloudFuture} containing the loaded {@link Session}
     */
    public CloudFuture<Session> session(
            @NotNull UUID uniqueId
    ) {
        var cachedSession = this.redisConnection.session(uniqueId);
        if (cachedSession != null) return new CloudFuture<>(cachedSession);
        return this.sqlConnection.session(uniqueId);
    }

    /**
     * Attempts to claim ownership of a player's {@link Session} from Redis for the current spigot service.
     *
     * @param playerUniqueId {@link UUID} of the player
     * @param claimUniqueId  {@link UUID} distinguishing this claim from other claims of the same service
     * @return {@link SessionClaimResult} of the ownership claim attempt
     * @throws IllegalStateException If the {@link #serviceUniqueId} is {@code null}
     */
    private @NotNull SessionClaimResult claim(
            @NotNull UUID playerUniqueId,
            @NotNull UUID claimUniqueId
    ) {
        if (this.serviceUniqueId == null) throw new IllegalStateException(
                "Sessions can only be claimed by spigot services."
        );

        return this.redisConnection.claim(
                playerUniqueId,
                this.serviceUniqueId,
                claimUniqueId,
                this::serviceExists
        );
    }

    /**
     * Asynchronously waits for the owning service to release the given player's {@link Session}, then transfers
     * it to the SQL database and removes it from Redis.
     * <p>
     * If the owning service no longer is registered and running, the last cached values are recovered directly.
     *
     * @param uniqueId {@link UUID} of the player
     */
    public void saveAndUncacheAsynchronously(
            @NotNull UUID uniqueId
    ) {
        var task = TaskScheduler.executeTask(() -> this.saveAndUncache(uniqueId));
        if (task == null) this.logger.warning(
                "Unable to schedule session transfer for player " + uniqueId
        );
    }

    /**
     * Waits for the owning service to release the given player's {@link Session}, then transfers it to the SQL
     * database and removes it from Redis.
     * <p>
     * If the owning service no longer is registered and running, the last cached values are recovered directly.
     *
     * @param uniqueId {@link UUID} of the player
     */
    private void saveAndUncache(
            @NotNull UUID uniqueId
    ) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var result = this.redisConnection.transferCandidate(uniqueId, this::serviceExists);
                if (result.status() == SessionTransferResult.Status.MISSING) return;
                if (result.status() == SessionTransferResult.Status.OCCUPIED) {
                    Thread.sleep(TRANSFER_RETRY_DELAY_MILLIS);
                    continue;
                }

                var snapshot = result.session().snapshot();
                this.sqlConnection.safeUpdate(
                        uniqueId,
                        snapshot.onlinetimeInMillis(),
                        snapshot.playtimeInMillis()
                ).get();
                this.redisConnection.uncache(uniqueId, result.cacheToken());
                return;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            this.logger.warning(
                    "Failed to transfer cached session for player " + uniqueId + ": "
                            + exception.getMessage()
            );
        }
    }

    /**
     * Releases an owned {@link Session} with the given values so that another service can claim it again.
     *
     * @param playerUniqueId     {@link UUID} of the player
     * @param claimUniqueId      {@link UUID} of the session claim to release
     * @param onlinetimeInMillis Final onlinetime in milliseconds
     * @param playtimeInMillis   Final playtime in milliseconds
     * @throws IllegalStateException If the {@link #serviceUniqueId} is {@code null}
     */
    public void release(
            @NotNull UUID playerUniqueId,
            @NotNull UUID claimUniqueId,
            long onlinetimeInMillis,
            long playtimeInMillis
    ) {
        if (this.serviceUniqueId == null) throw new IllegalStateException(
                "Sessions can only be released by spigot services."
        );

        if (this.redisConnection.release(
                playerUniqueId,
                this.serviceUniqueId,
                claimUniqueId,
                onlinetimeInMillis,
                playtimeInMillis
        )) return;
        this.logger.warning(
                "Unable to release session for player " + playerUniqueId + " because its owner or "
                        + "claim changed or it is no longer cached in Redis"
        );
    }

    /**
     * Updates the {@link Session} of the given player in the SQL database if no cached Redis session exists.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis Onlinetime in milliseconds, or {@code null} to keep the current value
     * @param playtimeInMillis   Playtime in milliseconds, or {@code null} to keep the current value
     * @param force              Whether to overwrite database update restrictions
     * @return {@code true} if the update was scheduled, {@code false} if the session is currently cached
     */
    public boolean update(
            @NotNull UUID uniqueId,
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis,
            boolean force
    ) {
        // TODO: Forward manual session time updates to the current owning service and persist them correctly
        //       via channel messaging if target is online and their session therefore cached in Redis
        if (this.isCached(uniqueId)) return false;
        if (force) {
            this.sqlConnection.update(
                    uniqueId,
                    onlinetimeInMillis,
                    playtimeInMillis
            );
            return true;
        }
        this.sqlConnection.safeUpdate(
                uniqueId,
                onlinetimeInMillis,
                playtimeInMillis
        );
        return true;
    }

    /**
     * Resets the {@link Session} of the given player in the SQL database if no cached Redis session exists.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@code true} if the reset was scheduled, {@code false} if the session is currently cached
     */
    public boolean reset(
            @NotNull UUID uniqueId
    ) {
        // TODO: Forward manual session time resets to the current owning service and persist them correctly
        //       via channel messaging if target is online and their session therefore cached in Redis
        if (this.isCached(uniqueId)) return false;
        this.sqlConnection.delete(uniqueId);
        return true;
    }

    /**
     * Checks whether the {@link Session} of the given player is currently cached in Redis.
     *
     * @param uniqueId {@link UUID} of the player
     * @return {@code true} if no {@link Session} is cached in Redis, {@code false} otherwise
     * @see RedisConnection#exists(UUID)
     */
    private boolean isCached(
            @NotNull UUID uniqueId
    ) {
        return this.redisConnection.exists(uniqueId);
    }

    /**
     * Checks whether the service represented by the given {@link UUID} is still registered and not stopped.
     *
     * @param serviceUniqueId {@link UUID} of the service
     * @return {@code true} if the service is still registered and not stopped, {@code false} otherwise
     */
    private boolean serviceExists(
            @NotNull UUID serviceUniqueId
    ) {
        if (this.cloudServiceProvider == null) throw new IllegalStateException(
                "No cloud service provider found."
        );

        var service = this.cloudServiceProvider.service(serviceUniqueId);
        if (service == null) return false;
        var state = BridgeServiceHelper.guessStateFromServiceInfoSnapshot(service);
        return state != BridgeServiceHelper.ServiceInfoState.STOPPED;
    }

}
