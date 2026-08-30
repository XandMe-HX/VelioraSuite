package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Uses Paper's native scale attribute; no packets, NMS, or repeating task is used. */
final class RaceScaleHelper {
    private final VelioraSuite plugin;
    private boolean warned;

    RaceScaleHelper(VelioraSuite plugin) { this.plugin = plugin; }

    void apply(Player player, double scale) {
        Attribute attribute = attribute();
        AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance == null) { warnOnce(); return; }
        instance.setBaseValue(Math.clamp(scale, 0.5D, 1.2D));
    }

    void reset(Player player) { apply(player, 1.0D); }

    private Attribute attribute() {
        for (String name : new String[]{"SCALE", "GENERIC_SCALE"}) {
            try { return Attribute.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

    private void warnOnce() {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("Race: Attribute SCALE tidak tersedia; bentuk player tetap normal.");
    }
}
