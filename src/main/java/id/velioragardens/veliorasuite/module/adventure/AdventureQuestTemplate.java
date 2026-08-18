package id.velioragardens.veliorasuite.module.adventure;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record AdventureQuestTemplate(
        String id,
        String name,
        AdventureRank rank,
        AdventureQuestType type,
        String target,
        int amount,
        int durationMinutes,
        int money,
        long playerExp,
        long guildExp,
        int mana
) {
    public Material material() {
        try { return Material.valueOf(target.toUpperCase()); }
        catch (Exception ignored) { return null; }
    }

    public EntityType entityType() {
        try { return EntityType.valueOf(target.toUpperCase()); }
        catch (Exception ignored) { return null; }
    }
}
