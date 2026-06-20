package id.velioragardens.veliorasuite.module.clearlag;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import id.velioragardens.veliorasuite.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClearLagModule extends AbstractModule implements CommandExecutor, TabCompleter {

    private BukkitTask timerTask;
    private int secondsLeft;
    private int lastCleared;
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
                int amount = clear(false);
                if (amount > 0) Bukkit.broadcastMessage(color(message("cleared").replace("%amount%", String.valueOf(amount))));
                else Bukkit.broadcastMessage(color(message("no-items")));
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

    public int clear(boolean manual) {
        int amount = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldAllowed(world)) continue;
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (shouldRemove(entity, manual)) {
                    entity.remove();
                    amount++;
                }
            }
        }
        lastCleared = amount;
        lastClearMillis = System.currentTimeMillis();
        return amount;
    }

    private boolean isWorldAllowed(World world) {
        List<String> disabled = configFile.get().getStringList("worlds.disabled-worlds");
        if (disabled.contains(world.getName())) return false;
        List<String> enabledWorlds = configFile.get().getStringList("worlds.enabled-worlds");
        return enabledWorlds.isEmpty() || enabledWorlds.contains(world.getName());
    }

    private boolean shouldRemove(Entity entity, boolean manual) {
        FileConfiguration cfg = configFile.get();
        if (entity instanceof Player) return false;
        if (protectEntity(entity)) return false;
        if (entity instanceof Item item) {
            if (!cfg.getBoolean("remove.dropped-items", true)) return false;
            int minAge = manual ? 0 : cfg.getInt("auto-clear.minimum-item-age-seconds", 300) * 20;
            if (item.getTicksLived() < minAge) return false;
            ItemStack stack = item.getItemStack();
            return stack == null || !cfg.getStringList("whitelist-items").contains(stack.getType().name());
        }
        if (entity instanceof ExperienceOrb) return cfg.getBoolean("remove.experience-orbs", true);
        if (entity instanceof Arrow) return cfg.getBoolean("remove.arrows", true);
        if (entity instanceof Snowball) return cfg.getBoolean("remove.snowballs", true);
        if (entity instanceof Egg) return cfg.getBoolean("remove.eggs", true);
        if (entity instanceof Fireball) return cfg.getBoolean("remove.fireballs", true);
        if (entity instanceof Projectile) return cfg.getBoolean("remove.other-projectiles", false);
        return false;
    }

    private boolean protectEntity(Entity entity) {
        FileConfiguration cfg = configFile.get();
        if (cfg.getBoolean("protect.armor-stands", true) && entity instanceof ArmorStand) return true;
        if (cfg.getBoolean("protect.villagers", true) && entity instanceof Villager) return true;
        if (cfg.getBoolean("protect.named-entities", true) && entity.getCustomName() != null && !entity.getCustomName().isBlank()) return true;
        if (cfg.getBoolean("protect.tamed-animals", true) && entity instanceof Tameable tameable && tameable.isTamed()) return true;
        if (cfg.getBoolean("protect.vehicles-with-passenger", true) && entity instanceof Vehicle && !entity.getPassengers().isEmpty()) return true;
        return entity instanceof LivingEntity living && living.getScoreboardTags().contains("veliora_protected");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.clearlag.admin")) { sender.sendMessage(color(message("no-permission"))); return true; }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "clear" -> {
                int amount = clear(true);
                sender.sendMessage(color(message("manual-clear").replace("%amount%", String.valueOf(amount)).replace("%player%", sender.getName())));
            }
            case "reload" -> {
                onReload();
                sender.sendMessage(color(message("reload")));
            }
            case "timer", "status" -> {
                sender.sendMessage(color("&8【&aVelioraClearLag&8】 &fNext clear: &a" + TimeUtil.formatSeconds(secondsLeft)));
                sender.sendMessage(color("&8【&aVelioraClearLag&8】 &fLast clear: &a" + lastCleared + " item"));
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
}
