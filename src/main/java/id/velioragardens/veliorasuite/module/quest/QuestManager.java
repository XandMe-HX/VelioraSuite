package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestManager {

    private final VelioraSuite plugin;
    private final QuestConfigManager configManager;
    private final QuestDataManager dataManager;
    private final QuestSkillsHook skillsHook;
    private final QuestRewardManager rewardManager;
    private final QuestProgressManager progressManager;
    private final QuestStarterManager starterManager;
    private final QuestBossBarManager bossBarManager;
    private QuestGuiManager guiManager;
    private final Map<UUID, Map<QuestCategory, Long>> lastProgress = new HashMap<>();

    public QuestManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new QuestConfigManager(plugin);
        this.dataManager = new QuestDataManager(plugin, configManager);
        this.skillsHook = new QuestSkillsHook(plugin);
        this.rewardManager = new QuestRewardManager(plugin, configManager);
        this.progressManager = new QuestProgressManager(configManager, dataManager);
        this.starterManager = new QuestStarterManager(configManager, dataManager);
        this.bossBarManager = new QuestBossBarManager(configManager);
    }

    public void load() {
        configManager.load();
        dataManager.load();
        guiManager = new QuestGuiManager(this);
    }

    public void reload() {
        dataManager.flush();
        configManager.load();
        dataManager.load();
    }

    public void shutdown() {
        bossBarManager.hideAll();
        dataManager.shutdown();
    }

    public QuestConfigManager getConfigManager() { return configManager; }
    public QuestDataManager getDataManager() { return dataManager; }

    public boolean adminSkillXp(Player target, QuestCategory category, String operation, long amount) {
        if (target == null || category == null || amount < 0) return false;
        return switch (operation.toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> progressManager.addRawSkillExperience(target, category, amount);
            case "set" -> progressManager.setSkillExperience(target, category, amount);
            case "remove" -> progressManager.removeSkillExperience(target, category, amount);
            default -> false;
        };
    }

    public boolean adminSkillLevel(Player target, QuestCategory category, String operation, int amount) {
        if (target == null || category == null || amount < 0) return false;
        PlayerCategoryProgress progress = dataManager.getOrCreate(target).getCategoryProgress(category);
        if (progress == null) return false;
        return switch (operation.toLowerCase(java.util.Locale.ROOT)) {
            case "setlevel" -> progressManager.setSkillLevel(target, category, amount);
            case "addlevel" -> progressManager.setSkillLevel(target, category, progress.getLevel() + amount);
            case "reset" -> progressManager.setSkillLevel(target, category, 1);
            default -> false;
        };
    }
    public QuestProgressManager getProgressManager() { return progressManager; }
    public QuestStarterManager getStarterManager() { return starterManager; }
    public QuestGuiManager getGuiManager() { return guiManager; }
    public QuestSkillsHook getSkillsHook() { return skillsHook; }
    public QuestBossBarManager getBossBarManager() { return bossBarManager; }

    public void openGui(Player player) {
        if (!configManager.isGuiEnabled()) {
            sendProgress(player);
            return;
        }
        guiManager.open(player);
    }

    public boolean startQuest(Player player, QuestCategory category) {
        if (!validateCategory(player, category)) return false;
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        if (progress.getState() == QuestState.ACTIVE) {
            send(player, "quest-already-active", "%prefix% &cQuest ini sedang aktif.", Map.of());
            return false;
        }
        if (progress.getState() == QuestState.READY_TO_CLAIM) {
            return completeQuest(player, category, data, progress, true);
        }

        int manaCost = skillsHook.getQuestManaCost(progress.getLevel());
        boolean bypass = configManager.hasBypassMana(player);
        boolean manaRequired = configManager.isRequireSkillsMana();
        boolean manaAvailable = skillsHook.isAvailable();
        boolean useMana = manaRequired && manaAvailable && !bypass;
        int manaBefore = useMana ? skillsHook.getMana(player) : 0;
        if (!bypass) {
            if (manaRequired && !manaAvailable) {
                send(player, "skills-not-found", "%prefix% &cMana system belum aktif. Hubungi staff.", Map.of());
                return false;
            }
            if (useMana && !skillsHook.hasMana(player, manaCost)) {
                send(player, "mana-not-enough", "%prefix% &cMana kamu tidak cukup. Butuh &f%mana_cost% &cMana untuk mulai quest ini.", Map.of("%mana_cost%", String.valueOf(manaCost)));
                return false;
            }
            if (useMana && manaCost > 0 && !skillsHook.takeMana(player, manaCost, "quest:" + category.key())) {
                send(player, "mana-not-enough", "%prefix% &cMana kamu tidak cukup. Butuh &f%mana_cost% &cMana untuk mulai quest ini.", Map.of("%mana_cost%", String.valueOf(manaCost)));
                return false;
            }
        }
        int manaAfter = useMana ? skillsHook.getMana(player) : 0;
        int chargedMana = useMana ? manaCost : 0;
        logManaDebug(player, category, progress.getLevel(), manaBefore, chargedMana, manaAfter, bypass);

        progress.setState(QuestState.ACTIVE);
        progress.setCurrentProgress(0);
        progress.setCurrentTarget(configManager.calculateTarget(category, progress.getLevel()));
        progress.setCurrentRewardMoney(configManager.calculateRewardMoney(progress.getLevel()));
        dataManager.save(data);
        bossBarManager.showOrUpdate(player, category, progress);
        send(player, "quest-started", "%prefix% &aQuest &f%quest% &adimulai. Mana terpakai: &f%mana_cost%&a.", placeholders(category, progress, chargedMana));
        return true;
    }

    public boolean claimQuest(Player player, QuestCategory category) {
        if (!validateCategory(player, category)) return false;
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        if (progress.getState() != QuestState.READY_TO_CLAIM) {
            send(player, "quest-not-ready", "%prefix% &cQuest ini belum selesai.", Map.of());
            return false;
        }
        return completeQuest(player, category, data, progress, false);
    }

    public void cancelQuest(Player player, QuestCategory category) {
        if (!validateCategory(player, category)) return;
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        if (progress.getState() != QuestState.ACTIVE) {
            send(player, "quest-not-active", "%prefix% &cQuest ini belum aktif.", Map.of());
            return;
        }
        progress.setState(QuestState.NOT_STARTED);
        progress.setCurrentProgress(0);
        dataManager.save(data);
        bossBarManager.hide(player);
        send(player, "quest-cancelled", "%prefix% &eQuest &f%quest% &edibatalkan. Mana yang sudah dipakai tidak dikembalikan.", placeholders(category, progress, 0));
    }

    public void addProgress(Player player, QuestCategory category, int amount) {
        if (!configManager.canEarnProgress(player)) return;
        long now = System.currentTimeMillis();
        long cooldown = configManager.getSourceCooldownMillis();
        Map<QuestCategory, Long> playerCooldowns = lastProgress.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long previous = playerCooldowns.getOrDefault(category, 0L);
        if (cooldown > 0 && now - previous < cooldown) return;
        playerCooldowns.put(category, now);
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        int levelBefore = progress.getLevel();
        progressManager.addSkillExperience(player, category, amount);
        if (progress.getLevel() > levelBefore) onSkillLevelUp(player, category, progress, levelBefore);
        boolean ready = progressManager.addQuestProgress(player, category, amount);
        if (ready) {
            completeQuest(player, category, data, progress, true);
            return;
        }
        if (progress.getState() == QuestState.ACTIVE) {
            bossBarManager.showOrUpdate(player, category, progress);
        }
    }

    /** Applies small, configurable RPG rewards when XP advances a skill.
     * Quest completion still gives money/items, but it no longer inflates level. */
    private void onSkillLevelUp(Player player, QuestCategory category, PlayerCategoryProgress progress, int previousLevel) {
        for (int level = previousLevel + 1; level <= progress.getLevel(); level++) {
            if (configManager.isManaBonusLevel(level) && skillsHook.isAvailable()) {
                skillsHook.addMaxMana(player, configManager.getManaLevelBonus(), true);
            }
            if (category == QuestCategory.MONSTER_HUNTER && level % configManager.getHunterHealthLevelInterval() == 0) {
                AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) maxHealth.setBaseValue(Math.min(configManager.getHunterHealthCap(), maxHealth.getBaseValue() + configManager.getHunterHealthBonus()));
            }
            int multiplier = configManager.getMilestoneRewardMultiplier(level);
            if (multiplier > 0) rewardManager.giveItems(player, configManager.getMilestoneItemRewards(category), multiplier);
            int money = configManager.getLevelMoneyReward(level);
            if (money > 0) rewardManager.depositMoney(player, money);
        }
        String subtitle = configManager.getLevelSubtitle().replace("%quest%", configManager.getCategoryDisplayName(category)).replace("%level%", String.valueOf(progress.getLevel()));
        if (configManager.isLevelTitleEnabled()) player.sendTitle(configManager.color(configManager.getLevelTitle()), configManager.color(subtitle), 10, 50, 15);
        dataManager.save(dataManager.getOrCreate(player));
    }

    public void sendProgress(Player player) {
        String summary = progressManager.progressSummary(player);
        for (String line : summary.split("\n")) player.sendMessage(configManager.color(configManager.getPrefix() + "&e" + line));
    }

    public void sendSkillProfile(Player player, QuestCategory category) {
        if (!validateCategory(player, category) || !configManager.canUseSkill(player, category)) { sendNoPermission(player); return; }
        PlayerCategoryProgress progress = dataManager.getOrCreate(player).getCategoryProgress(category);
        long required = configManager.xpRequired(category, progress.getLevel());
        player.sendMessage(configManager.color("&8&m------------------------"));
        player.sendMessage(configManager.color("&a" + configManager.getCategoryDisplayName(category) + " &fLevel &a" + progress.getLevel() + "&7/&a" + configManager.getMaxLevel()));
        player.sendMessage(configManager.color("&7Skill XP: &f" + progress.getExperience() + "&7/&f" + required));
        player.sendMessage(configManager.color("&7XP per aksi: &f" + configManager.sourceXp(category, 1) + " &8| &7Multiplier: &fx" + formatMultiplier(configManager.permissionMultiplier(player, category))));
        player.sendMessage(configManager.color("&7Quest: &f" + progress.getCurrentProgress() + "&7/&f" + progress.getCurrentTarget()));
        int interval = configManager.getLevelMoneyInterval();
        int nextMilestone = ((progress.getLevel() / interval) + 1) * interval;
        player.sendMessage(configManager.color("&7Bonus uang level &f" + nextMilestone + "&7: &a" + configManager.getLevelMoneyReward(nextMilestone)));
        player.sendMessage(configManager.color("&8&m------------------------"));
    }

    public void sendStats(Player player) {
        PlayerQuestData data = dataManager.getOrCreate(player);
        player.sendMessage(configManager.color("&8&m------------------------"));
        player.sendMessage(configManager.color("&aVeliora Skills &7| &fTotal Level: &a" + totalLevel(data)));
        for (QuestCategory category : QuestCategory.values()) {
            PlayerCategoryProgress progress = data.getCategoryProgress(category);
            player.sendMessage(configManager.color("&7- " + configManager.getCategoryDisplayName(category) + "&7: &f" + progress.getLevel()));
        }
        player.sendMessage(configManager.color("&8&m------------------------"));
    }

    /** Shows the real gameplay source of every skill; this is XP skill, never vanilla XP. */
    public void sendSources(Player player, QuestCategory requested) {
        player.sendMessage(configManager.color("&8&m------------------------"));
        player.sendMessage(configManager.color("&aSumber Skill XP &7- &fXP ini terpisah dari XP Minecraft."));
        if (requested != null) {
            sendSourceLine(player, requested);
        } else {
            for (QuestCategory category : QuestCategory.values()) sendSourceLine(player, category);
        }
        player.sendMessage(configManager.color("&8&m------------------------"));
    }

    public void sendMultiplier(Player player) {
        double global = configManager.globalPermissionMultiplier(player);
        player.sendMessage(configManager.color("&8&m------------------------"));
        player.sendMessage(configManager.color("&aSkill XP Multiplier &7| &fGlobal: &ax" + formatMultiplier(global)));
        for (QuestCategory category : QuestCategory.values()) {
            double total = configManager.permissionMultiplier(player, category);
            if (Math.abs(total - global) > 0.001D) {
                player.sendMessage(configManager.color("&7- " + configManager.getCategoryDisplayName(category) + "&7: &ax" + formatMultiplier(total)));
            }
        }
        player.sendMessage(configManager.color("&7Permission contoh: &fveliorasuite.multiplier.50 &7atau &fveliorasuite.multiplier.mining.50"));
        player.sendMessage(configManager.color("&8&m------------------------"));
    }

    /** Public API used later by VelioraEnchant without needing to duplicate player data. */
    public boolean meetsSkillRequirement(Player player, QuestCategory category, int minimumLevel) {
        if (player == null || category == null) return false;
        return dataManager.getOrCreate(player).getCategoryProgress(category).getLevel() >= Math.max(1, minimumLevel);
    }

    public int getSkillLevel(Player player, QuestCategory category) {
        if (player == null || category == null) return 0;
        return dataManager.getOrCreate(player).getCategoryProgress(category).getLevel();
    }

    public double getSkillXpMultiplier(Player player, QuestCategory category) {
        return player == null || category == null ? 1.0D : configManager.permissionMultiplier(player, category);
    }

    private void sendSourceLine(Player player, QuestCategory category) {
        player.sendMessage(configManager.color("&7- " + configManager.getCategoryDisplayName(category)
                + "&7: &f" + sourceDescription(category) + " &8(+" + configManager.sourceXp(category, 1) + " XP)"));
    }

    private String sourceDescription(QuestCategory category) {
        return switch (category) {
            case WOODCUTTING -> "tebang kayu alami";
            case MINING -> "tambang batu dan ore alami";
            case FARMER -> "panen tanaman matang";
            case CHEF -> "masak makanan";
            case MONSTER_HUNTER -> "kalahkan monster alami";
            case ANIMAL_HUNTER -> "kalahkan hewan alami";
            case FISHING -> "tangkap ikan";
            case AGILITY -> "bergerak menjelajah";
            case ALCHEMY -> "brew atau minum potion";
            case ARCHERY -> "serang mob dengan panah";
            case EXCAVATION -> "gali tanah, pasir, dan gravel";
            case FIGHTING -> "serang mob dengan melee";
            case DEFENSE -> "terima serangan mob";
            case ENCHANTING -> "enchant item di enchanting table";
        };
    }

    private String formatMultiplier(double value) {
        return String.format(java.util.Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public void sendTop(CommandSender sender, QuestCategory category) {
        List<PlayerQuestData> players = dataManager.getAllKnownPlayers();
        players.sort((left, right) -> Integer.compare(score(right, category), score(left, category)));
        sender.sendMessage(configManager.color("&8&m------------------------"));
        sender.sendMessage(configManager.color("&aSkill Top &7- &f" + (category == null ? "Total Level" : configManager.getCategoryDisplayName(category))));
        int shown = 0;
        for (PlayerQuestData data : players) {
            if (++shown > 10) break;
            sender.sendMessage(configManager.color("&e#" + shown + " &f" + data.getName() + " &7- &a" + score(data, category)));
        }
        if (shown == 0) sender.sendMessage(configManager.color("&7Belum ada data pemain."));
        sender.sendMessage(configManager.color("&8&m------------------------"));
    }

    public void sendRank(Player player, QuestCategory category) {
        List<PlayerQuestData> players = dataManager.getAllKnownPlayers();
        players.sort((left, right) -> Integer.compare(score(right, category), score(left, category)));
        int rank = 1;
        for (PlayerQuestData data : players) {
            if (data.getUuid().equals(player.getUniqueId())) break;
            rank++;
        }
        PlayerQuestData self = dataManager.getOrCreate(player);
        player.sendMessage(configManager.color("&aRank Skill kamu: &f#" + rank + " &7(" + (category == null ? "Total" : category.key()) + ": &a" + score(self, category) + "&7)"));
    }

    private int score(PlayerQuestData data, QuestCategory category) { return category == null ? totalLevel(data) : data.getCategoryProgress(category).getLevel(); }
    private int totalLevel(PlayerQuestData data) { return data.getCategories().values().stream().mapToInt(PlayerCategoryProgress::getLevel).sum(); }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.messageList("help", List.of(
                "&8&m--------------------------------",
                "&a&lVelioraQuest",
                "&f/quests &7- Buka GUI quest.",
                "&f/quests progress &7- Cek progress quest.",
                "&f/quests skill <category> &7- Detail sebuah skill.",
                "&f/sources [skill] &7- Lihat aktivitas pemberi Skill XP.",
                "&f/quests multiplier &7- Lihat bonus XP aktif.",
                "&f/quests start <category> &7- Mulai quest.",
                "&f/quests claim <category> &7- Claim reward.",
                "&f/stats &7- Lihat semua level skill.",
                "&f/skilltop [skill] &7- Ranking skill.",
                "&f/chef &7- Profil skill Koki.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void sendStatus(CommandSender sender) {
        Map<String, String> map = new HashMap<>();
        map.put("%enabled%", String.valueOf(configManager.isEnabled()));
        map.put("%skills_mana%", String.valueOf(skillsHook.isAvailable()));
        map.put("%vault%", String.valueOf(plugin.getServer().getPluginManager().getPlugin("Vault") != null));
        map.put("%categories%", String.valueOf(QuestCategory.values().length));
        map.put("%players%", String.valueOf(dataManager.countPlayers()));
        sendLines(sender, configManager.messageList("status", List.of(
                "&8&m--------------------------------",
                "&a&lVelioraQuest Status",
                "&7Enabled: &f%enabled%",
                "&7Skills Mana: &f%skills_mana%",
                "&7Vault: &f%vault%",
                "&7Categories: &f%categories%",
                "&7Players Data: &f%players%",
                "&8&m--------------------------------"
        )), map);
    }

    public void sendReloadSuccess(CommandSender sender) { send(sender, "reload-success", "%prefix% &aVelioraQuest berhasil direload.", Map.of()); }
    public void sendNoPermission(CommandSender sender) { send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of()); }
    public void sendPlayerOnly(CommandSender sender) { send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.", Map.of()); }

    private boolean completeQuest(Player player, QuestCategory category, PlayerQuestData data, PlayerCategoryProgress progress, boolean automatic) {
        if (progress == null || progress.getState() != QuestState.READY_TO_CLAIM) return false;

        int money = progress.getCurrentRewardMoney();
        rewardManager.depositMoney(player, money);
        id.velioragardens.veliorasuite.module.adventure.AdventureModule adventure = plugin.getModuleManager().getModule("adventure")
                .filter(id.velioragardens.veliorasuite.module.adventure.AdventureModule.class::isInstance)
                .map(id.velioragardens.veliorasuite.module.adventure.AdventureModule.class::cast).orElse(null);
        if (adventure != null && adventure.getManager() != null) {
            adventure.getManager().addExperience(player, configManager.getAdventureExpPerCompletion());
        }
        rewardManager.giveItems(player, configManager.getBaseItemRewards(category), 1);
        if (configManager.isGiveManaOnComplete() && skillsHook.isAvailable()) {
            skillsHook.giveMana(player, configManager.getManaReward(), "quest:reward:" + category.key());
        }

        int newCompleted = progress.getCompletedCount() + 1;
        progress.setCompletedCount(newCompleted);
        // Level belongs to XP sources now. Completion is a reward loop only.
        boolean levelUp = false;
        int reachedLevel = progress.getLevel();
        int milestoneMultiplier = levelUp ? configManager.getMilestoneRewardMultiplier(reachedLevel) : 0;
        boolean manaBonus = false;
        progress.setCurrentProgress(0);
        progress.setCurrentTarget(configManager.calculateTarget(category, progress.getLevel()));
        progress.setCurrentRewardMoney(configManager.calculateRewardMoney(progress.getLevel()));

        boolean autoRestart = tryAutoRestart(player, category, progress);
        progress.setState(autoRestart ? QuestState.ACTIVE : QuestState.CLAIMED);
        dataManager.save(data);
        if (autoRestart) bossBarManager.showOrUpdate(player, category, progress);
        else bossBarManager.hide(player);

        if (automatic) {
            send(player, "quest-auto-completed", "%prefix% &aQuest &f%quest% &aselesai. Reward: &f%money%&a + &f%base_items%&a. Level sekarang: &f%level%&a.%milestone_message%", placeholders(category, progress, 0, money, milestoneMultiplier, manaBonus));
            if (autoRestart) {
                send(player, "quest-auto-restarted", "%prefix% &7Quest &f%quest% &7lanjut otomatis. Target baru: &f%target%&7.", placeholders(category, progress, 0));
            } else if (configManager.isAutoRestartAfterClaim()) {
                send(player, "quest-auto-paused", "%prefix% &eQuest belum dilanjutkan karena Mana tidak cukup. Mulai lagi dari menu Quest setelah reset.", Map.of());
            }
            return true;
        }

        send(player, "quest-claimed", "%prefix% &aReward quest &f%quest% &aberhasil diclaim. Kamu mendapat &f%money%&a + &f%base_items%&a.%milestone_message%", placeholders(category, progress, skillsHook.getQuestManaCost(progress.getLevel()), money, milestoneMultiplier, manaBonus));
        if (levelUp) send(player, "quest-level-up", "%prefix% &bQuest &f%quest% &bnaik ke level &f%level%&b.", placeholders(category, progress, 0, money, milestoneMultiplier, manaBonus));
        if (autoRestart) send(player, "quest-auto-restarted", "%prefix% &7Quest &f%quest% &7lanjut otomatis. Target baru: &f%target%&7.", placeholders(category, progress, 0));
        return true;
    }

    private boolean tryAutoRestart(Player player, QuestCategory category, PlayerCategoryProgress progress) {
        if (!configManager.isAutoRestartAfterClaim()) return false;
        if (configManager.hasBypassMana(player) || !configManager.isRequireSkillsMana()) return true;
        if (!skillsHook.isAvailable()) return false;

        int manaCost = skillsHook.getQuestManaCost(progress.getLevel());
        return manaCost <= 0 || skillsHook.takeMana(player, manaCost, "quest:auto:" + category.key());
    }

    private boolean validateCategory(Player player, QuestCategory category) {
        if (category == null) return false;
        if (!configManager.isCategoryEnabled(category)) {
            send(player, "quest-disabled", "%prefix% &cQuest ini sedang dinonaktifkan.", Map.of());
            return false;
        }
        return true;
    }

    private void logManaDebug(Player player, QuestCategory category, int level, int before, int cost, int after, boolean bypass) {
        if (!configManager.isDebugMana()) return;
        plugin.getLogger().info("VelioraQuest mana debug: player=" + player.getName()
                + " category=" + category.key()
                + " level=" + level
                + " before=" + before
                + " cost=" + cost
                + " after=" + after
                + " bypass=" + bypass);
    }

    private Map<String, String> placeholders(QuestCategory category, PlayerCategoryProgress progress, int manaCost) {
        return placeholders(category, progress, manaCost, progress.getCurrentRewardMoney());
    }

    private Map<String, String> placeholders(QuestCategory category, PlayerCategoryProgress progress, int manaCost, int money) {
        return placeholders(category, progress, manaCost, money, 0, false);
    }

    private Map<String, String> placeholders(QuestCategory category, PlayerCategoryProgress progress, int manaCost, int money, int milestoneMultiplier, boolean manaBonus) {
        Map<String, String> map = new HashMap<>();
        map.put("%category%", category.key());
        map.put("%quest%", configManager.color(configManager.getCategoryDisplayName(category)));
        map.put("%level%", String.valueOf(progress.getLevel()));
        map.put("%progress%", String.valueOf(progress.getCurrentProgress()));
        map.put("%target%", String.valueOf(progress.getCurrentTarget()));
        map.put("%mana_cost%", String.valueOf(manaCost));
        map.put("%money%", String.valueOf(money));
        map.put("%mana_reward%", String.valueOf(configManager.getManaLevelBonus()));
        map.put("%base_items%", configManager.formatItemRewards(configManager.getBaseItemRewards(category), 1));
        String milestoneItems = configManager.formatItemRewards(configManager.getMilestoneItemRewards(category), milestoneMultiplier);
        map.put("%milestone_items%", milestoneItems);
        map.put("%milestone_message%", milestoneMultiplier > 0
                ? " &6Bonus level: &f" + milestoneItems + (manaBonus ? " &6dan &b+" + configManager.getManaLevelBonus() + " Max Mana" : "")
                : "");
        return map;
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.message(path, fallback), placeholders)));
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) sender.sendMessage(configManager.color(apply(line, placeholders)));
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }
}
