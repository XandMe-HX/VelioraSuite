package id.velioragardens.veliorasuite.module.quest.model;

import org.bukkit.Material;

/** A safe, configurable item reward for one quest completion. */
public record QuestItemReward(Material material, int amount) {
    public QuestItemReward {
        if (material == null) throw new IllegalArgumentException("material");
        amount = Math.max(1, amount);
    }
}
