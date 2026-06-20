package id.velioragardens.veliorasuite.module.team;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ConfirmCommand implements CommandExecutor {

    private final TeamManager teamManager;
    private final boolean confirm;

    public ConfirmCommand(TeamManager teamManager, boolean confirm) {
        this.teamManager = teamManager;
        this.confirm = confirm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa digunakan oleh player.");
            return true;
        }

        if (confirm) {
            teamManager.confirmLeave(player);
        } else {
            teamManager.cancelLeave(player);
        }
        return true;
    }
}
