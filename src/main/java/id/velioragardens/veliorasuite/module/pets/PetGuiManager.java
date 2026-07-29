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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

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
        inventory.setItem(14, item(Material.BARREL, "&6Pet Storage", List.of("&7Buka storage pet aktif."), "storage", null));
        inventory.setItem(15, item(Material.LEAD, "&cDismiss Pet", List.of("&7Simpan pet aktif."), "dismiss", null));
        inventory.setItem(16, item(Material.PAPER, "&fRename / Feed", List.of("&7/pet rename <pet|active> <nama>", "&7/pet feed <pet|active>"), null, null));
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
            lore.add("&7Storage: &f" + pet.storageSize());
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

    public void openStorage(Player player, PetDefinition definition, OwnedPet owned) {
        Inventory inventory = menu(definition.storageSize(), "&8Pet Storage", "storage", definition.id());
        List<ItemStack> items = manager.data().loadStorage(player.getUniqueId(), definition.id());
        for (int i = 0; i < Math.min(inventory.getSize(), items.size()); i++) inventory.setItem(i, items.get(i));
        player.openInventory(inventory);
        player.sendMessage(config.color(config.message("pet-storage-open", "%prefix% &aMembuka storage pet.")));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PetMenuHolder holder)) return;
        if ("storage".equals(holder.type)) return;
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
            case "confirm" -> openConfirm(player, petId);
            case "buy" -> { manager.buy(player, petId); openShop(player); }
            case "gacha_start" -> manager.startGacha(player);
            case "summon" -> { manager.summon(player, petId); player.closeInventory(); }
            default -> { }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PetMenuHolder holder)) return;
        if (!"storage".equals(holder.type) || holder.petId == null) return;
        manager.saveStorage(player, holder.petId, event.getInventory());
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
        private Inventory inventory;
        private PetMenuHolder(String type, String petId) { this.type = type; this.petId = petId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
