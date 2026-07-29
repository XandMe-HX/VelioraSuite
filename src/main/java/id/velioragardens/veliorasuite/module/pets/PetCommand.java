package id.velioragardens.veliorasuite.module.pets;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PetCommand implements CommandExecutor, TabCompleter {
    private static final List<String> FEED_AMOUNTS = List.of("1", "5", "10", "32", "64");

    private final PetManager manager;
    private final PetConfigManager config;

    public PetCommand(PetManager manager, PetConfigManager config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only player.");
            return true;
        }
        if (args.length == 0) {
            if (!has(player, "veliorasuite.pets.use")) { noPerm(player); return true; }
            manager.openMain(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "shop" -> { if (!has(player, "veliorasuite.pets.shop")) { noPerm(player); return true; } manager.openShop(player); }
            case "gacha" -> { if (!has(player, "veliorasuite.pets.gacha")) { noPerm(player); return true; } manager.openGacha(player); }
            case "list" -> { if (!has(player, "veliorasuite.pets.use")) { noPerm(player); return true; } manager.openList(player); }
            case "info" -> { if (!has(player, "veliorasuite.pets.use")) { noPerm(player); return true; } manager.sendInfo(player, args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "active"); }
            case "summon" -> { if (!has(player, "veliorasuite.pets.use")) { noPerm(player); return true; } if (args.length < 2) { player.sendMessage(config.color(config.message("pet-summon-usage", "%prefix% &eGunakan: &f/pet summon <pet>"))); return true; } manager.summon(player, args[1].toLowerCase(Locale.ROOT)); }
            case "dismiss" -> { if (!has(player, "veliorasuite.pets.use")) { noPerm(player); return true; } manager.dismiss(player, true); }
            case "storage" -> { if (!has(player, "veliorasuite.pets.storage")) { noPerm(player); return true; } manager.openStorage(player); }
            case "rename" -> { if (!has(player, "veliorasuite.pets.rename")) { noPerm(player); return true; } if (args.length < 3) { player.sendMessage("/pet rename <pet|active> <nama>"); return true; } manager.rename(player, args[1].toLowerCase(Locale.ROOT), join(args, 2)); }
            case "feed" -> { if (!has(player, "veliorasuite.pets.use")) { noPerm(player); return true; } handleFeed(player, args); }
            case "ride" -> { if (!has(player, "veliorasuite.pets.use")) { noPerm(player); return true; } return true; }
            case "reload" -> { if (!has(player, "veliorasuite.pets.reload") && !has(player, "veliorasuite.pets.admin")) { noPerm(player); return true; } manager.reload(); player.sendMessage(config.color(config.message("reload-success", "%prefix% &aVelioraPets berhasil direload."))); }
            case "give" -> {
                if (!has(player, "veliorasuite.pets.give") && !has(player, "veliorasuite.pets.admin")) { noPerm(player); return true; }
                if (args.length < 3) { player.sendMessage("/pet give <player> <pet>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(config.color(config.prefix() + "&cPlayer tidak online.")); return true; }
                if (!manager.givePet(target, args[2].toLowerCase(Locale.ROOT), false)) player.sendMessage(config.color(config.prefix() + "&cPet tidak ditemukan."));
            }
            case "remove" -> {
                if (!has(player, "veliorasuite.pets.remove") && !has(player, "veliorasuite.pets.admin")) { noPerm(player); return true; }
                if (args.length < 3) { player.sendMessage("/pet remove <player> <pet>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(config.color(config.prefix() + "&cPlayer tidak online.")); return true; }
                if (!manager.removePet(target, args[2].toLowerCase(Locale.ROOT))) player.sendMessage(config.color(config.prefix() + "&cPlayer tidak punya pet itu."));
            }
            default -> manager.openMain(player);
        }
        return true;
    }

    private void handleFeed(Player player, String[] args) {
        String target = "active";
        int amount = 1;
        if (args.length >= 2) {
            if (isInteger(args[1])) {
                amount = parseAmount(player, args[1]);
                if (amount < 1) return;
            } else {
                target = args[1].toLowerCase(Locale.ROOT);
            }
        }
        if (args.length >= 3) {
            amount = parseAmount(player, args[2]);
            if (amount < 1) return;
        }
        manager.feed(player, target, amount);
    }

    private int parseAmount(Player player, String raw) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount >= 1) return amount;
        } catch (NumberFormatException ignored) {
        }
        player.sendMessage(config.color(config.message("pet-feed-invalid-amount", "%prefix% &cJumlah makanan harus angka minimal 1.")));
        return -1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("shop", "gacha", "list", "info", "summon", "dismiss", "storage", "rename", "feed", "ride", "reload", "give", "remove")) if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(option);
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("summon") || args[0].equalsIgnoreCase("rename") || args[0].equalsIgnoreCase("feed") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("ride"))) {
            if (sender instanceof Player player) {
                String lower = args[1].toLowerCase(Locale.ROOT);
                if ("active".startsWith(lower)) result.add("active");
                for (String id : manager.playerData(player.getUniqueId()).owned().keySet()) if (id.startsWith(lower)) result.add(id);
                if (args[0].equalsIgnoreCase("feed")) addAmountTabs(result, lower);
            }
            return result;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("feed")) {
            addAmountTabs(result, args[2].toLowerCase(Locale.ROOT));
            return result;
        }
        if ((args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove")))) {
            String lower = args[2].toLowerCase(Locale.ROOT);
            for (String id : config.pets().keySet()) if (id.startsWith(lower)) result.add(id);
        }
        return result;
    }

    private void addAmountTabs(List<String> result, String lower) {
        for (String option : FEED_AMOUNTS) if (option.startsWith(lower)) result.add(option);
    }

    private boolean isInteger(String raw) { try { Integer.parseInt(raw); return true; } catch (NumberFormatException ignored) { return false; } }
    private boolean has(CommandSender sender, String permission) { return sender.hasPermission(permission) || sender.isOp(); }
    private void noPerm(CommandSender sender) { sender.sendMessage(config.color(config.message("no-permission", "%prefix% &cKamu tidak punya izin."))); }
    private String join(String[] args, int start) { StringBuilder builder = new StringBuilder(); for (int i = start; i < args.length; i++) { if (builder.length() > 0) builder.append(' '); builder.append(args[i]); } return builder.toString(); }
}
