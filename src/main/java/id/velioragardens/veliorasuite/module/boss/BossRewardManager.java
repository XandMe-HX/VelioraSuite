package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import id.velioragardens.veliorasuite.module.boss.model.BossRarity;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import id.velioragardens.veliorasuite.module.team.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private void notifyPlayers(String message) {
        if (!config.playerNotificationsEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(message);
    }

    public void distribute(BossDefinition definition, Location deathLocation, BossDamageTracker tracker) {
        cleanupRewardCooldowns();
        List<BossDamageTracker.Entry> top = tracker.top();
        double totalDamage = top.stream().mapToDouble(BossDamageTracker.Entry::damage).sum();
        if (totalDamage <= 0.0D) return;

        Map<UUID, RewardPlan> plans = new LinkedHashMap<>();
        Map<String, List<UUID>> eligibleByTeam = new LinkedHashMap<>();
        for (int rankIndex = 0; rankIndex < top.size(); rankIndex++) {
            BossDamageTracker.Entry entry = top.get(rankIndex);
            Player player = Bukkit.getPlayer(entry.uuid());
            double damagePercent = (entry.damage() / totalDamage) * 100.0D;
            if (!eligible(player, deathLocation, entry.damage(), definition.id())
                    || damagePercent < config.minDamageContributionPercent()) continue;

            Team team = findTeam(entry.uuid());
            String teamName = team == null ? "" : team.getName();
            long personal = calculatePersonalReward(definition, rankIndex, damagePercent);
            plans.put(entry.uuid(), new RewardPlan(player, entry, rankIndex, damagePercent, personal, teamName));
            if (!teamName.isBlank()) eligibleByTeam.computeIfAbsent(teamName, ignored -> new ArrayList<>()).add(entry.uuid());
        }

        Map<UUID, Long> teamBonuses = calculateTeamBonuses(eligibleByTeam);
        Map<UUID, Long> plannedRewards = new HashMap<>();
        for (Map.Entry<UUID, RewardPlan> entry : plans.entrySet()) {
            long reward = entry.getValue().personalReward() + teamBonuses.getOrDefault(entry.getKey(), 0L);
            if (reward > 0L) reward = Math.max(500L, reward);
            if (config.moneyTotalCapEnabled()) reward = Math.min(reward, config.moneyTotalCapMax());
            plannedRewards.put(entry.getKey(), reward);
        }

        notifyPlayers(config.color("&8&m--------------------------------"));
        notifyPlayers(config.color("&c&lTop Damage"));
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            BossDamageTracker.Entry entry = top.get(i);
            String playerName = offlineName(entry.uuid());
            double damagePercent = (entry.damage() / totalDamage) * 100.0D;
            notifyPlayers(config.color("&7#" + (i + 1) + " &f" + playerName));
            notifyPlayers(config.color("&f" + String.format("%.0f", damagePercent) + "%"));
            notifyPlayers(config.color("&7Reward &e" + formatMoney(plannedRewards.getOrDefault(entry.uuid(), 0L))));
        }
        notifyPlayers(config.color("&8&m--------------------------------"));
        
        for (Map.Entry<UUID, RewardPlan> entry : plans.entrySet()) {
            RewardPlan plan = entry.getValue();
            Player player = plan.player();
            if (player == null || !player.isOnline()) continue;
            long reward = plannedRewards.getOrDefault(entry.getKey(), 0L);
            if (reward > 0 && deposit(player, reward)) {
                player.sendMessage(config.color(config.message("reward-money-total",
                    "%prefix% &aTotal reward uang boss kamu: &e%money%")
                    .replace("%money%", String.format("%,d", reward))));
            }

            long teamBonus = teamBonuses.getOrDefault(entry.getKey(), 0L);
            if (teamBonus > 0L) {
                player.sendMessage(config.color(config.message("reward-team-bonus",
                                "%prefix% &bBonus team &f%team%&b: &e%money% &7(dibagi ke anggota yang mencapai 10% damage).")
                        .replace("%team%", plan.teamName())
                        .replace("%money%", formatMoney(teamBonus))));
            }

            giveBalancedItems(player, definition.rarity(), plan.rankIndex());
            rewardCooldown.put(cooldownKey(player.getUniqueId(), definition.id()), System.currentTimeMillis());
            player.sendMessage(config.color(config.message("reward-received",
                "%prefix% &aKamu mendapat reward boss karena memberi &f%damage% &adamage.")
                .replace("%damage%", String.format("%.1f", plan.entry().damage()))));
        }
    }

    private long calculatePersonalReward(BossDefinition definition, int rankIndex, double damagePercent) {
        if (!config.bossMoneyEnabled(definition)) return 0L;
        long min = config.bossMoneyMin(definition);
        long max = Math.max(min, config.bossMoneyMax(definition));
        long base = randomMoney(min, max);
        double contributionScale = 0.75D + Math.min(0.25D, Math.max(0.0D, damagePercent) / 100.0D);
        long reward = Math.round(base * contributionScale);
        if (config.topDamageBonusEnabled() && rankIndex < 3) {
            reward += randomMoney(config.topBonusMin(rankIndex), config.topBonusMax(rankIndex));
        }
        return reward;
    }

    private Map<UUID, Long> calculateTeamBonuses(Map<String, List<UUID>> eligibleByTeam) {
        Map<UUID, Long> result = new HashMap<>();
        if (!config.teamBonusEnabled()) return result;
        for (List<UUID> members : eligibleByTeam.values()) {
            if (members.size() < config.teamBonusMinimumMembers()) continue;
            long pool = randomMoney(config.teamBonusPoolMin(), config.teamBonusPoolMax());
            long share = Math.max(1L, pool / members.size());
            for (UUID uuid : members) result.put(uuid, share);
        }
        return result;
    }

    private Team findTeam(UUID uuid) {
        if (plugin.getModuleManager() == null) return null;
        return plugin.getModuleManager().getModule("team")
                .filter(TeamModule.class::isInstance)
                .map(TeamModule.class::cast)
                .filter(TeamModule::isEnabled)
                .map(TeamModule::getTeamManager)
                .map(manager -> manager.getDataManager().getTeamByPlayer(uuid))
                .orElse(null);
    }

    private long randomMoney(long min, long max) {
        long safeMin = Math.max(0L, min);
        long safeMax = Math.max(safeMin, max);
        return safeMin == safeMax ? safeMin : safeMin + random.nextLong(safeMax - safeMin + 1L);
    }

    private boolean eligible(Player player, Location deathLocation, double damage, String bossId) {
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

    private record RewardPlan(Player player, BossDamageTracker.Entry entry, int rankIndex,
                              double damagePercent, long personalReward, String teamName) { }
}
