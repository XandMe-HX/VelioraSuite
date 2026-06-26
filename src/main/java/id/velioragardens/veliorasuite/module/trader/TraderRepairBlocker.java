package id.velioragardens.veliorasuite.module.trader;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class TraderRepairBlocker implements Listener {

    private final TraderConfigManager configManager;
    private final TraderItemFactory itemFactory;

    public TraderRepairBlocker(TraderConfigManager configManager, TraderItemFactory itemFactory) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (!configManager.isRepairBlockEnabled()) return;
        for (ItemStack item : event.getInventory().getContents()) {
            if (itemFactory.isUnrepairable(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (!configManager.isRepairBlockEnabled()) return;
        for (ItemStack item : event.getInventory().getContents()) {
            if (itemFactory.isUnrepairable(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (!configManager.isRepairBlockEnabled()) return;
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (itemFactory.isUnrepairable(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!configManager.isRepairBlockEnabled()) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!itemFactory.isUnrepairable(hand)) return;
        String message = event.getMessage().toLowerCase(Locale.ROOT).replaceFirst("/", "").trim();
        String root = message.split(" ")[0];
        for (String blocked : configManager.getBlockedRepairCommands()) {
            if (root.equals(blocked.toLowerCase(Locale.ROOT))) {
                event.setCancelled(true);
                player.sendMessage(configManager.color(configManager.getPrefix() + "&cItem trader ini tidak bisa direpair."));
                return;
            }
        }
    }
}
