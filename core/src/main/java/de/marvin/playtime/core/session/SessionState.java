package de.marvin.playtime.core.session;

/**
 * Represents the lifecycle state of a player's {@link Session} in the local cache. A {@link Session} is either
 * being loaded from Redis or SQL, or has completed loading and is ready for use.
 */
sealed interface SessionState permits LoadingSession, LoadedSession {
}
