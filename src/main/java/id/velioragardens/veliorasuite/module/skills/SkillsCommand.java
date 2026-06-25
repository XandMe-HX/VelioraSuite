package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SkillsCommand implements CommandExecutor, TabCompleter {

    private final SkillsManager manager;

    public SkillsCommand(SkillsManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
            manager.sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                manager.sendStatus(sender);
                return true;
            }
            case "reload" -> {
                if (!manager.getConfigManager().hasReload(sender)) { manager.sendNoPermission(sender); return true; }
                manager.reload();
                manager.sendReloadSuccess(sender);
                return true;
            }
            case "mana" -> {
                handleMana(sender, args);
                return true;
            }
            default -> {
                manager.sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (manager.getConfigManager().hasUse(sender)) options.addAll(Arrays.asList("help", "status", "mana"));
            if (manager.getConfigManager().hasReload(sender)) options.add("reload");
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mana")) {
            List<String> options = new ArrayList<>();
            if (manager.getConfigManager().hasManaAdmin(sender)) options.addAll(Arrays.asList("set", "add", "remove", "reset"));
            return filter(options, args[1]);
        }
        return new ArrayList<>();
    }

    private void handleMana(CommandSender sender, String[] args) {
        if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return; }

        if (args.length == 1) {
            if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return; }
            manager.sendManaSelf(player);
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("set") || sub.equals("add") || sub.equals("remove") || sub.equals("reset")) {
            handleManaAdmin(sender, sub, args);
            return;
        }

        PlayerManaData target = manager.findManaTarget(args[1]);
        if (target == null) { manager.sendTargetNotFound(sender, args[1]); return; }
        manager.sendManaOther(sender, target);
    }

    private void handleManaAdmin(CommandSender sender, String sub, String[] args) {
        if (!manager.getConfigManager().hasManaAdmin(sender)) { manager.sendNoPermission(sender); return; }
        if (args.length < 3) { manager.sendHelp(sender); return; }
        PlayerManaData target = manager.findManaTarget(args[2]);
        if (target == null) { manager.sendTargetNotFound(sender, args[2]); return; }

        if (sub.equals("reset")) {
            manager.getManaManager().resetMana(target);
            manager.sendManaReset(sender, target);
            return;
        }

        if (args.length < 4) { manager.sendHelp(sender); return; }
        Integer amount = parse(args[3]);
        if (amount == null) { manager.sendInvalidNumber(sender); return; }

        switch (sub) {
            case "set" -> { manager.getManaManager().setMana(target, amount); manager.sendManaSet(sender, target); }
            case "add" -> { manager.getManaManager().addMana(target, amount); manager.sendManaAdd(sender, target, amount); }
            case "remove" -> { manager.getManaManager().removeMana(target, amount); manager.sendManaRemove(sender, target, amount); }
            default -> manager.sendHelp(sender);
        }
    }

    private Integer parse(String input) {
        try { return Integer.parseInt(input); } catch (NumberFormatException exception) { return null; }
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) result.add(option);
        return result;
    }
}
