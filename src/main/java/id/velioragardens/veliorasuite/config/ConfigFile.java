package id.velioragardens.veliorasuite.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ConfigFile {

    private final JavaPlugin plugin;
    private final String path;
    private File file;
    private FileConfiguration config;

    public ConfigFile(JavaPlugin plugin, String path) {
        this.plugin = plugin;
        this.path = path;
        reload();
    }

    public void reload() {
        this.file = new File(plugin.getDataFolder(), path);

        if (!file.exists()) {
            plugin.saveResource(path, false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration get() {
        return config;
    }

    public File getFile() {
        return file;
    }

    public String getPath() {
        return path;
    }
}
