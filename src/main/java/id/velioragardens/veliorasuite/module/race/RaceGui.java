package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/** Empty-slot menus intentionally avoid decorative panes, which the server resource pack remaps. */
public final class RaceGui implements Listener {
    private final VelioraSuite plugin;
    private final RaceManager manager;
    private final NamespacedKey actionKey;

    public RaceGui(VelioraSuite plugin, RaceManager manager) {
        this.plugin = plugin; this.manager = manager; this.actionKey = new NamespacedKey(plugin, "race_gui_action");
    }
    public void openGuide(Player player) {
        Inventory inventory = menu("guide", 27, "§8Pilih Ras §7| Panduan");
        inventory.setItem(4, icon(Material.WRITTEN_BOOK, "&d&lPanduan Ras", List.of("&7Pilih ras untuk menentukan gaya bermainmu.", "&7Ras memberi benefit dan kelemahan yang seimbang.", "", "&ePilihan pertama gratis.", "&cRas belum dipilih sampai konfirmasi akhir."), "guide_continue"));
        inventory.setItem(13, icon(Material.BOOK, "&bBaca dahulu", List.of("&7Klik buku panduan di atas untuk lanjut.", "&7Pemilihan bentuk dan konfirmasi akhir", "&7dibuka pada tahap berikutnya."), null));
        inventory.setItem(22, icon(Material.BARRIER, "&cBelum ingin memilih", List.of("&7Kamu dapat membuka lagi dengan &f/race&7."), "close"));
        player.sendMessage("§d[Ras] §fBaca panduan dahulu, lalu pilih ras yang paling cocok dengan gaya mainmu.");
        player.openInventory(inventory);
    }
    private void openRaces(Player player) {
        Inventory inventory = menu("races", 27, "§8Pilih Ras §7| 6 Ras Utama");
        inventory.setItem(4, icon(Material.BOOK, "&bPilih satu ras", List.of("&7Klik ras untuk melihat benefit dan kelemahannya.", "&7Klik tidak langsung mengunci pilihan."), null));
        inventory.setItem(10, raceIcon("HUMAN", Material.PLAYER_HEAD));
        inventory.setItem(11, raceIcon("ELF", Material.BOW));
        inventory.setItem(12, raceIcon("DWARF", Material.IRON_PICKAXE));
        inventory.setItem(14, raceIcon("BEASTMAN", Material.RABBIT_FOOT));
        inventory.setItem(15, raceIcon("DEMON", Material.BLAZE_ROD));
        inventory.setItem(16, raceIcon("ANGEL", Material.FEATHER));
        inventory.setItem(22, icon(Material.ARROW, "&eKembali ke panduan", List.of("&7Baca ulang aturan pemilihan ras."), "guide"));
        player.openInventory(inventory);
    }
    private void openDetail(Player player, String race) {
        RaceInfo info = RaceInfo.valueOf(race);
        Inventory inventory = menu("detail:" + race, 27, "§8Detail Ras §7| " + info.title);
        inventory.setItem(4, icon(info.material, info.color + "&l" + info.title, info.lore(), null));
        inventory.setItem(11, icon(Material.ARROW, "&eKembali", List.of("&7Kembali ke daftar ras."), "races"));
        inventory.setItem(15, icon(Material.LIME_DYE, "&aPilih " + info.title, List.of("&7Pilihan belum permanen.", "&7Berikutnya kamu memilih bentuk player."), "draft:" + race));
        player.openInventory(inventory);
    }
    private void draft(Player player, String race) {
        manager.setDraft(player.getUniqueId(), race);
        RaceInfo info = RaceInfo.valueOf(race);
        player.closeInventory();
        player.sendMessage("§d[Ras] §fPilihan sementara: " + info.color + info.title + "§f.");
        player.sendMessage("§7Ras belum tersimpan permanen. Progress 3 akan membuka pilihan bentuk dan konfirmasi akhir.");
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem(); if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING); if (action == null) return;
        if (action.equals("guide_continue")) { openRaces(player); return; }
        if (action.equals("guide")) { openGuide(player); return; }
        if (action.equals("races")) { openRaces(player); return; }
        if (action.startsWith("detail:")) { openDetail(player, action.substring("detail:".length())); return; }
        if (action.startsWith("draft:")) { draft(player, action.substring("draft:".length())); return; }
        if (action.equals("close")) player.closeInventory();
    }
    private ItemStack raceIcon(String race, Material material) { RaceInfo info = RaceInfo.valueOf(race); return icon(material, info.color + "&l" + info.title, List.of("&7" + info.tagline, "", "&eKlik untuk melihat detail."), "detail:" + race); }
    private Inventory menu(String type, int size, String title) { return org.bukkit.Bukkit.createInventory(new Holder(type), size, title); }
    private ItemStack icon(Material material, String name, List<String> lore, String action) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(color(name)); meta.setLore(lore.stream().map(this::color).toList()); if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action); item.setItemMeta(meta); return item; }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    private record Holder(String type) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private enum RaceInfo {
        HUMAN("Human", "&f", Material.PLAYER_HEAD, "Seimbang dan cepat berkembang", List.of("&aBenefit", "&7• XP aktivitas &f+16%", "&7• Reward quest &f+10%", "", "&cKelemahan", "&7• Tidak memiliki spesialisasi elemen.")),
        ELF("Elf", "&a", Material.BOW, "Pemanah dan penjelajah hutan", List.of("&aBenefit", "&7• Speed &f+10%", "&7• Damage bow &f+16%", "&7• XP eksplorasi &f+10%", "", "&cKelemahan", "&7• Defense &f-8%")),
        DWARF("Dwarf", "&6", Material.IRON_PICKAXE, "Penambang dan penjaga tangguh", List.of("&aBenefit", "&7• Mining XP &f+24%", "&7• Tahan knockback", "&7• Durability alat hemat &f16%", "", "&cKelemahan", "&7• Speed &f-8%")),
        BEASTMAN("Beastman", "&e", Material.RABBIT_FOOT, "Petarung cepat dan lincah", List.of("&aBenefit", "&7• Sprint speed &f+14%", "&7• Damage melee &f+12%", "&7• Fall damage &f-30%", "", "&cKelemahan", "&7• Damage bow &f-8%")),
        DEMON("Demon", "&c", Material.BLAZE_ROD, "Pejuang api dan malam", List.of("&aBenefit", "&7• Fire resistance", "&7• Damage malam &f+16%", "", "&cKelemahan", "&7• Damage diterima siang &f+10%")),
        ANGEL("Angel", "&b", Material.FEATHER, "Pelindung cahaya dan penjelajah", List.of("&aBenefit", "&7• Tidak menerima fall damage", "&7• Regen ringan siang hari", "&7• Quest XP &f+10%", "", "&cKelemahan", "&7• Damage melee malam &f-8%"));
        private final String title, color, tagline; private final Material material; private final List<String> lore;
        RaceInfo(String title, String color, Material material, String tagline, List<String> lore) { this.title=title; this.color=color; this.material=material; this.tagline=tagline; this.lore=lore; }
        List<String> lore() { return lore; }
    }
}
