package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.kits.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class KitRewardManager {

    private final VelioraSuite plugin;
    private final KitPurchaseManager purchaseManager;

    public KitRewardManager(VelioraSuite plugin, KitPurchaseManager purchaseManager) {
        this.plugin = plugin;
        this.purchaseManager = purchaseManager;
    }

    public boolean hasInventorySpace(Player player, Kit kit) {
        int requiredSlots = countRequiredSlots(kit.getItems());
        int emptySlots = 0;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                emptySlots++;
            }
        }

        return emptySlots >= requiredSlots;
    }

    public void giveKit(Player player, Kit kit, boolean dropExtraItems) {
        for (ItemStack item : kit.getItems()) {
            var leftover = player.getInventory().addItem(item.clone());
            if (!leftover.isEmpty() && dropExtraItems) {
                leftover.values().forEach(leftoverItem -> player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
            }
        }

        if (kit.getReward().getMoney() > 0) {
            purchaseManager.deposit(player, kit.getReward().getMoney());
        }

        if (kit.getReward().getExp() > 0) {
            player.giveExp(kit.getReward().getExp());
        }

        for (String command : kit.getReward().getCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("%player%", player.getName())
                    .replace("%kit%", kit.getId()));
        }
    }

    private int countRequiredSlots(List<ItemStack> items) {
        int slots = 0;

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            slots++;
        }

        return slots;
    }
}
