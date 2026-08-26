package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Activity hooks are batched to avoid a file write for every movement packet. */
public final class QuestProgressionListener implements Listener {
    private final QuestManager manager;
    private final Map<UUID, Double> agilityDistance = new HashMap<>();
    private final Map<Location, RecentBrewer> recentBrewers = new HashMap<>();

    public QuestProgressionListener(QuestManager manager) { this.manager = manager; }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;
        double moved = event.getFrom().distance(event.getTo());
        if (moved <= 0.0D || moved > 8.0D) return;
        Player player = event.getPlayer();
        double total = agilityDistance.getOrDefault(player.getUniqueId(), 0.0D) + moved;
        if (total >= 25.0D) {
            manager.addProgress(player, QuestCategory.AGILITY, (int) (total / 25.0D));
            total %= 25.0D;
        }
        agilityDistance.put(player.getUniqueId(), total);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        RecentBrewer recent = recentBrewers.remove(event.getBlock().getLocation());
        if (recent == null || System.currentTimeMillis() - recent.usedAt() > 120_000L) return;
        Player player = org.bukkit.Bukkit.getPlayer(recent.playerId());
        if (player != null && player.isOnline()) manager.addProgress(player, QuestCategory.ALCHEMY, 1);
    }

    /** Records who used a brewing stand so completed brews grant Alchemy XP to a real player. */
    @EventHandler(ignoreCancelled = true)
    public void onBrewingStandUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BREWING_STAND) return;
        recentBrewers.put(event.getClickedBlock().getLocation(), new RecentBrewer(event.getPlayer().getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionDrink(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.POTION) manager.addProgress(event.getPlayer(), QuestCategory.ALCHEMY, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player player) {
            manager.addProgress(player, QuestCategory.ARCHERY, 1);
        }
    }

    private record RecentBrewer(UUID playerId, long usedAt) { }
}
