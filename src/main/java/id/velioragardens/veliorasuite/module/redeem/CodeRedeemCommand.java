package id.velioragardens.veliorasuite.module.redeem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Locale;

public final class CodeRedeemCommand implements CommandExecutor, TabCompleter {
    private final CodeRedeemManager manager;
    private final CodeRedeemModule module;
    public CodeRedeemCommand(CodeRedeemManager manager, CodeRedeemModule module) { this.manager = manager; this.module = module; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean adminCommand = command.getName().equalsIgnoreCase("cdmanager") || label.equalsIgnoreCase("cd");
        if (!adminCommand) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§c/redeem hanya dapat dipakai pemain."); return true; }
            if (!player.hasPermission("veliorasuite.redeem.use")) { player.sendMessage("§cKamu tidak punya izin memakai kode redeem."); return true; }
            if (args.length != 1) { player.sendMessage("§eGunakan: §f/redeem <kode>"); return true; }
            player.sendMessage(manager.redeem(player, args[0]));
            return true;
        }
        if (!sender.hasPermission("veliorasuite.redeem.admin")) { sender.sendMessage("§cKhusus admin."); return true; }
        if (args.length == 0) {
            if (sender instanceof Player player) module.openManager(player);
            else sender.sendMessage("§eGunakan: /cd set <kode>, /cd money set <nominal> <kode>, /cd item set <kode>.");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("set") && args.length >= 2 && sender instanceof Player player) { sender.sendMessage(manager.createFromDraft(player, args[1])); return true; }
        if (sub.equals("money") && args.length >= 4 && args[1].equalsIgnoreCase("set")) {
            try { sender.sendMessage(manager.createMoney(args[3], Double.parseDouble(args[2]), sender.getName())); }
            catch (NumberFormatException ex) { sender.sendMessage("§cNominal uang harus angka positif."); }
            return true;
        }
        if (sub.equals("item") && args.length >= 3 && args[1].equalsIgnoreCase("set") && sender instanceof Player player) { sender.sendMessage(manager.createItem(args[2], player.getInventory().getItemInMainHand(), sender.getName())); return true; }
        if (sub.equals("command") && args.length >= 4 && args[1].equalsIgnoreCase("set")) { sender.sendMessage(manager.createCommand(args[2], String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)), sender.getName())); return true; }
        if (sub.equals("delete") && args.length >= 2) { sender.sendMessage(manager.delete(args[1])); return true; }
        if (sub.equals("list")) { sender.sendMessage("§6Kode aktif (§f" + manager.codes().size() + "§6): §f" + String.join(", ", manager.codes())); return true; }
        sender.sendMessage("§e/cdmanager §7(GUI) §8| §e/cd set <kode> §8| §e/cd money set <nominal> <kode> §8| §e/cd item set <kode> §8| §e/cd command set <kode> <command> §8| §e/cd delete <kode>");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("veliorasuite.redeem.admin")) return List.of();
        if (args.length == 1 && !command.getName().equalsIgnoreCase("redeem")) return List.of("set", "money", "item", "command", "delete", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) return List.copyOf(manager.codes());
        if (args.length == 2 && (args[0].equalsIgnoreCase("money") || args[0].equalsIgnoreCase("item") || args[0].equalsIgnoreCase("command"))) return List.of("set");
        return List.of();
    }
}
