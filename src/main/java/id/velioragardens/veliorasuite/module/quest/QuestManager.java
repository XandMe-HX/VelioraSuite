package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        boolean ready = progressManager.addProgress(player, category, amount);
        PlayerQuestData data = dataManager.getOrCreate(player);
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        if (ready) {
            completeQuest(player, category, data, progress, true);
            return;
        }
        if (progress.getState() == QuestState.ACTIVE) {
            bossBarManager.showOrUpdate(player, category, progress);
        }
    }

    public void sendProgress(Player player) {
        String summary = progressManager.progressSummary(player);
        for (String line : summary.split("\n")) player.sendMessage(configManager.color(configManager.getPrefix() + "&e" + line));
    }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.messageList("help", List.of(
                "&8&m--------------------------------",
                "&a&lVelioraQuest",
                "&f/quests &7- Buka GUI quest.",
                "&f/quests progress &7- Cek progress quest.",
                "&f/quests start <category> &7- Mulai quest.",
                "&f/quests claim <category> &7- Claim reward.",
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
        boolean levelUp = newCompleted % configManager.getCompletionsPerLevel() == 0
                && progress.getLevel() < configManager.getMaxLevel();
        if (levelUp) progress.setLevel(progress.getLevel() + 1);
        int reachedLevel = progress.getLevel();
        int milestoneMultiplier = levelUp ? configManager.getMilestoneRewardMultiplier(reachedLevel) : 0;
        boolean manaBonus = levelUp && configManager.isManaBonusLevel(reachedLevel) && skillsHook.isAvailable();
        if (manaBonus) skillsHook.addMaxMana(player, configManager.getManaLevelBonus(), true);
        if (milestoneMultiplier > 0) rewardManager.giveItems(player, configManager.getMilestoneItemRewards(category), milestoneMultiplier);
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
