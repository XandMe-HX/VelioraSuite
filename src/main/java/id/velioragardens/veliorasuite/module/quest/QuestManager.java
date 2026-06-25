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
        configManager.load();
        dataManager.load();
    }

    public void shutdown() {
        bossBarManager.hideAll();
        dataManager.flush();
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
            send(player, "quest-not-ready", "%prefix% &cQuest ini sudah selesai. Claim dulu rewardnya.", Map.of());
            return false;
        }

        int manaCost = skillsHook.getQuestManaCost(progress.getLevel());
        boolean bypass = configManager.hasBypassMana(player);
        int manaBefore = skillsHook.isAvailable() ? skillsHook.getMana(player) : 0;
        if (!bypass) {
            if (configManager.isRequireSkillsMana() && !skillsHook.isAvailable()) {
                send(player, "skills-not-found", "%prefix% &cMana system belum aktif. Hubungi staff.", Map.of());
                return false;
            }
            if (skillsHook.isAvailable() && !skillsHook.hasMana(player, manaCost)) {
                send(player, "mana-not-enough", "%prefix% &cMana kamu tidak cukup. Butuh &f%mana_cost% &cMana untuk mulai quest ini.", Map.of("%mana_cost%", String.valueOf(manaCost)));
                return false;
            }
            if (skillsHook.isAvailable() && manaCost > 0 && !skillsHook.takeMana(player, manaCost, "quest:" + category.key())) {
                send(player, "mana-not-enough", "%prefix% &cMana kamu tidak cukup. Butuh &f%mana_cost% &cMana untuk mulai quest ini.", Map.of("%mana_cost%", String.valueOf(manaCost)));
                return false;
            }
        }
        int manaAfter = skillsHook.isAvailable() ? skillsHook.getMana(player) : 0;
        logManaDebug(player, category, progress.getLevel(), manaBefore, manaCost, manaAfter, bypass);

        progress.setState(QuestState.ACTIVE);
        progress.setCurrentProgress(0);
        progress.setCurrentTarget(configManager.calculateTarget(category, progress.getLevel()));
        progress.setCurrentRewardMoney(configManager.calculateRewardMoney(progress.getLevel()));
        dataManager.save(data);
        bossBarManager.showOrUpdate(player, category, progress);
        send(player, "quest-started", "%prefix% &aQuest &f%quest% &adimulai. Mana terpakai: &f%mana_cost%&a.", placeholders(category, progress, manaCost));
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

        int money = progress.getCurrentRewardMoney();
        rewardManager.depositMoney(player, money);
        if (configManager.isGiveManaOnComplete() && skillsHook.isAvailable()) {
            skillsHook.addMaxMana(player, configManager.getManaReward(), true);
        }

        int newCompleted = progress.getCompletedCount() + 1;
        progress.setCompletedCount(newCompleted);
        boolean levelUp = newCompleted % configManager.getCompletionsPerLevel() == 0;
        if (levelUp) progress.setLevel(progress.getLevel() + 1);
        progress.setState(QuestState.CLAIMED);
        progress.setCurrentProgress(0);
        progress.setCurrentTarget(configManager.calculateTarget(category, progress.getLevel()));
        progress.setCurrentRewardMoney(configManager.calculateRewardMoney(progress.getLevel()));
        dataManager.save(data);
        bossBarManager.hide(player);

        send(player, "quest-claimed", "%prefix% &aReward quest &f%quest% &aberhasil diclaim. Kamu mendapat &f%money% &adan &f%mana_reward% Max Mana&a.", placeholders(category, progress, skillsHook.getQuestManaCost(progress.getLevel()), money));
        if (levelUp) send(player, "quest-level-up", "%prefix% &bQuest &f%quest% &bnaik ke level &f%level%&b.", placeholders(category, progress, 0));
        return true;
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
        PlayerCategoryProgress progress = dataManager.getOrCreate(player).getCategoryProgress(category);
        if (progress.getState() == QuestState.ACTIVE) {
            bossBarManager.showOrUpdate(player, category, progress);
        }
        if (ready) {
            if (configManager.isBossBarHideWhenComplete()) bossBarManager.hide(player);
            send(player, "quest-ready-claim", "%prefix% &aQuest &f%quest% &aselesai. Claim reward dengan &f/quests claim %category%&a.", placeholders(category, progress, 0));
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
        Map<String, String> map = new HashMap<>();
        map.put("%category%", category.key());
        map.put("%quest%", configManager.color(configManager.getCategoryDisplayName(category)));
        map.put("%level%", String.valueOf(progress.getLevel()));
        map.put("%progress%", String.valueOf(progress.getCurrentProgress()));
        map.put("%target%", String.valueOf(progress.getCurrentTarget()));
        map.put("%mana_cost%", String.valueOf(manaCost));
        map.put("%money%", String.valueOf(money));
        map.put("%mana_reward%", String.valueOf(configManager.getManaReward()));
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
