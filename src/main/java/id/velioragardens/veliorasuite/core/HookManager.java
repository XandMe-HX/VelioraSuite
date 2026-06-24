package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HookManager {

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
            "floodgate"
    };

    private final VelioraSuite plugin;
    private final Map<String, Boolean> hooks = new LinkedHashMap<>();

    public HookManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void loadHooks() {
        hooks.clear();

        for (String hookName : OPTIONAL_HOOKS) {
            boolean hooked = Bukkit.getPluginManager().getPlugin(hookName) != null;
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
}
