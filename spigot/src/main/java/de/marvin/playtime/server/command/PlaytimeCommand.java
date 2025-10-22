package de.marvin.playtime.server.command;

import de.marvin.api.core.api.UserAPI;
import de.marvin.api.dependencies.lang3.tuple.Pair;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.database.DatabaseHandler;
import de.marvin.playtime.core.util.TimeConverter;
import de.marvin.playtime.server.config.Config;
import eu.cloudnetservice.modules.bridge.player.CloudOfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class PlaytimeCommand implements CommandExecutor {

    private final PlaytimeAPI playtimeAPI;
    private final Config config;

    private final UserAPI userAPI;

    public PlaytimeCommand(
            PlaytimeAPI playtimeAPI,
            Config config,
            UserAPI userAPI
    ) {
        this.playtimeAPI = playtimeAPI;
        this.config = config;
        this.userAPI = userAPI;
    }

    @Override
    public boolean onCommand(
            CommandSender commandSender,
            Command command,
            String label,
            String[] args
    ) {
        if (commandSender instanceof Player player && !player.hasPermission("playtime.moderation")) {
            this.sendOwnPlaytime(player);
            return true;
        }

        switch (args.length) {
            case 0 -> {
                if (!(commandSender instanceof Player player)) {
                    commandSender.sendMessage("This command is for players only.");
                    return false;
                }
                this.sendOwnPlaytime(player);
                return true;
            }

            case 2 -> {
                var action = args[0];
                var target = args[1];

                switch (action.toLowerCase()) {
                    case "get" -> {
                        if (this.isUniqueId(target)) {
                            var uniqueId = UUID.fromString(target);
                            this.userAPI.offlineUserAsync(uniqueId).thenAcceptAsync(cloudOfflinePlayer ->
                                    this.sendConnectionInformation(commandSender, cloudOfflinePlayer)
                            );
                            return true;
                        }
                        this.userAPI.offlineUserAsync(target).thenAcceptAsync(cloudOfflinePlayer ->
                                this.sendConnectionInformation(commandSender, cloudOfflinePlayer)
                        );
                        return true;
                    }
                    case "reset" -> {
                        if (!commandSender.hasPermission("playtime.moderation.reset")) {
                            var message = this.config.message("no-permission");
                            commandSender.sendMessage(message);
                            return false;
                        }

                        if (this.isUniqueId(target)) {
                            var uniqueId = UUID.fromString(target);
                            this.userAPI.offlineUserAsync(uniqueId).thenAcceptAsync(cloudOfflinePlayer -> {
                                if (cloudOfflinePlayer == null) {
                                    var message = this.config.message("player-not-found");
                                    commandSender.sendMessage(message);
                                    return;
                                }
                                this.resetPlayer(commandSender, cloudOfflinePlayer);
                            });
                            return true;
                        }
                        this.userAPI.offlineUserAsync(target).thenAcceptAsync(cloudOfflinePlayer -> {
                            if (cloudOfflinePlayer == null) {
                                var message = this.config.message("player-not-found");
                                commandSender.sendMessage(message);
                                return;
                            }
                            this.resetPlayer(commandSender, cloudOfflinePlayer);
                        });
                        return true;
                    }
                    default -> {
                        var commandUsage = this.config.message("moderation-command-usage");
                        commandSender.sendMessage(commandUsage);
                        return false;
                    }
                }
            }

            case 4 -> {
                if (!commandSender.hasPermission("playtime.moderation.update")) {
                    var message = this.config.message("no-permission");
                    commandSender.sendMessage(message);
                    return false;
                }

                var action = args[0];
                var timeType = args[2];

                if (
                        !action.equalsIgnoreCase("update")
                        || (!timeType.equalsIgnoreCase("online")
                        && !timeType.equalsIgnoreCase("play"))
                ) {
                    var commandUsage = this.config.message("moderation-command-usage");
                    commandSender.sendMessage(commandUsage);
                    return false;
                }

                var target = args[1];
                var newTime = args[3];
                if (this.isUniqueId(target)) {
                    var uniqueId = UUID.fromString(target);
                    this.userAPI.offlineUserAsync(uniqueId).thenAcceptAsync(cloudOfflinePlayer -> {
                        if (cloudOfflinePlayer == null) {
                            var message = this.config.message("player-not-found");
                            commandSender.sendMessage(message);
                            return;
                        }
                        this.updatePlayer(commandSender, cloudOfflinePlayer, newTime, timeType);
                    });
                    return true;
                }
                this.userAPI.offlineUserAsync(target).thenAcceptAsync(cloudOfflinePlayer -> {
                    if (cloudOfflinePlayer == null) {
                        var message = this.config.message("player-not-found");
                        commandSender.sendMessage(message);
                        return;
                    }
                    this.updatePlayer(commandSender, cloudOfflinePlayer, newTime, timeType);
                });
                return true;
            }

            default -> {
                var commandUsage = this.config.message("moderation-command-usage");
                commandSender.sendMessage(commandUsage);
                return false;
            }
        }
    }

    /**
     * Sends the own playtime to the given {@link Player}.
     *
     * @param player {@link Player} to send the playtime to
     */
    private void sendOwnPlaytime(
            @NotNull Player player
    ) {
        var session = this.playtimeAPI.session(player.getUniqueId());
        var playtime = TimeConverter.convertMillisToDaysHoursMinutes(
                session.playtimeInMillis(),
                true,
                true
        );
        var onlinetime = TimeConverter.convertMillisToDaysHoursMinutes(
                session.onlinetimeInMillis(),
                true,
                true
        );
        var message = this.config.message(
                "own-current-playtime",
                Pair.of("playtime", playtime),
                Pair.of("onlinetime", onlinetime)
        );
        player.sendMessage(message);
    }

    /**
     * Sends the playtime information of given {@link UUID}
     * to the given {@link CommandSender}.
     *
     * @param commandSender {@link CommandSender} to send the information to
     * @param cloudOfflinePlayer {@link CloudOfflinePlayer} of the player
     *                          to get the information for
     */
    private void sendConnectionInformation(
            @NotNull CommandSender commandSender,
            @Nullable CloudOfflinePlayer cloudOfflinePlayer
    ) {
        if (cloudOfflinePlayer == null) {
            var message = this.config.message("player-not-found");
            commandSender.sendMessage(message);
            return;
        }

        var session = this.playtimeAPI.session(cloudOfflinePlayer.uniqueId());
        var playtime = TimeConverter.convertMillisToDaysHoursMinutes(
                session.playtimeInMillis(),
                true,
                true
        );
        var onlinetime = TimeConverter.convertMillisToDaysHoursMinutes(
                session.onlinetimeInMillis(),
                true,
                true
        );

        var firstSeen = this.convertTimeToString(
                cloudOfflinePlayer.firstLoginTimeMillis()
        );
        var lastSeen = this.convertTimeToString(
                cloudOfflinePlayer.lastLoginTimeMillis()
        );

        var message = this.config.message(
                "player-get-playtime",
                Pair.of("player", cloudOfflinePlayer.name()),
                Pair.of("playtime", playtime),
                Pair.of("onlinetime", onlinetime),
                Pair.of("first_seen", firstSeen),
                Pair.of("last_seen", lastSeen)
        );
        commandSender.sendMessage(message);
    }

    /**
     * Updates the online-/playtime of given {@link UUID}
     * and notifies the given {@link CommandSender}.
     *
     * @param commandSender {@link CommandSender} to notify
     * @param cloudOfflinePlayer {@link CloudOfflinePlayer} of the player to update
     * @param newTime new time string
     * @param timeType type of time to update ("online" or "play")
     */
    private void updatePlayer(
            CommandSender commandSender,
            CloudOfflinePlayer cloudOfflinePlayer,
            String newTime,
            String timeType
    ) {
        try {
            var convertedTime = TimeConverter.convertTimeStringToLong(newTime);
            this.playtimeAPI.update(
                    cloudOfflinePlayer.uniqueId(),
                    timeType.equalsIgnoreCase("online") ? convertedTime : null,
                    timeType.equalsIgnoreCase("play") ? convertedTime : null
            );
        } catch (Exception exception) {
            var message = this.config.message(
                    "player-update-error",
                    Pair.of("player", cloudOfflinePlayer.name()),
                    Pair.of("error", exception.getMessage())
            );
            commandSender.sendMessage(message);
            return;
        }
        var message = this.config.message(
                "player-update-" + timeType.toLowerCase() + "time",
                Pair.of("player", cloudOfflinePlayer.name()),
                Pair.of("new_time", newTime)
        );
        commandSender.sendMessage(message);
    }

    /**
     * Resets the playtime of given {@link UUID}
     * and notifies the given {@link CommandSender}.
     *
     * @param commandSender {@link CommandSender} to notify
     * @param cloudOfflinePlayer {@link CloudOfflinePlayer} of the player to reset
     */
    private void resetPlayer(
            @NotNull CommandSender commandSender,
            @NotNull CloudOfflinePlayer cloudOfflinePlayer
    ) {
        this.playtimeAPI.reset(cloudOfflinePlayer.uniqueId());
        var message = this.config.message(
                "player-reset-playtime",
                Pair.of("player", cloudOfflinePlayer.name())
        );
        commandSender.sendMessage(message);
    }

    // Helper methods

    /**
     * Checks whether the given string is a valid {@link UUID}.
     *
     * @param string string to check
     * @return {@code true} if the string is a valid {@link UUID},
     *         {@code false} otherwise.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean isUniqueId(
            String string
    ) {
        try {
            if (string == null) return false;
            UUID.fromString(string);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Converts the given time in milliseconds to a formatted date string.
     *
     * @param time time in milliseconds
     * @return Formatted date string.
     */
    private String convertTimeToString(
            long time
    ){
        var date = new Date(time);
        var format = new SimpleDateFormat("dd.MM.yyyy',' HH:mm:ss");
        return format.format(date);
    }

}
