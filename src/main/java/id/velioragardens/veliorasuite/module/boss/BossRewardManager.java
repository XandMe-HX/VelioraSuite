package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossRarity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class BossRewardManager {

    private final VelioraSuite plugin;
    private final BossConfigManager config;
    private final Random random = new Random();
    private final Map<String, Long> rewardCooldown = new HashMap<>();
    private boolean vaultWarned;

    public BossRewardManager(VelioraSuite plugin, BossConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void distribute(BossDefinition definition, Location deathLocation, BossDamageTracker tracker) {
        cleanupRewardCooldowns();
        List<BossDamageTracker.Entry> top = tracker.top();
        double totalDamage = top.stream().mapToDouble(BossDamageTracker.Entry::damage).sum();
        
        if (totalDamage <= 0.0D) return;

        Map<UUID, Long> plannedRewards = new HashMap<>();
        for (int i = 0; i < top.size(); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            double damagePercent = (entry.damage() / totalDamage) * 100.0D;
            long reward = eligible(player, deathLocation, entry.damage(), totalDamage, definition.id()) && damagePercent >= config.minDamageContributionPercent()
                    ? calculateRankBasedReward(i, damagePercent)
                    : 0L;
            plannedRewards.put(entry.uuid(), reward);
        }

        Bukkit.broadcastMessage(config.color("&8&m--------------------------------"));
        Bukkit.broadcastMessage(config.color("&c&lTop Damage"));
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            String playerName = offlineName(entry.uuid());
            double damagePercent = (entry.damage() / totalDamage) * 100.0D;
            Bukkit.broadcastMessage(config.color("&7#" + (i + 1) + " &f" + playerName));
            Bukkit.broadcastMessage(config.color("&f" + String.format("%.0f", damagePercent) + "%"));
            Bukkit.broadcastMessage(config.color("&7Reward &e" + formatMoney(plannedRewards.getOrDefault(entry.uuid(), 0L))));
        }
        Bukkit.broadcastMessage(config.color("&8&m--------------------------------"));
        
        // Distribute rewards based on damage contribution
        for (int i = 0; i < top.size(); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            if (!eligible(player, deathLocation, entry.damage(), totalDamage, definition.id())) continue;
            
            double damagePercent = (entry.damage() / totalDamage) * 100.0D;
            
            if (damagePercent < config.minDamageContributionPercent()) {
                continue;
            }
            
            long reward = plannedRewards.getOrDefault(entry.uuid(), 0L);
            
            if (reward > 0 && deposit(player, reward)) {
                player.sendMessage(config.color(config.message("reward-money-total", 
                    "%prefix% &aTotal reward uang boss kamu: &e%money%")
                    .replace("%money%", String.format("%,d", reward))));
            }
            
            giveBalancedItems(player, definition.rarity(), i);
            rewardCooldown.put(cooldownKey(player.getUniqueId(), definition.id()), System.currentTimeMillis());
            player.sendMessage(config.color(config.message("reward-received", 
                "%prefix% &aKamu mendapat reward boss karena memberi &f%damage% &adamage.")
                .replace("%damage%", String.format("%.1f", entry.damage()))));
        }
    }

    /**
     * Calculate reward based on rank with config-driven random variation.
     * Defaults preserve the old hardcoded values to avoid accidental inflation.
     */
    private long calculateRankBasedReward(int rankIndex, double damagePercent) {
        long min = config.rankMoneyMin(rankIndex);
        long max = Math.max(min, config.rankMoneyMax(rankIndex));
        long span = max - min;
        long randomPart = (long) Math.floor(random.nextDouble() * (span * 0.85D + 1.0D));
        long contributionPart = Math.round(span * Math.min(0.15D, Math.max(0.0D, damagePercent) / 100.0D * 0.15D));
        long reward = Math.min(max, min + randomPart + contributionPart);
        if (rankIndex == 0) reward = Math.min(reward, 20_000L);
        if (config.moneyTotalCapEnabled()) reward = Math.min(reward, config.moneyTotalCapMax());
        return reward;
    }

    private boolean eligible(Player player, Location deathLocation, double damage, double totalDamage, String bossId) {
        if (player == null || !player.isOnline()) return false;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (deathLocation != null && deathLocation.getWorld() != null) {
            if (!player.getWorld().equals(deathLocation.getWorld())) return false;
            if (player.getLocation().distanceSquared(deathLocation) > 80.0D * 80.0D) return false;
        }
        if (damage < config.minDamageToReward()) return false;
        long last = rewardCooldown.getOrDefault(cooldownKey(player.getUniqueId(), bossId), 0L);
        long cooldown = config.rewardCooldownMillis();
        return cooldown <= 0L || System.currentTimeMillis() - last >= cooldown;
    }

    private String cooldownKey(UUID uuid, String bossId) { return uuid + ":" + bossId; }

    private void cleanupRewardCooldowns() {
        long ttl = Math.max(60_000L, config.rewardCooldownMillis());
        long cutoff = System.currentTimeMillis() - ttl;
        rewardCooldown.values().removeIf(lastRewardAt -> lastRewardAt < cutoff);
    }

    private void giveBalancedItems(Player player, BossRarity rarity, int rankIndex) {
        // Removed Diamond and Netherite Scrap to prevent economy inflation
        give(player, Material.BREAD, rarity == BossRarity.COMMON ? 8 : 0);
        give(player, Material.COOKED_BEEF, rarity == BossRarity.COMMON || rarity == BossRarity.RARE ? 8 : 0);
        give(player, Material.IRON_INGOT, rarity == BossRarity.COMMON ? randomRange(2, 6) : 0);
        give(player, Material.EMERALD, switch (rarity) { case COMMON -> randomRange(1, 2); case RARE, EPIC -> randomRange(2, 4); default -> randomRange(3, 6); });
        if (rarity.ordinal() >= BossRarity.RARE.ordinal() && random.nextDouble() < 0.35D) give(player, Material.GOLDEN_APPLE, rarity.ordinal() >= BossRarity.EPIC.ordinal() ? randomRange(1, 2) : 1);
    }

    private int randomRange(int min, int max) { return min + random.nextInt(Math.max(1, max - min + 1)); }

    private String offlineName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    private String formatMoney(long value) {
        return String.format("%,d", value).replace(',', '.');
    }

    private void give(Player player, Material material, int amount) {
        if (amount <= 0) return;
        player.getInventory().addItem(new ItemStack(material, amount)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private boolean deposit(Player player, long amount) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) { warnVault(player); return false; }
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"}) RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) { warnVault(player); return false; }
            Object economy = registration.getProvider();
            Method deposit = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            deposit.invoke(economy, player, (double) amount);
            return true;
        } catch (Exception exception) {
            warnVault(player);
            return false;
        }
    }

    private void warnVault(Player player) {
        if (player != null) player.sendMessage(config.color(config.message("reward-no-economy", "&cEconomy tidak tersedia, reward uang dilewati.")));
        if (vaultWarned) return;
        vaultWarned = true;
        plugin.getLogger().warning("VelioraBoss: Vault economy tidak aktif, reward money di-skip.");
    }
}
