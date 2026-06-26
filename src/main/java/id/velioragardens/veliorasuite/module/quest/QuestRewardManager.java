package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public final class QuestRewardManager {

    private final VelioraSuite plugin;
    private final QuestConfigManager configManager;
    private boolean vaultWarned;

    public QuestRewardManager(VelioraSuite plugin, QuestConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean depositMoney(Player player, int amount) {
        if (amount <= 0) return true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return warnMissingVault(player);
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return warnMissingVault(player);
            Object economy = registration.getProvider();
            Method deposit = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            deposit.invoke(economy, player, (double) amount);
            return true;
        } catch (Exception exception) {
            return warnMissingVault(player);
        }
    }

    private boolean warnMissingVault(Player player) {
        if (!vaultWarned) {
            plugin.getLogger().warning("VelioraQuest: Vault economy tidak aktif, reward money quest dilewati.");
            vaultWarned = true;
        }
        if (player != null) {
            player.sendMessage(configManager.color(configManager.message("vault-not-found", "%prefix% &eVault economy belum aktif, reward money dilewati.")));
        }
        return false;
    }
}
