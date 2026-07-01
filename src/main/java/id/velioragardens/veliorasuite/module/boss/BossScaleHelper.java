package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

public final class BossScaleHelper {

    private final VelioraSuite plugin;
    private boolean warnedScale;

    public BossScaleHelper(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void apply(LivingEntity entity, double scale) {
        if (entity == null || scale <= 1.0D) return;
        Attribute attribute = scaleAttribute();
        if (attribute == null) {
            warnScaleOnce();
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            warnScaleOnce();
            return;
        }
        instance.setBaseValue(scale);
    }

    public void setMaxHealth(LivingEntity entity, double health) {
        if (entity == null) return;
        Attribute attribute = attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(health);
        try { entity.setHealth(Math.min(health, entity.getMaxHealth())); } catch (Exception ignored) { }
    }

    public void applyCombatDefense(LivingEntity entity, double armor, double toughness, double knockbackResistance) {
        if (entity == null) return;
        setAttribute(entity, armor, "ARMOR", "GENERIC_ARMOR");
        setAttribute(entity, toughness, "ARMOR_TOUGHNESS", "GENERIC_ARMOR_TOUGHNESS");
        setAttribute(entity, knockbackResistance, "KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE");
    }

    private void setAttribute(LivingEntity entity, double value, String... names) {
        Attribute attribute = attribute(names);
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private Attribute scaleAttribute() { return attribute("SCALE", "GENERIC_SCALE"); }

    private Attribute attribute(String... names) {
        for (String name : names) {
            try { return Attribute.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { }
        }
        return null;
    }

    private void warnScaleOnce() {
        if (warnedScale) return;
        warnedScale = true;
        plugin.getLogger().warning("VelioraBoss: Attribute SCALE tidak tersedia, boss tetap spawn ukuran normal.");
    }
}
