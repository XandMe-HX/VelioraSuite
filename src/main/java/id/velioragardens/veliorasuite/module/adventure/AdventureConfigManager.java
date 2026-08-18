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

public final class AdventureConfigManager {
    private final VelioraSuite plugin;
    private YamlConfiguration config;
    private List<AdventureQuestTemplate> templates = List.of();
    private final Map<AdventureRank, Long> rankRequirements = new EnumMap<>(AdventureRank.class);

    public AdventureConfigManager(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        plugin.saveResourceIfNotExists("modules/adventure.yml");
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/adventure.yml"));
        loadRanks();
        loadTemplates();
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
    public String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
    public int dailyQuestCount() { return Math.max(1, Math.min(9, config.getInt("daily.quest-count", 5))); }
    public int minimumOnlineMembers() { return Math.max(2, config.getInt("team.minimum-online-members", 2)); }
    public int coordinateMin() { return config.getInt("locations.min", -2000); }
    public int coordinateMax() { return config.getInt("locations.max", 2000); }
    public String questWorld() { return config.getString("locations.world", "world"); }
    public int exploreRadius() { return Math.max(5, config.getInt("locations.completion-radius", 20)); }
    public int activationRadius() { return Math.max(16, config.getInt("locations.mob-activation-radius", 48)); }
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
