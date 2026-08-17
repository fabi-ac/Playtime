package de.marvin.playtime.core.session;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a {@link Session} whose Redis or SQL load has not completed yet. Updates received while loading
 * are retained and applied to the loaded {@link Session} before it is published to the local cache.
 */
final class LoadingSession implements SessionState {

    private Long onlinetimeInMillis;
    private Long playtimeInMillis;
    private Boolean countPlaytime;
    private Boolean awayFromKeyboard;
    private boolean activityUpdated;
    private boolean reset;

    /**
     * Retains updated time values until the session load completes. A {@code null} value leaves the
     * corresponding retained value unchanged.
     *
     * @param onlinetimeInMillis New onlinetime in milliseconds, or {@code null}
     * @param playtimeInMillis   New playtime in milliseconds, or {@code null}
     */
    void update(
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis
    ) {
        if (onlinetimeInMillis != null) this.onlinetimeInMillis = onlinetimeInMillis;
        if (playtimeInMillis != null) this.playtimeInMillis = playtimeInMillis;
    }

    /**
     * Retains whether playtime should be counted once the session load completes.
     *
     * @param countPlaytime Playtime counting status
     */
    void setCountPlaytime(
            boolean countPlaytime
    ) {
        this.countPlaytime = countPlaytime;
    }

    /**
     * Retains the player's AFK status until the session load completes.
     *
     * @param awayFromKeyboard AFK status
     */
    void setAwayFromKeyboard(
            boolean awayFromKeyboard
    ) {
        this.awayFromKeyboard = awayFromKeyboard;
    }

    /**
     * Retains player activity until the session load completes. Activity also clears a previously retained AFK
     * status, matching {@link Session#updateLastActivity()}.
     */
    void updateLastActivity() {
        this.activityUpdated = true;
        this.awayFromKeyboard = false;
    }

    /**
     * Marks the pending {@link Session} for reset and discards previously retained time updates.
     */
    void reset() {
        this.reset = true;
        this.onlinetimeInMillis = null;
        this.playtimeInMillis = null;
        this.countPlaytime = null;
        this.awayFromKeyboard = null;
        this.activityUpdated = false;
    }

    /**
     * Resolves this loading state into a usable {@link Session}. A pending reset is applied first, followed by
     * retained time updates.
     *
     * @param uniqueId     {@link UUID} of the player
     * @param loadedSession {@link Session} loaded from Redis or SQL
     * @return Resolved {@link Session} containing all changes received while loading
     */
    Session resolve(
            @NotNull UUID uniqueId,
            @NotNull Session loadedSession
    ) {
        var resolvedSession = this.reset
                ? Session.defaultSession(uniqueId)
                : loadedSession;
        resolvedSession.update(
                this.onlinetimeInMillis,
                this.playtimeInMillis
        );
        if (this.countPlaytime != null)
            resolvedSession.setCountPlaytime(this.countPlaytime);
        if (this.activityUpdated)
            resolvedSession.updateLastActivity();
        if (this.awayFromKeyboard != null)
            resolvedSession.setAwayFromKeyboard(this.awayFromKeyboard);
        return resolvedSession;
    }

}
