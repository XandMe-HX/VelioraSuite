package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TeamManager {

    private final VelioraSuite plugin;
    private final ConfigFile configFile;
    private final Map<UUID, Long> pendingLeave = new HashMap<>();

    private File dataFile;
    private FileConfiguration data;

    public TeamManager(VelioraSuite plugin, ConfigFile configFile) {
        this.plugin = plugin;
        this.configFile = configFile;
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Gagal membuat folder data VelioraTeam.");
        }

        dataFile = new File(folder, "teams.yml");
        if (!dataFile.exists()) {
            try {
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("File teams.yml sudah ada tapi tidak terbaca.");
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Gagal membuat teams.yml: " + exception.getMessage());
            }
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.isConfigurationSection("teams")) {
            data.createSection("teams");
            save();
        }
    }

    public void save() {
        if (data == null || dataFile == null) {
            return;
        }

        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Gagal menyimpan teams.yml: " + exception.getMessage());
        }
    }

    public boolean createTeam(Player owner, String rawName) {
        String name = normalizeTeamName(rawName);
        int createCost = config().getInt("settings.create-cost", 250000);

        if (getTeamByPlayer(owner.getUniqueId()) != null) {
            message(owner, "create.already-in-team", Map.of());
            return false;
        }

        if (!isValidTeamName(rawName)) {
            message(owner, "create.invalid-name", Map.of(
                    "min", String.valueOf(config().getInt("settings.name.min-length", 5)),
                    "max", String.valueOf(config().getInt("settings.name.max-length", 6))
            ));
            return false;
        }

        if (isBlockedName(name)) {
            message(owner, "create.blocked-name", Map.of("team", name));
            return false;
        }

        if (teamExists(name)) {
            message(owner, "create.already-exists", Map.of("team", name));
            return false;
        }

        if (!pay(owner, createCost)) {
            message(owner, "create.not-enough-money", Map.of("cost", formatMoney(createCost)));
            return false;
        }

        int defaultMax = config().getInt("settings.limits.default-max-members", 5);
        String path = "teams." + name;
        data.set(path + ".owner", owner.getUniqueId().toString());
        data.set(path + ".owner-name", owner.getName());
        data.set(path + ".admins", new ArrayList<String>());
        data.set(path + ".members", new ArrayList<String>());
        data.set(path + ".level", 1);
        data.set(path + ".max-members", defaultMax);
        data.set(path + ".created-at", System.currentTimeMillis());
        save();

        message(owner, "create.success", Map.of("team", name, "cost", formatMoney(createCost)));
        return true;
    }

    public boolean upgradeTeam(Player player, String rawTeamName) {
        Team team = rawTeamName == null ? getTeamByPlayer(player.getUniqueId()) : getTeam(rawTeamName);
        int upgradeCost = config().getInt("settings.upgrade-cost", 500000);

        if (team == null) {
            message(player, "general.team-not-found", Map.of());
            return false;
        }

        if (!canUpgrade(player.getUniqueId(), team)) {
            message(player, "upgrade.not-owner-or-admin", Map.of("team", team.getName()));
            return false;
        }

        int maxLevel = config().getInt("settings.limits.max-upgrade-level", 2);
        if (team.getLevel() >= maxLevel) {
            message(player, "upgrade.max-level", Map.of("team", team.getName()));
            return false;
        }

        if (!pay(player, upgradeCost)) {
            message(player, "upgrade.not-enough-money", Map.of("cost", formatMoney(upgradeCost)));
            return false;
        }

        int upgradedMax = config().getInt("settings.limits.upgraded-max-members", 10);
        String path = "teams." + team.getName();
        data.set(path + ".level", team.getLevel() + 1);
        data.set(path + ".max-members", upgradedMax);
        save();

        message(player, "upgrade.success", Map.of(
                "team", team.getName(),
                "max_members", String.valueOf(upgradedMax),
                "cost", formatMoney(upgradeCost)
        ));
        return true;
    }

    public boolean addAdmin(Player actor, String targetName) {
        Team team = getTeamByPlayer(actor.getUniqueId());
        OfflinePlayer target = findTarget(targetName);

        if (team == null) {
            message(actor, "member.not-in-team", Map.of());
            return false;
        }

        if (!team.isOwner(actor.getUniqueId())) {
            message(actor, "member.owner-only", Map.of());
            return false;
        }

        if (target == null) {
            message(actor, "member.target-not-found", Map.of("player", targetName));
            return false;
        }

        if (getTeamByPlayer(target.getUniqueId()) != null) {
            message(actor, "member.target-already-in-team", Map.of("player", target.getName() == null ? targetName : target.getName()));
            return false;
        }

        if (team.getTotalMembers() >= team.getMaxMembers()) {
            message(actor, "member.team-full", Map.of("team", team.getName()));
            return false;
        }

        List<String> admins = data.getStringList("teams." + team.getName() + ".admins");
        admins.add(target.getUniqueId().toString());
        data.set("teams." + team.getName() + ".admins", admins);
        save();

        String name = target.getName() == null ? targetName : target.getName();
        message(actor, "member.admin-add-success", Map.of("player", name, "team", team.getName()));
        notifyTarget(target, "member.you-added-admin", Map.of("team", team.getName()));
        return true;
    }

    public boolean addMember(Player actor, String targetName) {
        Team team = getTeamByPlayer(actor.getUniqueId());
        OfflinePlayer target = findTarget(targetName);

        if (team == null) {
            message(actor, "member.not-in-team", Map.of());
            return false;
        }

        if (!team.isOwner(actor.getUniqueId()) && !team.isAdmin(actor.getUniqueId())) {
            message(actor, "member.no-permission-team", Map.of("team", team.getName()));
            return false;
        }

        if (target == null) {
            message(actor, "member.target-not-found", Map.of("player", targetName));
            return false;
        }

        if (getTeamByPlayer(target.getUniqueId()) != null) {
            message(actor, "member.target-already-in-team", Map.of("player", target.getName() == null ? targetName : target.getName()));
            return false;
        }

        if (team.getTotalMembers() >= team.getMaxMembers()) {
            message(actor, "member.team-full", Map.of("team", team.getName()));
            return false;
        }

        List<String> members = data.getStringList("teams." + team.getName() + ".members");
        members.add(target.getUniqueId().toString());
        data.set("teams." + team.getName() + ".members", members);
        save();

        String name = target.getName() == null ? targetName : target.getName();
        message(actor, "member.add-success", Map.of("player", name, "team", team.getName()));
        notifyTarget(target, "member.you-added-member", Map.of("team", team.getName()));
        return true;
    }

    public boolean kickMember(Player actor, String targetName) {
        Team team = getTeamByPlayer(actor.getUniqueId());
        OfflinePlayer target = findTarget(targetName);

        if (team == null) {
            message(actor, "member.not-in-team", Map.of());
            return false;
        }

        if (!team.isOwner(actor.getUniqueId())) {
            message(actor, "member.owner-only", Map.of());
            return false;
        }

        if (target == null) {
            message(actor, "member.target-not-found", Map.of("player", targetName));
            return false;
        }

        if (team.isOwner(target.getUniqueId())) {
            message(actor, "kick.owner-cannot-kick-self", Map.of());
            return false;
        }

        boolean removed = removeFromList("teams." + team.getName() + ".admins", target.getUniqueId())
                | removeFromList("teams." + team.getName() + ".members", target.getUniqueId());

        if (!removed) {
            message(actor, "kick.not-in-your-team", Map.of("player", targetName));
            return false;
        }

        save();
        String name = target.getName() == null ? targetName : target.getName();
        message(actor, "kick.success", Map.of("player", name, "team", team.getName()));
        notifyTarget(target, "kick.you-kicked", Map.of("team", team.getName()));
        return true;
    }

    public void requestLeave(Player player) {
        Team team = getTeamByPlayer(player.getUniqueId());
        if (team == null) {
            message(player, "member.not-in-team", Map.of());
            return;
        }

        if (team.isOwner(player.getUniqueId()) && config().getBoolean("settings.leave.owner-cannot-leave", true)) {
            message(player, "leave.owner-cannot-leave", Map.of("team", team.getName()));
            return;
        }

        int seconds = config().getInt("settings.leave.confirm-timeout-seconds", 30);
        pendingLeave.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
        message(player, "leave.confirm", Map.of("team", team.getName(), "seconds", String.valueOf(seconds)));
    }

    public void confirmLeave(Player player) {
        Long expires = pendingLeave.get(player.getUniqueId());
        if (expires == null || expires < System.currentTimeMillis()) {
            pendingLeave.remove(player.getUniqueId());
            message(player, "leave.no-pending", Map.of());
            return;
        }

        Team team = getTeamByPlayer(player.getUniqueId());
        if (team == null) {
            pendingLeave.remove(player.getUniqueId());
            message(player, "member.not-in-team", Map.of());
            return;
        }

        removeFromList("teams." + team.getName() + ".admins", player.getUniqueId());
        removeFromList("teams." + team.getName() + ".members", player.getUniqueId());
        save();
        pendingLeave.remove(player.getUniqueId());
        message(player, "leave.success", Map.of("team", team.getName()));
    }

    public void cancelLeave(Player player) {
        if (pendingLeave.remove(player.getUniqueId()) != null) {
            message(player, "leave.cancelled", Map.of());
        } else {
            message(player, "leave.no-pending", Map.of());
        }
    }

    public Team getTeam(String rawName) {
        if (rawName == null) {
            return null;
        }

        String name = normalizeTeamName(rawName);
        ConfigurationSection section = data.getConfigurationSection("teams." + name);
        if (section == null) {
            return null;
        }

        String ownerString = section.getString("owner", "");
        if (ownerString.isEmpty()) {
            return null;
        }

        UUID owner = UUID.fromString(ownerString);
        List<UUID> admins = toUuidList(section.getStringList("admins"));
        List<UUID> members = toUuidList(section.getStringList("members"));
        int level = section.getInt("level", 1);
        int maxMembers = section.getInt("max-members", config().getInt("settings.limits.default-max-members", 5));

        return new Team(name, owner, admins, members, level, maxMembers);
    }

    public Team getTeamByPlayer(UUID uuid) {
        ConfigurationSection teams = data.getConfigurationSection("teams");
        if (teams == null) {
            return null;
        }

        for (String name : teams.getKeys(false)) {
            Team team = getTeam(name);
            if (team != null && team.isMember(uuid)) {
                return team;
            }
        }

        return null;
    }

    public Map<String, Team> getTeams() {
        Map<String, Team> teamsMap = new LinkedHashMap<>();
        ConfigurationSection teams = data.getConfigurationSection("teams");
        if (teams == null) {
            return teamsMap;
        }

        for (String name : teams.getKeys(false)) {
            Team team = getTeam(name);
            if (team != null) {
                teamsMap.put(name, team);
            }
        }

        return teamsMap;
    }

    public String getTeamPrefix(UUID uuid) {
        Team team = getTeamByPlayer(uuid);
        if (team == null) {
            return config().getString("chat.no-team-placeholder", "");
        }

        String format = config().getString("chat.format", "&8【&a%team%&8】 ");
        return ColorUtil.color(format.replace("%team%", team.getName()));
    }

    public void sendInfo(Player player) {
        Team team = getTeamByPlayer(player.getUniqueId());
        if (team == null) {
            message(player, "member.not-in-team", Map.of());
            return;
        }

        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
        player.sendMessage(ColorUtil.color("&aTeam: &f" + team.getName()));
        player.sendMessage(ColorUtil.color("&aRole: &f" + getRole(player.getUniqueId(), team)));
        player.sendMessage(ColorUtil.color("&aLevel: &f" + team.getLevel()));
        player.sendMessage(ColorUtil.color("&aMember: &f" + team.getTotalMembers() + "&7/&f" + team.getMaxMembers()));
        player.sendMessage(ColorUtil.color("&8&m------------------------------"));
    }

    public String getRole(UUID uuid, Team team) {
        if (team.isOwner(uuid)) {
            return "Owner";
        }
        if (team.isAdmin(uuid)) {
            return "Admin";
        }
        return "Member";
    }

    private boolean canUpgrade(UUID uuid, Team team) {
        boolean ownerCanUpgrade = config().getBoolean("permissions.owner-can-upgrade", true);
        boolean adminCanUpgrade = config().getBoolean("permissions.admin-can-upgrade", true);
        return (ownerCanUpgrade && team.isOwner(uuid)) || (adminCanUpgrade && team.isAdmin(uuid));
    }

    private boolean teamExists(String name) {
        return data.isConfigurationSection("teams." + normalizeTeamName(name));
    }

    private boolean isValidTeamName(String rawName) {
        if (rawName == null) {
            return false;
        }

        int min = config().getInt("settings.name.min-length", 5);
        int max = config().getInt("settings.name.max-length", 6);
        if (rawName.length() < min || rawName.length() > max) {
            return false;
        }

        boolean lettersOnly = config().getBoolean("settings.name.letters-only", true);
        if (lettersOnly && !rawName.matches("[A-Za-z]+")) {
            return false;
        }

        if (!config().getBoolean("settings.name.allow-numbers", false) && rawName.matches(".*[0-9].*")) {
            return false;
        }

        return config().getBoolean("settings.name.allow-symbols", false) || rawName.matches("[A-Za-z0-9]+") || lettersOnly;
    }

    private String normalizeTeamName(String rawName) {
        if (rawName == null) {
            return "";
        }

        String name = rawName.trim();
        if (config().getBoolean("settings.name.force-uppercase", true)) {
            name = name.toUpperCase(Locale.ROOT);
        }
        return name;
    }

    private boolean isBlockedName(String name) {
        if (!config().getBoolean("blacklist.enabled", true)) {
            return false;
        }

        String upper = normalizeTeamName(name).toUpperCase(Locale.ROOT);
        for (String blocked : config().getStringList("blacklist.blocked-names")) {
            if (upper.contains(blocked.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private OfflinePlayer findTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        if (!config().getBoolean("settings.allow-offline-targets", false)) {
            return null;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) {
            return null;
        }
        return offline;
    }

    private boolean pay(Player player, int amount) {
        if (!config().getBoolean("economy.enabled", true) || amount <= 0) {
            return true;
        }

        Economy economy = plugin.getHookManager().getEconomy();
        if (economy == null) {
            return !config().getBoolean("economy.require-vault", true);
        }

        if (economy.getBalance(player) < amount) {
            return false;
        }

        economy.withdrawPlayer(player, amount);
        return true;
    }

    private boolean removeFromList(String path, UUID uuid) {
        List<String> list = data.getStringList(path);
        boolean removed = list.remove(uuid.toString());
        data.set(path, list);
        return removed;
    }

    private List<UUID> toUuidList(List<String> strings) {
        List<UUID> uuids = new ArrayList<>();
        for (String string : strings) {
            try {
                uuids.add(UUID.fromString(string));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }

    private void notifyTarget(OfflinePlayer target, String key, Map<String, String> placeholders) {
        if (target instanceof Player player && player.isOnline()) {
            message(player, key, placeholders);
        }
    }


    public void sendTeamMessage(Player sender, String rawMessage) {
        Team team = getTeamByPlayer(sender.getUniqueId());
        if (team == null) {
            message(sender, "member.not-in-team", Map.of());
            return;
        }

        if (rawMessage == null || rawMessage.isBlank()) {
            sender.sendMessage(ColorUtil.color(config().getString("messages.team-chat.empty", "&8[&aVelioraTeam&8] &cPesan tidak boleh kosong.")));
            return;
        }

        String format = config().getString("messages.team-chat.format", "&8【&aTeam %team%&8】 &f%player%&7: &f%message%");
        String finalMessage = format
                .replace("%team%", team.getName())
                .replace("%player%", sender.getName())
                .replace("%message%", rawMessage);

        for (UUID uuid : team.getMembers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(ColorUtil.color(finalMessage));
            }
        }

        for (UUID uuid : team.getAdmins()) {
            Player admin = Bukkit.getPlayer(uuid);
            if (admin != null && admin.isOnline()) {
                admin.sendMessage(ColorUtil.color(finalMessage));
            }
        }

        Player owner = Bukkit.getPlayer(team.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ColorUtil.color(finalMessage));
        }
    }

    public void message(Player player, String key, Map<String, String> placeholders) {
        String prefix = config().getString("messages.prefix", "&8[&aVelioraTeam&8]");
        String message = config().getString("messages." + key, "&cMessage not found: " + key);
        message = message.replace("%prefix%", prefix);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        player.sendMessage(ColorUtil.color(message));
    }

    private String formatMoney(int amount) {
        return config().getString("economy.money-format", "%,d").formatted(amount);
    }

    private FileConfiguration config() {
        return configFile.get();
    }
}
