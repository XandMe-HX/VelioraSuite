package id.velioragardens.veliorasuite.module.guide;

import id.velioragardens.veliorasuite.VelioraSuite;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class GuideManager {

    private final VelioraSuite plugin;
    private final Map<String, TreeMap<Integer, GuidePage>> pages = new HashMap<>();

    private FileConfiguration config;

    public GuideManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "modules/guide.yml");

        if (!file.exists()) {
            plugin.saveResource("modules/guide.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        this.pages.clear();

        ConfigurationSection sections = config.getConfigurationSection("sections");
        if (sections == null) {
            plugin.getLogger().warning("VelioraGuide: section 'sections' tidak ditemukan di guide.yml.");
            return;
        }

        for (String sectionName : sections.getKeys(false)) {
            TreeMap<Integer, GuidePage> sectionPages = new TreeMap<>();
            ConfigurationSection pageSection = sections.getConfigurationSection(sectionName + ".pages");

            if (pageSection == null) {
                continue;
            }

            for (String rawPage : pageSection.getKeys(false)) {
                int pageNumber;

                try {
                    pageNumber = Integer.parseInt(rawPage);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                String path = sectionName + ".pages." + rawPage;
                String title = sections.getString(path + ".title", "Page " + pageNumber);
                sectionPages.put(pageNumber, new GuidePage(
                        pageNumber,
                        title,
                        sections.getStringList(path + ".lines")
                ));
            }

            pages.put(sectionName.toLowerCase(Locale.ROOT), sectionPages);
        }

        plugin.getLogger().info("VelioraGuide loaded with " + pages.size() + " section(s).");
    }

    public void reload() {
        load();
    }

    public List<String> getPageSuggestions(String sectionName) {
        TreeMap<Integer, GuidePage> sectionPages = pages.get(sectionName.toLowerCase(Locale.ROOT));
        List<String> suggestions = new ArrayList<>();

        if (sectionPages == null || sectionPages.isEmpty()) {
            suggestions.add("1");
            return suggestions;
        }

        for (Integer pageNumber : sectionPages.keySet()) {
            suggestions.add(String.valueOf(pageNumber));
        }

        return suggestions;
    }

    public void sendPage(CommandSender sender, String sectionName, int pageNumber, String commandLabel) {
        TreeMap<Integer, GuidePage> sectionPages = pages.get(sectionName.toLowerCase(Locale.ROOT));

        if (sectionPages == null || sectionPages.isEmpty()) {
            send(sender, getMessage("page-not-found").replace("%page%", String.valueOf(pageNumber)));
            return;
        }

        GuidePage page = sectionPages.get(pageNumber);
        int maxPage = sectionPages.lastKey();

        if (page == null) {
            send(sender, getMessage("page-not-found").replace("%page%", String.valueOf(pageNumber)));
            return;
        }

        if (config.getBoolean("settings.clear-chat-before-page", false)) {
            int clearLines = Math.max(0, config.getInt("settings.clear-chat-lines", 0));
            for (int i = 0; i < clearLines; i++) {
                sender.sendMessage("");
            }
        }

        for (String line : page.getLines()) {
            sender.sendMessage(color(line
                    .replace("%page%", String.valueOf(pageNumber))
                    .replace("%max_page%", String.valueOf(maxPage))
                    .replace("%title%", page.getTitle())));
        }

        if (config.getBoolean("settings.show-navigation", true)) {
            sendNavigation(sender, commandLabel, pageNumber, maxPage);
        }
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, getMessage("reload-success"));
    }

    public void sendInvalidPage(CommandSender sender) {
        send(sender, getMessage("invalid-page"));
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, getMessage("no-permission"));
    }

    private void sendNavigation(CommandSender sender, String commandLabel, int page, int maxPage) {
        boolean hasBack = page > 1;
        boolean hasNext = page < maxPage;
        int previousPage = Math.max(1, page - 1);
        int nextPage = Math.min(maxPage, page + 1);

        sender.sendMessage(color("&8&m--------------------------------"));

        if (sender instanceof Player player && config.getBoolean("settings.clickable-buttons-java", true)) {
            TextComponent back = new TextComponent(color(hasBack ? "&e[Back]" : "&7[Back]"));
            if (hasBack) {
                back.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + commandLabel + " " + previousPage));
            }

            TextComponent info = new TextComponent(color(" &7Page &f" + page + "&7/&f" + maxPage + " "));

            TextComponent next = new TextComponent(color(hasNext ? "&a[Next]" : "&7[Next]"));
            if (hasNext) {
                next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + commandLabel + " " + nextPage));
            }

            player.spigot().sendMessage(back, info, next);
        } else {
            String back = hasBack ? "&e[Back]" : "&7[Back]";
            String next = hasNext ? "&a[Next]" : "&7[Next]";
            sender.sendMessage(color(back + " &7Page &f" + page + "&7/&f" + maxPage + " " + next));
        }

        if (config.getBoolean("settings.bedrock-navigation-hint", true)) {
            sender.sendMessage(color("&7Java: klik tombol. Bedrock: &f/" + commandLabel + " <halaman>"));
        }
    }

    private String getMessage(String path) {
        return config.getString("messages." + path, "&8【&aVelioraGuide&8】 &cMessage not found: " + path);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
