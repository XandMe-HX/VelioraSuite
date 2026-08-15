package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class KitsListener implements Listener {

    private final VelioraSuite plugin;
    private final KitsManager kitsManager;

    public KitsListener(VelioraSuite plugin, KitsManager kitsManager) {
        this.plugin = plugin;
        this.kitsManager = kitsManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof KitPreviewManager.PreviewHolder previewHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()
                    && previewHolder.isBackSlot(event.getRawSlot())) {
                kitsManager.openGui(player);
            }
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof KitGuiManager.KitsGuiHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        String action = holder.getAction(event.getRawSlot());
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("help".equals(action)) {
            player.closeInventory();
            kitsManager.sendHelp(player);
            return;
        }

        String kitId = holder.getKitId(event.getRawSlot());
        if (kitId == null) {
            return;
        }

        ClickType click = event.getClick();

        if (click.isShiftClick()) {
            kitsManager.buyKit(player, kitId);
            return;
        }

        if (click.isRightClick()) {
            kitsManager.previewKit(player, kitId);
            return;
        }

        if (click.isLeftClick()) {
            kitsManager.claimKit(player, kitId);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitGuiManager.KitsGuiHolder
                || event.getView().getTopInventory().getHolder() instanceof KitPreviewManager.PreviewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> kitsManager.giveFirstJoinKit(player), 40L);
    }
}
