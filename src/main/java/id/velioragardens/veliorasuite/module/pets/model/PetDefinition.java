package id.velioragardens.veliorasuite.module.pets.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record PetDefinition(
        String id,
        String displayName,
        EntityType entityType,
        Material icon,
        PetRarity rarity,
        PetSkillType skillType,
        double skillBonus,
        double damage,
        double scale,
        long price,
        int storageSize,
        Material foodMaterial,
        int feedExp,
        boolean flyingPet,
        boolean rideable,
        int adultLevel
) {
}
