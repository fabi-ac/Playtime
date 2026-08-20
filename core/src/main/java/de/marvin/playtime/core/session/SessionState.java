package de.marvin.playtime.core.session;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents the lifecycle state of a player's {@link Session} in the local cache. A {@link Session} is either
 * being loaded from Redis or SQL, or has completed loading and is ready for use.
 */
sealed interface SessionState permits LoadingSession, LoadedSession {

    /**
     * Returns {@link UUID} of this load attempt.
     *
     * @return {@link UUID} of this load attempt
     */
    @NotNull UUID claimUniqueId();

}
