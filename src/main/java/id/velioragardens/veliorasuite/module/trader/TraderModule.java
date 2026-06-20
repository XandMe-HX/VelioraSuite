package id.velioragardens.veliorasuite.module.trader;

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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TraderModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {
    private BukkitTask broadcastTask;
    private final String holderKey = "VelioraTrader";

    public TraderModule(VelioraSuite plugin) { super(plugin, "trader", "trader"); }
    @Override protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand c = plugin.getCommand("vtrader"); if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        startAutoBroadcast();
        plugin.getLogger().info("VelioraTrader module started.");
    }
    @Override protected void onDisable() { HandlerList.unregisterAll(this); if (broadcastTask != null) broadcastTask.cancel(); plugin.getLogger().info("VelioraTrader module stopped."); }

    private void startAutoBroadcast() {
        if (!configFile.get().getBoolean("settings.auto-announcement", true)) return;
        long interval = Math.max(5, configFile.get().getLong("settings.spawn-interval-minutes", 60)) * 1200L;
        broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.broadcastMessage(color(msg("spawn", Map.of("location", configFile.get().getString("settings.location-name", "Spawn"))))), interval, interval);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("veliorasuite.trader.admin")) { sender.sendMessage(color(msg("no-permission", Map.of()))); return true; }
            configFile.reload(); if (broadcastTask != null) broadcastTask.cancel(); startAutoBroadcast(); sender.sendMessage(color(msg("reload", Map.of()))); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Only player."); return true; }
        if (!player.hasPermission("veliorasuite.trader.use")) { player.sendMessage(color(msg("no-permission", Map.of()))); return true; }
        open(player); return true;
    }

    private void open(Player player) {
        int size = Math.max(9, configFile.get().getInt("gui.size", 27));
        Inventory inv = Bukkit.createInventory(null, size, color(configFile.get().getString("gui.title", "&8Veliora Trader")));
        ConfigurationSection items = configFile.get().getConfigurationSection("items");
        if (items != null) for (String id : items.getKeys(false)) {
            String path = "items." + id;
            if (!configFile.get().getBoolean(path + ".enabled", true)) continue;
            Material material = Material.matchMaterial(configFile.get().getString(path + ".material", "EMERALD"));
            if (material == null) material = Material.EMERALD;
            ItemStack stack = new ItemStack(material, Math.max(1, configFile.get().getInt(path + ".amount", 1)));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(configFile.get().getString(path + ".name", "&a" + id)));
                List<String> lore = new ArrayList<>();
                for (String line : configFile.get().getStringList(path + ".lore")) lore.add(color(line.replace("%price%", String.valueOf(configFile.get().getInt(path + ".price", 0)))));
                lore.add(color("&7Harga: &e" + configFile.get().getInt(path + ".price", 0)));
                lore.add(color("&8ID: " + id));
                meta.setLore(lore); stack.setItemMeta(meta);
            }
            int slot = configFile.get().getInt(path + ".slot", 0);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
        }
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(color(configFile.get().getString("gui.title", "&8Veliora Trader")))) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem(); if (clicked == null || !clicked.hasItemMeta()) return;
        String id = findItemBySlot(event.getSlot()); if (id == null) return;
        String path = "items." + id;
        int price = configFile.get().getInt(path + ".price", 0);
        Economy eco = plugin.getHookManager().getEconomy();
        if (price > 0) {
            if (eco == null) { player.sendMessage(color(msg("economy-not-ready", Map.of()))); return; }
            if (eco.getBalance(player) < price) { player.sendMessage(color(msg("not-enough-money", Map.of("price", String.valueOf(price))))); return; }
            eco.withdrawPlayer(player, price);
        }
        player.getInventory().addItem(clicked.clone()).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(color(msg("bought", Map.of("item", id, "price", String.valueOf(price)))));
    }

    private String findItemBySlot(int slot) { ConfigurationSection items = configFile.get().getConfigurationSection("items"); if (items==null) return null; for(String id:items.getKeys(false)) if(configFile.get().getInt("items."+id+".slot",-1)==slot) return id; return null; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if(args.length==1) return List.of("open","reload"); return List.of(); }
    private String color(String s) { return ColorUtil.color(s); }
    private String msg(String key, Map<String,String> vars) { String s=configFile.get().getString("messages."+key,"&8【&aVelioraTrader&8】 &cMessage not found: "+key); for(var e:vars.entrySet()) s=s.replace("%"+e.getKey()+"%",e.getValue()); return s; }
}
