package id.velioragardens.veliorasuite.module.skills;

import org.bukkit.entity.Player;

public final class SkillsApi {

    private final ManaManager manaManager;

    public SkillsApi(ManaManager manaManager) {
        this.manaManager = manaManager;
    }

    public int getMana(Player player) { return manaManager.getMana(player); }
    public int getMaxMana(Player player) { return manaManager.getMaxMana(player); }
    public boolean hasMana(Player player, int amount) { return manaManager.hasMana(player, amount); }
    public boolean takeMana(Player player, int amount, String reason) { return manaManager.takeMana(player, amount, reason); }
    public boolean giveMana(Player player, int amount, String reason) { return manaManager.giveMana(player, amount, reason); }
    public boolean setMana(Player player, int amount) { return manaManager.setMana(player, amount); }
    public boolean setMaxMana(Player player, int amount) { return manaManager.setMaxMana(player, amount); }
    public int getQuestManaCost(int questLevel) { return manaManager.getQuestManaCost(questLevel); }
}
