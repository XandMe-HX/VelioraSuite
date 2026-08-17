package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class ManaManager {

    private final SkillsConfigManager configManager;
    private final ManaDataManager dataManager;

    public ManaManager(SkillsConfigManager configManager, ManaDataManager dataManager) {
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    public PlayerManaData getData(OfflinePlayer player) {
        PlayerManaData data = dataManager.getOrCreate(player);
        resetIfMissed(data);
        clamp(data);
        dataManager.save(data);
        return data;
    }

    public PlayerManaData findByName(String name) {
        PlayerManaData data = dataManager.findByName(name);
        if (data == null) return null;
        resetIfMissed(data);
        clamp(data);
        dataManager.save(data);
        return data;
    }

    public int getMana(Player player) { return getData(player).getMana(); }
    public int getMaxMana(Player player) { return getData(player).getMaxMana(); }
    public boolean hasMana(Player player, int amount) { return getMana(player) >= Math.max(0, amount); }

    public boolean takeMana(Player player, int amount, String reason) {
        if (amount <= 0) return true;
        PlayerManaData data = getData(player);
        if (data.getMana() < amount) return false;
        data.setMana(data.getMana() - amount);
        data.addTotalManaSpent(amount);
        dataManager.save(data);
        return true;
    }

    public boolean giveMana(Player player, int amount, String reason) {
        if (amount <= 0) return true;
        PlayerManaData data = getData(player);
        int before = data.getMana();
        data.setMana(Math.min(data.getMaxMana(), data.getMana() + amount));
        data.addTotalManaEarned(Math.max(0, data.getMana() - before));
        dataManager.save(data);
        return true;
    }

    public boolean addMaxMana(Player player, int amount, boolean fillToNewMax) {
        if (player == null || amount <= 0) return false;
        PlayerManaData data = getData(player);
        data.setMaxMana(Math.min(configManager.getMaxManaCap(), data.getMaxMana() + amount));
        if (fillToNewMax) {
            data.setMana(data.getMaxMana());
        } else {
            clamp(data);
        }
        data.addTotalManaEarned(amount);
        dataManager.save(data);
        return true;
    }

    public boolean setMana(Player player, int amount) {
        PlayerManaData data = getData(player);
        data.setMana(amount);
        clamp(data);
        dataManager.save(data);
        return true;
    }

    public boolean setMaxMana(Player player, int amount) {
        PlayerManaData data = getData(player);
        data.setMaxMana(Math.min(configManager.getMaxManaCap(), Math.max(1, amount)));
        clamp(data);
        dataManager.save(data);
        return true;
    }

    public void setMana(PlayerManaData data, int amount) {
        data.setMana(amount);
        clamp(data);
        dataManager.save(data);
    }

    public void addMana(PlayerManaData data, int amount) {
        int before = data.getMana();
        data.setMana(data.getMana() + Math.max(0, amount));
        clamp(data);
        data.addTotalManaEarned(Math.max(0, data.getMana() - before));
        dataManager.save(data);
    }

    public void removeMana(PlayerManaData data, int amount) {
        int before = data.getMana();
        data.setMana(data.getMana() - Math.max(0, amount));
        clamp(data);
        data.addTotalManaSpent(Math.max(0, before - data.getMana()));
        dataManager.save(data);
    }

    public void resetMana(PlayerManaData data) {
        data.setMana(data.getMaxMana());
        data.setLastResetDate(dataManager.today());
        clamp(data);
        dataManager.save(data);
    }

    public void resetIfMissed(PlayerManaData data) {
        if (!configManager.isDailyResetEnabled() || !configManager.isResetOnJoinIfMissed()) return;
        if (!dataManager.today().equals(data.getLastResetDate())) resetMana(data);
    }

    public int getQuestManaCost(int questLevel) {
        if (!configManager.isQuestCostEnabled()) return 0;
        if (questLevel <= 4) return configManager.getQuestCost1To4();
        if (questLevel <= 9) return configManager.getQuestCost5To9();
        if (questLevel <= 14) return configManager.getQuestCost10To14();
        return configManager.getQuestCost15Plus();
    }

    private void clamp(PlayerManaData data) {
        if (data.getMaxMana() < configManager.getDefaultMaxMana()) {
            data.setMaxMana(configManager.getDefaultMaxMana());
            data.setMana(Math.max(data.getMana(), configManager.getDefaultMana()));
        }
        data.setMaxMana(Math.min(configManager.getMaxManaCap(), Math.max(1, data.getMaxMana())));
        int min = configManager.getMinMana();
        data.setMana(Math.max(min, Math.min(data.getMana(), data.getMaxMana())));
    }
}
