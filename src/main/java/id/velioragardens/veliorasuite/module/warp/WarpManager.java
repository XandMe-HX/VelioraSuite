package id.velioragardens.veliorasuite.module.warp;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.scheduler.BukkitTask;

public final class WarpManager implements Listener {
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_-]{2,24}");
    private static final Set<String> RESERVED = Set.of("vgwarp", "vwarp", "veliorasuite", "vs", "vgwar");

    private final VelioraSuite plugin;
    private final File configFile;
    private final File dataFile;
    private final Map<String, WarpPoint> warps = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldowns = new LinkedHashMap<>();
    private final Map<UUID, BukkitTask> pendingTeleports = new LinkedHashMap<>();
    private final Map<UUID, Long> arrivalFreezeUntil = new LinkedHashMap<>();
    private YamlConfiguration config;

    public WarpManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "modules/warp.yml");
        this.dataFile = new File(plugin.getDataFolder(), "data/warps.yml");
    }

    public void load() {
        plugin.saveResourceIfNotExists("modules/warp.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        warps.clear();
        aliases.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("warps");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String root = "warps." + key + ".";
                try {
                    UUID worldId = UUID.fromString(data.getString(root + "world", ""));
                    WarpPoint point = new WarpPoint(
                            normalize(data.getString(root + "name", key)),
                            worldId,
                            data.getString(root + "world-name", ""),
                            data.getDouble(root + "x"),
                            data.getDouble(root + "y"),
                            data.getDouble(root + "z"),
                            (float) data.getDouble(root + "yaw"),
                            (float) data.getDouble(root + "pitch"),
                            data.getString(root + "last-owner", ""),
                            normalizeAliases(data.getStringList(root + "aliases")),
                            data.getString(root + "permission", "")
                    );
                    warps.put(point.name(), point);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("VelioraWarp: warp " + key + " dilewati karena world UUID tidak valid.");
                }
            }
        }
        rebuildAliases();
    }

    public synchronized boolean setWarp(Player owner, String rawName) {
        String name = normalize(rawName);
        if (!validName(name)) return false;
        Location location = owner.getLocation();
        World world = location.getWorld();
        if (world == null) return false;

        List<String> pointAliases = name.equals("lobby") ? List.of("hub", "spawn") : List.of();
        WarpPoint point = new WarpPoint(name, world.getUID(), world.getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), owner.getUniqueId().toString(),
                pointAliases, "");
        warps.put(name, point);
        rebuildAliases();
        save();
        return true;
    }

    public synchronized boolean deleteWarp(String rawName) {
        String name = resolveName(rawName);
        if (name == null || warps.remove(name) == null) return false;
        rebuildAliases();
        save();
        return true;
    }

    public synchronized boolean addAlias(String rawName, String rawAlias) {
        String name = resolveName(rawName);
        String alias = normalize(rawAlias);
        WarpPoint point = name == null ? null : warps.get(name);
        if (point == null || !validName(alias) || aliases.containsKey(alias) || hasExternalCommand(alias)) return false;
        List<String> values = new ArrayList<>(point.aliases());
        values.add(alias);
        warps.put(name, point.withAliases(values));
        rebuildAliases();
        save();
        return true;
    }

    public synchronized boolean removeAlias(String rawName, String rawAlias) {
        String name = resolveName(rawName);
        WarpPoint point = name == null ? null : warps.get(name);
        if (point == null) return false;
        String alias = normalize(rawAlias);
        List<String> values = new ArrayList<>(point.aliases());
        if (!values.removeIf(value -> value.equalsIgnoreCase(alias))) return false;
        warps.put(name, point.withAliases(values));
        rebuildAliases();
        save();
        return true;
    }

    public boolean teleport(Player player, String rawName) {
        String name = resolveName(rawName);
        WarpPoint point = name == null ? null : warps.get(name);
        if (point == null) {
            player.sendMessage(color(message("not-found", "%prefix% &cWarp tidak ditemukan: &f%warp%").replace("%warp%", rawName)));
            return false;
        }
        if (!point.permission().isBlank() && !player.hasPermission(point.permission())) {
            player.sendMessage(color(message("no-permission", "%prefix% &cKamu tidak punya izin.")));
            return false;
        }

        int cooldown = Math.max(0, config.getInt("settings.cooldown-seconds", 3));
        long now = System.currentTimeMillis();
        long available = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (!player.hasPermission("veliorasuite.warp.admin") && available > now) {
            long seconds = Math.max(1L, (available - now + 999L) / 1000L);
            player.sendMessage(color(message("cooldown", "%prefix% &eTunggu &f%seconds%s &esebelum warp lagi.")
                    .replace("%seconds%", String.valueOf(seconds))));
            return false;
        }

        World world = Bukkit.getWorld(point.world());
        if (world == null && !point.worldName().isBlank()) world = Bukkit.getWorld(point.worldName());
        if (world == null) {
            player.sendMessage(color(message("world-missing", "%prefix% &cWorld tujuan belum dimuat.")));
            return false;
        }

        Location target = new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        beginCountdown(player, point, target, cooldown, now);
        return true;
    }

    private void beginCountdown(Player player, WarpPoint point, Location target, int cooldown, long now) {
        BukkitTask previous = pendingTeleports.remove(player.getUniqueId());
        if (previous != null) previous.cancel();
        Location origin = player.getLocation().clone();
        final int[] remaining = {Math.max(1, config.getInt("settings.countdown-seconds", 5))};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { cancelPending(player.getUniqueId()); return; }
            if (player.getWorld() != origin.getWorld() || player.getLocation().distanceSquared(origin) > 0.16D) {
                player.clearTitle();
                player.sendMessage(color(message("moved", "%prefix% &cTeleport dibatalkan karena kamu bergerak.")));
                cancelPending(player.getUniqueId());
                return;
            }
            if (remaining[0] > 0) {
                player.sendTitle(color("&bTeleport dalam &f" + remaining[0]), color("&7Jangan bergerak"), 0, 24, 0);
                remaining[0]--;
                return;
            }
            cancelPending(player.getUniqueId());
            target.getChunk().load(true);
            if (!player.teleport(target)) {
                player.sendMessage(color(message("failed", "%prefix% &cTeleport gagal. Coba lagi.")));
                return;
            }
            applyArrivalFreeze(player);
            cooldowns.put(player.getUniqueId(), now + cooldown * 1000L);
            if (config.getBoolean("settings.sound.enabled", true)) {
                try { player.playSound(player.getLocation(), Sound.valueOf(config.getString("settings.sound.name", "ENTITY_ENDERMAN_TELEPORT")), 0.8F, 1.1F); }
                catch (IllegalArgumentException ignored) { }
            }
            player.sendMessage(color(message("teleported", "%prefix% &aTeleport ke &f%warp%&a.").replace("%warp%", point.name())));
        }, 0L, 20L);
        pendingTeleports.put(player.getUniqueId(), task);
    }

    private void cancelPending(UUID playerId) {
        BukkitTask task = pendingTeleports.remove(playerId);
        if (task != null) task.cancel();
    }

    private void applyArrivalFreeze(Player player) {
        int seconds = Math.max(0, config.getInt("settings.arrival-freeze-seconds", 3));
        if (seconds <= 0) return;
        long until = System.currentTimeMillis() + seconds * 1000L;
        arrivalFreezeUntil.put(player.getUniqueId(), until);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, seconds * 20, 0, false, false, false));
        player.sendTitle(color("&bTiba di tujuan"), color("&7Bersiap..."), 0, seconds * 20, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long frozenUntil = arrivalFreezeUntil.get(player.getUniqueId());
            if (frozenUntil != null && frozenUntil <= System.currentTimeMillis()) {
                arrivalFreezeUntil.remove(player.getUniqueId());
                player.clearTitle();
            }
        }, seconds * 20L + 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        Long until = arrivalFreezeUntil.get(event.getPlayer().getUniqueId());
        if (until == null) return;
        if (until <= System.currentTimeMillis()) { arrivalFreezeUntil.remove(event.getPlayer().getUniqueId()); return; }
        if (event.getTo() != null && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrozenInteract(PlayerInteractEvent event) {
        Long until = arrivalFreezeUntil.get(event.getPlayer().getUniqueId());
        if (until != null && until > System.currentTimeMillis()) event.setCancelled(true);
    }

    public Location safetyTarget(Location from) {
        WarpPoint best = null;
        double bestDistance = Double.MAX_VALUE;
        if (from != null && from.getWorld() != null) {
            for (WarpPoint point : warps.values()) {
                World world = Bukkit.getWorld(point.world());
                if (world == null || !world.equals(from.getWorld())) continue;
                double dx = point.x() - from.getX(), dz = point.z() - from.getZ();
                double distance = dx * dx + dz * dz;
                if (distance < bestDistance) { best = point; bestDistance = distance; }
            }
        }
        if (best == null) best = warps.get(resolveName("lobby"));
        if (best == null) return from == null || from.getWorld() == null ? null : from.getWorld().getSpawnLocation();
        World world = Bukkit.getWorld(best.world());
        if (world == null) world = Bukkit.getWorld(best.worldName());
        return world == null ? null : new Location(world, best.x(), best.y(), best.z(), best.yaw(), best.pitch());
    }

    public synchronized void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (WarpPoint point : warps.values()) {
            String root = "warps." + point.name() + ".";
            data.set(root + "world", point.world().toString());
            data.set(root + "world-name", point.worldName());
            data.set(root + "x", point.x());
            data.set(root + "y", point.y());
            data.set(root + "z", point.z());
            data.set(root + "yaw", point.yaw());
            data.set(root + "pitch", point.pitch());
            data.set(root + "name", point.name());
            data.set(root + "last-owner", point.lastOwner());
            data.set(root + "aliases", point.aliases());
            data.set(root + "permission", point.permission());
        }
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraWarp: gagal menyimpan warps.yml: " + exception.getMessage());
        }
    }

    public String resolveName(String raw) {
        String value = normalize(raw);
        if (warps.containsKey(value)) return value;
        return aliases.get(value);
    }

    public boolean hasDirectAlias(String raw) { return resolveName(raw) != null; }
    public boolean hasExternalCommand(String raw) {
        PluginCommand command = Bukkit.getPluginCommand(normalize(raw));
        return command != null && command.getPlugin() != plugin;
    }
    public Set<String> directNames() { return Collections.unmodifiableSet(new LinkedHashSet<>(aliases.keySet())); }
    public Set<String> warpNames() { return Collections.unmodifiableSet(new LinkedHashSet<>(warps.keySet())); }
    public WarpPoint get(String raw) { String name = resolveName(raw); return name == null ? null : warps.get(name); }
    public boolean validName(String raw) { String name = normalize(raw); return SAFE_NAME.matcher(name).matches() && !RESERVED.contains(name); }
    public boolean hasAdmin(Player player) { return player.hasPermission(config.getString("permissions.admin", "veliorasuite.warp.admin")) || player.isOp(); }
    public boolean hasReload(Player player) { return player.hasPermission(config.getString("permissions.reload", "veliorasuite.warp.reload")) || hasAdmin(player); }
    public int afkTimeoutSeconds() { return Math.max(0, config.getInt("settings.afk.auto-seconds", 300)); }
    public String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
    public String message(String path, String fallback) {
        String prefix = config.getString("settings.prefix", "&8[&bVelioraWarp&8] ");
        return config.getString("messages." + path, fallback).replace("%prefix%", prefix);
    }

    private void rebuildAliases() {
        aliases.clear();
        for (WarpPoint point : warps.values()) {
            aliases.put(point.name(), point.name());
            for (String alias : point.aliases()) aliases.putIfAbsent(normalize(alias), point.name());
        }
    }

    private List<String> normalizeAliases(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String alias = normalize(value);
            if (validName(alias)) result.add(alias);
        }
        return List.copyOf(result);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record WarpPoint(String name, UUID world, String worldName, double x, double y, double z,
                            float yaw, float pitch, String lastOwner, List<String> aliases, String permission) {
        public WarpPoint {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            permission = permission == null ? "" : permission;
        }
        public WarpPoint withAliases(List<String> newAliases) {
            return new WarpPoint(name, world, worldName, x, y, z, yaw, pitch, lastOwner,
                    List.copyOf(new LinkedHashSet<>(newAliases)), permission);
        }
    }
}
