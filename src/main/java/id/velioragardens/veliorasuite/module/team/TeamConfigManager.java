package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class TeamConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public TeamConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/team.yml");
        File file = new File(plugin.getDataFolder(), "modules/team.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        migrateBalanceV2(file);
    }

    private void migrateBalanceV2(File file) {
        if (config.getInt("settings.balance-version", 0) >= 2) return;
        config.set("settings.create-cost", 50_000D);
        config.set("settings.upgrade-cost", 50_000D);
        config.set("settings.balance-version", 2);
        try {
            config.save(file);
            plugin.getLogger().info("VelioraTeam: harga create dan upgrade diperbarui menjadi 50.000.");
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraTeam: gagal menyimpan migrasi harga: " + exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return getBoolean("settings.enabled", true);
    }

    public String getPrefix() {
        return getString("settings.prefix", "&8[&bVelioraTeam&8] ");
    }

    public double getCreateCost() {
        return Math.max(0D, getDouble("settings.create-cost", 50000D));
    }

    public int getDefaultMaxMembers() {
        return Math.max(1, getInt("settings.default-max-members", 5));
    }

    public double getUpgradeCost() {
        return Math.max(0D, getDouble("settings.upgrade-cost", 50000D));
    }

    public int getUpgradeAddMembers() {
        return Math.max(1, getInt("settings.upgrade-add-members", 5));
    }

    public int getMaxMembers() {
        return Math.max(getDefaultMaxMembers(), getInt("settings.max-members", 10));
    }

    public boolean isUpgradeEnabled() {
        return getBoolean("settings.upgrade-enabled", true);
    }

    public int getInviteTimeoutSeconds() {
        return Math.max(1, getInt("settings.invite-timeout-seconds", 60));
    }

    public int getOwnerLeaveConfirmTimeoutSeconds() {
        return Math.max(1, getInt("settings.owner-leave-confirm-timeout-seconds", 30));
    }

    public int getMinTeamNameLength() {
        return Math.max(1, getInt("settings.min-team-name-length", 3));
    }

    public int getMaxTeamNameLength() {
        return Math.max(getMinTeamNameLength(), getInt("settings.max-team-name-length", 5));
    }

    public Pattern getTeamNamePattern() {
        String regex = getString("settings.team-name-regex", "^[A-Z]{3,5}$");
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            plugin.getLogger().warning("VelioraTeam: team-name-regex tidak valid. Fallback ke A-Z 3-5 huruf.");
            return Pattern.compile("^[A-Z]{3,5}$");
        }
    }

    public List<String> getBlockedNames() {
        return config == null ? List.of("ADMIN", "OWNER", "STAFF", "MOD") : config.getStringList("settings.blocked-names");
    }

    public boolean isAllowSetOwnerNonMember() {
        return getBoolean("settings.allow-setowner-non-member", true);
    }

    public boolean isChatTagEnabled() {
        return getBoolean("settings.chat-tag.enabled", false);
    }

    public String getChatTagFormat() {
        return getString("settings.chat-tag.format", getString("formats.chat-tag", "&f【&b&l%team%&f】&f "));
    }

    public int getMaxTeamNameLengthInChat() {
        return Math.max(1, getInt("settings.chat-tag.max-team-name-length-in-chat", 5));
    }

    public String getUsePermission() {
        return getString("permissions.use", "veliorasuite.team.use");
    }

    public String getAdminPermission() {
        return getString("permissions.admin", "veliorasuite.team.admin");
    }

    public String getReloadPermission() {
        return getString("permissions.reload", "veliorasuite.team.reload");
    }

    public String getSetOwnerPermission() {
        return getString("permissions.setowner", "veliorasuite.team.setowner");
    }

    public String getDeletePermission() {
        return getString("permissions.delete", "veliorasuite.team.delete");
    }

    public String getBypassCostPermission() {
        return getString("permissions.bypass-cost", "veliorasuite.team.bypasscost");
    }

    public String getBypassLimitPermission() {
        return getString("permissions.bypass-limit", "veliorasuite.team.bypasslimit");
    }

    public String getFormat(String path, String fallback) {
        return getString("formats." + path, fallback);
    }

    public List<String> getFormatList(String path, List<String> fallback) {
        List<String> lines = config == null ? List.of() : config.getStringList("formats." + path);
        return lines.isEmpty() ? fallback : lines;
    }

    public String getMessage(String path, String fallback) {
        return getString("messages." + path, fallback).replace("%prefix%", getPrefix());
    }

    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> lines = config == null ? List.of() : config.getStringList("messages." + path);
        return lines.isEmpty() ? fallback : lines;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String getString(String path, String fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getString(path, fallback);
    }

    private boolean getBoolean(String path, boolean fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getBoolean(path, fallback);
    }

    private int getInt(String path, int fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getInt(path, fallback);
    }

    private double getDouble(String path, double fallback) {
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        return config.getDouble(path, fallback);
    }
}
