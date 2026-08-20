package de.marvin.playtime.server.command.subcommand;

import de.marvin.api.core.api.UserAPI;
import de.marvin.api.dependencies.lang3.tuple.Pair;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.util.TimeConverter;
import de.marvin.playtime.server.config.Config;
import eu.cloudnetservice.modules.bridge.player.CloudOfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UpdateSubCommand extends PlaytimeSubCommand {

    public UpdateSubCommand(
            @NotNull PlaytimeAPI playtimeAPI,
            @NotNull Config config,
            @NotNull UserAPI userAPI
    ) {
        super(playtimeAPI, config, userAPI);
    }

    /**
     * Returns the name of the sub-command.
     *
     * @return The name of the sub-command
     */
    @Override
    public @NotNull String name() {
        return "update";
    }

    /**
     * {@inheritDoc}
     *
     * @param commandSender The executing {@link CommandSender} of the sub-command
     * @param args          The arguments passed to the sub-command
     * @return {@code true} if the command was executed successfully, {@code false} if any error occurred
     */
    @Override
    public boolean onCommand(
            @NotNull CommandSender commandSender,
            String @NotNull [] args
    ) {
        if (args.length < 3 || !TimeType.isValid(args[1])) {
            this.sendUsage(commandSender);
            return false;
        }

        var target = args[0];
        var timeType = TimeType.fromArgument(args[1]);
        var newTime = args[2];
        this.findOfflinePlayer(target, cloudOfflinePlayer -> {
            if (cloudOfflinePlayer == null) {
                var message = this.config.message("player-not-found");
                commandSender.sendMessage(message);
                return;
            }
            this.updatePlayer(commandSender, cloudOfflinePlayer, newTime, timeType);
        });
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @return The permissions needed for execution of the sub-command
     */
    @Override
    public @Nullable String permission() {
        return "update";
    }

    /**
     * Tries to update the playtime or onlinetime of the given {@link CloudOfflinePlayer} and sends a message
     * with the result to the given {@link CommandSender}.
     *
     * @param commandSender      {@link CommandSender} to send the result message to
     * @param cloudOfflinePlayer {@link CloudOfflinePlayer} whose playtime or onlinetime to update
     * @param newTime            New time to set
     * @param timeType           {@link TimeType} indicating whether to update playtime or onlinetime
     */
    private void updatePlayer(
            @NotNull CommandSender commandSender,
            @NotNull CloudOfflinePlayer cloudOfflinePlayer,
            @NotNull String newTime,
            @NotNull TimeType timeType
    ) {
        try {
            var convertedTime = TimeConverter.convertTimeStringToLong(newTime);
            var updated = this.playtimeAPI.update(
                    cloudOfflinePlayer.uniqueId(),
                    timeType == TimeType.ONLINE_TIME ? convertedTime : null,
                    timeType == TimeType.PLAY_TIME ? convertedTime : null
            );
            if (!updated) {
                var message = this.config.message(
                        "player-modification-to-offline-players-only",
                        Pair.of("player", cloudOfflinePlayer.name())
                );
                commandSender.sendMessage(message);
                return;
            }
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
                "player-update-" + timeType.argument() + "time",
                Pair.of("player", cloudOfflinePlayer.name()),
                Pair.of("new_time", newTime)
        );
        commandSender.sendMessage(message);
    }

    /**
     * Enum representing the time types for updating a player's time.
     */
    private enum TimeType {

        ONLINE_TIME("online"),
        PLAY_TIME("play");

        private final @NotNull String argument;

        TimeType(
                @NotNull String argument
        ) {
            this.argument = argument;
        }

        /**
         * Returns the argument name of the {@link TimeType}.
         *
         * @return Argument name of the {@link TimeType}
         */
        private @NotNull String argument() {
            return this.argument;
        }

        /**
         * Returns the {@link TimeType} corresponding to the given argument.
         *
         * @param argument Argument to get the {@link TimeType} for
         * @return {@link TimeType} corresponding to the given argument, or {@code null} if no matching
         * {@link TimeType} is found
         */
        private static @Nullable TimeType fromArgument(
                @Nullable String argument
        ) {
            if (argument == null) return null;
            for (var type : values()) {
                if (type.argument().equalsIgnoreCase(argument)) {
                    return type;
                }
            }
            return null;
        }

        /**
         * Checks if the given {@link TimeType} is valid.
         *
         * @param timeType String to check
         * @return {@code true} if the {@link TimeType} is valid, {@code false} otherwise
         */
        private static boolean isValid(
                @NotNull String timeType
        ) {
            for (var type : values()) {
                if (type.argument().equalsIgnoreCase(timeType)) {
                    return true;
                }
            }
            return false;
        }

    }

}
