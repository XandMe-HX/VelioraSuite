package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public final class PetEconomyManager {
    private final VelioraSuite plugin;
    private final PetConfigManager config;
    private boolean warned;

    public PetEconomyManager(VelioraSuite plugin, PetConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isReady() {
        return !config.economyEnabled() || provider() != null;
    }

    public boolean take(Player player, long amount) {
        if (!config.economyEnabled() || amount <= 0) return true;
        Object economy = provider();
        if (economy == null) { warn(); return false; }
        try {
            Method has = economy.getClass().getMethod("has", OfflinePlayer.class, double.class);
            Method withdraw = economy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            boolean enough = (boolean) has.invoke(economy, player, (double) amount);
            if (!enough) return false;
            withdraw.invoke(economy, player, (double) amount);
            return true;
        } catch (Exception exception) {
            warn();
            return false;
        }
    }

    private Object provider() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            return registration == null ? null : registration.getProvider();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void warn() {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("VelioraPets: Vault economy tidak aktif, transaksi pet ditolak aman.");
    }
}
