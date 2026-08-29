package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FishingCommand implements CommandExecutor, TabCompleter {

    private final FishingManager manager;

    public FishingCommand(FishingManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
            if (sender instanceof Player player) manager.openMainGui(player);
            else manager.sendHelp(sender);
            return true;
        }

        if ((args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))
                && args.length >= 2 && args[1].equalsIgnoreCase("coins")) {
            return handleCoinAdmin(sender, args, args[0].equalsIgnoreCase("give"));
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "coins", "coin", "saldo" -> {
                if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (args.length >= 4 && hasAdmin(sender)) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    long amount;
                    try { amount = Math.max(0L, Long.parseLong(args[3])); }
                    catch (NumberFormatException exception) { sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cJumlah Koin tidak valid.")); return true; }
                    if (args[2].equalsIgnoreCase("set")) manager.getDataManager().setCoins(target, amount);
                    else if (args[2].equalsIgnoreCase("add")) manager.getDataManager().depositCoins(target, amount);
                    else { sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eGunakan /fish coins <player> <set|add> <jumlah>.")); return true; }
                    sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&aSaldo Koin &f" + target.getName() + " &aberhasil diperbarui."));
                    return true;
                }
                if (!(sender instanceof Player player)) { sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eGunakan /fish coins <player> <set|add> <jumlah>.")); return true; }
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                        + "&fSaldo Fishing: &6" + manager.formattedCoins(player) + " Koin"));
                return true;
            }
            case "bag" -> {
                if (!hasBag(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openBagGui(player);
                return true;
            }
            case "sell" -> {
                if (!hasSell(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openSellGui(player);
                return true;
            }
            case "rods", "rod", "shop" -> {
                if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openRodShop(player);
                return true;
            }
            case "questrods", "rodquests" -> {
                if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openQuestRodShop(player);
                return true;
            }
            case "collection" -> {
                if (!hasUse(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                manager.openCollectionGui(player);
                return true;
            }
            case "top" -> {
                if (!hasTop(sender)) { manager.sendNoPermission(sender); return true; }
                manager.sendTop(sender);
                return true;
            }
            case "reload" -> {
                if (!hasReload(sender)) {
                    manager.sendNoPermission(sender);
                    return true;
                }
                manager.reload();
                manager.sendReloadSuccess(sender);
                return true;
            }
            case "test" -> {
                if (!hasAdmin(sender)) { manager.sendNoPermission(sender); return true; }
                if (!(sender instanceof Player player)) { manager.sendPlayerOnly(sender); return true; }
                if (args.length != 2) {
                    sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                            + "&eGunakan /fish test <id-ikan>. Contoh: &f/fish test eclipse_kraken"));
                    return true;
                }
                if (!manager.giveTestFish(player, args[1])) {
                    sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                            + "&cID ikan tidak ditemukan. Gunakan Tab untuk melihat daftar ID."));
                    return true;
                }
                sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                        + "&aItem tes diberikan. Periksa texture kepala dan hover item-nya."));
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
        if (args.length == 2 && hasAdmin(sender) && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            return filter(List.of("coins"), args[1]);
        }
        if (args.length == 3 && hasAdmin(sender) && args[1].equalsIgnoreCase("coins")) {
            List<String> targets = new ArrayList<>();
            targets.add("*");
            Bukkit.getOnlinePlayers().forEach(player -> targets.add(player.getName()));
            return filter(targets, args[2]);
        }
        if (args.length == 2 && hasAdmin(sender) && args[0].equalsIgnoreCase("test")) {
            return filter(new ArrayList<>(manager.getConfigManager().getFishDefinitions().keySet()), args[1]);
        }
        if (args.length != 1) return new ArrayList<>();
        List<String> options = new ArrayList<>(Arrays.asList("help"));
        if (hasBag(sender)) options.add("bag");
        if (hasSell(sender)) options.add("sell");
        if (hasUse(sender)) { options.add("collection"); options.add("rods"); options.add("questrods"); options.add("coins"); }
        if (hasTop(sender)) options.add("top");
        if (hasReload(sender)) options.add("reload");
        if (hasAdmin(sender)) { options.add("give"); options.add("take"); options.add("test"); }
        return filter(options, args[0]);
    }

    private boolean handleCoinAdmin(CommandSender sender, String[] args, boolean give) {
        if (!hasAdmin(sender)) { manager.sendNoPermission(sender); return true; }
        if (args.length != 4) {
            sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix()
                    + "&eGunakan /fish " + (give ? "give" : "take") + " coins <player|*> <jumlah>."));
            return true;
        }
        long amount;
        try { amount = Long.parseLong(args[3]); }
        catch (NumberFormatException exception) { amount = 0L; }
        if (amount <= 0L) {
            sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cJumlah harus lebih dari 0."));
            return true;
        }

        int affected = 0;
        if (args[2].equals("*")) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (give) manager.getDataManager().depositCoins(target, amount);
                else manager.getDataManager().takeCoins(target, amount);
                affected++;
            }
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cPlayer tidak ditemukan."));
                return true;
            }
            if (give) manager.getDataManager().depositCoins(target, amount);
            else manager.getDataManager().takeCoins(target, amount);
            affected = 1;
        }
        manager.getConfigManager().getPlugin().getLogger().info("[FishingCoins] " + sender.getName() + " "
                + (give ? "memberikan " : "mengambil ") + amount + " Koin kepada/dari " + args[2] + ".");
        sender.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&aBerhasil memproses &f"
                + manager.getConfigManager().formatCoins(amount) + " Koin &auntuk &f" + affected + " player&a."));
        return true;
    }

    private boolean hasUse(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getUsePermission()) || hasAdmin(sender);
    }

    private boolean hasBag(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getBagPermission()) || hasAdmin(sender);
    }

    private boolean hasSell(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getSellPermission()) || hasAdmin(sender);
    }

    private boolean hasTop(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getTopPermission()) || hasAdmin(sender);
    }

    private boolean hasReload(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getReloadPermission()) || hasAdmin(sender);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(manager.getConfigManager().getAdminPermission()) || sender.isOp();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(option);
        return result;
    }
}
