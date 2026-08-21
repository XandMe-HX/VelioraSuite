package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public final class CombatGuardManager implements Listener, CommandExecutor, TabCompleter {
    private final VelioraSuite plugin;
    private final SecurityConfigManager config;
    private final File evidenceFile;
    private final Map<UUID, CombatState> states = new HashMap<>();
    private final Map<String, CombatCase> cases = new LinkedHashMap<>();
    private final Map<UUID, Deque<Long>> clicks = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> recentTargets = new HashMap<>();
    private final Map<UUID, Deque<String>> chatContext = new HashMap<>();
    private int nextCaseId = 1;

    public CombatGuardManager(VelioraSuite plugin, SecurityConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.evidenceFile = new File(plugin.getDataFolder(), "data/combatguard-evidence.yml");
    }

    public void load() {
        states.clear();
        cases.clear();
        if (!evidenceFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(evidenceFile);
        nextCaseId = Math.max(1, yaml.getInt("next-case-id", 1));
        for (String id : yaml.getConfigurationSection("cases") == null
                ? List.<String>of() : yaml.getConfigurationSection("cases").getKeys(false)) {
            String root = "cases." + id + ".";
            try {
                UUID uuid = UUID.fromString(yaml.getString(root + "uuid", ""));
                cases.put(id, new CombatCase(id, uuid, yaml.getString(root + "player", "unknown"),
                        yaml.getString(root + "ip", ""), yaml.getString(root + "status", "PENDING"),
                        yaml.getStringList(root + "evidence"), yaml.getLong(root + "created", 0L)));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void reload() {
        save();
        load();
    }

    public void shutdown() {
        save();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!enabled() || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) return;
        if (exempt(player, target)) return;

        long now = System.currentTimeMillis();
        CombatState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new CombatState(player.getName()));
        state.decay(now, config.config().getDouble("settings.combat-guard.score-decay-per-second", 4.0D));
        state.stage = Math.min(state.stage, stage(state.score));
        state.name = player.getName();

        Deque<Long> hitTimes = clicks.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        hitTimes.addLast(now);
        while (!hitTimes.isEmpty() && now - hitTimes.peekFirst() > 1000L) hitTimes.removeFirst();

        Map<UUID, Long> targets = recentTargets.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        targets.entrySet().removeIf(entry -> now - entry.getValue() > 350L);
        targets.put(target.getUniqueId(), now);

        boolean bedrock = isBedrock(player);
        double reach = reach(player, target);
        double allowedReach = maxReach(player);
        double hardExtra = config.config().getDouble(
                bedrock ? "settings.combat-guard.bedrock-hard-reach-extra" : "settings.combat-guard.java-hard-reach-extra",
                bedrock ? 0.45D : 0.35D);
        boolean softReach = reach > allowedReach;
        boolean hardReach = reach > allowedReach + Math.max(0.15D, hardExtra);
        boolean noSight = !player.hasLineOfSight(target) && reach > 2.2D;
        double facing = facing(player, target);
        double minimumFacing = config.config().getDouble(
                bedrock ? "settings.combat-guard.bedrock-minimum-facing-dot" : "settings.combat-guard.minimum-facing-dot",
                bedrock ? -0.25D : 0.05D);
        boolean impossibleFacing = reach > 1.5D && facing < minimumFacing;
        boolean multiAura = targets.size() >= config.config().getInt("settings.combat-guard.multi-target-count", 3);
        boolean autoClick = hitTimes.size() > config.config().getInt("settings.combat-guard.maximum-cps", 22);
        boolean stableTps = Bukkit.getTPS()[0] >= config.config().getDouble("settings.combat-guard.minimum-tps-for-geometry", 18.0D);

        int eventScore = 0;
        int strongSignals = 0;
        List<String> signals = new ArrayList<>();
        if (stableTps && softReach) {
            eventScore += hardReach ? 35 : 12;
            if (hardReach) strongSignals++;
            signals.add(String.format(Locale.US, "reach %.2f/%.2f%s", reach, allowedReach, hardReach ? " HARD" : ""));
        }
        if (noSight) {
            eventScore += 35;
            strongSignals++;
            signals.add("hit-through-wall");
        }
        if (stableTps && impossibleFacing) {
            eventScore += bedrock ? 6 : 12;
            signals.add(String.format(Locale.US, "facing %.2f", facing));
        }
        if (multiAura) {
            eventScore += 32;
            strongSignals++;
            signals.add("multi-target " + targets.size());
        }
        if (autoClick) {
            eventScore += 10;
            signals.add("cps " + hitTimes.size());
        }

        // Bedrock touch controls can report wider aim and reach. A single geometric
        // signal is recorded gently; destructive actions require two independent signals.
        int requiredStrongSignals = bedrock
                ? config.config().getInt("settings.combat-guard.bedrock-strong-signals-required", 2)
                : 1;
        if (bedrock && strongSignals < requiredStrongSignals) eventScore = Math.min(eventScore, 8);
        if (eventScore < config.config().getInt("settings.combat-guard.minimum-event-score", 8)) return;

        state.score = Math.min(300.0D, state.score + eventScore);
        String evidence = evidence(player, target, reach, facing, targets.size(), hitTimes.size(), signals);
        state.addEvidence(evidence, config.config().getInt("settings.combat-guard.max-evidence-per-player", 20));

        int newStage = stage(state.score);
        boolean actionableHit = (!bedrock || strongSignals >= requiredStrongSignals)
                && (hardReach || noSight || multiAura);
        if (newStage <= state.stage) {
            if (state.stage >= 2 && actionableHit) event.setCancelled(true);
            return;
        }

        state.stage = newStage;
        if (newStage >= 2 && actionableHit) event.setCancelled(true);
        if (state.alertsSent < 3) {
            state.alertsSent++;
            alertStaff(player, state, evidence);
        }

        if (newStage >= 3 && !state.caseCreated) {
            state.caseCreated = true;
            String id = "CASE-" + String.format(Locale.ROOT, "%04d", nextCaseId++);
            String ip = player.getAddress() == null || player.getAddress().getAddress() == null
                    ? "" : player.getAddress().getAddress().getHostAddress();
            CombatCase combatCase = new CombatCase(id, player.getUniqueId(), player.getName(), ip,
                    "PENDING", new ArrayList<>(state.evidence), now);
            cases.put(id, combatCase);
            Bukkit.broadcast("§8[§4CombatGuard§8] §c" + id + " menunggu konfirmasi Owner/Admin/Guard.", "veliorasuite.security.alerts");
            player.kickPlayer("§cCombatGuard: beberapa sinyal serangan kuat terdeteksi.\n§7Kasus " + id + " sedang ditinjau staff.");
        }
        save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clicks.remove(event.getPlayer().getUniqueId());
        recentTargets.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.isBlank()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            String clean = message.length() > 160 ? message.substring(0, 160) : message;
            rememberChat(event.getPlayer().getUniqueId(), event.getPlayer().getName() + ": " + clean);
            String lower = clean.toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(event.getPlayer().getUniqueId())) continue;
                if (lower.contains(online.getName().toLowerCase(Locale.ROOT))) {
                    rememberChat(online.getUniqueId(), event.getPlayer().getName() + ": " + clean);
                }
            }
        });
    }

    private void rememberChat(UUID uuid, String line) {
        Deque<String> context = chatContext.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        context.addLast(time() + " CHAT " + line);
        while (context.size() > 5) context.removeFirst();
    }

    private boolean exempt(Player player, Entity target) {
        if (player.hasPermission("veliorasuite.security.bypass")) return true;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return true;
        if (target.getScoreboardTags().contains("velioraboss_boss")
                || target.getScoreboardTags().contains("velioraboss_minion")
                || target.getScoreboardTags().contains("veliorapet")) return true;
        return target.getType().name().contains("ARMOR_STAND");
    }

    private double maxReach(Player player) {
        double base = isBedrock(player)
                ? config.config().getDouble("settings.combat-guard.bedrock-max-reach", 3.85D)
                : config.config().getDouble("settings.combat-guard.java-max-reach", 3.45D);
        double pingCompensation = Math.min(0.45D, Math.max(0, player.getPing()) * 0.0015D);
        return base + pingCompensation;
    }

    private boolean isBedrock(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
            if (result instanceof Boolean value) return value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Fallback below keeps compatibility with older Floodgate installations.
        }
        String name = player.getName();
        return name.startsWith("_") || name.startsWith(".") || name.startsWith("*");
    }

    private double reach(Player player, LivingEntity target) {
        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        return Math.sqrt(square(eye.getX() - x) + square(eye.getY() - y) + square(eye.getZ() - z));
    }

    private double facing(Player player, LivingEntity target) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector targetDirection = target.getBoundingBox().getCenter().subtract(player.getEyeLocation().toVector());
        if (targetDirection.lengthSquared() < 0.0001D) return 1.0D;
        return direction.dot(targetDirection.normalize());
    }

    private int stage(double score) {
        if (score >= config.config().getDouble("settings.combat-guard.stage-3-score", 230.0D)) return 3;
        if (score >= config.config().getDouble("settings.combat-guard.stage-2-score", 140.0D)) return 2;
        if (score >= config.config().getDouble("settings.combat-guard.stage-1-score", 70.0D)) return 1;
        return 0;
    }

    private String evidence(Player player, LivingEntity target, double reach, double facing,
                            int targetCount, int cps, List<String> signals) {
        Location location = player.getLocation();
        return time() + " | " + player.getWorld().getName() + " "
                + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ()
                + " | target=" + target.getType() + " | reach=" + String.format(Locale.US, "%.2f", reach)
                + " | facing=" + String.format(Locale.US, "%.2f", facing)
                + " | targets=" + targetCount + " | cps=" + cps
                + " | ping=" + player.getPing() + "ms"
                + " | tps=" + String.format(Locale.US, "%.1f", Bukkit.getTPS()[0])
                + " | " + String.join(",", signals);
    }

    private void alertStaff(Player player, CombatState state, String evidence) {
        String message = "§8[§4CombatGuard " + state.stage + "/3§8] §f" + player.getName()
                + " §cdicurigai KillAura §7| skor §f" + (int) state.score + " §7| " + evidence;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("veliorasuite.security.alerts")
                    || online.hasPermission("veliorasuite.security.admin")
                    || online.hasPermission("veliorasuite.security.owner")) online.sendMessage(message);
        }
        plugin.getLogger().warning(org.bukkit.ChatColor.stripColor(message));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!hasStaff(sender)) {
            sender.sendMessage("§8[§4CombatGuard§8] §cKamu tidak memiliki izin.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status") || sub.equals("logs")) {
            if (args.length < 2) { sender.sendMessage("§cGunakan /vguard " + sub + " <player>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            UUID uuid = target == null ? findUuid(args[1]) : target.getUniqueId();
            CombatState state = uuid == null ? null : states.get(uuid);
            if (state == null) { sender.sendMessage("§7Tidak ada skor aktif untuk " + args[1] + "."); return true; }
            sender.sendMessage("§8[§4CombatGuard§8] §f" + state.name + " §7skor=§f" + (int) state.score + " §7alert=§f" + state.stage + "/3");
            if (sub.equals("logs")) {
                state.evidence.forEach(line -> sender.sendMessage("§8- §7" + line));
                Deque<String> chat = chatContext.get(uuid);
                if (chat != null && !chat.isEmpty()) {
                    sender.sendMessage("§eKonteks chat (bukan bukti otomatis):");
                    chat.forEach(line -> sender.sendMessage("§8- §7" + line));
                }
            }
            return true;
        }
        if (sub.equals("case")) {
            if (args.length < 2) { sender.sendMessage("§cGunakan /vguard case <id>"); return true; }
            CombatCase value = cases.get(args[1].toUpperCase(Locale.ROOT));
            if (value == null) { sender.sendMessage("§cCase tidak ditemukan."); return true; }
            sender.sendMessage("§8[§4CombatGuard§8] §f" + value.id + " §7player=§f" + value.player + " §7status=§f" + value.status);
            value.evidence.forEach(line -> sender.sendMessage("§8- §7" + line));
            return true;
        }
        if (sub.equals("clear")) {
            if (args.length < 2) { sender.sendMessage("§cGunakan /vguard clear <player> <alasan>"); return true; }
            UUID uuid = findUuid(args[1]);
            if (uuid != null) states.remove(uuid);
            markPlayerCases(args[1], "CLEARED by " + sender.getName() + ": " + join(args, 2));
            save();
            sender.sendMessage("§aSkor aktif dibersihkan; riwayat case ditandai, tidak dihapus.");
            return true;
        }
        if (sub.equals("deny")) {
            if (args.length < 2) { sender.sendMessage("§cGunakan /vguard deny <case> <alasan>"); return true; }
            CombatCase value = cases.get(args[1].toUpperCase(Locale.ROOT));
            if (value == null) { sender.sendMessage("§cCase tidak ditemukan."); return true; }
            value.status = "FALSE_POSITIVE by " + sender.getName() + ": " + join(args, 2);
            states.remove(value.uuid);
            save();
            sender.sendMessage("§aCase ditandai sebagai salah deteksi.");
            return true;
        }
        if (sub.equals("confirm")) {
            if (args.length < 3) { sender.sendMessage("§cGunakan /vguard confirm <case> <ipban|ban7d>"); return true; }
            CombatCase value = cases.get(args[1].toUpperCase(Locale.ROOT));
            if (value == null || !value.status.startsWith("PENDING")) { sender.sendMessage("§cCase tidak tersedia atau sudah diproses."); return true; }
            String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("ipban")) {
                if (!sender.hasPermission("veliorasuite.security.owner")) {
                    sender.sendMessage("§cIP-ban hanya dapat dikonfirmasi Owner.");
                    return true;
                }
                if (value.ip.isBlank()) { sender.sendMessage("§cAlamat IP tidak tersedia."); return true; }
                Bukkit.getBanList(BanList.Type.IP).addBan(value.ip, "CombatGuard " + value.id, null, sender.getName());
                Player online = Bukkit.getPlayer(value.uuid);
                if (online != null) online.kickPlayer("§cIP kamu diblokir setelah review " + value.id);
                value.status = "IP_BANNED by " + sender.getName();
            } else if (action.equals("ban7d")) {
                Date expires = new Date(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L);
                Bukkit.getBanList(BanList.Type.NAME).addBan(value.player, "CombatGuard " + value.id, expires, sender.getName());
                Player online = Bukkit.getPlayer(value.uuid);
                if (online != null) online.kickPlayer("§cKamu diban 7 hari setelah review " + value.id);
                value.status = "BANNED_7D by " + sender.getName();
            } else {
                sender.sendMessage("§cGunakan ipban atau ban7d.");
                return true;
            }
            save();
            sender.sendMessage("§aTindakan dikonfirmasi untuk " + value.id + ".");
            return true;
        }
        help(sender);
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§8§m---------- §4§lCombatGuard §8§m----------");
        sender.sendMessage("§f/vguard status <player>");
        sender.sendMessage("§f/vguard logs <player>");
        sender.sendMessage("§f/vguard case <id>");
        sender.sendMessage("§f/vguard confirm <id> <ipban|ban7d>");
        sender.sendMessage("§f/vguard deny <id> <alasan>");
        sender.sendMessage("§f/vguard clear <player> <alasan>");
    }

    private boolean hasStaff(CommandSender sender) {
        return sender.hasPermission("veliorasuite.security.alerts")
                || sender.hasPermission("veliorasuite.security.admin")
                || sender.hasPermission("veliorasuite.security.owner");
    }

    private UUID findUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (Map.Entry<UUID, CombatState> entry : states.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) return entry.getKey();
        }
        for (CombatCase value : cases.values()) if (value.player.equalsIgnoreCase(name)) return value.uuid;
        return null;
    }

    private void markPlayerCases(String player, String status) {
        for (CombatCase value : cases.values()) if (value.player.equalsIgnoreCase(player)) value.status = status;
    }

    private boolean enabled() {
        return config.config().getBoolean("settings.combat-guard.enabled", true);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-case-id", nextCaseId);
        for (CombatCase value : cases.values()) {
            String root = "cases." + value.id + ".";
            yaml.set(root + "uuid", value.uuid.toString());
            yaml.set(root + "player", value.player);
            yaml.set(root + "ip", value.ip);
            yaml.set(root + "status", value.status);
            yaml.set(root + "evidence", value.evidence);
            yaml.set(root + "created", value.created);
        }
        try {
            File parent = evidenceFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yaml.save(evidenceFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("CombatGuard: gagal menyimpan bukti: " + exception.getMessage());
        }
    }

    private String time() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'WIB'");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        return format.format(new Date());
    }

    private String join(String[] args, int start) {
        if (start >= args.length) return "tanpa alasan";
        StringBuilder value = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (!value.isEmpty()) value.append(' ');
            value.append(args[index]);
        }
        return value.toString();
    }

    private double square(double value) { return value * value; }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("status", "logs", "case", "confirm", "deny", "clear").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("confirm")) return List.of("ipban", "ban7d");
        return List.of();
    }

    private static final class CombatState {
        private String name;
        private double score;
        private int stage;
        private int alertsSent;
        private boolean caseCreated;
        private long updatedAt = System.currentTimeMillis();
        private final List<String> evidence = new ArrayList<>();

        private CombatState(String name) { this.name = name; }

        private void decay(long now, double perSecond) {
            double seconds = Math.max(0.0D, (now - updatedAt) / 1000.0D);
            score = Math.max(0.0D, score - seconds * Math.max(0.0D, perSecond));
            updatedAt = now;
        }

        private void addEvidence(String line, int maximum) {
            evidence.add(line);
            while (evidence.size() > Math.max(3, maximum)) evidence.removeFirst();
        }
    }

    private static final class CombatCase {
        private final String id;
        private final UUID uuid;
        private final String player;
        private final String ip;
        private String status;
        private final List<String> evidence;
        private final long created;

        private CombatCase(String id, UUID uuid, String player, String ip, String status,
                           List<String> evidence, long created) {
            this.id = id;
            this.uuid = uuid;
            this.player = player;
            this.ip = ip;
            this.status = status;
            this.evidence = evidence;
            this.created = created;
        }
    }
}
