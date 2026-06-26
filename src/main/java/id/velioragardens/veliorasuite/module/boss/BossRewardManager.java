package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.List;

public final class BossRewardManager {

    private final VelioraSuite plugin;
    private final BossConfigManager config;
    private boolean vaultWarned;

    public BossRewardManager(VelioraSuite plugin, BossConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void distribute(BossDefinition definition, Location deathLocation, BossDamageTracker tracker) {
        if (deathLocation != null && deathLocation.getWorld() != null) {
            deathLocation.getWorld().dropItemNaturally(deathLocation, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            deathLocation.getWorld().dropItemNaturally(deathLocation, new ItemStack(Material.GOLDEN_APPLE, 10));
        }
        List<BossDamageTracker.Entry> top = tracker.top();
        Bukkit.broadcastMessage(config.color(config.message("top-damage-header", "&cTop Damage Boss:")));
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            String name = player == null ? entry.uuid().toString().substring(0, 8) : player.getName();
            Bukkit.broadcastMessage(config.color("&7" + (i + 1) + ". &f" + name + " &7- &c" + String.format("%.1f", entry.damage()) + " damage"));
        }
        for (int i = 0; i < top.size(); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            if (entry.damage() < config.minDamageToReward()) continue;
            Player player = Bukkit.getPlayer(entry.uuid());
            if (player == null) continue;
            long money = moneyFor(definition, i);
            if (money > 0) deposit(player, money);
            if (i < 3) giveRareMaterial(player, definition, i);
            player.sendMessage(config.color(config.message("reward-received", "%prefix% &aKamu mendapat reward boss karena memberi &f%damage% &adamage.").replace("%damage%", String.format("%.1f", entry.damage()))));
        }
    }

    private long moneyFor(BossDefinition definition, int rankIndex) {
        if (rankIndex == 0) return config.rewardMoney(definition.rarity(), "money-top1");
        if (rankIndex == 1) return config.rewardMoney(definition.rarity(), "money-top2");
        if (rankIndex == 2) return config.rewardMoney(definition.rarity(), "money-top3");
        return config.rewardMoney(definition.rarity(), "participation-money");
    }

    private void giveRareMaterial(Player player, BossDefinition definition, int rankIndex) {
        int diamond = config.rewardMaterial(definition.rarity(), "diamond");
        int debris = config.rewardMaterial(definition.rarity(), "ancient-debris");
        if (rankIndex > 0) {
            diamond = Math.max(0, diamond / (rankIndex + 1));
            debris = Math.max(0, debris / (rankIndex + 1));
        }
        if (diamond > 0) player.getInventory().addItem(new ItemStack(Material.DIAMOND, diamond)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        if (debris > 0) player.getInventory().addItem(new ItemStack(Material.ANCIENT_DEBRIS, debris)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void deposit(Player player, long amount) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) { warnVault(); return; }
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) { warnVault(); return; }
            Object economy = registration.getProvider();
            Method deposit = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            deposit.invoke(economy, player, (double) amount);
        } catch (Exception exception) {
            warnVault();
        }
    }

    private void warnVault() {
        if (vaultWarned) return;
        vaultWarned = true;
        plugin.getLogger().warning("VelioraBoss: Vault economy tidak aktif, reward money di-skip.");
    }
}
