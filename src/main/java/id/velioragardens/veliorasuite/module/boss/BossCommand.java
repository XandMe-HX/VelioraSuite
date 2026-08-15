package id.velioragardens.veliorasuite.module.boss;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BossCommand implements CommandExecutor, TabCompleter {

    private final BossManager manager;

    public BossCommand(BossManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission("veliorasuite.boss.use") && !sender.isOp()) { noPerm(sender); return true; }
            manager.sendStatus(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Only player."); return true; }
                if (!has(sender, "veliorasuite.boss.set")) { noPerm(sender); return true; }
                if (args.length < 2) { sender.sendMessage("/boss set <nama>"); return true; }
                manager.setSpawnPoint(player, args[1].toLowerCase(Locale.ROOT));
                return true;
            }
            case "spawn" -> {
                if (!has(sender, "veliorasuite.boss.spawn")) { noPerm(sender); return true; }
                if (args.length < 2) {
                    manager.spawnRandom(sender);
                    return true;
                }
                manager.spawnByName(joinSpace(args, 1), sender);
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("veliorasuite.boss.use") && !sender.isOp()) { noPerm(sender); return true; }
                manager.sendBossList(sender);
                return true;
            }
            case "info" -> {
                if (!sender.hasPermission("veliorasuite.boss.use") && !sender.isOp()) { noPerm(sender); return true; }
                if (args.length < 2) { sender.sendMessage("/boss info <boss>"); return true; }
                manager.sendBossInfo(sender, joinSpace(args, 1));
                return true;
            }
            case "top" -> {
                if (!sender.hasPermission("veliorasuite.boss.use") && !sender.isOp()) { noPerm(sender); return true; }
                manager.sendTop(sender);
                return true;
            }
            case "stop" -> {
                if (!has(sender, "veliorasuite.boss.stop")) { noPerm(sender); return true; }
                manager.stopActive(true);
                sender.sendMessage(manager.config().color(manager.config().message("stop-success", "%prefix% &aBoss aktif berhasil dihentikan.")));
                return true;
            }
            case "reload" -> {
                if (!has(sender, "veliorasuite.boss.reload")) { noPerm(sender); return true; }
                manager.reload();
                sender.sendMessage(manager.config().color(manager.config().message("reload-success", "%prefix% &aVelioraBoss berhasil direload.")));
                return true;
            }
            default -> {
                manager.sendStatus(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("set", "spawn", "stop", "reload", "list", "info", "top")) if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(option);
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("info"))) {
            String lower = args[1].toLowerCase(Locale.ROOT);
            for (String id : manager.config().bosses().keySet()) if (id.startsWith(lower)) result.add(id);
        }
        return result;
    }

    private boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender.hasPermission("veliorasuite.boss.admin") || sender.isOp();
    }

    private void noPerm(CommandSender sender) {
        sender.sendMessage(manager.config().color(manager.config().prefix() + "&cKamu tidak punya izin."));
    }

    private String joinSpace(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
