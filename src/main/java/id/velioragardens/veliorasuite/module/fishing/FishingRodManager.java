package id.velioragardens.veliorasuite.module.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import id.velioragardens.veliorasuite.module.fishing.model.FishingRodDefinition;

import java.util.List;

public final class FishingRodManager implements Listener {

    private static final String SHOP_TITLE = "§8Fishing Rod Shop";

    private final FishingManager manager;
    private final NamespacedKey tierKey;
    private final NamespacedKey ownerKey;
    private final List<FishingRodDefinition> rods;

    public FishingRodManager(FishingManager manager) {
        this.manager = manager;
        tierKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_rod_tier");
        ownerKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_rod_owner");
        rods = manager.getConfigManager().getRodDefinitions();
    }

    public void open(Player player) {
        if (!manager.getConfigManager().isRodsEnabled()) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eRod Shop sedang dimatikan."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 27, SHOP_TITLE);
        for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, filler());
        for (RodDefinition rod : rods) inventory.setItem(10 + rod.tier(), createShopItem(player, rod));
        inventory.setItem(22, basic(Material.BARRIER, "§cKembali", List.of("§7Kembali ke menu Fishing.")));
        player.openInventory(inventory);
    }

    public int getTier(Player player) {
        if (player == null) return 0;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        String owner = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (tier == null || tier < 1 || definition(tier).tier() != tier) return 0;
        return player.getUniqueId().toString().equals(owner) && manager.getRodDataManager().has(player.getUniqueId(), tier) ? tier : 0;
    }

    public int clickReduction(Player player) {
        return definition(getTier(player)).clickReduction();
    }

    public int secondsBonus(Player player) {
        return definition(getTier(player)).secondsBonus();
    }

    public void showAura(Player player, FishHook hook) {
        int tier = getTier(player);
        if (tier < 3 || hook == null || !hook.isValid()) return;
        if (tier == 3) {
            hook.getWorld().spawnParticle(Particle.BUBBLE_POP, hook.getLocation(), 4, 0.16D, 0.16D, 0.16D, 0.02D);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 2, 0.25D, 0.35D, 0.25D, 0.01D);
        } else if (tier == 4) {
            hook.getWorld().spawnParticle(Particle.SPLASH, hook.getLocation(), 6, 0.28D, 0.12D, 0.28D, 0.04D);
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0.0D, 1.0D, 0.0D), 2, 0.22D, 0.35D, 0.22D, 0.005D);
        } else {
            hook.getWorld().spawnParticle(Particle.END_ROD, hook.getLocation(), 7, 0.18D, 0.18D, 0.18D, 0.01D);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 4, 0.28D, 0.42D, 0.28D, 0.01D);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (getBoundTier(event.getItemDrop().getItemStack()) <= 0) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eRod ini terikat pada pemiliknya."));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(SHOP_TITLE)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 22) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(manager.getConfigManager().getPlugin(), () -> manager.openMainGui(player));
            return;
        }
        if (slot < 11 || slot > 15) return;
        buy(player, definition(slot - 10));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(SHOP_TITLE)) event.setCancelled(true);
    }

    private void buy(Player player, FishingRodDefinition rod) {
        if (!manager.getConfigManager().isRodsEnabled()) return;
        boolean bypass = player.hasPermission(manager.getConfigManager().getRodBypassPermission())
                || player.hasPermission(manager.getConfigManager().getAdminPermission()) || player.isOp();
        boolean unlocked = manager.getRodDataManager().has(player.getUniqueId(), rod.tier());
        int owned = manager.getRodDataManager().highest(player.getUniqueId(), maxTier());
        if (unlocked) {
            if (hasBoundRod(player, rod.tier())) {
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eRod ini sudah ada di inventory kamu."));
                return;
            }
            if (player.getInventory().firstEmpty() < 0) {
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eInventory kamu penuh."));
                return;
            }
            manager.getRodDataManager().unlock(player.getUniqueId(), rod.tier());
        player.getInventory().addItem(createRod(player, rod));
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&aRod berhasil diambil kembali."));
            player.closeInventory();
            return;
        }
        if (!bypass && rod.tier() > 1 && owned < rod.tier() - 1) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eKamu harus membuka tier sebelumnya terlebih dahulu."));
            return;
        }
        int catches = manager.getDataManager().getOrCreate(player).getTotalCatches();
        if (!bypass && catches < rod.requiredCatches()) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eButuh &f" + rod.requiredCatches() + " tangkapan &euntuk rod ini."));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eInventory kamu penuh."));
            return;
        }
        if (!bypass && !manager.withdrawRodCost(player, rod.price())) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cUang kamu tidak cukup atau Vault tidak aktif."));
            return;
        }
        player.getInventory().addItem(createRod(player, rod));
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&aKamu mendapatkan rod baru!"));
        player.closeInventory();
    }

    private boolean hasBoundRod(Player player, int tier) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (getBoundTier(item, player) == tier) return true;
        }
        return false;
    }

    private ItemStack createShopItem(Player player, FishingRodDefinition rod) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(gradient(rod.name(), rod.fromColor(), rod.toColor()));
        boolean bypass = player.hasPermission(manager.getConfigManager().getRodBypassPermission())
                || player.hasPermission(manager.getConfigManager().getAdminPermission()) || player.isOp();
        int catches = manager.getDataManager().getOrCreate(player).getTotalCatches();
        List<Component> lore = List.of(
                Component.text(""),
                Component.text("Minigame", TextColor.color(0x55D6FF)),
                Component.text(" +" + rod.secondsBonus() + ".0 detik waktu", TextColor.color(0xB8C4D2)),
                Component.text(" -" + rod.clickReduction() + " klik diperlukan", TextColor.color(0xB8C4D2)),
                Component.text(""),
                Component.text("Aura", TextColor.color(0x55D6FF)),
                Component.text(" " + rod.aura(), TextColor.color(0xB8C4D2)),
                Component.text(""),
                Component.text(bypass ? " Admin bypass aktif" : " Syarat: " + rod.requiredCatches() + " tangkapan", TextColor.color(bypass ? 0x70E090 : 0xE6CE79)),
                Component.text(bypass ? " Gratis untuk admin" : " Harga: " + rod.price(), TextColor.color(bypass ? 0x70E090 : 0xE6CE79)),
                Component.text(" Kamu: " + catches + " tangkapan", TextColor.color(0x8391A5)),
                Component.text(""),
                Component.text("Klik untuk membeli", TextColor.color(0xFFFFFF))
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRod(Player owner, FishingRodDefinition rod) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(gradient(rod.name(), rod.from(), rod.to()));
        meta.lore(List.of(
                Component.text("VelioraFishing Rod • Tier " + rod.tier(), TextColor.color(0x55D6FF)),
                Component.text("+" + rod.secondsBonus() + ".0 detik • -" + rod.clickReduction() + " klik", TextColor.color(0xD6E0EB)),
                Component.text("Terikat: " + owner.getName(), TextColor.color(0x8391A5))
        ));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, rod.tier());
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private int getBoundTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer tier = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return tier == null ? 0 : tier;
    }

    private int getBoundTier(ItemStack item, Player owner) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        String rodOwner = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return tier != null && owner.getUniqueId().toString().equals(rodOwner) ? tier : 0;
    }

    private FishingRodDefinition definition(int tier) {
        return rods.stream().filter(rod -> rod.tier() == tier).findFirst().orElse(rods.getFirst());
    }

    private int maxTier() {
        return rods.stream().mapToInt(FishingRodDefinition::tier).max().orElse(1);
    }

    private ItemStack filler() {
        return basic(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private ItemStack basic(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component gradient(String text, String from, String to) {
        TextColor first = TextColor.fromHexString(from);
        TextColor last = TextColor.fromHexString(to);
        if (first == null || last == null || text.isEmpty()) return Component.text(text);
        Component result = Component.empty();
        for (int index = 0; index < text.length(); index++) {
            double ratio = text.length() == 1 ? 0.0D : index / (double) (text.length() - 1);
            int red = (int) Math.round(first.red() + (last.red() - first.red()) * ratio);
            int green = (int) Math.round(first.green() + (last.green() - first.green()) * ratio);
            int blue = (int) Math.round(first.blue() + (last.blue() - first.blue()) * ratio);
            result = result.append(Component.text(String.valueOf(text.charAt(index)), TextColor.color(red, green, blue)));
        }
        return result;
    }

}
