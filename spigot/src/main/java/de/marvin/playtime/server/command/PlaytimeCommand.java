package de.marvin.playtime.server.command;

import de.marvin.api.core.api.UserAPI;
import de.marvin.api.dependencies.lang3.tuple.Pair;
import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.core.util.TimeConverter;
import de.marvin.playtime.server.command.subcommand.GetSubCommand;
import de.marvin.playtime.server.command.subcommand.ResetSubCommand;
import de.marvin.playtime.server.command.subcommand.PlaytimeSubCommand;
import de.marvin.playtime.server.command.subcommand.UpdateSubCommand;
import de.marvin.playtime.server.config.Config;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Handles the main command for the playtime management system.
 */
public class PlaytimeCommand implements CommandExecutor, TabCompleter {

    /**
     * Base permission needed to access moderational features.
     */
    public static final String BASE_PERMISSION = "playtime.moderation";

    private final PlaytimeAPI playtimeAPI;
    private final Config config;

    /**
     * {@link Map} of all names to {@link PlaytimeSubCommand SubCommands}.
     */
    private final Map<String, PlaytimeSubCommand> subCommands = new HashMap<>();

    public PlaytimeCommand(
            PlaytimeAPI playtimeAPI,
            JavaPlugin plugin,
            Config config,
            UserAPI userAPI
    ) {
        this.playtimeAPI = playtimeAPI;
        this.config = config;

        // Register sub-commands
        this.registerSubCommand(new GetSubCommand(playtimeAPI, plugin, config, userAPI));
        this.registerSubCommand(new ResetSubCommand(playtimeAPI, config, userAPI));
        this.registerSubCommand(new UpdateSubCommand(playtimeAPI, config, userAPI));
    }

    /**
     * Handles the execution of the command.
     *
     * @param commandSender {@link CommandSender} of the command
     * @param command       {@link Command} object representing the command
     * @param label         Label of the command
     * @param args          Arguments of the command
     * @return {@code true} if the command was executed successfully, {@code false} otherwise
     */
    @Override
    public boolean onCommand(
            CommandSender commandSender,
            Command command,
            String label,
            String[] args
    ) {
        if (commandSender instanceof Player player && !player.hasPermission(BASE_PERMISSION)) {
            this.sendOwnPlaytime(player);
            return true;
        }

        if (args.length == 0) {
            if (!(commandSender instanceof Player player)) {
                this.sendUsage(commandSender);
                return false;
            }
            this.sendOwnPlaytime(player);
            return true;
        }

        var subCommand = this.subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            this.sendUsage(commandSender);
            return false;
        }

        if (!this.hasPermission(commandSender, subCommand)) {
            commandSender.sendMessage(this.config.message("no-permission"));
            return false;
        }

        return subCommand.onCommand(
                commandSender,
                Arrays.copyOfRange(args, 1, args.length)
        );
    }

    /**
     * Handles the tab completion of the command.
     *
     * @param commandSender {@link CommandSender} of the command
     * @param command       {@link Command} object representing the command
     * @param label         Label of the command
     * @param args          Arguments of the command
     * @return {@link List} of possible tab completions
     */
    @Override
    public List<String> onTabComplete(
            CommandSender commandSender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 1) return this.subCommands.keySet().stream().toList();
        return List.of();
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
        var message = this.config.message(
                "own-current-playtime",
                Pair.of("playtime", playtime),
                Pair.of("onlinetime", onlinetime)
        );
        player.sendMessage(message);
    }

    /**
     * Sends the moderational command usage to the given {@link CommandSender}.
     *
     * @param commandSender {@link CommandSender} to send the usage to
     */
    private void sendUsage(
            @NotNull CommandSender commandSender
    ) {
        var commandUsage = this.config.message("moderation-command-usage");
        commandSender.sendMessage(commandUsage);
    }

    /**
     * Checks if the given {@link CommandSender} has the necessary permission to execute the
     * {@link PlaytimeSubCommand}.
     *
     * @param commandSender {@link CommandSender} to check the permission for
     * @param subCommand    {@link PlaytimeSubCommand} to check the permission for
     * @return {@code true} if the {@link CommandSender} has the necessary permission, {@code false} otherwise
     */
    private boolean hasPermission(
            @NotNull CommandSender commandSender,
            @NotNull PlaytimeSubCommand subCommand
    ) {
        if (subCommand.permission() == null) return true;
        var permission = this.buildPermission(subCommand);
        return commandSender.hasPermission(permission);
    }

    /**
     * Builds the permission string for a {@link PlaytimeSubCommand} based on the
     * {@link PlaytimeCommand#BASE_PERMISSION}.
     *
     * @param subCommand {@link PlaytimeSubCommand} for which to build the permission string
     * @return The full permission string for the {@link PlaytimeSubCommand}
     */
    private String buildPermission(
            @NotNull PlaytimeSubCommand subCommand
    ) {
        return BASE_PERMISSION + "." + subCommand.permission();
    }

    /**
     * Registers a {@link PlaytimeSubCommand}.
     *
     * @param subCommand {@link PlaytimeSubCommand} to register
     */
    private void registerSubCommand(
            @NotNull PlaytimeSubCommand subCommand
    ) {
        this.subCommands.put(subCommand.name(), subCommand);
    }

}
