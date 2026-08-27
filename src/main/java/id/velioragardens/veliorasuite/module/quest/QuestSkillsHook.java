package id.velioragardens.veliorasuite.module.quest;

import org.bukkit.entity.Player;

/**
 * Compatibility stub retained only for reading legacy quest data.
 * VelioraSuite no longer owns skills or mana; AuraSkills is the sole provider.
 */
public final class QuestSkillsHook {
    public QuestSkillsHook(id.velioragardens.veliorasuite.VelioraSuite plugin) { }
    public boolean isAvailable() { return false; }
    public int getQuestManaCost(int level) { return 0; }
    public int getMana(Player player) { return 0; }
    public int getMaxMana(Player player) { return 0; }
    public boolean hasMana(Player player, int amount) { return true; }
    public boolean takeMana(Player player, int amount, String reason) { return true; }
    public void giveMana(Player player, int amount, String reason) { }
    public boolean addMaxMana(Player player, int amount, boolean fillToNewMax) { return false; }
}
