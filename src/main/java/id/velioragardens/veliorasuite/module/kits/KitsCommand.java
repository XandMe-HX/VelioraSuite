package id.velioragardens.veliorasuite.module.kits;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class KitsCommand implements CommandExecutor, TabCompleter {

    private final KitsManager kitsManager;

    public KitsCommand(KitsManager kitsManager) {
        this.kitsManager = kitsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!hasUse(sender)) {
                send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                return true;
            }

            if (kitsManager.getConfigManager().isOpenGuiOnMainCommand()) {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.");
                    return true;
                }

                kitsManager.openGui(player);
            } else {
                kitsManager.sendHelp(sender);
            }

            return true;
        }

        if (args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player player)) {
                send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.");
                return true;
            }

            if (!hasUse(sender)) {
                send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                return true;
            }

            kitsManager.openGui(player);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "help" -> {
                if (!hasUse(sender) && !hasAdmin(sender)) {
                    send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                    return true;
                }
                kitsManager.sendHelp(sender);
                return true;
            }

            case "list" -> {
                if (!hasUse(sender) && !hasAdmin(sender)) {
                    send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                    return true;
                }
                kitsManager.sendList(sender);
                return true;
            }

            case "claim" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.");
                    return true;
                }
                if (!hasUse(sender)) {
                    send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "usage-claim", "%prefix% &cGunakan: &f/kits claim <kit>");
                    return true;
                }
                kitsManager.claimKit(player, args[1]);
                return true;
            }

            case "preview" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.");
                    return true;
                }
                if (!hasUse(sender)) {
                    send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "usage-preview", "%prefix% &cGunakan: &f/kits preview <kit>");
                    return true;
                }
                kitsManager.previewKit(player, args[1]);
                return true;
            }

            case "buy" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.");
                    return true;
                }
                if (!hasUse(sender)) {
                    send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "usage-buy", "%prefix% &cGunakan: &f/kits buy <kit>");
                    return true;
                }
                kitsManager.buyKit(player, args[1]);
                return true;
            }

            case "cooldown" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.");
                    return true;
                }
                if (!hasUse(sender)) {
                    send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                    return true;
                }
                kitsManager.sendCooldowns(player);
                return true;
            }

            case "reload" -> {
                if (!hasReload(sender)) {
                    send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.");
                    return true;
                }
                kitsManager.reload();
                send(sender, "reload-success", "%prefix% &aVelioraKits berhasil direload.");
                return true;
            }

            default -> {
                kitsManager.sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();

            if (hasUse(sender) || hasAdmin(sender)) {
                options.addAll(Arrays.asList("help", "open", "list", "claim", "preview", "buy", "cooldown"));
            }

            if (hasReload(sender)) {
                options.add("reload");
            }

            return filter(options, args[0]);
        }

        if (args.length == 2 && Arrays.asList("claim", "preview", "buy").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(kitsManager.getKitIds(), args[1]);
        }

        return new ArrayList<>();
    }

    private boolean hasUse(CommandSender sender) {
        return sender.hasPermission(kitsManager.getConfigManager().getUsePermission()) || hasAdmin(sender);
    }

    private boolean hasReload(CommandSender sender) {
        return sender.hasPermission(kitsManager.getConfigManager().getReloadPermission()) || hasAdmin(sender);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(kitsManager.getConfigManager().getAdminPermission());
    }

    private void send(CommandSender sender, String path, String fallback) {
        sender.sendMessage(kitsManager.getConfigManager().color(kitsManager.getConfigManager().getMessage(path, fallback)));
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                result.add(option);
            }
        }

        return result;
    }
}
