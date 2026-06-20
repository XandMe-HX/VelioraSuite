package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class QuestModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {
    private File dataFile;
    private FileConfiguration data;

    public QuestModule(VelioraSuite plugin) { super(plugin, "quest", "quest"); }

    @Override protected void onEnable() {
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand c = plugin.getCommand("vquest"); if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        plugin.getLogger().info("VelioraQuest module started.");
    }
    @Override protected void onDisable() { HandlerList.unregisterAll(this); save(); plugin.getLogger().info("VelioraQuest module stopped."); }

    private void loadData() { File folder = new File(plugin.getDataFolder(), "data"); if (!folder.exists()) folder.mkdirs(); dataFile = new File(folder, "quests.yml"); data = YamlConfiguration.loadConfiguration(dataFile); }
    private void save() { try { if (data != null) data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Gagal save quests.yml: " + e.getMessage()); } }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only player."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { sendList(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start") && args.length >= 2) { start(player, args[1]); return true; }
        if (sub.equals("status")) { status(player); return true; }
        if (sub.equals("abandon")) { abandon(player); return true; }
        if (sub.equals("reload") && player.hasPermission("veliorasuite.quest.admin")) { configFile.reload(); player.sendMessage(color(msg("reload", Map.of()))); return true; }
        player.sendMessage(color("&8【&aVelioraQuest&8】 &f/vquest list, start <quest>, status, abandon"));
        return true;
    }

    private void sendList(Player player) {
        player.sendMessage(color("&8&m------------------------"));
        player.sendMessage(color("&aVelioraQuest List"));
        ConfigurationSection quests = configFile.get().getConfigurationSection("quests");
        if (quests == null) { player.sendMessage(color("&7Belum ada quest di config.")); return; }
        for (String id : quests.getKeys(false)) {
            player.sendMessage(color("&7- &f" + id + " &8| &a/vquest start " + id));
        }
        player.sendMessage(color("&8&m------------------------"));
    }

    private void start(Player player, String id) {
        String path = "quests." + id;
        if (!configFile.get().isConfigurationSection(path)) { player.sendMessage(color(msg("not-found", Map.of("quest", id)))); return; }
        String base = base(player);
        if (data.getString(base + ".active") != null) { player.sendMessage(color(msg("already-active", Map.of()))); return; }
        data.set(base + ".active", id);
        data.set(base + ".progress", 0);
        data.set(base + ".started-at", System.currentTimeMillis());
        save();
        player.sendMessage(color(msg("started", Map.of("quest", display(id)))));
    }

    private void status(Player player) {
        String id = data.getString(base(player) + ".active");
        if (id == null) { player.sendMessage(color(msg("no-active", Map.of()))); return; }
        int progress = data.getInt(base(player) + ".progress", 0);
        int target = configFile.get().getInt("quests." + id + ".target-amount", 1);
        player.sendMessage(color(msg("status", Map.of("quest", display(id), "progress", String.valueOf(progress), "target", String.valueOf(target)))));
    }

    private void abandon(Player player) {
        if (data.getString(base(player) + ".active") == null) { player.sendMessage(color(msg("no-active", Map.of()))); return; }
        data.set(base(player), null); save(); player.sendMessage(color(msg("abandoned", Map.of())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { progress(e.getPlayer(), "break", e.getBlock().getType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) { Player k = e.getEntity().getKiller(); if (k != null) progress(k, "kill", e.getEntityType().name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) { if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) progress(e.getPlayer(), "fish", "ANY"); }

    private void progress(Player player, String type, String target) {
        String id = data.getString(base(player) + ".active"); if (id == null) return;
        FileConfiguration cfg = configFile.get();
        String path = "quests." + id;
        if (!type.equalsIgnoreCase(cfg.getString(path + ".type", ""))) return;
        List<String> targets = cfg.getStringList(path + ".targets");
        if (!targets.isEmpty() && !targets.contains(target) && !targets.contains("ANY")) return;
        int progress = data.getInt(base(player) + ".progress", 0) + 1;
        int need = cfg.getInt(path + ".target-amount", 1);
        data.set(base(player) + ".progress", progress); save();
        if (progress >= need) complete(player, id); else player.sendMessage(color(msg("progress", Map.of("quest", display(id), "progress", String.valueOf(progress), "target", String.valueOf(need)))));
    }

    private void complete(Player player, String id) {
        String path = "quests." + id + ".rewards";
        Economy eco = plugin.getHookManager().getEconomy();
        double money = configFile.get().getDouble(path + ".money", 0);
        if (eco != null && money > 0) eco.depositPlayer(player, money);
        int exp = configFile.get().getInt(path + ".exp", 0); if (exp > 0) player.giveExp(exp);
        data.set(base(player), null); save();
        player.sendMessage(color(msg("completed", Map.of("quest", display(id), "money", String.valueOf((int) money), "exp", String.valueOf(exp)))));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "start", "status", "abandon", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) { ConfigurationSection q=configFile.get().getConfigurationSection("quests"); return q==null?List.of():new ArrayList<>(q.getKeys(false)); }
        return List.of();
    }
    private String base(Player p) { return "players." + p.getUniqueId(); }
    private String display(String id) { return configFile.get().getString("quests." + id + ".display-name", id); }
    private String msg(String key, Map<String,String> vars) { String s=configFile.get().getString("messages."+key,"&8【&aVelioraQuest&8】 &cMessage not found: "+key); for(var e:vars.entrySet()) s=s.replace("%"+e.getKey()+"%", e.getValue()); return s; }
    private String color(String s) { return ColorUtil.color(s); }
}
