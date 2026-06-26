package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishDefinition;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class FishItemFactory {

    private final FishingConfigManager configManager;
    private final NamespacedKey idKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey priceKey;
    private final NamespacedKey originKey;
    private final NamespacedKey regionKey;

    public FishItemFactory(VelioraSuite plugin, FishingConfigManager configManager) {
        this.configManager = configManager;
        this.idKey = new NamespacedKey(plugin, "veliorafishing_id");
        this.rarityKey = new NamespacedKey(plugin, "veliorafishing_rarity");
        this.weightKey = new NamespacedKey(plugin, "veliorafishing_weight");
        this.priceKey = new NamespacedKey(plugin, "veliorafishing_price");
        this.originKey = new NamespacedKey(plugin, "veliorafishing_origin");
        this.regionKey = new NamespacedKey(plugin, "veliorafishing_region");
    }

    public ItemStack create(CaughtFish fish) {
        FishDefinition definition = configManager.getFishDefinition(fish.id());
        if (definition == null) definition = fallbackDefinition(fish);
        return create(definition, fish);
    }

    public ItemStack create(FishDefinition definition, CaughtFish fish) {
        Material material = itemMaterial(definition);
        ItemStack item = new ItemStack(material == null ? Material.COD : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta instanceof SkullMeta skullMeta && definition.headEnabled() && !definition.headTextureBase64().isBlank()) {
                applyHeadTexture(skullMeta, definition.headTextureBase64());
                meta = skullMeta;
            }
            meta.setDisplayName(configManager.color(fish.rarity().color() + fish.name()));
            meta.setLore(List.of(
                    configManager.color("&7Rarity: " + fish.rarity().color() + fish.rarity().displayName()),
                    configManager.color("&7Berat: &f" + formatWeight(fish.weight())),
                    configManager.color("&7Origin: &f" + fish.origin()),
                    configManager.color("&7Region: &f" + fish.region()),
                    configManager.color("&7Harga: &a" + fish.price())
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(idKey, PersistentDataType.STRING, fish.id());
            pdc.set(rarityKey, PersistentDataType.STRING, fish.rarity().name());
            pdc.set(weightKey, PersistentDataType.DOUBLE, fish.weight());
            pdc.set(priceKey, PersistentDataType.INTEGER, fish.price());
            pdc.set(originKey, PersistentDataType.STRING, fish.origin());
            pdc.set(regionKey, PersistentDataType.STRING, fish.region());
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isCustomFish(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public int price(ItemStack item) {
        if (!isCustomFish(item)) return 0;
        Integer price = item.getItemMeta().getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER);
        return price == null ? 0 : Math.max(0, price);
    }

    public String formatWeight(double weight) {
        if (weight >= 1000.0D) return String.format("%.3f ton", weight / 1000.0D);
        return String.format("%.1f kg", weight);
    }

    private FishDefinition fallbackDefinition(CaughtFish fish) {
        Material material = switch (fish.rarity()) {
            case TRASH -> Material.LEATHER_BOOTS;
            case VANILLA, COMMON -> Material.COD;
            case ORNAMENTAL -> Material.TROPICAL_FISH;
            case EPIC -> Material.SALMON;
            case LEGENDARY, MITOLOGI -> Material.PLAYER_HEAD;
        };
        return new FishDefinition(fish.id(), fish.name(), fish.rarity(), material, fish.weight(), fish.weight(), fish.price(), fish.price(), fish.origin(), fish.region(), fish.rarity() == FishRarity.LEGENDARY || fish.rarity() == FishRarity.MITOLOGI, "", Material.TROPICAL_FISH);
    }

    private Material itemMaterial(FishDefinition definition) {
        if ((definition.rarity() == FishRarity.LEGENDARY || definition.rarity() == FishRarity.MITOLOGI) && definition.headEnabled()) return Material.PLAYER_HEAD;
        return definition.material() == null ? definition.fallbackMaterial() : definition.material();
    }

    private void applyHeadTexture(SkullMeta skullMeta, String texture) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object profile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "VelioraFish");
            Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", texture);
            put.invoke(properties, "textures", property);
            Method setProfile = skullMeta.getClass().getDeclaredMethod("setProfile", gameProfileClass);
            setProfile.setAccessible(true);
            setProfile.invoke(skullMeta, profile);
        } catch (Exception first) {
            try {
                Field profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
            } catch (Exception ignored) {
                // Head texture is optional; fallback head remains safe.
            }
        }
    }
}
