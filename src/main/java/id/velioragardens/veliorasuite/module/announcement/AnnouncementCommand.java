package id.velioragardens.veliorasuite.module.announcement;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AnnouncementCommand implements CommandExecutor, TabCompleter {

    private final AnnouncementManager announcementManager;

    public AnnouncementCommand(AnnouncementManager announcementManager) {
        this.announcementManager = announcementManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.announcement.admin")) {
            announcementManager.sendNoPermission(sender);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            announcementManager.sendStatus(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "reload" -> {
                announcementManager.reload();
                announcementManager.sendReloadSuccess(sender);
                return true;
            }

            case "send" -> {
                if (args.length < 2) {
                    announcementManager.sendNoAnnouncements(sender);
                    return true;
                }

                String id = args[1].toLowerCase(Locale.ROOT);
                boolean success = announcementManager.sendById(id);

                if (success) {
                    announcementManager.sendManualSuccess(sender, id);
                } else {
                    announcementManager.sendNotFound(sender, id);
                }

                return true;
            }

            default -> {
                announcementManager.sendStatus(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("veliorasuite.announcement.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return filter(Arrays.asList("status", "reload", "send"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return filter(announcementManager.getActiveIds(), args[1]);
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
