package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SkillsModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {

    private File dataFile;
    private FileConfiguration data;
    private BukkitTask manaTask;
    private final Map<UUID, Integer> mana = new HashMap<>();

    public SkillsModule(VelioraSuite plugin) { super(plugin, "skills", "skills"); }

    @Override protected void onEnable() {
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        register("skills"); register("vskills");
        startManaTask();
        plugin.getLogger().info("VelioraSkills module started.");
    }

    @Override protected void onDisable() {
        if (manaTask != null) manaTask.cancel();
        HandlerList.unregisterAll(this);
        save();
        plugin.getLogger().info("VelioraSkills module stopped.");
    }

    private void register(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
    }

    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "skills.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try { if (data != null && dataFile != null) data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Gagal save skills.yml: " + e.getMessage()); }
    }

    private void startManaTask() {
        if (!configFile.get().getBoolean("mana.enabled", true)) return;
        long period = Math.max(20L, configFile.get().getLong("mana.regen-seconds", 300) * 20L);
        manaTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int max = configFile.get().getInt("mana.max", 20);
            int amount = configFile.get().getInt("mana.regen-amount", 1);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                mana.put(player.getUniqueId(), Math.min(max, getMana(player) + amount));
            }
        }, period, period);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { mana.putIfAbsent(event.getPlayer().getUniqueId(), getMaxMana()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type.name().endsWith("_ORE") || type.name().contains("DEEPSLATE")) addExp(event.getPlayer(), "mining", configFile.get().getInt("gain.mining", 5));
        if (type.name().endsWith("_LOG") || type.name().endsWith("_STEM")) addExp(event.getPlayer(), "woodcutting", configFile.get().getInt("gain.woodcutting", 4));
        if (type.name().contains("WHEAT") || type == Material.CARROTS || type == Material.POTATOES || type == Material.BEETROOTS) addExp(event.getPlayer(), "farming", configFile.get().getInt("gain.farming", 3));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        EntityType type = event.getEntityType();
        if (type != EntityType.ARMOR_STAND && type != EntityType.VILLAGER) addExp(killer, "combat", configFile.get().getInt("gain.combat", 6));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            addExp(event.getPlayer(), "fishing", configFile.get().getInt("gain.fishing", 5));
        }
    }

    public void addExp(Player player, String skill, int amount) {
        if (!configFile.get().getBoolean("skills." + skill, true)) return;
        String base = "players." + player.getUniqueId() + "." + skill;
        int level = data.getInt(base + ".level", 1);
        int exp = data.getInt(base + ".exp", 0) + amount;
        int needed = expNeeded(level);
        while (exp >= needed) {
            exp -= needed; level++;
            player.sendMessage(ColorUtil.color(configFile.get().getString("messages.level-up", "&a%skill% %level%").replace("%skill%", skill).replace("%level%", String.valueOf(level))));
            needed = expNeeded(level);
        }
        data.set(base + ".level", level);
        data.set(base + ".exp", exp);
    }

    public int getLevel(UUID uuid, String skill) { return data.getInt("players." + uuid + "." + skill + ".level", 1); }
    public int getExp(UUID uuid, String skill) { return data.getInt("players." + uuid + "." + skill + ".exp", 0); }
    public int getMana(Player player) { return mana.getOrDefault(player.getUniqueId(), getMaxMana()); }
    public int getMaxMana() { return configFile.get().getInt("mana.max", 20); }
    private int expNeeded(int level) { return configFile.get().getInt("settings.base-exp", 100) + (level * configFile.get().getInt("settings.exp-per-level", 50)); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("vskills") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("veliorasuite.skills.admin")) { sender.sendMessage(ColorUtil.color("&cNo permission.")); return true; }
            configFile.reload(); sender.sendMessage(ColorUtil.color("&8【&aVelioraSkills&8】 &aConfig berhasil direload.")); return true;
        }
        Player target;
        if (args.length > 0 && sender.hasPermission("veliorasuite.skills.admin")) target = plugin.getServer().getPlayerExact(args[0]); else target = sender instanceof Player p ? p : null;
        if (target == null) { sender.sendMessage(ColorUtil.color("&cPlayer tidak ditemukan.")); return true; }
        sender.sendMessage(ColorUtil.color("&8&m-------------------------"));
        sender.sendMessage(ColorUtil.color("&aVelioraSkills &7- &f" + target.getName()));
        sender.sendMessage(ColorUtil.color("&7Mana: &b" + getMana(target) + "&7/&b" + getMaxMana()));
        for (String skill : List.of("mining", "woodcutting", "farming", "fishing", "combat")) sender.sendMessage(ColorUtil.color("&7- &f" + skill + " &8Lv.&a" + getLevel(target.getUniqueId(), skill) + " &7Exp &f" + getExp(target.getUniqueId(), skill)));
        sender.sendMessage(ColorUtil.color("&8&m-------------------------"));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("vskills") && args.length == 1) return List.of("reload");
        return List.of();
    }
}
