package id.velioragardens.veliorasuite.module.biome;

import id.velioragardens.veliorasuite.VelioraSuite;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;

/** Lightweight title/action-bar biome notification. No persistent display entities are created. */
public final class BiomeListener implements Listener, CommandExecutor, TabCompleter {
    private final VelioraSuite plugin;
    private final Map<UUID, String> biomes = new HashMap<>();
    private final Map<UUID, String> pendingBiomes = new HashMap<>();
    private final Map<UUID, Long> pendingSince = new HashMap<>();
    private final Map<UUID, Long> announced = new HashMap<>();
    private final Map<UUID, Boolean> toggles = new HashMap<>();
    private final Map<UUID, java.util.Set<String>> discovered = new HashMap<>();
    private final Map<UUID, BukkitTask> animations = new HashMap<>();
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private FileConfiguration config;
    private BukkitTask task;

    public BiomeListener(VelioraSuite plugin) { this.plugin = plugin; }
    public void load() { config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/biome.yml")); }
    public void start() { stop(); task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayers, 20L, 10L); }
    public void stop() { if (task != null) { task.cancel(); task = null; } animations.values().forEach(BukkitTask::cancel); animations.clear(); biomes.clear(); pendingBiomes.clear(); pendingSince.clear(); announced.clear(); toggles.clear(); discovered.clear(); }

