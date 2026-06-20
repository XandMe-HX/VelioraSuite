package id.velioragardens.veliorasuite.hook;

import id.velioragardens.veliorasuite.VelioraSuite;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HookManager {

    private final VelioraSuite plugin;
    private final Map<String, Boolean> hooks = new LinkedHashMap<>();
    private Economy economy;

    public HookManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void loadHooks() {
        hooks.clear();
        economy = null;

        check("Vault", "Vault");
        check("PlaceholderAPI", "PlaceholderAPI");
        check("LuckPerms", "LuckPerms");
        check("Geyser", "Geyser-Spigot");
        check("Floodgate", "floodgate");
        check("SkinsRestorer", "SkinsRestorer");
        check("GSit", "GSit");
        check("Citizens", "Citizens");
        check("DecentHolograms", "DecentHolograms");
        check("ExcellentCrates", "ExcellentCrates");
        check("Essentials", "Essentials");
        check("EssentialsChat", "EssentialsChat");
        check("RedProtect", "RedProtect");
        check("BetterRTP", "BetterRTP");
        check("EconomyShopGUI", "EconomyShopGUI");

        setupVaultEconomy();
    }

    private void check(String key, String pluginName) {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(pluginName);
        hooks.put(key.toLowerCase(), enabled);

        if (plugin.getConfig().getBoolean("settings.debug", false)) {
            plugin.getLogger().info("Hook " + key + ": " + (enabled ? "FOUND" : "MISSING"));
        }
    }

    private void setupVaultEconomy() {
        if (!isHooked("Vault")) {
            return;
        }

        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            plugin.getLogger().warning("Vault ditemukan, tapi Economy provider belum tersedia.");
            return;
        }

        economy = provider.getProvider();
        plugin.getLogger().info("Vault economy hooked: " + economy.getName());
    }

    public boolean isHooked(String key) {
        return hooks.getOrDefault(key.toLowerCase(), false);
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public Map<String, Boolean> getHooks() {
        return hooks;
    }
}
