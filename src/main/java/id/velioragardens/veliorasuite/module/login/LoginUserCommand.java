package id.velioragardens.veliorasuite.module.login;

import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LoginUserCommand implements CommandExecutor {
    enum Mode { REGISTER, LOGIN, LOGOUT, CHANGEPASS }
    private final LoginManager loginManager;
    private final Mode mode;
    public LoginUserCommand(LoginManager loginManager, Mode mode) { this.loginManager = loginManager; this.mode = mode; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk player."); return true; }
        switch (mode) {
            case REGISTER -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.color(loginManager.msg("usage-register"))); return true; }
                loginManager.register(player, args[0], args[1]);
            }
            case LOGIN -> {
                if (args.length < 1) { player.sendMessage(ColorUtil.color(loginManager.msg("usage-login"))); return true; }
                loginManager.login(player, args[0]);
            }
            case LOGOUT -> loginManager.logout(player);
            case CHANGEPASS -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.color(loginManager.msg("usage-changepass"))); return true; }
                loginManager.changePassword(player, args[0], args[1]);
            }
        }
        return true;
    }
}
