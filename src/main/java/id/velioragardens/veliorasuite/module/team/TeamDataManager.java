package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import id.velioragardens.veliorasuite.module.team.model.Team;
import id.velioragardens.veliorasuite.module.team.model.TeamMember;
import id.velioragardens.veliorasuite.module.team.model.TeamRole;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TeamDataManager {

    private final VelioraSuite plugin;
    private final Map<String, Team> teamsByName = new LinkedHashMap<>();
    private File file;
    private FileConfiguration data;
    private BufferedYamlWriter writer;

    public TeamDataManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.createFolder("data");
        this.file = new File(plugin.getDataFolder(), "data/teams.yml");

        if (!file.exists()) {
            try {
                boolean created = file.createNewFile();
                if (!created) {
                    plugin.getLogger().warning("VelioraTeam: teams.yml sudah ada atau gagal dibuat.");
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("VelioraTeam: gagal membuat teams.yml: " + exception.getMessage());
            }
        }

        try {
            this.data = YamlConfiguration.loadConfiguration(file);
            if (!data.contains("last-id")) {
                data.set("last-id", 0);
            }
            if (!data.contains("teams")) {
                data.createSection("teams");
            }
            loadTeamsFromData();
            save();
            writer = new BufferedYamlWriter(plugin, file, data, "data/teams.yml");
            writer.start();
        } catch (Exception exception) {
            plugin.getLogger().severe("VelioraTeam: data/teams.yml rusak atau gagal dibaca. Fallback data kosong. Error: " + exception.getMessage());
            this.data = new YamlConfiguration();
            data.set("last-id", 0);
            data.createSection("teams");
            teamsByName.clear();
        }
    }

    public void save() {
        if (data == null || file == null) {
            return;
        }
        if (writer == null) {
            try { data.save(file); } catch (IOException exception) { plugin.getLogger().severe("VelioraTeam: gagal menyimpan teams.yml: " + exception.getMessage()); }
            return;
        }
        writer.markDirty();
    }

    public void shutdown() { if (writer != null) writer.shutdown(); }

    public int nextId() {
        int nextId = data.getInt("last-id", 0) + 1;
        data.set("last-id", nextId);
        save();
        return nextId;
    }

    public void saveTeam(Team team) {
        if (team == null) {
            return;
        }

        teamsByName.put(normalize(team.getName()), team);
        String path = "teams." + team.getId();
        data.set(path + ".name", team.getName());
        data.set(path + ".display-name", team.getDisplayName());
        data.set(path + ".owner-uuid", team.getOwnerUuid() == null ? "" : team.getOwnerUuid().toString());
        data.set(path + ".owner-name", team.getOwnerName());
        data.set(path + ".max-members", team.getMaxMembers());
        data.set(path + ".upgraded", team.isUpgraded());
        data.set(path + ".created-at", team.getCreatedAt());
        data.set(path + ".last-active", team.getLastActive());
        data.set(path + ".members", null);

        for (TeamMember member : team.getMembers().values()) {
            String memberPath = path + ".members." + member.getUuid();
            data.set(memberPath + ".name", member.getName());
            data.set(memberPath + ".role", member.getRole().name());
            data.set(memberPath + ".joined-at", member.getJoinedAt());
        }

        save();
    }

    public void deleteTeam(Team team) {
        if (team == null) {
            return;
        }

        teamsByName.remove(normalize(team.getName()));
        data.set("teams." + team.getId(), null);
        save();
    }

    public Team getTeam(String name) {
        if (name == null) {
            return null;
        }
        return teamsByName.get(normalize(name));
    }

    public Team getTeamByPlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        for (Team team : teamsByName.values()) {
            if (team.isMember(uuid)) {
                return team;
            }
        }

        return null;
    }

    public boolean teamExists(String name) {
        return getTeam(name) != null;
    }

    public Collection<Team> getTeams() {
        return teamsByName.values().stream()
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<String> getTeamNames() {
        List<String> names = new ArrayList<>();
        for (Team team : getTeams()) {
            names.add(team.getName());
        }
        return names;
    }

    private void loadTeamsFromData() {
        teamsByName.clear();
        ConfigurationSection section = data.getConfigurationSection("teams");

        if (section == null) {
            return;
        }

        for (String rawId : section.getKeys(false)) {
            int id = safeInt(rawId);
            if (id <= 0) {
                continue;
            }

            Team team = readTeam("teams." + rawId, id);
            if (team != null) {
                teamsByName.put(normalize(team.getName()), team);
            }
        }
    }

    private Team readTeam(String path, int id) {
        try {
            String name = data.getString(path + ".name", "Team" + id);
            Team team = new Team(
                    id,
                    name,
                    data.getString(path + ".display-name", name),
                    parseUuid(data.getString(path + ".owner-uuid", "")),
                    data.getString(path + ".owner-name", "Unknown"),
                    data.getInt(path + ".max-members", 5),
                    data.getBoolean(path + ".upgraded", false),
                    data.getString(path + ".created-at", "-"),
                    data.getString(path + ".last-active", "")
            );

            ConfigurationSection membersSection = data.getConfigurationSection(path + ".members");
            if (membersSection != null) {
                for (String rawUuid : membersSection.getKeys(false)) {
                    UUID uuid = parseUuid(rawUuid);
                    if (uuid == null) {
                        continue;
                    }
                    String memberPath = path + ".members." + rawUuid;
                    team.addMember(new TeamMember(
                            uuid,
                            data.getString(memberPath + ".name", "Unknown"),
                            TeamRole.fromString(data.getString(memberPath + ".role", "MEMBER")),
                            data.getString(memberPath + ".joined-at", "-")
                    ));
                }
            }

            return team;
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraTeam: gagal membaca team #" + id + ". Team dilewati. Error: " + exception.getMessage());
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private int safeInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
