package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** One-time migration for base health written by the removed legacy skills module. */
public final class LegacyHealthCleanupListener implements Listener {
    private static final double VANILLA_BASE_HEALTH = 20.0D;

    private final File file;
    private final YamlConfiguration data;

    public LegacyHealthCleanupListener(VelioraSuite plugin) {
        file = new File(plugin.getDataFolder(), "data/legacy-health-cleanup.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uniqueId = player.getUniqueId();
        String path = "cleaned." + uniqueId;
        if (data.getBoolean(path, false)) return;

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() > VANILLA_BASE_HEALTH) {
            maxHealth.setBaseValue(VANILLA_BASE_HEALTH);
            if (player.getHealth() > maxHealth.getValue()) player.setHealth(maxHealth.getValue());
            player.sendMessage("§a[Veliora] §7Base health lama telah dikembalikan ke §f20§7.");
        }

        data.set(path, true);
        save();
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            data.save(file);
        } catch (IOException ignored) {
            // A later join can safely retry this one-time migration.
        }
    }
}
