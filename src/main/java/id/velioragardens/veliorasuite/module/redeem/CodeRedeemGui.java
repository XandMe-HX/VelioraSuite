package id.velioragardens.veliorasuite.module.redeem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

/** Small, intentionally static GUI: choosing rewards never polls every tick. */
public final class CodeRedeemGui implements Listener {
    private static final String MAIN = "§8CodeRedeem §7| Pengelola";
    private static final String TEMPLATES = "§8CodeRedeem §7| Template";
    private static final String PREVIEW = "§8CodeRedeem §7| Pratinjau";
    private final CodeRedeemManager manager;
    private final CodeRedeemModule module;
    public CodeRedeemGui(CodeRedeemManager manager, CodeRedeemModule module) { this.manager=manager; this.module=module; }

    public void openMain(Player player) {
        Inventory inv = menu("main", MAIN);
        fill(inv); inv.setItem(4, status(player));
        inv.setItem(10, icon(Material.GOLD_NUGGET,"§e$1.000","§7Pilih hadiah uang $1.000","§8Lalu: §f/cd set <kode>"));
        inv.setItem(11, icon(Material.GOLD_INGOT,"§6$10.000","§7Pilih hadiah uang $10.000","§8Lalu: §f/cd set <kode>"));
        inv.setItem(12, icon(Material.GOLD_BLOCK,"§6$50.000","§7Pilih hadiah uang $50.000","§8Lalu: §f/cd set <kode>"));
        inv.setItem(13, icon(Material.EMERALD_BLOCK,"§a$100.000","§7Pilih hadiah uang $100.000","§8Lalu: §f/cd set <kode>"));
        inv.setItem(15, icon(Material.CHEST,"§bPilih Template Hadiah","§7Ada 10 paket yang siap dipakai.","§eKlik untuk membuka template."));
        inv.setItem(16, icon(Material.ITEM_FRAME,"§dAmbil Item di Tangan","§7Simpan salinan item yang kamu pegang","§7sebagai hadiah kode.","§eKlik saat memegang item."));
        inv.setItem(21, icon(Material.BOOK,"§eCara Membuat Kode","§7Pilih hadiah di atas, kemudian ketik:","§f/cd set <kode>","§7Contoh: §f/cd set EVENTAGUSTUS"));
        inv.setItem(23, icon(Material.SPYGLASS,"§bPratinjau Pilihan","§7Lihat hadiah yang sedang kamu siapkan."));
        inv.setItem(25, icon(Material.BARRIER,"§cBatalkan Pilihan","§7Menghapus hadiah yang sedang dipilih."));
        player.openInventory(inv);
    }
    private void openTemplates(Player player) {
        Inventory inv=menu("templates", TEMPLATES); fill(inv);
        int slot=0; for(CodeRedeemManager.Template t:manager.templates().values()) { if(slot>=18)break; Material m=t.items.isEmpty()?Material.PAPER:t.items.getFirst().getType(); List<String> lore=new ArrayList<>(); lore.add("§7"+t.items.size()+" item hadiah"); if(t.money>0) lore.add("§6Bonus uang: $"+(long)t.money); lore.add("§eKlik untuk memilih"); inv.setItem(slot++,icon(m,"§a"+t.name,lore.toArray(String[]::new))); }
        inv.setItem(22,icon(Material.ARROW,"§eKembali","§7Kembali ke pengelola kode.")); player.openInventory(inv);
    }
    private void openPreview(Player player) {
        CodeRedeemManager.Draft draft=manager.draft(player); if(draft==null){player.sendMessage("§cBelum ada hadiah yang dipilih.");return;}
        Inventory inv=menu("preview", PREVIEW); fill(inv);
        if(draft.type()==CodeRedeemManager.Type.MONEY) inv.setItem(13,icon(Material.EMERALD,"§aHadiah Uang","§6$"+(long)draft.money(),"§7Setelah siap: §f/cd set <kode>"));
        else if(draft.type()==CodeRedeemManager.Type.ITEM) inv.setItem(13,draft.item().clone());
        else { CodeRedeemManager.Template t=manager.templates().get(draft.template()); int i=0; for(ItemStack item:t.items) inv.setItem(i++,item.clone()); if(t.money>0)inv.setItem(22,icon(Material.EMERALD,"§aBonus Uang","§6$"+(long)t.money)); }
        inv.setItem(26,icon(Material.ARROW,"§eKembali","§7Kembali ke pengelola kode.")); player.openInventory(inv);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if(!(event.getWhoClicked() instanceof Player player))return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        String type = holder.type;
        event.setCancelled(true); if(event.getClickedInventory()==null || event.getRawSlot()>=event.getView().getTopInventory().getSize()) return; int s=event.getRawSlot();
        if(type.equals("main")) {
            switch(s) {
                case 10 -> selectMoney(player,1000); case 11 -> selectMoney(player,10000); case 12 -> selectMoney(player,50000); case 13 -> selectMoney(player,100000);
                case 15 -> openTemplates(player); case 16 -> { ItemStack hand=player.getInventory().getItemInMainHand(); if(hand.getType().isAir()){player.sendMessage("§cPegang item dahulu.");return;} manager.setDraft(player,new CodeRedeemManager.Draft(CodeRedeemManager.Type.ITEM,0,null,hand.clone())); player.sendMessage("§aItem hadiah dipilih. Ketik §f/cd set <kode>§a."); openMain(player); }
                case 23 -> openPreview(player); case 25 -> {manager.clearDraft(player);player.sendMessage("§ePilihan hadiah dibatalkan.");openMain(player);} default -> {}
            }
        } else if(type.equals("templates")) {
            if(s==22){openMain(player);return;} if(s>=0&&s<18){int i=0; for(CodeRedeemManager.Template t:manager.templates().values()){if(i++==s){manager.setDraft(player,new CodeRedeemManager.Draft(CodeRedeemManager.Type.TEMPLATE,0,t.id,null));player.sendMessage("§aTemplate §e"+t.name+" §adipilih. Ketik §f/cd set <kode>§a.");openMain(player);return;}}}
        } else if(type.equals("preview") && s==26) openMain(player);
    }
    /** Drafts are only GUI convenience state and must not accumulate for offline players. */
    @EventHandler public void onQuit(PlayerQuitEvent event) { manager.clearDraft(event.getPlayer()); }
    private void selectMoney(Player player,double amount){manager.setDraft(player,new CodeRedeemManager.Draft(CodeRedeemManager.Type.MONEY,amount,null,null));player.sendMessage("§aHadiah uang §6$"+(long)amount+" §adipilih. Ketik §f/cd set <kode>§a.");openMain(player);}
    private ItemStack status(Player player){CodeRedeemManager.Draft d=manager.draft(player); if(d==null)return icon(Material.GRAY_DYE,"§7Belum Ada Hadiah Dipilih","§7Pilih uang, template, atau item di tangan."); String text=d.type()==CodeRedeemManager.Type.MONEY?"Uang $"+(long)d.money():d.type()==CodeRedeemManager.Type.ITEM?"Item: "+d.item().getType().name():"Template: "+manager.templates().get(d.template()).name;return icon(Material.LIME_DYE,"§aPilihan Siap","§f"+text,"§7Ketik: §f/cd set <kode>");}
    private void fill(Inventory inv){for(int i=0;i<inv.getSize();i++)inv.setItem(i,icon(Material.BLACK_STAINED_GLASS_PANE," "));}
    private Inventory menu(String type, String title) { return org.bukkit.Bukkit.createInventory(new MenuHolder(type), 27, title); }
    private ItemStack icon(Material material,String name,String...lore){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));item.setItemMeta(meta);return item;}
    private static final class MenuHolder implements InventoryHolder {
        private final String type;
        private MenuHolder(String type) { this.type = type; }
        @Override public Inventory getInventory() { return null; }
    }
}
