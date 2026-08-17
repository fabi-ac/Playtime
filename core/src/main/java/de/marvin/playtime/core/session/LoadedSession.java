package de.marvin.playtime.core.session;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a {@link Session} that has completed loading.
 *
 * @param session Loaded {@link Session}
 */
record LoadedSession(
        @NotNull Session session
) implements SessionState {
}
