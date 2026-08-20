package de.marvin.playtime.server.command.subcommand;

import de.marvin.api.core.api.UserAPI;
import de.marvin.api.dependencies.lang3.tuple.Pair;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.util.TimeConverter;
import de.marvin.playtime.server.config.Config;
import eu.cloudnetservice.modules.bridge.player.CloudOfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * {@link PlaytimeSubCommand} for getting first and last join data as well as playtime and onlinetime of a
 * player.
 */
public class GetSubCommand extends PlaytimeSubCommand {

    private final JavaPlugin plugin;

    public GetSubCommand(
            @NotNull PlaytimeAPI playtimeAPI,
            @NotNull JavaPlugin plugin,
            @NotNull Config config,
            @NotNull UserAPI userAPI
    ) {
        super(playtimeAPI, config, userAPI);
        this.plugin = plugin;
    }

    /**
     * {@inheritDoc}
     *
     * @return The name of the sub-command
     */
    @Override
    public @NotNull String name() {
        return "get";
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

        this.findOfflinePlayer(args[0], cloudOfflinePlayer -> this.sendConnectionInformation(
                commandSender,
                cloudOfflinePlayer
        ));
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @return The permissions needed for execution of the sub-command
     */
    @Override
    public @Nullable String permission() {
        return "get";
    }

    /**
     * Sends the connection information of the given {@link CloudOfflinePlayer} to the given
     * {@link CommandSender}.
     *
     * @param commandSender      {@link CommandSender} to send the information to
     * @param cloudOfflinePlayer {@link CloudOfflinePlayer} to get the information from
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

        this.playtimeAPI.sessionOrDefault(cloudOfflinePlayer.uniqueId()).onSuccess(session -> {
            var snapshot = session.snapshot();
            var playtime = TimeConverter.convertMillisToDaysHoursMinutes(
                    snapshot.playtimeInMillis(),
                    true,
                    true
            );
            var onlinetime = TimeConverter.convertMillisToDaysHoursMinutes(
                    snapshot.onlinetimeInMillis(),
                    true,
                    true
            );

            var firstSeen = TimeConverter.convertMillisToDateTime(cloudOfflinePlayer.firstLoginTimeMillis());
            var lastSeen = TimeConverter.convertMillisToDateTime(cloudOfflinePlayer.lastLoginTimeMillis());

            var message = this.config.message(
                    "player-get-playtime",
                    Pair.of("player", cloudOfflinePlayer.name()),
                    Pair.of("playtime", playtime),
                    Pair.of("onlinetime", onlinetime),
                    Pair.of("first_seen", firstSeen),
                    Pair.of("last_seen", lastSeen)
            );
            this.plugin.getServer().getScheduler().runTask(
                    this.plugin,
                    () -> commandSender.sendMessage(message)
            );
        });
    }

}
