package id.velioragardens.veliorasuite.module.boss.model;

import org.bukkit.entity.EntityType;

import java.util.List;

public record BossDefinition(
        String id,
        EntityType entityType,
        String displayName,
        BossRarity rarity,
        double health,
        double damage,
        double scale,
        List<BossSkillType> skills
) {
}
