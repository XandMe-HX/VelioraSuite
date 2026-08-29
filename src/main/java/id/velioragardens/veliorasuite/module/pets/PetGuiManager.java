package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.pets.model.OwnedPet;
import id.velioragardens.veliorasuite.module.pets.model.PetDefinition;
import id.velioragardens.veliorasuite.module.pets.model.PlayerPetData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PetGuiManager implements Listener {
    private final VelioraSuite plugin;
    private final PetManager manager;
    private final PetConfigManager config;
    private final NamespacedKey actionKey;
    private final NamespacedKey petKey;

    public PetGuiManager(VelioraSuite plugin, PetManager manager, PetConfigManager config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.actionKey = new NamespacedKey(plugin, "veliorapets_gui_action");
        this.petKey = new NamespacedKey(plugin, "veliorapets_gui_pet");
    }

    public void openMain(Player player) {
        Inventory inventory = menu(27, "&8Veliora Pets", "main", null);
        inventory.setItem(10, item(Material.NAME_TAG, "&dActive Pet", List.of("&7Lihat dan kelola pet aktif."), "list", null));
        inventory.setItem(11, item(Material.CHEST, "&ePet List", List.of("&7Lihat pet yang kamu miliki."), "list", null));
        inventory.setItem(12, item(Material.EMERALD, "&aPet Shop", List.of("&7Beli pet langsung."), "shop", null));
        inventory.setItem(13, item(Material.ENDER_CHEST, "&bPet Gacha", List.of("&7Harga: &f" + config.formatMoney(config.gachaPrice())), "gacha", null));
        inventory.setItem(14, item(Material.BARREL, "&6Pet Storage", List.of("&7Storage bersama semua pet.", "&7Kapasitas: &f27 slot"), "storage", null));
        inventory.setItem(15, item(Material.LEAD, "&cDismiss Pet", List.of("&7Simpan pet aktif."), "dismiss", null));
        inventory.setItem(16, item(Material.GOLDEN_CARROT, "&6Beri Makan Pet", List.of("&7Pilih pet lalu taruh makanan", "&7di GUI makan yang aman."), "feed_menu", null));
        player.openInventory(inventory);
    }

    public void openShop(Player player) {
        Inventory inventory = menu(54, "&8Pet Shop", "shop", null);
        int slot = 0;
        PlayerPetData pdata = manager.playerData(player.getUniqueId());
        for (PetDefinition pet : config.pets().values()) {
            if (slot >= 54) break;
            List<String> lore = new ArrayList<>();
            lore.add("&7Rarity: " + pet.rarity().color() + pet.rarity().name());
            lore.add("&7Harga: &f" + config.formatMoney(pet.price()));
            lore.add("&7Food: &f" + pet.foodMaterial().name() + " &8(+" + pet.feedExp() + " EXP)");
            lore.add("&7Damage: &f" + pet.damage());
            lore.add("&7Shared Storage: &f" + PetDataManager.SHARED_STORAGE_SIZE + " slot");
            lore.add(pdata.owns(pet.id()) ? "&eSudah dimiliki." : "&aKlik untuk beli.");
            inventory.setItem(slot++, item(pet.icon(), pet.displayName(), lore, pdata.owns(pet.id()) ? null : "confirm", pet.id()));
        }
        player.openInventory(inventory);
    }

    public void openConfirm(Player player, String petId) {
        PetDefinition pet = config.pets().get(petId);
        if (pet == null) return;
        Inventory inventory = menu(27, "&8Confirm Pet Buy", "confirm", petId);
        inventory.setItem(11, item(Material.LIME_WOOL, "&aBeli " + pet.displayName(), List.of("&7Harga: &f" + config.formatMoney(pet.price())), "buy", petId));
        inventory.setItem(15, item(Material.RED_WOOL, "&cBatal", List.of("&7Kembali ke shop."), "shop", null));
        player.openInventory(inventory);
    }

    public void openGacha(Player player) {
        Inventory inventory = menu(27, "&8Pet Gacha", "gacha", null);
        inventory.setItem(13, item(Material.ENDER_CHEST, "&bMulai Gacha", List.of("&7Harga: &f" + config.formatMoney(config.gachaPrice()), "&7Animasi ringan, tidak lag."), "gacha_start", null));
        player.openInventory(inventory);
    }

    public void animateGacha(Player player, Runnable finish) {
        Inventory inventory = menu(27, "&8Pet Gacha Rolling", "rolling", null);
        player.openInventory(inventory);
        List<PetDefinition> pets = new ArrayList<>(config.pets().values());
        if (pets.isEmpty()) return;
        new BukkitRunnable() {
            private int ticks;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                PetDefinition random = pets.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pets.size()));
                inventory.setItem(13, item(random.icon(), random.displayName(), List.of("&7Rolling..."), null, null));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.6F);
                ticks++;
                if (ticks >= 20) {
                    cancel();
                    finish.run();
                    player.closeInventory();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void openList(Player player) {
        Inventory inventory = menu(54, "&8Pet List", "list", null);
        PlayerPetData pdata = manager.playerData(player.getUniqueId());
        int slot = 0;
        for (OwnedPet owned : pdata.owned().values()) {
            PetDefinition pet = config.pets().get(owned.id());
            if (pet == null || slot >= 54) continue;
            inventory.setItem(slot++, item(pet.icon(), pet.displayName(), List.of("&7Level: &f" + owned.level(), "&7EXP: &f" + owned.exp(), "&7Food: &f" + pet.foodMaterial().name(), "&aKlik untuk summon."), "summon", pet.id()));
        }
        if (slot == 0) inventory.setItem(22, item(Material.BARRIER, "&cBelum punya pet", List.of("&7Beli di shop atau gacha dulu."), null, null));
        player.openInventory(inventory);
    }

    public void openFeedMenu(Player player) {
        Inventory inventory = menu(54, "&8Pilih Pet untuk Diberi Makan", "feed_menu", null);
        int slot = 0;
        for (OwnedPet owned : manager.playerData(player.getUniqueId()).owned().values()) {
            PetDefinition pet = config.pets().get(owned.id());
            if (pet == null || slot >= 54) continue;
            inventory.setItem(slot++, item(pet.icon(), pet.displayName(), List.of("&7Level: &f" + owned.level(), "&7Makanan: &f" + pet.foodMaterial().name(), "&aKlik untuk membuka tempat makan."), "feed_select", pet.id()));
        }
        if (slot == 0) inventory.setItem(22, item(Material.BARRIER, "&cBelum punya pet", List.of("&7Beli pet terlebih dahulu."), null, null));
        player.openInventory(inventory);
    }

    public void openFeeder(Player player, String petId) {
        PetDefinition pet = config.pets().get(petId);
        if (pet == null || manager.playerData(player.getUniqueId()).get(petId) == null) return;
        Inventory inventory = menu(27, "&8Makan: " + config.plain(pet.displayName()), "feeding", petId);
        for (int slot = 0; slot < inventory.getSize(); slot++) if (slot != 13) inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), null, null));
        inventory.setItem(4, item(pet.icon(), pet.displayName(), List.of("&7Masukkan maksimal " + config.maxFeedAmount() + " item.", "&7Makanan: &f" + pet.foodMaterial().name()), null, null));
        inventory.setItem(13, null);
        player.openInventory(inventory);
    }

    public void openStorage(Player viewer, UUID ownerUuid, String ownerName, boolean readOnly) {
        String title = readOnly ? "&8Pet Storage - " + ownerName : "&8Pet Storage";
        PetMenuHolder holder = new PetMenuHolder("storage", null, ownerUuid, readOnly);
        Inventory inventory = Bukkit.createInventory(holder, PetDataManager.SHARED_STORAGE_SIZE, config.color(title));
        holder.inventory = inventory;
        List<ItemStack> items = manager.data().loadStorage(ownerUuid);
        for (int i = 0; i < Math.min(inventory.getSize(), items.size()); i++) inventory.setItem(i, items.get(i));
        viewer.openInventory(inventory);
        if (!readOnly) {
            viewer.sendMessage(config.color(config.message("pet-storage-open", "%prefix% &aMembuka shared pet storage.")));
        }
    }

    public void saveAndCloseOpenStorages() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (!(topInventory.getHolder() instanceof PetMenuHolder holder)) continue;
            if (!"storage".equals(holder.type)) continue;
            if (!holder.readOnly && holder.storageOwner != null) {
                manager.saveStorage(holder.storageOwner, topInventory);
            }
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PetMenuHolder holder)) return;
        if ("feeding".equals(holder.type)) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.isShiftClick() || (event.getRawSlot() < topSize && event.getRawSlot() != 13)) event.setCancelled(true);
            return;
        }
        if ("storage".equals(holder.type)) {
            if (holder.readOnly) event.setCancelled(true);
            else scheduleStorageSave(holder, event.getView().getTopInventory());
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String petId = clicked.getItemMeta().getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "list" -> manager.openList(player);
            case "shop" -> manager.openShop(player);
            case "gacha" -> manager.openGacha(player);
            case "storage" -> manager.openStorage(player);
            case "dismiss" -> manager.dismiss(player, true);
            case "feed_menu" -> openFeedMenu(player);
            case "feed_select" -> openFeeder(player, petId);
            case "confirm" -> openConfirm(player, petId);
            case "buy" -> { manager.buy(player, petId); openShop(player); }
            case "gacha_start" -> manager.startGacha(player);
            case "summon" -> { manager.summon(player, petId); player.closeInventory(); }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PetMenuHolder holder)) return;
        if ("feeding".equals(holder.type)) {
            if (event.getRawSlots().stream().anyMatch(slot -> slot != 13)) event.setCancelled(true);
            return;
        }
        if (!"storage".equals(holder.type)) return;
        if (holder.readOnly) event.setCancelled(true);
        else scheduleStorageSave(holder, event.getView().getTopInventory());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof PetMenuHolder holder)) return;
        if ("feeding".equals(holder.type)) {
            ItemStack offered = event.getInventory().getItem(13);
            ItemStack returned = manager.feedFromGui((Player) event.getPlayer(), holder.petId, offered);
            if (returned != null) {
                java.util.Map<Integer, ItemStack> leftovers = ((Player) event.getPlayer()).getInventory().addItem(returned);
                leftovers.values().forEach(item -> ((Player) event.getPlayer()).getWorld().dropItemNaturally(((Player) event.getPlayer()).getLocation(), item));
            }
            return;
        }
        if (!"storage".equals(holder.type) || holder.storageOwner == null || holder.readOnly) return;
        manager.saveStorage(holder.storageOwner, event.getInventory());
    }

    private void scheduleStorageSave(PetMenuHolder holder, Inventory inventory) {
        if (holder.storageOwner == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> manager.saveStorage(holder.storageOwner, inventory));
    }

    private Inventory menu(int size, String title, String type, String petId) {
        PetMenuHolder holder = new PetMenuHolder(type, petId);
        Inventory inventory = Bukkit.createInventory(holder, size, config.color(title));
        holder.inventory = inventory;
        return inventory;
    }

    private ItemStack item(Material material, String name, List<String> lore, String action, String petId) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(config.color(name));
        List<String> colored = new ArrayList<>();
        for (String line : lore) colored.add(config.color(line));
        meta.setLore(colored);
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (petId != null) meta.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, petId);
        item.setItemMeta(meta);
        return item;
    }

    private static final class PetMenuHolder implements InventoryHolder {
        private final String type;
        private final String petId;
        private final UUID storageOwner;
        private final boolean readOnly;
        private Inventory inventory;
        private PetMenuHolder(String type, String petId) { this(type, petId, null, false); }
        private PetMenuHolder(String type, String petId, UUID storageOwner, boolean readOnly) {
            this.type = type;
            this.petId = petId;
            this.storageOwner = storageOwner;
            this.readOnly = readOnly;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
