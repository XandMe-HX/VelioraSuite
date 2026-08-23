package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.module.trader.model.TraderTradeItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public final class TraderGuiManager implements Listener {

    private final TraderConfigManager configManager;
    private final TraderManager traderManager;
    private final TraderItemFactory itemFactory;
    private final Map<UUID, Map<Integer, String>> slotItems = new HashMap<>();
    private final Set<UUID> sellMenus = new HashSet<>();

    public TraderGuiManager(TraderConfigManager configManager, TraderManager traderManager, TraderItemFactory itemFactory) {
        this.configManager = configManager;
        this.traderManager = traderManager;
        this.itemFactory = itemFactory;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, configManager.color(configManager.getGuiTitle()));
        Map<Integer, String> map = new HashMap<>();
        List<Integer> slots = List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43);
        List<TraderTradeItem> activeItems = traderManager.getActiveItems();
        for (int i = 0; i < activeItems.size() && i < slots.size(); i++) {
            TraderTradeItem item = activeItems.get(i);
            int slot = slots.get(i);
            boolean soldOut = traderManager.getPurchaseManager().isSoldOut(player, item);
            inventory.setItem(slot, itemFactory.createTradeDisplay(item, soldOut));
            map.put(slot, item.getId());
        }
        inventory.setItem(45, button(Material.EMERALD, "&a&lJUAL HASIL FARM", List.of("&7Jual item pilihan dengan harga tetap.", "&eKlik untuk membuka halaman jual.")));
        inventory.setItem(49, closeButton());
        slotItems.put(player.getUniqueId(), map);
        sellMenus.remove(player.getUniqueId());
        player.openInventory(inventory);
    }

    private void openSell(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, configManager.color("&1Veliora &bTrader Sell"));
        Map<Integer, String> map = new HashMap<>(); int slot = 10;
        for (Map.Entry<Material, Double> entry : configManager.getSellPrices().entrySet()) {
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            ItemStack display = new ItemStack(entry.getKey()); ItemMeta meta = display.getItemMeta();
            meta.setDisplayName(configManager.color("&b" + entry.getKey().name().replace('_',' ')));
            meta.setLore(List.of(configManager.color("&7Harga jual per item: &a$" + entry.getValue()), configManager.color("&eKlik untuk menjual SEMUA item ini.")));
            display.setItemMeta(meta); inventory.setItem(slot, display); map.put(slot++, entry.getKey().name());
        }
        inventory.setItem(45, button(Material.ARROW,"&eKEMBALI",List.of("&7Kembali ke katalog trader.")));
        inventory.setItem(49, closeButton()); slotItems.put(player.getUniqueId(),map); sellMenus.add(player.getUniqueId()); player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean sell = sellMenus.contains(player.getUniqueId());
        if (!event.getView().getTitle().equals(configManager.color(configManager.getGuiTitle()))
                && !event.getView().getTitle().equals(configManager.color("&1Veliora &bTrader Sell"))) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        if (!sell && event.getRawSlot() == 45) { openSell(player); return; }
        if (sell && event.getRawSlot() == 45) { open(player); return; }
        String itemId = slotItems.getOrDefault(player.getUniqueId(), Map.of()).get(event.getRawSlot());
        if (itemId == null) return;
        if (sell) { sellAll(player, Material.matchMaterial(itemId)); openSell(player); return; }
        traderManager.buy(player, itemId);
        open(player);
    }

    private void sellAll(Player player, Material material) {
        if (material == null) return; double price = configManager.getSellPrices().getOrDefault(material,0D); int amount=0;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (stack != null && stack.getType()==material) amount += stack.getAmount();
        if (amount == 0) { player.sendMessage(configManager.color(configManager.getPrefix()+"&eKamu tidak memiliki item itu.")); return; }
        Object economy;
        try {
            Class<?> economyClass=Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes","unchecked"}) RegisteredServiceProvider<?> registration=Bukkit.getServicesManager().getRegistration((Class)economyClass);
            if(registration==null)throw new IllegalStateException(); economy=registration.getProvider();
        } catch (ReflectiveOperationException|LinkageError|IllegalStateException exception) { player.sendMessage(configManager.color(configManager.getPrefix()+"&cVault Economy tidak tersedia."));return; }
        double total=amount*price;
        try { Method deposit=economy.getClass().getMethod("depositPlayer",org.bukkit.OfflinePlayer.class,double.class);deposit.invoke(economy,player,total); }
        catch(ReflectiveOperationException exception){player.sendMessage(configManager.color(configManager.getPrefix()+"&cGagal menambahkan saldo."));return;}
        for(ItemStack stack:player.getInventory().getStorageContents())if(stack!=null&&stack.getType()==material)stack.setAmount(0);
        player.sendMessage(configManager.color(configManager.getPrefix()+"&aTerjual &f"+amount+" "+material.name()+" &aseharga &f$"+String.format(java.util.Locale.US,"%.2f",total)));
    }

    private ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(configManager.color("&cClose"));
            meta.setLore(List.of(configManager.color("&7Tutup menu trader.")));
            item.setItemMeta(meta);
        }
        return item;
    }
    private ItemStack button(Material material,String name,List<String> lore){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(configManager.color(name));meta.setLore(lore.stream().map(configManager::color).toList());item.setItemMeta(meta);return item;}
}
