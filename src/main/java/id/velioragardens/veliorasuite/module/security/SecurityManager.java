package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.security.model.BanSource;
import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SecurityManager {

    private final VelioraSuite plugin;
    private final SecurityConfigManager configManager;
    private final SecurityRiskManager riskManager;
    private final SecurityAlertManager alertManager;
    private final SecurityJoinProtectionManager joinProtectionManager;
    private final SecurityCommandProtectionManager commandProtectionManager;
    private final SecurityTabProtectionManager tabProtectionManager;
    private final SecurityAltGuard altGuard;
    private final SpawnerGuardManager spawnerGuardManager;
    private final AntiDupeManager antiDupeManager;
    private final VelioraBanManager banManager;
    private final File xrayStateFile;

    private final Map<UUID, List<OreRecord>> oreRecords = new HashMap<>();
    private final Map<UUID, String> oreNames = new HashMap<>();
    private final Set<String> placedOre = new HashSet<>();
    private final Set<String> exemptOreNames = new HashSet<>();
    private final List<OreReport> oreAlerts = new ArrayList<>();
    private final Map<String, Long> alertCooldown = new HashMap<>();
    private final Map<UUID, Integer> xrayWarnings = new HashMap<>();
    private final Map<UUID, Long> xrayLastAction = new HashMap<>();
    private final Map<UUID, Long> pendingXrayBans = new HashMap<>();

    public SecurityManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new SecurityConfigManager(plugin);
        this.riskManager = new SecurityRiskManager(plugin, configManager);
        this.alertManager = new SecurityAlertManager(plugin, configManager);
        this.joinProtectionManager = new SecurityJoinProtectionManager(configManager, riskManager);
        this.commandProtectionManager = new SecurityCommandProtectionManager(configManager, riskManager);
        this.tabProtectionManager = new SecurityTabProtectionManager(configManager, commandProtectionManager);
        this.altGuard = new SecurityAltGuard(plugin, configManager);
        this.spawnerGuardManager = new SpawnerGuardManager(plugin, configManager);
        this.antiDupeManager = new AntiDupeManager(plugin, configManager);
        this.banManager = new VelioraBanManager(plugin);
        this.xrayStateFile = new File(plugin.getDataFolder(), "data/xray-enforcement.yml");
    }

    public void load() {
        configManager.load();
        altGuard.load();
        spawnerGuardManager.load();
        banManager.load();
        loadXrayState();
        plugin.getLogger().info("VelioraSecurity loaded.");
    }

    public void reload() {
        configManager.load();
        altGuard.load();
        spawnerGuardManager.load();
        banManager.load();
        loadXrayState();
        alertManager.clearCooldowns();
        joinProtectionManager.clear();
    }

    public SecurityConfigManager getConfigManager() { return configManager; }
    public SecurityTabProtectionManager getTabProtectionManager() { return tabProtectionManager; }

    public boolean handleSpawnerPlace(Player player, Block block, org.bukkit.inventory.ItemStack item) {
        return spawnerGuardManager.handlePlace(player, block, item);
    }

    public void handleSpawnerBreak(Block block) { spawnerGuardManager.handleBreak(block); }
    public void handleSpawnerRemoved(Block block) { spawnerGuardManager.handleBreak(block); }
    public void rollbackSpawnerPlace(Player player, Block block) { spawnerGuardManager.rollbackPlace(player, block); }
    public void scheduleAntiDupeScan(Player player, long delayTicks) { antiDupeManager.scheduleScan(player, delayTicks); }

    public SecurityDecision checkJoin(Player player) {
        oreNames.put(player.getUniqueId(), player.getName());
        SecurityDecision decision = joinProtectionManager.check(player);
        alertIfNeeded(decision);
        return decision;
    }

    public String checkAltJoin(Player player) { return altGuard.onJoin(player); }
    public void handleAltQuit(Player player) { altGuard.onQuit(player); }
    public boolean checkAltPay(Player player, String commandLine) { return altGuard.onPayCommand(player, commandLine); }
    public void sendAltHelp(CommandSender sender) { altGuard.sendHelp(sender); }
    public void sendAltCheck(CommandSender sender, String name) { altGuard.sendCheck(sender, name); }
    public void sendAltList(CommandSender sender) { altGuard.sendList(sender); }
    public void sendAltAlerts(CommandSender sender) { altGuard.sendAlerts(sender); }
    public void altTrust(CommandSender sender, String name, boolean value) { altGuard.trust(sender, name, value); }

    public SecurityDecision checkCommand(Player player, String commandLine) {
        SecurityDecision decision = commandProtectionManager.check(player, commandLine);
        alertIfNeeded(decision);
        return decision;
    }

    public void trackOrePlace(Player player, Block block) {
        if (block == null || !isOre(block.getType())) return;
        placedOre.add(locationKey(block.getLocation()));
        if (placedOre.size() > 10000) placedOre.clear();
    }

    public void trackOreBreak(Player player, Block block) {
        if (player == null || block == null || !isOre(block.getType())) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (configManager.hasBypass(player) || exemptOreNames.contains(player.getName().toLowerCase(Locale.ROOT))) return;
        if (placedOre.remove(locationKey(block.getLocation()))) return;

        UUID uuid = player.getUniqueId();
        oreNames.put(uuid, player.getName());
        oreRecords.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(new OreRecord(System.currentTimeMillis(), block.getType().name()));
        trim(uuid);
        OreReport fiveMinuteReport = report(uuid, 5);
        OreReport report = strongest(fiveMinuteReport, report(uuid, 15), report(uuid, 60));
        if (!report.level().equals("NORMAL")) addOreAlert(player, report);
        if (fiveMinuteReport.level().equals("EXTREME")) handleExtremeXray(player, fiveMinuteReport);
    }

    public void scheduleOreDigest(Player player, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player == null || !player.isOnline()) return;
            if (!configManager.hasAlerts(player) && !configManager.hasAdmin(player)) return;
            if (!oreAlerts.isEmpty()) player.sendMessage(color("&8[&cVelioraOreWatch&8] &eAda &f" + oreAlerts.size() + " &ereport mining. Gunakan &f/vxray alerts&e."));
        }, delayTicks);
    }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.messageList("help", List.of(
                "&8&m--------------------------------",
                "&c&lVelioraSecurity",
                "&f/vsecurity status &7- Cek status security.",
                "&f/vsecurity alerts &7- Lihat alert terbaru.",
                "&f/vsecurity reload &7- Reload config.",
                "&f/valt help &7- Cek akun ganda dan abuse ekonomi.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void sendOreHelp(CommandSender sender) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        sendLines(sender, List.of(
                "&8&m--------------------------------",
                "&c&lVelioraOreWatch",
                "&f/vxray status &7- status monitor ore.",
                "&f/vxray check <player> &7- cek player.",
                "&f/vxray logs <player> &7- log ore terakhir.",
                "&f/vxray suspects &7- list player mencurigakan.",
                "&f/vxray alerts &7- alert terbaru.",
                "&f/vxray allreport &7- semua report tidak normal.",
                "&f/vxray reset <player> &7- reset data player.",
                "&f/vxray exempt <player> &7- bypass player.",
                "&f/vxray unexempt <player> &7- hapus bypass.",
                "&8&m--------------------------------"
        ), Map.of());
    }

    public void sendOreStatus(CommandSender sender) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        sendLines(sender, List.of(
                "&8&m--------------------------------",
                "&c&lVelioraOreWatch Status",
                "&7Tracked Players: &f" + oreRecords.size(),
                "&7Alerts: &f" + oreAlerts.size(),
                "&7Placed Ore Cache: &f" + placedOre.size(),
                "&7Mode: &f2 peringatan, ban 15 hari wajib konfirmasi owner",
                "&7Pending Confirmation: &f" + pendingXrayBans.size(),
                "&8&m--------------------------------"
        ), Map.of());
    }

    public void sendOreCheck(CommandSender sender, String name) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        UUID uuid = uuidByName(name);
        if (uuid == null) { sender.sendMessage(color("&8[&cVelioraOreWatch&8] &cData player tidak ditemukan.")); return; }
        sendOreReport(sender, report(uuid, 5));
        sendOreReport(sender, report(uuid, 15));
        sendOreReport(sender, report(uuid, 60));
    }

    public void sendOreLogs(CommandSender sender, String name) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        UUID uuid = uuidByName(name);
        if (uuid == null) { sender.sendMessage(color("&8[&cVelioraOreWatch&8] &cData player tidak ditemukan.")); return; }
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lOre Logs &8- &f" + oreNames.getOrDefault(uuid, name)));
        oreRecords.getOrDefault(uuid, List.of()).stream().sorted(Comparator.comparingLong(OreRecord::time).reversed()).limit(12).forEach(record -> sender.sendMessage(color("&8- &f" + record.ore())));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    public void sendOreAlerts(CommandSender sender) {
        if (!configManager.hasAlerts(sender)) { sendNoPermission(sender); return; }
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lVelioraOreWatch Alerts"));
        if (oreAlerts.isEmpty()) sender.sendMessage(color("&7Belum ada alert."));
        oreAlerts.stream().sorted(Comparator.comparingInt(OreReport::score).reversed()).limit(15).forEach(report -> sendOreReport(sender, report));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    public void sendOreSuspects(CommandSender sender) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        List<OreReport> reports = new ArrayList<>();
        for (UUID uuid : oreRecords.keySet()) {
            OreReport report = strongest(report(uuid, 5), report(uuid, 15), report(uuid, 60));
            if (!report.level().equals("NORMAL")) reports.add(report);
        }
        reports.sort(Comparator.comparingInt(OreReport::score).reversed());
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lVelioraOreWatch Suspects"));
        if (reports.isEmpty()) sender.sendMessage(color("&7Tidak ada data mencurigakan."));
        reports.stream().limit(15).forEach(report -> sendOreReport(sender, report));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    public void sendOreAllReport(CommandSender sender) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lVelioraOreWatch All Report"));
        int count = 0;
        for (UUID uuid : oreRecords.keySet()) {
            OreReport report = strongest(report(uuid, 5), report(uuid, 15), report(uuid, 60));
            if (report.level().equals("NORMAL")) continue;
            sendOreReport(sender, report);
            count++;
        }
        if (count == 0) sender.sendMessage(color("&7Tidak ada report yang tidak normal."));
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    public void resetOre(CommandSender sender, String name) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        UUID uuid = uuidByName(name);
        if (uuid != null) {
            oreRecords.remove(uuid);
            xrayWarnings.remove(uuid);
            xrayLastAction.remove(uuid);
            pendingXrayBans.remove(uuid);
            saveXrayState();
        }
        oreAlerts.removeIf(report -> report.name().equalsIgnoreCase(name));
        sender.sendMessage(color("&8[&cVelioraOreWatch&8] &aData &f" + name + " &adireset."));
    }

    public void exemptOre(CommandSender sender, String name, boolean value) {
        if (!configManager.hasAdmin(sender)) { sendNoPermission(sender); return; }
        if (value) exemptOreNames.add(name.toLowerCase(Locale.ROOT)); else exemptOreNames.remove(name.toLowerCase(Locale.ROOT));
        sender.sendMessage(color("&8[&cVelioraOreWatch&8] &aBypass &f" + name + " &a= &f" + value));
    }

    public void confirmXrayBan(CommandSender sender, String name) {
        if (!configManager.hasOwner(sender)) {
            sender.sendMessage(color("&8[&cVelioraOreWatch&8] &cHanya owner/OP yang dapat mengonfirmasi ban Xray."));
            return;
        }
        UUID uuid = uuidByName(name);
        if (uuid == null || !pendingXrayBans.containsKey(uuid)) {
            sender.sendMessage(color("&8[&cVelioraOreWatch&8] &cTidak ada ban Xray yang menunggu konfirmasi untuk &f" + name + "&c."));
            return;
        }
        long expiresAt = pendingXrayBans.get(uuid);
        if (System.currentTimeMillis() > expiresAt) {
            pendingXrayBans.remove(uuid);
            xrayWarnings.put(uuid, 1);
            saveXrayState();
            sender.sendMessage(color("&8[&cVelioraOreWatch&8] &eKonfirmasi sudah kedaluwarsa. Player kembali ke peringatan pertama."));
            return;
        }
        String playerName = oreNames.getOrDefault(uuid, name);
        int days = configManager.getXrayBanDays();
        banManager.banPlayerTemporarily(uuid, playerName,
                "Xray terdeteksi 2 kali dan dikonfirmasi Owner Veliora Gardens", BanSource.AUTO_XRAY,
                days * 24L * 60L * 60L * 1000L);
        pendingXrayBans.remove(uuid);
        xrayWarnings.remove(uuid);
        xrayLastAction.remove(uuid);
        saveXrayState();
        sender.sendMessage(color("&8[&cVelioraOreWatch&8] &aBan Xray &f" + days + " hari &auntuk &f" + playerName + " &aberhasil dikonfirmasi."));
    }

    public void denyXrayBan(CommandSender sender, String name) {
        if (!configManager.hasOwner(sender)) {
            sender.sendMessage(color("&8[&cVelioraOreWatch&8] &cHanya owner/OP yang dapat menolak ban Xray."));
            return;
        }
        UUID uuid = uuidByName(name);
        if (uuid == null || pendingXrayBans.remove(uuid) == null) {
            sender.sendMessage(color("&8[&cVelioraOreWatch&8] &cTidak ada konfirmasi aktif untuk &f" + name + "&c."));
            return;
        }
        xrayWarnings.put(uuid, 1);
        xrayLastAction.put(uuid, System.currentTimeMillis());
        saveXrayState();
        sender.sendMessage(color("&8[&cVelioraOreWatch&8] &aBan ditolak. &f" + name + " &akembali ke status peringatan pertama."));
    }

    public void sendStatus(CommandSender sender) {
        sendLines(sender, configManager.messageList("status", List.of(
                "&8&m--------------------------------",
                "&c&lVelioraSecurity Status",
                "&7Enabled: &f%enabled%",
                "&7Join Protection: &f%join_protection%",
                "&7Name Protection: &f%name_protection%",
                "&7Command Protection: &f%command_protection%",
                "&7Tab Protection: &f%tab_protection%",
                "&7Blocked Commands: &f%blocked_commands%",
                "&7Recent Alerts: &f%recent_alerts%",
                "&8&m--------------------------------"
        )), statusPlaceholders());
    }

    public void sendAlerts(CommandSender sender) {
        alertManager.sendRecent(sender);
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraSecurity berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
    }

    public String denyMessage(SecurityDecision decision) {
        return configManager.color(configManager.message(decision.messageKey(), decision.fallbackMessage()));
    }

    private void addOreAlert(Player player, OreReport report) {
        String key = player.getUniqueId() + ":" + report.window() + ":" + report.level();
        long now = System.currentTimeMillis();
        if (now - alertCooldown.getOrDefault(key, 0L) < 30000L) return;
        alertCooldown.put(key, now);
        oreAlerts.add(report);
        while (oreAlerts.size() > 50) oreAlerts.remove(0);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!configManager.hasAlerts(online) && !configManager.hasAdmin(online)) continue;
            online.sendMessage(color("&8[&cVelioraOreWatch&8] &cSuspicious Mining"));
            sendOreReport(online, report);
        }
    }

    private void handleExtremeXray(Player player, OreReport report) {
        if (!configManager.isXrayEnforcementEnabled() || configManager.hasBypass(player)) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long pendingUntil = pendingXrayBans.get(uuid);
        if (pendingUntil != null) {
            if (now <= pendingUntil) return;
            pendingXrayBans.remove(uuid);
            xrayWarnings.put(uuid, 1);
        }
        long cooldown = configManager.getXrayStrikeCooldownMinutes() * 60_000L;
        if (now - xrayLastAction.getOrDefault(uuid, 0L) < cooldown) return;
        int warning = Math.min(2, xrayWarnings.getOrDefault(uuid, 0) + 1);
        xrayWarnings.put(uuid, warning);
        xrayLastAction.put(uuid, now);
        oreNames.put(uuid, player.getName());

        if (warning == 1) {
            notifyXrayOwners(player.getName(), "&ePeringatan Xray 1/2 &7- player diperingatkan dan dikeluarkan.", report);
            saveXrayState();
            player.kickPlayer(color("&cPeringatan Xray 1/2\n&7Pola mining kamu melewati batas EXTREME.\n&7Hentikan Xray. Pelanggaran berikutnya menunggu konfirmasi owner untuk ban 15 hari."));
            return;
        }

        long confirmationUntil = now + configManager.getXrayConfirmationMinutes() * 60_000L;
        pendingXrayBans.put(uuid, confirmationUntil);
        saveXrayState();
        notifyXrayOwners(player.getName(), "&cPeringatan Xray 2/2 &7- menunggu konfirmasi owner: &f/vxray confirm "
                + player.getName() + " &7atau &f/vxray deny " + player.getName(), report);
        player.kickPlayer(color("&cPeringatan Xray 2/2\n&7Kasus dikirim ke owner untuk konfirmasi ban 15 hari."));
    }

    private void notifyXrayOwners(String playerName, String action, OreReport report) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!configManager.hasAlerts(online) && !configManager.hasAdmin(online)) continue;
            online.sendMessage(color("&8[&cVelioraOreWatch&8] &f" + playerName + " &8| " + action));
            sendOreReport(online, report);
        }
        plugin.getLogger().warning("VelioraOreWatch enforcement: " + playerName + " - " + org.bukkit.ChatColor.stripColor(color(action)));
    }

    private void sendOreReport(CommandSender sender, OreReport report) {
        sender.sendMessage(color("&8[&cVelioraOreWatch&8] &f" + report.name() + " &7UUID &f" + report.uuid()));
        String action = report.level().equals("EXTREME") ? "Peringatan/Kick + Konfirmasi Owner" : "Alert admin";
        sender.sendMessage(color("&7Window: &f" + report.window() + " menit &8| &7Score: &e" + report.level() + " &8| &7Action: &f" + action));
        sender.sendMessage(color("&7Diamond Ore: &f" + report.diamond() + " &7Ancient Debris: &f" + report.debris() + " &7Emerald Ore: &f" + report.emerald()));
        sender.sendMessage(color("&7Gold Ore: &f" + report.gold() + " &7Iron Ore: &f" + report.iron()));
    }

    private OreReport report(UUID uuid, int minutes) {
        long since = System.currentTimeMillis() - minutes * 60000L;
        int diamond = 0, debris = 0, gold = 0, iron = 0, emerald = 0;
        for (OreRecord record : oreRecords.getOrDefault(uuid, List.of())) {
            if (record.time() < since) continue;
            String ore = record.ore();
            if (ore.contains("DIAMOND_ORE")) diamond++;
            else if (ore.equals("ANCIENT_DEBRIS")) debris++;
            else if (ore.contains("GOLD_ORE")) gold++;
            else if (ore.contains("IRON_ORE")) iron++;
            else if (ore.contains("EMERALD_ORE")) emerald++;
        }
        String level = level(minutes, diamond, debris, gold, iron, emerald);
        int score = diamond * 3 + debris * 10 + emerald * 4 + gold + iron / 2;
        return new OreReport(uuid, oreNames.getOrDefault(uuid, "unknown"), minutes, diamond, debris, gold, iron, emerald, level, score);
    }

    private String level(int minutes, int diamond, int debris, int gold, int iron, int emerald) {
        double factor = minutes / 5.0D;
        if (debris >= 12 * factor || diamond >= 55 * factor || emerald >= 25 * factor || gold >= 110 * factor || iron >= 220 * factor) return "EXTREME";
        if (debris >= 9 * factor || diamond >= 40 * factor || emerald >= 15 * factor || gold >= 75 * factor || iron >= 150 * factor) return "HIGH";
        if (debris >= 5 * factor || diamond >= 25 * factor || emerald >= 8 * factor || gold >= 40 * factor || iron >= 80 * factor) return "ALERT";
        return "NORMAL";
    }

    private OreReport strongest(OreReport a, OreReport b, OreReport c) {
        return List.of(a, b, c).stream().max(Comparator.comparingInt(report -> weight(report.level()) * 10000 + report.score())).orElse(a);
    }

    private int weight(String level) {
        return switch (level) { case "EXTREME" -> 3; case "HIGH" -> 2; case "ALERT" -> 1; default -> 0; };
    }

    private UUID uuidByName(String name) {
        for (Map.Entry<UUID, String> entry : oreNames.entrySet()) if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        Player player = Bukkit.getPlayerExact(name);
        return player == null ? null : player.getUniqueId();
    }

    private void trim(UUID uuid) {
        long since = System.currentTimeMillis() - 3600000L;
        oreRecords.getOrDefault(uuid, new ArrayList<>()).removeIf(record -> record.time() < since);
    }

    private void loadXrayState() {
        xrayWarnings.clear();
        xrayLastAction.clear();
        pendingXrayBans.clear();
        if (!xrayStateFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(xrayStateFile);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;
        long now = System.currentTimeMillis();
        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                String path = "players." + rawUuid;
                xrayWarnings.put(uuid, Math.max(0, Math.min(2, data.getInt(path + ".warnings", 0))));
                xrayLastAction.put(uuid, data.getLong(path + ".last-action", 0L));
                long pendingUntil = data.getLong(path + ".pending-until", 0L);
                if (pendingUntil > now) pendingXrayBans.put(uuid, pendingUntil);
                String name = data.getString(path + ".name", "");
                if (!name.isBlank()) oreNames.put(uuid, name);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveXrayState() {
        YamlConfiguration data = new YamlConfiguration();
        Set<UUID> uuids = new HashSet<>();
        uuids.addAll(xrayWarnings.keySet());
        uuids.addAll(pendingXrayBans.keySet());
        for (UUID uuid : uuids) {
            String path = "players." + uuid;
            data.set(path + ".name", oreNames.getOrDefault(uuid, "unknown"));
            data.set(path + ".warnings", xrayWarnings.getOrDefault(uuid, 0));
            data.set(path + ".last-action", xrayLastAction.getOrDefault(uuid, 0L));
            data.set(path + ".pending-until", pendingXrayBans.getOrDefault(uuid, 0L));
        }
        File parent = xrayStateFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try {
            data.save(xrayStateFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraOreWatch gagal menyimpan state enforcement: " + exception.getMessage());
        }
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.contains("DIAMOND_ORE") || name.equals("ANCIENT_DEBRIS") || name.contains("GOLD_ORE") || name.contains("IRON_ORE") || name.contains("EMERALD_ORE") || name.contains("LAPIS_ORE") || name.contains("REDSTONE_ORE") || name.contains("COAL_ORE") || name.contains("COPPER_ORE");
    }

    private String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void alertIfNeeded(SecurityDecision decision) {
        if (decision == null || !decision.alert()) return;
        alertManager.alert(decision.type(), decision.player(), decision.risk(), decision.reason(), decision.action());
    }

    private Map<String, String> statusPlaceholders() {
        Map<String, String> map = new HashMap<>();
        map.put("%enabled%", String.valueOf(configManager.isEnabled()));
        map.put("%join_protection%", String.valueOf(configManager.isJoinProtectionEnabled()));
        map.put("%name_protection%", String.valueOf(configManager.isNameProtectionEnabled()));
        map.put("%command_protection%", String.valueOf(configManager.isCommandProtectionEnabled()));
        map.put("%tab_protection%", String.valueOf(configManager.isTabProtectionEnabled()));
        map.put("%blocked_commands%", String.valueOf(commandProtectionManager.blockedCommands().size()));
        map.put("%recent_alerts%", String.valueOf(alertManager.recentCount()));
        return map;
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.message(path, fallback), placeholders)));
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) sender.sendMessage(configManager.color(apply(line, placeholders)));
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }

    private String color(String text) { return configManager.color(text); }

    private record OreRecord(long time, String ore) { }
    private record OreReport(UUID uuid, String name, int window, int diamond, int debris, int gold, int iron, int emerald, String level, int score) { }
}
