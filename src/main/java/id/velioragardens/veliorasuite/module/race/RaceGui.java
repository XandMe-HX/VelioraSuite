package id.velioragardens.veliorasuite.module.race;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.gui.GuiLayout;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.List;
import java.util.Locale;

/** Race menus share the Suite chest layout while selection remains confirmation-first. */
public final class RaceGui implements Listener {
    private final VelioraSuite plugin;
    private final RaceManager manager;
    private final NamespacedKey actionKey;
    private final RaceScaleHelper scaleHelper;
    private final RaceBenefits benefits;
    private final RaceEconomyHook economy;

    public RaceGui(VelioraSuite plugin, RaceManager manager, RaceBenefits benefits) {
        this.plugin = plugin; this.manager = manager; this.benefits = benefits; this.economy = new RaceEconomyHook(plugin); this.actionKey = new NamespacedKey(plugin, "race_gui_action"); this.scaleHelper = new RaceScaleHelper(plugin);
    }
    public void openGuide(Player player) {
        Inventory inventory = menu("guide", 27, "§8Pilih Ras §7| Panduan");
        inventory.setItem(4, icon(Material.WRITTEN_BOOK, "&d&lPanduan Ras", List.of("&7Pilih ras untuk menentukan gaya bermainmu.", "&7Ras memberi benefit dan kelemahan yang seimbang.", "", "&ePilihan pertama gratis.", "&cRas belum dipilih sampai konfirmasi akhir."), "guide_continue"));
        inventory.setItem(13, icon(Material.BOOK, "&bAlur pemilihan", List.of("&71. Baca info ras", "&72. Pilih bentuk tubuh", "&73. Konfirmasi pilihan akhir", "", "&eKlik buku ungu untuk mulai."), null));
        inventory.setItem(22, icon(Material.BARRIER, "&cBelum ingin memilih", List.of("&7Kamu dapat membuka lagi dengan &f/race&7."), "close"));
        player.sendMessage("§d[Ras] §fBaca panduan dahulu, lalu pilih ras yang paling cocok dengan gaya mainmu.");
        player.openInventory(inventory);
    }
    private void openRaces(Player player) {
        openRaces(player, false);
    }
    public void openChange(Player player) {
        if (!manager.selected(player.getUniqueId())) { openGuide(player); return; }
        long remaining = manager.changeRemaining(player.getUniqueId());
        if (remaining > 0L && !player.hasPermission("veliorasuite.race.bypasschange")) { player.sendMessage("§cRas baru dapat diubah lagi dalam §e" + duration(remaining) + "§c."); return; }
        openRaces(player, true);
    }
    private void openRaces(Player player, boolean changing) {
        Inventory inventory = menu("races", 27, "§8Pilih Ras §7| 6 Ras Utama");
        List<String> info = changing ? List.of("&7Pilih ras baru untuk melihat detail.", "&7Biaya: &6$" + String.format("%,.0f", manager.changeCost()), "&7Cooldown setelah berhasil: &f7 hari", "", "&eKlik tidak langsung memotong uang.") : List.of("&7Klik ras untuk melihat benefit dan kelemahannya.", "&7Klik tidak langsung mengunci pilihan.");
        inventory.setItem(4, icon(Material.BOOK, changing ? "&6Ganti Ras" : "&bPilih satu ras", info, null));
        inventory.setItem(10, raceIcon("HUMAN", Material.PLAYER_HEAD, changing));
        inventory.setItem(11, raceIcon("ELF", Material.BOW, changing));
        inventory.setItem(12, raceIcon("DWARF", Material.IRON_PICKAXE, changing));
        inventory.setItem(14, raceIcon("BEASTMAN", Material.RABBIT_FOOT, changing));
        inventory.setItem(15, raceIcon("DEMON", Material.BLAZE_ROD, changing));
        inventory.setItem(16, raceIcon("ANGEL", Material.FEATHER, changing));
        inventory.setItem(22, icon(Material.ARROW, "&eKembali ke panduan", List.of("&7Baca ulang aturan pemilihan ras."), "guide"));
        player.openInventory(inventory);
    }
    private void openDetail(Player player, String race) {
        openDetail(player, race, false);
    }
    private void openDetail(Player player, String race, boolean changing) {
        RaceInfo info = RaceInfo.valueOf(race);
        Inventory inventory = menu("detail:" + race, 27, "§8Detail Ras §7| " + info.title);
        inventory.setItem(4, icon(info.material, info.color + "&l" + info.title, info.lore(), null));
        inventory.setItem(11, icon(Material.ARROW, "&eKembali", List.of("&7Kembali ke daftar ras."), changing ? "change_races" : "races"));
        inventory.setItem(15, icon(Material.LIME_DYE, changing ? "&aGanti ke " + info.title : "&aPilih " + info.title, changing ? List.of("&7Uang belum dipotong.", "&7Pilih bentuk lalu konfirmasi perubahan.") : List.of("&7Pilihan belum permanen.", "&7Berikutnya kamu memilih bentuk player."), (changing ? "change_draft:" : "draft:") + race));
        player.openInventory(inventory);
    }
    private void draft(Player player, String race) {
        manager.setDraft(player.getUniqueId(), race);
        RaceInfo info = RaceInfo.valueOf(race);
        player.sendMessage("§d[Ras] §fPilihan sementara: " + info.color + info.title + "§f.");
        player.sendMessage("§7Pilih bentuk tubuh dahulu. Ras belum tersimpan sampai konfirmasi akhir.");
        openForms(player, race);
    }
    private void openForms(Player player, String race) {
        openForms(player, race, false);
    }
    private void openForms(Player player, String race, boolean changing) {
        RaceInfo info = RaceInfo.valueOf(race);
        Inventory inventory = menu("form:" + race, 27, "§8Bentuk Tubuh §7| " + info.title);
        inventory.setItem(4, icon(info.material, info.color + "&l" + info.title, List.of("&7Ras masih pilihan sementara.", "&7Pilih bentuk yang nyaman dilihat."), null));
        inventory.setItem(10, formIcon(Material.SMALL_AMETHYST_BUD, "&d&lMode Bocil", "&7Skala: &f55%", "&7Tubuh kecil; hanya tampilan.", race, "CHILD", changing));
        inventory.setItem(13, formIcon(Material.ARMOR_STAND, "&a&lDewasa Normal", "&7Skala: &f100%", "&7Ukuran Minecraft standar.", race, "ADULT", changing));
        inventory.setItem(16, formIcon(Material.END_ROD, "&b&lDewasa Tinggi", "&7Skala: &f115%", "&7Sedikit lebih tinggi, tetap aman.", race, "TALL", changing));
        inventory.setItem(22, icon(Material.ARROW, "&eKembali", List.of("&7Kembali ke detail ras."), (changing ? "change_detail:" : "detail:") + race));
        player.openInventory(inventory);
    }
    private ItemStack formIcon(Material material, String name, String scale, String description, String race, String form, boolean changing) {
        return icon(material, name, List.of(scale, description, "", "&eKlik untuk membuka konfirmasi."), (changing ? "change_confirm:" : "confirm:") + race + ":" + form);
    }
    private void openConfirm(Player player, String race, String form) {
        openConfirm(player, race, form, false);
    }
    private void openConfirm(Player player, String race, String form, boolean changing) {
        RaceInfo info = RaceInfo.valueOf(race);
        String formName = formName(form);
        Inventory inventory = menu("confirm:" + race + ":" + form, 27, "§8Konfirmasi Ras");
        inventory.setItem(4, icon(info.material, info.color + "&l" + info.title, List.of("&7Ras pilihanmu."), null));
        List<String> summary = changing ? List.of("&7Ras baru: " + info.color + info.title, "&7Bentuk: &f" + formName, "&7Skala: &f" + (int) (manager.scaleFor(form) * 100) + "%", "", "&6Biaya: &f$" + String.format("%,.0f", manager.changeCost()), "&cUang hanya dipotong saat tombol hijau ditekan.") : List.of("&7Ras: " + info.color + info.title, "&7Bentuk: &f" + formName, "&7Skala: &f" + (int) (manager.scaleFor(form) * 100) + "%", "", "&cSetelah dikonfirmasi, perubahan", "&cmengikuti aturan biaya/cooldown tahap berikutnya.");
        inventory.setItem(13, icon(Material.WRITABLE_BOOK, "&fRingkasan Pilihan", summary, null));
        inventory.setItem(11, icon(Material.ARROW, "&eUbah Bentuk", List.of("&7Kembali tanpa menyimpan pilihan."), (changing ? "change_form:" : "form:") + race));
        inventory.setItem(15, icon(Material.LIME_DYE, changing ? "&a&lBayar dan Ganti Ras" : "&a&lKonfirmasi Pilihan", changing ? List.of("&7Potong &f$" + String.format("%,.0f", manager.changeCost()) + " &7hanya jika transaksi sukses.", "&7Cooldown berikutnya: &f7 hari.") : List.of("&7Simpan ras dan bentuk tubuh sekarang.", "&aTidak bisa dipilih ulang bebas."), (changing ? "change_complete:" : "complete:") + race + ":" + form));
        player.openInventory(inventory);
    }
    private void complete(Player player, String race, String form) {
        if (manager.selected(player.getUniqueId())) { player.closeInventory(); player.sendMessage("§cKamu sudah memiliki ras."); return; }
        manager.complete(player.getUniqueId(), race, form);
        scaleHelper.apply(player, manager.scaleFor(form));
        benefits.applyPassive(player);
        player.closeInventory();
        RaceInfo info = RaceInfo.valueOf(race);
        player.sendMessage("§a[Ras] §fPilihan tersimpan: " + info.color + info.title + " §7• §f" + formName(form) + "§a.");
        player.sendMessage("§7Benefit gameplay ras dan ukuran tubuh sudah diterapkan.");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.65F, 1.2F);
    }
    private void change(Player player, String race, String form) {
        if (!manager.canChange(player.getUniqueId()) && !player.hasPermission("veliorasuite.race.bypasschange")) { player.closeInventory(); player.sendMessage("§cRas baru dapat diubah lagi dalam §e" + duration(manager.changeRemaining(player.getUniqueId())) + "§c."); return; }
        if (!player.hasPermission("veliorasuite.race.bypasschange")) {
            RaceEconomyHook.Result result = economy.charge(player, manager.changeCost());
            if (result != RaceEconomyHook.Result.OK) { player.sendMessage(switch (result) { case UNAVAILABLE -> "§cVault Economy belum tersedia. Ras tidak diubah dan uang tidak dipotong."; case INSUFFICIENT -> "§cSaldo tidak cukup untuk mengganti ras."; default -> "§cTransaksi gagal. Ras tidak diubah dan tidak ada biaya yang diproses."; }); return; }
        }
        manager.change(player.getUniqueId(), race, form);
        scaleHelper.apply(player, manager.scaleFor(form));
        benefits.applyPassive(player);
        player.closeInventory();
        RaceInfo info = RaceInfo.valueOf(race);
        player.sendMessage("§a[Ras] §fRas berhasil diubah ke " + info.color + info.title + " §7• §f" + formName(form) + "§a.");
        player.sendMessage("§7Perubahan berikutnya tersedia dalam 7 hari.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.1F);
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
        if (action.equals("change_races")) { openRaces(player, true); return; }
        if (action.startsWith("detail:")) { openDetail(player, action.substring("detail:".length())); return; }
        if (action.startsWith("draft:")) { draft(player, action.substring("draft:".length())); return; }
        if (action.startsWith("change_detail:")) { openDetail(player, action.substring("change_detail:".length()), true); return; }
        if (action.startsWith("change_draft:")) { String race = action.substring("change_draft:".length()); manager.setDraft(player.getUniqueId(), race); openForms(player, race, true); return; }
        if (action.startsWith("change_form:")) { openForms(player, action.substring("change_form:".length()), true); return; }
        if (action.startsWith("form:")) { openForms(player, action.substring("form:".length())); return; }
        if (action.startsWith("confirm:")) { String[] parts = action.split(":"); if (parts.length == 3) openConfirm(player, parts[1], parts[2]); return; }
        if (action.startsWith("complete:")) { String[] parts = action.split(":"); if (parts.length == 3) complete(player, parts[1], parts[2]); return; }
        if (action.startsWith("change_confirm:")) { String[] parts = action.split(":"); if (parts.length == 3) openConfirm(player, parts[1], parts[2], true); return; }
        if (action.startsWith("change_complete:")) { String[] parts = action.split(":"); if (parts.length == 3) change(player, parts[1], parts[2]); return; }
        if (action.equals("close")) player.closeInventory();
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof Holder)) return;
        if (!manager.enforcementEnabled() || manager.selected(player.getUniqueId()) || player.hasPermission("veliorasuite.race.admin")) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || manager.selected(player.getUniqueId())) return;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) return;
            openGuide(player);
        }, 10L);
    }
    public void applySavedScale(Player player) { if (manager.selected(player.getUniqueId())) scaleHelper.apply(player, manager.scaleFor(manager.form(player.getUniqueId()))); }
    public void resetScale(Player player) { scaleHelper.reset(player); }
    private String formName(String form) { return switch (form.toUpperCase(Locale.ROOT)) { case "CHILD" -> "Mode Bocil"; case "TALL" -> "Dewasa Tinggi"; default -> "Dewasa Normal"; }; }
    private ItemStack raceIcon(String race, Material material, boolean changing) { RaceInfo info = RaceInfo.valueOf(race); return icon(material, info.color + "&l" + info.title, List.of("&7" + info.tagline, "", "&eKlik untuk melihat detail."), (changing ? "change_detail:" : "detail:") + race); }
    private String duration(long millis) { long minutes = Math.max(1L, (millis + 59_999L) / 60_000L); long days = minutes / 1_440L; long hours = (minutes % 1_440L) / 60L; long mins = minutes % 60L; return days > 0 ? days + "h " + hours + "j" : hours > 0 ? hours + "j " + mins + "m" : mins + "m"; }
    private Inventory menu(String type, int size, String title) {
        Inventory inventory = org.bukkit.Bukkit.createInventory(new Holder(type), size, title);
        GuiLayout.decorateMenu(inventory, Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
        return inventory;
    }
    private ItemStack icon(Material material, String name, List<String> lore, String action) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(color(name)); meta.setLore(lore.stream().map(this::color).toList()); if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action); item.setItemMeta(meta); return item; }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    private record Holder(String type) implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private enum RaceInfo {
        HUMAN("Human", "&f", Material.PLAYER_HEAD, "Seimbang dan cepat berkembang", List.of("&aBenefit", "&7• Semua XP yang didapat &f+16%", "", "&cKelemahan", "&7• Tidak memiliki spesialisasi elemen.")),
        ELF("Elf", "&a", Material.BOW, "Pemanah dan penjelajah hutan", List.of("&aBenefit", "&7• Speed &f+10%", "&7• Damage bow &f+16%", "&7• XP eksplorasi &f+10%", "", "&cKelemahan", "&7• Defense &f-8%")),
        DWARF("Dwarf", "&6", Material.IRON_PICKAXE, "Penambang dan penjaga tangguh", List.of("&aBenefit", "&7• Mining XP &f+24%", "&7• Tahan knockback", "&7• Durability alat hemat &f16%", "", "&cKelemahan", "&7• Speed &f-8%")),
        BEASTMAN("Beastman", "&e", Material.RABBIT_FOOT, "Petarung cepat dan lincah", List.of("&aBenefit", "&7• Sprint speed &f+14%", "&7• Damage melee &f+12%", "&7• Fall damage &f-30%", "", "&cKelemahan", "&7• Damage bow &f-8%")),
        DEMON("Demon", "&c", Material.BLAZE_ROD, "Pejuang api dan malam", List.of("&aBenefit", "&7• Fire resistance", "&7• Damage malam &f+16%", "", "&cKelemahan", "&7• Damage diterima siang &f+10%")),
        ANGEL("Angel", "&b", Material.FEATHER, "Pelindung cahaya dan penjelajah", List.of("&aBenefit", "&7• Tidak menerima fall damage", "&7• Regen ringan siang hari", "&7• Semua XP yang didapat &f+10%", "", "&cKelemahan", "&7• Damage melee malam &f-8%"));
        private final String title, color, tagline; private final Material material; private final List<String> lore;
        RaceInfo(String title, String color, Material material, String tagline, List<String> lore) { this.title=title; this.color=color; this.material=material; this.tagline=tagline; this.lore=lore; }
        List<String> lore() { return lore; }
    }
}
