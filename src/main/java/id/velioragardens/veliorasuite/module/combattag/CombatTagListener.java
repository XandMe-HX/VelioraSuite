package id.velioragardens.veliorasuite.module.combattag;

import id.velioragardens.veliorasuite.VelioraSuite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** PvP-only visual combat tag. It does not cancel logout or modify player data. */
final class CombatTagListener implements Listener {
    private final VelioraSuite plugin;
    private final Map<UUID, Long> taggedUntil = new HashMap<>();
    private FileConfiguration config;
    private BukkitTask task;

    CombatTagListener(VelioraSuite plugin) { this.plugin = plugin; }
    void load() { config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/combat-tag.yml")); }
    void start() { stop(); task=Bukkit.getScheduler().runTaskTimer(plugin,this::update,20L,20L); }
    void stop() { if(task!=null){task.cancel();task=null;} taggedUntil.clear(); }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player player ? player : null;
        if (attacker == null || attacker.equals(victim) || !config.getBoolean("settings.enabled", true)) return;
        tag(attacker); tag(victim);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { taggedUntil.remove(event.getPlayer().getUniqueId()); }

    private void tag(Player player) {
        long until=System.currentTimeMillis()+Math.max(1L,config.getLong("settings.duration-seconds",15L))*1000L;
        taggedUntil.put(player.getUniqueId(),until);
    }
    private void update() {
        long now=System.currentTimeMillis();
        taggedUntil.entrySet().removeIf(entry -> entry.getValue()<=now || Bukkit.getPlayer(entry.getKey())==null);
        for (Map.Entry<UUID,Long> entry : taggedUntil.entrySet()) {
            Player player=Bukkit.getPlayer(entry.getKey()); if(player==null||!player.isOnline())continue;
            long seconds=Math.max(1L,(entry.getValue()-now+999L)/1000L);
            player.sendActionBar(Component.text("⚔ ",NamedTextColor.RED)
                .append(Component.text("DALAM PERTARUNGAN ",NamedTextColor.GOLD))
                .append(Component.text("("+seconds+"s)",NamedTextColor.YELLOW)));
        }
    }
}
