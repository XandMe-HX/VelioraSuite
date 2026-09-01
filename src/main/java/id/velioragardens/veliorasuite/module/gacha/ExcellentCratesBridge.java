package id.velioragardens.veliorasuite.module.gacha;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Optional reflection bridge: Suite never loads ExcellentCrates/NightCore classes itself. */
final class ExcellentCratesBridge {
    private final Plugin plugin;

    ExcellentCratesBridge(Plugin plugin) { this.plugin = plugin; }
    boolean available() { return plugin != null && plugin.isEnabled(); }

    List<GachaOffer> discover(Map<String, String> keyOverrides, Map<String, Long> prices, long defaultPrice, boolean requireVirtualKeys) {
        if (!available()) return List.of();
        try {
            Object crateManager = call(plugin, "getCrateManager");
            Object keyManager = call(plugin, "getKeyManager");
            Object value = call(crateManager, "getCrates");
            if (!(value instanceof Collection<?> crates)) return List.of();
            List<GachaOffer> offers = new ArrayList<>();
            for (Object crate : crates) {
                String crateId = text(call(crate, "getId"));
                if (crateId.isBlank()) continue;
                String keyId = keyOverrides.getOrDefault(crateId.toLowerCase(Locale.ROOT), crateId);
                Object key = call(keyManager, "getKeyById", String.class, keyId);
                if (key == null) continue;
                boolean virtualKey = call(key, "isVirtual") instanceof Boolean virtual && virtual;
                if (requireVirtualKeys && !virtualKey) continue;
                String name = text(call(crate, "getName"));
                Object icon = call(crate, "getItemStack");
                ItemStack item = icon instanceof ItemStack stack ? stack.clone() : null;
                offers.add(new GachaOffer(crateId, keyId, name.isBlank() ? crateId : name, item, Math.max(0L, prices.getOrDefault(crateId.toLowerCase(Locale.ROOT), defaultPrice)), virtualKey));
            }
            return offers;
        } catch (ReflectiveOperationException exception) {
            return List.of();
        }
    }

    boolean giveKey(Player player, String keyId) {
        if (!available()) return false;
        try {
            Object manager = call(plugin, "getKeyManager");
            Object key = call(manager, "getKeyById", String.class, keyId);
            if (key == null) return false;
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("giveKey") || method.getParameterCount() != 3) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types[0].isAssignableFrom(Player.class) && types[2] == int.class) {
                    method.invoke(manager, player, key, 1);
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) { }
        return false;
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException { return target.getClass().getMethod(method).invoke(target); }
    private static Object call(Object target, String method, Class<?> type, Object argument) throws ReflectiveOperationException { return target.getClass().getMethod(method, type).invoke(target, argument); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
