package id.velioragardens.veliorasuite.module.announcement;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnnouncementCommand implements CommandExecutor, TabCompleter {

    private final AnnouncementManager announcementManager;

    public AnnouncementCommand(AnnouncementManager announcementManager) {
        this.announcementManager = announcementManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!hasAnyAnnouncementPermission(sender)) {
                announcementManager.sendNoPermission(sender);
                return true;
            }

            announcementManager.sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "status" -> {
                if (!hasPermission(sender, announcementManager.getStatusPermission())) {
                    announcementManager.sendNoPermission(sender);
                    return true;
                }

                announcementManager.sendStatus(sender);
                return true;
            }

            case "list" -> {
                if (!hasPermission(sender, announcementManager.getStatusPermission())) {
                    announcementManager.sendNoPermission(sender);
                    return true;
                }

                announcementManager.sendList(sender);
                return true;
            }

            case "reload" -> {
                if (!hasPermission(sender, announcementManager.getReloadPermission())) {
                    announcementManager.sendNoPermission(sender);
                    return true;
                }

                announcementManager.reload();
                announcementManager.sendReloadSuccess(sender);
                return true;
            }

            case "send" -> {
                if (!hasPermission(sender, announcementManager.getSendPermission())) {
                    announcementManager.sendNoPermission(sender);
                    return true;
                }

                if (args.length < 2) {
                    announcementManager.sendUsageSend(sender);
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
                if (!hasAnyAnnouncementPermission(sender)) {
                    announcementManager.sendNoPermission(sender);
                    return true;
                }

                announcementManager.sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();

            if (hasAnyAnnouncementPermission(sender)) {
                options.add("help");
            }

            if (hasPermission(sender, announcementManager.getStatusPermission())) {
                options.add("status");
                options.add("list");
            }

            if (hasPermission(sender, announcementManager.getReloadPermission())) {
                options.add("reload");
            }

            if (hasPermission(sender, announcementManager.getSendPermission())) {
                options.add("send");
            }

            return filter(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("send") && hasPermission(sender, announcementManager.getSendPermission())) {
            return filter(announcementManager.getActiveIds(), args[1]);
        }

        return new ArrayList<>();
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(announcementManager.getAdminPermission()) || sender.hasPermission(permission);
    }

    private boolean hasAnyAnnouncementPermission(CommandSender sender) {
        return sender.hasPermission(announcementManager.getAdminPermission())
                || sender.hasPermission(announcementManager.getStatusPermission())
                || sender.hasPermission(announcementManager.getReloadPermission())
                || sender.hasPermission(announcementManager.getSendPermission());
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
