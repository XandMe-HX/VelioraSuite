package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

public final class PetScaleHelper {
    private static final double MIN_VISIBLE_PET_SCALE = 0.65D;
    private final VelioraSuite plugin;
    private boolean warned;

    public PetScaleHelper(VelioraSuite plugin) { this.plugin = plugin; }

    public void apply(LivingEntity entity, double scale) {
        Attribute attribute = attribute("SCALE", "GENERIC_SCALE");
        if (attribute == null || entity.getAttribute(attribute) == null) { warnOnce(); return; }
        AttributeInstance instance = entity.getAttribute(attribute);
        instance.setBaseValue(normalizePetScale(scale));
    }

    private double normalizePetScale(double scale) {
        if (scale <= 0.0D) return MIN_VISIBLE_PET_SCALE;
        if (scale < MIN_VISIBLE_PET_SCALE) return MIN_VISIBLE_PET_SCALE;
        return Math.min(2.5D, scale);
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
