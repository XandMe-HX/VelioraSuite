package id.velioragardens.veliorasuite.config;

import id.velioragardens.veliorasuite.VelioraSuite;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigManager {

    private final VelioraSuite plugin;
    private final Map<String, ConfigFile> files = new LinkedHashMap<>();

    public ConfigManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        register("messages", "messages.yml");

        register("anti", "modules/anti.yml");
        register("clearlag", "modules/clearlag.yml");
        register("quest", "modules/quest.yml");
        register("skills", "modules/skills.yml");
        register("trader", "modules/trader.yml");
        register("fishing", "modules/fishing.yml");
        register("boss", "modules/boss.yml");
        register("rewards", "modules/rewards.yml");
        register("chat", "modules/chat.yml");
        register("team", "modules/team.yml");
        register("guide", "modules/guide.yml");
        register("security", "modules/security.yml");
        register("login", "modules/login.yml");
        register("report", "modules/report.yml");
        register("announcement", "modules/announcement.yml");
        register("kits", "modules/kits.yml");
    }

    public void reloadAll() {
        for (ConfigFile file : files.values()) {
            file.reload();
        }
    }

    private void register(String key, String path) {
        files.put(key.toLowerCase(), new ConfigFile(plugin, path));
    }

    public ConfigFile getFile(String key) {
        return files.get(key.toLowerCase());
    }

    public Map<String, ConfigFile> getFiles() {
        return files;
    }
}
