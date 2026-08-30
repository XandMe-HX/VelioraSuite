package id.velioragardens.veliorasuite.module.biome;

import id.velioragardens.veliorasuite.VelioraSuite;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Lightweight title/action-bar biome notification. No persistent display entities are created. */
public final class BiomeListener implements Listener, CommandExecutor, TabCompleter {
    private final VelioraSuite plugin;
    private final Map<UUID, String> biomes = new HashMap<>();
    private final Map<UUID, Long> announced = new HashMap<>();
    private final Map<UUID, Boolean> toggles = new HashMap<>();
    private FileConfiguration config;
    private BukkitTask task;

    public BiomeListener(VelioraSuite plugin) { this.plugin = plugin; }
    public void load() { config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/biome.yml")); }
    public void start() { stop(); task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayers, 20L, 20L); }
    public void stop() { if (task != null) { task.cancel(); task = null; } biomes.clear(); announced.clear(); toggles.clear(); }

    @EventHandler public void join(PlayerJoinEvent event) { Bukkit.getScheduler().runTaskLater(plugin, () -> announceJoin(event.getPlayer()), 40L); }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID id = event.getPlayer().getUniqueId(); biomes.remove(id); announced.remove(id); toggles.remove(id); }

    private void announceJoin(Player player) {
        if (!player.isOnline() || !enabled(player)) return;
        String biome = key(player); biomes.put(player.getUniqueId(), biome); announce(player, biome, true);
    }
    private void checkPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabled(player)) { biomes.remove(player.getUniqueId()); continue; }
            String biome = key(player); String previous = biomes.put(player.getUniqueId(), biome);
            if (previous != null && !previous.equals(biome)) announce(player, biome, false);
        }
    }
    private boolean enabled(Player player) {
        if (!config.getBoolean("settings.enabled", true) || !toggles.getOrDefault(player.getUniqueId(), true)) return false;
        return config.getStringList("worlds").stream().anyMatch(world -> world.equals("*") || world.equalsIgnoreCase(player.getWorld().getName()));
    }
    private void announce(Player player, String biome, boolean bypassCooldown) {
        long now = System.currentTimeMillis(); long cooldown = Math.max(0, config.getLong("cooldown-seconds", 8)) * 1000L;
        if (!bypassCooldown && now - announced.getOrDefault(player.getUniqueId(), 0L) < cooldown) return;
        announced.put(player.getUniqueId(), now);
        String name = friendly(biome);
        player.sendTitle(color(config.getString("display.title", "&d&lBIOME BARU")), color(config.getString("display.subtitle", "&f{biome}").replace("{biome}", name)), 8, 45, 12);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color(config.getString("display.actionbar", "&8✦ &fMemasuki &d{biome}").replace("{biome}", name))));
        player.getWorld().spawnParticle(parseParticle(config.getString("effects.particle", "END_ROD")), player.getLocation().add(0, 1, 0), Math.max(1, config.getInt("effects.count", 12)), .45, .65, .45, .01);
        player.playSound(player.getLocation(), parseSound(config.getString("effects.sound", "BLOCK_AMETHYST_BLOCK_CHIME")), .45f, 1.35f);
    }
    private String key(Player player) { return player.getLocation().getBlock().getBiome().getKey().toString(); }
    private String friendly(String biome) { String raw = biome.replaceFirst("^[^:]+:", "").replace('_', ' '); StringBuilder out = new StringBuilder(); for (String word : raw.split(" ")) if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' '); return out.toString().trim(); }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
    private Particle parseParticle(String value) { try { return Particle.valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Particle.END_ROD; } }
    private Sound parseSound(String value) { try { return Sound.valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Sound.BLOCK_AMETHYST_BLOCK_CHIME; } }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Hanya player."); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("toggle")) { boolean value = !toggles.getOrDefault(player.getUniqueId(), true); toggles.put(player.getUniqueId(), value); player.sendMessage(color("&7[&dBiome&7] " + (value ? "&aDiaktifkan." : "&cDimatikan."))); if (value) announceJoin(player); return true; }
        if (action.equals("reload") && player.hasPermission("veliorasuite.biome.admin")) { load(); player.sendMessage(color("&7[&dBiome&7] &aConfig direload.")); return true; }
        player.sendMessage(color("&7[&dBiome&7] &fStatus: " + (enabled(player) ? "&aaktif" : "&cmati") + "&7. Gunakan &f/vbiome toggle")); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return args.length == 1 ? List.of("toggle", "status", "reload") : List.of(); }
}
