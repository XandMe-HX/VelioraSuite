package id.velioragardens.veliorasuite.module.actionhouse;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.lang.reflect.Method;

final class ActionHouseEconomy {
    enum Result { OK, UNAVAILABLE, INSUFFICIENT, FAILED }
    private final VelioraSuite plugin;
    ActionHouseEconomy(VelioraSuite plugin) { this.plugin = plugin; }
    Result withdraw(Player player, double amount) { return transaction("withdrawPlayer", player, amount, true); }
    Result deposit(Player player, double amount) { return transaction("depositPlayer", player, amount, false); }
    private Result transaction(String method, Player player, double amount, boolean checkBalance) {
        if (amount <= 0) return Result.OK;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return Result.UNAVAILABLE;
        try {
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"unchecked", "rawtypes"}) RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration((Class) type);
            if (provider == null || provider.getProvider() == null) return Result.UNAVAILABLE;
            Object economy = provider.getProvider();
            if (checkBalance && (!(type.getMethod("has", OfflinePlayer.class, double.class).invoke(economy, player, amount) instanceof Boolean enough) || !enough)) return Result.INSUFFICIENT;
            Object response = type.getMethod(method, OfflinePlayer.class, double.class).invoke(economy, player, amount);
            Object success = response == null ? null : response.getClass().getMethod("transactionSuccess").invoke(response);
            return success instanceof Boolean ok && ok ? Result.OK : Result.FAILED;
        } catch (ReflectiveOperationException | LinkageError exception) { plugin.getLogger().warning("ActionHouse Vault gagal: " + exception.getClass().getSimpleName()); return Result.FAILED; }
    }
}
