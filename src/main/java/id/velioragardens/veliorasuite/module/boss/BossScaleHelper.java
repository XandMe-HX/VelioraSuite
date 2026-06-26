package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

public final class BossScaleHelper {

    private final VelioraSuite plugin;
    private boolean warned;

    public BossScaleHelper(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void apply(LivingEntity entity, double scale) {
        if (entity == null || scale <= 1.0D) return;
        Attribute attribute = scaleAttribute();
        if (attribute == null) {
            warnOnce();
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            warnOnce();
            return;
        }
        instance.setBaseValue(scale);
    }

    public void setMaxHealth(LivingEntity entity, double health) {
        if (entity == null) return;
        Attribute attribute = attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
        if (attribute != null && entity.getAttribute(attribute) != null) entity.getAttribute(attribute).setBaseValue(health);
        try { entity.setHealth(Math.min(health, entity.getMaxHealth())); } catch (Exception ignored) { }
    }

    private Attribute scaleAttribute() { return attribute("SCALE", "GENERIC_SCALE"); }

    private Attribute attribute(String... names) {
        for (String name : names) {
            try { return Attribute.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { }
        }
        return null;
    }

    private void warnOnce() {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("VelioraBoss: Attribute SCALE tidak tersedia, boss tetap spawn ukuran normal.");
    }
}
