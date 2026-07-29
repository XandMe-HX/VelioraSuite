package id.velioragardens.veliorasuite.module.playtime;

import id.velioragardens.veliorasuite.module.playtime.PlaytimeManager.PlaytimeEntry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private final PlaytimeManager manager;
    private final PlaytimeConfigManager config;

    public PlaytimeCommand(PlaytimeManager manager, PlaytimeConfigManager config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("me")) {
            sendMe(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "top" -> sendTop(sender);
            case "reload" -> reload(sender);
            case "help" -> sendHelp(sender, label);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendMe(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(config.message("player-only", "%prefix% &cCommand ini hanya bisa digunakan player.")));
            return;
        }
        if (!player.hasPermission(config.usePermission())) {
            sender.sendMessage(color(config.message("no-permission", "%prefix% &cKamu tidak punya izin.")));
            return;
        }
        long current = manager.currentSessionMillis(player.getUniqueId());
        long last = manager.lastSessionMillis(player.getUniqueId());
        long best = manager.bestSessionMillis(player.getUniqueId());
        for (String line : config.messageList("me", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraPlaytime",
                "&7Player: &f%player%",
                "&7Sesi sekarang: &a%current%",
                "&7Sesi terakhir: &f%last%",
                "&7Rekor sesi: &e%best%",
                "&8&m--------------------------------"
        ))) {
            sender.sendMessage(color(apply(line, Map.of(
                    "%player%", player.getName(),
                    "%current%", manager.format(current),
                    "%last%", manager.format(last),
                    "%best%", manager.format(best)
            ))));
        }
    }

    private void sendTop(CommandSender sender) {
        if (!sender.hasPermission(config.topPermission())) {
            sender.sendMessage(color(config.message("no-permission", "%prefix% &cKamu tidak punya izin.")));
            return;
        }
        sender.sendMessage(color(config.message("top-header", "&8&m--------------------------------")));
        sender.sendMessage(color(config.message("top-title", "&b&lTop Playtime Session")));
        List<PlaytimeEntry> top = manager.top(config.topSize());
        if (top.isEmpty()) {
            sender.sendMessage(color(config.message("top-empty", "%prefix% &7Belum ada data playtime.")));
        } else {
            String format = config.message("top-line", "&7%rank%. &f%player% &8- &a%time%");
            for (int i = 0; i < top.size(); i++) {
                PlaytimeEntry entry = top.get(i);
                sender.sendMessage(color(apply(format, Map.of(
                        "%rank%", String.valueOf(i + 1),
                        "%player%", entry.name(),
                        "%time%", manager.format(entry.millis())
                ))));
            }
        }
        sender.sendMessage(color(config.message("top-footer", "&8&m--------------------------------")));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(config.reloadPermission())) {
            sender.sendMessage(color(config.message("no-permission", "%prefix% &cKamu tidak punya izin.")));
            return;
        }
        manager.reload();
        sender.sendMessage(color(config.message("reload-success", "%prefix% &aVelioraPlaytime berhasil direload.")));
    }

    private void sendHelp(CommandSender sender, String label) {
        for (String line : config.messageList("help", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraPlaytime",
                "&f/" + label + " me &7- Lihat sesi online kamu.",
                "&f/" + label + " top &7- Leaderboard sesi online terlama.",
                "&f/" + label + " reload &7- Reload config playtime.",
                "&7Placeholder: &f%playtime% &7atau &f%veliorasuite_playtime%",
                "&8&m--------------------------------"
        ))) sender.sendMessage(color(line));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(Arrays.asList("me", "top", "help"));
        if (sender.hasPermission(config.reloadPermission())) options.add("reload");
        String input = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.startsWith(input)).toList();
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }

    private String color(String text) { return config.color(text); }
}
