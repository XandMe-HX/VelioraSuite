package id.velioragardens.veliorasuite.module.home;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Mobile-first home UI which deliberately owns the Essentials-style command names. */
public final class HomeModule implements VelioraModule, Listener, CommandExecutor, TabCompleter {
    private static final List<String> RANKS = List.of("default","warga","petani","pengrajin","hunter","scout","knight","wizard","mithril","archon","overlord","streamer","vtuber");
    private static final Map<String,Integer> LIMITS = Map.ofEntries(Map.entry("default",1),Map.entry("warga",1),Map.entry("petani",2),Map.entry("pengrajin",2),Map.entry("hunter",3),Map.entry("scout",3),Map.entry("knight",4),Map.entry("wizard",4),Map.entry("mithril",4),Map.entry("archon",6),Map.entry("overlord",6),Map.entry("streamer",6),Map.entry("vtuber",6));
    private final VelioraSuite plugin;
    private final Map<UUID, LinkedHashMap<String, Home>> homes = new HashMap<>();
    private final Set<UUID> deleteMode = new LinkedHashSet<>();
    private File file;
    private boolean enabled;

    public HomeModule(VelioraSuite plugin){this.plugin=plugin;}
    @Override public String getName(){return "home";}
    @Override public void load(){file=new File(plugin.getDataFolder(),"data/homes.yml"); loadData();}
    @Override public void enable(){enabled=true; for(String c:List.of("sethome","home","homes","delhome","homemanager"))register(c); Bukkit.getPluginManager().registerEvents(this,plugin); if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI")!=null)new HomeExpansion().register();}
    @Override public void disable(){enabled=false; HandlerList.unregisterAll(this); save(); for(String c:List.of("sethome","home","homes","delhome","homemanager")){PluginCommand pc=plugin.getCommand(c);if(pc!=null){DisabledCommand d=new DisabledCommand(plugin,"VelioraHome");pc.setExecutor(d);pc.setTabCompleter(d);}}}
    @Override public void reload(){save();loadData();}
    @Override public boolean isEnabled(){return enabled;}
    public int count(Player p){return homes.getOrDefault(p.getUniqueId(),new LinkedHashMap<>()).size();}
    public int limit(Player p){if(p.hasPermission("veliorasuite.home.unlimited")||p.isOp())return 999;int limit=1;for(String rank:RANKS)if(p.hasPermission("essentials.sethome.multiple."+rank)||p.hasPermission("veliorasuite.home.limit."+rank))limit=Math.max(limit,LIMITS.get(rank));return limit;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Player only.");return true;}return execute(p,label,args);}
    private boolean execute(Player p,String rawLabel,String[] args){String label=rawLabel.toLowerCase(Locale.ROOT);
        if(label.equals("sethome")){if(args.length==0){openGuide(p);return true;}set(p,args[0]);return true;}
        if(label.equals("delhome")){if(args.length==0){deleteMode.add(p.getUniqueId());openHomes(p,p.getUniqueId(),false);return true;}delete(p,args[0]);return true;}
        if(label.equals("homemanager")){if(!p.hasPermission("veliorasuite.home.admin")){msg(p,"&cTidak punya izin.");return true;}openManager(p);return true;}
        if(args.length>0){teleport(p,p.getUniqueId(),args[0],false);return true;}openHomes(p,p.getUniqueId(),false);return true;}

    @EventHandler(priority=org.bukkit.event.EventPriority.HIGHEST) public void intercept(PlayerCommandPreprocessEvent e){String body=e.getMessage().substring(1);String[] split=body.split(" ");String label=split[0].toLowerCase(Locale.ROOT);if(!List.of("sethome","home","homes","delhome","homemanager").contains(label))return;e.setCancelled(true);execute(e.getPlayer(),label,Arrays.copyOfRange(split,1,split.length));}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder h)||!(e.getWhoClicked() instanceof Player p))return;e.setCancelled(true);if(e.getClickedInventory()!=e.getView().getTopInventory())return;int slot=e.getRawSlot();String action=h.actions.get(slot);if(action==null)return;
        if(action.equals("close")){p.closeInventory();return;}if(action.equals("set")){p.closeInventory();msg(p,"&eKetik &f/sethome <nama> &euntuk menyimpan posisi ini.");return;}if(action.equals("open-list")){openHomes(p,p.getUniqueId(),false);return;}if(action.equals("toggle-delete")){if(deleteMode.remove(p.getUniqueId())){}else deleteMode.add(p.getUniqueId());openHomes(p,h.target,h.admin);return;}if(action.startsWith("player:")){openHomes(p,UUID.fromString(action.substring(7)),true);return;}if(action.startsWith("home:")){String name=action.substring(5);if(!h.admin&&deleteMode.contains(p.getUniqueId())){delete(p,name);openHomes(p,p.getUniqueId(),false);}else teleport(p,h.target,name,h.admin);}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}

    private void set(Player p,String raw){String name=safe(raw);if(name.isBlank()){msg(p,"&cNama home hanya boleh huruf, angka, _ atau -.");return;}LinkedHashMap<String,Home> map=homes.computeIfAbsent(p.getUniqueId(),x->new LinkedHashMap<>());if(!map.containsKey(name)&&map.size()>=limit(p)){msg(p,"&cBatas home penuh: &f"+map.size()+"/"+limit(p));return;}Location l=p.getLocation();map.put(name,new Home(name,l.getWorld().getUID(),l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch()));save();msg(p,"&aHome &f"+name+" &adisimpan. &7("+map.size()+"/"+limit(p)+")");}
    private void delete(Player p,String raw){String name=safe(raw);LinkedHashMap<String,Home> map=homes.get(p.getUniqueId());if(map==null||map.remove(name)==null){msg(p,"&cHome tidak ditemukan.");return;}save();msg(p,"&aHome &f"+name+" &adihapus.");}
    private void teleport(Player viewer,UUID owner,String raw,boolean admin){Home h=homes.getOrDefault(owner,new LinkedHashMap<>()).get(safe(raw));if(h==null){msg(viewer,"&cHome tidak ditemukan.");return;}World w=Bukkit.getWorld(h.world);if(w==null)w=Bukkit.getWorld(h.worldName);if(w==null){msg(viewer,"&cWorld home belum dimuat.");return;}Location target=new Location(w,h.x,h.y,h.z,h.yaw,h.pitch);viewer.closeInventory();viewer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,120,0,false,false,false));for(int i=5;i>=1;i--){int n=i;Bukkit.getScheduler().runTaskLater(plugin,()->{if(!viewer.isOnline())return;viewer.sendTitle(color("&b"+n),color("&7Teleport ke home &f"+h.name),0,22,0);viewer.playSound(viewer.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.5f,1f);},(5-i)*20L);}Bukkit.getScheduler().runTaskLater(plugin,()->{if(!viewer.isOnline())return;viewer.teleport(target);viewer.removePotionEffect(PotionEffectType.BLINDNESS);viewer.sendTitle(color("&aTiba!"),color("&f"+h.name),2,25,8);viewer.playSound(viewer.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,.8f,1.1f);},100L);}
    private void openGuide(Player p){Holder h=new Holder(p.getUniqueId(),false);Inventory inv=gui(h,27,"&1Veliora &bHome");fill(inv);put(inv,h,11,Material.RED_BED,"&aSET HOME","set","&7Simpan lokasi tempatmu berdiri.","&eKetik /sethome <nama>");put(inv,h,15,Material.BOOK,"&bDAFTAR HOME","list","&7Lihat, teleport, atau hapus home.","&eKlik untuk membuka daftar.");h.actions.put(15,"open-list");p.openInventory(inv);}
    private void openHomes(Player p,UUID target,boolean admin){Holder h=new Holder(target,admin);Inventory inv=gui(h,54,admin?"&1Admin &bHome Manager":"&1Veliora &bHomes");frame(inv);LinkedHashMap<String,Home> map=homes.getOrDefault(target,new LinkedHashMap<>());int slot=10;for(Home home:map.values()){while(slot%9==0||slot%9==8)slot++;put(inv,h,slot++,Material.RED_BED,"&b"+home.name,"home:"+home.name,"&7World: &f"+home.worldName,"&7Klik: teleport",admin?"&cMode admin":"&cAktifkan mode hapus untuk menghapus");}if(!admin){put(inv,h,45,Material.LIME_BED,"&aSET HOME","set","&7Ketik /sethome <nama>");put(inv,h,53,deleteMode.contains(p.getUniqueId())?Material.LIME_DYE:Material.RED_DYE,deleteMode.contains(p.getUniqueId())?"&aMODE HAPUS AKTIF":"&cMODE HAPUS","toggle-delete","&7Tekan lalu pilih home.");}put(inv,h,49,Material.BARRIER,"&cTUTUP","close","&7Tutup menu.");p.openInventory(inv);}
    private void openManager(Player p){Holder h=new Holder(p.getUniqueId(),true);Inventory inv=gui(h,54,"&1Admin &bHome Manager");frame(inv);int slot=10;List<UUID> ids=new ArrayList<>(homes.keySet());ids.sort(Comparator.comparing(id->String.valueOf(Bukkit.getOfflinePlayer(id).getName())));for(UUID id:ids){while(slot%9==0||slot%9==8)slot++;OfflinePlayer off=Bukkit.getOfflinePlayer(id);ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)head.getItemMeta();meta.setOwningPlayer(off);meta.setDisplayName(color("&b"+(off.getName()==null?id:off.getName())));meta.setLore(color(List.of("&7Jumlah home: &f"+homes.get(id).size(),"&eKlik untuk melihat.")));head.setItemMeta(meta);inv.setItem(slot,head);h.actions.put(slot++,"player:"+id);}put(inv,h,49,Material.BARRIER,"&cTUTUP","close","&7Tutup menu.");p.openInventory(inv);}

    private Inventory gui(Holder h,int size,String title){Inventory i=Bukkit.createInventory(h,size,color(title));h.inv=i;return i;}private void fill(Inventory i){ItemStack pane=item(Material.BLUE_STAINED_GLASS_PANE," ",List.of());for(int s=0;s<i.getSize();s++)i.setItem(s,pane);}private void frame(Inventory i){ItemStack pane=item(Material.BLUE_STAINED_GLASS_PANE," ",List.of());for(int s=0;s<i.getSize();s++){int r=s/9,c=s%9;if(r==0||r==i.getSize()/9-1||c==0||c==8)i.setItem(s,pane);}}
    private void put(Inventory i,Holder h,int slot,Material m,String name,String action,String...lore){i.setItem(slot,item(m,name,List.of(lore)));h.actions.put(slot,action);}private ItemStack item(Material m,String name,List<String> lore){ItemStack s=new ItemStack(m);ItemMeta meta=s.getItemMeta();meta.setDisplayName(color(name));meta.setLore(color(lore));s.setItemMeta(meta);return s;}
    private void register(String name){PluginCommand c=plugin.getCommand(name);if(c!=null){c.setExecutor(this);c.setTabCompleter(this);}}
    private void loadData(){homes.clear();YamlConfiguration y=YamlConfiguration.loadConfiguration(file);ConfigurationSection root=y.getConfigurationSection("players");if(root==null)return;for(String uid:root.getKeys(false)){try{UUID id=UUID.fromString(uid);LinkedHashMap<String,Home> map=new LinkedHashMap<>();ConfigurationSection hs=root.getConfigurationSection(uid+".homes");if(hs!=null)for(String n:hs.getKeys(false)){String p="players."+uid+".homes."+n+".";map.put(n,new Home(n,UUID.fromString(y.getString(p+"world")),y.getString(p+"world-name","world"),y.getDouble(p+"x"),y.getDouble(p+"y"),y.getDouble(p+"z"),(float)y.getDouble(p+"yaw"),(float)y.getDouble(p+"pitch")));}homes.put(id,map);}catch(Exception e){plugin.getLogger().warning("Home rusak dilewati: "+uid);}}}
    private void save(){YamlConfiguration y=new YamlConfiguration();for(var entry:homes.entrySet())for(Home h:entry.getValue().values()){String p="players."+entry.getKey()+".homes."+h.name+".";y.set(p+"world",h.world.toString());y.set(p+"world-name",h.worldName);y.set(p+"x",h.x);y.set(p+"y",h.y);y.set(p+"z",h.z);y.set(p+"yaw",h.yaw);y.set(p+"pitch",h.pitch);}try{File par=file.getParentFile();if(par!=null)par.mkdirs();y.save(file);}catch(IOException e){plugin.getLogger().warning("Gagal menyimpan homes: "+e.getMessage());}}
    private String safe(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","");}private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}private List<String> color(List<String> s){return s.stream().map(this::color).toList();}private void msg(Player p,String s){p.sendMessage(color("&8[&bVelioraHome&8] &r"+s));}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(!(s instanceof Player p)||args.length!=1)return List.of();return new ArrayList<>(homes.getOrDefault(p.getUniqueId(),new LinkedHashMap<>()).keySet());}
    private record Home(String name,UUID world,String worldName,double x,double y,double z,float yaw,float pitch){}
    private static final class Holder implements InventoryHolder{final UUID target;final boolean admin;final Map<Integer,String> actions=new HashMap<>();Inventory inv;Holder(UUID target,boolean admin){this.target=target;this.admin=admin;}public Inventory getInventory(){return inv;}}
    private final class HomeExpansion extends PlaceholderExpansion{public String getIdentifier(){return "sethomegui";}public String getAuthor(){return "XandMe";}public String getVersion(){return plugin.getDescription().getVersion();}public boolean persist(){return true;}public String onPlaceholderRequest(Player p,String id){if(p==null)return "0";return switch(id.toLowerCase(Locale.ROOT)){case "homes"->String.valueOf(count(p));case "maxhomes"->String.valueOf(limit(p));default->null;};}}
}
