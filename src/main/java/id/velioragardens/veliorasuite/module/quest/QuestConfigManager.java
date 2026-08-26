package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestItemReward;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public QuestConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/quest.yml");
        File file = new File(plugin.getDataFolder(), "modules/quest.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        migrateAutomaticProgressionV2(file);
        migrateRewardsV3(file);
        migrateSkillGrowthV4(file);
        migrateAuraStyleProgressionV5(file);
        migrateProtectionV6(file);
        migrateExperienceLevelRewardsV7(file);
        migrateSkillCommandsV8(file);
        migrateRequirementsV9(file);
        migrateLegacyWoodcuttingDisplay(file);
    }

    public boolean isEnabled() { return bool("settings.enabled", true); }
    public String getPrefix() { return str("settings.prefix", "&8[&aVelioraQuest&8] "); }
    public boolean isRequireSkillsMana() { return bool("settings.require-skills-mana", false); }
    public boolean isDebugMana() { return bool("settings.debug-mana", false); }
    public boolean isGuiEnabled() { return bool("settings.gui.enabled", true); }
    public String getGuiTitle() { return str("settings.gui.title", "&8Veliora Quest"); }
    public int getGuiSize() { int size = integer("settings.gui.size", 54); return size <= 0 ? 54 : Math.min(54, ((size + 8) / 9) * 9); }
    public boolean isBossBarEnabled() { return bool("settings.bossbar.enabled", true); }
    public String getBossBarTitle() { return str("settings.bossbar.title", "&f%quest% &7- &a%progress%&7/&a%target% &8(&e%percent%%&8)"); }
    public BarColor getBossBarColor() { return bossBarColor(str("settings.bossbar.color", "GREEN")); }
    public BarStyle getBossBarStyle() { return bossBarStyle(str("settings.bossbar.style", "SEGMENTED_10")); }
    public boolean isBossBarHideWhenComplete() { return bool("settings.bossbar.hide-when-complete", true); }
    public int getBossBarAutoHideSeconds() { return Math.max(0, integer("settings.bossbar.auto-hide-seconds", 8)); }
    public int getCompletionsPerLevel() { return Math.max(1, integer("settings.progression.completions-per-level", 3)); }
    public int getMaxLevel() { return Math.max(1, integer("settings.progression.max-level", 500)); }
    public boolean isAutoStartOnProgress() { return bool("settings.progression.auto-start-on-progress", true); }
    public boolean isAutoRestartAfterClaim() { return bool("settings.progression.auto-restart-after-claim", true); }
    public boolean isGiveManaOnComplete() { return bool("settings.rewards.give-mana-on-complete", false); }
    public int getManaReward() { return Math.max(0, integer("settings.rewards.mana-reward", 0)); }
    public long getAdventureExpPerCompletion() { return Math.max(0L, config.getLong("integrations.adventure-exp-per-completion", 75L)); }
    public int getBaseMoney() { return Math.max(0, integer("settings.rewards.base-money", 1000)); }
    public int getMoneyIncreasePerLevel() { return Math.max(0, integer("settings.rewards.money-increase-per-level", integer("settings.rewards.money-increase-per-tier", 150))); }
    public int getMaxMoneyReward() { return Math.max(getBaseMoney(), integer("settings.rewards.max-money", 20000)); }
    public int getFarmMinimumGrowthSeconds() { return Math.max(0, integer("settings.anti-exploit.farm-minimum-growth-seconds", 300)); }
    public int getSourceCooldownMillis() { return Math.max(0, integer("settings.anti-exploit.source-cooldown-millis", 125)); }
    public boolean canEarnProgress(Player player) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
        return !config.getStringList("settings.anti-exploit.blocked-worlds").contains(player.getWorld().getName());
    }
    public int getManaLevelInterval() { return Math.max(1, integer("settings.rewards.mana-level-interval", 3)); }
    public int getManaLevelBonus() { return Math.max(0, integer("settings.rewards.mana-level-bonus", 1)); }
    public int getMilestoneLevelInterval() { return Math.max(1, integer("settings.rewards.milestone-level-interval", 5)); }
    public int getHunterHealthLevelInterval() { return Math.max(1, integer("settings.rewards.hunter-health-level-interval", 5)); }
    public double getHunterHealthBonus() { return Math.max(0.0D, config.getDouble("settings.rewards.hunter-health-bonus", 1.0D)); }
    public double getHunterHealthCap() { return Math.max(20.0D, config.getDouble("settings.rewards.hunter-health-cap", 30.0D)); }
    public boolean isLevelTitleEnabled() { return bool("settings.progression.level-up-title.enabled", true); }
    public String getLevelTitle() { return str("settings.progression.level-up-title.title", "&aLEVEL UP!"); }
    public String getLevelSubtitle() { return str("settings.progression.level-up-title.subtitle", "&f%quest% &7menjadi level &a%level%"); }
    public int getMilestoneMaxMultiplier() { return Math.max(1, integer("settings.rewards.milestone-max-multiplier", 3)); }
    public boolean isStarterEnabled() { return bool("settings.starter.enabled", true); }
    public boolean isStarterReminderEnabled() { return bool("settings.starter.reminder-enabled", true); }
    public int getStarterReminderIntervalSeconds() { return Math.max(900, integer("settings.starter.reminder-interval-seconds", 900)); }
    public List<String> getClaimLandCommands() { return commandList("settings.starter.claim-land-commands", List.of("/claim", "/lands claim", "/land claim", "/claimland")); }
    public List<String> getSetHomeCommands() { return commandList("settings.starter.set-home-commands", List.of("/sethome", "/home set")); }
    public List<String> getStarterKitCommands() { return commandList("settings.starter.starter-kit-commands", List.of("/kits claim starter", "/kit starter", "/kits starter")); }

    public boolean isCategoryEnabled(QuestCategory category) { return bool("categories." + category.key() + ".enabled", true); }
    public String getCategoryDisplayName(QuestCategory category) { return str("categories." + category.key() + ".display-name", fallbackDisplayName(category)); }
    public Material getCategoryIcon(QuestCategory category) { return material(str("categories." + category.key() + ".icon", fallbackIcon(category).name()), fallbackIcon(category)); }
    public int getBaseTarget(QuestCategory category) { return Math.max(1, integer("categories." + category.key() + ".base-target", fallbackBaseTarget(category))); }
    public int getTargetIncreasePerLevel(QuestCategory category) { return Math.max(0, integer("categories." + category.key() + ".target-increase-per-level", fallbackTargetIncrease(category))); }

    public int calculateTarget(QuestCategory category, int level) {
        int cappedLevel = Math.max(1, Math.min(level, getMaxLevel()));
        double levelIndex = cappedLevel - 1.0D;
        double hardMultiplier = 1.0D + (levelIndex * 0.28D) + (Math.pow(levelIndex / 10.0D, 2.0D) * 0.65D);
        long target = Math.round((getBaseTarget(category) * hardMultiplier) + (levelIndex * getTargetIncreasePerLevel(category)));
        return (int) Math.max(1L, Math.min(200000L, target));
    }

    public int calculateRewardMoney(int level) {
        int cappedLevel = Math.max(1, Math.min(level, getMaxLevel()));
        long reward = Math.round(getBaseMoney()
                + ((cappedLevel - 1L) * (long) getMoneyIncreasePerLevel())
                + (Math.sqrt(cappedLevel) * 350.0D));
        return (int) Math.max(0L, Math.min(getMaxMoneyReward(), reward));
    }

    public boolean isManaBonusLevel(int level) {
        return level > 0 && level % getManaLevelInterval() == 0;
    }

    public int getMilestoneRewardMultiplier(int level) {
        if (level <= 0 || level % getMilestoneLevelInterval() != 0) return 0;
        return Math.min(getMilestoneMaxMultiplier(), 1 + (level / 10));
    }

    public List<QuestItemReward> getBaseItemRewards(QuestCategory category) {
        return itemRewards(category, "base-item-rewards");
    }

    public List<QuestItemReward> getMilestoneItemRewards(QuestCategory category) {
        return itemRewards(category, "milestone-item-rewards");
    }

    public String formatItemRewards(List<QuestItemReward> rewards, int multiplier) {
        if (rewards.isEmpty() || multiplier <= 0) return "-";
        List<String> parts = new ArrayList<>();
        for (QuestItemReward reward : rewards) {
            parts.add(prettyMaterial(reward.material()) + " x" + (reward.amount() * multiplier));
        }
        return String.join(", ", parts);
    }

    public boolean isCountHoeFarmland() { return bool("categories.farmer.count-hoe-farmland", true); }

    public Set<Material> getMaterials(QuestCategory category, String node) {
        List<String> names = config == null ? List.of() : config.getStringList("categories." + category.key() + "." + node);
        if (names.isEmpty()) names = fallbackMaterials(category, node);
        Set<Material> result = new LinkedHashSet<>();
        for (String name : names) {
            Material material = material(name, null);
            if (material != null) result.add(material);
        }
        return result;
    }

    private List<QuestItemReward> itemRewards(QuestCategory category, String node) {
        if (config == null) return List.of();
        List<QuestItemReward> result = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList("categories." + category.key() + "." + node)) {
            Object rawMaterial = entry.get("material");
            Material material = material(rawMaterial == null ? "" : String.valueOf(rawMaterial), null);
            if (material == null || material.isAir()) continue;
            Object rawAmount = entry.get("amount");
            int amount = rawAmount instanceof Number number ? number.intValue() : 1;
            result.add(new QuestItemReward(material, amount));
        }
        return result;
    }

    private String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public Set<EntityType> getEntities(QuestCategory category) {
        List<String> names = config == null ? List.of() : config.getStringList("categories." + category.key() + ".entities");
        if (names.isEmpty()) names = fallbackEntities(category);
        Set<EntityType> result = new LinkedHashSet<>();
        for (String name : names) {
            try { result.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT))); } catch (Exception ignored) { }
        }
        return result;
    }

    public String getUsePermission() { return str("permissions.use", "veliorasuite.quest.use"); }
    public String getAdminPermission() { return str("permissions.admin", "veliorasuite.quest.admin"); }
    public String getReloadPermission() { return str("permissions.reload", "veliorasuite.quest.reload"); }
    public String getBypassManaPermission() { return str("permissions.bypass-mana", "veliorasuite.quest.bypassmana"); }
    public String getResetPermission() { return str("permissions.reset", "veliorasuite.quest.reset"); }
    public boolean hasUse(CommandSender sender) { return sender.hasPermission(getUsePermission()) || hasAdmin(sender); }
    public boolean hasAdmin(CommandSender sender) { return sender.hasPermission(getAdminPermission()) || sender.isOp(); }
    public boolean hasReload(CommandSender sender) { return sender.hasPermission(getReloadPermission()) || hasAdmin(sender); }
    public boolean hasBypassMana(CommandSender sender) { return sender.hasPermission(getBypassManaPermission()) || hasAdmin(sender); }
    public boolean hasReset(CommandSender sender) { return sender.hasPermission(getResetPermission()) || hasAdmin(sender); }

    public String message(String path, String fallback) { return str("messages." + path, fallback).replace("%prefix%", getPrefix()); }
    public List<String> messageList(String path, List<String> fallback) { List<String> list = config == null ? List.of() : config.getStringList("messages." + path); return list.isEmpty() ? fallback : list; }
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    /** Enables automatic AuraSkills-style progression once without touching player quest data. */
    private void migrateAutomaticProgressionV2(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 2) return;
        config.set("settings.require-skills-mana", false);
        config.set("settings.progression.auto-start-on-progress", true);
        config.set("settings.progression.auto-restart-after-claim", true);
        config.set("settings.progression.config-version", 2);
        config.set("messages.quest-started", "%prefix% &aQuest &f%quest% &amengikuti aktivitasmu secara otomatis.");
        config.set("messages.quest-auto-completed", "%prefix% &aQuest &f%quest% &aselesai. Reward &f%money% &amasuk. Level sekarang: &f%level%&a. Quest berikutnya langsung berjalan.");
        config.set("messages.quest-not-active", "%prefix% &eQuest ini akan aktif otomatis saat kamu melakukan aktivitas kategorinya.");
        try {
            config.save(file);
            plugin.getLogger().info("VelioraQuest: progression otomatis v2 diterapkan tanpa mereset data player.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraQuest: gagal menyimpan migrasi progression otomatis: " + exception.getMessage());
        }
    }

    private void migrateRewardsV3(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 3) return;
        config.set("settings.rewards.give-mana-on-complete", true);
        config.set("settings.rewards.mana-reward", 5);
        config.set("settings.rewards.base-money", 500);
        config.set("settings.rewards.money-increase-per-level", 75);
        config.set("settings.rewards.max-money", 10000);
        config.set("settings.progression.config-version", 3);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraQuest: reward v3 diterapkan (Mana +5, money 500-10000).");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraQuest: gagal menyimpan migrasi reward v3: " + exception.getMessage());
        }
    }

    public boolean canUseSkill(CommandSender sender, QuestCategory category) {
        if (sender == null || category == null) return false;
        if (hasAdmin(sender)) return true;
        String permission = "veliorasuite.skill." + category.key();
        return !sender.isPermissionSet(permission) || sender.hasPermission(permission);
    }

    public boolean areRequirementsEnabled() { return bool("settings.requirements.enabled", true); }
    public String getRequirementMessage() { return str("settings.requirements.denied-message", "%prefix% &cButuh skill &f%skill% &clevel &f%level% &cuntuk memakai &f%item%&c."); }

    /** Global Aura-style requirements use the simple YAML form
     * MATERIAL skill:level [another_skill:level]. */
    public List<SkillRequirement> getRequirements(Material material, boolean armor) {
        if (!areRequirementsEnabled() || material == null) return List.of();
        List<SkillRequirement> result = new ArrayList<>();
        String path = "settings.requirements." + (armor ? "armor" : "item") + ".global";
        for (String line : config.getStringList(path)) {
            String[] pieces = line.trim().split("\\s+");
            if (pieces.length < 2 || !pieces[0].equalsIgnoreCase(material.name())) continue;
            for (int index = 1; index < pieces.length; index++) {
                String[] pair = pieces[index].split(":", 2);
                QuestCategory category = QuestCategory.fromKey(pair[0]);
                if (category == null || pair.length != 2) continue;
                try { result.add(new SkillRequirement(category, Math.max(1, Integer.parseInt(pair[1])))); }
                catch (NumberFormatException ignored) { }
            }
        }
        return result;
    }

    public int getLevelMoneyInterval() { return Math.max(1, integer("settings.rewards.level-money.interval", 10)); }
    public int getLevelMoneyBase() { return Math.max(0, integer("settings.rewards.level-money.base", 100)); }
    public int getLevelMoneyIncrease() { return Math.max(0, integer("settings.rewards.level-money.increase-per-milestone", 50)); }
    public int getLevelMoneyMax() { return Math.max(getLevelMoneyBase(), integer("settings.rewards.level-money.max", 2000)); }
    public int getLevelMoneyReward(int level) {
        if (level <= 0 || level % getLevelMoneyInterval() != 0) return 0;
        int milestone = level / getLevelMoneyInterval();
        return Math.min(getLevelMoneyMax(), getLevelMoneyBase() + Math.max(0, milestone - 1) * getLevelMoneyIncrease());
    }

    /** AuraSkills-like configurable curve. Uses a safe quadratic default and
     * individual per-skill overrides without requiring an expression parser. */
    public long xpRequired(QuestCategory category, int level) {
        String root = "progression.xp-requirements.skills." + category.key() + ".";
        int base = integer(root + "base", integer("progression.xp-requirements.default.base", 100));
        double multiplier = decimal(root + "multiplier", decimal("progression.xp-requirements.default.multiplier", 25.0D));
        double power = decimal(root + "power", decimal("progression.xp-requirements.default.power", 2.0D));
        return Math.max(1L, Math.round(base + multiplier * Math.pow(Math.max(0, level - 1), power)));
    }

    public int sourceXp(QuestCategory category, int units) {
        return Math.max(1, integer("categories." + category.key() + ".xp-per-action", 5)) * Math.max(1, units);
    }

    public double permissionMultiplier(org.bukkit.entity.Player player, QuestCategory category) {
        double percent = 0.0D;
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String node = info.getPermission().toLowerCase(java.util.Locale.ROOT);
            String prefix = "veliorasuite.multiplier.";
            if (!node.startsWith(prefix)) continue;
            String value = node.substring(prefix.length());
            if (value.startsWith(category.key() + ".")) value = value.substring(category.key().length() + 1);
            else if (value.contains(".")) continue;
            try { percent += Double.parseDouble(value); } catch (NumberFormatException ignored) { }
        }
        return Math.max(0.0D, 1.0D + percent / 100.0D);
    }

    private void migrateSkillGrowthV4(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 4) return;
        config.set("settings.rewards.mana-level-interval", 1);
        config.set("settings.rewards.milestone-level-interval", 5);
        config.set("settings.rewards.hunter-health-level-interval", 2);
        config.set("settings.rewards.hunter-health-bonus", 1.0D);
        config.set("settings.rewards.hunter-health-cap", 40.0D);
        config.set("settings.progression.config-version", 4);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraQuest: pertumbuhan v4 diterapkan (Mana tiap level, HP Hunter tiap 2 level).");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraQuest: gagal menyimpan pertumbuhan v4: " + exception.getMessage());
        }
    }

    /** Makes progression slower without resetting existing player quest data. */
    private void migrateAuraStyleProgressionV5(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 5) return;
        config.set("settings.progression.config-version", 5);
        config.set("settings.progression.completions-per-level", 3);
        config.set("settings.progression.level-up-title.enabled", true);
        config.set("settings.progression.level-up-title.title", "&aLEVEL UP!");
        config.set("settings.progression.level-up-title.subtitle", "&f%quest% &7menjadi level &a%level%");
        config.set("settings.rewards.mana-level-interval", 3);
        config.set("settings.rewards.hunter-health-level-interval", 5);
        config.set("settings.rewards.hunter-health-cap", 30.0D);
        setNewCategory("agility", "&aAgility", "RABBIT_FOOT", 24, 10, "SUGAR", 2, "RABBIT_FOOT", 1);
        setNewCategory("alchemy", "&dAlchemy", "BREWING_STAND", 12, 6, "GLOWSTONE_DUST", 2, "BLAZE_POWDER", 4);
        setNewCategory("archery", "&6Archery", "BOW", 36, 18, "ARROW", 8, "SPECTRAL_ARROW", 4);
        try { config.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("VelioraQuest: gagal menyimpan progression v5: " + exception.getMessage()); }
    }

    /** Explicit safety net for protected/lobby worlds. Cancelled Bukkit events
     * are already ignored by listeners; this also handles plugins that use a
     * separate protection layer instead of cancelling the event. */
    private void migrateProtectionV6(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 6) return;
        config.set("settings.anti-exploit.blocked-worlds", List.of("lobby", "war_world"));
        config.set("settings.anti-exploit.source-cooldown-millis", 125);
        config.set("settings.progression.config-version", 6);
        try { config.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("VelioraQuest: gagal menyimpan proteksi progression v6: " + exception.getMessage()); }
    }

    /**
     * Moves growth rewards to the actual XP skill level. Older configs kept
     * Aura-style values from v5, which made the intended one-mana-per-level
     * and Hunter health-per-two-levels rule unavailable after an update.
     */
    private void migrateExperienceLevelRewardsV7(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 7) return;
        config.set("settings.rewards.mana-level-interval", 1);
        config.set("settings.rewards.hunter-health-level-interval", 2);
        config.set("settings.rewards.hunter-health-bonus", 1.0D);
        config.set("settings.rewards.hunter-health-cap", 30.0D);
        config.set("settings.progression.config-version", 7);
        try { config.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("VelioraQuest: gagal menyimpan hadiah XP level v7: " + exception.getMessage()); }
    }

    /** Expands the skill path to level 500 and adds deliberately small milestone money. */
    private void migrateSkillCommandsV8(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 8) return;
        config.set("settings.progression.max-level", 500);
        config.set("settings.rewards.level-money.interval", 10);
        config.set("settings.rewards.level-money.base", 100);
        config.set("settings.rewards.level-money.increase-per-milestone", 50);
        config.set("settings.rewards.level-money.max", 2000);
        config.set("settings.progression.config-version", 8);
        try { config.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("VelioraQuest: gagal menyimpan skill command v8: " + exception.getMessage()); }
    }

    private void migrateRequirementsV9(File file) {
        if (config.getInt("settings.progression.config-version", 0) >= 9) return;
        config.set("settings.requirements.enabled", true);
        config.set("settings.requirements.denied-message", "%prefix% &cButuh skill &f%skill% &clevel &f%level% &cuntuk memakai &f%item%&c.");
        config.set("settings.requirements.item.global", List.of(
                "DIAMOND_PICKAXE mining:15", "NETHERITE_PICKAXE mining:35",
                "DIAMOND_SWORD monster_hunter:15", "NETHERITE_SWORD monster_hunter:35",
                "BOW archery:10", "TRIDENT fishing:20"));
        config.set("settings.requirements.armor.global", List.of(
                "DIAMOND_HELMET monster_hunter:15", "DIAMOND_CHESTPLATE monster_hunter:15",
                "DIAMOND_LEGGINGS monster_hunter:15", "DIAMOND_BOOTS agility:12",
                "NETHERITE_HELMET monster_hunter:35", "NETHERITE_CHESTPLATE monster_hunter:35",
                "NETHERITE_LEGGINGS monster_hunter:35", "NETHERITE_BOOTS agility:30"));
        config.set("settings.progression.config-version", 9);
        try { config.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("VelioraQuest: gagal menyimpan requirement v9: " + exception.getMessage()); }
    }

    private void setNewCategory(String key, String name, String icon, int target, int increase,
                                String baseMaterial, int baseAmount, String milestoneMaterial, int milestoneAmount) {
        String path = "categories." + key;
        if (config.isConfigurationSection(path)) return;
        config.set(path + ".enabled", true);
        config.set(path + ".display-name", name);
        config.set(path + ".icon", icon);
        config.set(path + ".base-target", target);
        config.set(path + ".target-increase-per-level", increase);
        config.set(path + ".base-item-rewards", List.of(Map.of("material", baseMaterial, "amount", baseAmount)));
        config.set(path + ".milestone-item-rewards", List.of(Map.of("material", milestoneMaterial, "amount", milestoneAmount)));
    }

    /** Migrates the old placeholder category so existing server configs show the real tree-cutting quest. */
    private void migrateLegacyWoodcuttingDisplay(File file) {
        if (config == null || !"&dWeekly / Special".equals(config.getString("categories.woodcutting.display-name"))) return;
        config.set("categories.woodcutting.display-name", "&6Tebang Kayu");
        config.set("categories.woodcutting.icon", "IRON_AXE");
        config.set("categories.woodcutting.base-target", 96);
        config.set("categories.woodcutting.target-increase-per-level", 56);
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraQuest: gagal memigrasikan tampilan quest tebang kayu: " + exception.getMessage());
        }
    }

    private Material material(String name, Material fallback) { Material material = Material.matchMaterial(name == null ? "" : name.trim().toUpperCase(Locale.ROOT)); return material == null ? fallback : material; }
    private List<String> commandList(String path, List<String> fallback) { List<String> list = config == null ? List.of() : config.getStringList(path); return list.isEmpty() ? fallback : list; }
    private String str(String path, String fallback) { return config == null || !config.contains(path) ? fallback : config.getString(path, fallback); }
    private boolean bool(String path, boolean fallback) { return config == null || !config.contains(path) ? fallback : config.getBoolean(path, fallback); }
    private int integer(String path, int fallback) { return config == null || !config.contains(path) ? fallback : config.getInt(path, fallback); }
    private BarColor bossBarColor(String value) { try { return BarColor.valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return BarColor.GREEN; } }
    private BarStyle bossBarStyle(String value) { try { return BarStyle.valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return BarStyle.SEGMENTED_10; } }

    private String fallbackDisplayName(QuestCategory category) {
        return switch (category) {
            case WOODCUTTING -> "&aWoodcutting";
            case MINING -> "&7Mining";
            case FARMER -> "&eFarmer";
            case CHEF -> "&6Chef";
            case MONSTER_HUNTER -> "&cMonster Hunter";
            case ANIMAL_HUNTER -> "&fAnimal Hunter";
            case FISHING -> "&bFishing";
            case AGILITY -> "&aAgility";
            case ALCHEMY -> "&dAlchemy";
            case ARCHERY -> "&6Archery";
        };
    }

    private Material fallbackIcon(QuestCategory category) {
        return switch (category) {
            case WOODCUTTING -> Material.OAK_LOG;
            case MINING -> Material.IRON_PICKAXE;
            case FARMER -> Material.WHEAT;
            case CHEF -> Material.COOKED_BEEF;
            case MONSTER_HUNTER -> Material.IRON_SWORD;
            case ANIMAL_HUNTER -> Material.COOKED_CHICKEN;
            case FISHING -> Material.FISHING_ROD;
            case AGILITY -> Material.RABBIT_FOOT;
            case ALCHEMY -> Material.BREWING_STAND;
            case ARCHERY -> Material.BOW;
        };
    }

    private int fallbackBaseTarget(QuestCategory category) {
        return switch (category) {
            case WOODCUTTING -> 192;
            case MINING -> 96;
            case FARMER -> 96;
            case CHEF -> 32;
            case MONSTER_HUNTER, ANIMAL_HUNTER -> 25;
            case FISHING -> 20;
            case AGILITY -> 24;
            case ALCHEMY -> 12;
            case ARCHERY -> 36;
        };
    }

    private int fallbackTargetIncrease(QuestCategory category) {
        return switch (category) {
            case WOODCUTTING -> 96;
            case MINING -> 64;
            case FARMER -> 48;
            case CHEF -> 18;
            case MONSTER_HUNTER, ANIMAL_HUNTER -> 12;
            case FISHING -> 10;
            case AGILITY -> 10;
            case ALCHEMY -> 6;
            case ARCHERY -> 18;
        };
    }

    private List<String> fallbackMaterials(QuestCategory category, String node) {
        if (category == QuestCategory.WOODCUTTING) return List.of("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG", "STRIPPED_OAK_LOG", "STRIPPED_SPRUCE_LOG", "STRIPPED_BIRCH_LOG", "STRIPPED_JUNGLE_LOG", "STRIPPED_ACACIA_LOG", "STRIPPED_DARK_OAK_LOG", "STRIPPED_MANGROVE_LOG", "STRIPPED_CHERRY_LOG");
        if (category == QuestCategory.MINING) return List.of("STONE", "COBBLESTONE", "DEEPSLATE", "COAL_ORE", "DEEPSLATE_COAL_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "IRON_ORE", "DEEPSLATE_IRON_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE", "ANCIENT_DEBRIS");
        if (category == QuestCategory.FARMER && node.equals("plant-materials")) return List.of("WHEAT_SEEDS", "CARROT", "POTATO", "BEETROOT_SEEDS", "SUGAR_CANE", "MELON_SEEDS", "PUMPKIN_SEEDS", "COCOA_BEANS", "NETHER_WART");
        if (category == QuestCategory.FARMER) return List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "SUGAR_CANE", "MELON", "PUMPKIN", "COCOA", "NETHER_WART");
        if (category == QuestCategory.CHEF) return List.of("COOKED_BEEF", "COOKED_CHICKEN", "COOKED_MUTTON", "COOKED_PORKCHOP", "COOKED_COD", "COOKED_SALMON", "BAKED_POTATO", "DRIED_KELP");
        return new ArrayList<>();
    }

    private List<String> fallbackEntities(QuestCategory category) {
        if (category == QuestCategory.MONSTER_HUNTER) return List.of("ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "ENDERMAN", "WITCH", "DROWNED", "HUSK", "STRAY", "SLIME", "PHANTOM", "PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "BREEZE", "BOGGED");
        if (category == QuestCategory.ANIMAL_HUNTER) return List.of("COW", "SHEEP", "CHICKEN", "PIG", "RABBIT", "COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH");
        return List.of();
    }

    private double decimal(String path, double fallback) {
        return config == null ? fallback : config.getDouble(path, fallback);
    }

    public record SkillRequirement(QuestCategory category, int level) { }
}
