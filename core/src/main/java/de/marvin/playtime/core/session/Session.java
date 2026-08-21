package de.marvin.playtime.core.session;

import de.marvin.playtime.core.listener.AwayStatusChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Holds information about a player's online- and playtime.
 */
public class Session {

    private static final long NANOSECONDS_PER_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);

    /**
     * {@link UUID} of the player.
     */
    @NotNull
    private final UUID uniqueId;

    /**
     * Online time of the player in nanoseconds.
     */
    private long onlinetimeInNanos;
    /**
     * Play time of the player in nanoseconds.
     */
    private long playtimeInNanos;

    /**
     * Timestamp of the last accumulated session state.
     */
    private long lastUpdateNanos;
    /**
     * Timestamp of the player's last activity.
     */
    private long lastActivityNanos;

    /**
     * Whether playtime currently should be counted.
     */
    private boolean countPlaytime;
    /**
     * Whether the player currently is away from keyboard and no playtime should be counted.
     */
    private boolean awayFromKeyboard;

    /**
     * {@link AwayStatusChangeListener} that receives {@link #awayFromKeyboard} status changes for this
     * {@link Session}.
     */
    private @Nullable AwayStatusChangeListener awayStatusChangeListener;

    /**
     * Whether time should currently be tracked in this {@link Session}.
     */
    private boolean trackingTime;

    /**
     * Creates a new {@link Session} instance.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis Onlinetime in milliseconds
     * @param playtimeInMillis   Playtime in milliseconds
     * @throws ArithmeticException If either time exceeds the nanosecond range
     */
    public Session(
            @NotNull UUID uniqueId,
            long onlinetimeInMillis,
            long playtimeInMillis
    ) {
        this.uniqueId = uniqueId;

        this.onlinetimeInNanos = Session.toNanos(onlinetimeInMillis);
        this.playtimeInNanos = Session.toNanos(playtimeInMillis);

        this.lastUpdateNanos = System.nanoTime();
        this.lastActivityNanos = this.lastUpdateNanos;

        this.countPlaytime = false;
        this.awayFromKeyboard = false;

        this.trackingTime = false;
    }

    /**
     * Returns the {@link UUID} of the player.
     *
     * @return {@link UUID} of the player
     */
    public @NotNull UUID uniqueId() {
        return this.uniqueId;
    }

    /**
     * Returns the current onlinetime in milliseconds.
     *
     * @return Onlinetime in milliseconds
     */
    public synchronized long onlinetimeInMillis() {
        this.accumulateElapsedTime(System.nanoTime());
        return Session.toMillis(this.onlinetimeInNanos);
    }

    /**
     * Returns the current playtime in milliseconds.
     *
     * @return Playtime in milliseconds
     */
    public synchronized long playtimeInMillis() {
        this.accumulateElapsedTime(System.nanoTime());
        return Session.toMillis(this.playtimeInNanos);
    }

    /**
     * Returns a {@link Snapshot} of the {@link Session}'s persisted time values.
     *
     * @return {@link Snapshot} of onlinetime and playtime in milliseconds
     */
    public synchronized @NotNull Snapshot snapshot() {
        this.accumulateElapsedTime(System.nanoTime());
        return this.currentSnapshot();
    }

    /**
     * Returns whether playtime should be counted or not.
     *
     * @return {@code true} if playtime should be counted, {@code false} otherwise
     */
    public synchronized boolean countPlaytime() {
        return this.countPlaytime;
    }

    /**
     * Returns whether the player is currently away from keyboard.
     *
     * @return {@code true} if player currently is AFK, {@code false} otherwise
     */
    public synchronized boolean awayFromKeyboard() {
        this.accumulateElapsedTime(System.nanoTime());
        return this.awayFromKeyboard;
    }

    /**
     * Sets whether playtime should be counted.
     *
     * @param countPlaytime Playtime counting status
     */
    public synchronized void setCountPlaytime(
            boolean countPlaytime
    ) {
        this.accumulateElapsedTime(System.nanoTime());
        this.countPlaytime = countPlaytime;
    }

    /**
     * Sets whether the player is away from keyboard.
     *
     * @param awayFromKeyboard AFK status
     */
    public synchronized void setAwayFromKeyboard(
            boolean awayFromKeyboard
    ) {
        var previousStatus = this.awayFromKeyboard;
        this.accumulateElapsedTime(System.nanoTime(), false);
        this.changeAwayFromKeyboard(awayFromKeyboard);
        if (previousStatus != this.awayFromKeyboard) this.notifyAwayStatusChange();
    }

    /**
     * Toggles whether the player is away from keyboard.
     */
    public synchronized void toggleAwayFromKeyboard() {
        // When toggling AFK status off, update last activity to prevent immediate reactivation of AFK status
        // due to the automatic threshold
        if (this.awayFromKeyboard) {
            this.updateLastActivity();
            return;
        }
        this.setAwayFromKeyboard(true);
    }

    /**
     * Sets the {@link AwayStatusChangeListener} for this {@link Session}.
     *
     * @param listener {@link AwayStatusChangeListener} to notify, or {@code null} to disable notifications
     */
    synchronized void setAwayStatusChangeListener(
            @Nullable AwayStatusChangeListener listener
    ) {
        this.awayStatusChangeListener = listener;
    }

    /**
     * Accumulates elapsed time up to the activity and updates the {@link Session#lastActivityNanos} timestamp.
     * <p>
     * <b>Note:</b> Since this method is called on a player activity, it also clears the AFK status if it was
     * set previously.
     */
    public synchronized void updateLastActivity() {
        var nowNanos = System.nanoTime();
        var previousStatus = this.awayFromKeyboard;
        this.accumulateElapsedTime(nowNanos, false);
        this.lastActivityNanos = nowNanos;
        this.changeAwayFromKeyboard(false);
        if (previousStatus != this.awayFromKeyboard) this.notifyAwayStatusChange();
    }

    /**
     * Updates the {@link Session}'s {@link Session#onlinetimeInMillis} and {@link Session#playtimeInMillis}
     * with given values if not null.
     *
     * @param onlinetimeInMillis New onlinetime in milliseconds
     * @param playtimeInMillis   New playtime in milliseconds
     * @throws ArithmeticException If either specified time exceeds the nanosecond range
     */
    public synchronized void update(
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis
    ) {
        this.accumulateElapsedTime(System.nanoTime());
        if (onlinetimeInMillis != null)
            this.onlinetimeInNanos = Session.toNanos(onlinetimeInMillis);
        if (playtimeInMillis != null)
            this.playtimeInNanos = Session.toNanos(playtimeInMillis);
    }

    /**
     * Accumulates the time elapsed since the previous update based on the {@link Session}'s state.
     */
    public synchronized void update() {
        this.accumulateElapsedTime(System.nanoTime());
    }

    /**
     * Adds persisted base values without changing the time measured locally since this {@link Session} was
     * created.
     *
     * @param onlinetimeInMillis Onlinetime to add, or {@code null}
     * @param playtimeInMillis   Playtime to add, or {@code null}
     * @throws ArithmeticException If conversion or addition exceeds the nanosecond range
     */
    synchronized void addPersistedTime(
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis
    ) {
        this.accumulateElapsedTime(System.nanoTime());
        if (onlinetimeInMillis != null)
            this.onlinetimeInNanos = Math.addExact(
                    this.onlinetimeInNanos,
                    Session.toNanos(onlinetimeInMillis)
            );
        if (playtimeInMillis != null)
            this.playtimeInNanos = Math.addExact(
                    this.playtimeInNanos,
                    Session.toNanos(playtimeInMillis)
            );
    }

    /**
     * Starts measuring elapsed time for this {@link Session} from its creation timestamp.
     */
    synchronized void startTracking() {
        if (this.trackingTime) return;
        this.trackingTime = true;
    }

    /**
     * Accumulates all remaining elapsed time, stops time measurement and returns the persisted values.
     *
     * @return {@link Snapshot} of onlinetime and playtime in milliseconds
     */
    synchronized Snapshot finishTracking() {
        this.accumulateElapsedTime(System.nanoTime(), false);
        this.trackingTime = false;
        return this.currentSnapshot();
    }

    /**
     * Resets this {@link Session} while retaining active time measurement.
     */
    synchronized void reset() {
        var nowNanos = System.nanoTime();
        var previousStatus = this.awayFromKeyboard;
        this.accumulateElapsedTime(nowNanos, false);
        this.onlinetimeInNanos = 0;
        this.playtimeInNanos = 0;
        this.countPlaytime = false;
        this.changeAwayFromKeyboard(false);
        this.lastActivityNanos = nowNanos;
        if (previousStatus != this.awayFromKeyboard) this.notifyAwayStatusChange();
    }

    /**
     * Accumulates the elapsed time since the previous update according to the {@link Session} state during
     * that interval. If no playtime is counted at the time or the player is marked as AFK, no elapsed
     * playtime is accumulated. If the {@link SessionHandler#afkThreshold()} was crossed, only the part
     * before the threshold is added to playtime.
     *
     * @param nowNanos Current timestamp in nanoseconds
     */
    private void accumulateElapsedTime(
            long nowNanos
    ) {
        this.accumulateElapsedTime(nowNanos, true);
    }

    /**
     * Accumulates elapsed time and optionally reports an automatic AFK status change.
     *
     * @param nowNanos               Current timestamp in nanoseconds
     * @param notifyAwayStatusChange Whether an AFK status change should be reported immediately
     */
    private void accumulateElapsedTime(
            long nowNanos,
            boolean notifyAwayStatusChange
    ) {
        if (!this.trackingTime) return;

        // Always count onlinetime
        var intervalStartNanos = this.lastUpdateNanos;
        var elapsedNanos = nowNanos - intervalStartNanos;
        if (elapsedNanos <= 0) return;
        this.lastUpdateNanos = nowNanos;
        this.onlinetimeInNanos = Math.addExact(this.onlinetimeInNanos, elapsedNanos);

        // Accumulate playtime only while enabled and before the player becomes AFK
        var afkThresholdNanos = TimeUnit.MILLISECONDS.toNanos(SessionHandler.afkThreshold());
        if (this.countPlaytime && !this.awayFromKeyboard) {
            var nanosUntilAfk = afkThresholdNanos - (intervalStartNanos - this.lastActivityNanos);
            if (nanosUntilAfk > 0) this.playtimeInNanos = Math.addExact(
                    this.playtimeInNanos,
                    Math.min(elapsedNanos, nanosUntilAfk)
            );
        }

        // Mark the player as AFK if the threshold was crossed
        if (nowNanos - this.lastActivityNanos < afkThresholdNanos) return;
        if (this.changeAwayFromKeyboard(true) && notifyAwayStatusChange) this.notifyAwayStatusChange();
    }

    /**
     * Changes the player's AFK status and returns whether this change was effective.
     *
     * @param status New AFK status
     * @return {@code true} if the status change was effective, {@code false} otherwise
     */
    private boolean changeAwayFromKeyboard(
            boolean status
    ) {
        if (this.awayFromKeyboard == status) return false;
        this.awayFromKeyboard = status;
        return true;
    }

    /**
     * Notifies the configured {@link AwayStatusChangeListener} about the current AFK status.
     */
    private void notifyAwayStatusChange() {
        if (this.awayStatusChangeListener == null) return;
        this.awayStatusChangeListener.handleAwayStatusChange(this.uniqueId, this.awayFromKeyboard);
    }

    /**
     * Returns the persisted values without accumulating additional elapsed time.
     *
     * @return Current {@link Snapshot} of onlinetime and playtime in milliseconds
     */
    private @NotNull Snapshot currentSnapshot() {
        return new Snapshot(
                Session.toMillis(this.onlinetimeInNanos),
                Session.toMillis(this.playtimeInNanos)
        );
    }

    /**
     * Converts milliseconds to nanoseconds.
     *
     * @param milliseconds Time in milliseconds
     * @return Time in nanoseconds
     * @throws ArithmeticException If the converted value exceeds the range of a {@code long}
     */
    private static long toNanos(
            long milliseconds
    ) {
        return Math.multiplyExact(milliseconds, NANOSECONDS_PER_MILLISECOND);
    }

    /**
     * Converts the nanoseconds to milliseconds.
     *
     * @param nanoseconds Time in nanoseconds
     * @return Time in milliseconds
     */
    private static long toMillis(
            long nanoseconds
    ) {
        return nanoseconds / NANOSECONDS_PER_MILLISECOND;
    }

    /**
     * Extracts the loaded {@link Session} from a {@link SessionState}.
     *
     * @param state Current {@link SessionState}, or {@code null}
     * @return Loaded {@link Session}, or {@code null} while absent or loading
     */
    static @Nullable Session fromState(
            @Nullable SessionState state
    ) {
        return state instanceof LoadedSession loadedSession
                ? loadedSession.session()
                : null;
    }

    /**
     * Creates a default {@link Session} with {@code 0} onlinetime and {@code 0} playtime.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Default {@link Session}
     */
    public static @NotNull Session defaultSession(
            @NotNull UUID uniqueId
    ) {
        return new Session(uniqueId, 0, 0);
    }

    /**
     * Immutable snapshot of a {@link Session}'s persisted time values.
     *
     * @param onlinetimeInMillis Onlinetime in milliseconds
     * @param playtimeInMillis   Playtime in milliseconds
     */
    public record Snapshot(
            long onlinetimeInMillis,
            long playtimeInMillis
    ) {
    }

}
