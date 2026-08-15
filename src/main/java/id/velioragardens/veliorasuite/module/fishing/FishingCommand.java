package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FishingCommand implements CommandExecutor, TabCompleter {

    private final FishingManager manager;

    public FishingCommand(FishingManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
            if (sender instanceof Player player) manager.openMainGui(player);
            else manager.sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "bag" -> {
                if (!hasBag(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openBagGui(player);
                return true;
            }
            case "sell" -> {
                if (!hasSell(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openSellGui(player);
                return true;
            }
            case "rods", "rod", "shop" -> {
                if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openRodShop(player);
                return true;
            }
            case "collection" -> {
                if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openCollectionGui(player);
                return true;
            }
            case "top" -> {
                if (!hasTop(sender)) { manager.sendNoPermission(sender); return true; }
                manager.sendTop(sender);
                return true;
            }
            case "reload" -> {
                if (!hasReload(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.reload();
                manager.sendReloadSuccess(sender);
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
        if (args.length != 1) return new ArrayList<>();
        List<String> options = new ArrayList<>(Arrays.asList("help"));
        if (hasBag(sender)) options.add("bag");
        if (hasSell(sender)) options.add("sell");
        if (hasUse(sender)) { options.add("collection"); options.add("rods"); }
        if (hasTop(sender)) options.add("top");
        if (hasReload(sender)) options.add("reload");
        return filter(options, args[0]);
    }

    private boolean hasUse(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getUsePermission()) || hasAdmin(sender);
    }

    private boolean hasBag(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getBagPermission()) || hasAdmin(sender);
    }

    private boolean hasSell(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getSellPermission()) || hasAdmin(sender);
    }

    private boolean hasTop(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getTopPermission()) || hasAdmin(sender);
    }

    private boolean hasReload(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getReloadPermission()) || hasAdmin(sender);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getAdminPermission()) || sender.isOp();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(option);
        return result;
    }
}
