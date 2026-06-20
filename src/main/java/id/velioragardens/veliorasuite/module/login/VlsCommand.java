package id.velioragardens.veliorasuite.module.login;

import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class VlsCommand implements CommandExecutor, TabCompleter {
    private final LoginManager loginManager;
    public VlsCommand(LoginManager loginManager) { this.loginManager = loginManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.login.admin")) { sender.sendMessage(ColorUtil.color(loginManager.msg("no-permission"))); return true; }
        if (args.length == 0) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { loginManager.reload(); sender.sendMessage(ColorUtil.color(loginManager.msg("reload"))); }
            case "setpass" -> {
                if (args.length < 3) { sender.sendMessage(ColorUtil.color("&cGunakan: /vls setpass <player> <newPassword>")); return true; }
                loginManager.setPassword(args[1], args[2]);
                sender.sendMessage(ColorUtil.color(loginManager.msg("admin-setpass").replace("%player%", args[1])));
            }
            case "unregister" -> {
                if (args.length < 2) { sender.sendMessage(ColorUtil.color("&cGunakan: /vls unregister <player>")); return true; }
                boolean ok = loginManager.unregister(args[1]);
                sender.sendMessage(ColorUtil.color(ok ? loginManager.msg("admin-unregister").replace("%player%", args[1]) : "&cPlayer belum register."));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
        sender.sendMessage(ColorUtil.color("&aVeliora Login Security"));
        sender.sendMessage(ColorUtil.color("&e/vls reload &7- Reload config"));
        sender.sendMessage(ColorUtil.color("&e/vls setpass <player> <newPassword> &7- Reset password"));
        sender.sendMessage(ColorUtil.color("&e/vls unregister <player> &7- Hapus akun login"));
        sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("reload", "setpass", "unregister"), args[0]);
        return new ArrayList<>();
    }
    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.startsWith(lower)) result.add(option);
        return result;
    }
}
