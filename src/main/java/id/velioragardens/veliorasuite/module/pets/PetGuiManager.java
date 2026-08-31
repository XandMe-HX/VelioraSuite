package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.gui.GuiLayout;
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
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
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
        inventory.setItem(10, item(Material.NAME_TAG, "&d&lPET AKTIF", List.of("&7Lihat daftar pet dan panggil", "&7pet yang ingin menemanimu."), "list", null));
        inventory.setItem(11, item(Material.CHEST, "&e&lDAFTAR PET", List.of("&7Lihat level, EXP, dan status", "&7seluruh pet milikmu."), "list", null));
        inventory.setItem(12, item(Material.EMERALD, "&a&lTOKO PET", List.of("&7Beli pet permanen dengan", "&7harga yang terlihat jelas."), "shop", null));
        inventory.setItem(13, item(Material.ENDER_CHEST, "&b&lGACHA PET", List.of("&7Harga: &f" + config.formatMoney(config.gachaPrice()), "&7Hadiah dipilih secara acak."), "gacha", null));
        inventory.setItem(14, item(Material.BARREL, "&6&lPENYIMPANAN PET", List.of("&7Penyimpanan bersama semua pet.", "&7Kapasitas: &f27 slot"), "storage", null));
        inventory.setItem(15, item(Material.GOLDEN_CARROT, "&6&lBERI MAKAN", List.of("&7Pilih pet, lalu letakkan makanan", "&7hanya pada slot makan yang tersedia."), "feed_menu", null));
        inventory.setItem(16, item(Material.LEAD, "&c&lSIMPAN PET AKTIF", List.of("&7Menghilangkan pet aktif tanpa", "&7menghapus data atau levelnya."), "dismiss", null));
        player.openInventory(inventory);
    }

    public void openShop(Player player) {
        openShop(player, 0);
    }

    private void openShop(Player player, int page) {
        List<PetDefinition> pets = new ArrayList<>(config.pets().values());
        int pages = pages(pets.size());
        int current = clampPage(page, pages);
        Inventory inventory = menu(54, "&8Veliora Pets &7• &aToko", "shop", null, current);
        PlayerPetData pdata = manager.playerData(player.getUniqueId());
        int start = current * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, pets.size());
        for (int index = start; index < end; index++) {
            PetDefinition pet = pets.get(index);
            List<String> lore = new ArrayList<>();
            lore.add("&8━━━━━━━━━━━━━━━━━━━━");
            lore.add("&7Kelangkaan: " + pet.rarity().color() + pet.rarity().name());
            lore.add("&7Harga: &f" + config.formatMoney(pet.price()));
            lore.add("&7Makanan: &f" + prettyMaterial(pet.foodMaterial()) + " &8(+" + pet.feedExp() + " EXP)");
            lore.add("&7Serangan: &f" + pet.damage());
            lore.add(pet.babyPet() ? "&dMode: &fBAYI permanen &8(tidak membesar)" : "&aMode: &fDewasa &8(tumbuh tiap 10 level)");
            lore.add("&7Tunggangan: " + (pet.rideable() ? "&aBisa setelah level " + pet.adultLevel() : "&cTidak bisa"));
            lore.add("&8━━━━━━━━━━━━━━━━━━━━");
            lore.add(pdata.owns(pet.id()) ? "&e✓ Kamu sudah memiliki pet ini." : "&aKlik untuk melihat konfirmasi pembelian.");
            inventory.setItem(CONTENT_SLOTS[index - start], item(pet.icon(), pet.displayName(), lore, pdata.owns(pet.id()) ? null : "confirm", pet.id()));
        }
        navigation(inventory, current, pages, "shop");
        player.openInventory(inventory);
    }

    public void openConfirm(Player player, String petId) {
        PetDefinition pet = config.pets().get(petId);
        if (pet == null) return;
        Inventory inventory = menu(27, "&8Konfirmasi Pembelian", "confirm", petId);
        inventory.setItem(11, item(Material.LIME_WOOL, "&a&lBELI " + pet.displayName(), List.of("&7Harga: &f" + config.formatMoney(pet.price()), "&7Pet akan masuk ke daftar milikmu.", "&eKlik untuk mengonfirmasi."), "buy", petId));
        inventory.setItem(15, item(Material.RED_WOOL, "&c&lBATAL", List.of("&7Kembali ke toko tanpa membeli."), "shop", null));
        player.openInventory(inventory);
    }

    public void openGacha(Player player) {
        Inventory inventory = menu(27, "&8Veliora Pets &7• &bGacha", "gacha", null);
        inventory.setItem(13, item(Material.ENDER_CHEST, "&b&lMULAI GACHA", List.of("&7Harga: &f" + config.formatMoney(config.gachaPrice()), "&7Animasi singkat dan aman untuk server.", "&eKlik untuk memulai."), "gacha_start", null));
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
        openList(player, 0);
    }

    private void openList(Player player, int page) {
        List<OwnedPet> ownedPets = new ArrayList<>(manager.playerData(player.getUniqueId()).owned().values());
        int pages = pages(ownedPets.size());
        int current = clampPage(page, pages);
        Inventory inventory = menu(54, "&8Veliora Pets &7• &eKoleksi", "list", null, current);
        PlayerPetData pdata = manager.playerData(player.getUniqueId());
        int start = current * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, ownedPets.size());
        for (int index = start; index < end; index++) {
            OwnedPet owned = ownedPets.get(index);
            PetDefinition pet = config.pets().get(owned.id());
            if (pet == null) continue;
            inventory.setItem(CONTENT_SLOTS[index - start], item(pet.icon(), pet.displayName(), List.of(
                    "&8━━━━━━━━━━━━━━━━━━━━", "&7Level: &f" + owned.level(), "&7EXP: &f" + owned.exp(),
                    pet.babyPet() ? "&dMode: &fBAYI permanen" : "&aMode: &fDewasa, tumbuh tiap 10 level",
                    "&7Makanan: &f" + prettyMaterial(pet.foodMaterial()),
                    pet.rideable() ? "&eTunggangan: &fLevel " + pet.adultLevel() : "&8Tidak bisa ditunggangi",
                    "&8━━━━━━━━━━━━━━━━━━━━", "&aKlik untuk memanggil pet ini."), "summon", pet.id()));
        }
        if (ownedPets.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "&cBelum punya pet", List.of("&7Beli di toko atau gunakan gacha dahulu."), null, null));
        navigation(inventory, current, pages, "list");
        player.openInventory(inventory);
    }

    public void openFeedMenu(Player player) {
        openFeedMenu(player, 0);
    }

    private void openFeedMenu(Player player, int page) {
        List<OwnedPet> ownedPets = new ArrayList<>(manager.playerData(player.getUniqueId()).owned().values());
        int pages = pages(ownedPets.size());
        int current = clampPage(page, pages);
        Inventory inventory = menu(54, "&8Veliora Pets &7• &6Beri Makan", "feed_menu", null, current);
        int start = current * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, ownedPets.size());
        for (int index = start; index < end; index++) {
            OwnedPet owned = ownedPets.get(index);
            PetDefinition pet = config.pets().get(owned.id());
            if (pet == null) continue;
            inventory.setItem(CONTENT_SLOTS[index - start], item(pet.icon(), pet.displayName(), List.of("&7Level: &f" + owned.level(), "&7Makanan: &f" + prettyMaterial(pet.foodMaterial()), "&aKlik untuk membuka tempat makan."), "feed_select", pet.id()));
        }
        if (ownedPets.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "&cBelum punya pet", List.of("&7Beli pet terlebih dahulu."), null, null));
        navigation(inventory, current, pages, "feed_menu");
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
            case "shop_prev" -> openShop(player, holder.page - 1);
            case "shop_next" -> openShop(player, holder.page + 1);
            case "list_prev" -> openList(player, holder.page - 1);
            case "list_next" -> openList(player, holder.page + 1);
            case "feed_menu_prev" -> openFeedMenu(player, holder.page - 1);
            case "feed_menu_next" -> openFeedMenu(player, holder.page + 1);
            case "pets_main" -> openMain(player);
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
        return menu(size, title, type, petId, 0);
    }

    private Inventory menu(int size, String title, String type, String petId, int page) {
        PetMenuHolder holder = new PetMenuHolder(type, petId, null, false, page);
        Inventory inventory = Bukkit.createInventory(holder, size, config.color(title));
        holder.inventory = inventory;
        GuiLayout.decorateMenu(inventory, Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
        return inventory;
    }

    private void navigation(Inventory inventory, int page, int pages, String section) {
        inventory.setItem(45, item(page > 0 ? Material.ARROW : Material.GRAY_DYE, page > 0 ? "&e&lHALAMAN SEBELUMNYA" : "&8Halaman pertama", List.of("&7Halaman " + (page + 1) + " dari " + pages), page > 0 ? section + "_prev" : null, null));
        inventory.setItem(49, item(Material.BARRIER, "&c&lKEMBALI KE PET", List.of("&7Kembali ke menu utama pet."), "pets_main", null));
        inventory.setItem(53, item(page + 1 < pages ? Material.ARROW : Material.GRAY_DYE, page + 1 < pages ? "&e&lHALAMAN BERIKUTNYA" : "&8Halaman terakhir", List.of("&7Halaman " + (page + 1) + " dari " + pages), page + 1 < pages ? section + "_next" : null, null));
    }

    private int pages(int amount) { return Math.max(1, (amount + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length); }
    private int clampPage(int page, int pages) { return Math.max(0, Math.min(page, pages - 1)); }
    private String prettyMaterial(Material material) { return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '); }

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
        private final int page;
        private Inventory inventory;
        private PetMenuHolder(String type, String petId) { this(type, petId, null, false, 0); }
        private PetMenuHolder(String type, String petId, UUID storageOwner, boolean readOnly) {
            this(type, petId, storageOwner, readOnly, 0);
        }
        private PetMenuHolder(String type, String petId, UUID storageOwner, boolean readOnly, int page) {
            this.type = type;
            this.petId = petId;
            this.storageOwner = storageOwner;
            this.readOnly = readOnly;
            this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
