package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReportManager {
    private final VelioraSuite plugin;
    private final ConfigFile configFile;
    private File dataFile;
    private FileConfiguration data;

    public ReportManager(VelioraSuite plugin, ConfigFile configFile) { this.plugin = plugin; this.configFile = configFile; }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "reports.yml");
        if (!dataFile.exists()) { try { dataFile.createNewFile(); } catch (IOException e) { plugin.getLogger().warning(e.getMessage()); } }
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.isConfigurationSection("reports")) data.createSection("reports");
        save();
    }

    public void save() { try { if (data != null && dataFile != null) data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Gagal simpan reports.yml: " + e.getMessage()); } }
    public void reload() { configFile.reload(); load(); }

    public int createPlayerReport(Player reporter, String targetName, String reason) {
        int id = nextId();
        Player targetOnline = Bukkit.getPlayerExact(targetName);
        OfflinePlayer target = targetOnline != null ? targetOnline : Bukkit.getOfflinePlayer(targetName);
        String path = "reports." + id;
        data.set(path + ".type", "player");
        data.set(path + ".status", "open");
        data.set(path + ".reporter", reporter.getName());
        data.set(path + ".reporter-uuid", reporter.getUniqueId().toString());
        data.set(path + ".reporter-ip", ip(reporter));
        data.set(path + ".target", target.getName() == null ? targetName : target.getName());
        data.set(path + ".target-uuid", target.getUniqueId().toString());
        data.set(path + ".target-ip", targetOnline == null ? "unknown" : ip(targetOnline));
        data.set(path + ".reason", reason);
        data.set(path + ".created-at", System.currentTimeMillis());
        save();
        notifyAdmins(id, reporter.getName(), targetName, reason);
        return id;
    }

    public int createBugReport(Player reporter, String message) {
        int id = nextId();
        String path = "reports." + id;
        data.set(path + ".type", "bug");
        data.set(path + ".status", "open");
        data.set(path + ".reporter", reporter.getName());
        data.set(path + ".reporter-uuid", reporter.getUniqueId().toString());
        data.set(path + ".reporter-ip", ip(reporter));
        data.set(path + ".reason", message);
        data.set(path + ".created-at", System.currentTimeMillis());
        save();
        notifyAdmins(id, reporter.getName(), "BUG", message);
        return id;
    }

    public List<Integer> openReports() {
        List<Integer> ids = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("reports");
        if (section == null) return ids;
        for (String key : section.getKeys(false)) {
            if (data.getString("reports." + key + ".status", "open").equalsIgnoreCase("open")) {
                try { ids.add(Integer.parseInt(key)); } catch (NumberFormatException ignored) {}
            }
        }
        ids.sort(Integer::compareTo);
        return ids;
    }

    public void sendInfo(CommandSender sender, int id) {
        String path = "reports." + id;
        if (!data.isConfigurationSection(path)) { sender.sendMessage(ColorUtil.color(msg("not-found").replace("%id%", String.valueOf(id)))); return; }
        sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
        sender.sendMessage(ColorUtil.color("&aReport #" + id));
        sender.sendMessage(ColorUtil.color("&7Type: &f" + data.getString(path + ".type")));
        sender.sendMessage(ColorUtil.color("&7Status: &f" + data.getString(path + ".status")));
        sender.sendMessage(ColorUtil.color("&7Reporter: &f" + data.getString(path + ".reporter")));
        sender.sendMessage(ColorUtil.color("&7Reporter IP: &f" + data.getString(path + ".reporter-ip")));
        sender.sendMessage(ColorUtil.color("&7Target: &f" + data.getString(path + ".target", "-")));
        sender.sendMessage(ColorUtil.color("&7Target IP: &f" + data.getString(path + ".target-ip", "unknown")));
        sender.sendMessage(ColorUtil.color("&7Reason: &f" + data.getString(path + ".reason")));
        sender.sendMessage(ColorUtil.color("&8&m------------------------------"));
    }

    public boolean close(CommandSender sender, int id) {
        String path = "reports." + id;
        if (!data.isConfigurationSection(path)) return false;
        data.set(path + ".status", "closed");
        data.set(path + ".closed-by", sender.getName());
        data.set(path + ".closed-at", System.currentTimeMillis());
        save();
        return true;
    }

    public boolean banIp(CommandSender sender, int id, String reason) {
        String ip = data.getString("reports." + id + ".target-ip", "unknown");
        if (ip.equalsIgnoreCase("unknown") || ip.isBlank()) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban-ip " + ip + " " + reason);
        data.set("reports." + id + ".banip-by", sender.getName());
        data.set("reports." + id + ".banip-reason", reason);
        save();
        return true;
    }

    private int nextId() { int id = data.getInt("last-id", 0) + 1; data.set("last-id", id); return id; }
    private String ip(Player player) { return player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress(); }
    public String msg(String key) { return configFile.get().getString("messages." + key, "&cMessage not found: " + key).replace("%prefix%", configFile.get().getString("messages.prefix", "&8【&aVelioraReport&8】")); }

    private void notifyAdmins(int id, String reporter, String target, String reason) {
        if (!configFile.get().getBoolean("notify-admins", true)) return;
        String msg = msg("admin-notify").replace("%id%", String.valueOf(id)).replace("%reporter%", reporter).replace("%target%", target).replace("%reason%", reason);
        for (Player player : Bukkit.getOnlinePlayers()) if (player.hasPermission("veliorasuite.report.admin")) player.sendMessage(ColorUtil.color(msg));
    }
}
