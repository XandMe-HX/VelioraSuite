package id.velioragardens.veliorasuite.module.guard;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Server-area protection. It deliberately does not replace RedProtect player claims. */
public final class GuardClaimModule implements VelioraModule, Listener, CommandExecutor, TabCompleter {
    private static final List<String> FLAGS = List.of("break", "place", "doors", "iron-doors", "trapdoors", "containers", "drop-items", "pvp", "hostile-spawn", "passive-spawn", "explosions", "entity-damage");
    private final VelioraSuite plugin;
    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>(), pos2 = new HashMap<>();
    private final Map<UUID, String> inside = new HashMap<>();
    private File file;
    private boolean enabled;

    public GuardClaimModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "guardclaim"; }
    @Override public void load() { file = new File(plugin.getDataFolder(), "data/guard-claims.yml"); loadData(); }
    @Override public void enable() { enabled = true; PluginCommand c = plugin.getCommand("vgclaim"); if (c != null) { c.setExecutor(this); c.setTabCompleter(this); } Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void disable() { enabled = false; HandlerList.unregisterAll(this); save(); PluginCommand c = plugin.getCommand("vgclaim"); if (c != null) { DisabledCommand d = new DisabledCommand(plugin, "VelioraGuard"); c.setExecutor(d); c.setTabCompleter(d); } }
    @Override public void reload() { save(); loadData(); }
    @Override public boolean isEnabled() { return enabled; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Command ini hanya untuk player."); return true; }
        if (!admin(p)) { msg(p, "&cKamu tidak punya izin Guard Claim."); return true; }
        if (args.length == 0) { help(p); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("pos1")) { pos1.put(p.getUniqueId(), p.getLocation()); msg(p, "&aPosisi 1 disimpan: &f" + shortLoc(p.getLocation())); return true; }
        if (sub.equals("pos2")) { pos2.put(p.getUniqueId(), p.getLocation()); msg(p, "&aPosisi 2 disimpan: &f" + shortLoc(p.getLocation())); return true; }
        if (sub.equals("claim") && args.length >= 2) {
            String id = safe(args[1]); Location a = pos1.get(p.getUniqueId()), b = pos2.get(p.getUniqueId());
            if (id.isBlank() || claims.containsKey(id)) { msg(p, "&cID tidak valid atau sudah dipakai."); return true; }
            if (a == null || b == null || !a.getWorld().equals(b.getWorld())) { msg(p, "&cAtur /vgclaim pos1 dan /vgclaim pos2 di world yang sama dahulu."); return true; }
            Claim claim = Claim.create(id, a, b); claims.put(id, claim); save(); msg(p, "&aGuard Claim &f" + id + " &adibuat dari dasar sampai langit."); return true;
        }
        if ((sub.equals("delete") || sub.equals("remove")) && args.length >= 3 && args[2].equalsIgnoreCase("confirm")) { Claim c = claims.remove(safe(args[1])); if (c == null) msg(p, "&cClaim tidak ditemukan."); else { save(); msg(p, "&aGuard Claim &f" + c.id + " &adihapus."); } return true; }
        if (sub.equals("flag") && args.length >= 4) { Claim c = claims.get(safe(args[1])); if (c == null) { msg(p, "&cClaim tidak ditemukan."); return true; } String flag = args[2].toLowerCase(Locale.ROOT); if (!FLAGS.contains(flag) && !flag.equals("welcome") && !flag.equals("welcome-subtitle")) { msg(p, "&cFlag tidak dikenal. Gunakan /vgclaim flags."); return true; } if (flag.startsWith("welcome")) { c.text.put(flag, String.join(" ", Arrays.copyOfRange(args, 3, args.length))); } else { Boolean value = bool(args[3]); if (value == null) { msg(p, "&cGunakan true atau false."); return true; } c.flags.put(flag, value); } save(); msg(p, "&aFlag &f" + flag + " &adiperbarui."); return true; }
        if (sub.equals("flags")) { msg(p, "&bFlags: &f" + String.join(", ", FLAGS)); msg(p, "&7Contoh: /vgclaim flag lobby break false"); return true; }
        if (sub.equals("list")) { msg(p, claims.isEmpty() ? "&7Belum ada Guard Claim." : "&bGuard Claim: &f" + String.join(", ", claims.keySet())); return true; }
        if (sub.equals("info") && args.length >= 2) { Claim c = claims.get(safe(args[1])); if (c == null) msg(p, "&cClaim tidak ditemukan."); else { msg(p, "&b" + c.id + " &7| world: &f" + c.worldName + " &7| X: &f" + c.minX + ".." + c.maxX + " &7| Z: &f" + c.minZ + ".." + c.maxZ); msg(p, "&7Break:" + yes(c.flag("break")) + " &7Place:" + yes(c.flag("place")) + " &7PVP:" + yes(c.flag("pvp")) + " &7Drop:" + yes(c.flag("drop-items"))); } return true; }
        if (sub.equals("reload")) { reload(); msg(p, "&aGuard Claim dimuat ulang."); return true; }
        help(p); return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void breakBlock(BlockBreakEvent e) { deny(e.getPlayer(), e.getBlock().getLocation(), "break", e::setCancelled, "menghancurkan blok"); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void placeBlock(BlockPlaceEvent e) { deny(e.getPlayer(), e.getBlockPlaced().getLocation(), "place", e::setCancelled, "menaruh blok"); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void interact(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return; Block block = e.getClickedBlock(); Material m = block.getType(); String flag = null;
        if (m.name().contains("TRAPDOOR")) flag = "trapdoors";
        else if (m.name().contains("DOOR")) flag = m.name().contains("IRON") ? "iron-doors" : "doors";
        else if (block.getState() instanceof org.bukkit.inventory.InventoryHolder) flag = "containers";
        if (flag != null) deny(e.getPlayer(), block.getLocation(), flag, e::setCancelled, "menggunakan blok ini");
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void drop(PlayerDropItemEvent e) { deny(e.getPlayer(), e.getPlayer().getLocation(), "drop-items", e::setCancelled, "menjatuhkan item"); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void pvp(EntityDamageByEntityEvent e) { if (e.getEntity() instanceof Player victim && e.getDamager() instanceof Player attacker) deny(attacker, victim.getLocation(), "pvp", e::setCancelled, "PvP"); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void entityDamage(EntityDamageByEntityEvent e) { if (e.getDamager() instanceof Player p && !(e.getEntity() instanceof Player)) deny(p, e.getEntity().getLocation(), "entity-damage", e::setCancelled, "menyerang entity"); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void spawn(CreatureSpawnEvent e) { Claim c = at(e.getLocation()); if (c == null) return; boolean hostile = e.getEntity() instanceof org.bukkit.entity.Monster; if (!c.flag(hostile ? "hostile-spawn" : "passive-spawn")) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void explode(EntityExplodeEvent e) { Claim c = at(e.getLocation()); if (c != null && !c.flag("explosions")) { e.blockList().clear(); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void explode(BlockExplodeEvent e) { Claim c = at(e.getBlock().getLocation()); if (c != null && !c.flag("explosions")) { e.blockList().clear(); } }
    @EventHandler public void move(PlayerMoveEvent e) { if (e.getTo() == null || e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockZ() == e.getTo().getBlockZ() && e.getFrom().getWorld().equals(e.getTo().getWorld())) return; Claim c = at(e.getTo()); String before = inside.get(e.getPlayer().getUniqueId()); String now = c == null ? null : c.id; if (Objects.equals(before, now)) return; if (now == null) inside.remove(e.getPlayer().getUniqueId()); else { inside.put(e.getPlayer().getUniqueId(), now); e.getPlayer().sendTitle(col(c.text.getOrDefault("welcome", "&aWELCOME")), col(c.text.getOrDefault("welcome-subtitle", "&f" + c.id)), 5, 40, 10); } }

    private void deny(Player p, Location l, String flag, java.util.function.Consumer<Boolean> cancel, String action) { Claim c = at(l); if (c == null || c.flag(flag) || bypass(p)) return; cancel.accept(true); msg(p, "&cKamu tidak dapat " + action + " di area &f" + c.id + "&c."); }
    private boolean bypass(Player p) { return p.isOp() || p.hasPermission("veliorasuite.guard.bypass") || p.hasPermission("veliorasuite.bypass"); }
    private boolean admin(Player p) { return bypass(p) || p.hasPermission("veliorasuite.guard.admin"); }
    private Claim at(Location l) { if (l == null || l.getWorld() == null) return null; for (Claim c : claims.values()) if (c.world.equals(l.getWorld().getUID()) && l.getBlockX() >= c.minX && l.getBlockX() <= c.maxX && l.getBlockZ() >= c.minZ && l.getBlockZ() <= c.maxZ) return c; return null; }
    private void loadData() { claims.clear(); YamlConfiguration y = YamlConfiguration.loadConfiguration(file); ConfigurationSection root = y.getConfigurationSection("claims"); if (root == null) return; for (String id : root.getKeys(false)) try { String p = "claims." + id + "."; Claim c = new Claim(id, UUID.fromString(y.getString(p + "world")), y.getString(p + "world-name", "world"), y.getInt(p + "min-x"), y.getInt(p + "max-x"), y.getInt(p + "min-z"), y.getInt(p + "max-z")); for (String f : FLAGS) c.flags.put(f, y.getBoolean(p + "flags." + f, false)); c.text.put("welcome", y.getString(p + "welcome.title", "&aWELCOME")); c.text.put("welcome-subtitle", y.getString(p + "welcome.subtitle", "&f" + id)); claims.put(id, c); } catch (Exception ex) { plugin.getLogger().warning("Guard Claim rusak dilewati: " + id); } }
    private void save() { YamlConfiguration y = new YamlConfiguration(); for (Claim c : claims.values()) { String p = "claims." + c.id + "."; y.set(p + "world", c.world.toString()); y.set(p + "world-name", c.worldName); y.set(p + "min-x", c.minX); y.set(p + "max-x", c.maxX); y.set(p + "min-z", c.minZ); y.set(p + "max-z", c.maxZ); for (String f : FLAGS) y.set(p + "flags." + f, c.flag(f)); y.set(p + "welcome.title", c.text.get("welcome")); y.set(p + "welcome.subtitle", c.text.get("welcome-subtitle")); } try { File parent = file.getParentFile(); if (parent != null) parent.mkdirs(); y.save(file); } catch (IOException ex) { plugin.getLogger().warning("Guard Claim gagal disimpan: " + ex.getMessage()); } }
    private void help(Player p) { for (String line : List.of("&bVeliora Guard Claim", "&f/vgclaim pos1 &7dan &f/vgclaim pos2 &7- pilih dua sudut", "&f/vgclaim claim <id> &7- simpan wilayah dari bawah sampai atas", "&f/vgclaim flag <id> <flag> <true|false>", "&f/vgclaim flag <id> welcome <judul>", "&f/vgclaim info <id> &7| &f/vgclaim list", "&f/vgclaim delete <id> confirm")) msg(p, line); }
    private String safe(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", ""); }
    private String col(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private void msg(Player p, String s) { p.sendMessage(col("&8[&aVelioraGuard&8] &r" + s)); }
    private String shortLoc(Location l) { return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ(); }
    private String yes(boolean b) { return b ? "&aON" : "&cOFF"; }
    private Boolean bool(String s) { return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on") ? true : s.equalsIgnoreCase("false") || s.equalsIgnoreCase("off") ? false : null; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) { if (args.length == 1) return List.of("pos1", "pos2", "claim", "delete", "flag", "flags", "list", "info", "reload"); if (args.length == 2 && !args[0].equalsIgnoreCase("claim")) return new ArrayList<>(claims.keySet()); if (args.length == 3 && args[0].equalsIgnoreCase("flag")) return new ArrayList<>(FLAGS); if (args.length == 4 && args[0].equalsIgnoreCase("flag")) return List.of("true", "false"); return List.of(); }

    private static final class Claim { final String id; final UUID world; final String worldName; final int minX, maxX, minZ, maxZ; final Map<String, Boolean> flags = new HashMap<>(); final Map<String, String> text = new HashMap<>(); Claim(String id, UUID world, String worldName, int minX, int maxX, int minZ, int maxZ) { this.id = id; this.world = world; this.worldName = worldName; this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ; } static Claim create(String id, Location a, Location b) { Claim c = new Claim(id, a.getWorld().getUID(), a.getWorld().getName(), Math.min(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockZ(), b.getBlockZ())); for (String f : FLAGS) c.flags.put(f, false); c.text.put("welcome", "&aWELCOME"); c.text.put("welcome-subtitle", "&f" + id); return c; } boolean flag(String flag) { return flags.getOrDefault(flag, false); } }
}
