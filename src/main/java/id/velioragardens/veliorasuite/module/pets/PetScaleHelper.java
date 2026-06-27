package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

public final class PetScaleHelper {
    private final VelioraSuite plugin;
    private boolean warned;

    public PetScaleHelper(VelioraSuite plugin) { this.plugin = plugin; }

    public void apply(LivingEntity entity, double scale) {
        Attribute attribute = attribute("SCALE", "GENERIC_SCALE");
        if (attribute == null || entity.getAttribute(attribute) == null) { warnOnce(); return; }
        AttributeInstance instance = entity.getAttribute(attribute);
        instance.setBaseValue(scale);
    }

    private Attribute attribute(String... names) {
        for (String name : names) {
            try { return Attribute.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { }
        }
        return null;
    }

    private void warnOnce() {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("VelioraPets: Attribute SCALE tidak tersedia, pet tetap ukuran normal.");
    }
}
