package de.marvin.playtime.core.session;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents a {@link Session} that has completed loading.
 *
 * @param session       Loaded {@link Session}
 * @param claimUniqueId {@link UUID} of the load attempt
 */
record LoadedSession(
        @NotNull Session session,
        @NotNull UUID claimUniqueId
) implements SessionState {
}
