package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public final class FishingEconomyHook {

    private final VelioraSuite plugin;
    private boolean warned;

    public FishingEconomyHook(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return warn();
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return warn();
            Object economy = registration.getProvider();
            Method deposit = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            deposit.invoke(economy, player, amount);
            return true;
        } catch (Exception exception) {
            return warn();
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return warn();
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return warn();
            Object economy = registration.getProvider();
            Method withdraw = economy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            withdraw.invoke(economy, player, amount);
            return true;
        } catch (Exception exception) {
            return warn();
        }
    }

    private boolean warn() {
        if (!warned) {
            plugin.getLogger().warning("VelioraFishing: Vault economy tidak aktif, hasil sell dilewati.");
            warned = true;
        }
        return false;
    }
}
