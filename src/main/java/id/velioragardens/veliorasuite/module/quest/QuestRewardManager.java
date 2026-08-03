package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.quest.model.QuestItemReward;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class QuestRewardManager {

    private final VelioraSuite plugin;
    private final QuestConfigManager configManager;
    private boolean vaultWarned;

    public QuestRewardManager(VelioraSuite plugin, QuestConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean depositMoney(Player player, int amount) {
        if (amount <= 0) return true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return warnMissingVault(player);
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return warnMissingVault(player);
            Object economy = registration.getProvider();
            Method deposit = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            deposit.invoke(economy, player, (double) amount);
            return true;
        } catch (Exception exception) {
            return warnMissingVault(player);
        }
    }

    /** Gives configured items; a full inventory drops only the overflow at the player's feet. */
    public void giveItems(Player player, List<QuestItemReward> rewards, int multiplier) {
        if (player == null || rewards.isEmpty() || multiplier <= 0) return;
        for (QuestItemReward reward : rewards) {
            int remaining = Math.max(1, reward.amount() * multiplier);
            int maxStack = Math.max(1, reward.material().getMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(maxStack, remaining);
                ItemStack stack = new ItemStack(reward.material(), amount);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                for (ItemStack extra : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
                remaining -= amount;
            }
        }
    }

    private boolean warnMissingVault(Player player) {
        if (!vaultWarned) {
            plugin.getLogger().warning("VelioraQuest: Vault economy tidak aktif, reward money quest dilewati.");
            vaultWarned = true;
        }
        if (player != null) {
            player.sendMessage(configManager.color(configManager.message("vault-not-found", "%prefix% &eVault economy belum aktif, reward money dilewati.")));
        }
        return false;
    }
}
