package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class MessageManager {

    private final VelioraSuite plugin;
    private FileConfiguration messages;

    public MessageManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResourceIfNotExists("messages.yml");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String prefix = messages.getString("prefix", "&8【&aVelioraSuite&8】");
        String message = messages.getString(path, "%prefix% &cMessage tidak ditemukan: " + path);
        return color(message.replace("%prefix%", prefix));
    }

    public String get(String path, Map<String, String> placeholders) {
        String message = get(path);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return message;
    }

    public List<String> getList(String path) {
        String prefix = messages.getString("prefix", "&8【&aVelioraSuite&8】");

        return messages.getStringList(path).stream()
                .map(line -> color(line.replace("%prefix%", prefix)))
                .toList();
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void sendRaw(CommandSender sender, String message) {
        String prefix = messages.getString("prefix", "&8【&aVelioraSuite&8】");
        sender.sendMessage(color(message.replace("%prefix%", prefix)));
    }

    public void sendList(CommandSender sender, String path) {
        for (String line : getList(path)) {
            sender.sendMessage(line);
        }
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
