package id.velioragardens.veliorasuite.module.clearlag;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import id.velioragardens.veliorasuite.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClearLagModule extends AbstractModule implements CommandExecutor, TabCompleter {

    private BukkitTask timerTask;
    private int secondsLeft;
    private int lastCleared;
    private int lastEntities;
    private long lastClearMillis;

    public ClearLagModule(VelioraSuite plugin) {
        super(plugin, "clearlag", "clearlag");
    }

    @Override
    protected void onEnable() {
        registerCommand();
        startTimer();
        plugin.getLogger().info("VelioraClearLag module started.");
    }

    @Override
    protected void onDisable() {
        stopTimer();
        plugin.getLogger().info("VelioraClearLag module stopped.");
    }

    @Override
    protected void onReload() {
        stopTimer();
        configFile.reload();
        startTimer();
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("vclearlag");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private void startTimer() {
        FileConfiguration cfg = configFile.get();
        if (!cfg.getBoolean("auto-clear.enabled", true)) return;
        secondsLeft = Math.max(30, cfg.getInt("auto-clear.interval-seconds", 600));
        Set<Integer> warnings = new HashSet<>(cfg.getIntegerList("warning.times"));
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled) return;
            if (cfg.getBoolean("warning.enabled", true) && warnings.contains(secondsLeft)) {
                Bukkit.broadcastMessage(color(message("warning").replace("%time%", String.valueOf(secondsLeft))));
            }
            if (secondsLeft <= 0) {
                ClearResult result = clear(false);
                if (result.totalAmount() > 0) {
                    Bukkit.broadcastMessage(color(message("cleared").replace("%amount%", String.valueOf(result.totalAmount())).replace("%entities%", String.valueOf(result.entities()))));
                } else {
                    Bukkit.broadcastMessage(color(message("no-items")));
                }
                secondsLeft = Math.max(30, cfg.getInt("auto-clear.interval-seconds", 600));
                return;
            }
            secondsLeft--;
        }, 20L, 20L);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    public ClearResult clear(boolean manual) {
        int amount = 0;
        int entities = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldAllowed(world)) continue;
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                int count = removableAmount(entity, manual);
                if (count > 0) {
                    entity.remove();
                    amount += count;
                    entities++;
                }
            }
            ClusterResult cluster = clearMobClusters(world);
            amount += cluster.removed();
            entities += cluster.removed();
        }
        lastCleared = amount;
        lastEntities = entities;
        lastClearMillis = System.currentTimeMillis();
        return new ClearResult(amount, entities);
    }

    private boolean isWorldAllowed(World world) {
        List<String> disabled = configFile.get().getStringList("worlds.disabled-worlds");
        if (disabled.contains(world.getName())) return false;
        List<String> enabledWorlds = configFile.get().getStringList("worlds.enabled-worlds");
        return enabledWorlds.isEmpty() || enabledWorlds.contains(world.getName());
    }

    private int removableAmount(Entity entity, boolean manual) {
        FileConfiguration cfg = configFile.get();
        if (entity instanceof Player) return 0;
        if (protectEntity(entity)) return 0;
        if (entity instanceof Item item) {
            if (!cfg.getBoolean("remove.dropped-items", true)) return 0;
            int minAge = manual ? 0 : cfg.getInt("auto-clear.minimum-item-age-seconds", 300) * 20;
            if (item.getTicksLived() < minAge) return 0;
            ItemStack stack = item.getItemStack();
            if (stack != null && cfg.getStringList("whitelist-items").contains(stack.getType().name())) return 0;
            return stack == null ? 1 : Math.max(1, stack.getAmount());
        }
        if (entity instanceof ExperienceOrb) return cfg.getBoolean("remove.experience-orbs", true) ? 1 : 0;
        if (entity instanceof Arrow) return cfg.getBoolean("remove.arrows", true) ? 1 : 0;
        if (entity instanceof Snowball) return cfg.getBoolean("remove.snowballs", true) ? 1 : 0;
        if (entity instanceof Egg) return cfg.getBoolean("remove.eggs", true) ? 1 : 0;
        if (entity instanceof Fireball) return cfg.getBoolean("remove.fireballs", true) ? 1 : 0;
        if (entity instanceof Projectile) return cfg.getBoolean("remove.other-projectiles", false) ? 1 : 0;
        return 0;
    }

    private ClusterResult clearMobClusters(World world) {
        FileConfiguration cfg = configFile.get();
        if (!cfg.getBoolean("mob-limiter.enabled", true)) return new ClusterResult(0);
        int maxSame = Math.max(1, cfg.getInt("mob-limiter.max-same-mob-in-radius", 8));
        double radius = Math.max(2.0, cfg.getDouble("mob-limiter.radius", 8.0));
        double playerSafeRadius = Math.max(0.0, cfg.getDouble("mob-limiter.player-safe-radius", 24.0));
        boolean removeUnreachable = cfg.getBoolean("mob-limiter.remove-unreachable", true);
        int removed = 0;
        Map<String, List<LivingEntity>> groups = new HashMap<>();
        for (LivingEntity living : world.getLivingEntities()) {
            if (living instanceof Player) continue;
            if (protectEntity(living)) continue;
            if (living.getTicksLived() < cfg.getInt("mob-limiter.minimum-age-seconds", 60) * 20) continue;
            if (nearPlayer(living.getLocation(), playerSafeRadius)) continue;
            String key = living.getType().name() + ":" + clusterKey(living.getLocation(), radius);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(living);
        }
        for (List<LivingEntity> list : groups.values()) {
            if (list.size() <= maxSame) continue;
            list.sort((a, b) -> Integer.compare(b.getTicksLived(), a.getTicksLived()));
            for (int i = maxSame; i < list.size(); i++) {
                LivingEntity entity = list.get(i);
                if (removeUnreachable && entity instanceof Mob mob && mob.getTarget() != null) continue;
                entity.remove();
                removed++;
            }
        }
        return new ClusterResult(removed);
    }

    private boolean nearPlayer(Location location, double radius) {
        if (radius <= 0) return false;
        double radiusSquared = radius * radius;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) return true;
        }
        return false;
    }

    private String clusterKey(Location location, double radius) {
        int x = (int) Math.floor(location.getBlockX() / radius);
        int y = (int) Math.floor(location.getBlockY() / radius);
        int z = (int) Math.floor(location.getBlockZ() / radius);
        return x + ":" + y + ":" + z;
    }

    private boolean protectEntity(Entity entity) {
        FileConfiguration cfg = configFile.get();
        if (cfg.getBoolean("protect.armor-stands", true) && entity instanceof ArmorStand) return true;
        if (cfg.getBoolean("protect.villagers", true) && entity instanceof Villager) return true;
        if (cfg.getBoolean("protect.named-entities", true) && entity.getCustomName() != null && !entity.getCustomName().isBlank()) return true;
        if (cfg.getBoolean("protect.tamed-animals", true) && entity instanceof Tameable tameable && tameable.isTamed()) return true;
        if (cfg.getBoolean("protect.vehicles-with-passenger", true) && entity instanceof Vehicle && !entity.getPassengers().isEmpty()) return true;
        if (entity.getScoreboardTags().contains("veliora_boss") || entity.getScoreboardTags().contains("veliora_trader") || entity.getScoreboardTags().contains("veliora_protected")) return true;
        List<String> protectedTypes = cfg.getStringList("mob-limiter.protected-types");
        return protectedTypes.contains(entity.getType().name());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.clearlag.admin")) {
            sender.sendMessage(color(message("no-permission")));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "clear" -> {
                ClearResult result = clear(true);
                sender.sendMessage(color(message("manual-clear").replace("%amount%", String.valueOf(result.totalAmount())).replace("%entities%", String.valueOf(result.entities())).replace("%player%", sender.getName())));
            }
            case "reload" -> {
                onReload();
                sender.sendMessage(color(message("reload")));
            }
            case "timer", "status" -> {
                sender.sendMessage(color("&8【&aVelioraClearLag&8】 &fNext clear: &a" + TimeUtil.formatSeconds(secondsLeft)));
                sender.sendMessage(color("&8【&aVelioraClearLag&8】 &fLast clear amount: &a" + lastCleared + " item/mob"));
                sender.sendMessage(color("&8【&aVelioraClearLag&8】 &fLast removed entities: &a" + lastEntities));
                sender.sendMessage(color("&8【&aVelioraClearLag&8】 &fLast clear time: &a" + (lastClearMillis == 0 ? "never" : TimeUtil.formatSeconds((System.currentTimeMillis() - lastClearMillis) / 1000) + " ago")));
            }
            default -> sender.sendMessage(color("&8【&aVelioraClearLag&8】 &f/vclearlag clear, status, timer, reload"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("clear", "status", "timer", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private String message(String path) {
        return configFile.get().getString("messages." + path, "&8【&aVelioraClearLag&8】 &cMessage not found: " + path);
    }

    private String color(String text) { return ColorUtil.color(text); }

    public record ClearResult(int totalAmount, int entities) { }
    private record ClusterResult(int removed) { }
}
