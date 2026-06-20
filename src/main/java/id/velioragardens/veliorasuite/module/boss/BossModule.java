package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BossModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {
    private UUID activeBoss;
    private String activeBossId;
    private BossBar bossBar;
    private BukkitTask updateTask;
    private final Map<UUID, Double> damage = new HashMap<>();

    public BossModule(VelioraSuite plugin) { super(plugin, "boss", "boss"); }
    @Override protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand c = plugin.getCommand("vboss"); if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        plugin.getLogger().info("VelioraBoss module started.");
    }
    @Override protected void onDisable() { HandlerList.unregisterAll(this); if (bossBar != null) bossBar.removeAll(); if (updateTask != null) updateTask.cancel(); plugin.getLogger().info("VelioraBoss module stopped."); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veliorasuite.boss.admin")) { sender.sendMessage(color(msg("no-permission", Map.of()))); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) { sender.sendMessage(color("&8【&aVelioraBoss&8】 &fActive: &a" + (activeBoss != null ? activeBossId : "none"))); return true; }
        if (args[0].equalsIgnoreCase("reload")) { configFile.reload(); sender.sendMessage(color(msg("reload", Map.of()))); return true; }
        if (args[0].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Only player can spawn boss here."); return true; }
            String id = args.length >= 2 ? args[1] : configFile.get().getString("settings.default-boss", "ancient_guardian");
            spawn(player.getLocation(), id, true); return true;
        }
        if (args[0].equalsIgnoreCase("kill")) { killActive(); sender.sendMessage(color("&8【&aVelioraBoss&8】 &cActive boss removed.")); return true; }
        sender.sendMessage(color("&8【&aVelioraBoss&8】 &f/vboss spawn <id>, status, kill, reload")); return true;
    }

    private void spawn(Location location, String id, boolean broadcast) {
        if (activeBoss != null) killActive();
        String path = "bosses." + id;
        EntityType type = EntityType.valueOf(configFile.get().getString(path + ".type", "ZOMBIE").toUpperCase(Locale.ROOT));
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity living)) return;
        String name = color(configFile.get().getString(path + ".name", "&cVeliora Boss"));
        double health = configFile.get().getDouble(path + ".health", 300);
        living.setCustomName(name); living.setCustomNameVisible(true); living.addScoreboardTag("veliora_boss"); living.addScoreboardTag("veliora_protected");
        try { living.setMaxHealth(health); } catch (Throwable ignored) { }
        living.setHealth(Math.min(health, living.getMaxHealth()));
        activeBoss = living.getUniqueId(); activeBossId = id; damage.clear();
        bossBar = Bukkit.createBossBar(name, BarColor.RED, BarStyle.SEGMENTED_10); bossBar.setProgress(1.0); Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateBar(living), 20L, 20L);
        if (broadcast) Bukkit.broadcastMessage(color(msg("spawn", Map.of("boss", strip(name)))));
    }

    private void updateBar(LivingEntity living) {
        if (living.isDead() || !living.isValid()) { finishBoss(null); return; }
        double max = living.getMaxHealth();
        double progress = Math.max(0.0, Math.min(1.0, living.getHealth() / max));
        if (bossBar != null) { bossBar.setProgress(progress); Bukkit.getOnlinePlayers().forEach(p -> { if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p); }); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (activeBoss == null || !e.getEntity().getUniqueId().equals(activeBoss)) return;
        Player player = e.getDamager() instanceof Player p ? p : null;
        if (player == null) return;
        damage.put(player.getUniqueId(), damage.getOrDefault(player.getUniqueId(), 0.0) + e.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) { if (activeBoss != null && e.getEntity().getUniqueId().equals(activeBoss)) finishBoss(e.getEntity().getKiller()); }

    private void finishBoss(Player killer) {
        if (activeBoss == null) return;
        String id = activeBossId == null ? "boss" : activeBossId;
        Economy eco = plugin.getHookManager().getEconomy();
        damage.entrySet().stream().sorted(Map.Entry.<UUID,Double>comparingByValue(Comparator.reverseOrder())).limit(3).forEach(entry -> {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                double money = configFile.get().getDouble("bosses." + id + ".rewards.top-damage-money", 500);
                if (eco != null && money > 0) eco.depositPlayer(p, money);
                p.sendMessage(color(msg("top-damage-reward", Map.of("money", String.valueOf((int) money)))));
            }
        });
        Bukkit.broadcastMessage(color(msg("defeated", Map.of("boss", id))));
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        activeBoss = null; activeBossId = null; damage.clear();
    }

    private void killActive() { if (activeBoss != null) { Entity e = Bukkit.getEntity(activeBoss); if (e != null) e.remove(); finishBoss(null); } }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if(args.length==1) return List.of("spawn","status","kill","reload"); if(args.length==2 && args[0].equalsIgnoreCase("spawn")) { ConfigurationSection s=configFile.get().getConfigurationSection("bosses"); return s==null?List.of():s.getKeys(false).stream().toList(); } return List.of(); }
    private String color(String s) { return ColorUtil.color(s); }
    private String strip(String s) { return s.replaceAll("§[0-9A-FK-ORa-fk-or]", ""); }
    private String msg(String key, Map<String,String> vars) { String s=configFile.get().getString("messages."+key,"&8【&aVelioraBoss&8】 &cMessage not found: "+key); for(var e:vars.entrySet()) s=s.replace("%"+e.getKey()+"%",e.getValue()); return s; }
}
