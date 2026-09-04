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
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            if (previewHolder.isBackSlot(event.getRawSlot())) {
                kitsManager.openGui(player);
            } else if (previewHolder.isClaimSlot(event.getRawSlot())) {
                player.closeInventory();
                kitsManager.claimKitFromGui(player, previewHolder.kitId());
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
        if("premium".equals(action)) { kitsManager.openPremiumGui(player); return; }
        if("main".equals(action)) { kitsManager.openGui(player); return; }
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
            kitsManager.previewKit(player, kitId);
            player.sendMessage(org.bukkit.ChatColor.AQUA + "Periksa isinya, lalu tekan tombol hijau AMBIL KIT.");
            return;
        }

        if (click.isRightClick()) {
            kitsManager.previewKit(player, kitId);
            return;
        }

        if (click.isLeftClick()) {
            kitsManager.previewKit(player, kitId);
            player.sendMessage(org.bukkit.ChatColor.YELLOW + "Tekan tombol hijau AMBIL KIT jika sudah yakin.");
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