    @EventHandler public void join(PlayerJoinEvent event) { Bukkit.getScheduler().runTaskLater(plugin, () -> announceJoin(event.getPlayer()), 40L); }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID id = event.getPlayer().getUniqueId(); biomes.remove(id); pendingBiomes.remove(id); pendingSince.remove(id); announced.remove(id); toggles.remove(id); discovered.remove(id); BukkitTask animation=animations.remove(id); if(animation!=null)animation.cancel(); }

    private void announceJoin(Player player) {
        if (!player.isOnline() || !enabled(player)) return;
        String biome = key(player); biomes.put(player.getUniqueId(), biome);
        if (config.getBoolean("display.announce-on-join", false)) announce(player, biome, true);
    }
    private void checkPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabled(player)) { biomes.remove(player.getUniqueId()); continue; }
            UUID uuid = player.getUniqueId(); String biome = key(player); String previous = biomes.get(uuid);
            if (previous == null) { biomes.put(uuid, biome); continue; }
            if (previous.equals(biome)) { pendingBiomes.remove(uuid); pendingSince.remove(uuid); continue; }
            long now = System.currentTimeMillis();
            if (!biome.equals(pendingBiomes.get(uuid))) { pendingBiomes.put(uuid, biome); pendingSince.put(uuid, now); continue; }
            long settle = Math.max(1L, config.getLong("anti-spam.minimum-biome-stay-seconds", 5L)) * 1000L;
            if (now - pendingSince.getOrDefault(uuid, now) < settle) continue;
            biomes.put(uuid, biome); pendingBiomes.remove(uuid); pendingSince.remove(uuid); announce(player, biome, false);
        }
    }
    private boolean enabled(Player player) {
        if (!config.getBoolean("settings.enabled", true) || !toggles.getOrDefault(player.getUniqueId(), true)) return false;
        List<String> worlds = config.contains("worlds") ? config.getStringList("worlds") : config.getStringList("enabled-worlds");
        return worlds.stream().anyMatch(world -> world.equals("*") || world.equalsIgnoreCase(player.getWorld().getName()));
    }
    private long cooldownSeconds() {
        return Math.max(0L, config.contains("cooldown-seconds") ? config.getLong("cooldown-seconds", 8L) : config.getLong("cooldown.duration", 8L));
    }
    private void announce(Player player, String biome, boolean bypassCooldown) {
        long now = System.currentTimeMillis(); long cooldown = cooldownSeconds() * 1000L;
        if (!bypassCooldown && now - announced.getOrDefault(player.getUniqueId(), 0L) < cooldown) return;
        announced.put(player.getUniqueId(), now);
        String name = configuredName(biome);
        boolean firstDiscovery = discovered.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(biome);
        if (!firstDiscovery && config.getBoolean("anti-spam.only-first-discovery", true)) return;
        String titleKey = firstDiscovery ? "display.new-biome-title" : "display.title";
        String title = config.getString(titleKey, firstDiscovery ? "&d&lBIOME BARU" : "&d&l{biome}");
        String subtitle = config.getString("display.subtitle", "&7Sekarang memasuki &f{biome}");
        startTyping(player,title,subtitle,config.getString("display.actionbar", "&8✦ &fMemasuki &d{biome}"),name);
        if (config.getBoolean("effects.particles-enabled", true)) player.getWorld().spawnParticle(parseParticle(config.getString("effects.particle", "END_ROD")), player.getLocation().add(0, 1, 0), Math.max(1, config.getInt("effects.count", 12)), .45, .65, .45, .01);
        // Kompatibel dengan konfigurasi BiomeAnnouncer lama: animation.sound.*.
        boolean soundEnabled = config.contains("effects.sound-enabled") ? config.getBoolean("effects.sound-enabled") : config.getBoolean("animation.sound.enabled", true);
        if (soundEnabled) {
            String sound = config.getString("effects.sound", "BLOCK_AMETHYST_BLOCK_CHIME");
            float volume = (float) config.getDouble("effects.sound-volume", config.getDouble("animation.sound.volume", .45D));
            float pitch = (float) config.getDouble("effects.sound-pitch", config.getDouble("animation.sound.pitch", 1.35D));
            player.playSound(player.getLocation(), parseSound(sound), Math.max(0.0F, volume), Math.max(0.5F, Math.min(2.0F, pitch)));
        }
    }
    private void startTyping(Player player,String titleTemplate,String subtitleTemplate,String actionTemplate,String biomeName) {
        BukkitTask old=animations.remove(player.getUniqueId()); if(old!=null)old.cancel();
        String plain=PlainTextComponentSerializer.plainText().serialize(component(biomeName)); if(plain.isBlank())plain=friendly(key(player));
        final String target=plain; final int[] length={0};
        long interval=Math.max(1L,config.getLong("display.typing.interval-ticks",2L));
        boolean typing=config.getBoolean("display.typing.enabled",true);
        Runnable frame=()->{
            int visible=typing?Math.min(target.length(),length[0]):target.length();
            String shown=target.substring(0,visible)+(typing&&visible<target.length()?"_":"");
            Component title=component(titleTemplate.replace("{biome}",shown));
            Component subtitle=component(subtitleTemplate.replace("{biome}",shown));
            player.showTitle(Title.title(title,subtitle,Title.Times.times(Duration.ZERO,Duration.ofMillis((visible<target.length()?interval+1:Math.max(20,config.getInt("display.stay-ticks",45)))*50L),Duration.ofMillis(Math.max(0,config.getInt("display.fade-out-ticks",12))*50L))));
            player.sendActionBar(component(actionTemplate.replace("{biome}",shown)));
        };
        if(!typing){frame.run();return;}
        BukkitTask animation=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!player.isOnline()){BukkitTask current=animations.remove(player.getUniqueId());if(current!=null)current.cancel();return;}
            frame.run();
            if(length[0]++>=target.length()){BukkitTask current=animations.remove(player.getUniqueId());if(current!=null)current.cancel();}
        },0L,interval);
        animations.put(player.getUniqueId(),animation);
    }
    private String key(Player player) { return player.getLocation().getBlock().getBiome().getKey().toString(); }
    private String configuredName(String biome) {
        String configured = config.getString("biome-names." + biomeConfigKey(biome));
        return configured == null || configured.isBlank() ? friendly(biome) : color(configured);
    }
    private String biomeConfigKey(String biome) { return biome.replace('/', '_').replace('.', '_').replace(':', '_'); }
    /** Terra may include a category path; the final segment is the displayed biome name. */
    private String friendly(String biome) {
        String raw = biome.replaceFirst("^[^:]+:", "");
        int slash = raw.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < raw.length()) raw = raw.substring(slash + 1);
        raw = raw.replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : raw.split(" ")) if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        return out.toString().trim();
    }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
    private Component component(String value) { return MINI.deserialize(legacyToMini(value==null?"":value)); }
    private String legacyToMini(String value) {
        String out=value;
        String codes="0123456789abcdefklmnor";
        String[] tags={"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white","obfuscated","bold","strikethrough","underlined","italic","reset"};
        for(int i=0;i<codes.length();i++){
            String tag="<"+tags[i]+">";
            char lower=codes.charAt(i),upper=Character.toUpperCase(lower);
            out=out.replace("&"+lower,tag).replace("&"+upper,tag).replace("§"+lower,tag).replace("§"+upper,tag);
        }
        return out;
    }
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
