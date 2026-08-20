package de.marvin.playtime.core.database.result;

import de.marvin.playtime.core.session.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents the result of trying to claim ownership of a player's cached {@link Session}.
 *
 * @param status          {@link Status Outcome} of the claim
 * @param session         Claimed {@link Session}, or {@code null}
 * @param serviceUniqueId {@link UUID} of the active service preventing the claim, or {@code null}
 */
public record SessionClaimResult(
        @NotNull SessionClaimResult.Status status,
        @Nullable Session session,
        @Nullable UUID serviceUniqueId
) {

    /**
     * Creates a successful {@link SessionClaimResult}.
     *
     * @param session Claimed {@link Session}
     * @return Successful {@link SessionClaimResult}
     */
    public static @NotNull SessionClaimResult claimed(
            @NotNull Session session
    ) {
        return new SessionClaimResult(Status.CLAIMED, session, null);
    }

    /**
     * Creates an occupied {@link SessionClaimResult}.
     *
     * @param serviceUniqueId {@link UUID} of the active service owning the {@link Session}
     * @return Occupied {@link SessionClaimResult}
     */
    public static @NotNull SessionClaimResult occupied(
            @NotNull UUID serviceUniqueId
    ) {
        return new SessionClaimResult(Status.OCCUPIED, null, serviceUniqueId);
    }

    /**
     * Creates a result indicating that no cached session exists.
     *
     * @return Missing {@link SessionClaimResult}
     */
    public static @NotNull SessionClaimResult missing() {
        return new SessionClaimResult(Status.MISSING, null, null);
    }

    /**
     * Returns whether ownership was claimed.
     *
     * @return {@code true} if {@link #session()} is available, otherwise {@code false}
     */
    public boolean claimed() {
        return this.status == Status.CLAIMED;
    }

    /**
     * Possible outcomes of claiming a session.
     */
    public enum Status {

        /**
         * Ownership was claimed.
         */
        CLAIMED,

        /**
         * Another active service owns the session.
         */
        OCCUPIED,

        /**
         * No cached session exists.
         */
        MISSING

    }

}
