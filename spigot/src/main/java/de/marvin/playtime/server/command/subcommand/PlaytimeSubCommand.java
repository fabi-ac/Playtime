package de.marvin.playtime.server.command.subcommand;

import de.marvin.api.core.api.UserAPI;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.server.config.Config;
import eu.cloudnetservice.modules.bridge.player.CloudOfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents a sub-command for the {@link de.marvin.playtime.server.command.PlaytimeCommand}.
 */
public abstract class PlaytimeSubCommand {

    protected final PlaytimeAPI playtimeAPI;
    protected final Config config;
    protected final UserAPI userAPI;

    public PlaytimeSubCommand(
            @NotNull PlaytimeAPI playtimeAPI,
            @NotNull Config config,
            @NotNull UserAPI userAPI
    ) {
        this.playtimeAPI = playtimeAPI;
        this.config = config;
        this.userAPI = userAPI;
    }

    /**
     * Returns the name of the sub-command.
     *
     * @return The name of the sub-command
     */
    public abstract @NotNull String name();

    /**
     * Executes the sub-command with the given sender and arguments.
     *
     * @param commandSender The executing {@link CommandSender} of the sub-command
     * @param args          The arguments passed to the sub-command
     * @return {@code true} if the command was executed successfully, {@code false} if any error occurred
     */
    public abstract boolean onCommand(@NotNull CommandSender commandSender, @NotNull String[] args);

    /**
     * Returns the permissions needed for execution of the sub-command.
     * <p>
     * <b>Note:</b> If {@code null} is returned, the sub-command does not require any special permissions
     * aside from the {@link de.marvin.playtime.server.command.PlaytimeCommand#BASE_PERMISSION}. Otherwise,
     * the returned permission will be checked in the format {@code playtime.moderation.<permission>}.
     *
     * @return The permissions needed for execution of the sub-command
     */
    public @Nullable String permission() {
        return null;
    }

    /**
     * Sends the moderational command usage to the given {@link CommandSender}.
     *
     * @param commandSender {@link CommandSender} to send the usage to
     */
    protected void sendUsage(
            @NotNull CommandSender commandSender
    ) {
        var commandUsage = this.config.message("moderation-command-usage");
        commandSender.sendMessage(commandUsage);
    }

    /**
     * Looks up an {@link CloudOfflinePlayer} by their {@link UUID} or name.
     *
     * @param target   {@link UUID} or name of the player to look up
     * @param consumer {@link Consumer} to handle the result of the lookup
     */
    protected void findOfflinePlayer(
            @NotNull String target,
            @NotNull Consumer<CloudOfflinePlayer> consumer
    ) {
        try {
            var uniqueId = UUID.fromString(target);
            this.userAPI.offlineUserAsync(uniqueId).thenAcceptAsync(consumer);
        } catch (IllegalArgumentException exception) {
            this.userAPI.offlineUserAsync(target).thenAcceptAsync(consumer);
        }
    }

}
