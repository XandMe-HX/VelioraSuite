package id.velioragardens.veliorasuite.module.warp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;

public final class WarpListener implements Listener {
    private final WarpManager manager;

    public WarpListener(WarpManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDynamicWarp(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String raw = event.getMessage().substring(1).trim();
        if (raw.isEmpty() || raw.contains(" ")) return;
        String label = raw.toLowerCase(Locale.ROOT);
        if (!manager.hasDirectAlias(label) || manager.hasExternalCommand(label)) return;
        event.setCancelled(true);
        manager.teleport(event.getPlayer(), label);
    }

    @EventHandler
    public void onCommands(PlayerCommandSendEvent event) {
        for (String name : manager.directNames()) {
            if (!manager.hasExternalCommand(name)) event.getCommands().add(name);
        }
    }
}
