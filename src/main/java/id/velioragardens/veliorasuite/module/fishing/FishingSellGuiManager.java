package id.velioragardens.veliorasuite.module.fishing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class FishingSellGuiManager implements Listener {

    private final FishingManager manager;

    public FishingSellGuiManager(FishingManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        SellHolder holder = new SellHolder();
        int size = manager.getConfigManager().getSellGuiSize();
        Inventory inventory = Bukkit.createInventory(holder, size, manager.getConfigManager().color(manager.getConfigManager().getSellGuiTitle()));
        holder.inventory = inventory;
        drawButtons(inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellHolder holder)) return;

        int rawSlot = event.getRawSlot();
        int topSize = top.getSize();

        if (rawSlot >= 0 && rawSlot < topSize) {
            if (isControlSlot(top, rawSlot)) {
                event.setCancelled(true);
                if (rawSlot == confirmSlot(top)) sellAndClose(player, top, holder);
                else if (rawSlot == backSlot(top)) {
                    returnUnsold(player, top);
                    holder.soldOrReturned = true;
                    manager.openMainGui(player);
                } else if (rawSlot == closeSlot(top)) {
                    player.closeInventory();
                }
                return;
            }

            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isSellable(cursor)) {
                event.setCancelled(true);
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("sell-only-fish", "%prefix% &eHanya ikan yang bisa dimasukkan ke menu sell.")));
            }
            return;
        }

        if (event.isShiftClick()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            event.setCancelled(true);
            if (!isSellable(clicked)) {
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("sell-only-fish", "%prefix% &eHanya ikan yang bisa dimasukkan ke menu sell.")));
                return;
            }
            int moved = moveToInputSlots(top, clicked.clone());
            if (moved > 0) {
                clicked.setAmount(clicked.getAmount() - moved);
                player.updateInventory();
            } else {
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("sell-gui-full", "%prefix% &eMenu sell sudah penuh.")));
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellHolder)) return;
        int topSize = top.getSize();
        boolean affectsTop = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                affectsTop = true;
                if (isControlSlot(top, rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (affectsTop && !isSellable(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SellHolder holder)) return;
        if (holder.soldOrReturned) return;
        if (event.getPlayer() instanceof Player player) returnUnsold(player, inventory);
    }

    private void sellAndClose(Player player, Inventory inventory, SellHolder holder) {
        List<ItemStack> sellable = new ArrayList<>();
        for (int slot = 0; slot < inputLimit(inventory); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (isSellable(item)) sellable.add(item.clone());
        }
        if (sellable.isEmpty()) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("sell-empty", "%prefix% &eTidak ada ikan yang bisa dijual.")));
            return;
        }

        if (!manager.sell(player, sellable)) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("sell-failed-returned", "%prefix% &eSell dibatalkan. Ikan sudah dikembalikan ke inventory.")));
            returnUnsold(player, inventory);
            holder.soldOrReturned = true;
            player.closeInventory();
            return;
        }

        clearInputSlots(inventory);
        holder.soldOrReturned = true;
        player.closeInventory();
    }

    private void returnUnsold(Player player, Inventory inventory) {
        for (int slot = 0; slot < inputLimit(inventory); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            returnItem(player, item.clone());
            inventory.setItem(slot, null);
        }
    }

    private void clearInputSlots(Inventory inventory) {
        for (int slot = 0; slot < inputLimit(inventory); slot++) inventory.setItem(slot, null);
    }

    private int moveToInputSlots(Inventory inventory, ItemStack source) {
        int moved = 0;
        int remaining = source.getAmount();

        for (int slot = 0; slot < inputLimit(inventory) && remaining > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir()) continue;
            if (!existing.isSimilar(source)) continue;
            int max = Math.min(existing.getMaxStackSize(), 64);
            int space = max - existing.getAmount();
            if (space <= 0) continue;
            int add = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + add);
            remaining -= add;
            moved += add;
        }

        for (int slot = 0; slot < inputLimit(inventory) && remaining > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;
            ItemStack copy = source.clone();
            int add = Math.min(Math.min(copy.getMaxStackSize(), 64), remaining);
            copy.setAmount(add);
            inventory.setItem(slot, copy);
            remaining -= add;
            moved += add;
        }
        return moved;
    }

    private void drawButtons(Inventory inventory) {
        inventory.setItem(backSlot(inventory), button(Material.ARROW, "&aBack", List.of("&7Kembali ke menu fishing.")));
        inventory.setItem(confirmSlot(inventory), button(Material.EMERALD_BLOCK, "&aSell Fish", List.of("&7Klik untuk menjual semua ikan", "&7yang kamu taruh di menu ini.")));
        inventory.setItem(closeSlot(inventory), button(Material.BARRIER, "&cClose", List.of("&7Tutup menu.", "&7Ikan yang belum dijual akan dikembalikan.")));
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color(name));
            meta.setLore(lore.stream().map(manager.getConfigManager()::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private int inputLimit(Inventory inventory) {
        return Math.max(0, inventory.getSize() - 9);
    }

    private int backSlot(Inventory inventory) {
        return inventory.getSize() - 9;
    }

    private int confirmSlot(Inventory inventory) {
        return inventory.getSize() - 5;
    }

    private int closeSlot(Inventory inventory) {
        return inventory.getSize() - 1;
    }

    private boolean isControlSlot(Inventory inventory, int slot) {
        return slot >= inputLimit(inventory);
    }

    private boolean isSellable(ItemStack item) {
        return item != null && !item.getType().isAir()
                && (manager.getItemFactory().isCustomFish(item)
                || (manager.getConfigManager().isVanillaFishSellAllowed() && manager.getConfigManager().isVanillaFish(item.getType())));
    }

    private void returnItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private static final class SellHolder implements InventoryHolder {
        private Inventory inventory;
        private boolean soldOrReturned;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
