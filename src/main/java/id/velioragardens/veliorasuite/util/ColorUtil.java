package id.velioragardens.veliorasuite.util;

import org.bukkit.ChatColor;

public final class ColorUtil {

    private ColorUtil() {
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
