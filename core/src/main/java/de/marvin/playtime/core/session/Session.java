package de.marvin.playtime.core.session;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Holds information about a player's online- and playtime.
 */
public class Session {

    /**
     * {@link UUID} of the player.
     */
    @NotNull private final UUID uniqueId;

    /**
     * Online time of the player in milliseconds.
     */
    private long onlinetimeInMillis;
    /**
     * Play time of the player in milliseconds.
     */
    private long playtimeInMillis;

    /**
     * Determines, if playtime should be counted.
     */
    private boolean countPlaytime;
    /**
     * Determines, if player currently is away from keyboard and no playtime should be counted.
     */
    private boolean awayFromKeyboard;
    /**
     * Timestamp in milliseconds at which the player's last activity took place.
     */
    private long lastActivity;

    /**
     * Creates a new {@link Session} instance.
     *
     * @param uniqueId           {@link UUID} of the player
     * @param onlinetimeInMillis Onlinetime in milliseconds
     * @param playtimeInMillis   Playtime in milliseconds
     */
    public Session(
            @NotNull UUID uniqueId,
            long onlinetimeInMillis,
            long playtimeInMillis
    ) {
        this.uniqueId = uniqueId;

        this.onlinetimeInMillis = onlinetimeInMillis;
        this.playtimeInMillis = playtimeInMillis;

        this.countPlaytime = false;
        this.awayFromKeyboard = false;
        this.lastActivity = System.currentTimeMillis();
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
     * Returns the onlinetime in milliseconds.
     *
     * @return Onlinetime in milliseconds
     */
    public synchronized long onlinetimeInMillis() {
        return this.onlinetimeInMillis;
    }

    /**
     * Returns the playtime in milliseconds.
     *
     * @return Playtime in milliseconds
     */
    public synchronized long playtimeInMillis() {
        return this.playtimeInMillis;
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
     * Returns whether player currently is away from keyboard or not.
     *
     * @return {@code true} if player currently is afk, {@code false} otherwise
     */
    public synchronized boolean awayFromKeyboard() {
        return this.awayFromKeyboard;
    }

    /**
     * Returns timestamp in milliseconds at which the player's last activity took place.
     *
     * @return Timestamp in milliseconds at which the player's last activity took place
     */
    public synchronized long lastActivity() {
        return this.lastActivity;
    }

    /**
     * Returns a consistent snapshot of the session's persisted time values.
     *
     * @return {@link Snapshot} of onlinetime and playtime in milliseconds
     */
    public synchronized Snapshot snapshot() {
        return new Snapshot(
                this.onlinetimeInMillis,
                this.playtimeInMillis
        );
    }

    /**
     * Sets whether playtime should be counted or not.
     *
     * @param countPlaytime Playtime counting status
     */
    public synchronized void setCountPlaytime(
            boolean countPlaytime
    ) {
        this.countPlaytime = countPlaytime;
    }

    /**
     * Sets whether the player is away from keyboard or not.
     *
     * @param awayFromKeyboard AFK status
     */
    public synchronized void setAwayFromKeyboard(
            boolean awayFromKeyboard
    ) {
        this.awayFromKeyboard = awayFromKeyboard;
    }

    /**
     * Updates last activity timestamp.
     */
    public synchronized void updateLastActivity() {
        this.lastActivity = System.currentTimeMillis();
        if (this.awayFromKeyboard) this.awayFromKeyboard = false;
    }

    /**
     * Updates the sessions {@link Session#onlinetimeInMillis} and {@link Session#playtimeInMillis} with given
     * values if not null.
     *
     * @param onlinetimeInMillis New onlinetime in milliseconds
     * @param playtimeInMillis   New playtime in milliseconds
     */
    public synchronized void update(
            @Nullable Long onlinetimeInMillis,
            @Nullable Long playtimeInMillis
    ) {
        if (onlinetimeInMillis != null) this.onlinetimeInMillis = onlinetimeInMillis;
        if (playtimeInMillis != null) this.playtimeInMillis = playtimeInMillis;
    }

    /**
     * Updates the sessions {@link Session#onlinetimeInMillis} and {@link Session#playtimeInMillis} based on
     * the {@link Session}'s state.
     */
    public synchronized void update() {
        this.onlinetimeInMillis += 1000L;
        if (!this.countPlaytime) return;
        if (this.awayFromKeyboard) return;
        if (this.lastActivity + SessionHandler.afkThreshold() <= System.currentTimeMillis()) {
            this.setAwayFromKeyboard(true);
            return;
        }
        this.playtimeInMillis += 1000L;
    }

    /**
     * Creates a default {@link Session} with {@code 0} onlinetime and {@code 0} playtime.
     *
     * @param uniqueId {@link UUID} of the player
     * @return Default {@link Session}
     */
    public static Session defaultSession(
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
