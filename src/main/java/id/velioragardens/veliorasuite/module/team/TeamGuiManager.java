package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.module.team.model.Team;
import id.velioragardens.veliorasuite.module.team.model.TeamMember;
import id.velioragardens.veliorasuite.util.SafePlayerHead;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/** GUI ringan: tidak menyimpan session global dan tidak menjalankan polling. */
public final class TeamGuiManager implements Listener {
    private static final int[] MEMBER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private final TeamManager manager;

    public TeamGuiManager(TeamManager manager) { this.manager = manager; }

    public void openMain(Player player) {
        Team team = manager.getPlayerTeam(player.getUniqueId());
        if (team == null) { player.sendMessage("§8[§bVELIORA TEAM§8] §cKamu belum memiliki team. Gunakan /team create atau /team join."); return; }
        Holder holder = new Holder("main", player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 27, "§8Team §b" + team.getDisplayName()); holder.inventory = inventory;
        fill(inventory);
        inventory.setItem(10, item(Material.ENDER_PEARL, "§b§lHOME TEAM", List.of("§7Teleport menuju home team.", team.hasHome() ? "§aKlik untuk teleport." : "§cHome belum diatur.")));
        inventory.setItem(11, item(Material.WRITABLE_BOOK, "§e§lCHAT TEAM", List.of("§7Aktifkan/nonaktifkan chat khusus team.", "§eKlik untuk mengubah.")));
        inventory.setItem(12, item(Material.PLAYER_HEAD, "§a§lANGGOTA", List.of("§7Lihat semua anggota dan jabatan.", "§eKlik untuk membuka.")));
        inventory.setItem(13, item(Material.BOOK, "§d§lINFORMASI", List.of("§7Owner: §f" + team.getOwnerName(), "§7Saldo: §a$" + team.getBalance(), "§7Skor: §e" + team.getScore(), "§7Status: " + (team.isOpen() ? "§aTERBUKA" : "§eUNDANGAN"))));
        inventory.setItem(15, item(Material.COMPARATOR, "§6§lPENGATURAN", List.of("§7Home, PvP, status masuk,", "§7serta pembubaran team.", "§eOwner/Admin: klik untuk membuka.")));
        inventory.setItem(16, item(Material.BARRIER, "§c§lKELUAR TEAM", List.of("§7Keluar sebagai member.", "§cOwner harus pindahkan owner dahulu.")));
        player.openInventory(inventory);
    }

    private void openMembers(Player player, Team team) {
        openMembers(player, team, 0);
    }

