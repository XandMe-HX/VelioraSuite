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
        SLOTS.put(20, QuestCategory.MONSTER_HUNTER);
        SLOTS.put(22, QuestCategory.ANIMAL_HUNTER);
        SLOTS.put(24, QuestCategory.FISHING);
        SLOTS.put(29, QuestCategory.MINING);
        SLOTS.put(31, QuestCategory.FARMER);
        SLOTS.put(33, QuestCategory.CHEF);
        SLOTS.put(40, QuestCategory.WOODCUTTING);
    }

    private final QuestManager manager;

    public QuestGuiManager(QuestManager manager) { this.manager = manager; }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, manager.getConfigManager().color(manager.getConfigManager().getGuiTitle()));
        fill(inventory);
        PlayerQuestData data = manager.getDataManager().getOrCreate(player);
        for (Map.Entry<Integer, QuestCategory> entry : SLOTS.entrySet()) inventory.setItem(entry.getKey(), item(player, data, entry.getValue()));
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

    private void fill(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    private ItemStack item(Player player, PlayerQuestData data, QuestCategory category) {
        PlayerCategoryProgress progress = data.getCategoryProgress(category);
        Material icon = manager.getConfigManager().getCategoryIcon(category);
        ItemStack item = new ItemStack(icon == null ? Material.BOOK : icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(manager.getConfigManager().color(manager.getConfigManager().getCategoryDisplayName(category)));
            List<String> lore = new ArrayList<>();
            int nextLevel = progress.getLevel() + 1;
            int milestoneMultiplier = manager.getConfigManager().getMilestoneRewardMultiplier(nextLevel);
            lore.add("&8&m------------------------");
            lore.add("&7Level quest: &f" + progress.getLevel());
            lore.add("&7Target: &f" + progress.getCurrentProgress() + "/" + progress.getCurrentTarget());
            lore.add("&7Biaya mulai: &b" + manager.getSkillsHook().getQuestManaCost(progress.getLevel()) + " Mana");
            lore.add(" ");
            lore.add("&aHadiah setiap selesai:");
            lore.add("&7• Uang: &a" + progress.getCurrentRewardMoney());
            lore.add("&7• Item: &f" + manager.getConfigManager().formatItemRewards(manager.getConfigManager().getBaseItemRewards(category), 1));
            if (milestoneMultiplier > 0) {
                lore.add(" ");
                lore.add("&6Bonus saat mencapai level " + nextLevel + ":");
                lore.add("&7• Item: &f" + manager.getConfigManager().formatItemRewards(manager.getConfigManager().getMilestoneItemRewards(category), milestoneMultiplier));
                lore.add("&7• Max Mana: &b+" + manager.getConfigManager().getManaLevelBonus());
            }
            lore.add(" ");
            lore.add("&7Status: &f" + status(progress.getState()));
            lore.add(" ");
            if (progress.getState() == QuestState.READY_TO_CLAIM) lore.add("&aKlik untuk claim reward.");
            else if (progress.getState() == QuestState.ACTIVE) lore.add("&eKlik untuk lihat progress.");
            else lore.add("&aKlik untuk buka/start quest.");
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

    private String done(boolean value) { return value ? "Selesai" : "Belum"; }

    private String status(QuestState state) {
        return switch (state) {
            case NOT_STARTED -> "Belum dimulai";
            case ACTIVE -> "Sedang berjalan";
            case READY_TO_CLAIM -> "Siap diklaim";
            case CLAIMED -> "Selesai, pilih quest untuk lanjut";
        };
    }
}
