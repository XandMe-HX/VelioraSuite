package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HookManager implements Listener {

    private static final String[] OPTIONAL_HOOKS = {
            "Vault",
            "PlaceholderAPI",
            "LuckPerms",
            "Essentials",
            "EconomyShopGUI",
            "RedProtect",
            "SkinsRestorer",
            "GSit",
            "Geyser-Spigot",
            "floodgate",
            "ExcellentCrates",
            "AggressiveAnimals",
            "VelioraWar",
            "VelioraGacha",
            "VelioraFTB"
    };

    private final VelioraSuite plugin;
    private final Map<String, Boolean> hooks = new LinkedHashMap<>();

    public HookManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void loadHooks() {
        hooks.clear();

        for (String hookName : OPTIONAL_HOOKS) {
            boolean hooked = Bukkit.getPluginManager().isPluginEnabled(hookName);
            hooks.put(hookName, hooked);
        }

        if (plugin.getConfig().getBoolean("settings.debug", false)) {
            plugin.getLogger().info("Hook status: " + hooks);
        }
    }

    public boolean hasHook(String hookName) {
        String normalized = hookName.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Boolean> entry : hooks.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getValue();
            }
        }

        return false;
    }

    public Map<String, Boolean> getHooks() {
        return Collections.unmodifiableMap(hooks);
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        update(event.getPlugin().getName(), true);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        update(event.getPlugin().getName(), false);
    }

    private void update(String pluginName, boolean enabled) {
        for (String hookName : OPTIONAL_HOOKS) {
            if (hookName.equalsIgnoreCase(pluginName)) {
                hooks.put(hookName, enabled);
                return;
            }
        }
    }
}
