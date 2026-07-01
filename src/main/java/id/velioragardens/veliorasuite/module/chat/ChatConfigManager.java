package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChatConfigManager {

    private final VelioraSuite plugin;
    private FileConfiguration config;

    public ChatConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/chat.yml");
        File file = new File(plugin.getDataFolder(), "modules/chat.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return getBoolean("settings.enabled", true); }
    public String getPrefix() { return getString("settings.prefix", "&8[&bVelioraChat&8] "); }
    public boolean isFormatterEnabled() { return getBoolean("settings.formatter-enabled", false); }
    public boolean isEssentialsMode() { return getBoolean("settings.essentials-mode", true); }
    public boolean isUsePlaceholderApi() { return getBoolean("settings.use-placeholderapi", true); }
    public boolean isTeamTagPlaceholderEnabled() { return getBoolean("settings.team-tag-placeholder-enabled", true); }
    public boolean isProtectionEnabled() { return getBoolean("settings.protection-enabled", true); }

    public boolean isCooldownEnabled() { return getBoolean("settings.cooldown.enabled", true); }
    public int getCooldownSeconds() { return Math.max(0, getInt("settings.cooldown.seconds", 2)); }

    public boolean isCommandSpamEnabled() { return getBoolean("settings.command-spam.enabled", true); }
    public int getCommandSpamSeconds() { return Math.max(0, getInt("settings.command-spam.seconds", 2)); }
    public boolean isCommandSpamApplyToAllCommands() { return getBoolean("settings.command-spam.apply-to-all-commands", true); }
    public List<String> getIgnoredCommands() { return config == null ? List.of("/login", "/register", "/l", "/reg") : config.getStringList("settings.command-spam.ignored-commands"); }

    public Map<String, Integer> getExtraCommandCooldowns() {
        Map<String, Integer> cooldowns = new LinkedHashMap<>();
        if (config == null) return cooldowns;

        ConfigurationSection section = config.getConfigurationSection("settings.command-spam.extra-cooldowns");
        if (section == null) return cooldowns;

        for (String key : section.getKeys(false)) {
            cooldowns.put(key.toLowerCase(Locale.ROOT), Math.max(0, section.getInt(key, 0)));
        }
        return cooldowns;
    }

    public boolean isAutoReplyEnabled() { return getBoolean("settings.auto-reply.enabled", true); }
    public int getAutoReplyCooldownSeconds() { return Math.max(0, getInt("settings.auto-reply.cooldown-seconds", 30)); }
    public int getAutoReplyDelayTicks() { return Math.max(0, getInt("settings.auto-reply.delay-ticks", 20)); }
    public String getAutoReplyPrefix() { return getString("settings.auto-reply.prefix", "&8[&aVelioraGuide&8] "); }

    public Map<String, AutoReplyEntry> getAutoReplies() {
        Map<String, AutoReplyEntry> replies = new LinkedHashMap<>();
        if (config != null) {
            ConfigurationSection section = config.getConfigurationSection("settings.auto-reply.replies");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    List<String> triggers = section.getStringList(key + ".triggers");
                    List<String> lines = section.getStringList(key + ".lines");
                    if (!triggers.isEmpty() && !lines.isEmpty()) {
                        replies.put(key, new AutoReplyEntry(triggers, lines));
                    }
                }
            }
        }

        if (!replies.isEmpty()) return replies;

        replies.put("guide", new AutoReplyEntry(
                List.of("panduan", "cara main", "bingung", "command apa", "fitur server", "guide", "vguide"),
                List.of(
                        "&fButuh panduan? Ketik &b/vguide &7untuk menu utama server.",
                        "&7Cek juga &b/vrules&7, &b/vrank&7, dan &b/vproduct&7."
                )
        ));
        replies.put("rank", new AutoReplyEntry(
                List.of("rank", "vip", "harga rank", "benefit rank", "beli rank", "donate"),
                List.of(
                        "&fInfo rank ada di &b/vrank&f.",
                        "&7Pakai &b/vrank 2&7 sampai &b/vrank 13 &7untuk detail harga dan benefit."
                )
        ));
        replies.put("product", new AutoReplyEntry(
                List.of("produk", "product", "fitur", "plugin", "vproduct", "vporduct"),
                List.of(
                        "&fDaftar fitur dan produk server ada di &b/vproduct&f.",
                        "&7Kalau typo &b/vporduct&7 juga tetap diarahkan ke product."
                )
        ));
        replies.put("home", new AutoReplyEntry(
                List.of("sethome", "cara set home", "home tidak bisa", "home pin", "teleport home"),
                List.of(
                        "&fBuat home: &b/sethome <nama>&f. Teleport: &b/home <nama>&f.",
                        "&7Contoh: &b/sethome base &7lalu &b/home base&7."
                )
        ));
        replies.put("quest", new AutoReplyEntry(
                List.of("quest", "misi", "level quest", "cara quest"),
                List.of(
                        "&fQuest jalan otomatis saat mining, farming, fishing, atau kill mob.",
                        "&7Cek progress: &b/quests progress &7atau buka menu: &b/quests&7."
                )
        ));
        replies.put("claim", new AutoReplyEntry(
                List.of("claim", "claim land", "cara claim", "base aman", "grief"),
                List.of(
                        "&fUntuk claim area/base, cek panduan di &b/vguide 5&f.",
                        "&7Jangan lupa sethome di base setelah claim: &b/sethome base&7."
                )
        ));
        replies.put("report", new AutoReplyEntry(
                List.of("lapor", "report", "bug", "player nakal", "cheater"),
                List.of(
                        "&fLaporkan bug/player dengan &b/report <player|bug> <alasan>&f.",
                        "&7Staff/OP online akan menerima notifikasi report."
                )
        ));
        return replies;
    }

    public boolean isAntiRepeatEnabled() { return getBoolean("settings.anti-repeat.enabled", true); }
    public int getMaxRepeat() { return Math.max(1, getInt("settings.anti-repeat.max-repeat", 2)); }

    public boolean isAntiCapsEnabled() { return getBoolean("settings.anti-caps.enabled", true); }
    public int getMaxCapsPercent() { return Math.max(1, Math.min(100, getInt("settings.anti-caps.max-caps-percent", 70))); }
    public int getCapsMinLength() { return Math.max(1, getInt("settings.anti-caps.min-length", 8)); }
    public String getCapsAction() { return getString("settings.anti-caps.action", "LOWERCASE").toUpperCase(Locale.ROOT); }

    public boolean isWordFilterEnabled() { return getBoolean("settings.word-filter.enabled", true); }
    public String getWordFilterAction() { return getString("settings.word-filter.action", "REPLACE").toUpperCase(Locale.ROOT); }
    public String getReplacement() { return getString("settings.word-filter.replacement", "***"); }
    public boolean isCheckNormalizedEnabled() { return getBoolean("settings.word-filter.check-normalized", true); }
    public boolean isBlockSeparatedLettersEnabled() { return getBoolean("settings.word-filter.block-separated-letters", true); }
    public boolean isReduceRepeatedLettersEnabled() { return getBoolean("settings.word-filter.reduce-repeated-letters", true); }
    public List<String> getBlockedWords() { return config == null ? List.of() : config.getStringList("settings.word-filter.blocked-words"); }
    public List<String> getBlockedPatterns() { return config == null ? List.of() : config.getStringList("settings.word-filter.blocked-patterns"); }

    public String getUsePermission() { return getString("permissions.use", "veliorasuite.chat.use"); }
    public String getAdminPermission() { return getString("permissions.admin", "veliorasuite.chat.admin"); }
    public String getReloadPermission() { return getString("permissions.reload", "veliorasuite.chat.reload"); }
    public String getBypassCooldownPermission() { return getString("permissions.bypass-cooldown", "veliorasuite.chat.bypasscooldown"); }
    public String getBypassFilterPermission() { return getString("permissions.bypass-filter", "veliorasuite.chat.bypassfilter"); }
    public String getBypassCommandCooldownPermission() { return getString("permissions.bypass-command-cooldown", "veliorasuite.chat.bypasscommandcooldown"); }

    public String getPublicChatFormat() { return getString("formats.public-chat", "&7%luckperms_prefix%&f%player%&7: &f%message%"); }
    public String getTeamTagEmpty() { return getString("formats.team-tag-empty", ""); }
    public String getLuckPermsPrefixEmpty() { return getString("formats.luckperms-prefix-empty", ""); }

    public String getMessage(String path, String fallback) {
        return getString("messages." + path, fallback).replace("%prefix%", getPrefix());
    }

    public List<String> getMessageList(String path, List<String> fallback) {
        List<String> list = config == null ? List.of() : config.getStringList("messages." + path);
        return list.isEmpty() ? fallback : list;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String getString(String path, String fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getString(path, fallback);
    }

    private boolean getBoolean(String path, boolean fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getBoolean(path, fallback);
    }

    private int getInt(String path, int fallback) {
        if (config == null || !config.contains(path)) return fallback;
        return config.getInt(path, fallback);
    }

    public record AutoReplyEntry(List<String> triggers, List<String> lines) { }
}
