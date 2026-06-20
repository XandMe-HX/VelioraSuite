package id.velioragardens.veliorasuite.module.guide;

import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuideCommand implements CommandExecutor, TabCompleter {

    private final GuideManager guideManager;
    private final GuideType type;

    public GuideCommand(GuideManager guideManager, GuideType type) {
        this.guideManager = guideManager;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("veliorasuite.guide.admin")) {
                sender.sendMessage(ColorUtil.color(guideManager.message("general.no-permission", Map.of())));
                return true;
            }

            guideManager.reload();
            sender.sendMessage(ColorUtil.color(guideManager.message("general.reloaded", Map.of())));
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException exception) {
                sender.sendMessage(ColorUtil.color(guideManager.message("general.invalid-page", Map.of("page", args[0]))));
                return true;
            }
        }

        guideManager.send(sender, type, page);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return new ArrayList<>();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>(guideManager.tabPages(type, input));
        if (sender.hasPermission("veliorasuite.guide.admin") && "reload".startsWith(input)) {
            result.add("reload");
        }
        return result;
    }
}
