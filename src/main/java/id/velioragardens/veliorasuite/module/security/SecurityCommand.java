package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SecurityCommand implements CommandExecutor, TabCompleter {

    private final ConfigFile configFile;
    private final SecurityListener listener;

    public SecurityCommand(ConfigFile configFile, SecurityListener listener) {
        this.configFile = configFile;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.security.admin")) {
            sender.sendMessage(ColorUtil.color(configFile.get().getString("messages.no-permission")));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
            sender.sendMessage(ColorUtil.color("&aVelioraSecurity Status"));
            sender.sendMessage(ColorUtil.color("&7Blocked attempts: &f" + listener.getBlockedCount()));
            sender.sendMessage(ColorUtil.color("&7Command protect: &f" + configFile.get().getBoolean("protection.block-commands.enabled", true)));
            sender.sendMessage(ColorUtil.color("&7Tab complete: &f" + configFile.get().getBoolean("tab-complete.enabled", true)));
            sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            configFile.reload();
            sender.sendMessage(ColorUtil.color(configFile.get().getString("messages.reload")));
            return true;
        }
        sender.sendMessage(ColorUtil.color("&cGunakan: /vsecurity status atau /vsecurity reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("status", "reload"), args[0]);
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.startsWith(lower)) result.add(option);
        return result;
    }
}
