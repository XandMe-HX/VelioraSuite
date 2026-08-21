package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.security.model.BanSource;
import id.velioragardens.veliorasuite.module.security.model.SecurityDecision;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.TimeZone;

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
    private final File xrayEvidenceFile;

    private final Map<UUID, List<OreRecord>> oreRecords = new HashMap<>();
    private final Map<UUID, List<MiningRecord>> miningRecords = new HashMap<>();
    private final Map<UUID, String> oreNames = new HashMap<>();
    private final Set<String> placedOre = new HashSet<>();
    private final Set<String> exemptOreNames = new HashSet<>();
    private final List<OreReport> oreAlerts = new ArrayList<>();
    private final Map<String, Long> alertCooldown = new HashMap<>();
    private final Map<UUID, Integer> xrayWarnings = new HashMap<>();
    private final Map<UUID, Long> xrayLastAction = new HashMap<>();
    private final Map<UUID, Long> pendingXrayBans = new HashMap<>();
    private final Map<UUID, Integer> visualOreAlerts = new HashMap<>();
    private final Map<UUID, Map<Material, Integer>> xrayInventoryBaselines = new HashMap<>();
    private final Map<UUID, Honeypot> activeHoneypots = new HashMap<>();
    private final Map<UUID, Integer> honeypotHits = new HashMap<>();

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
        this.xrayEvidenceFile = new File(plugin.getDataFolder(), "data/xray-evidence.yml");
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

    public void trackMiningBreak(Player player, Block block) {
        if (player == null || block == null || player.hasMetadata("velioraftb_vein_secondary")) return;
        handleHoneypotBreak(player, block);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        Location location = block.getLocation();
        List<MiningRecord> records = miningRecords.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
        records.add(new MiningRecord(System.currentTimeMillis(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        long cutoff = System.currentTimeMillis() - 60L * 60_000L;
        records.removeIf(record -> record.time() < cutoff);
        if (records.size() > 2500) records.subList(0, records.size() - 2500).clear();
    }

    public void trackOreBreak(Player player, Block block) {
        // VelioraFTB calls Player#breakBlock for every secondary Vein Miner block.
        // Only the player's original/manual break is evidence; generated secondary
        // breaks must not inflate OreWatch ratios or trigger automatic punishment.
        if (player == null || block == null || !isOre(block.getType())) return;
        if (player.hasMetadata("velioraftb_vein_secondary")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (configManager.hasBypass(player) || exemptOreNames.contains(player.getName().toLowerCase(Locale.ROOT))) return;
        if (placedOre.remove(locationKey(block.getLocation()))) return;

        UUID uuid = player.getUniqueId();
        oreNames.put(uuid, player.getName());
        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortune = tool.getEnchantments().entrySet().stream()
                .filter(entry -> entry.getKey().getKey().getKey().equalsIgnoreCase("fortune"))
                .mapToInt(Map.Entry::getValue).max().orElse(0);
        boolean silkTouch = tool.getEnchantments().keySet().stream()
                .anyMatch(enchantment -> enchantment.getKey().getKey().equalsIgnoreCase("silk_touch"));
        Location location = block.getLocation();
        OreRecord previousRare = latestRareRecord(uuid, location.getWorld().getName());
        double transitionDistance = previousRare == null ? 0.0D : distance(previousRare, location);
        double transitionSeconds = previousRare == null ? 0.0D : Math.max(0.001D, (System.currentTimeMillis() - previousRare.time()) / 1000.0D);
        long breaksBetween = previousRare == null ? 0L : miningRecords.getOrDefault(uuid, List.of()).stream()
                .filter(record -> record.time() > previousRare.time() && record.world().equals(location.getWorld().getName())).count();
        double pathEfficiency = previousRare == null ? 0.0D : Math.min(1.0D, transitionDistance / Math.max(1.0D, breaksBetween));
        boolean fastTransition = transitionDistance >= 24.0D && transitionSeconds < Math.max(12.0D, transitionDistance * 0.70D);
        boolean preciseTransition = transitionDistance >= 16.0D && breaksBetween >= 4L && pathEfficiency >= 0.78D;
        boolean exposedToCave = exposedFaces(block) >= 2;
        oreRecords.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(new OreRecord(
                System.currentTimeMillis(), block.getType().name(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                tool.getType().name(), fortune, silkTouch, fastTransition, preciseTransition,
                exposedToCave, transitionDistance, transitionSeconds, pathEfficiency));
        trim(uuid);
        OreReport fiveMinuteReport = report(uuid, 5);
        OreReport report = strongest(fiveMinuteReport, report(uuid, 15), report(uuid, 60));
        if (!report.level().equals("NORMAL")) addOreAlert(player, report);
        if (report.level().equals("HIGH")) armHoneypot(player, report);
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
                "&7Mode: &f1 efek, 2 karantina+kick, 3 ban 3 hari, ulang ban 15 hari",
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
        oreRecords.getOrDefault(uuid, List.of()).stream().sorted(Comparator.comparingLong(OreRecord::time).reversed()).limit(12)
                .forEach(record -> sender.sendMessage(color("&8- &7" + formatTime(record.time()) + " &f" + record.ore()
                        + " &8| &7" + record.world() + " " + record.x() + " " + record.y() + " " + record.z()
                        + " &8| &7tool &f" + record.tool() + " &7Fortune " + record.fortune()
                        + (record.silkTouch() ? " &bSilk Touch" : ""))));
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
            visualOreAlerts.remove(uuid);
            xrayInventoryBaselines.remove(uuid);
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

        int displayed = visualOreAlerts.getOrDefault(player.getUniqueId(), 0);
        if (displayed >= configManager.getXrayVisualAlertLimit()) return;
        visualOreAlerts.put(player.getUniqueId(), displayed + 1);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!configManager.hasAlerts(online) && !configManager.hasAdmin(online)) continue;
            online.sendMessage(color("&8[&cVelioraOreWatch&8] &cSuspicious Mining &7("
                    + (displayed + 1) + "/" + configManager.getXrayVisualAlertLimit() + ")"));
            sendOreReport(online, report);
        }
    }

    private void handleExtremeXray(Player player, OreReport report) {
        if (!configManager.isXrayEnforcementEnabled() || configManager.hasBypass(player)) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = configManager.getXrayStrikeCooldownMinutes() * 60_000L;
        if (now - xrayLastAction.getOrDefault(uuid, 0L) < cooldown) return;

        // Geyser/Bedrock mining packets and reach timing differ from Java. Keep the
        // full evidence, but require staff review instead of automatic punishment.
        if (isBedrock(player)) {
            xrayLastAction.put(uuid, now);
            saveXrayEvidence(player, report, "BEDROCK_REVIEW_ONLY");
            notifyXrayOwners(player.getName(), "&bBedrock review-only &7- bukti tersimpan, tidak ada auto-ban.", report);
            return;
        }

        int warning = Math.min(4, xrayWarnings.getOrDefault(uuid, 0) + 1);
        xrayWarnings.put(uuid, warning);
        xrayLastAction.put(uuid, now);
        oreNames.put(uuid, player.getName());

        if (warning == 1) {
            xrayInventoryBaselines.put(uuid, snapshotSuspiciousInventory(player));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    configManager.getXrayBlindnessSeconds() * 20, 0, false, false, true));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.9F, 0.65F);
            player.sendTitle(color("&4&lX-RAY TERDETEKSI"), color("&cPeringatan 1/3 &7- aktivitasmu sedang dicatat"), 10, 80, 20);
            player.sendMessage(color("&8[&cVelioraOreWatch&8] &cBerhenti menggunakan Xray. &7Pelanggaran kedua: item mencurigakan dikarantina dan kamu dikeluarkan."));
            notifyXrayOwners(player.getName(), "&eTahap 1/3 &7- blindness, suara, title, dan bukti disimpan.", report);
            saveXrayEvidence(player, report, "WARNING_1");
            saveXrayState();
            return;
        }

        String quarantineId = quarantineSuspiciousGain(player, report);
        if (warning == 2) {
            notifyXrayOwners(player.getName(), "&6Tahap 2/3 &7- kick dan item hasil sesi dikarantina."
                    + (quarantineId.isBlank() ? "" : " &7ID: &f" + quarantineId), report);
            saveXrayEvidence(player, report, "KICK_2 quarantine=" + quarantineId);
            xrayInventoryBaselines.put(uuid, snapshotSuspiciousInventory(player));
            saveXrayState();
            player.kickPlayer(color("&cPeringatan Xray 2/3\n&7Item mencurigakan diamankan untuk pemeriksaan owner."
                    + (quarantineId.isBlank() ? "" : "\n&7ID karantina: &f" + quarantineId)
                    + "\n&7Pelanggaran berikutnya: ban 3 hari."));
            return;
        }

        int days = warning == 3 ? configManager.getXrayFirstBanDays() : configManager.getXrayRepeatBanDays();
        String stage = warning == 3 ? "BAN_3_DAYS" : "REPEAT_BAN_15_DAYS";
        saveXrayEvidence(player, report, stage + " quarantine=" + quarantineId);
        notifyXrayOwners(player.getName(), (warning == 3 ? "&cTahap 3/3" : "&4Pelanggaran berulang")
                + " &7- auto-ban &f" + days + " hari&7."
                + (quarantineId.isBlank() ? "" : " Karantina: &f" + quarantineId), report);
        saveXrayState();
        banManager.banPlayerTemporarily(uuid, player.getName(),
                "Xray EXTREME tahap " + warning + ". Banding/keringanan: " + configManager.getXrayAppealContact(),
                BanSource.AUTO_XRAY, days * 24L * 60L * 60L * 1000L);
    }

    private Map<Material, Integer> snapshotSuspiciousInventory(Player player) {
        Map<Material, Integer> snapshot = new HashMap<>();
        for (Material material : suspiciousXrayMaterials()) {
            int amount = player.getInventory().all(material).values().stream().mapToInt(ItemStack::getAmount).sum();
            snapshot.put(material, amount);
        }
        return snapshot;
    }

    private String quarantineSuspiciousGain(Player player, OreReport report) {
        Map<Material, Integer> before = xrayInventoryBaselines.get(player.getUniqueId());
        if (before == null) {
            xrayInventoryBaselines.put(player.getUniqueId(), snapshotSuspiciousInventory(player));
            return "";
        }
        Map<Material, Integer> now = snapshotSuspiciousInventory(player);
        Map<Material, Integer> requested = new HashMap<>();
        for (Material material : suspiciousXrayMaterials()) {
            int gained = Math.max(0, now.getOrDefault(material, 0) - before.getOrDefault(material, 0));
            if (gained > 0) requested.put(material, gained);
        }
        return antiDupeManager.quarantineSuspiciousItems(player, requested,
                "XRAY_STAGE_" + xrayWarnings.getOrDefault(player.getUniqueId(), 0)
                        + "_D" + report.diamond() + "_A" + report.debris());
    }

    private List<Material> suspiciousXrayMaterials() {
        return List.of(Material.DIAMOND, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.ANCIENT_DEBRIS);
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
        String action = report.level().equals("EXTREME") ? "Enforcement bertahap otomatis" : "Review admin";
        sender.sendMessage(color("&7Waktu: &f" + formatTime(report.latestTime()) + " WIB &8| &7Window: &f"
                + report.window() + "m &8| &7Level: &e" + report.level() + " &8| &7Action: &f" + action));
        sender.sendMessage(color("&7Diamond: &f" + report.diamond() + " &7Debris: &f" + report.debris()
                + " &7Emerald: &f" + report.emerald() + " &8| &7Total ore: &f" + report.total()
                + " &7Rasio rare: &f" + String.format(Locale.US, "%.1f%%", report.rareRatio() * 100.0D)));
        sender.sendMessage(color("&7Lokasi terakhir: &f" + report.world() + " " + report.x() + " " + report.y() + " " + report.z()
                + " &8| &7Tool: &f" + report.tool() + " &7Fortune " + report.fortune()
                + (report.silkTouch() ? " &bSilk Touch" : "")
                + (report.caveLikely() ? " &8| &aKonteks cave terdeteksi" : "")));
        sender.sendMessage(color("&7Pola jalur: &f" + report.preciseTransitions() + " presisi &8| &f"
                + report.fastTransitions() + " perpindahan cepat"
                + (report.caveLikely() ? " &8| &aSkor dilonggarkan karena cave" : "")));
    }

    private OreReport report(UUID uuid, int minutes) {
        long since = System.currentTimeMillis() - minutes * 60000L;
        int diamond = 0, debris = 0, gold = 0, iron = 0, emerald = 0, total = 0;
        int fastTransitions = 0, preciseTransitions = 0, caveDiscoveries = 0;
        OreRecord latest = null;
        for (OreRecord record : oreRecords.getOrDefault(uuid, List.of())) {
            if (record.time() < since) continue;
            total++;
            if (record.fastTransition()) fastTransitions++;
            if (record.preciseTransition()) preciseTransitions++;
            if (record.exposedToCave()) caveDiscoveries++;
            if (latest == null || record.time() > latest.time()) latest = record;
            String ore = record.ore();
            if (ore.contains("DIAMOND_ORE")) diamond++;
            else if (ore.equals("ANCIENT_DEBRIS")) debris++;
            else if (ore.contains("GOLD_ORE")) gold++;
            else if (ore.contains("IRON_ORE")) iron++;
            else if (ore.contains("EMERALD_ORE")) emerald++;
        }
        double rareRatio = (diamond + debris + emerald) / (double) Math.max(1, total);
        boolean caveLikely = (total >= 30 && rareRatio < 0.18D) || caveDiscoveries >= Math.max(3, (diamond + debris + emerald) / 2);
        String level = level(minutes, diamond, debris, gold, iron, emerald, total, fastTransitions, preciseTransitions, caveLikely);
        int score = diamond * 3 + debris * 10 + emerald * 4 + gold + iron / 2
                + fastTransitions * 18 + preciseTransitions * 14 - (caveLikely ? 20 : 0);
        long latestTime = latest == null ? System.currentTimeMillis() : latest.time();
        return new OreReport(uuid, oreNames.getOrDefault(uuid, "unknown"), minutes, diamond, debris, gold, iron,
                emerald, total, rareRatio, caveLikely, level, score, latestTime,
                latest == null ? "unknown" : latest.world(), latest == null ? 0 : latest.x(),
                latest == null ? 0 : latest.y(), latest == null ? 0 : latest.z(),
                latest == null ? "unknown" : latest.tool(), latest == null ? 0 : latest.fortune(),
                latest != null && latest.silkTouch(), fastTransitions, preciseTransitions);
    }

    private boolean isBedrock(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
            if (result instanceof Boolean value) return value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Older Floodgate: use the configured username prefix as fallback.
        }
        String name = player.getName();
        return name.startsWith("_") || name.startsWith(".") || name.startsWith("*");
    }

    private String level(int minutes, int diamond, int debris, int gold, int iron, int emerald, int total,
                         int fastTransitions, int preciseTransitions, boolean caveLikely) {
        double factor = minutes / 5.0D;
        double rareRatio = (diamond + debris + emerald) / (double) Math.max(1, total);

        // Fortune and Silk Touch never multiply this counter: one broken ore block
        // is always one record. A broad cave vein is softened by the rare/total ratio.
        boolean rareExtreme = debris >= 12 * factor || diamond >= 55 * factor;
        boolean directPattern = rareRatio >= 0.18D || debris >= 16 * factor || diamond >= 80 * factor;
        boolean navigationPattern = fastTransitions + preciseTransitions >= 3;
        if ((rareExtreme && directPattern && !caveLikely) || (navigationPattern && (diamond >= 25 * factor || debris >= 5 * factor))) return "EXTREME";
        if (debris >= 9 * factor || diamond >= 40 * factor || emerald >= 15 * factor
                || gold >= 75 * factor || iron >= 150 * factor) return "HIGH";
        if (debris >= 5 * factor || diamond >= 25 * factor || emerald >= 8 * factor
                || gold >= 40 * factor || iron >= 80 * factor) return "ALERT";
        return "NORMAL";
    }

    private OreRecord latestRareRecord(UUID uuid, String world) {
        List<OreRecord> records = oreRecords.getOrDefault(uuid, List.of());
        for (int i = records.size() - 1; i >= 0; i--) {
            OreRecord record = records.get(i);
            if (record.world().equals(world) && (record.ore().contains("DIAMOND_ORE")
                    || record.ore().equals("ANCIENT_DEBRIS") || record.ore().contains("EMERALD_ORE"))) return record;
        }
        return null;
    }

    private double distance(OreRecord record, Location location) {
        double dx = record.x() - location.getBlockX();
        double dy = record.y() - location.getBlockY();
        double dz = record.z() - location.getBlockZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private int exposedFaces(Block block) {
        int exposed = 0;
        for (org.bukkit.block.BlockFace face : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN)) {
            if (block.getRelative(face).isPassable()) exposed++;
        }
        return exposed;
    }

    private void armHoneypot(Player player, OreReport report) {
        if (!configManager.config().getBoolean("settings.xray-enforcement.honeypot.enabled", true)
                || isBedrock(player) || player.getLocation().getBlockY() > 16
                || activeHoneypots.containsKey(player.getUniqueId())) return;
        int lifetimeSeconds = Math.max(5, configManager.config().getInt("settings.xray-enforcement.honeypot.lifetime-seconds", 20));
        Location origin = player.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = 5 + (attempt % 4);
            if ((attempt & 1) == 1) dx = -dx;
            int dz = 4 + ((attempt * 3) % 5);
            if ((attempt & 2) != 0) dz = -dz;
            Block candidate = origin.getBlock().getRelative(dx, (attempt % 3) - 1, dz);
            if (!isHoneypotStone(candidate) || exposedFaces(candidate) != 0) continue;
            Honeypot honeypot = new Honeypot(candidate.getLocation(), candidate.getBlockData(),
                    System.currentTimeMillis() + lifetimeSeconds * 1000L, report.score());
            activeHoneypots.put(player.getUniqueId(), honeypot);
            player.sendBlockChange(candidate.getLocation(), Material.DIAMOND_ORE.createBlockData());
            Bukkit.getScheduler().runTaskLater(plugin, () -> clearHoneypot(player), lifetimeSeconds * 20L);
            return;
        }
    }

    private void handleHoneypotBreak(Player player, Block block) {
        Honeypot honeypot = activeHoneypots.get(player.getUniqueId());
        if (honeypot == null || !sameBlock(honeypot.location(), block.getLocation())) return;
        activeHoneypots.remove(player.getUniqueId());
        player.sendBlockChange(honeypot.location(), honeypot.originalData());
        int hits = honeypotHits.merge(player.getUniqueId(), 1, Integer::sum);
        OreReport report = strongest(report(player.getUniqueId(), 5), report(player.getUniqueId(), 15), report(player.getUniqueId(), 60));
        saveXrayEvidence(player, report, "HONEYPOT_HIT_" + hits);
        notifyXrayOwners(player.getName(), "&cHoneypot tersentuh &7(" + hits + "/2). Bukti disimpan.", report);
        int highScore = Math.max(100, configManager.config().getInt("settings.xray-enforcement.honeypot.hit-plus-high-score", 180));
        if (hits >= 2 || honeypot.reportScore() >= highScore) handleExtremeXray(player, report);
    }

    private void clearHoneypot(Player player) {
        Honeypot honeypot = activeHoneypots.remove(player.getUniqueId());
        if (honeypot != null && player.isOnline()) player.sendBlockChange(honeypot.location(), honeypot.originalData());
    }

    private boolean isHoneypotStone(Block block) {
        return block.getType() == Material.STONE || block.getType() == Material.DEEPSLATE;
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX() && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
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

    private void saveXrayEvidence(Player player, OreReport report, String action) {
        File parent = xrayEvidenceFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        YamlConfiguration data = xrayEvidenceFile.exists()
                ? YamlConfiguration.loadConfiguration(xrayEvidenceFile) : new YamlConfiguration();
        String id = System.currentTimeMillis() + "-" + player.getUniqueId().toString().substring(0, 8);
        String path = "records." + id;
        data.set(path + ".timestamp", System.currentTimeMillis());
        data.set(path + ".time-wib", formatTime(System.currentTimeMillis()));
        data.set(path + ".player", player.getName());
        data.set(path + ".uuid", player.getUniqueId().toString());
        data.set(path + ".action", action);
        data.set(path + ".window-minutes", report.window());
        data.set(path + ".level", report.level());
        data.set(path + ".score", report.score());
        data.set(path + ".diamond-ore", report.diamond());
        data.set(path + ".ancient-debris", report.debris());
        data.set(path + ".total-ore", report.total());
        data.set(path + ".rare-ratio", report.rareRatio());
        data.set(path + ".cave-context", report.caveLikely());
        data.set(path + ".last-location", report.world() + " " + report.x() + " " + report.y() + " " + report.z());
        data.set(path + ".tool", report.tool());
        data.set(path + ".fortune", report.fortune());
        data.set(path + ".silk-touch", report.silkTouch());
        List<String> logs = oreRecords.getOrDefault(player.getUniqueId(), List.of()).stream()
                .filter(record -> record.time() >= System.currentTimeMillis() - report.window() * 60000L)
                .sorted(Comparator.comparingLong(OreRecord::time).reversed()).limit(30)
                .map(record -> formatTime(record.time()) + " WIB | " + record.ore() + " | "
                        + record.world() + " " + record.x() + " " + record.y() + " " + record.z()
                        + " | " + record.tool() + " Fortune " + record.fortune()
                        + (record.silkTouch() ? " SilkTouch" : ""))
                .toList();
        data.set(path + ".ore-log", logs);
        ConfigurationSection records = data.getConfigurationSection("records");
        if (records != null && records.getKeys(false).size() > 500) {
            records.getKeys(false).stream().sorted().limit(records.getKeys(false).size() - 500)
                    .forEach(old -> data.set("records." + old, null));
        }
        try {
            data.save(xrayEvidenceFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("VelioraOreWatch gagal menyimpan bukti: " + exception.getMessage());
        }
    }

    private String formatTime(long time) {
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        return format.format(new java.util.Date(time));
    }

    private void loadXrayState() {
        xrayWarnings.clear();
        xrayLastAction.clear();
        pendingXrayBans.clear();
        visualOreAlerts.clear();
        xrayInventoryBaselines.clear();
        if (!xrayStateFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(xrayStateFile);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;
        long now = System.currentTimeMillis();
        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                String path = "players." + rawUuid;
                xrayWarnings.put(uuid, Math.max(0, Math.min(4, data.getInt(path + ".warnings", 0))));
                xrayLastAction.put(uuid, data.getLong(path + ".last-action", 0L));
                long pendingUntil = data.getLong(path + ".pending-until", 0L);
                if (pendingUntil > now) pendingXrayBans.put(uuid, pendingUntil);
                String name = data.getString(path + ".name", "");
                if (!name.isBlank()) oreNames.put(uuid, name);
                ConfigurationSection baselineSection = data.getConfigurationSection(path + ".inventory-baseline");
                if (baselineSection != null) {
                    Map<Material, Integer> baseline = new HashMap<>();
                    for (String materialName : baselineSection.getKeys(false)) {
                        Material material = Material.matchMaterial(materialName);
                        if (material != null) baseline.put(material, Math.max(0, baselineSection.getInt(materialName)));
                    }
                    if (!baseline.isEmpty()) xrayInventoryBaselines.put(uuid, baseline);
                }
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveXrayState() {
        YamlConfiguration data = new YamlConfiguration();
        Set<UUID> uuids = new HashSet<>();
        uuids.addAll(xrayWarnings.keySet());
        uuids.addAll(pendingXrayBans.keySet());
        uuids.addAll(xrayInventoryBaselines.keySet());
        for (UUID uuid : uuids) {
            String path = "players." + uuid;
            data.set(path + ".name", oreNames.getOrDefault(uuid, "unknown"));
            data.set(path + ".warnings", xrayWarnings.getOrDefault(uuid, 0));
            data.set(path + ".last-action", xrayLastAction.getOrDefault(uuid, 0L));
            data.set(path + ".pending-until", pendingXrayBans.getOrDefault(uuid, 0L));
            Map<Material, Integer> baseline = xrayInventoryBaselines.get(uuid);
            if (baseline != null) {
                for (Map.Entry<Material, Integer> entry : baseline.entrySet()) {
                    data.set(path + ".inventory-baseline." + entry.getKey().name(), entry.getValue());
                }
            }
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

    private record MiningRecord(long time, String world, int x, int y, int z) { }

    private record Honeypot(Location location, org.bukkit.block.data.BlockData originalData,
                            long expiresAt, int reportScore) { }

    private record OreRecord(long time, String ore, String world, int x, int y, int z,
                             String tool, int fortune, boolean silkTouch, boolean fastTransition,
                             boolean preciseTransition, boolean exposedToCave, double transitionDistance,
                             double transitionSeconds, double pathEfficiency) { }

    private record OreReport(UUID uuid, String name, int window, int diamond, int debris, int gold, int iron,
                             int emerald, int total, double rareRatio, boolean caveLikely, String level, int score,
                             long latestTime, String world, int x, int y, int z, String tool, int fortune,
                             boolean silkTouch, int fastTransitions, int preciseTransitions) { }
}
