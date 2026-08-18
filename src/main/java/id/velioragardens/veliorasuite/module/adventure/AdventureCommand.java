package id.velioragardens.veliorasuite.module.adventure;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AdventureCommand implements CommandExecutor, TabCompleter {
    private final AdventureManager manager;
    private final boolean teamMenu;

    public AdventureCommand(AdventureManager manager, boolean teamMenu) { this.manager = manager; this.teamMenu = teamMenu; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (teamMenu) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk pemain."); return true; }
            manager.openTeam(player); return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("veliorasuite.adventure.admin")) { sender.sendMessage("Tidak punya izin."); return true; }
            manager.reload(); sender.sendMessage(manager.config().prefix() + manager.config().color("&aConfig dan data dimuat ulang.")); return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("setrank")) {
            if (!sender.hasPermission("veliorasuite.adventure.admin")) { sender.sendMessage("Tidak punya izin."); return true; }
            @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            manager.setRank(sender, target, String.join(" ", Arrays.copyOfRange(args, 2, args.length))); return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("accept")) {
            if (!(sender instanceof Player player)) return true;
            manager.accept(player, args[1]); return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("submit") || args[0].equalsIgnoreCase("claim"))) {
            if (!(sender instanceof Player player)) return true;
            manager.claim(player); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk pemain."); return true; }
        manager.openMain(player); return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (teamMenu) return List.of();
        List<String> values = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("veliorasuite.adventure.admin")) values.addAll(List.of("reload", "setrank"));
        if (args.length == 2 && args[0].equalsIgnoreCase("setrank") && sender.hasPermission("veliorasuite.adventure.admin")) Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        if (args.length == 3 && args[0].equalsIgnoreCase("setrank") && sender.hasPermission("veliorasuite.adventure.admin")) {
            for (AdventureRank rank : AdventureRank.values()) values.add(rank.name());
            values.add("RAJA IBLIS");
        }
        String token = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(token)).toList();
    }
}
