package id.velioragardens.veliorasuite.command;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public final class DisabledCommand implements CommandExecutor, TabCompleter {

    private final VelioraSuite plugin;
    private final String moduleName;

    public DisabledCommand(VelioraSuite plugin, String moduleName) {
        this.plugin = plugin;
        this.moduleName = moduleName;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getMessageManager().sendRaw(sender, "%prefix% &cModule &f" + moduleName + " &csedang dimatikan.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
