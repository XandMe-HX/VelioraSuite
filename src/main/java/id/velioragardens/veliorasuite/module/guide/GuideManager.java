package id.velioragardens.veliorasuite.module.guide;

import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class GuideManager {

    private final ConfigFile configFile;

    public GuideManager(ConfigFile configFile) {
        this.configFile = configFile;
    }

    public void reload() {
        configFile.reload();
    }

    public void send(CommandSender sender, GuideType type, int page) {
        Map<Integer, GuidePage> pages = getPages(type);
        if (pages.isEmpty()) {
            sendMessage(sender, message("general.no-pages", Map.of("type", type.getKey())));
            return;
        }

        int targetPage = page;
        if (!pages.containsKey(targetPage)) {
            int defaultPage = configFile.get().getInt("guides." + type.getKey() + ".settings.default-page", 1);
            targetPage = pages.containsKey(defaultPage) ? defaultPage : pages.keySet().iterator().next();
        }

        if (configFile.get().getBoolean("navigation.clear-chat-before-page", true)) {
            int blankLines = Math.max(0, configFile.get().getInt("navigation.clear-chat-lines", 20));
            for (int i = 0; i < blankLines; i++) sendMessage(sender, "");
        }

        GuidePage guidePage = pages.get(targetPage);
        for (String line : guidePage.getLines()) {
            sendMessage(sender, line);
        }

        if (configFile.get().getBoolean("navigation.enabled", true)) {
            sendNavigation(sender, type, targetPage, Collections.max(pages.keySet()));
        }
    }

    public Map<Integer, GuidePage> getPages(GuideType type) {
        Map<Integer, GuidePage> pages = new TreeMap<>();
        ConfigurationSection section = configFile.get().getConfigurationSection("guides." + type.getKey() + ".pages");
        if (section == null) {
            return pages;
        }

        for (String key : section.getKeys(false)) {
            try {
                int number = Integer.parseInt(key);
                String path = "guides." + type.getKey() + ".pages." + key;
                String title = configFile.get().getString(path + ".title", type.getKey() + " page " + number);
                List<String> lines = configFile.get().getStringList(path + ".lines");
                pages.put(number, new GuidePage(number, title, lines));
            } catch (NumberFormatException ignored) {
            }
        }

        return pages;
    }

    private void sendNavigation(CommandSender sender, GuideType type, int page, int maxPage) {
        String command = "/" + type.getShortCommand();
        boolean clickable = configFile.get().getBoolean("navigation.clickable-java", true);
        boolean manual = configFile.get().getBoolean("navigation.show-manual-command", true);
        int previous = Math.max(1, page - 1);
        int next = Math.min(maxPage, page + 1);

        sendMessage(sender, "");

        if (sender instanceof Player player && clickable) {
            TextComponent root = new TextComponent("");

            TextComponent back = component(configFile.get().getString("navigation.buttons.back", "&8[&e< Back&8]"), command + " " + previous, "Klik untuk halaman sebelumnya");
            TextComponent middle = new TextComponent(ColorUtil.color(" &7Page &f" + page + "&7/&f" + maxPage + " "));
            TextComponent nextButton = component(configFile.get().getString("navigation.buttons.next", "&8[&aNext >&8]"), command + " " + next, "Klik untuk halaman berikutnya");

            root.addExtra(back);
            root.addExtra(middle);
            root.addExtra(nextButton);
            player.spigot().sendMessage(root);
        } else {
            sendMessage(sender, "&7Page &f" + page + "&7/&f" + maxPage);
        }

        if (manual) {
            sendMessage(sender, "&8Java: klik tombol. Bedrock: &f" + command + " <halaman>");
        }
    }

    private TextComponent component(String text, String command, String hover) {
        TextComponent component = new TextComponent(ColorUtil.color(text));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ColorUtil.color("&f" + hover + "\n&7" + command))));
        return component;
    }

    public List<String> tabPages(GuideType type, String input) {
        List<String> result = new ArrayList<>();
        for (Integer page : getPages(type).keySet()) {
            String value = String.valueOf(page);
            if (value.startsWith(input)) {
                result.add(value);
            }
        }
        return result;
    }

    public String message(String key, Map<String, String> placeholders) {
        String prefix = configFile.get().getString("messages.prefix", "&8[&aVelioraGuide&8]");
        String message = configFile.get().getString("messages." + key, "&cMessage not found: " + key);
        message = message.replace("%prefix%", prefix);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return message;
    }

    private void sendMessage(CommandSender sender, String text) {
        sender.sendMessage(ColorUtil.color(text));
    }
}
