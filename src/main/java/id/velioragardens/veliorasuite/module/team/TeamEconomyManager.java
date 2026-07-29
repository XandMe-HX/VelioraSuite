package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public final class TeamEconomyManager {

    private final VelioraSuite plugin;
    private Object economy;
    private Class<?> economyClass;
    private boolean warnedMissingVault;

    public TeamEconomyManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        economy = null;
        economyClass = null;

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            warnMissingVault();
            return;
        }

        try {
            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);

            if (registration == null) {
                warnMissingVault();
                return;
            }

            economy = registration.getProvider();
        } catch (ClassNotFoundException exception) {
            warnMissingVault();
        }
    }

    public boolean hasEconomy() {
        return economy != null && economyClass != null;
    }

    public boolean hasEnough(Player player, double amount) {
        if (amount <= 0) return true;
        if (!hasEconomy()) return false;

        try {
            Method method = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            Object result = method.invoke(economy, player, amount);
            return result instanceof Boolean value && value;
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraTeam: gagal cek saldo Vault: " + exception.getMessage());
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
            if (amount <= 0) return true;
            if (!hasEconomy()) return false;

            try {
                // Gunakan OfflinePlayer karena Vault Economy mengimplementasikan OfflinePlayer untuk kecocokan UUID
                Method method = economyClass.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
                Object response = method.invoke(economy, player, amount);
                
                if (response == null) return false;
                
                // Mengambil status sukses dari EconomyResponse menggunakan reflection
                Method successMethod = response.getClass().getMethod("transactionSuccess");
                Object success = successMethod.invoke(response);
                return success instanceof Boolean value && value;
            } catch (Exception exception) {
                plugin.getLogger().warning("VelioraTeam: gagal menarik uang Vault: " + exception.getMessage());
                exception.printStackTrace(); // Agar ketahuan persis baris mana yang error di console
                return false;
            }
        }

    private void warnMissingVault() {
        if (!warnedMissingVault) {
            plugin.getLogger().warning("VelioraTeam: Vault economy tidak tersedia. Create/upgrade team berbayar akan diblokir kecuali bypass cost.");
            warnedMissingVault = true;
        }
    }
}
