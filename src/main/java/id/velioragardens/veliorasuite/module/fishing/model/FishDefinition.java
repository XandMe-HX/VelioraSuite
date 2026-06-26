package id.velioragardens.veliorasuite.module.fishing.model;

import org.bukkit.Material;

public record FishDefinition(
        String id,
        String name,
        FishRarity rarity,
        Material material,
        double minWeight,
        double maxWeight,
        int minPrice,
        int maxPrice,
        String origin,
        String region,
        boolean headEnabled,
        String headTextureBase64,
        Material fallbackMaterial
) {
}
