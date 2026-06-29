package id.velioragardens.veliorasuite.module.pets.compat;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.PetConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;

public final class RedProtectCompat {
    private final VelioraSuite plugin;
    private boolean warned;

    public RedProtectCompat(VelioraSuite plugin, PetConfigManager config) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return redProtectPlugin() != null;
    }

    public boolean isInstalled() {
        return redProtectPlugin() != null;
    }

    public boolean canSpawnPet(Player owner, Location location) {
        if (!isEnabled() || owner == null || location == null) return true;
        if (owner.hasPermission("veliorasuite.pets.redprotect.bypass")) return true;
        Object region = region(location);
        if (region == null) return true;
        if (isOwnerOrMember(owner, location)) return true;
        return false;
    }

    public boolean canMovePet(Player owner, Location from, Location to) {
        if (!isEnabled() || to == null) return true;
        return canSpawnPet(owner, to);
    }

    public String regionName(Location location) {
        Object region = region(location);
        if (region == null) return "none";
        Object name = invoke(region, "getName");
        if (name == null) name = invoke(region, "getID");
        return name == null ? "unknown" : String.valueOf(name);
    }

    public boolean isInsideRegion(Location location) {
        return region(location) != null;
    }

    public boolean isOwnerOrMember(Player player, Location location) {
        if (player == null || location == null) return false;
        if (player.hasPermission("veliorasuite.pets.redprotect.bypass")) return true;
        Object region = region(location);
        if (region == null) return true;
        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        return callBool(region, "isLeader", name)
                || callBool(region, "isLeader", player)
                || callBool(region, "isOwner", name)
                || callBool(region, "isOwner", player)
                || callBool(region, "isAdmin", name)
                || callBool(region, "isAdmin", player)
                || callBool(region, "isMember", name)
                || callBool(region, "isMember", player)
                || listContains(region, "getLeaders", name, uuid)
                || listContains(region, "getOwners", name, uuid)
                || listContains(region, "getAdmins", name, uuid)
                || listContains(region, "getMembers", name, uuid);
    }

    private Object region(Location location) {
        if (location == null || location.getWorld() == null) return null;
        try {
            Plugin rp = redProtectPlugin();
            if (rp == null) return null;
            Object api = invoke(rp, "getAPI");
            if (api == null) api = staticGetAPI();
            if (api == null) return null;
            Object region = invoke(api, "getRegion", location);
            if (region != null) return region;
            region = invoke(api, "getRegion", location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld().getName());
            if (region != null) return region;
            return invoke(api, "getRegion", location.getBlockX(), location.getBlockZ(), location.getWorld().getName());
        } catch (Throwable throwable) {
            warnOnce("VelioraPets: RedProtect API gagal dibaca, fallback allow. " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return null;
        }
    }

    private Plugin redProtectPlugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("RedProtect");
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private Object staticGetAPI() {
        for (String className : new String[]{"br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect", "br.net.fabiozumbi12.RedProtect.RedProtect"}) {
            try {
                Class<?> clazz = Class.forName(className);
                Object instance = null;
                try { instance = clazz.getMethod("get").invoke(null); } catch (Exception ignored) { }
                if (instance != null) {
                    Object api = invoke(instance, "getAPI");
                    if (api != null) return api;
                }
                Object api = clazz.getMethod("getAPI").invoke(null);
                if (api != null) return api;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean callBool(Object target, String method, Object arg) {
        try {
            Method m = target.getClass().getMethod(method, arg instanceof Player ? Player.class : String.class);
            Object value = m.invoke(target, arg);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean listContains(Object target, String method, String name, String uuid) {
        Object value = invoke(target, method);
        if (value == null) return false;
        String raw = String.valueOf(value).toLowerCase(Locale.ROOT);
        return raw.contains(name.toLowerCase(Locale.ROOT)) || raw.contains(uuid.toLowerCase(Locale.ROOT));
    }

    private Object invoke(Object target, String method, Object... args) {
        if (target == null) return null;
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
            try { return m.invoke(target, args); } catch (Throwable ignored) { }
        }
        return null;
    }

    private void warnOnce(String message) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning(message);
    }
}
