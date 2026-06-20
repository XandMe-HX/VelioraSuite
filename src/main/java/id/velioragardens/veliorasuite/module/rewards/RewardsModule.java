package id.velioragardens.veliorasuite.module.rewards;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import id.velioragardens.veliorasuite.util.TimeUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RewardsModule extends AbstractModule implements CommandExecutor, TabCompleter {
    private File dataFile;
    private FileConfiguration data;

    public RewardsModule(VelioraSuite plugin) { super(plugin, "rewards", "rewards"); }

    @Override protected void onEnable() {
        loadData();
        register("daily"); register("vreward");
        plugin.getLogger().info("VelioraRewards module started.");
    }
    @Override protected void onDisable() { save(); plugin.getLogger().info("VelioraRewards module stopped."); }

    private void register(String name) { PluginCommand c = plugin.getCommand(name); if (c != null) { c.setExecutor(this); c.setTabCompleter(this); } }
    private void loadData() { File folder = new File(plugin.getDataFolder(), "data"); if (!folder.exists()) folder.mkdirs(); dataFile = new File(folder, "rewards.yml"); data = YamlConfiguration.loadConfiguration(dataFile); }
    private void save() { try { if (data != null) data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Gagal save rewards.yml: " + e.getMessage()); } }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("vreward")) {
            if (!sender.hasPermission("veliorasuite.rewards.admin")) { sender.sendMessage(color(msg("no-permission", Map.of()))); return true; }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { configFile.reload(); sender.sendMessage(color(msg("reload", Map.of()))); return true; }
            if (args.length > 1 && args[0].equalsIgnoreCase("reset")) { data.set("players." + args[1].toLowerCase(Locale.ROOT), null); save(); sender.sendMessage(color("&8【&aVelioraRewards&8】 &aReset reward data untuk &f" + args[1])); return true; }
            sender.sendMessage(color("&8【&aVelioraRewards&8】 &f/vreward reload, /vreward reset <player>")); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Only player."); return true; }
        if (!player.hasPermission("veliorasuite.daily.use")) { player.sendMessage(color(msg("no-permission", Map.of()))); return true; }
        claim(player); return true;
    }

    private void claim(Player player) {
        long cooldown = configFile.get().getLong("daily.cooldown-hours", 24) * 3600_000L;
        String path = "players." + player.getName().toLowerCase(Locale.ROOT) + ".last-claim";
        long last = data.getLong(path, 0L);
        long now = System.currentTimeMillis();
        if (last > 0 && now - last < cooldown) {
            player.sendMessage(color(msg("cooldown", Map.of("time", TimeUtil.formatSeconds((cooldown - (now - last)) / 1000)))));
            return;
        }
        FileConfiguration cfg = configFile.get();
        double money = cfg.getDouble("daily.rewards.money", 250);
        Economy eco = plugin.getHookManager().getEconomy();
        if (money > 0 && eco != null) eco.depositPlayer(player, money);
        int exp = cfg.getInt("daily.rewards.exp", 25);
        if (exp > 0) player.giveExp(exp);
        ConfigurationSection items = cfg.getConfigurationSection("daily.rewards.items");
        if (items != null) for (String key : items.getKeys(false)) {
            Material material = Material.matchMaterial(items.getString(key + ".material", "BREAD"));
            int amount = items.getInt(key + ".amount", 1);
            if (material != null) player.getInventory().addItem(new ItemStack(material, amount)).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        data.set(path, now); save();
        player.sendMessage(color(msg("claim", Map.of("money", String.valueOf((int)money), "exp", String.valueOf(exp)))));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if (alias.equalsIgnoreCase("vreward") && args.length == 1) return List.of("reload", "reset"); return List.of(); }
    private String color(String s) { return ColorUtil.color(s); }
    private String msg(String key, Map<String, String> vars) { String text = configFile.get().getString("messages." + key, "&8【&aVelioraRewards&8】 &cMessage not found: " + key); for (var e: vars.entrySet()) text=text.replace("%"+e.getKey()+"%", e.getValue()); return text; }
}
