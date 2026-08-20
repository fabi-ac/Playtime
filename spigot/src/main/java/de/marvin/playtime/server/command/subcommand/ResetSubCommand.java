package de.marvin.playtime.server.command.subcommand;

import de.marvin.api.core.api.UserAPI;
import de.marvin.api.dependencies.lang3.tuple.Pair;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.server.config.Config;
import eu.cloudnetservice.modules.bridge.player.CloudOfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResetSubCommand extends PlaytimeSubCommand {

    public ResetSubCommand(
            @NotNull PlaytimeAPI playtimeAPI,
            @NotNull Config config,
            @NotNull UserAPI userAPI
    ) {
        super(playtimeAPI, config, userAPI);
    }

    /**
     * {@inheritDoc}
     *
     * @return The name of the sub-command
     */
    @Override
    public @NotNull String name() {
        return "reset";
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
        if (args.length < 1) {
            this.sendUsage(commandSender);
            return false;
        }

        this.findOfflinePlayer(args[0], cloudOfflinePlayer -> {
            if (cloudOfflinePlayer == null) {
                var message = this.config.message("player-not-found");
                commandSender.sendMessage(message);
                return;
            }
            this.resetPlayer(commandSender, cloudOfflinePlayer);
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
        return "reset";
    }

    /**
     * Tries to reset the playtime of the given {@link CloudOfflinePlayer} and sends a message with the result
     * to the given {@link CommandSender}.
     *
     * @param commandSender      {@link CommandSender} to send the result message to
     * @param cloudOfflinePlayer {@link CloudOfflinePlayer} whose playtime to reset
     */
    private void resetPlayer(
            @NotNull CommandSender commandSender,
            @NotNull CloudOfflinePlayer cloudOfflinePlayer
    ) {
        if (!this.playtimeAPI.reset(cloudOfflinePlayer.uniqueId())) {
            var message = this.config.message(
                    "player-modification-to-offline-players-only",
                    Pair.of("player", cloudOfflinePlayer.name())
            );
            commandSender.sendMessage(message);
            return;
        }
        var message = this.config.message(
                "player-reset-playtime",
                Pair.of("player", cloudOfflinePlayer.name())
        );
        commandSender.sendMessage(message);
    }

}
