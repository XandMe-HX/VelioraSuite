package id.velioragardens.veliorasuite.module.gacha;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import java.io.File;
import java.util.*;

/** Admin copies real item templates; display decorations never enter delivered items. */
final class TemplateKeyShop implements Listener {
    private final VelioraSuite plugin;
    private final File file;
    private YamlConfiguration data;
    TemplateKeyShop(VelioraSuite plugin) {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "data/keyshop.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }
    void reload() { data = YamlConfiguration.loadConfiguration(file); }
    void enable() { HandlerList.unregisterAll(this); Bukkit.getPluginManager().registerEvents(this, plugin); }
    void disable() { HandlerList.unregisterAll(this); for(Player p:Bukkit.getOnlinePlayers()) if(p.getOpenInventory().getTopInventory().getHolder() instanceof Holder)p.closeInventory(); }
    void open(Player player, boolean admin) {
        if (admin && !player.hasPermission("veliorasuite.gacha.admin")) return;
        Inventory inv = Bukkit.createInventory(new Holder(admin), 54, admin ? "KeyShop | Editor Admin" : "KeyShop | Beli Key");
        for (int slot=0; slot<45; slot++) {
            ItemStack sample = sample(slot); if(sample==null) continue;
            ItemStack display = sample.clone(); var meta=display.getItemMeta();
            List<String> lore=new ArrayList<>(meta.hasLore()?meta.getLore():List.of());
            lore.add("§eHarga: " + price(slot));
            lore.add(admin?"§7Kiri +100 | Kanan -100 | Shift kanan hapus":"§aKlik untuk membeli 1 key");
            meta.setLore(lore); display.setItemMeta(meta); inv.setItem(slot,display);
        }
        ItemStack info=new ItemStack(Material.PAPER); var meta=info.getItemMeta();
        meta.setDisplayName(admin?"§eKlik key di inventory bawah untuk menambah":"§ePembelian key fisik");
        meta.setLore(List.of(admin?"§7Contoh disalin, item asli tidak diambil.":"§7Kosongkan minimal satu slot inventory."));
        info.setItemMeta(meta); inv.setItem(49,info); player.openInventory(inv);
    }
    private long price(int slot) { return Math.max(0,data.getLong("items."+slot+".price",500)); }
    private ItemStack sample(int slot) {
        String encoded=data.getString("items."+slot+".item"); if(encoded==null)return null;
        try{return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));}
        catch(RuntimeException ex){return null;}
    }
    private boolean save(Player player, String before) {
        try { file.getParentFile().mkdirs(); data.save(file); return true; }
        catch(Exception ex){ try{data.loadFromString(before);}catch(Exception ignored){} player.sendMessage("§cGagal menyimpan toko. Perubahan dibatalkan."); return false; }
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder h) || !(event.getWhoClicked() instanceof Player p))return;
        event.setCancelled(true);
        int slot=event.getRawSlot();
        if(h.admin()) {
            if(!p.hasPermission("veliorasuite.gacha.admin"))return;
            String before=data.saveToString();
            if(event.getClickedInventory()==p.getInventory() && event.isLeftClick() && !event.isShiftClick()) {
                ItemStack sample=event.getCurrentItem(); if(sample==null||sample.getType().isAir())return;
                int free=-1; for(int i=0;i<45;i++)if(sample(i)==null){free=i;break;}
                if(free<0){p.sendMessage("§cToko penuh (45 key).");return;}
                sample=sample.clone(); sample.setAmount(1);
                data.set("items."+free+".item",Base64.getEncoder().encodeToString(sample.serializeAsBytes()));
                data.set("items."+free+".price",500);
            } else if(slot>=0&&slot<45&&sample(slot)!=null) {
                if(event.isShiftClick()&&event.isRightClick())data.set("items."+slot,null);
                else if(event.isRightClick())data.set("items."+slot+".price",Math.max(0,price(slot)-100));
                else if(event.isLeftClick())data.set("items."+slot+".price",Math.min(1000000000L,price(slot)+100));
                else return;
            } else return;
            save(p,before); Bukkit.getScheduler().runTask(plugin,()->open(p,true)); return;
        }
        if(slot<0||slot>=45||!p.hasPermission("veliorasuite.gacha.use"))return;
        ItemStack sample=sample(slot); if(sample==null)return;
        if(p.getInventory().firstEmpty()<0){p.sendMessage("§cKosongkan satu slot inventory dahulu.");return;}
        if(!charge(p,price(slot))){p.sendMessage("§cSaldo tidak cukup atau Vault Economy tidak tersedia.");return;}
        // Synchronous transaction: no task yields between debit and inventory delivery.
        p.getInventory().addItem(sample.clone());
        p.sendMessage("§aBerhasil membeli 1 key seharga §e"+price(slot));
        p.closeInventory();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private boolean charge(Player p,long price){
        try{
            Class type=Class.forName("net.milkbowl.vault.economy.Economy");
            var registration=Bukkit.getServicesManager().getRegistration(type); if(registration==null)return false;
            Object response=type.getMethod("withdrawPlayer",OfflinePlayer.class,double.class).invoke(registration.getProvider(),p,(double)price);
            return (boolean)response.getClass().getMethod("transactionSuccess").invoke(response);
        }catch(ReflectiveOperationException ex){return false;}
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    private record Holder(boolean admin) implements InventoryHolder { public Inventory getInventory(){return null;} }
}
