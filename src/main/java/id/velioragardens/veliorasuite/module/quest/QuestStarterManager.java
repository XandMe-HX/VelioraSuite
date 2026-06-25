package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class QuestStarterManager {

    private final QuestConfigManager configManager;
    private final QuestDataManager dataManager;

    public QuestStarterManager(QuestConfigManager configManager, QuestDataManager dataManager) {
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    public void ensureStarter(Player player) {
        if (!configManager.isStarterEnabled() || player == null) return;
        PlayerQuestData data = dataManager.getOrCreate(player);
        data.setStarterCompleted(data.isStarterDone());
        dataManager.save(data);
    }

    public boolean trackCommand(Player player, String commandLine) {
        if (!configManager.isStarterEnabled() || player == null || commandLine == null) return false;
        PlayerQuestData data = dataManager.getOrCreate(player);
        if (data.isStarterCompleted() || data.isStarterDone()) return false;

        boolean changed = false;
        if (!data.isClaimLand() && matches(commandLine, configManager.getClaimLandCommands())) {
            data.setClaimLand(true);
            changed = true;
        }
        if (!data.isSetHome() && matches(commandLine, configManager.getSetHomeCommands())) {
            data.setSetHome(true);
            changed = true;
        }
        if (!data.isStarterKit() && matches(commandLine, configManager.getStarterKitCommands())) {
            data.setStarterKit(true);
            changed = true;
        }
        if (data.isStarterDone()) {
            data.setStarterCompleted(true);
            changed = true;
        }
        if (changed) dataManager.save(data);
        return changed;
    }

    public void sendReminderIfNeeded(Player player) {
        if (!configManager.isStarterEnabled() || !configManager.isStarterReminderEnabled() || player == null) return;
        PlayerQuestData data = dataManager.getOrCreate(player);
        if (data.isStarterCompleted() || data.isStarterDone()) return;
        long now = System.currentTimeMillis();
        long interval = configManager.getStarterReminderIntervalSeconds() * 1000L;
        if (now - data.getLastReminder() < interval) return;
        data.setLastReminder(now);
        dataManager.save(data);
        sendReminder(player, data);
    }

    private boolean matches(String commandLine, List<String> configured) {
        String normalized = normalize(commandLine);
        for (String command : configured) {
            if (normalized.equals(normalize(command))) return true;
        }
        return false;
    }

    private String normalize(String commandLine) {
        String result = commandLine == null ? "" : commandLine.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return result.startsWith("/") ? result : "/" + result;
    }

    private void sendReminder(Player player, PlayerQuestData data) {
        for (String line : configManager.messageList("starter-reminder", List.of(
                "&8&m--------------------------------",
                "&a&lStarter Quest",
                "&7Selesaikan tugas awal agar aman bermain:",
                "&fClaim Land: &e%claim_land%",
                "&fSet Home: &e%set_home%",
                "&fAmbil Kit Starter: &e%starter_kit%",
                "&8&m--------------------------------"
        ))) {
            player.sendMessage(configManager.color(line
                    .replace("%claim_land%", yesNo(data.isClaimLand()))
                    .replace("%set_home%", yesNo(data.isSetHome()))
                    .replace("%starter_kit%", yesNo(data.isStarterKit()))));
        }
    }

    private String yesNo(boolean done) {
        return done ? "Selesai" : "Belum";
    }
}
