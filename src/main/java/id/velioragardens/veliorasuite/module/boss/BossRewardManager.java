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
    private static final double MIN_CONTRIBUTION_PERCENT = 5.0D;
    private static final long REWARD_COOLDOWN_MILLIS = 60_000L;

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
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            String name = player == null ? entry.uuid().toString().substring(0, 8) : player.getName();
            Bukkit.broadcastMessage(config.color("&7" + (i + 1) + ". &f" + name + " &7- &c" + String.format("%.1f", entry.damage()) + " damage"));
        }
        for (int i = 0; i < top.size(); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            Player player = Bukkit.getPlayer(entry.uuid());
            if (!eligible(player, deathLocation, entry.damage(), totalDamage, definition.id())) continue;
            long main = mainMoney(definition);
            long bonus = i < 3 ? topBonus(i) : 0L;
            long total = main + bonus;
            if (config.moneyTotalCapEnabled()) total = Math.min(total, config.moneyTotalCapMax());
            if (total > 0 && deposit(player, total)) player.sendMessage(config.color(config.message("reward-money-total", "%prefix% &aTotal reward uang boss kamu: &e%money%").replace("%money%", String.valueOf(total))));
            giveBalancedItems(player, definition.rarity(), i);
            rewardCooldown.put(cooldownKey(player.getUniqueId(), definition.id()), System.currentTimeMillis());
            player.sendMessage(config.color(config.message("reward-received", "%prefix% &aKamu mendapat reward boss karena memberi &f%damage% &adamage.").replace("%damage%", String.format("%.1f", entry.damage()))));
        }
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
        if (percent < MIN_CONTRIBUTION_PERCENT) return false;
        long last = rewardCooldown.getOrDefault(cooldownKey(player.getUniqueId(), bossId), 0L);
        return System.currentTimeMillis() - last >= REWARD_COOLDOWN_MILLIS;
    }

    private String cooldownKey(UUID uuid, String bossId) { return uuid + ":" + bossId; }

    private long mainMoney(BossDefinition definition) {
        if (!config.bossMoneyEnabled(definition)) return 0L;
        return randomMoney(config.bossMoneyMin(definition), config.bossMoneyMax(definition));
    }

    private long topBonus(int rankIndex) {
        if (!config.topDamageBonusEnabled()) return 0L;
        return randomMoney(config.topBonusMin(rankIndex), config.topBonusMax(rankIndex));
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
