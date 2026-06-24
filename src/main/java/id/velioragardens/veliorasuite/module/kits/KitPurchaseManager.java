package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public final class KitPurchaseManager {

    private final VelioraSuite plugin;
    private Object economy;
    private Class<?> economyClass;
    private boolean warnedMissingVault;

    public KitPurchaseManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.economy = null;
        this.economyClass = null;

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            warnMissingVault();
            return;
        }

        try {
            this.economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);

            if (registration == null) {
                warnMissingVault();
                return;
            }

            this.economy = registration.getProvider();
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
            plugin.getLogger().warning("VelioraKits: gagal cek saldo Vault: " + exception.getMessage());
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (amount <= 0) return true;
        if (!hasEconomy()) return false;

        try {
            Method method = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Object response = method.invoke(economy, player, amount);
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            Object success = successMethod.invoke(response);
            return success instanceof Boolean value && value;
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraKits: gagal menarik uang Vault: " + exception.getMessage());
            return false;
        }
    }

    public void deposit(Player player, double amount) {
        if (amount <= 0 || !hasEconomy()) return;

        try {
            Method method = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            method.invoke(economy, player, amount);
        } catch (Exception exception) {
            plugin.getLogger().warning("VelioraKits: gagal memberi uang Vault: " + exception.getMessage());
        }
    }

    private void warnMissingVault() {
        if (!warnedMissingVault) {
            plugin.getLogger().warning("VelioraKits: Vault economy tidak tersedia. Fitur buy dan reward money akan dilewati.");
            warnedMissingVault = true;
        }
    }
}
