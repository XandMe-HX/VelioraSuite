package id.velioragardens.veliorasuite.module.guide;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class GuideCommand implements CommandExecutor, TabCompleter {

    private final GuideManager guideManager;
    private final String sectionName;

    public GuideCommand(GuideManager guideManager, String sectionName) {
        this.guideManager = guideManager;
        this.sectionName = sectionName;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.guide.use")) {
            guideManager.sendNoPermission(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("veliorasuite.guide.admin")) {
                guideManager.sendNoPermission(sender);
                return true;
            }

            guideManager.reload();
            guideManager.sendReloadSuccess(sender);
            return true;
        }

        int page = 1;

        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                guideManager.sendInvalidPage(sender);
                return true;
            }
        }

        if (page < 1) {
            guideManager.sendInvalidPage(sender);
            return true;
        }

        guideManager.sendPage(sender, sectionName, page, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("veliorasuite.guide.use")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("1", "2", "3"));

            if (sender.hasPermission("veliorasuite.guide.admin")) {
                options.add("reload");
            }

            return filter(options, args[0]);
        }

        return new ArrayList<>();
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
