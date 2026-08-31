package id.velioragardens.veliorasuite.module.adventure;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.gui.GuiLayout;
import id.velioragardens.veliorasuite.module.race.RaceModule;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import id.velioragardens.veliorasuite.module.team.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class AdventureManager implements Listener {
    private final VelioraSuite plugin;
    private final AdventureConfigManager config;
    private final AdventureDataManager data;
    private final NamespacedKey actionKey;
    private final NamespacedKey questKey;
    private final Set<String> placedBlocks = new HashSet<>();
    private final Set<UUID> excludedFarmMobs = new HashSet<>();
    private final Map<UUID, Long> moveChecks = new HashMap<>();
    private final Map<UUID, Long> guideCooldowns = new HashMap<>();
    private final Map<UUID, ProgressWindow> progressWindows = new HashMap<>();
    private final Map<UUID, BossBar> questBars = new HashMap<>();
    private BukkitTask questBarTask;
    private BukkitTask inactivityTask;

    public AdventureManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.config = new AdventureConfigManager(plugin);
        this.data = new AdventureDataManager(plugin);
        this.actionKey = new NamespacedKey(plugin, "adventure_action");
        this.questKey = new NamespacedKey(plugin, "adventure_quest");
    }

    public void load() { config.load(); data.load(); startQuestBars(); startInactivityChecks(); }
    public void reload() { stopQuestBars(); stopInactivityChecks(); data.shutdown(); config.load(); data.load(); startQuestBars(); startInactivityChecks(); }
    public void shutdown() { stopQuestBars(); stopInactivityChecks(); data.shutdown(); cleanupQuestMobs(-1); }
    public AdventureConfigManager config() { return config; }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(new AdventureHolder("main"), 27, config.mainTitle());
        fill(inventory, Material.GREEN_STAINED_GLASS_PANE);
        inventory.setItem(11, item(Material.WRITABLE_BOOK, "&aMisi Hari Ini", List.of(
                "&7Lihat lima misi Guild Petualang", "&7yang tersedia hari ini.", "", "&eKlik untuk membuka."), "daily", ""));
        inventory.setItem(13, item(Material.BOOK, "&ePanduan Guild", List.of(
                "&7Cara membuat team, menerima misi,", "&7menyelesaikan, dan menyetor hadiah.", "", "&eKlik untuk dikirim ke chat."), "guide", ""));
        inventory.setItem(15, item(Material.CHEST, "&bSetor dan Riwayat", List.of(
                "&7Setor misi yang sudah selesai", "&7dan lihat progress guild.", "", "&eKlik untuk membuka."), "submit", ""));
        inventory.setItem(17, item(Material.DIAMOND_PICKAXE, "&6Profesi Petualang", List.of(
                "&7Penambang, Penebang, Petani,", "&7Pemburu, dan Nelayan.", "", "&eKlik untuk melihat perkembangan."), "professions", ""));
        inventory.setItem(22, profileItem(player));
        player.openInventory(inventory);
    }

    public void openTeam(Player player) {
        Inventory inventory = Bukkit.createInventory(new AdventureHolder("team"), 27, config.teamTitle());
        fill(inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        Team team = team(player);
        inventory.setItem(10, item(Material.PLAYER_HEAD, "&bStatus Team", team == null
                ? List.of("&7Kamu belum memiliki team.", "&7Gunakan &f/team create <nama>&7.")
                : List.of("&7Nama: &f" + team.getDisplayName(), "&7Owner: &f" + team.getOwnerName(),
                "&7Member: &f" + team.getMembers().size() + "/" + team.getMaxMembers(), "&7Online: &f" + onlineMembers(team)), "none", ""));
        inventory.setItem(12, item(Material.NAME_TAG, "&aBuat Team", List.of("&7Gunakan:", "&f/team create <nama>", "", "&eKlik untuk panduan."), "team_create", ""));
        inventory.setItem(14, item(Material.PAPER, "&eUndang Anggota", List.of("&7Gunakan:", "&f/team invite <nama>", "", "&eKlik untuk panduan."), "team_invite", ""));
        inventory.setItem(16, item(Material.COMPASS, "&dGuild Petualang", List.of("&7Kembali ke menu petualang."), "main", ""));
        player.openInventory(inventory);
    }

    private void openDaily(Player player) {
        Team team = requireReadyTeam(player, false);
        if (team == null) return;
        AdventureDataManager.GuildData guild = daily(team);
        Inventory inventory = Bukkit.createInventory(new AdventureHolder("daily"), 27, config.questsTitle());
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        int[] slots = {10, 11, 13, 15, 16};
        for (int i = 0; i < Math.min(slots.length, guild.dailyIds().size()); i++) {
            AdventureQuestTemplate quest = template(guild.dailyIds().get(i));
            if (quest == null) continue;
            boolean active = quest.id().equals(guild.activeQuest());
            String status = active ? (guild.ready() ? "&aSIAP DISETOR" : "&eAKTIF") : guild.activeQuest().isBlank() ? "&7BELUM DIAMBIL" : "&8MENUNGGU";
            List<String> lore = new ArrayList<>();
            lore.add("&7Rank: " + config.rankDisplay(quest.rank()));
            lore.add("&7Tugas: &f" + objective(quest));
            lore.add("&7Hadiah: &a$" + quest.money() + " &7+ &b" + quest.playerExp() + " EXP");
            lore.add("&7Guild EXP: &d" + quest.guildExp() + " &7| Mana: &b" + quest.mana());
            if (active) {
                lore.add("&7Progress: &f" + guild.activeProgress() + "/" + guild.activeTarget());
                lore.add("&7Lokasi: &fX " + guild.activeX() + " Z " + guild.activeZ());
            }
            lore.add(""); lore.add("&7Status: " + status);
            lore.add(guild.activeQuest().isBlank() ? "&eKlik untuk menerima misi." : "&8Selesaikan misi aktif terlebih dahulu.");
            inventory.setItem(slots[i], item(icon(quest), "&f" + quest.name(), lore, "accept", quest.id()));
        }
        inventory.setItem(22, item(Material.ARROW, "&cKembali", List.of("&7Kembali ke menu utama."), "main", ""));
        player.openInventory(inventory);
    }

    private void openSubmit(Player player) {
        Team team = team(player);
        if (team == null) { send(player, "team-required", "&cKamu harus memiliki team terlebih dahulu."); return; }
        AdventureDataManager.GuildData guild = daily(team);
        Inventory inventory = Bukkit.createInventory(new AdventureHolder("submit"), 27, config.submitTitle());
        fill(inventory, Material.BLUE_STAINED_GLASS_PANE);
        AdventureQuestTemplate quest = template(guild.activeQuest());
        if (quest == null) {
            inventory.setItem(13, item(Material.BARRIER, "&cTidak Ada Misi Aktif", List.of("&7Pilih misi dari menu Misi Hari Ini."), "daily", ""));
        } else {
            List<String> lore = new ArrayList<>(List.of("&7Misi: &f" + quest.name(),
                    "&7Progress: &f" + guild.activeProgress() + "/" + guild.activeTarget(),
                    "&7Kontributor: &f" + guild.contributions().size(), ""));
            lore.add(guild.ready() ? "&aKlik untuk menyerahkan dan menerima hadiah." : "&cMisi belum selesai.");
            inventory.setItem(13, item(guild.ready() ? Material.EMERALD_BLOCK : Material.CLOCK,
                    guild.ready() ? "&aSetor Misi" : "&eMisi Sedang Berjalan", lore, "claim", quest.id()));
        }
        inventory.setItem(11, item(Material.EXPERIENCE_BOTTLE, "&dPerkembangan Guild", List.of(
                "&7Level Guild: &f" + guildLevel(guild.exp()), "&7Guild EXP: &f" + guild.exp(),
                "&7Misi selesai: &f" + guild.completed()), "none", ""));
        inventory.setItem(15, profileItem(player));
        inventory.setItem(22, item(Material.ARROW, "&cKembali", List.of("&7Kembali ke menu utama."), "main", ""));
        player.openInventory(inventory);
    }

    public void openProfessions(Player player) {
        Inventory inventory = Bukkit.createInventory(new AdventureHolder("professions"), 27, config.color("&8Profesi Petualang"));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        int[] slots = {10, 11, 13, 15, 16};
        for (int i = 0; i < AdventureProfession.values().length; i++) {
            AdventureProfession profession = AdventureProfession.values()[i];
            long exp = professionExp(player, profession);
            int level = professionLevel(exp);
            long current = exp % config.professionLevelExp();
            inventory.setItem(slots[i], item(profession.icon(), profession.color() + profession.display(), List.of(
                    "&7Level: &f" + level, "&7Pengalaman: &f" + exp,
                    "&7Menuju level berikutnya: &f" + current + "/" + config.professionLevelExp(), "", "&8Progres dihitung dari aktivitas alami."), "none", ""));
        }
        inventory.setItem(22, item(Material.ARROW, "&cKembali", List.of("&7Kembali ke menu utama."), "main", ""));
        player.openInventory(inventory);
    }

    public boolean accept(Player player, String questId) {
        Team team = requireReadyTeam(player, true);
        if (team == null) return false;
        AdventureDataManager.GuildData guild = daily(team);
        if (!guild.activeQuest().isBlank()) { send(player, "quest-active", "&cTeam kamu sudah mempunyai misi aktif."); return false; }
        if (!guild.dailyIds().contains(questId)) { send(player, "quest-not-daily", "&cMisi itu tidak tersedia hari ini."); return false; }
        AdventureQuestTemplate quest = template(questId);
        if (quest == null) return false;
        AdventureRank playerRank = standardRank(player);
        if (playerRank.ordinal() < quest.rank().ordinal()) {
            send(player, "rank-too-low", "&cRank kamu belum cukup. Butuh rank &f%rank%&c.", "%rank%", quest.rank().name());
            return false;
        }
        World world = Bukkit.getWorld(config.questWorld());
        if (world == null) { send(player, "quest-world-missing", "&cWorld misi tidak ditemukan: &f" + config.questWorld()); return false; }
        Location location = findSafeQuestLocation(world, new Random(System.nanoTime() ^ team.getId()));
        if (location == null) { send(player, "quest-location-failed", "&cLokasi misi aman belum ditemukan. Coba lagi."); return false; }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        guild.start(quest, System.currentTimeMillis() + quest.durationMinutes() * 60_000L, x, z);
        data.save();
        broadcast(team, config.prefix() + config.color("&aMisi &f" + quest.name() + " &aditerima. Lokasi: &fX " + x + " Z " + z + "&a."));
        refreshQuestBars();
        return true;
    }

    public boolean claim(Player player) {
        Team team = requireReadyTeam(player, true);
        if (team == null) return false;
        AdventureDataManager.GuildData guild = daily(team);
        AdventureQuestTemplate quest = template(guild.activeQuest());
        if (quest == null || !guild.ready()) { send(player, "quest-not-ready", "&cMisi belum siap disetor."); return false; }
        if (expired(guild)) {
            cleanupQuestMobs(team.getId()); guild.clearActive(); data.save();
            send(player, "quest-expired", "&cMisi telah kedaluwarsa atau hangus karena tidak ada progres."); return false;
        }
        int minimum = Math.max(1, (int) Math.ceil(quest.amount() * 0.10D));
        for (Map.Entry<UUID, Integer> entry : guild.contributions().entrySet()) {
            if (entry.getValue() < minimum) continue;
            Player member = Bukkit.getPlayer(entry.getKey());
            String name = member == null ? Bukkit.getOfflinePlayer(entry.getKey()).getName() : member.getName();
            AdventureDataManager.PlayerData profile = data.player(entry.getKey(), name);
            int oldLevel = member == null ? 0 : level(member);
            AdventureRank oldRank = member == null ? null : standardRank(member);
            double raceMultiplier = plugin.getModuleManager().getModule("race")
                    .filter(RaceModule.class::isInstance).map(RaceModule.class::cast)
                    .map(module -> module.getManager().questRewardMultiplier(entry.getKey())).orElse(1.0D);
            int rewardedExp = (int) Math.ceil(quest.playerExp() * raceMultiplier);
            int rewardedMoney = (int) Math.ceil(quest.money() * raceMultiplier);
            profile.addExp(rewardedExp); profile.complete();
            if (member != null) {
                deposit(member, rewardedMoney);
                send(member, "quest-reward", "&aMisi selesai! Hadiah: &f$%money% &7+ &b%exp% Guild EXP.",
                        "%money%", String.valueOf(rewardedMoney), "%exp%", String.valueOf(rewardedExp));
                member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
                member.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, member.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.5D, 0.7D, 0.5D, 0.025D);
                showProgressCelebration(member, oldLevel, oldRank);
            }
        }
        cleanupQuestMobs(team.getId());
        guild.complete(quest.guildExp());
        TeamModule teamModule = plugin.getModuleManager().getModule("team").filter(TeamModule.class::isInstance).map(TeamModule.class::cast).orElse(null);
        if (teamModule != null) teamModule.getTeamManager().addQuestScore(team, Math.max(5L, quest.guildExp() / 10L));
        data.save();
        broadcast(team, config.prefix() + config.color("&dGuild memperoleh &f" + quest.guildExp() + " Guild EXP&d."));
        return true;
    }

    public void addFishingProgress(Player player, int amount) { awardProfession(player, AdventureProfession.FISHER, amount); progress(player, AdventureQuestType.FISH, "ANY", amount); }
    public void addBossProgress(Player player, int amount) { progress(player, AdventureQuestType.BOSS, "ANY", amount); }
    public void addExperience(Player player, long amount) {
        if (player == null || amount <= 0) return;
        int oldLevel = level(player);
        AdventureRank oldRank = standardRank(player);
        profile(player).addExp(amount);
        data.save();
        showProgressCelebration(player, oldLevel, oldRank);
    }
    public long exp(Player player) { return profile(player).exp(); }
    public int completed(Player player) { return profile(player).completed(); }
    public int level(Player player) {
        long exp = exp(player), used = 0L;
        for (int level = 1; level < config.maxLevel(); level++) {
            long needed = config.levelBaseExp() + (long) (level - 1) * config.levelGrowthExp();
            if (exp < used + needed) return level;
            used += needed;
        }
        return config.maxLevel();
    }
    public long levelCurrentExp(Player player) {
        int level = level(player); long used = 0L;
        for (int current = 1; current < level; current++) used += config.levelBaseExp() + (long) (current - 1) * config.levelGrowthExp();
        return Math.max(0L, exp(player) - used);
    }
    public long levelRequiredExp(Player player) {
        int level = level(player);
        return level >= config.maxLevel() ? 0L : config.levelBaseExp() + (long) (level - 1) * config.levelGrowthExp();
    }
    public AdventureRank standardRank(Player player) { return config.rankFor(exp(player)); }
    public String rank(Player player) {
        String custom = profile(player).customRank();
        return custom.isBlank() ? config.rankDisplay(standardRank(player)) : config.color(custom);
    }
    public String rankPlain(Player player) {
        String custom = org.bukkit.ChatColor.stripColor(rank(player));
        return custom == null || custom.isBlank() ? standardRank(player).name() : custom;
    }
    public long rankNextExp(Player player) {
        AdventureRank rank = standardRank(player);
        int next = rank.ordinal() + 1;
        return next >= AdventureRank.values().length ? 0L : config.rankRequirement(AdventureRank.values()[next]);
    }
    public long rankRemainingExp(Player player) { return Math.max(0L, rankNextExp(player) - exp(player)); }
    public String nextRank(Player player) {
        AdventureRank rank = standardRank(player);
        int next = rank.ordinal() + 1;
        return next >= AdventureRank.values().length ? "MAX" : config.rankDisplay(AdventureRank.values()[next]);
    }
    public int guildLevel(Player player) { Team team = team(player); return team == null ? 0 : guildLevel(data.guild(team.getId()).exp()); }
    public long guildExp(Player player) { Team team = team(player); return team == null ? 0L : data.guild(team.getId()).exp(); }
    public int guildCompleted(Player player) { Team team = team(player); return team == null ? 0 : data.guild(team.getId()).completed(); }

    public void setRank(CommandSender sender, OfflinePlayer target, String rankText) {
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        AdventureDataManager.PlayerData profile = data.player(target.getUniqueId(), name);
        try {
            AdventureRank rank = AdventureRank.valueOf(rankText.toUpperCase(Locale.ROOT));
            profile.customRank(""); profile.setExp(config.rankRequirement(rank));
        } catch (IllegalArgumentException exception) {
            profile.customRank(rankText);
        }
        data.save();
        sender.sendMessage(config.prefix() + config.color("&aRank &f" + name + " &adiatur menjadi &f" + rankText + "&a."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        if (excludedFarmMobs.remove(event.getEntity().getUniqueId())) return;
        Team team = team(player);
        if (event.getEntity().getScoreboardTags().contains("veliora_adventure_mob")
                && (team == null || !event.getEntity().getScoreboardTags().contains("veliora_adventure_team_" + team.getId()))) return;
        awardProfession(player, AdventureProfession.HUNTER, config.professionKillExp());
        progress(player, AdventureQuestType.KILL, event.getEntityType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG) {
            excludedFarmMobs.add(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String key = blockKey(event.getBlock().getLocation());
        if (placedBlocks.remove(key)) return;
        Material type = event.getBlock().getType();
        if (Tag.LOGS.isTagged(type)) awardProfession(event.getPlayer(), AdventureProfession.FORAGER, config.professionForagingExp());
        else if (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) awardProfession(event.getPlayer(), AdventureProfession.MINER, config.professionMiningExp());
        else if (isCrop(type)) awardProfession(event.getPlayer(), AdventureProfession.FARMER, config.professionFarmingExp());
        progress(event.getPlayer(), AdventureQuestType.BREAK, type.name(), 1);
        progress(event.getPlayer(), AdventureQuestType.FARM, type.name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { placedBlocks.add(blockKey(event.getBlockPlaced().getLocation())); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) return;
        long now = System.currentTimeMillis();
        if (moveChecks.getOrDefault(event.getPlayer().getUniqueId(), 0L) > now) return;
        moveChecks.put(event.getPlayer().getUniqueId(), now + 1000L);
        Player player = event.getPlayer(); Team team = team(player); if (team == null) return;
        AdventureDataManager.GuildData guild = daily(team); AdventureQuestTemplate quest = template(guild.activeQuest());
        if (quest == null || guild.ready() || !config.questWorld().equals(player.getWorld().getName())) return;
        double distance = horizontalDistance(player.getLocation(), guild.activeX(), guild.activeZ());
        if (quest.type() == AdventureQuestType.EXPLORE && distance <= config.exploreRadius()) progress(player, AdventureQuestType.EXPLORE, "ANY", 1);
        if (quest.type() == AdventureQuestType.KILL && config.spawnQuestMobs() && !guild.mobsSpawned() && distance <= config.activationRadius()) spawnQuestMobs(team, guild, quest, player.getWorld());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdventureHolder) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem(); if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String quest = clicked.getItemMeta().getPersistentDataContainer().get(questKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "main" -> openMain(player);
            case "daily" -> openDaily(player);
            case "submit" -> openSubmit(player);
            case "professions" -> openProfessions(player);
            case "guide" -> sendGuide(player);
            case "accept" -> { if (quest != null && accept(player, quest)) openSubmit(player); }
            case "claim" -> { if (claim(player)) openSubmit(player); }
            case "team_create" -> { player.closeInventory(); send(player, "team-create-guide", "&eBuat team dengan &f/team create <nama>&e."); }
            case "team_invite" -> { player.closeInventory(); send(player, "team-invite-guide", "&eUndang teman dengan &f/team invite <nama>&e."); }
            default -> { }
        }
    }

    @EventHandler
    public void hideCommands(PlayerCommandSendEvent event) {
        if (!config.hideCommands() || event.getPlayer().hasPermission("veliorasuite.adventure.admin")) return;
        Set<String> hidden = Set.of("vgpetualang", "vgquest", "petualang", "guildquest", "vgteam", "teamgui");
        event.getCommands().removeIf(command -> hidden.contains(command.toLowerCase(Locale.ROOT)));
    }

    private void progress(Player player, AdventureQuestType type, String target, int amount) {
        Team team = team(player); if (team == null || onlineMembers(team) < config.minimumOnlineMembers()) return;
        AdventureDataManager.GuildData guild = daily(team); AdventureQuestTemplate quest = template(guild.activeQuest());
        if (quest == null || quest.type() != type || guild.ready()) return;
        if (expired(guild)) { cleanupQuestMobs(team.getId()); guild.clearActive(); data.save(); refreshQuestBars(); return; }
        if (!quest.target().equalsIgnoreCase("ANY") && !quest.target().equalsIgnoreCase(target)) return;
        if (!config.questWorld().equals(player.getWorld().getName())
                || horizontalDistance(player.getLocation(), guild.activeX(), guild.activeZ()) > config.objectiveRadius()) return;
        if (!allowProgress(player, amount)) return;
        guild.addProgress(player.getUniqueId(), amount);
        if (guild.ready()) broadcast(team, config.prefix() + config.color("&aMisi selesai! Kembali ke NPC misi untuk menyetor hadiah."));
        data.save();
        refreshQuestBars();
    }

    private boolean allowProgress(Player player, int requested) {
        long second = System.currentTimeMillis() / 1000L;
        ProgressWindow window = progressWindows.computeIfAbsent(player.getUniqueId(), ignored -> new ProgressWindow(second));
        if (window.second != second) { window.second = second; window.amount = 0; }
        int accepted = Math.max(0, Math.min(Math.max(0, requested), config.maxProgressPerSecond() - window.amount));
        window.amount += accepted;
        return accepted >= Math.max(1, requested);
    }

    public long professionExp(Player player, AdventureProfession profession) { return profile(player).professionExp(profession); }
    public int professionLevel(long exp) { return Math.min(config.professionMaxLevel(), 1 + (int) (Math.max(0L, exp) / config.professionLevelExp())); }
    private void awardProfession(Player player, AdventureProfession profession, long amount) {
        if (player == null || amount <= 0L || !config.professionsEnabled()) return;
        AdventureDataManager.PlayerData profile = profile(player);
        long before = profile.professionExp(profession);
        profile.addProfessionExp(profession, amount);
        data.save();
        if (professionLevel(before) < professionLevel(profile.professionExp(profession))) {
            player.sendMessage(config.prefix() + config.color("&6Profesi &f" + profession.display() + " &6naik ke level &f" + professionLevel(profile.professionExp(profession)) + "&6!"));
        }
    }
    private boolean isCrop(Material type) { return switch (type) { case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, COCOA, MELON, PUMPKIN -> true; default -> false; }; }

    private AdventureDataManager.GuildData daily(Team team) {
        AdventureDataManager.GuildData guild = data.guild(team.getId());
        ZoneId zone;
        try { zone = ZoneId.of(config.timezone()); }
        catch (DateTimeException ignored) { zone = ZoneId.of("Asia/Jakarta"); }
        String date = LocalDate.now(zone).toString();
        if (date.equals(guild.dailyDate()) && !guild.dailyIds().isEmpty()) return guild;
        int unlockedOrdinal = team.getMembers().keySet().stream()
                .map(uuid -> data.player(uuid, Bukkit.getOfflinePlayer(uuid).getName()))
                .mapToInt(profile -> config.rankFor(profile.exp()).ordinal()).max().orElse(0);
        List<AdventureQuestTemplate> pool = new ArrayList<>(config.templates().stream()
                .filter(quest -> quest.rank().ordinal() <= unlockedOrdinal).toList());
        if (pool.size() < config.dailyQuestCount()) pool = new ArrayList<>(config.templates().stream()
                .filter(quest -> quest.rank() == AdventureRank.F).toList());
        Collections.shuffle(pool, new Random(31L * team.getId() + date.hashCode()));
        List<String> ids = pool.stream().limit(config.dailyQuestCount()).map(AdventureQuestTemplate::id).toList();
        guild.daily(date, ids); data.save(); return guild;
    }

    private AdventureQuestTemplate template(String id) { if (id == null || id.isBlank()) return null; return config.templates().stream().filter(q -> q.id().equalsIgnoreCase(id)).findFirst().orElse(null); }
    private Team team(Player player) {
        TeamModule module = plugin.getModuleManager().getModule("team").filter(TeamModule.class::isInstance).map(TeamModule.class::cast).orElse(null);
        return module == null || module.getTeamManager() == null ? null : module.getTeamManager().getDataManager().getTeamByPlayer(player.getUniqueId());
    }
    private Team requireReadyTeam(Player player, boolean onlineRequired) {
        Team team = team(player);
        if (team == null) { send(player, "team-required", "&cKamu harus memiliki team terlebih dahulu."); return null; }
        if (onlineRequired && onlineMembers(team) < config.minimumOnlineMembers()) {
            send(player, "team-online-required", "&cMinimal &f%amount% &canggota team harus online.", "%amount%", String.valueOf(config.minimumOnlineMembers())); return null;
        }
        return team;
    }
    private int onlineMembers(Team team) { return (int) team.getMembers().keySet().stream().filter(uuid -> Bukkit.getPlayer(uuid) != null).count(); }
    private void broadcast(Team team, String message) { team.getMembers().keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).forEach(player -> player.sendMessage(message)); }

    private void spawnQuestMobs(Team team, AdventureDataManager.GuildData guild, AdventureQuestTemplate quest, World world) {
        EntityType type = quest.entityType(); if (type == null || !type.isAlive()) return;
        int y = world.getHighestBlockYAt(guild.activeX(), guild.activeZ()) + 1;
        Location center = new Location(world, guild.activeX() + .5, y, guild.activeZ() + .5);
        int count = Math.min(quest.amount(), config.maxSpawnedMobs());
        for (int index = 0; index < count; index++) try {
            Location spawn = center.clone().add((index % 5) - 2, 0, (index / 5) - 2);
            Entity entity = world.spawnEntity(spawn, type);
            entity.addScoreboardTag("veliora_adventure_mob"); entity.addScoreboardTag("veliora_adventure_team_" + team.getId());
            if (entity instanceof LivingEntity living) living.setRemoveWhenFarAway(false);
        } catch (IllegalArgumentException ignored) { }
        guild.setMobsSpawned(); data.save();
    }

    private boolean expired(AdventureDataManager.GuildData guild) {
        long now = System.currentTimeMillis();
        if (guild.activeExpires() > 0L && now > guild.activeExpires()) return true;
        return guild.activeLastActivity() > 0L && now - guild.activeLastActivity() >= config.inactivityExpireMinutes() * 60_000L;
    }

    private Location findSafeQuestLocation(World world, Random random) {
        int min = Math.min(config.coordinateMin(), config.coordinateMax());
        int max = Math.max(config.coordinateMin(), config.coordinateMax());
        for (int attempt = 0; attempt < 48; attempt++) {
            int x = random.nextInt(max - min + 1) + min;
            int z = random.nextInt(max - min + 1) + min;
            org.bukkit.block.Block ground = world.getHighestBlockAt(x, z);
            Location feet = ground.getLocation().add(0.5D, 1.0D, 0.5D);
            if (ground.isLiquid() || !ground.getType().isSolid() || !feet.getBlock().isPassable()
                    || !feet.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()) continue;
            return feet;
        }
        return null;
    }

    private void startQuestBars() {
        stopQuestBars();
        questBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshQuestBars, 20L, 20L);
    }

    private void startInactivityChecks() {
        stopInactivityChecks();
        inactivityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::expireInactiveQuests, 20L * 60L, 20L * 60L);
    }

    private void stopInactivityChecks() {
        if (inactivityTask != null) inactivityTask.cancel();
        inactivityTask = null;
    }

    private void expireInactiveQuests() {
        long now = System.currentTimeMillis();
        long inactivityMillis = config.inactivityExpireMinutes() * 60_000L;
        boolean changed = false;
        for (AdventureDataManager.GuildData guild : data.guilds()) {
            if (guild.activeQuest().isBlank() || guild.ready()) continue;
            boolean durationExpired = guild.activeExpires() > 0L && now > guild.activeExpires();
            boolean inactive = guild.activeLastActivity() > 0L && now - guild.activeLastActivity() >= inactivityMillis;
            if (!durationExpired && !inactive) continue;
            cleanupQuestMobs(guild.id());
            guild.clearActive();
            changed = true;
            notifyQuestExpired(guild.id(), inactive);
        }
        if (changed) { data.save(); refreshQuestBars(); }
    }

    private void notifyQuestExpired(int guildId, boolean inactive) {
        TeamModule module = plugin.getModuleManager().getModule("team").filter(TeamModule.class::isInstance).map(TeamModule.class::cast).orElse(null);
        if (module == null || module.getTeamManager() == null) return;
        for (Team team : module.getTeamManager().getDataManager().getTeams()) {
            if (team.getId() != guildId) continue;
            String reason = inactive ? "&cMisi hangus karena tidak ada progres selama 15 menit." : "&cMisi telah kedaluwarsa.";
            broadcast(team, config.prefix() + config.color(reason));
            return;
        }
    }

    private void stopQuestBars() {
        if (questBarTask != null) questBarTask.cancel();
        questBarTask = null;
        questBars.values().forEach(BossBar::removeAll);
        questBars.clear();
    }

    private void refreshQuestBars() {
        Set<UUID> visible = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Team team = team(player);
            AdventureDataManager.GuildData guild = team == null ? null : daily(team);
            AdventureQuestTemplate quest = guild == null ? null : template(guild.activeQuest());
            if (quest == null || guild.activeExpires() > 0 && System.currentTimeMillis() > guild.activeExpires()) continue;
            visible.add(player.getUniqueId());
            BossBar bar = questBars.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID));
            bar.setTitle(config.color("&bMisi: &f" + quest.name() + " &7(" + guild.activeProgress() + "/" + guild.activeTarget() + ")"));
            bar.setProgress(guild.activeTarget() <= 0 ? 0.0D : Math.min(1.0D, (double) guild.activeProgress() / guild.activeTarget()));
            bar.setColor(guild.ready() ? BarColor.GREEN : BarColor.BLUE);
            bar.addPlayer(player);
        }
        questBars.entrySet().removeIf(entry -> {
            if (visible.contains(entry.getKey())) return false;
            entry.getValue().removeAll();
            return true;
        });
    }

    private void cleanupQuestMobs(int teamId) {
        String teamTag = "veliora_adventure_team_" + teamId;
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
            if (!entity.getScoreboardTags().contains("veliora_adventure_mob")) continue;
            if (teamId < 0 || entity.getScoreboardTags().contains(teamTag)) entity.remove();
        }
    }

    private AdventureDataManager.PlayerData profile(Player player) { return data.player(player.getUniqueId(), player.getName()); }
    private int guildLevel(long exp) { return Math.min(30, 1 + (int) Math.floor(Math.sqrt(Math.max(0L, exp) / 500.0D))); }
    private String objective(AdventureQuestTemplate quest) {
        return switch (quest.type()) {
            case KILL -> "Kalahkan " + quest.amount() + " " + quest.target();
            case BREAK -> "Tambang " + quest.amount() + " " + quest.target();
            case FARM -> "Panen " + quest.amount() + " " + quest.target();
            case FISH -> "Tangkap " + quest.amount() + " ikan";
            case BOSS -> "Kalahkan " + quest.amount() + " boss dungeon";
            case EXPLORE -> "Temukan lokasi petualangan";
        };
    }
    private Material icon(AdventureQuestTemplate quest) { return switch (quest.type()) {
        case KILL -> Material.IRON_SWORD; case BREAK -> Material.IRON_PICKAXE; case FARM -> Material.WHEAT;
        case FISH -> Material.FISHING_ROD; case BOSS -> Material.NETHER_STAR; case EXPLORE -> Material.COMPASS;
    }; }
    private ItemStack profileItem(Player player) {
        return item(Material.EXPERIENCE_BOTTLE, "&aProfil Petualang", List.of(
                "&7Rank: " + rank(player), "&7Level: &f" + level(player) + "/" + config.maxLevel(),
                "&7EXP: &f" + exp(player), "&7Misi selesai: &f" + completed(player),
                "&7Guild Level: &f" + guildLevel(player)), "none", "");
    }
    private ItemStack item(Material material, String name, List<String> lore, String action, String quest) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(config.color(name)); meta.setLore(lore.stream().map(config::color).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (quest != null && !quest.isBlank()) meta.getPersistentDataContainer().set(questKey, PersistentDataType.STRING, quest);
        item.setItemMeta(meta); return item;
    }
    private void fill(Inventory inventory, Material material) {
        GuiLayout.decorateMenu(inventory, material, Material.GRAY_STAINED_GLASS_PANE);
    }
    private void sendGuide(Player player) {
        long now = System.currentTimeMillis(), allowed = guideCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (allowed > now) { send(player, "guide-cooldown", "&ePanduan bisa dibuka lagi dalam &f%seconds% detik&e.", "%seconds%", String.valueOf((allowed - now + 999) / 1000)); return; }
        guideCooldowns.put(player.getUniqueId(), now + 300_000L); player.closeInventory();
        for (String line : config.raw().getStringList("messages.guide")) player.sendMessage(config.color(line));
    }
    private void send(Player player, String path, String fallback, String... replacements) {
        String message = config.message(path, fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) message = message.replace(replacements[index], replacements[index + 1]);
        player.sendMessage(config.prefix() + message);
    }
    private void showProgressCelebration(Player player, int oldLevel, AdventureRank oldRank) {
        int newLevel = level(player);
        AdventureRank newRank = standardRank(player);
        if (newRank != oldRank) {
            player.sendTitle(config.color("&6RANK NAIK!"), config.color(config.rankDisplay(newRank)), 5, 40, 12);
            showRankEffect(player, newRank);
        } else if (newLevel > oldLevel) {
            player.sendTitle(config.color("&bLEVEL PETUALANGAN UP!"), config.color("&fLevel " + newLevel), 4, 30, 8);
            plugin.getEffects().particle(player.getLocation().add(0.0D, 1.0D, 0.0D), Particle.HAPPY_VILLAGER, 18, 0.45D, 0.65D, 0.45D, 0.025D);
            plugin.getEffects().sound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75F, 1.15F);
        }
    }
    private void showRankEffect(Player player, AdventureRank rank) {
        Location location = player.getLocation().add(0.0D, 1.0D, 0.0D);
        switch (rank) {
            case F, E -> { player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 18, 0.45D, 0.60D, 0.45D, 0.02D); player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.15F); }
            case D, C -> { player.getWorld().spawnParticle(Particle.END_ROD, location, 28, 0.50D, 0.70D, 0.50D, 0.03D); player.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 1.2F); }
            case B, A -> { player.getWorld().spawnParticle(Particle.ENCHANT, location, 36, 0.65D, 0.80D, 0.65D, 0.35D); player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.0F); }
            case S -> { player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 42, 0.60D, 0.85D, 0.60D, 0.02D); player.playSound(location, Sound.ITEM_TOTEM_USE, 0.72F, 1.05F); }
            case SS -> { player.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 48, 0.60D, 0.90D, 0.60D, 0.02D); player.playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.32F, 1.55F); }
            case SSS -> { player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 54, 0.65D, 0.95D, 0.65D, 0.18D); player.getWorld().spawnParticle(Particle.FIREWORK, location, 3, 0.45D, 0.75D, 0.45D, 0.03D); player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.9F); }
        }
    }
    private void deposit(Player player, int amount) {
        if (amount <= 0) return;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"}) RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return;
            Object economy = registration.getProvider(); Method deposit = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            deposit.invoke(economy, player, (double) amount);
        } catch (Exception exception) { plugin.getLogger().warning("VelioraPetualang: reward Vault gagal untuk " + player.getName()); }
    }
    private double horizontalDistance(Location location, int x, int z) { double dx = location.getX() - x, dz = location.getZ() - z; return Math.sqrt(dx * dx + dz * dz); }
    private String blockKey(Location location) { return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ(); }

    private static final class AdventureHolder implements InventoryHolder {
        private final String type;
        private AdventureHolder(String type) { this.type = type; }
        @Override public Inventory getInventory() { return null; }
    }

    private static final class ProgressWindow {
        private long second;
        private int amount;
        private ProgressWindow(long second) { this.second = second; }
    }
}
