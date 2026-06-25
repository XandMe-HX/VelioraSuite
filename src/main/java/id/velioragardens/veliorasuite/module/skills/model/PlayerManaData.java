package id.velioragardens.veliorasuite.module.skills.model;

import java.util.UUID;

public final class PlayerManaData {

    private final UUID uuid;
    private String name;
    private int mana;
    private int maxMana;
    private String lastResetDate;
    private int totalManaEarned;
    private int totalManaSpent;

    public PlayerManaData(UUID uuid, String name, int mana, int maxMana, String lastResetDate, int totalManaEarned, int totalManaSpent) {
        this.uuid = uuid;
        this.name = name;
        this.mana = mana;
        this.maxMana = maxMana;
        this.lastResetDate = lastResetDate;
        this.totalManaEarned = totalManaEarned;
        this.totalManaSpent = totalManaSpent;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getMana() { return mana; }
    public void setMana(int mana) { this.mana = mana; }
    public int getMaxMana() { return maxMana; }
    public void setMaxMana(int maxMana) { this.maxMana = maxMana; }
    public String getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(String lastResetDate) { this.lastResetDate = lastResetDate; }
    public int getTotalManaEarned() { return totalManaEarned; }
    public void addTotalManaEarned(int amount) { this.totalManaEarned += Math.max(0, amount); }
    public int getTotalManaSpent() { return totalManaSpent; }
    public void addTotalManaSpent(int amount) { this.totalManaSpent += Math.max(0, amount); }
}
