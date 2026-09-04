package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;

/** Optional VelioraEnchant 1.4+ integration. No hard dependency or changes to rarity gates. */
final class PatientAnglerHook {
    private static Plugin owner;
    private static Method bonusMethod, recordMethod, enchantMethod;
    private static Plugin resolve() {
        Plugin current = Bukkit.getPluginManager().getPlugin("VelioraEnchant");
        if (current == null || !current.isEnabled()) return null;
        if (owner != current) {
            owner = current; bonusMethod = null; recordMethod = null; enchantMethod = null;
            try { enchantMethod = current.getClass().getMethod("getFishingEnchantBonus", Player.class, String.class); }
            catch (NoSuchMethodException ignored) { }
            try {
                bonusMethod = current.getClass().getMethod("getPatientAnglerBonus", Player.class);
                recordMethod = current.getClass().getMethod("recordPatientAnglerCatch", Player.class, boolean.class);
            } catch (NoSuchMethodException ignored) { /* Older Enchant versions remain supported. */ }
        }
        return current;
    }
    static double bonus(Player player) {
        if (player == null) return 0;
        Plugin plugin = resolve();
        if (plugin == null || bonusMethod == null) return 0;
        try {
            double result = ((Number)bonusMethod.invoke(plugin, player)).doubleValue();
            return Double.isFinite(result) ? Math.clamp(result, 0, .10) : 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return 0; }
    }
    static double enchantBonus(Player player, String id) {
        Plugin plugin=resolve();
        if(player==null || plugin==null || enchantMethod==null) return 0;
        try {
            double value=((Number)enchantMethod.invoke(plugin,player,id)).doubleValue();
            return Double.isFinite(value) ? Math.clamp(value,0,.09) : 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return 0; }
    }
    static void success(Player player, boolean rare) {
        Plugin plugin = resolve();
        if (plugin == null || recordMethod == null) return;
        try { recordMethod.invoke(plugin, player, rare); }
        catch (ReflectiveOperationException ignored) { /* Optional hook cannot fail a successful catch. */ }
    }
}
