package id.velioragardens.veliorasuite.module.announcement;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementManager {

    private final VelioraSuite plugin;
    private final AnnouncementConfigManager configManager;
    private final AnnouncementTask task;
    private final Map<String, AnnouncementMessage> activeAnnouncements = new LinkedHashMap<>();

    private int currentIndex = 0;
    private String lastRandomId = "";

    public AnnouncementManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new AnnouncementConfigManager(plugin);
        this.task = new AnnouncementTask(plugin, this);
    }

    public void load() {
        configManager.load();
        activeAnnouncements.clear();
        currentIndex = 0;
        lastRandomId = "";

        for (AnnouncementMessage announcement : configManager.getAnnouncements()) {
            if (announcement.isValid()) {
                activeAnnouncements.put(announcement.getId(), announcement);
            }
        }

        plugin.getLogger().info("VelioraAnnouncement loaded with " + activeAnnouncements.size() + " active announcement(s).");
    }

    public void start() {
        stop();

        if (!isEnabled()) {
            plugin.getLogger().info("VelioraAnnouncement tidak dijalankan karena settings.enabled=false.");
            return;
        }

        if (!configManager.isAutoStart()) {
            plugin.getLogger().info("VelioraAnnouncement auto-start dimatikan dari config.");
            return;
        }

        long initialDelay = configManager.getInitialDelaySeconds() * 20L;
        long interval = getIntervalSeconds() * 20L;
        task.start(initialDelay, interval);
    }

    public void stop() {
        task.stop();
    }

    public void reload() {
        stop();
        load();
        start();
    }

    public void shutdown() {
        stop();
        activeAnnouncements.clear();
    }

    public boolean isEnabled() {
        return configManager.isEnabled();
    }

    public boolean isRunning() {
        return task.isRunning();
    }

    public int getIntervalSeconds() {
        return configManager.getIntervalSeconds();
    }

    public String getMode() {
        return configManager.getMode();
    }

    public int getActiveCount() {
        return activeAnnouncements.size();
    }

    public List<String> getActiveIds() {
        return new ArrayList<>(activeAnnouncements.keySet());
    }

    public void sendNext() {
        if (!isEnabled() || activeAnnouncements.isEmpty()) {
            return;
        }

        AnnouncementMessage announcement = getNextAnnouncement();

        if (announcement != null) {
            broadcast(announcement);
        }
    }

    public boolean sendById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        AnnouncementMessage announcement = activeAnnouncements.get(id.toLowerCase(Locale.ROOT));

        if (announcement == null) {
            return false;
        }

        broadcast(announcement);
        return true;
    }

    public void sendStatus(CommandSender sender) {
        List<String> lines = configManager.getMessageList("status", List.of(
                "&8&m--------------------------------",
                "&a&lVelioraAnnouncement Status",
                "&7Enabled: &f%enabled%",
                "&7Running: &f%running%",
                "&7Mode: &f%mode%",
                "&7Interval: &f%interval%s",
                "&7Total Message: &f%total%",
                "&8&m--------------------------------"
        ));

        for (String line : lines) {
            sender.sendMessage(color(applyCommonPlaceholders(line)));
        }
    }

    public void sendList(CommandSender sender) {
        sender.sendMessage(color(configManager.getMessage("list-header", "&8&m--------------------------------")));
        sender.sendMessage(color(configManager.getMessage("list-title", "&a&lVelioraAnnouncement List")));

        if (activeAnnouncements.isEmpty()) {
            sender.sendMessage(color(configManager.getMessage("list-empty", "%prefix% &cTidak ada announcement aktif.")));
        } else {
            String format = configManager.getMessage("list-format", "&7- &f%id%");

            for (String id : activeAnnouncements.keySet()) {
                sender.sendMessage(color(format.replace("%id%", id)));
            }
        }

        sender.sendMessage(color(configManager.getMessage("list-footer", "&8&m--------------------------------")));
    }

    public void sendHelp(CommandSender sender) {
        List<String> lines = configManager.getMessageList("help", List.of(
                "&8&m--------------------------------",
                "&a&lVelioraAnnouncement",
                "&e/vannounce help &7- Melihat bantuan command.",
                "&e/vannounce status &7- Melihat status announcement.",
                "&e/vannounce list &7- Melihat id announcement aktif.",
                "&e/vannounce send <id> &7- Mengirim announcement.",
                "&e/vannounce reload &7- Reload announcement.",
                "&8&m--------------------------------"
        ));

        for (String line : lines) {
            sender.sendMessage(color(line));
        }
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, configManager.getMessage("reload-success", "%prefix% &aAnnouncement berhasil direload."));
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, configManager.getMessage("no-permission", "%prefix% &cKamu tidak punya izin."));
    }

    public void sendNotFound(CommandSender sender, String id) {
        send(sender, configManager.getMessage("not-found", "%prefix% &cAnnouncement &f%id% &ctidak ditemukan.").replace("%id%", id));
    }

    public void sendUsageSend(CommandSender sender) {
        send(sender, configManager.getMessage("usage-send", "%prefix% &cGunakan: &f/vannounce send <id>"));
    }

    public void sendManualSuccess(CommandSender sender, String id) {
        send(sender, configManager.getMessage("send-success", "%prefix% &aAnnouncement &f%id% &aberhasil dikirim.").replace("%id%", id));
    }

    private AnnouncementMessage getNextAnnouncement() {
        List<AnnouncementMessage> announcements = new ArrayList<>(activeAnnouncements.values());

        if (announcements.isEmpty()) {
            return null;
        }

        if (getMode().equals("RANDOM")) {
            return getRandomAnnouncement(announcements);
        }

        if (currentIndex >= announcements.size()) {
            currentIndex = 0;
        }

        AnnouncementMessage announcement = announcements.get(currentIndex);
        currentIndex++;
        return announcement;
    }

    private AnnouncementMessage getRandomAnnouncement(List<AnnouncementMessage> announcements) {
        if (announcements.size() == 1 || !configManager.isRandomAvoidRepeat()) {
            AnnouncementMessage announcement = announcements.get(ThreadLocalRandom.current().nextInt(announcements.size()));
            lastRandomId = announcement.getId();
            return announcement;
        }

        AnnouncementMessage announcement;
        int tries = 0;

        do {
            announcement = announcements.get(ThreadLocalRandom.current().nextInt(announcements.size()));
            tries++;
        } while (announcement.getId().equals(lastRandomId) && tries < 10);

        lastRandomId = announcement.getId();
        return announcement;
    }

    private void broadcast(AnnouncementMessage announcement) {
        List<String> lines = announcement.getLines();

        if (lines.isEmpty()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!announcement.canReceive(player)) {
                continue;
            }

            sendAnnouncementLines(player, lines);
            playSound(player);
        }
    }

    private void sendAnnouncementLines(Player player, List<String> lines) {
        String prefix = configManager.getPrefix();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String output = i == 0 ? prefix + line : line;
            player.sendMessage(color(output));
        }
    }

    private void playSound(Player player) {
        if (!configManager.isSoundEnabled()) {
            return;
        }

        Sound sound = configManager.getSound();

        if (sound == null) {
            return;
        }

        player.playSound(player.getLocation(), sound, configManager.getSoundVolume(), configManager.getSoundPitch());
    }

    private String applyCommonPlaceholders(String text) {
        return text
                .replace("%prefix%", configManager.getPrefix())
                .replace("%enabled%", String.valueOf(isEnabled()))
                .replace("%running%", String.valueOf(isRunning()))
                .replace("%auto_start%", String.valueOf(configManager.isAutoStart()))
                .replace("%mode%", getMode())
                .replace("%interval%", String.valueOf(getIntervalSeconds()))
                .replace("%total%", String.valueOf(getActiveCount()));
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(color(applyCommonPlaceholders(message)));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
