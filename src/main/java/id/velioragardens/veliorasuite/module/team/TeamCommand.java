package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.util.ColorUtil;
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
    private final Runnable reloadAction;

    public TeamCommand(TeamManager teamManager, Runnable reloadAction) {
        this.teamManager = teamManager;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa digunakan oleh player.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.color("&8[&aVelioraTeam&8] &cGunakan: &f/vteam create <nama>"));
                    return true;
                }
                teamManager.createTeam(player, args[1]);
                return true;
            }
            case "upgrade" -> {
                String teamName = null;
                if (args.length >= 2) {
                    teamName = args[1].equalsIgnoreCase("team") && args.length >= 3 ? args[2] : args[1];
                }
                teamManager.upgradeTeam(player, teamName);
                return true;
            }
            case "addadmin" -> {
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.color("&8[&aVelioraTeam&8] &cGunakan: &f/vteam addadmin <nama>"));
                    return true;
                }
                teamManager.addAdmin(player, args[1]);
                return true;
            }
            case "addmember" -> {
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.color("&8[&aVelioraTeam&8] &cGunakan: &f/vteam addmember <nama>"));
                    return true;
                }
                teamManager.addMember(player, args[1]);
                return true;
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.color("&8[&aVelioraTeam&8] &cGunakan: &f/vteam kick <nama>"));
                    return true;
                }
                teamManager.kickMember(player, args[1]);
                return true;
            }
            case "leave" -> {
                teamManager.requestLeave(player);
                return true;
            }
            case "msg" -> {
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.color("&8[&aVelioraTeam&8] &cGunakan: &f/vteam msg <pesan>"));
                    return true;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                teamManager.sendTeamMessage(player, message);
                return true;
            }
            case "info" -> {
                teamManager.sendInfo(player);
                return true;
            }
            case "list" -> {
                player.sendMessage(ColorUtil.color("&8&m------------------------------"));
                player.sendMessage(ColorUtil.color("&aVelioraTeam List"));
                if (teamManager.getTeams().isEmpty()) {
                    player.sendMessage(ColorUtil.color("&7Belum ada team yang dibuat."));
                } else {
                    for (Team team : teamManager.getTeams().values()) {
                        player.sendMessage(ColorUtil.color("&7- &8【&a" + team.getName() + "&8】 &f" + team.getTotalMembers() + "&7/&f" + team.getMaxMembers()));
                    }
                }
                player.sendMessage(ColorUtil.color("&8&m------------------------------"));
                return true;
            }
            case "reload" -> {
                if (!player.hasPermission("veliorasuite.team.admin")) {
                    player.sendMessage(ColorUtil.color("&8[&aVelioraTeam&8] &cKamu tidak memiliki izin."));
                    return true;
                }
                reloadAction.run();
                player.sendMessage(ColorUtil.color("&8[&aVelioraTeam&8] &aConfig team berhasil direload."));
                return true;
            }
            default -> {
                sendHelp(player);
                return true;
            }
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
        player.sendMessage(ColorUtil.color("&aVelioraTeam Commands"));
        player.sendMessage(ColorUtil.color("&e/vteam create <nama> &7- Buat team"));
        player.sendMessage(ColorUtil.color("&e/vteam upgrade <namaTeam> &7- Upgrade slot"));
        player.sendMessage(ColorUtil.color("&e/vteam addadmin <nama> &7- Tambah admin"));
        player.sendMessage(ColorUtil.color("&e/vteam addmember <nama> &7- Tambah member"));
        player.sendMessage(ColorUtil.color("&e/vteam kick <nama> &7- Kick member"));
        player.sendMessage(ColorUtil.color("&e/vteam msg <pesan> &7- Chat khusus team"));
        player.sendMessage(ColorUtil.color("&e/vteam leave &7- Keluar team"));
        player.sendMessage(ColorUtil.color("&e/vteam info &7- Info team"));
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "upgrade", "addadmin", "addmember", "kick", "msg", "leave", "info", "list", "reload"), args[0]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(option);
        }
        return result;
    }
}
