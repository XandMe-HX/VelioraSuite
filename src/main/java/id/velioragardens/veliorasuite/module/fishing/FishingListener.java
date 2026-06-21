package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class FishingListener implements Listener {
    private final FishingManager manager;

    public FishingListener(FishingManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.getCaught() instanceof Item item) item.remove();
        event.setExpToDrop(0);
        manager.handleCaughtFish(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInMinigame(player)) return;
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            manager.processClick(player);
        }
    }

    @EventHandler
    public void onSellClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof FishingSellHolder && event.getPlayer() instanceof Player player) {
            manager.handleSellClose(player, event.getInventory());
        }
    }
}
