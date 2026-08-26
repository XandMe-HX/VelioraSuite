package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class QuestCommand implements CommandExecutor, TabCompleter {

    private final QuestManager manager;

    public QuestCommand(QuestManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("skills")) {
            Player player = player(sender);
            if (player != null && manager.getConfigManager().hasUse(sender)) manager.openGui(player);
            else if (player != null) manager.sendNoPermission(sender);
            return true;
        }
        if (commandName.equals("stats")) {
            Player player = player(sender);
            if (player != null && manager.getConfigManager().hasUse(sender)) manager.sendStats(player);
            else if (player != null) manager.sendNoPermission(sender);
            return true;
        }
        if (commandName.equals("skilltop")) {
            QuestCategory category = args.length == 0 ? null : QuestCategory.fromKey(args[0]);
            if (args.length > 0 && category == null && !args[0].equalsIgnoreCase("total")) { manager.sendHelp(sender); return true; }
            manager.sendTop(sender, category);
            return true;
        }
        if (commandName.equals("skillrank")) {
            Player player = player(sender);
            if (player == null) return true;
            QuestCategory category = args.length == 0 ? null : QuestCategory.fromKey(args[0]);
            if (args.length > 0 && category == null && !args[0].equalsIgnoreCase("total")) { manager.sendHelp(sender); return true; }
            manager.sendRank(player, category);
            return true;
        }
        QuestCategory directCategory = QuestCategory.fromKey(commandName);
        if (directCategory != null) {
            Player player = player(sender);
            if (player != null) manager.sendSkillProfile(player, directCategory);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            Player player = player(sender);
            if (player == null) return true;
            if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
            manager.openGui(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> { manager.sendHelp(sender); return true; }
            case "progress" -> {
                Player player = player(sender);
                if (player == null) return true;
                if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                manager.sendProgress(player);
                return true;
            }
            case "stats" -> {
                Player player = player(sender);
                if (player == null) return true;
                if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                manager.sendStats(player);
                return true;
            }
            case "skill" -> {
                Player player = player(sender);
                if (player == null) return true;
                if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (args.length < 2) { manager.sendHelp(sender); return true; }
                manager.sendSkillProfile(player, QuestCategory.fromKey(args[1]));
                return true;
            }
            case "top" -> {
                QuestCategory category = args.length < 2 ? null : QuestCategory.fromKey(args[1]);
                if (args.length >= 2 && category == null && !args[1].equalsIgnoreCase("total")) { manager.sendHelp(sender); return true; }
                manager.sendTop(sender, category);
                return true;
            }
            case "rank" -> {
                Player player = player(sender);
                if (player == null) return true;
                QuestCategory category = args.length < 2 ? null : QuestCategory.fromKey(args[1]);
                if (args.length >= 2 && category == null && !args[1].equalsIgnoreCase("total")) { manager.sendHelp(sender); return true; }
                manager.sendRank(player, category);
                return true;
            }
            case "start" -> {
                Player player = player(sender);
                if (player == null) return true;
                if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (args.length < 2) { manager.sendHelp(sender); return true; }
                manager.startQuest(player, QuestCategory.fromKey(args[1]));
                return true;
            }
            case "claim" -> {
                Player player = player(sender);
                if (player == null) return true;
                if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (args.length < 2) { manager.sendHelp(sender); return true; }
                manager.claimQuest(player, QuestCategory.fromKey(args[1]));
                return true;
            }
            case "cancel" -> {
                Player player = player(sender);
                if (player == null) return true;
                if (!manager.getConfigManager().hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (args.length < 2) { manager.sendHelp(sender); return true; }
                manager.cancelQuest(player, QuestCategory.fromKey(args[1]));
                return true;
            }
            case "reload" -> {
                if (!manager.getConfigManager().hasReload(sender)) { manager.sendNoPermission(sender); return true; }
                manager.reload();
                manager.sendReloadSuccess(sender);
                return true;
            }
            case "status" -> {
                if (!manager.getConfigManager().hasAdmin(sender)) { manager.sendNoPermission(sender); return true; }
                manager.sendStatus(sender);
                return true;
            }
            default -> { manager.sendHelp(sender); return true; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (!command.getName().equalsIgnoreCase("quests")) {
                if (command.getName().equalsIgnoreCase("skilltop") || command.getName().equalsIgnoreCase("skillrank")) return filter(categoriesWithTotal(), args[0]);
                return new ArrayList<>();
            }
            List<String> options = new ArrayList<>(Arrays.asList("gui", "progress", "stats", "skill", "top", "rank", "start", "claim", "cancel", "help"));
            if (manager.getConfigManager().hasReload(sender)) options.add("reload");
            if (manager.getConfigManager().hasAdmin(sender)) options.add("status");
            return filter(options, args[0]);
        }
        if (args.length == 2 && List.of("start", "claim", "cancel", "skill").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(categoriesWithTotal(), args[1]);
        }
        if (args.length == 2 && List.of("top", "rank").contains(args[0].toLowerCase(Locale.ROOT))) return filter(categoriesWithTotal(), args[1]);
        return new ArrayList<>();
    }

    private Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            manager.sendPlayerOnly(sender);
            return null;
        }
        return player;
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(option);
        return result;
    }

    private List<String> categoriesWithTotal() {
        List<String> categories = new ArrayList<>();
        categories.add("total");
        for (QuestCategory category : QuestCategory.values()) categories.add(category.key());
        return categories;
    }
}
