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
        List<BossDamageTracker.Entry> top = tracker.top();
        double totalDamage = top.stream().mapToDouble(BossDamageTracker.Entry::damage).sum();
        Bukkit.broadcastMessage(config.color(config.message("top-damage-header", "&cTop Damage Boss:")));
        
        // Display top 3 contributors
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            String name = player == null ? entry.uuid().toString().substring(0, 8) : player.getName();
            Bukkit.broadcastMessage(config.color("&7" + (i + 1) + ". &f" + name + " &7- &c" + String.format("%.1f", entry.damage()) + " damage"));
        }
        
        // Distribute rewards based on damage contribution
        for (int i = 0; i < top.size(); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            if (!eligible(player, deathLocation, entry.damage(), totalDamage, definition.id())) continue;
            
            // Calculate reward based on damage contribution and rank
            long reward = calculateDamageBasedReward(i, entry.damage(), totalDamage, definition);
            
            if (reward > 0 && deposit(player, reward)) {
                player.sendMessage(config.color(config.message("reward-money-total", 
                    "%prefix% &aTotal reward uang boss kamu: &e%money%")
                    .replace("%money%", String.format("%,d", reward))));
            }
            
            giveBalancedItems(player, definition.rarity(), i);
            rewardCooldown.put(cooldownKey(player.getUniqueId(), definition.id()), System.currentTimeMillis());
            player.sendMessage(config.color(config.message("reward-received", 
                "%prefix% &aKamu mendapat reward boss karena memberi &f%damage% &adamage.")
                .replace("%damage%", String.format("%.1f", entry.damage())))));
        }
    }

    /**
     * Calculate reward based on damage contribution
     * Rank 1: max 20.000
     * Rank 2: ~12.000
     * Rank 3: ~8.000
     * Rank 4-10: ~2.000-5.000
     * Rest: small reward
     */
    private long calculateDamageBasedReward(int rankIndex, double damageDealt, double totalDamage, BossDefinition definition) {
        if (totalDamage <= 0.0D) return 0L;
        
        double damagePercent = damageDealt / totalDamage * 100.0D;
        
        // Calculate base reward for this boss
        long mainReward = mainMoney(definition);
        
        long reward;
        
        if (rankIndex == 0) {
            // Top damager gets max 20.000 (or configured max)
            reward = Math.min(20_000L, mainReward);
        } else if (rankIndex == 1) {
            // 2nd place gets ~60% of top reward
            reward = Math.min(12_000L, (mainReward * 60) / 100);
        } else if (rankIndex == 2) {
            // 3rd place gets ~40% of top reward
            reward = Math.min(8_000L, (mainReward * 40) / 100);
        } else if (rankIndex <= 9) {
            // Rank 4-10: scale based on damage percentage
            // Map damage percent to 2.000-5.000 range
            long minReward = 2_000L;
            long maxReward = 5_000L;
            long scaledReward = minReward + (long) ((damagePercent / 5.0D) * (maxReward - minReward));
            reward = Math.min(maxReward, Math.max(minReward, scaledReward));
        } else {
            // Rank 11+: small reward (500-2000) only if damage percent > 0.5%
            if (damagePercent > 0.5D) {
                reward = Math.min(2_000L, Math.max(500L, (long) (damagePercent * 100)));
            } else {
                reward = 0L; // No reward for minimal damage
            }
        }
        
        // Apply cap if enabled
        if (config.moneyTotalCapEnabled()) {
            reward = Math.min(reward, config.moneyTotalCapMax());
        }
        
        return Math.max(0L, reward);
    }

    private boolean eligible(Player player, Location deathLocation, double damage, double totalDamage, String bossId) {
        if (player == null || !player.isOnline()) return false;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (deathLocation != null && deathLocation.getWorld() != null) {
            if (!player.getWorld().equals(deathLocation.getWorld())) return false;
            if (player.getLocation().distanceSquared(deathLocation) > 80.0D * 80.0D) return false;
        }
        if (damage < config.minDamageToReward()) return false;
        double percent = totalDamage <= 0.0D ? 0.0D : (damage / totalDamage) * 100.0D;
        if (percent < config.minDamageContributionPercent()) return false;
        long last = rewardCooldown.getOrDefault(cooldownKey(player.getUniqueId(), bossId), 0L);
        long cooldown = config.rewardCooldownMillis();
        return cooldown <= 0L || System.currentTimeMillis() - last >= cooldown;
    }

    private String cooldownKey(UUID uuid, String bossId) { return uuid + ":" + bossId; }

    private long mainMoney(BossDefinition definition) {
        if (!config.bossMoneyEnabled(definition)) return 0L;
        return randomMoney(config.bossMoneyMin(definition), config.bossMoneyMax(definition));
    }

    private long randomMoney(long min, long max) {
        long safeMin = Math.max(0L, Math.min(min, max));
        long safeMax = Math.max(safeMin, Math.max(min, max));
        if (safeMax <= safeMin) return safeMin;
        return safeMin + (long) Math.floor(random.nextDouble() * (safeMax - safeMin + 1));
    }

    private void giveBalancedItems(Player player, BossRarity rarity, int rankIndex) {
        give(player, Material.BREAD, rarity == BossRarity.COMMON ? 8 : 0);
        give(player, Material.COOKED_BEEF, rarity == BossRarity.COMMON || rarity == BossRarity.RARE ? 8 : 0);
        give(player, Material.IRON_INGOT, rarity == BossRarity.COMMON ? randomRange(2, 6) : 0);
        give(player, Material.EMERALD, switch (rarity) { case COMMON -> randomRange(1, 2); case RARE, EPIC -> randomRange(2, 4); default -> randomRange(3, 6); });
        if (rarity.ordinal() >= BossRarity.RARE.ordinal() && random.nextDouble() < 0.35D) give(player, Material.GOLDEN_APPLE, rarity.ordinal() >= BossRarity.EPIC.ordinal() ? randomRange(1, 2) : 1);
        int diamondCap = switch (rarity) { case COMMON -> 0; case RARE -> 1; case EPIC -> 2; case LEGENDARY, MYTHIC -> 3; };
        int diamonds = Math.max(0, Math.min(diamondCap, config.rewardMaterial(rarity, "diamond")));
        if (rankIndex > 0) diamonds = Math.max(0, diamonds / (rankIndex + 1));
        if (diamonds > 0 && random.nextDouble() < 0.35D) give(player, Material.DIAMOND, diamonds);
        int scraps = Math.max(0, Math.min(1, config.rewardMaterial(rarity, "ancient-debris")));
        if ((rarity == BossRarity.LEGENDARY || rarity == BossRarity.MYTHIC) && scraps > 0 && rankIndex == 0 && random.nextDouble() < 0.15D) give(player, Material.NETHERITE_SCRAP, scraps);
    }

    private int randomRange(int min, int max) { return min + random.nextInt(Math.max(1, max - min + 1)); }

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
