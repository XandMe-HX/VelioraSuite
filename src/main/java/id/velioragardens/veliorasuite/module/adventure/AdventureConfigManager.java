package id.velioragardens.veliorasuite.module.adventure;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdventureConfigManager {
    private static final Pattern HEX_COLOR = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final VelioraSuite plugin;
    private YamlConfiguration config;
    private List<AdventureQuestTemplate> templates = List.of();
    private final Map<AdventureRank, Long> rankRequirements = new EnumMap<>(AdventureRank.class);

    public AdventureConfigManager(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        plugin.saveResourceIfNotExists("modules/adventure.yml");
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/adventure.yml"));
        migrateAppearanceAndAuraBridge();
        loadRanks();
        loadTemplates();
    }

    private void migrateAppearanceAndAuraBridge() {
        if (config.getInt("config-version", 1) >= 2) return;
        Map<AdventureRank, String> old = Map.of(
                AdventureRank.F, "&7F", AdventureRank.E, "&aE", AdventureRank.D, "&bD",
                AdventureRank.C, "&9C", AdventureRank.B, "&5B", AdventureRank.A, "&6A",
                AdventureRank.S, "&cS", AdventureRank.SS, "&dSS", AdventureRank.SSS, "&fSSS");
        Map<AdventureRank, String> fresh = Map.of(
                AdventureRank.F, "&8[&#9A9A9A&lF&8]", AdventureRank.E, "&8[&#72F28D&lE&8]",
                AdventureRank.D, "&8[&#45D6FF&lD&8]", AdventureRank.C, "&8[&#4D8DFF&lC&8]",
                AdventureRank.B, "&8[&#A86BFF&lB&8]", AdventureRank.A, "&8[&#FFD34D&lA&8]",
                AdventureRank.S, "&8[&#FF4D6D&lS&8]", AdventureRank.SS, "&8[&#FF56CB&lS&#B15CFF&lS&8]",
                AdventureRank.SSS, "&8[&#FF4D5F&lS&#FF9B45&lS&#FFE14D&lS&8]");
        for (AdventureRank rank : AdventureRank.values()) {
            String path = "ranks." + rank.name() + ".display";
            if (old.get(rank).equals(config.getString(path))) config.set(path, fresh.get(rank));
        }
        config.addDefault("integrations.auraskills.enabled", true);
        config.addDefault("integrations.auraskills.xp-to-adventure-ratio", 0.15D);
        config.addDefault("integrations.auraskills.minimum-source-xp", 1.0D);
        for (String skill : List.of("farming", "foraging", "mining", "fishing", "excavation")) config.addDefault("integrations.auraskills.skills." + skill, true);
        for (String skill : List.of("fighting", "defense", "archery")) config.addDefault("integrations.auraskills.skills." + skill, false);
        config.set("config-version", 2);
        try { config.save(new File(plugin.getDataFolder(), "modules/adventure.yml")); }
        catch (java.io.IOException exception) { plugin.getLogger().warning("VelioraPetualang: konfigurasi baru tidak dapat disimpan."); }
    }

    private void loadRanks() {
        rankRequirements.clear();
        for (AdventureRank rank : AdventureRank.values()) {
            rankRequirements.put(rank, Math.max(0L, config.getLong("ranks." + rank.name() + ".required-exp", defaultRequirement(rank))));
        }
    }

    private void loadTemplates() {
        List<AdventureQuestTemplate> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("quests");
        if (section != null) for (String id : section.getKeys(false)) {
            String path = "quests." + id;
            AdventureQuestTemplate template = new AdventureQuestTemplate(
                    id,
                    config.getString(path + ".name", id),
                    AdventureRank.parse(config.getString(path + ".rank", "F")),
                    AdventureQuestType.parse(config.getString(path + ".type", "KILL")),
                    config.getString(path + ".target", "ZOMBIE"),
                    Math.max(1, config.getInt(path + ".amount", 10)),
                    Math.max(5, config.getInt(path + ".duration-minutes", 30)),
                    Math.max(0, config.getInt(path + ".rewards.money", 500)),
                    Math.max(0L, config.getLong(path + ".rewards.player-exp", 100)),
                    Math.max(0L, config.getLong(path + ".rewards.guild-exp", 50)),
                    Math.max(0, config.getInt(path + ".rewards.mana", 2))
            );
            if (valid(template)) loaded.add(template);
            else plugin.getLogger().warning("VelioraPetualang: template quest tidak valid: " + id);
        }
        templates = List.copyOf(loaded);
    }

    private boolean valid(AdventureQuestTemplate template) {
        return switch (template.type()) {
            case KILL -> template.entityType() != null;
            case BREAK, FARM -> template.material() != null;
            case FISH, BOSS, EXPLORE -> true;
        };
    }

    public YamlConfiguration raw() { return config; }
    public List<AdventureQuestTemplate> templates() { return templates; }
    public String prefix() { return color(config.getString("messages.prefix", "&8[&aVeliora Petualang&8] &r")); }
    public String message(String path, String fallback) { return color(config.getString("messages." + path, fallback)); }
    public String color(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_COLOR.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder legacy = new StringBuilder("§x");
            for (char character : hex.toCharArray()) legacy.append('§').append(character);
            matcher.appendReplacement(output, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(output);
        return ChatColor.translateAlternateColorCodes('&', output.toString());
    }
    public int dailyQuestCount() { return Math.max(1, Math.min(9, config.getInt("daily.quest-count", 5))); }
    public int minimumOnlineMembers() { return Math.max(2, config.getInt("team.minimum-online-members", 2)); }
    public int coordinateMin() { return config.getInt("locations.min", -2000); }
    public int coordinateMax() { return config.getInt("locations.max", 2000); }
    public String questWorld() { return config.getString("locations.world", "world"); }
    public int exploreRadius() { return Math.max(5, config.getInt("locations.completion-radius", 20)); }
    public int activationRadius() { return Math.max(16, config.getInt("locations.mob-activation-radius", 48)); }
    public int objectiveRadius() { return Math.max(24, config.getInt("locations.objective-radius", 96)); }
    public int maxProgressPerSecond() { return Math.max(1, config.getInt("anti-farm.max-progress-per-second", 12)); }
    public int maxSpawnedMobs() { return Math.max(1, Math.min(30, config.getInt("locations.max-spawned-mobs", 20))); }
    public boolean spawnQuestMobs() { return config.getBoolean("locations.spawn-quest-mobs", true); }
    public boolean hideCommands() { return config.getBoolean("settings.hide-member-commands", true); }
    public String timezone() { return config.getString("daily.timezone", "Asia/Jakarta"); }
    public int maxLevel() { return Math.max(1, config.getInt("level.max", 100)); }
    public long levelBaseExp() { return Math.max(100L, config.getLong("level.base-exp", 500)); }
    public long levelGrowthExp() { return Math.max(0L, config.getLong("level.growth-exp", 75)); }
    public long rankRequirement(AdventureRank rank) { return rankRequirements.getOrDefault(rank, 0L); }
    public AdventureRank rankFor(long exp) {
        return rankRequirements.entrySet().stream().filter(entry -> exp >= entry.getValue())
                .max(Comparator.comparingLong(Map.Entry::getValue)).map(Map.Entry::getKey).orElse(AdventureRank.F);
    }
    public String rankDisplay(AdventureRank rank) { return color(config.getString("ranks." + rank.name() + ".display", "&f" + rank.name())); }
    public boolean auraSkillsEnabled() { return config.getBoolean("integrations.auraskills.enabled", true); }
    public boolean auraSkillEnabled(String skill) { return config.getBoolean("integrations.auraskills.skills." + skill.toLowerCase(java.util.Locale.ROOT), false); }
    public double auraSkillsRatio() { return Math.max(0.0D, Math.min(1.0D, config.getDouble("integrations.auraskills.xp-to-adventure-ratio", 0.15D))); }
    public double auraSkillsMinimumXp() { return Math.max(0.0D, config.getDouble("integrations.auraskills.minimum-source-xp", 1.0D)); }
    public String mainTitle() { return color(config.getString("gui.main-title", "&8Guild Petualang")); }
    public String questsTitle() { return color(config.getString("gui.quests-title", "&8Misi Hari Ini")); }
    public String submitTitle() { return color(config.getString("gui.submit-title", "&8Setor dan Riwayat")); }
    public String teamTitle() { return color(config.getString("gui.team-title", "&8Team Petualang")); }

    private long defaultRequirement(AdventureRank rank) {
        return switch (rank) {
            case F -> 0L; case E -> 2_500L; case D -> 7_500L; case C -> 20_000L;
            case B -> 50_000L; case A -> 100_000L; case S -> 200_000L;
            case SS -> 400_000L; case SSS -> 750_000L;
        };
    }
}
