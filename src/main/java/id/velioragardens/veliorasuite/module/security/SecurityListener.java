package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.config.ConfigFile;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.List;
import java.util.Locale;

public final class SecurityListener implements Listener {

    private final ConfigFile configFile;
    private int blockedCount;

    public SecurityListener(ConfigFile configFile) {
        this.configFile = configFile;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!configFile.get().getBoolean("protection.block-commands.enabled", true)) return;
        if (event.getPlayer().hasPermission(configFile.get().getString("settings.bypass-permission", "veliorasuite.security.bypass"))) return;

        String command = normalize(event.getMessage());
        if (isBlocked(command)) {
            event.setCancelled(true);
            blockedCount++;
            event.getPlayer().sendMessage(ColorUtil.color(configFile.get().getString("messages.blocked-command", "&8【&aVelioraSecurity&8】 &cCommand ini tidak bisa digunakan.")));
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!configFile.get().getBoolean("tab-complete.enabled", true)) return;
        if (event.getPlayer().hasPermission(configFile.get().getString("settings.bypass-permission", "veliorasuite.security.bypass"))) return;

        List<String> allowed = configFile.get().getStringList("tab-complete.allowed-commands");
        event.getCommands().removeIf(command -> !allowed.contains(command.toLowerCase(Locale.ROOT)));
    }

    public boolean isBlocked(String command) {
        String lower = command.toLowerCase(Locale.ROOT);

        for (String exact : configFile.get().getStringList("protection.block-commands.exact")) {
            if (lower.equals(exact.toLowerCase(Locale.ROOT).replaceFirst("^/", ""))) return true;
        }

        for (String prefix : configFile.get().getStringList("protection.block-commands.prefixes")) {
            String clean = prefix.toLowerCase(Locale.ROOT).replaceFirst("^/", "");
            if (lower.startsWith(clean)) return true;
        }

        return false;
    }

    private String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("/")) value = value.substring(1);
        int space = value.indexOf(' ');
        return space == -1 ? value : value.substring(0, space);
    }

    public int getBlockedCount() {
        return blockedCount;
    }
}
