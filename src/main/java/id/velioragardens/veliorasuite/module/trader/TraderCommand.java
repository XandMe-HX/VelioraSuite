package id.velioragardens.veliorasuite.module.trader;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TraderCommand implements CommandExecutor, TabCompleter {

    private final TraderManager manager;
    private final TraderConfigManager configManager;

    public TraderCommand(TraderManager manager, TraderConfigManager configManager) {
        this.manager = manager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(configManager.getReloadPermission()) && !sender.hasPermission(configManager.getAdminPermission()) && !sender.isOp()) {
                manager.sendNoPermission(sender);
                return true;
            }
            manager.reload();
            manager.sendReloadSuccess(sender);
            return true;
        }
        if (!sender.hasPermission(configManager.getUsePermission()) && !sender.hasPermission(configManager.getAdminPermission()) && !sender.isOp()) {
            manager.sendNoPermission(sender);
            return true;
        }
        manager.sendStatus(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return new ArrayList<>();
        if (!sender.hasPermission(configManager.getReloadPermission()) && !sender.hasPermission(configManager.getAdminPermission()) && !sender.isOp()) return new ArrayList<>();
        String lower = args[0].toLowerCase(Locale.ROOT);
        return "reload".startsWith(lower) ? List.of("reload") : new ArrayList<>();
    }
}
