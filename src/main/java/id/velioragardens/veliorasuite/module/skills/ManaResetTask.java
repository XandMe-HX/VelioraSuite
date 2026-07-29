package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public final class ManaResetTask {

    private final VelioraSuite plugin;
    private final SkillsConfigManager configManager;
    private final ManaManager manaManager;
    private BukkitTask task;
    private String lastRunDate = "";

    public ManaResetTask(VelioraSuite plugin, SkillsConfigManager configManager, ManaManager manaManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.manaManager = manaManager;
    }

    public void start() {
        stop();
        if (!configManager.isEnabled() || !configManager.isDailyResetEnabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * 60L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!configManager.isDailyResetEnabled()) return;
        LocalDate today = LocalDate.now();
        if (today.toString().equals(lastRunDate)) return;
        LocalTime now = LocalTime.now();
        LocalTime resetTime = parseResetTime();
        if (now.isBefore(resetTime)) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerManaData data = manaManager.getData(player);
            if (!today.toString().equals(data.getLastResetDate())) {
                manaManager.resetMana(data);
            }
        }
        lastRunDate = today.toString();
    }

    private LocalTime parseResetTime() {
        try {
            return LocalTime.parse(configManager.getResetTime());
        } catch (DateTimeParseException exception) {
            return LocalTime.MIDNIGHT;
        }
    }
}
