package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.module.boss.model.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class BossBarManager {

    private final BossConfigManager config;
    private BossBar bar;

    public BossBarManager(BossConfigManager config) {
        this.config = config;
    }

    public void create(BossDefinition definition) {
        clear();
        if (!config.bossBarEnabled()) return;
        BarColor color = config.colorBossBarByRarity() ? definition.rarity().barColor() : BarColor.RED;
        bar = Bukkit.createBossBar("", color, config.bossBarStyle());
        bar.setVisible(true);
    }

    public void tick(BossDefinition definition, LivingEntity boss, long despawnAt) {
        if (bar == null || boss == null || boss.isDead()) return;
        double health = Math.max(0.0D, boss.getHealth());
        double max = Math.max(1.0D, boss.getMaxHealth());
        String title = config.bossBarTitle()
                .replace("%boss%", config.color(definition.displayName()))
                .replace("%health%", String.valueOf((int) Math.ceil(health)))
                .replace("%max_health%", String.valueOf((int) Math.ceil(max)))
                .replace("%time%", timeLeft(despawnAt));
        bar.setTitle(config.color(title));
        bar.setProgress(Math.max(0.0D, Math.min(1.0D, health / max)));
        updatePlayers(boss.getLocation());
    }

    public void clear() {
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        bar = null;
    }

    private void updatePlayers(Location location) {
        if (bar == null || location == null || location.getWorld() == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(location.getWorld())) {
                bar.removePlayer(player);
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= config.bossBarRadius() * config.bossBarRadius()) bar.addPlayer(player);
            else bar.removePlayer(player);
        }
    }

    private String timeLeft(long target) {
        long seconds = Math.max(0L, (target - System.currentTimeMillis()) / 1000L);
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }
}
