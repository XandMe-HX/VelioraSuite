package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AnnouncementManager {
    private final VelioraSuite plugin;
    private final ConfigFile configFile;
    private final Random random = new Random();
    private BukkitTask task;
    private int index;
    public AnnouncementManager(VelioraSuite plugin, ConfigFile configFile) { this.plugin = plugin; this.configFile = configFile; }

    public void start() {
        stop();
        if (!configFile.get().getBoolean("enabled", true)) return;
        long interval = Math.max(20L, configFile.get().getLong("settings.interval-seconds", 300) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sendNext, interval, interval);
    }

    public void stop() { if (task != null) { task.cancel(); task = null; } }
    public void reload() { configFile.reload(); start(); }

    public void sendNext() {
        List<String> ids = ids();
        if (ids.isEmpty()) return;
        String mode = configFile.get().getString("settings.mode", "random");
        String id;
        if (mode.equalsIgnoreCase("sequential")) { id = ids.get(index++ % ids.size()); }
        else { id = ids.get(random.nextInt(ids.size())); }
        send(id);
    }

    public boolean send(String id) {
        String path = "announcements." + id;
        if (!configFile.get().isConfigurationSection(path)) return false;
        List<String> lines = configFile.get().getStringList(path + ".lines");
        List<String> worlds = configFile.get().getStringList(path + ".worlds");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!worlds.isEmpty() && !worlds.contains(player.getWorld().getName())) continue;
            for (String line : lines) player.sendMessage(ColorUtil.color(line));
        }
        return true;
    }

    public List<String> ids() {
        ConfigurationSection section = configFile.get().getConfigurationSection("announcements");
        return section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
    }

    public ConfigFile getConfigFile() { return configFile; }
}
