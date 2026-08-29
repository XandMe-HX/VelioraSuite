package id.velioragardens.veliorasuite.module.team;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Command staff terpisah agar /team tetap aman dan mudah dipahami pemain. */
public final class TeamAdminCommand implements CommandExecutor, TabCompleter {
    private final TeamManager manager;

    public TeamAdminCommand(TeamManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!manager.hasAdminPermission(sender)) {
            manager.sendNoPermission(sender);
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> manager.sendAdminHelp(sender);
            case "reload" -> { manager.reload(); manager.sendReloadSuccess(sender); }
            case "chatspy" -> {
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                if (args.length >= 2 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) manager.setChatSpy(player, args[1].equalsIgnoreCase("on"));
                else manager.toggleChatSpy(player);
            }
            case "create" -> { if (args.length < 2) manager.sendAdminUsage(sender); else manager.adminCreate(sender, args[1]); }
            case "name" -> { if (args.length < 3) manager.sendAdminUsage(sender); else manager.adminRename(sender, args[1], args[2]); }
            case "description", "tag", "color" -> {
                if (args.length < 3) { manager.sendAdminUsage(sender); return true; }
                manager.adminSetText(sender, args[1], sub, join(args, 2));
            }
            case "invite" -> {
                if (args.length < 3) { manager.sendAdminUsage(sender); return true; }
                manager.adminInvite(sender, args[1], Bukkit.getPlayerExact(args[2]));
            }
            case "join" -> {
                if (args.length < 3) { manager.sendAdminUsage(sender); return true; }
                manager.forceJoin(sender, args[1], Bukkit.getPlayerExact(args[2]));
            }
            case "leave" -> {
                if (args.length < 2) { manager.sendAdminUsage(sender); return true; }
                manager.forceLeave(sender, Bukkit.getPlayerExact(args[1]));
            }
            case "promote" -> {
                if (args.length < 2) { manager.sendAdminUsage(sender); return true; }
                manager.changeRole(sender, Bukkit.getPlayerExact(args[1]), true);
            }
            case "demote" -> {
                if (args.length < 2) { manager.sendAdminUsage(sender); return true; }
                manager.changeRole(sender, Bukkit.getPlayerExact(args[1]), false);
            }
            case "setowner" -> {
                if (args.length < 2) { manager.sendAdminUsage(sender); return true; }
                manager.setOwnerForMember(sender, Bukkit.getPlayerExact(args[1]));
            }
            case "disband" -> {
                if (args.length < 2) { manager.sendAdminUsage(sender); return true; }
                manager.deleteTeam(sender, args[1]);
            }
            case "purge" -> manager.adminPurgeScores(sender);
            case "setrank" -> {
                if (args.length < 3) { manager.sendAdminUsage(sender); return true; }
                try { manager.adminSetRank(sender, args[1], Integer.parseInt(args[2])); } catch (NumberFormatException exception) { manager.sendAdminUsage(sender); }
            }
            case "score", "money" -> {
                if (args.length < 5) { manager.sendAdminUsage(sender); return true; }
                String operation = args[1].toLowerCase(Locale.ROOT);
                if (!List.of("set", "add", "remove").contains(operation) || !List.of("player", "team").contains(args[2].toLowerCase(Locale.ROOT))) { manager.sendAdminUsage(sender); return true; }
                try { manager.adminChangeNumber(sender, operation, args[2], args[3], Double.parseDouble(args[4]), sub.equals("money")); } catch (NumberFormatException exception) { manager.sendAdminUsage(sender); }
            }
            default -> manager.sendAdminHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!manager.hasAdminPermission(sender)) return List.of();
        if (args.length == 1) return filter(List.of("help", "reload", "chatspy", "create", "name", "description", "tag", "color", "invite", "join", "leave", "promote", "demote", "setowner", "purge", "score", "money", "disband", "setrank"), args[0]);
        if (args.length == 2 && List.of("invite", "join", "disband", "name", "description", "tag", "color", "setrank").contains(args[0].toLowerCase(Locale.ROOT))) return filter(manager.getDataManager().getTeamNames(), args[1]);
        if (args.length == 2 && List.of("score", "money").contains(args[0].toLowerCase(Locale.ROOT))) return filter(List.of("set", "add", "remove"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("chatspy")) return filter(List.of("on", "off"), args[1]);
        if (args.length == 3 && List.of("score", "money").contains(args[0].toLowerCase(Locale.ROOT))) return filter(List.of("team", "player"), args[2]);
        if (args.length == 4 && List.of("score", "money").contains(args[0].toLowerCase(Locale.ROOT)) && args[2].equalsIgnoreCase("team")) return filter(manager.getDataManager().getTeamNames(), args[3]);
        if ((args.length == 2 && List.of("leave", "promote", "demote", "setowner").contains(args[0].toLowerCase(Locale.ROOT))) ||
                (args.length == 3 && List.of("invite", "join").contains(args[0].toLowerCase(Locale.ROOT)))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String needle = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }

    private String join(String[] args, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }
}
