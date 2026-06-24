package id.velioragardens.veliorasuite.module.chat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ChatCommand implements CommandExecutor, TabCompleter {

    private final ChatManager chatManager;

    public ChatCommand(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!chatManager.hasUsePermission(sender) && !chatManager.hasAdminPermission(sender)) {
                chatManager.sendNoPermission(sender);
                return true;
            }
            chatManager.sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            if (!chatManager.hasUsePermission(sender) && !chatManager.hasAdminPermission(sender)) {
                chatManager.sendNoPermission(sender);
                return true;
            }
            chatManager.sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!chatManager.hasReloadPermission(sender)) {
                chatManager.sendNoPermission(sender);
                return true;
            }
            chatManager.reload();
            chatManager.sendReloadSuccess(sender);
            return true;
        }

        chatManager.sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return new ArrayList<>();
        }

        List<String> options = new ArrayList<>();
        if (chatManager.hasUsePermission(sender) || chatManager.hasAdminPermission(sender)) {
            options.addAll(Arrays.asList("help", "status"));
        }
        if (chatManager.hasReloadPermission(sender)) {
            options.add("reload");
        }

        return filter(options, args[0]);
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
