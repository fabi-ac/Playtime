package de.marvin.playtime.server.command;

import de.marvin.playtime.core.PlaytimeAPI;
import de.marvin.playtime.server.config.Config;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the command for toggling the away from keyboard status of a player.
 */
public class AFKCommand implements CommandExecutor {

    /**
     * The name of the command.
     */
    public static final String NAME = "afk";

    private final PlaytimeAPI playtimeAPI;
    private final Config config;

    public AFKCommand(
            PlaytimeAPI playtimeAPI,
            Config config
    ) {
        this.playtimeAPI = playtimeAPI;
        this.config = config;
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
        if (!(commandSender instanceof Player player)) {
            commandSender.sendMessage(this.config.message("players-only-command"));
            return false;
        }

        this.playtimeAPI.toggleAwayStatus(player.getUniqueId());
        return true;
    }

}
