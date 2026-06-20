package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AnnouncementCommand implements CommandExecutor, TabCompleter {
    private final AnnouncementManager manager;
    public AnnouncementCommand(AnnouncementManager manager) { this.manager = manager; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.announcement.admin")) { sender.sendMessage(ColorUtil.color(manager.getConfigFile().get().getString("messages.no-permission"))); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) { sender.sendMessage(ColorUtil.color("&aVelioraAnnouncement aktif. Pesan: &f" + manager.ids().size())); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { manager.reload(); sender.sendMessage(ColorUtil.color(manager.getConfigFile().get().getString("messages.reload"))); }
            case "send" -> { if (args.length < 2) { sender.sendMessage(ColorUtil.color("&cGunakan: /vannounce send <id>")); return true; } boolean ok = manager.send(args[1]); sender.sendMessage(ColorUtil.color(ok ? "&aAnnouncement dikirim." : "&cAnnouncement tidak ditemukan.")); }
            default -> sender.sendMessage(ColorUtil.color("&cGunakan: /vannounce status|reload|send <id>"));
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if (args.length == 1) return filter(Arrays.asList("status", "reload", "send"), args[0]); if (args.length == 2 && args[0].equalsIgnoreCase("send")) return filter(manager.ids(), args[1]); return new ArrayList<>(); }
    private List<String> filter(List<String> options, String input) { List<String> r = new ArrayList<>(); String lower = input.toLowerCase(Locale.ROOT); for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(lower)) r.add(o); return r; }
}
