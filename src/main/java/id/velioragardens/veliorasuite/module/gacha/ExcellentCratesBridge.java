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

    List<GachaOffer> discover(Map<String, String> keyOverrides, Map<String, Long> prices, long defaultPrice, boolean physicalKeysOnly) {
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
                if (physicalKeysOnly && virtualKey) continue;
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
                if (types[0].isAssignableFrom(player.getClass()) && types[1].isInstance(key) && (types[2] == int.class || types[2] == Integer.class)) {
                    method.invoke(manager, player, key, 1);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Veliora keyshop gagal memberi key '" + keyId + "': " + exception.getClass().getSimpleName() + " - " + exception.getMessage());
        }
        return false;
    }

    boolean isPhysical(String keyId) {
        if (!available()) return false;
        try {
            Object manager=call(plugin,"getKeyManager");
            Object key=call(manager,"getKeyById",String.class,keyId);
            return key != null && !(call(key,"isVirtual") instanceof Boolean virtual && virtual);
        } catch (ReflectiveOperationException | RuntimeException exception) { return false; }
    }

    boolean hasInventorySpace(Player player, String keyId) {
        if (!available()) return false;
        try {
            Object manager=call(plugin,"getKeyManager");
            Object key=call(manager,"getKeyById",String.class,keyId);
            Object stack=key==null?null:call(key,"getItemStack");
            if (!(stack instanceof ItemStack item)) return false;
            if (player.getInventory().firstEmpty() >= 0) return true;
            return java.util.Arrays.stream(player.getInventory().getStorageContents())
                .anyMatch(existing -> existing != null && existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize());
        } catch (ReflectiveOperationException | RuntimeException exception) { return false; }
    }

    int keyAmount(Player player, String keyId) {
        if (!available()) return -1;
        try {
            Object manager = call(plugin, "getKeyManager");
            Object key = call(manager, "getKeyById", String.class, keyId);
            if (key == null) return -1;
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("getKeysAmount") || method.getParameterCount() != 2) continue;
                if (method.getParameterTypes()[0].isAssignableFrom(player.getClass()) && method.getParameterTypes()[1].isInstance(key)) {
                    Object value = method.invoke(manager, player, key);
                    return value instanceof Number number ? number.intValue() : -1;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Veliora keyshop gagal menghitung key '" + keyId + "': " + exception.getMessage());
        }
        return -1;
    }

    boolean takeKey(Player player, String keyId) {
        if (!available()) return false;
        try {
            Object manager = call(plugin, "getKeyManager");
            Object key = call(manager, "getKeyById", String.class, keyId);
            if (key == null) return false;
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("takeKey") || method.getParameterCount() != 3) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types[0].isAssignableFrom(player.getClass()) && types[1].isInstance(key) && (types[2] == int.class || types[2] == Integer.class)) {
                    method.invoke(manager, player, key, 1);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Veliora keyshop gagal rollback key '" + keyId + "': " + exception.getMessage());
        }
        return false;
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException { return target.getClass().getMethod(method).invoke(target); }
    private static Object call(Object target, String method, Class<?> type, Object argument) throws ReflectiveOperationException { return target.getClass().getMethod(method, type).invoke(target, argument); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
