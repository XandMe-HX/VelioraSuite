package id.velioragardens.veliorasuite.module.autotool;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Selects only a suitable existing hotbar tool; it never creates, moves, or removes items. */
public final class AutoToolManager implements Listener, CommandExecutor, TabCompleter {
    private final VelioraSuite plugin;
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    private FileConfiguration config;
    private YamlConfiguration players;
    private BufferedYamlWriter writer;

    public AutoToolManager(VelioraSuite plugin) { this.plugin = plugin; }

    public void load() {
        if (writer != null) writer.shutdown();
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/autotool.yml"));
        File dataFile = new File(plugin.getDataFolder(), "data/autotool.yml");
        players = YamlConfiguration.loadConfiguration(dataFile);
        writer = new BufferedYamlWriter(plugin, dataFile, players, "data AutoTool");
        writer.start();
    }
    public void start() { if (writer != null) writer.start(); }
    public void shutdown() { if (writer != null) writer.shutdown(); lastSwitch.clear(); }
    public boolean isModuleEnabled() { return config != null && config.getBoolean("settings.enabled", true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (event.getInstaBreak() || !isEnabled(event.getPlayer())) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, config.getLong("settings.switch-cooldown-ticks", 2L)) * 50L;
        if (now - lastSwitch.getOrDefault(player.getUniqueId(), 0L) < cooldown) return;
        int slot = bestSlot(player, targetTool(event.getBlock()));
        if (slot < 0 || slot == player.getInventory().getHeldItemSlot()) return;
        player.getInventory().setHeldItemSlot(slot);
        lastSwitch.put(player.getUniqueId(), now);
    }

    private boolean isEnabled(Player player) {
        if (!isModuleEnabled() || !players.getBoolean("players." + player.getUniqueId() + ".enabled", true)) return false;
        return !config.getBoolean("settings.require-permission", true) || player.hasPermission("veliorasuite.autotool.use");
    }

    private Tool targetTool(Block block) {
        Material type = block.getType();
        if (Tag.LEAVES.isTagged(type) || type == Material.COBWEB || type.name().endsWith("_WOOL")) return Tool.SHEARS;
        if (Tag.MINEABLE_AXE.isTagged(type)) return Tool.AXE;
        if (Tag.MINEABLE_SHOVEL.isTagged(type)) return Tool.SHOVEL;
        if (Tag.MINEABLE_HOE.isTagged(type)) return Tool.HOE;
        return Tag.MINEABLE_PICKAXE.isTagged(type) ? Tool.PICKAXE : Tool.NONE;
    }

    private int bestSlot(Player player, Tool target) {
        if (target == Tool.NONE) return -1;
        int winner = -1, bestScore = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir() || !target.matches(item.getType())) continue;
            int score = tier(item.getType()) * 100 + item.getEnchantmentLevel(Enchantment.EFFICIENCY);
            if (score > bestScore) { bestScore = score; winner = slot; }
        }
        return winner;
    }

    private int tier(Material material) {
        String name = material.name();
        if (name.startsWith("NETHERITE_")) return 6;
        if (name.startsWith("DIAMOND_")) return 5;
        if (name.startsWith("IRON_")) return 4;
        if (name.startsWith("GOLDEN_")) return 3;
        if (name.startsWith("STONE_")) return 2;
        return name.startsWith("WOODEN_") ? 1 : 0;
    }

    private void setEnabled(Player player, boolean enabled) {
        players.set("players." + player.getUniqueId() + ".enabled", enabled);
        writer.markDirty();
    }
    private String msg(String key) { return color(config.getString("messages.prefix", "") + config.getString("messages." + key, "")); }
    private String color(String value) { return value == null ? "" : value.replace('&', '§'); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya untuk player."); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("reload")) {
            if (!player.hasPermission("veliorasuite.autotool.reload")) { player.sendMessage("§cTidak ada izin."); return true; }
            load(); player.sendMessage(msg("reloaded")); return true;
        }
        boolean state = players.getBoolean("players." + player.getUniqueId() + ".enabled", true);
        if (action.equals("on")) { setEnabled(player, true); player.sendMessage(msg("enabled")); return true; }
        if (action.equals("off")) { setEnabled(player, false); player.sendMessage(msg("disabled")); return true; }
        player.sendMessage(msg("status").replace("{status}", state ? "§aaktif" : "§cmati"));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("on", "off", "status", "reload") : List.of();
    }

    private enum Tool {
        PICKAXE("_PICKAXE"), AXE("_AXE"), SHOVEL("_SHOVEL"), HOE("_HOE"), SHEARS(null), NONE(null);
        private final String suffix;
        Tool(String suffix) { this.suffix = suffix; }
        boolean matches(Material material) { return this == SHEARS ? material == Material.SHEARS : suffix != null && material.name().endsWith(suffix); }
    }
}
