package id.velioragardens.veliorasuite.module.fishing;

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

public final class FishingCommand implements CommandExecutor, TabCompleter {
    private final FishingManager manager;

    public FishingCommand(FishingManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya player.");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "top" -> manager.sendTop(player);
            case "stats" -> manager.sendStats(player);
            case "sell" -> manager.openSellGui(player);
            case "sellhand" -> manager.sellHand(player);
            case "reload" -> {
                if (!player.hasPermission("veliorasuite.fishing.admin")) {
                    player.sendMessage(ColorUtil.color("&cKamu tidak punya izin."));
                    return true;
                }
                manager.reload();
                player.sendMessage(manager.msg("reload"));
            }
            default -> help(player);
        }
        return true;
    }

    private void help(Player player) {
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
        player.sendMessage(ColorUtil.color("&aVelioraFishing"));
        player.sendMessage(ColorUtil.color("&e/vf top &7- Ranking pemancing"));
        player.sendMessage(ColorUtil.color("&e/vf stats &7- Statistik kamu"));
        player.sendMessage(ColorUtil.color("&e/vf sell &7- Buka GUI jual ikan"));
        player.sendMessage(ColorUtil.color("&e/vf sellhand &7- Jual ikan di tangan"));
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("top", "stats", "sell", "sellhand", "reload"), args[0]);
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.startsWith(lower)) result.add(option);
        return result;
    }
}
