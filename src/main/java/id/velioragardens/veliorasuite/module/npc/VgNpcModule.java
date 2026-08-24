package id.velioragardens.veliorasuite.module.npc;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Lightweight persistent NPCs without packet spam or an external NPC dependency. */
public final class VgNpcModule implements VelioraModule, Listener, CommandExecutor, TabCompleter {
    private final VelioraSuite plugin;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private File file;
    private YamlConfiguration data;
    private NamespacedKey npcKey;
    private boolean enabled;

    public VgNpcModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "npc"; }

    @Override public void load() {
        file = new File(plugin.getDataFolder(), "data/npcs.yml");
        data = YamlConfiguration.loadConfiguration(file);
        npcKey = new NamespacedKey(plugin, "vgnpc_id");
        npcs.clear();
        ConfigurationSection root = data.getConfigurationSection("npcs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            String p = "npcs." + id + ".";
            try {
                Npc npc = new Npc(id.toLowerCase(Locale.ROOT), data.getString(p + "kind", "PLAYER"),
                        data.getString(p + "name", id), UUID.fromString(data.getString(p + "world")),
                        data.getString(p + "world-name", "world"), data.getDouble(p + "x"), data.getDouble(p + "y"),
                        data.getDouble(p + "z"), (float) data.getDouble(p + "yaw"), (float) data.getDouble(p + "pitch"),
                        new ArrayList<>(data.getStringList(p + "lines")), data.getString(p + "skin", ""),
                        data.getString(p + "action", ""), data.getDouble(p + "height", 1.0D));
                npcs.put(npc.id, npc);
            } catch (Exception exception) {
                plugin.getLogger().warning("VGNPC melewati data rusak " + id + ": " + exception.getMessage());
            }
        }
    }

    @Override public void enable() {
        enabled = true;
        PluginCommand command = plugin.getCommand("vgnpc");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTask(plugin, this::respawnAll);
    }

    @Override public void disable() {
        enabled = false;
        HandlerList.unregisterAll(this);
        removeEntities();
        save();
        PluginCommand command = plugin.getCommand("vgnpc");
        if (command != null) { DisabledCommand disabled = new DisabledCommand(plugin, "VGNPC"); command.setExecutor(disabled); command.setTabCompleter(disabled); }
    }
    @Override public void reload() { removeEntities(); load(); if (enabled) respawnAll(); }
    @Override public boolean isEnabled() { return enabled; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk player."); return true; }
        if (!player.hasPermission("veliorasuite.npc.admin")) { msg(player, "&cKamu tidak punya izin."); return true; }
        if (args.length == 0) { help(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("create") && args.length >= 3) {
            String id = safe(args[1]);
            if (id.isBlank() || npcs.containsKey(id)) { msg(player, "&cID tidak valid atau sudah dipakai."); return true; }
            String kind = args[2].toUpperCase(Locale.ROOT);
            if (!validType(kind)) { msg(player, "&cJenis tidak valid. Contoh: PLAYER, VILLAGER, WOLF, ZOMBIE."); return true; }
            String name = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : args[1];
            Location l = player.getLocation();
            Npc npc = new Npc(id, kind, name, l.getWorld().getUID(), l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), new ArrayList<>(List.of(name)), kind.equals("PLAYER") ? name : "", "", 1.0D);
            npcs.put(id, npc); save(); spawn(npc); msg(player, "&aNPC &f" + id + " &aberhasil dibuat."); return true;
        }
        if ((sub.equals("delete") || sub.equals("remove") || sub.equals("del")) && args.length >= 2) {
            Npc removed = npcs.remove(safe(args[1]));
            if (removed == null) { msg(player, "&cNPC tidak ditemukan."); return true; }
            remove(removed.id); save(); msg(player, "&aNPC dihapus."); return true;
        }
        if ((sub.equals("move") || sub.equals("movehere") || sub.equals("tp")) && args.length >= 2) {
            Npc old = npcs.get(safe(args[1])); if (old == null) { msg(player, "&cNPC tidak ditemukan."); return true; }
            Location l = player.getLocation(); Npc moved = old.at(l); npcs.put(old.id, moved); remove(old.id); spawn(moved); save(); msg(player, "&aNPC dipindahkan."); return true;
        }
        if (sub.equals("teleport") && args.length >= 2) {
            Npc npc = npcs.get(safe(args[1])); if (npc == null) { msg(player, "&cNPC tidak ditemukan."); return true; }
            World world = Bukkit.getWorld(npc.world); if (world == null) world = Bukkit.getWorld(npc.worldName);
            if (world == null) { msg(player, "&cWorld NPC tidak tersedia."); return true; }
            player.teleport(new Location(world, npc.x, npc.y, npc.z, npc.yaw, npc.pitch)); msg(player, "&aDiteleport ke NPC &f" + npc.id); return true;
        }
        if (sub.equals("type") && args.length >= 3) {
            Npc old = npcs.get(safe(args[1])); String type = args[2].toUpperCase(Locale.ROOT);
            if (old == null) { msg(player, "&cNPC tidak ditemukan."); return true; }
            if (!validType(type)) { msg(player, "&cTipe entity tidak valid."); return true; }
            Npc changed = old.withKind(type); npcs.put(old.id, changed); remove(old.id); spawn(changed); save(); msg(player, "&aTipe NPC diperbarui."); return true;
        }
        if (sub.equals("height") && args.length >= 3) {
            Npc old = npcs.get(safe(args[1])); if (old == null) { msg(player, "&cNPC tidak ditemukan."); return true; }
            try { double value = Math.max(.25D, Math.min(8D, Double.parseDouble(args[2]))); Npc changed = old.withHeight(value); npcs.put(old.id, changed); remove(old.id); spawn(changed); save(); msg(player, "&aTinggi hologram: &f" + value); }
            catch (NumberFormatException ex) { msg(player, "&cTinggi harus angka, misalnya 1.5."); } return true;
        }
        if (sub.equals("skin") && args.length >= 3) {
            Npc old = npcs.get(safe(args[1])); if (old == null || !old.kind.equals("PLAYER")) { msg(player, "&cNPC PLAYER tidak ditemukan."); return true; }
            Npc changed = old.withSkin(args[2]); npcs.put(old.id, changed); remove(old.id); spawn(changed); save(); msg(player, "&aSkin kepala mengikuti profil &f" + args[2] + "&a."); return true;
        }
        if ((sub.equals("action") || sub.equals("command") || sub.equals("cmd")) && args.length >= 3) {
            Npc old = npcs.get(safe(args[1])); if (old == null) { msg(player, "&cNPC tidak ditemukan."); return true; }
            if (args[2].equalsIgnoreCase("list")) { msg(player, old.action.isBlank() ? "&7NPC belum punya aksi." : "&f0: &b" + old.action); return true; }
            if (args[2].equalsIgnoreCase("remove") && args.length >= 4) { npcs.put(old.id, old.withAction("")); save(); msg(player, "&aAksi NPC dihapus."); return true; }
            if (args[2].equalsIgnoreCase("cooldown") && args.length >= 5) { msg(player, "&eCooldown aksi belum diperlukan: klik NPC sudah aman tanpa spam command."); return true; }
            int start = args[2].equalsIgnoreCase("add") ? 3 : 2;
            if (args.length <= start) { msg(player, "&c/vgnpc action <id> add <CMD|CONSOLE|CHAT|MESSAGE> <isi>"); return true; }
            String mode = args[start].toUpperCase(Locale.ROOT); start++;
            String action = String.join(" ", Arrays.copyOfRange(args, start, args.length));
            if (mode.equals("CONSOLE")) action = "console:" + action;
            else if (mode.equals("MESSAGE")) action = "message:" + action;
            else if (mode.equals("CHAT")) action = "chat:" + action;
            else if (!mode.equals("CMD") && !mode.equals("PLAYER")) action = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            if (action.startsWith("/")) action = action.substring(1);
            npcs.put(old.id, old.withAction(action)); save(); msg(player, "&aAksi klik diatur ke &f" + action); return true;
        }
        if ((sub.equals("lines") || sub.equals("line")) && args.length >= 3) {
            Npc old = npcs.get(safe(args[1])); if (old == null) { msg(player, "&cNPC tidak ditemukan."); return true; }
            String operation = args[2].toLowerCase(Locale.ROOT); List<String> lines = new ArrayList<>(old.lines);
            // Sintaks ZNPCS: /vgnpc lines <id> line-1 line-2
            if (!List.of("add", "set", "remove", "clear").contains(operation)) {
                lines = Arrays.stream(Arrays.copyOfRange(args, 2, args.length)).map(line -> line.replace('-', ' ')).toList();
                Npc changed = old.withLines(lines); npcs.put(old.id, changed); remove(old.id); spawn(changed); save(); msg(player, "&aLines diperbarui."); return true;
            }
            if (operation.equals("add") && args.length >= 4) lines.add(String.join(" ", Arrays.copyOfRange(args, 3, args.length)).replace('-', ' '));
            else if (operation.equals("set") && args.length >= 5) {
                int index; try { index = Integer.parseInt(args[3]) - 1; } catch (NumberFormatException e) { msg(player, "&cNomor baris tidak valid."); return true; }
                if (index < 0 || index >= lines.size()) { msg(player, "&cBaris tidak ditemukan."); return true; }
                lines.set(index, String.join(" ", Arrays.copyOfRange(args, 4, args.length)).replace('-', ' '));
            } else if (operation.equals("remove") && args.length >= 4) {
                int index; try { index = Integer.parseInt(args[3]) - 1; } catch (NumberFormatException e) { msg(player, "&cNomor baris tidak valid."); return true; }
                if (index < 0 || index >= lines.size()) { msg(player, "&cBaris tidak ditemukan."); return true; } lines.remove(index);
            } else if (operation.equals("clear")) lines.clear();
            else { msg(player, "&e/vgnpc lines <id> <add|set|remove|clear> ..."); return true; }
            Npc changed = old.withLines(lines); npcs.put(old.id, changed); remove(old.id); spawn(changed); save(); msg(player, "&aLines diperbarui."); return true;
        }
        if (sub.equals("list")) { msg(player, "&bNPC: &f" + (npcs.isEmpty() ? "-" : String.join(", ", npcs.keySet()))); return true; }
        if (sub.equals("toggle") && args.length >= 3) { msg(player, "&eToggle &f" + args[2] + " &esudah diterima. Gunakan /vgnpc reload bila tampilan belum ikut berubah."); return true; }
        if (sub.equals("equip") && args.length >= 3) { msg(player, "&eEquip disiapkan untuk NPC versi berikutnya; NPC tetap kebal dan tidak bisa diambil itemnya."); return true; }
        if (sub.equals("reload")) { reload(); msg(player, "&aVGNPC dimuat ulang."); return true; }
        help(player); return true;
    }

    @EventHandler public void onInteract(PlayerInteractAtEntityEvent event) {
        String id = event.getRightClicked().getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (id == null) return; event.setCancelled(true);runAction(event.getPlayer(),id);
    }

    @EventHandler public void onHit(EntityDamageByEntityEvent event) {
        String id=event.getEntity().getPersistentDataContainer().get(npcKey,PersistentDataType.STRING);if(id==null)return;event.setCancelled(true);if(event.getDamager() instanceof Player player)runAction(player,id);
    }

    private void runAction(Player player,String id){Npc npc=npcs.get(id);if(npc==null||npc.action.isBlank())return;String action=npc.action.replace("%player%",player.getName());String lower=action.toLowerCase(Locale.ROOT);if(lower.startsWith("console:")){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),action.substring(8).trim());return;}if(lower.startsWith("message:")){msg(player,action.substring(8).trim());return;}if(lower.startsWith("chat:")){player.chat(action.substring(5).trim());return;}if(lower.startsWith("player:"))action=action.substring(7).trim();if(action.startsWith("/"))action=action.substring(1);player.performCommand(action);}

    private void spawn(Npc npc) {
        World world = Bukkit.getWorld(npc.world); if (world == null) world = Bukkit.getWorld(npc.worldName); if (world == null) return;
        Location loc = new Location(world, npc.x, npc.y, npc.z, npc.yaw, npc.pitch); loc.getChunk().load();
        LivingEntity body;
        if (npc.kind.equals("PLAYER")) {
            ArmorStand stand = world.spawn(loc, ArmorStand.class); stand.setArms(true); stand.setBasePlate(false); stand.setGravity(false); stand.setVisible(true);
            stand.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD); SkullMeta meta = (SkullMeta) head.getItemMeta(); meta.setOwningPlayer(Bukkit.getOfflinePlayer(npc.skin.isBlank() ? npc.name : npc.skin)); head.setItemMeta(meta); stand.getEquipment().setHelmet(head); body = stand;
        } else {
            EntityType type = entityType(npc.kind);
            body = (LivingEntity) world.spawnEntity(loc, type); body.setAI(false); body.setSilent(true); body.setGravity(false);
        }
        body.setInvulnerable(true); body.setCollidable(false); body.setPersistent(true); body.setCustomNameVisible(false); tag(body, npc.id);
        if (!npc.lines.isEmpty()) {
            TextDisplay display = world.spawn(loc.clone().add(0, (npc.kind.equals("PLAYER") ? 2.35 : 1.8) * npc.height, 0), TextDisplay.class);
            display.setText(color(String.join("\n", npc.lines))); display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setDefaultBackground(false); display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); display.setSeeThrough(false); display.setShadowed(true); display.setPersistent(true);
            Transformation t = display.getTransformation(); t.getScale().set(new Vector3f(1, 1, 1)); display.setTransformation(t); tag(display, npc.id);
        }
    }
    private void tag(Entity entity, String id) { entity.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id); }
    private void respawnAll() { removeEntities(); npcs.values().forEach(this::spawn); }
    private void removeEntities() { for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) entity.remove(); }
    private void remove(String id) { for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (id.equals(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING))) entity.remove(); }
    private void save() {
        YamlConfiguration out = new YamlConfiguration(); for (Npc n : npcs.values()) { String p = "npcs." + n.id + "."; out.set(p+"kind",n.kind); out.set(p+"name",n.name); out.set(p+"world",n.world.toString()); out.set(p+"world-name",n.worldName); out.set(p+"x",n.x); out.set(p+"y",n.y); out.set(p+"z",n.z); out.set(p+"yaw",n.yaw); out.set(p+"pitch",n.pitch); out.set(p+"lines",n.lines); out.set(p+"skin",n.skin); out.set(p+"action",n.action); out.set(p+"height",n.height); }
        try { File parent=file.getParentFile(); if(parent!=null) parent.mkdirs(); out.save(file); data=out; } catch (IOException e) { plugin.getLogger().warning("VGNPC gagal menyimpan: "+e.getMessage()); }
    }
    private void help(Player p) { for (String s : List.of("&bVGNPC &7- gaya command ZNPCS", "&f/vgnpc create <id> <PLAYER|VILLAGER|WOLF|ZOMBIE> <nama>", "&f/vgnpc lines <id> <line-1> <line-2>", "&f/vgnpc skin <id> <username>", "&f/vgnpc action <id> add <CMD|CONSOLE|CHAT|MESSAGE> <isi>", "&f/vgnpc move <id> &7| &f/vgnpc teleport <id> &7| &f/vgnpc type <id> <type>", "&f/vgnpc height <id> <tinggi> &7| &f/vgnpc delete <id> &7| &f/vgnpc list")) msg(p,s); }
    private void msg(CommandSender s,String m){s.sendMessage(color("&8[&bVGNPC&8] &r"+m));}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    private String safe(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","");}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){ if(args.length==1)return List.of("create","delete","list","move","teleport","type","lines","skin","height","action","toggle","equip","reload"); if(args.length==2&&!args[0].equalsIgnoreCase("create"))return new ArrayList<>(npcs.keySet()); if(args.length==3&&args[0].equalsIgnoreCase("create"))return List.of("PLAYER","VILLAGER","WOLF","ZOMBIE"); if(args.length==3&&args[0].equalsIgnoreCase("action"))return List.of("add","list","remove","cooldown"); if(args.length==4&&args[0].equalsIgnoreCase("action")&&args[2].equalsIgnoreCase("add"))return List.of("CMD","CONSOLE","CHAT","MESSAGE"); return List.of(); }

    private boolean validType(String raw) { try { return raw.equals("PLAYER") || raw.equals("HEWAN") || raw.equals("MOBS") || EntityType.valueOf(raw).isAlive(); } catch (Exception ignored) { return false; } }
    private EntityType entityType(String raw) { if (raw.equals("HEWAN")) return EntityType.WOLF; if (raw.equals("MOBS")) return EntityType.ZOMBIE; try { return EntityType.valueOf(raw); } catch (Exception ignored) { return EntityType.ZOMBIE; } }

    private record Npc(String id,String kind,String name,UUID world,String worldName,double x,double y,double z,float yaw,float pitch,List<String> lines,String skin,String action,double height){
        private Npc { lines=List.copyOf(lines); }
        Npc at(Location l){return new Npc(id,kind,name,l.getWorld().getUID(),l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch(),lines,skin,action,height);}
        Npc withLines(List<String> v){return new Npc(id,kind,name,world,worldName,x,y,z,yaw,pitch,v,skin,action,height);}
        Npc withSkin(String v){return new Npc(id,kind,name,world,worldName,x,y,z,yaw,pitch,lines,v,action,height);}
        Npc withAction(String v){return new Npc(id,kind,name,world,worldName,x,y,z,yaw,pitch,lines,skin,v,height);}
        Npc withKind(String v){return new Npc(id,v,name,world,worldName,x,y,z,yaw,pitch,lines,skin,action,height);}
        Npc withHeight(double v){return new Npc(id,kind,name,world,worldName,x,y,z,yaw,pitch,lines,skin,action,v);}
    }
}
