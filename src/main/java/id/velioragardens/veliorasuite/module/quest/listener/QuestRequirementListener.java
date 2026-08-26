package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestConfigManager;
import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Enforces only explicit global requirements. It deliberately checks material
 * rather than lore/PDC, so custom enchantments remain fully compatible. */
public final class QuestRequirementListener implements Listener {
    private final QuestManager manager;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public QuestRequirementListener(QuestManager manager) { this.manager = manager; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!allowed(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!allowed(event.getPlayer(), event.getItem(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player player = event.getDamager() instanceof Player direct ? direct
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (player != null && !allowed(player, player.getInventory().getItemInMainHand(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player
                && !allowed(player, player.getInventory().getItemInMainHand(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorEquip(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack candidate = event.getCursor();
        if (candidate == null || candidate.getType().isAir()) candidate = event.getCurrentItem();
        if (candidate == null || candidate.getType().isAir() || !isArmor(candidate.getType())) return;
        if (!allowed(player, candidate, true)) event.setCancelled(true);
    }

    private boolean allowed(Player player, ItemStack item, boolean armor) {
        if (item == null || item.getType().isAir() || !manager.getConfigManager().areRequirementsEnabled()) return true;
        for (QuestConfigManager.SkillRequirement requirement : manager.getConfigManager().getRequirements(item.getType(), armor)) {
            PlayerCategoryProgress progress = manager.getDataManager().getOrCreate(player).getCategoryProgress(requirement.category());
            if (progress != null && progress.getLevel() >= requirement.level()) continue;
            notice(player, requirement, item.getType());
            return false;
        }
        return true;
    }

    private void notice(Player player, QuestConfigManager.SkillRequirement requirement, Material item) {
        long now = System.currentTimeMillis();
        if (now - lastNotice.getOrDefault(player.getUniqueId(), 0L) < 1250L) return;
        lastNotice.put(player.getUniqueId(), now);
        String message = manager.getConfigManager().getRequirementMessage()
                .replace("%skill%", manager.getConfigManager().getCategoryDisplayName(requirement.category()))
                .replace("%level%", String.valueOf(requirement.level()))
                .replace("%item%", pretty(item));
        player.sendMessage(manager.getConfigManager().color(message));
    }

    private boolean isArmor(Material material) { return material.name().endsWith("_HELMET") || material.name().endsWith("_CHESTPLATE") || material.name().endsWith("_LEGGINGS") || material.name().endsWith("_BOOTS"); }
    private String pretty(Material material) { String[] words = material.name().toLowerCase().split("_"); StringBuilder result = new StringBuilder(); for (String word : words) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' '); return result.toString().trim(); }
}
