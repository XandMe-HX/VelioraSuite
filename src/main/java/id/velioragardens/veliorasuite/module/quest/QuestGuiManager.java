package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.quest.model.PlayerQuestData;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
import id.velioragardens.veliorasuite.module.quest.model.QuestState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuestGuiManager implements Listener {

    private static final Map<Integer, QuestCategory> SLOTS = new HashMap<>();
    static {
        SLOTS.put(10, QuestCategory.WOODCUTTING);
        SLOTS.put(12, QuestCategory.MINING);
        SLOTS.put(14, QuestCategory.FARMER);
        SLOTS.put(16, QuestCategory.CHEF);
        SLOTS.put(28, QuestCategory.MONSTER_HUNTER);
        SLOTS.put(30, QuestCategory.ANIMAL_HUNTER);
        SLOTS.put(32, QuestCategory.FISHING);
    }

    private final QuestManager manager;

    public QuestGuiManager(QuestManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, manager.getConfigManager().getGuiSize(), manager.getConfigManager().color(manager.getConfigManager().getGuiTitle()));
        PlayerQuestData data = manager.getDataManager().getOrCreate(player);
        for (Map.Entry<Integer, QuestCategory> entry : SLOTS.entrySet()) {
            inventory.setItem(entry.getKey(), item(player, data, entry.getValue()));
        }
        inventory.setItem(4, starterItem(data));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(manager.getConfigManager().color(manager.getConfigManager().getGuiTitle()))) return;
        event.setCancelled(true);
        QuestCategory category = SLOTS.get(event.getRawSlot());
        if (category == null) return;
        PlayerCategoryProgress progress = manager.getDataManager().getOrCreate(player).getCategoryProgress(category);
        if (progress.getState() == QuestState.READY_TO_CLAIM) manager.claimQuest(player, category);
        else if (progress.getState() == QuestState.ACTIVE) manager.sendProgress(player);
        else manager.startQuest(player, category);
        open(player);
    }

    private ItemStack item(Player player, PlayerQuestData data, QuestCategory category) {
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        Material icon = manager.getConfigManager().getCategoryIcon(category);
        ItemStack item = new ItemStack(icon == null ? Material.BOOK : icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color(manager.getConfigManager().getCategoryDisplayName(category)));
            List<String> lore = new ArrayList<>();
            lore.add("&7Level: &f" + progress.getLevel());
            lore.add("&7Progress: &f" + progress.getCurrentProgress() + "/" + progress.getCurrentTarget());
            lore.add("&7Mana Cost: &f" + manager.getSkillsHook().getQuestManaCost(progress.getLevel()));
            lore.add("&7Reward: &f" + progress.getCurrentRewardMoney());
            lore.add("&7Completed: &f" + progress.getCompletedCount());
            lore.add("&7Status: &f" + progress.getState().name());
            lore.add(" ");
            if (progress.getState() == QuestState.READY_TO_CLAIM) lore.add("&aKlik untuk claim reward.");
            else if (progress.getState() == QuestState.ACTIVE) lore.add("&eKlik untuk lihat progress.");
            else lore.add("&aKlik untuk mulai quest.");
            meta.setLore(lore.stream().map(manager.getConfigManager()::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack starterItem(PlayerQuestData data) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color("&aStarter Quest"));
            List<String> lore = List.of(
                    "&7Claim Land: &f" + done(data.isClaimLand()),
                    "&7Set Home: &f" + done(data.isSetHome()),
                    "&7Kit Starter: &f" + done(data.isStarterKit()),
                    "&7Status: &f" + (data.isStarterDone() ? "SELESAI" : "BELUM")
            );
            meta.setLore(lore.stream().map(manager.getConfigManager()::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String done(boolean value) {
        return value ? "Selesai" : "Belum";
    }
}
