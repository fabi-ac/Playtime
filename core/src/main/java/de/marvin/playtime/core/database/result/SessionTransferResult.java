package de.marvin.playtime.core.database.result;

import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents the result of retrieving a cached session for SQL transfer.
 *
 * @param status          {@link Status Outcome} of the retrieval
 * @param session         {@link Session} that can be transferred, or {@code null}
 * @param cacheToken      Cache token identifying the Redis snapshot, or {@code null}
 * @param serviceUniqueId {@link UUID} of the active service preventing transfer, or {@code null}
 */
public record SessionTransferResult(
        @NotNull SessionTransferResult.Status status,
        @Nullable Session session,
        @Nullable String cacheToken,
        @Nullable UUID serviceUniqueId
) {

    /**
     * Creates a transferable {@link SessionTransferResult}.
     *
     * @param session    {@link Session} represented by the cached value
     * @param cacheToken Cache token identifying the Redis snapshot
     * @return Transferable {@link SessionTransferResult}
     */
    public static @NotNull SessionTransferResult transferable(
            @NotNull Session session,
            @NotNull String cacheToken
    ) {
        return new SessionTransferResult(Status.TRANSFERABLE, session, cacheToken, null);
    }

    /**
     * Creates an occupied {@link SessionTransferResult}.
     *
     * @param serviceUniqueId {@link UUID} of the active service owning the {@link Session}
     * @return Occupied {@link SessionTransferResult}
     */
    public static @NotNull SessionTransferResult occupied(
            @NotNull UUID serviceUniqueId
    ) {
        return new SessionTransferResult(Status.OCCUPIED, null, null, serviceUniqueId);
    }

    /**
     * Creates a {@link SessionTransferResult} indicating that no cached session exists.
     *
     * @return Missing {@link SessionTransferResult}
     */
    public static @NotNull SessionTransferResult missing() {
        return new SessionTransferResult(Status.MISSING, null, null, null);
    }

    /**
     * Possible outcomes of retrieving a Redis session for SQL transfer.
     */
    public enum Status {

        /**
         * The cached session can be transferred.
         */
        TRANSFERABLE,

        /**
         * An active service still owns the session.
         */
        OCCUPIED,

        /**
         * No cached session exists.
         */
        MISSING

    }

}