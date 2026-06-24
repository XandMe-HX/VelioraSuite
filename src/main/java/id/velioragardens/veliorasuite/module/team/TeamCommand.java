package id.velioragardens.veliorasuite.module.team;

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

public final class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamManager teamManager;

    public TeamCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!teamManager.hasUsePermission(sender) && !teamManager.hasAdminPermission(sender)) {
                teamManager.sendNoPermission(sender);
                return true;
            }
            teamManager.sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "create" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (!checkUse(sender)) return true;
                if (args.length < 2) {
                    teamManager.sendUsage(sender);
                    return true;
                }
                teamManager.createTeam(player, args[1]);
                return true;
            }
            case "invite" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (!checkUse(sender)) return true;
                if (args.length < 2) {
                    teamManager.sendUsage(sender);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                teamManager.invite(player, target);
                return true;
            }
            case "accept" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (!checkUse(sender)) return true;
                teamManager.acceptInvite(player);
                return true;
            }
            case "leave" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (!checkUse(sender)) return true;
                boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
                teamManager.leave(player, confirm);
                return true;
            }
            case "list" -> {
                if (!teamManager.hasUsePermission(sender) && !teamManager.hasAdminPermission(sender)) {
                    teamManager.sendNoPermission(sender);
                    return true;
                }
                teamManager.listTeams(sender);
                return true;
            }
            case "chat" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (!checkUse(sender)) return true;
                if (args.length < 2) {
                    teamManager.sendUsage(sender);
                    return true;
                }
                teamManager.teamChat(player, join(args, 1));
                return true;
            }
            case "upgrade" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                if (!checkUse(sender)) return true;
                teamManager.upgrade(player);
                return true;
            }
            case "setowner" -> {
                if (!teamManager.hasSetOwnerPermission(sender)) {
                    teamManager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 3) {
                    teamManager.sendUsage(sender);
                    return true;
                }
                teamManager.setOwner(sender, args[1], Bukkit.getPlayerExact(args[2]));
                return true;
            }
            case "delete" -> {
                if (!teamManager.hasDeletePermission(sender)) {
                    teamManager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 2) {
                    teamManager.sendUsage(sender);
                    return true;
                }
                teamManager.deleteTeam(sender, args[1]);
                return true;
            }
            case "info" -> {
                if (!teamManager.hasAdminPermission(sender)) {
                    teamManager.sendNoPermission(sender);
                    return true;
                }
                if (args.length < 2) {
                    teamManager.sendUsage(sender);
                    return true;
                }
                teamManager.infoTeam(sender, args[1]);
                return true;
            }
            case "reload" -> {
                if (!teamManager.hasReloadPermission(sender)) {
                    teamManager.sendNoPermission(sender);
                    return true;
                }
                teamManager.reload();
                teamManager.sendReloadSuccess(sender);
                return true;
            }
            default -> {
                teamManager.sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (teamManager.hasUsePermission(sender) || teamManager.hasAdminPermission(sender)) {
                options.addAll(Arrays.asList("help", "create", "invite", "accept", "leave", "list", "chat", "upgrade"));
            }
            if (teamManager.hasSetOwnerPermission(sender)) options.add("setowner");
            if (teamManager.hasDeletePermission(sender)) options.add("delete");
            if (teamManager.hasAdminPermission(sender)) options.add("info");
            if (teamManager.hasReloadPermission(sender)) options.add("reload");
            return filter(options, args[0]);
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (first.equals("invite")) {
                return filter(onlinePlayers(), args[1]);
            }
            if (Arrays.asList("setowner", "delete", "info").contains(first)) {
                return filter(teamManager.getDataManager().getTeamNames(), args[1]);
            }
            if (first.equals("leave")) {
                return filter(List.of("confirm"), args[1]);
            }
        }

        if (args.length == 3 && first.equals("setowner")) {
            return filter(onlinePlayers(), args[2]);
        }

        return new ArrayList<>();
    }

    private boolean checkUse(CommandSender sender) {
        if (!teamManager.hasUsePermission(sender)) {
            teamManager.sendNoPermission(sender);
            return false;
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            teamManager.sendPlayerOnly(sender);
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

    private String join(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
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
