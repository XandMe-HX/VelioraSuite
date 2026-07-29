package id.velioragardens.veliorasuite.command;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class VelioraCommand implements CommandExecutor, TabCompleter {

    private final VelioraSuite plugin;

    public VelioraCommand(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.admin")) {
            send(sender, "&cKamu tidak punya izin.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "reload" -> {
                plugin.reloadSuite();
                send(sender, "&aPlugin berhasil direload.");
                return true;
            }

            case "modules" -> {
                sendModules(sender);
                return true;
            }

            case "version" -> {
                send(sender, "&aVelioraSuite &7version &f" + plugin.getDescription().getVersion());
                return true;
            }

            case "debug" -> {
                boolean debug = plugin.getConfig().getBoolean("settings.debug", false);
                send(sender, "&aDebug mode: &f" + debug);
                return true;
            }

            default -> {
                send(sender, "&cCommand tidak dikenal. Gunakan &f/" + label + " help&c.");
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&a&lVelioraSuite &7- Core Command"));
        sender.sendMessage(color("&e/" + label + " help &7- Melihat bantuan command."));
        sender.sendMessage(color("&e/" + label + " reload &7- Reload config utama."));
        sender.sendMessage(color("&e/" + label + " modules &7- Melihat status module."));
        sender.sendMessage(color("&e/" + label + " version &7- Melihat versi plugin."));
        sender.sendMessage(color("&e/" + label + " debug &7- Melihat status debug."));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    private void sendModules(CommandSender sender) {
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&a&lVelioraSuite Modules"));

        if (plugin.getConfigManager().getConfiguredModuleNames().isEmpty()) {
            sender.sendMessage(color("&7Belum ada section modules di modules.yml."));
            sender.sendMessage(color("&8&m--------------------------------"));
            return;
        }

        for (String moduleName : plugin.getConfigManager().getConfiguredModuleNames()) {
            boolean enabled = plugin.getConfigManager().isModuleEnabled(moduleName);
            String status = enabled ? "&aON" : "&cOFF";

            sender.sendMessage(color("&7- &f" + moduleName + " &8: " + status));
        }

        sender.sendMessage(color("&8&m--------------------------------"));
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(color("&8【&aVelioraSuite&8】 &r" + message));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("veliorasuite.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return filter(Arrays.asList("help", "reload", "modules", "version", "debug"), args[0]);
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
