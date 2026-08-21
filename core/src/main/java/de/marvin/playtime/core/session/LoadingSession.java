package de.marvin.playtime.core.session;

import de.marvin.playtime.core.listener.AwayStatusChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a {@link Session} whose Redis or SQL load has not completed yet. Time and state changes received
 * while loading are measured locally and merged with the persisted values before publication to the local cache.
 */
final class LoadingSession implements SessionState {

    /**
     * {@link UUID} to distinguish this load attempt from other attempts on the same service.
     */
    private final UUID claimUniqueId = UUID.randomUUID();

    /**
     * Represents the {@link Session} object that has not been fully loaded from the database yet.
     */
    private final Session pendingSession;

    /**
     * Whether the onlinetime was overridden manually.
     */
    private boolean onlinetimeOverridden;
    /**
     * Whether the playtime was overridden manually.
     */
    private boolean playtimeOverridden;
    /**
     * Whether the online- and playtime were reset.
     */
    private boolean reset;

    /**
     * Creates a loading state and records when local time measurement should begin.
     *
     * @param uniqueId                 {@link UUID} of the player
     * @param awayStatusChangeListener {@link AwayStatusChangeListener} that receives AFK status changes
     */
    LoadingSession(
            @NotNull UUID uniqueId,
            @NotNull AwayStatusChangeListener awayStatusChangeListener
    ) {
        this.pendingSession = Session.defaultSession(uniqueId);
        this.pendingSession.setAwayStatusChangeListener(awayStatusChangeListener);
        this.pendingSession.startTracking();
    }

    /**
     * Returns {@link UUID} of this load attempt.
     *
     * @return {@link UUID} of this load attempt
     */
    @Override
    public @NotNull UUID claimUniqueId() {
        return this.claimUniqueId;
    }

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
        this.pendingSession.update(onlinetimeInMillis, playtimeInMillis);
        if (onlinetimeInMillis != null) this.onlinetimeOverridden = true;
        if (playtimeInMillis != null) this.playtimeOverridden = true;
    }

    /**
     * Retains whether playtime should be counted once the session load completes.
     *
     * @param countPlaytime Playtime counting status
     */
    void setCountPlaytime(
            boolean countPlaytime
    ) {
        this.pendingSession.setCountPlaytime(countPlaytime);
    }

    /**
     * Retains the player's AFK status until the session load completes.
     *
     * @param awayFromKeyboard AFK status
     */
    void setAwayFromKeyboard(
            boolean awayFromKeyboard
    ) {
        this.pendingSession.setAwayFromKeyboard(awayFromKeyboard);
    }

    /**
     * Retains player activity until the session load completes. Activity also clears a previously retained AFK
     * status, matching {@link Session#updateLastActivity()}.
     */
    void updateLastActivity() {
        this.pendingSession.updateLastActivity();
    }

    /**
     * Marks the pending {@link Session} for reset and discards previously retained time updates.
     */
    void reset() {
        this.pendingSession.reset();
        this.reset = true;
        this.onlinetimeOverridden = false;
        this.playtimeOverridden = false;
    }

    /**
     * Resolves this loading state into a usable {@link Session}. Unless reset or explicitly overridden while
     * loading, persisted values are added to the time already measured locally.
     *
     * @param loadedSession {@link Session} loaded from Redis or SQL
     * @return Resolved {@link Session} containing all changes received while loading
     */
    Session resolve(
            @NotNull Session loadedSession
    ) {
        if (this.reset) return this.pendingSession;

        var loadedSnapshot = loadedSession.snapshot();
        this.pendingSession.addPersistedTime(
                this.onlinetimeOverridden ? null : loadedSnapshot.onlinetimeInMillis(),
                this.playtimeOverridden ? null : loadedSnapshot.playtimeInMillis()
        );
        return this.pendingSession;
    }

}