    private void openMembers(Player player, Team team, int requestedPage) {
        List<TeamMember> members = new ArrayList<>(team.getMembers().values());
        int pages = Math.max(1, (members.size() + MEMBER_SLOTS.length - 1) / MEMBER_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Holder holder = new Holder("members", player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8Anggota §b" + team.getDisplayName()); holder.inventory = inventory; fill(inventory);
        int start = page * MEMBER_SLOTS.length;
        int end = Math.min(start + MEMBER_SLOTS.length, members.size());
        for (int index = start; index < end; index++) {
            TeamMember member = members.get(index);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            SafePlayerHead.applyOnlineProfile(meta, member.getUuid());
            meta.setDisplayName("§f" + member.getName());
            meta.setLore(List.of("§8━━━━━━━━━━━━━━━━", "§7Jabatan: " + roleColor(member.getRole().name()), "§7Bergabung: §f" + member.getJoinedAt(), "§8━━━━━━━━━━━━━━━━"));
            head.setItemMeta(meta); inventory.setItem(MEMBER_SLOTS[index - start], head);
        }
        inventory.setItem(45, item(page > 0 ? Material.ARROW : Material.GRAY_DYE, page > 0 ? "§e§lHALAMAN SEBELUMNYA" : "§8Halaman pertama", List.of("§7Halaman " + (page + 1) + " dari " + pages)));
        inventory.setItem(49, item(Material.ARROW, "§eKEMBALI", List.of("§7Kembali ke menu team.")));
        inventory.setItem(53, item(page + 1 < pages ? Material.ARROW : Material.GRAY_DYE, page + 1 < pages ? "§e§lHALAMAN BERIKUTNYA" : "§8Halaman terakhir", List.of("§7Halaman " + (page + 1) + " dari " + pages)));
        player.openInventory(inventory);
    }

    private void openSettings(Player player, Team team) {
        Holder holder = new Holder("settings", player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 27, "§8Pengaturan §b" + team.getDisplayName()); holder.inventory = inventory; fill(inventory);
        inventory.setItem(10, item(Material.LODESTONE, "§bATUR HOME", List.of("§7Menjadikan lokasi saat ini", "§7sebagai home team.")));
        inventory.setItem(11, item(Material.IRON_SWORD, team.isPvpEnabled() ? "§aPvP: AKTIF" : "§cPvP: NONAKTIF", List.of("§7Klik untuk mengubah PvP anggota.")));
        inventory.setItem(12, item(Material.OAK_DOOR, team.isOpen() ? "§aTEAM TERBUKA" : "§eKHUSUS UNDANGAN", List.of("§7Klik untuk mengubah status masuk.")));
        inventory.setItem(15, item(Material.TNT, "§cBUBARKAN TEAM", List.of("§cTidak dapat dibatalkan.", "§eKlik untuk layar konfirmasi.")));
        inventory.setItem(18, item(Material.ARROW, "§eKEMBALI", List.of("§7Kembali ke menu team.")));
        player.openInventory(inventory);
    }

    private void openConfirm(Player player, Team team) {
        Holder holder = new Holder("confirm", player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 27, "§4Konfirmasi Bubarkan Team"); holder.inventory = inventory; fill(inventory);
        inventory.setItem(11, item(Material.LIME_WOOL, "§aYA, BUBARKAN", List.of("§cTeam " + team.getDisplayName() + " akan dihapus.")));
        inventory.setItem(15, item(Material.RED_WOOL, "§cBATAL", List.of("§7Kembali tanpa menghapus team.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Team team = manager.getPlayerTeam(holder.owner);
        if (team == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        switch (holder.page) {
            case "main" -> { if (slot == 10) manager.teleportTeamHome(player); else if (slot == 11) manager.toggleTeamChat(player); else if (slot == 12) openMembers(player, team); else if (slot == 15) openSettings(player, team); else if (slot == 16) { player.closeInventory(); manager.leave(player); } }
            case "members" -> { if (slot == 45 && holder.currentPage > 0) openMembers(player, team, holder.currentPage - 1); else if (slot == 49) openMain(player); else if (slot == 53) openMembers(player, team, holder.currentPage + 1); }
            case "settings" -> { if (slot == 10) { manager.setTeamHome(player); openSettings(player, team); } else if (slot == 11) { manager.toggleTeamPvp(player); openSettings(player, team); } else if (slot == 12) { manager.toggleTeamOpen(player); openSettings(player, team); } else if (slot == 15) openConfirm(player, team); else if (slot == 18) openMain(player); }
            case "confirm" -> { if (slot == 11) { player.closeInventory(); manager.disbandOwnedTeam(player); } else if (slot == 15) openSettings(player, team); }
            default -> { }
        }
    }

    private void fill(Inventory inventory) { ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane); }
    private ItemStack item(Material material, String name, List<String> lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item; }
    private String roleColor(String role) { return switch (role) { case "OWNER" -> "§6OWNER"; case "ADMIN" -> "§cADMIN"; default -> "§aMEMBER"; }; }
    private static final class Holder implements InventoryHolder { private final String page; private final UUID owner; private final int currentPage; private Inventory inventory; private Holder(String page, UUID owner) { this(page, owner, 0); } private Holder(String page, UUID owner, int currentPage) { this.page = page; this.owner = owner; this.currentPage = currentPage; } @Override public Inventory getInventory() { return inventory; } }
}
