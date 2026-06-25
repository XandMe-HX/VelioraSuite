package id.velioragardens.veliorasuite.module.clearlag;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ClearLagCommand implements CommandExecutor, TabCompleter {

    private final ClearLagManager manager;

    public ClearLagCommand(ClearLagManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!manager.getConfigManager().hasStatusPermission(sender)) {
                manager.sendNoPermission(sender);
                return true;
            }
            manager.sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (!manager.getConfigManager().hasStatusPermission(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.sendStatus(sender);
                return true;
            }
            case "reload" -> {
                if (!manager.getConfigManager().hasReloadPermission(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.reload();
                manager.sendReloadSuccess(sender);
                return true;
            }
            case "clear" -> {
                if (!manager.getConfigManager().hasClearPermission(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                handleClear(sender, args);
                return true;
            }
            case "tps" -> {
                if (!manager.getConfigManager().hasStatusPermission(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.sendTps(sender);
                return true;
            }
            case "memory" -> {
                if (!manager.getConfigManager().hasStatusPermission(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.sendMemory(sender);
                return true;
            }
            default -> {
                manager.sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (manager.getConfigManager().hasStatusPermission(sender)) options.addAll(Arrays.asList("help", "status", "tps", "memory"));
            if (manager.getConfigManager().hasClearPermission(sender)) options.add("clear");
            if (manager.getConfigManager().hasReloadPermission(sender)) options.add("reload");
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear") && manager.getConfigManager().hasClearPermission(sender)) {
            return filter(Arrays.asList("items", "mobs", "projectiles"), args[1]);
        }
        return new ArrayList<>();
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("items")) {
            manager.clearItems(true);
            return;
        }
        if (args[1].equalsIgnoreCase("mobs")) {
            manager.clearMobs(sender);
            return;
        }
        if (args[1].equalsIgnoreCase("projectiles")) {
            manager.clearProjectiles(sender);
            return;
        }
        manager.sendHelp(sender);
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) result.add(option);
        }
        return result;
    }
}
