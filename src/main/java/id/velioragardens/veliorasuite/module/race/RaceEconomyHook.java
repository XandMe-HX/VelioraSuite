package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/** Optional Vault bridge. A missing or failed provider never changes a race or deducts money. */
final class RaceEconomyHook {
    private final VelioraSuite plugin;
    RaceEconomyHook(VelioraSuite plugin) { this.plugin = plugin; }

    Result charge(Player player, double amount) {
        if (amount <= 0D) return Result.OK;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return Result.UNAVAILABLE;
        try {
            Class<?> economyType = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"unchecked", "rawtypes"}) RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyType);
            if (registration == null || registration.getProvider() == null) return Result.UNAVAILABLE;
            Object economy = registration.getProvider();
            Method has = economyType.getMethod("has", OfflinePlayer.class, double.class);
            if (!(has.invoke(economy, player, amount) instanceof Boolean enough) || !enough) return Result.INSUFFICIENT;
            Object response = economyType.getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(economy, player, amount);
            if (response == null) return Result.FAILED;
            Object success = response.getClass().getMethod("transactionSuccess").invoke(response);
            return success instanceof Boolean ok && ok ? Result.OK : Result.FAILED;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Race: transaksi Vault gagal tanpa mengubah ras: " + exception.getClass().getSimpleName());
            return Result.FAILED;
        }
    }
    enum Result { OK, UNAVAILABLE, INSUFFICIENT, FAILED }
}
