package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishDefinition;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FishItemFactory {

    /*
     * Stable custom heads from minecraft-heads.com. They resolve only to Mojang's
     * textures CDN in the client, so fishing never downloads an image or calls an API.
     * A fish may still override this with head.texture-base64 in fishing.yml.
     */
    private static final String[] HIGH_TIER_FISH_TEXTURES = {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDBjZDcxZmJiYmJiNjZjN2JhZjc4ODFmNDE1YzY0ZmE4NGY2NTA0OTU4YTU3Y2NkYjg1ODkyNTI2NDdlYSJ9fX0=",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODVlYWY4N2NmYmMyM2NjZmNkYWUwZmM4ZGY4NDc3MTFhNmJlMDRiYjNjZWFmODBlMjcxZmRlZGZkMjUzNWU4In19fQ==",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQ1NWI5MTI5ZTVjZWY3MjAwOWE4MjcxYmFlZjc4NzQwYjk2YzQ4YjAyNWY4NjM2YjU1MTNmMTAzZWY5NzU0OCJ9fX0=",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWI3YThkMGQ3MzY1ZGU5NWYyM2FjMzc1NDk4MDI0Y2IzMWFlNGU4ZDZkMDZkMTJhNjIxZTQzY2YxYjc2NjdkNiJ9fX0=",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTEzZjA4NmNjYjU2MzIzZjIzOGJhMzQ4OWZmMmExYTM0YzBmZGNlZWFmYzQ4M2FjZmYwZTU0ODhjZmQ2YzJmMSJ9fX0=",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGZkYTc4ZDA2NWYxOThmNzcyMmM4MGY3ODdkM2VlZmY3NGI1M2FlMGY4ODdhOGRiZmJlNTA1OWU2MjM4YmQ3YiJ9fX0="
    };
    private static final Pattern TEXTURE_URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"(https?://[^\\\"]+)\\\"");

    private final FishingConfigManager configManager;
    private final NamespacedKey idKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey priceKey;
    private final NamespacedKey originKey;
    private final NamespacedKey regionKey;
    private final NamespacedKey mutationKey;
    private final NamespacedKey mutationMultiplierKey;

    public FishItemFactory(VelioraSuite plugin, FishingConfigManager configManager) {
        this.configManager = configManager;
        this.idKey = new NamespacedKey(plugin, "veliorafishing_id");
        this.rarityKey = new NamespacedKey(plugin, "veliorafishing_rarity");
        this.weightKey = new NamespacedKey(plugin, "veliorafishing_weight");
        this.priceKey = new NamespacedKey(plugin, "veliorafishing_price");
        this.originKey = new NamespacedKey(plugin, "veliorafishing_origin");
        this.regionKey = new NamespacedKey(plugin, "veliorafishing_region");
        this.mutationKey = new NamespacedKey(plugin, "veliorafishing_mutation");
        this.mutationMultiplierKey = new NamespacedKey(plugin, "veliorafishing_mutation_multiplier");
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
            if (meta instanceof SkullMeta skullMeta && definition.headEnabled()) {
                applyHeadTexture(skullMeta, effectiveHeadTexture(definition));
                meta = skullMeta;
            }
            meta.setDisplayName(configManager.color(fish.rarity().color() + fish.name()));
            meta.setLore(List.of(
                    configManager.color("&7Rarity: " + fish.rarity().color() + fish.rarity().displayName()),
                    configManager.color("&7Berat: &f" + formatWeight(fish.weight())),
                    configManager.color("&7Origin: &f" + fish.origin()),
                    configManager.color("&7Region: &f" + fish.region()),
                    configManager.color("&7Mutasi: &d" + fish.mutation() + " &7(x" + trimMultiplier(fish.mutationMultiplier()) + ")"),
                    configManager.color("&7Harga: &6" + configManager.formatCoins(fish.price()) + " Koin")
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(idKey, PersistentDataType.STRING, fish.id());
            pdc.set(rarityKey, PersistentDataType.STRING, fish.rarity().name());
            pdc.set(weightKey, PersistentDataType.DOUBLE, fish.weight());
            pdc.set(priceKey, PersistentDataType.INTEGER, fish.price());
            pdc.set(originKey, PersistentDataType.STRING, fish.origin());
            pdc.set(regionKey, PersistentDataType.STRING, fish.region());
            pdc.set(mutationKey, PersistentDataType.STRING, fish.mutation());
            pdc.set(mutationMultiplierKey, PersistentDataType.DOUBLE, fish.mutationMultiplier());
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isCustomFish(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public boolean isStorableFish(ItemStack item) {
        return isCustomFish(item)
                || (configManager.isVanillaFishSellAllowed() && item != null && configManager.isVanillaFish(item.getType()));
    }

    public CaughtFish read(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (isCustomFish(item)) return readCustomFish(item);
        if (configManager.isVanillaFishSellAllowed() && configManager.isVanillaFish(item.getType())) return readVanillaFish(item);
        return null;
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

    private CaughtFish readCustomFish(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(idKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) return null;

        FishDefinition definition = configManager.getFishDefinition(id);
        String rarityRaw = pdc.get(rarityKey, PersistentDataType.STRING);
        Double weight = pdc.get(weightKey, PersistentDataType.DOUBLE);
        Integer price = pdc.get(priceKey, PersistentDataType.INTEGER);
        String origin = pdc.get(originKey, PersistentDataType.STRING);
        String region = pdc.get(regionKey, PersistentDataType.STRING);
        String mutation = pdc.get(mutationKey, PersistentDataType.STRING);
        Double mutationMultiplier = pdc.get(mutationMultiplierKey, PersistentDataType.DOUBLE);

        return new CaughtFish(
                id,
                definition == null ? displayName(item, id) : definition.name(),
                FishRarity.fromKey(rarityRaw == null ? "COMMON" : rarityRaw),
                weight == null ? 0.0D : Math.max(0.0D, weight),
                price == null ? 0 : Math.max(0, price),
                origin == null || origin.isBlank() ? "VelioraFishing" : origin,
                region == null || region.isBlank() ? "Veliora" : region,
                mutation == null || mutation.isBlank() ? "Normal" : mutation,
                mutationMultiplier == null ? 1.0D : Math.max(1.0D, mutationMultiplier)
        );
    }

    private CaughtFish readVanillaFish(ItemStack item) {
        String id = "vanilla_" + item.getType().name().toLowerCase(Locale.ROOT);
        FishDefinition definition = configManager.getFishDefinition(id);
        return new CaughtFish(
                id,
                definition == null ? readableMaterialName(item.getType()) : definition.name(),
                FishRarity.VANILLA,
                1.0D,
                configManager.randomPrice(FishRarity.VANILLA),
                "Vanilla",
                "Ocean"
        );
    }

    private String displayName(ItemStack item, String fallback) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            if (stripped != null && !stripped.isBlank()) return stripped;
        }
        return fallback;
    }

    private String readableMaterialName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? material.name() : builder.toString();
    }

    private FishDefinition fallbackDefinition(CaughtFish fish) {
        Material material = switch (fish.rarity()) {
            case TRASH -> Material.LEATHER_BOOTS;
            case VANILLA, COMMON -> Material.COD;
            case ORNAMENTAL -> Material.TROPICAL_FISH;
            case EPIC -> Material.SALMON;
            case LEGENDARY, MITOLOGI, SECRET -> Material.PLAYER_HEAD;
        };
        return new FishDefinition(fish.id(), fish.name(), fish.rarity(), material, fish.weight(), fish.weight(), fish.price(), fish.price(), fish.origin(), fish.region(), fish.rarity().power() >= FishRarity.LEGENDARY.power(), "", Material.TROPICAL_FISH);
    }

    private Material itemMaterial(FishDefinition definition) {
        if (definition.rarity().power() >= FishRarity.LEGENDARY.power() && definition.headEnabled()) return Material.PLAYER_HEAD;
        return definition.material() == null ? definition.fallbackMaterial() : definition.material();
    }

    private String effectiveHeadTexture(FishDefinition definition) {
        if (definition.headTextureBase64() != null && !definition.headTextureBase64().isBlank()) {
            return definition.headTextureBase64().trim();
        }
        if (definition.rarity().power() < FishRarity.LEGENDARY.power()) return "";
        return HIGH_TIER_FISH_TEXTURES[Math.floorMod(definition.id().hashCode(), HIGH_TIER_FISH_TEXTURES.length)];
    }

    private void applyHeadTexture(SkullMeta skullMeta, String texture) {
        String textureUrl = textureUrl(texture);
        if (textureUrl == null) return;
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "VelioraFish");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            skullMeta.setPlayerProfile(profile);
            return;
        } catch (Exception ignored) {
            // Kept for older Paper implementations whose PlayerProfile API cannot set a custom URL.
        }
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

    private String textureUrl(String texture) {
        if (texture == null || texture.isBlank()) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(texture.trim()), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL.matcher(decoded);
            if (!matcher.find()) return null;
            String url = matcher.group(1);
            return url.startsWith("https://textures.minecraft.net/texture/") ? url : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String trimMultiplier(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.US, "%.1f", value);
    }
}
