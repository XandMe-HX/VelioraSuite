package id.velioragardens.veliorasuite.module.actionhouse;
import org.bukkit.command.Command; import org.bukkit.command.CommandExecutor; import org.bukkit.command.CommandSender; import org.bukkit.entity.Player;
final class ActionHouseCommand implements CommandExecutor { private final ActionHouseGui gui; ActionHouseCommand(ActionHouseGui gui){this.gui=gui;} public boolean onCommand(CommandSender sender, Command command, String label, String[] args){if(!(sender instanceof Player player)){sender.sendMessage("Command ini hanya untuk pemain.");return true;}gui.openStore(player,0);return true;} }
