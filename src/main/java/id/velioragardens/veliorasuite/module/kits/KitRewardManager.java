package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.kits.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class KitRewardManager {

    private final VelioraSuite plugin;
    private final KitPurchaseManager purchaseManager;

    public KitRewardManager(VelioraSuite plugin, KitPurchaseManager purchaseManager) {
        this.plugin = plugin;
        this.purchaseManager = purchaseManager;
    }

    public boolean hasInventorySpace(Player player, Kit kit) {
        return getMissingSlots(player, kit) <= 0;
    }

    public int getMissingSlots(Player player, Kit kit) {
        RewardPlan plan = buildPlan(player, kit);
        return countMissingSlots(player.getInventory().getStorageContents(), plan.inventoryItems());
    }

    public boolean willReplaceArmor(Player player, Kit kit) {
        RewardPlan plan = buildPlan(player, kit);
        return !plan.oldArmorToStore().isEmpty();
    }

    public boolean hasArmorToEquip(Kit kit) {
        for (ItemStack item : kit.getItems()) {
            if (armorSlot(item) != ArmorSlot.NONE) return true;
        }
        return false;
    }

    public void giveKit(Player player, Kit kit, boolean dropExtraItems) {
        RewardPlan plan = buildPlan(player, kit);
        PlayerInventory inventory = player.getInventory();

        for (ItemStack oldArmor : plan.oldArmorToStore()) {
            addOrDrop(player, oldArmor, dropExtraItems);
        }

        for (Map.Entry<ArmorSlot, ItemStack> entry : plan.armorToEquip().entrySet()) {
            setArmor(inventory, entry.getKey(), entry.getValue().clone());
        }

        for (ItemStack item : plan.inventoryItems()) {
            addOrDrop(player, item, dropExtraItems);
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

    private RewardPlan buildPlan(Player player, Kit kit) {
        Map<ArmorSlot, ItemStack> armorToEquip = new EnumMap<>(ArmorSlot.class);
        List<ItemStack> inventoryItems = new ArrayList<>();
        List<ItemStack> oldArmorToStore = new ArrayList<>();

        for (ItemStack raw : kit.getItems()) {
            if (raw == null || raw.getType().isAir()) continue;
            ItemStack item = raw.clone();
            ArmorSlot slot = armorSlot(item);
            if (slot != ArmorSlot.NONE && !armorToEquip.containsKey(slot)) {
                armorToEquip.put(slot, item);
            } else {
                inventoryItems.add(item);
            }
        }

        PlayerInventory inventory = player.getInventory();
        for (ArmorSlot slot : armorToEquip.keySet()) {
            ItemStack old = getArmor(inventory, slot);
            if (old != null && !old.getType().isAir()) {
                oldArmorToStore.add(old.clone());
            }
        }

        List<ItemStack> allInventoryItems = new ArrayList<>();
        allInventoryItems.addAll(oldArmorToStore);
        allInventoryItems.addAll(inventoryItems);
        return new RewardPlan(armorToEquip, inventoryItems, oldArmorToStore, allInventoryItems);
    }

    private int countMissingSlots(ItemStack[] contents, List<ItemStack> requiredItems) {
        ItemStack[] simulated = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            simulated[i] = contents[i] == null ? null : contents[i].clone();
        }

        int missingSlots = 0;
        for (ItemStack required : requiredItems) {
            if (required == null || required.getType().isAir()) continue;
            int remaining = required.getAmount();

            for (ItemStack existing : simulated) {
                if (remaining <= 0) break;
                if (existing == null || existing.getType().isAir() || !existing.isSimilar(required)) continue;
                int space = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
                int add = Math.min(space, remaining);
                existing.setAmount(existing.getAmount() + add);
                remaining -= add;
            }

            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                ItemStack existing = simulated[i];
                if (existing != null && !existing.getType().isAir()) continue;
                int add = Math.min(required.getMaxStackSize(), remaining);
                ItemStack placed = required.clone();
                placed.setAmount(add);
                simulated[i] = placed;
                remaining -= add;
            }

            if (remaining > 0) {
                missingSlots += (int) Math.ceil(remaining / (double) Math.max(1, required.getMaxStackSize()));
            }
        }
        return missingSlots;
    }

    private void addOrDrop(Player player, ItemStack item, boolean dropExtraItems) {
        if (item == null || item.getType().isAir()) return;
        var leftover = player.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) {
            leftover.values().forEach(leftoverItem -> player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
            if (!dropExtraItems) {
                plugin.getLogger().warning("VelioraKits: inventory unexpectedly full after pre-check for " + player.getName() + ". Dropped leftover to avoid item loss.");
            }
        }
    }

    private ArmorSlot armorSlot(ItemStack item) {
        if (item == null) return ArmorSlot.NONE;
        Material material = item.getType();
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return ArmorSlot.HELMET;
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return ArmorSlot.CHESTPLATE;
        if (name.endsWith("_LEGGINGS")) return ArmorSlot.LEGGINGS;
        if (name.endsWith("_BOOTS")) return ArmorSlot.BOOTS;
        return ArmorSlot.NONE;
    }

    private ItemStack getArmor(PlayerInventory inventory, ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> inventory.getHelmet();
            case CHESTPLATE -> inventory.getChestplate();
            case LEGGINGS -> inventory.getLeggings();
            case BOOTS -> inventory.getBoots();
            case NONE -> null;
        };
    }

    private void setArmor(PlayerInventory inventory, ArmorSlot slot, ItemStack item) {
        switch (slot) {
            case HELMET -> inventory.setHelmet(item);
            case CHESTPLATE -> inventory.setChestplate(item);
            case LEGGINGS -> inventory.setLeggings(item);
            case BOOTS -> inventory.setBoots(item);
            case NONE -> { }
        }
    }

    private enum ArmorSlot {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        NONE
    }

    private record RewardPlan(Map<ArmorSlot, ItemStack> armorToEquip, List<ItemStack> inventoryItems, List<ItemStack> oldArmorToStore, List<ItemStack> allInventoryItems) {
    }
}
