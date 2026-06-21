package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SkillsModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {

    private File dataFile;
    private FileConfiguration data;
    private BukkitTask manaTask;
    private BukkitTask actionBarTask;
    private final Map<UUID, Integer> mana = new HashMap<>();
    private SkillsPlaceholderExpansion placeholderExpansion;

    public SkillsModule(VelioraSuite plugin) {
        super(plugin, "skills", "skills");
    }

    @Override
    protected void onEnable() {
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        register("skills");
        register("vskills");
        startManaTask();
        startActionBarTask();
        registerPlaceholders();
        plugin.getLogger().info("VelioraSkills module started.");
    }

    @Override
    protected void onDisable() {
        if (manaTask != null) manaTask.cancel();
        if (actionBarTask != null) actionBarTask.cancel();
        if (placeholderExpansion != null) {
            try { placeholderExpansion.unregister(); } catch (Throwable ignored) { }
            placeholderExpansion = null;
        }
        HandlerList.unregisterAll(this);
        save();
        plugin.getLogger().info("VelioraSkills module stopped.");
    }

    private void register(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private void registerPlaceholders() {
        if (!plugin.getHookManager().isHooked("PlaceholderAPI")) return;
        try {
            placeholderExpansion = new SkillsPlaceholderExpansion(this);
            placeholderExpansion.register();
            plugin.getLogger().info("VelioraSkills PlaceholderAPI expansion registered.");
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Gagal register placeholder VelioraSkills: " + throwable.getMessage());
        }
    }

    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "skills.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            if (data != null && dataFile != null) data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal save skills.yml: " + e.getMessage());
        }
    }

    private void startManaTask() {
        if (!configFile.get().getBoolean("mana.enabled", true)) return;
        long period = Math.max(20L, configFile.get().getLong("mana.regen-seconds", 300) * 20L);
        manaTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int max = getMaxMana();
            int amount = configFile.get().getInt("mana.regen-amount", 1);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                mana.put(player.getUniqueId(), Math.min(max, getMana(player) + amount));
            }
        }, period, period);
    }

    private void startActionBarTask() {
        if (!configFile.get().getBoolean("actionbar.enabled", true)) return;
        long period = Math.max(10L, configFile.get().getLong("actionbar.update-ticks", 40L));
        actionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!player.isOnline()) continue;
                String text = applyPlaceholders(player, configFile.get().getString("actionbar.format", "&c❤ %health%/%max_health% &8| &b✦ %mana%/%max_mana%"));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ColorUtil.color(text)));
            }
        }, 40L, period);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        mana.putIfAbsent(event.getPlayer().getUniqueId(), getStoredMana(event.getPlayer().getUniqueId()));
    }

    public boolean takeMana(Player player, int amount) {
        if (amount <= 0 || !configFile.get().getBoolean("mana.enabled", true)) return true;
        int current = getMana(player);
        if (current < amount) return false;
        mana.put(player.getUniqueId(), current - amount);
        saveMana(player.getUniqueId(), current - amount);
        return true;
    }

    public void addMana(Player player, int amount) {
        int value = Math.min(getMaxMana(), getMana(player) + Math.max(0, amount));
        mana.put(player.getUniqueId(), value);
        saveMana(player.getUniqueId(), value);
    }

    public void addExp(Player player, String skill, int amount) {
        if (skill == null || amount <= 0) return;
        skill = skill.toLowerCase(Locale.ROOT);
        if (!configFile.get().getBoolean("skills." + skill + ".enabled", true)) return;

        String base = "players." + player.getUniqueId() + "." + skill;
        int level = data.getInt(base + ".level", 1);
        int exp = data.getInt(base + ".exp", 0) + amount;
        int needed = expNeeded(level);

        while (exp >= needed) {
            exp -= needed;
            level++;
            player.sendMessage(ColorUtil.color(configFile.get().getString("messages.level-up", "&8【&aVelioraSkills&8】 &aSkill &f%skill% &anaik ke level &f%level%&a!")
                    .replace("%skill%", displayName(skill))
                    .replace("%level%", String.valueOf(level))));
            needed = expNeeded(level);
        }

        data.set(base + ".level", level);
        data.set(base + ".exp", exp);
        save();
    }

    public int getLevel(UUID uuid, String skill) {
        return data.getInt("players." + uuid + "." + skill.toLowerCase(Locale.ROOT) + ".level", 1);
    }

    public int getExp(UUID uuid, String skill) {
        return data.getInt("players." + uuid + "." + skill.toLowerCase(Locale.ROOT) + ".exp", 0);
    }

    public int getMana(Player player) {
        return mana.getOrDefault(player.getUniqueId(), getStoredMana(player.getUniqueId()));
    }

    public int getMaxMana() {
        return configFile.get().getInt("mana.max", 20);
    }

    private int getStoredMana(UUID uuid) {
        return data.getInt("players." + uuid + ".mana", getMaxMana());
    }

    private void saveMana(UUID uuid, int value) {
        data.set("players." + uuid + ".mana", value);
        save();
    }

    private int expNeeded(int level) {
        return configFile.get().getInt("settings.base-exp", 100) + (level * configFile.get().getInt("settings.exp-per-level", 50));
    }

    public String applyPlaceholders(Player player, String text) {
        if (text == null) return "";
        return text
                .replace("%player%", player.getName())
                .replace("%health%", String.valueOf((int) Math.ceil(player.getHealth())))
                .replace("%max_health%", String.valueOf((int) Math.ceil(player.getMaxHealth())))
                .replace("%mana%", String.valueOf(getMana(player)))
                .replace("%max_mana%", String.valueOf(getMaxMana()))
                .replace("%mining_level%", String.valueOf(getLevel(player.getUniqueId(), "mining")))
                .replace("%woodcutting_level%", String.valueOf(getLevel(player.getUniqueId(), "woodcutting")))
                .replace("%farming_level%", String.valueOf(getLevel(player.getUniqueId(), "farming")))
                .replace("%fishing_level%", String.valueOf(getLevel(player.getUniqueId(), "fishing")))
                .replace("%combat_level%", String.valueOf(getLevel(player.getUniqueId(), "combat")))
                .replace("%chef_level%", String.valueOf(getLevel(player.getUniqueId(), "chef")))
                .replace("%hunter_level%", String.valueOf(getLevel(player.getUniqueId(), "hunter")));
    }

    private String displayName(String skill) {
        return configFile.get().getString("skills." + skill + ".display-name", skill);
    }

    private List<String> skillKeys() {
        ConfigurationSection section = configFile.get().getConfigurationSection("skills");
        if (section == null) return List.of("mining", "woodcutting", "farming", "fishing", "combat", "chef", "hunter");
        return new ArrayList<>(section.getKeys(false));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("vskills") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("veliorasuite.skills.admin")) {
                sender.sendMessage(ColorUtil.color("&cNo permission."));
                return true;
            }
            configFile.reload();
            sender.sendMessage(ColorUtil.color("&8【&aVelioraSkills&8】 &aConfig berhasil direload."));
            return true;
        }

        Player target;
        if (args.length > 0 && sender.hasPermission("veliorasuite.skills.admin")) {
            target = plugin.getServer().getPlayerExact(args[0]);
        } else {
            target = sender instanceof Player p ? p : null;
        }
        if (target == null) {
            sender.sendMessage(ColorUtil.color("&cPlayer tidak ditemukan."));
            return true;
        }

        sender.sendMessage(ColorUtil.color("&8&m-------------------------"));
        sender.sendMessage(ColorUtil.color("&aVelioraSkills &7- &f" + target.getName()));
        sender.sendMessage(ColorUtil.color("&7Mana: &b" + getMana(target) + "&7/&b" + getMaxMana()));
        for (String skill : skillKeys()) {
            sender.sendMessage(ColorUtil.color("&7- &f" + displayName(skill) + " &8Lv.&a" + getLevel(target.getUniqueId(), skill) + " &7Exp &f" + getExp(target.getUniqueId(), skill)));
        }
        sender.sendMessage(ColorUtil.color("&8&m-------------------------"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("vskills") && args.length == 1) return List.of("reload");
        return List.of();
    }
}
