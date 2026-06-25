package id.velioragardens.veliorasuite.module.loginsecurity;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LoginSecurityCommand implements CommandExecutor, TabCompleter {

    private final LoginSecurityManager manager;

    public LoginSecurityCommand(LoginSecurityManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        switch (name) {
            case "register" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (args.length < 2) {
                    manager.sendUsage(sender, "register-usage");
                    return true;
                }
                manager.register(player, args[0], args[1]);
                return true;
            }
            case "login" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (args.length < 1) {
                    manager.sendUsage(sender, "login-usage");
                    return true;
                }
                manager.login(player, args[0]);
                return true;
            }
            case "changepass" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (args.length < 2) {
                    manager.sendUsage(sender, "changepass-usage");
                    return true;
                }
                manager.changePassword(player, args[0], args[1]);
                return true;
            }
            case "unregister" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (args.length < 1) {
                    manager.sendUsage(sender, "unregister-usage");
                    return true;
                }
                manager.unregister(player, args[0]);
                return true;
            }
            case "logout" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                manager.logout(player);
                return true;
            }
            case "risetpw" -> {
                if (!manager.getConfigManager().hasOwnerPermission(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 1) {
                    manager.sendUsage(sender, "risetpw-usage");
                    return true;
                }
                manager.ownerReset(sender, args[0]);
                return true;
            }
            case "cpowner" -> {
                if (!manager.getConfigManager().hasOwnerPermission(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 2) {
                    manager.sendUsage(sender, "cpowner-usage");
                    return true;
                }
                manager.ownerChangePassword(sender, args[0], args[1]);
                return true;
            }
            case "loginsecurity" -> {
                return handleAdmin(sender, args);
            }
            default -> {
                manager.sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("loginsecurity")) {
            if (args.length == 1) {
                return filter(Arrays.asList("help", "status", "reload"), args[0]);
            }
            return new ArrayList<>();
        }

        if ((name.equals("risetpw") || name.equals("cpowner")) && args.length == 1 && manager.getConfigManager().hasOwnerPermission(sender)) {
            return filter(onlinePlayers(), args[0]);
        }

        return new ArrayList<>();
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!manager.getConfigManager().hasAdminPermission(sender)) {
            manager.sendNoPermission(sender);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            manager.sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            manager.sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            manager.reload();
            manager.sendReloadSuccess(sender);
            return true;
        }

        manager.sendHelp(sender);
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            manager.sendPlayerOnly(sender);
            return null;
        }
        return player;
    }

    private List<String> onlinePlayers() {
        List<String> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(player.getName());
        }
        return players;
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
