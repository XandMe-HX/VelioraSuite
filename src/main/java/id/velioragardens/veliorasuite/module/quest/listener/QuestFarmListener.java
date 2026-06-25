package id.velioragardens.veliorasuite.module.quest.listener;

import id.velioragardens.veliorasuite.module.quest.QuestManager;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class QuestFarmListener implements Listener {

    private static final Set<Material> FARMLAND_BASE = Set.of(Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT, Material.ROOTED_DIRT);
    private final QuestManager manager;

    public QuestFarmListener(QuestManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        if (manager.getConfigManager().getMaterials(QuestCategory.FARMER, "harvest-materials").contains(event.getBlock().getType())) {
            manager.addProgress(event.getPlayer(), QuestCategory.FARMER, 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlant(BlockPlaceEvent event) {
        if (manager.getConfigManager().getMaterials(QuestCategory.FARMER, "plant-materials").contains(event.getItemInHand().getType())) {
            manager.addProgress(event.getPlayer(), QuestCategory.FARMER, 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHoe(PlayerInteractEvent event) {
        if (!manager.getConfigManager().isCountHoeFarmland()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemStack item = event.getItem();
        if (item == null || !Tag.ITEMS_HOES.isTagged(item.getType())) return;
        Block block = event.getClickedBlock();
        if (FARMLAND_BASE.contains(block.getType())) {
            manager.addProgress(event.getPlayer(), QuestCategory.FARMER, 1);
        }
    }
}
