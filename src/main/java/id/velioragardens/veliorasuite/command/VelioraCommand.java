package id.velioragardens.veliorasuite.command;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.Module;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class VelioraCommand implements CommandExecutor {

    private final VelioraSuite plugin;

    public VelioraCommand(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.admin")) {
            sender.sendMessage(ColorUtil.color("&8[&aVelioraSuite&8] &cKamu tidak memiliki izin."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadSuite();
                sender.sendMessage(ColorUtil.color("&8[&aVelioraSuite&8] &aPlugin berhasil direload."));
                return true;
            }

            case "modules" -> {
                sendModules(sender);
                return true;
            }

            case "version" -> {
                sender.sendMessage(ColorUtil.color("&8[&aVelioraSuite&8] &fVersion: &a" + plugin.getDescription().getVersion()));
                return true;
            }

            case "debug" -> {
                boolean debug = plugin.getConfig().getBoolean("settings.debug", false);
                sender.sendMessage(ColorUtil.color("&8[&aVelioraSuite&8] &fDebug: " + (debug ? "&aON" : "&cOFF")));
                return true;
            }

            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&8&m--------------------------------"));
        sender.sendMessage(ColorUtil.color("&a&lVelioraSuite &7- Main Command"));
        sender.sendMessage(ColorUtil.color("&e/vs reload &7- Reload plugin"));
        sender.sendMessage(ColorUtil.color("&e/vs modules &7- Lihat module aktif"));
        sender.sendMessage(ColorUtil.color("&e/vs version &7- Lihat versi plugin"));
        sender.sendMessage(ColorUtil.color("&e/vs debug &7- Lihat status debug"));
        sender.sendMessage(ColorUtil.color("&8&m--------------------------------"));
    }

    private void sendModules(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&8&m--------------------------------"));
        sender.sendMessage(ColorUtil.color("&a&lVelioraSuite Modules"));

        Map<String, Module> modules = plugin.getModuleManager().getModules();

        if (modules.isEmpty()) {
            sender.sendMessage(ColorUtil.color("&7Belum ada module yang diregister."));
        } else {
            for (Module module : modules.values()) {
                String status = module.isEnabled() ? "&aON" : "&cOFF";
                sender.sendMessage(ColorUtil.color("&7- &f" + module.getName() + " &8: " + status));
            }
        }

        sender.sendMessage(ColorUtil.color("&8&m--------------------------------"));
    }
}
