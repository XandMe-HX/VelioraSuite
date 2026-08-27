package id.velioragardens.veliorasuite.module.menu;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.warp.WarpModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MenuModule implements VelioraModule, Listener, CommandExecutor {
    private static final int[] TOP_SLOTS = {10,11,12,13,14,15,16,19,20,21};
    private final VelioraSuite plugin;
    private FileConfiguration config;
    private File configFile;
    private boolean enabled;
    private List<Score> playtimeTop = List.of();
    private List<Score> balanceTop = List.of();
    private List<Score> killsTop = List.of();
    private List<Score> deathsTop = List.of();
    private long nextRefresh;

    public MenuModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "menu"; }

    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/menu.yml");
        configFile = new File(plugin.getDataFolder(), "modules/menu.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        migrateOceanTheme();
        migrateMenuV2();
    }

    private void migrateOceanTheme() {
        boolean changed = false;
        if (isLegacyGold(config.getString("settings.prefix"))) {
            config.set("settings.prefix", "&8[&bVeliora&3Menu&8] &r");
            changed = true;
        }
        Map<String, String> titles = Map.of(
                "main", "&1Veliora &bMenu",
                "warps", "&1Veliora &bWarp",
                "ranks", "&1Veliora &bRank",
                "playtime", "&1Top &bPlaytime",
                "balance", "&1Top &bBalance",
                "kills", "&1Top &bKill",
                "deaths", "&1Top &bDeath",
                "team", "&1Veliora &bTeam",
                "rtp", "&1Veliora &bRTP",
                "skills", "&1Veliora &bSkills");
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            String path = "titles." + entry.getKey();
            if (isLegacyGold(config.getString(path))) {
                config.set(path, entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            try {
                config.save(configFile);
                plugin.getLogger().info("Tema lama VelioraMenu dimigrasikan ke Ocean Theme.");
            } catch (java.io.IOException exception) {
                plugin.getLogger().warning("Gagal menyimpan migrasi Ocean Theme: " + exception.getMessage());
            }
        }
    }

    private boolean isLegacyGold(String value) {
        return value != null && value.contains("&6&l[") && value.contains("VELIORA");
    }

    private void migrateMenuV2() {
        if (config.getInt("settings.config-version", 0) >= 2) return;
        try (InputStreamReader reader = new InputStreamReader(
                java.util.Objects.requireNonNull(plugin.getResource("modules/menu.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            config.set("settings.config-version", 2);
            config.set("titles.kills", defaults.getString("titles.kills", "&1Top &bKill"));
            config.set("titles.deaths", defaults.getString("titles.deaths", "&1Top &bDeath"));
            config.set("ranks", null);
            ConfigurationSection ranks = defaults.getConfigurationSection("ranks");
            if (ranks != null) {
                for (Map.Entry<String, Object> entry : ranks.getValues(true).entrySet()) {
                    if (entry.getValue() instanceof ConfigurationSection) continue;
                    config.set("ranks." + entry.getKey(), entry.getValue());
                }
            }
            config.save(configFile);
            plugin.getLogger().info("VelioraMenu diperbarui: rank resmi serta Top Kill/Death aktif.");
        } catch (Exception exception) {
            plugin.getLogger().warning("Gagal migrasi VelioraMenu v2: " + exception.getMessage());
        }
    }

    @Override public void enable() {
        enabled = config.getBoolean("settings.enabled", true);
        if (!enabled) return;
        PluginCommand command = plugin.getCommand("menu");
        if (command != null) command.setExecutor(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTask(plugin, this::refreshLeaderboards);
    }

    @Override public void disable() {
        enabled = false;
        org.bukkit.event.HandlerList.unregisterAll(this);
        PluginCommand command = plugin.getCommand("menu");
        if (command != null) command.setExecutor(new DisabledCommand(plugin, "VelioraMenu"));
    }

    @Override public void reload() { load(); }
    @Override public boolean isEnabled() { return enabled; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya untuk player.");
            return true;
        }
        openMain(player);
        return true;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        switch (holder.page) {
            case MAIN -> clickMain(player, slot);
            case WARPS -> clickWarps(player, holder, slot);
            case RANKS -> clickRank(player, slot);
            case PLAYTIME, BALANCE, KILLS, DEATHS -> clickTop(player, slot);
            case TEAM -> clickTeam(player, slot);
            case RTP -> { if (slot == 22) run(player, "rtp"); else if (slot == 49) openMain(player); }
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        // InventoryHolder tidak menyimpan data player, sehingga tidak ada cache session yang bocor.
    }

    private void openMain(Player player) {
        Holder holder = new Holder(Page.MAIN);
        Inventory gui = inventory(holder, 54, title("main", "&6&l[ &e&lVELIORA &a&lMENU &6&l]"));
        frame(gui);
        gui.setItem(13, profile(player));
        gui.setItem(19, item(Material.EMERALD, "&a&lSHOP", "&7Beli kebutuhan server.", "&eKlik untuk membuka /shop"));
        gui.setItem(21, item(Material.GOLD_INGOT, "&6&lSELL", "&7Jual barang melalui menu.", "&eKlik untuk /sellgui"));
        gui.setItem(23, item(Material.FISHING_ROD, "&b&lFISHING", "&7Tas, koleksi, rod, dan hasil pancing."));
        gui.setItem(25, item(Material.WRITABLE_BOOK, "&e&lQUEST", "&7Quest otomatis dan level aktivitas."));
        gui.setItem(28, item(Material.BONE, "&d&lPETS", "&7Pet milikmu, makan, mastery, dan storage."));
        gui.setItem(30, item(Material.ENCHANTED_BOOK, "&b&lAURASKILLS", "&7Lihat skill, statistik, dan kemampuanmu.", "&eKlik untuk membuka /skills"));
        gui.setItem(32, item(Material.ENDER_PEARL, "&a&lWARP", "&7Lobby, Dungeon, PvP, dan Guild."));
        gui.setItem(34, item(Material.COMPASS, "&6&lRTP", "&7Teleport acak dengan aman."));
        gui.setItem(37, item(Material.CHEST, "&e&lKITS", "&7Buka kit VelioraSuite."));
        gui.setItem(39, item(Material.IRON_AXE, "&a&lFTB SKILLS", "&7Farmer, Tree Feller, dan Vein Miner."));
        gui.setItem(41, item(Material.TRIPWIRE_HOOK, "&d&lKEY SHOP", "&7Hadiah dan key VelioraGacha."));
        gui.setItem(43, item(Material.WHITE_BANNER, "&b&lTEAM", "&7Buat, undang, dan kelola team."));
        gui.setItem(45, item(Material.CLOCK, "&e&lTOP PLAYTIME", "&7Sepuluh pemain paling aktif."));
        gui.setItem(46, item(Material.IRON_SWORD, "&c&lTOP KILL", "&7Sepuluh pemain dengan kill terbanyak."));
        gui.setItem(49, item(Material.NETHER_STAR, "&6&lRANK", "&7Lihat seluruh rank dan manfaatnya."));
        gui.setItem(52, item(Material.SKELETON_SKULL, "&7&lTOP DEATH", "&7Sepuluh pemain dengan kematian terbanyak."));
        gui.setItem(53, item(Material.GOLD_BLOCK, "&a&lTOP BALANCE", "&7Sepuluh saldo tertinggi."));
        player.openInventory(gui);
    }

    private void clickMain(Player player, int slot) {
        switch (slot) {
            case 19 -> run(player, "shop");
            case 21 -> run(player, "sellgui");
            case 23 -> run(player, "fish");
            case 25 -> run(player, "quests");
            case 28 -> run(player, "pet");
            case 30 -> run(player, "skills");
            case 32 -> openWarps(player);
            case 34 -> openRtp(player);
            case 37 -> run(player, "kits");
            case 39 -> run(player, "ftb");
            case 41 -> run(player, "key shop");
            case 43 -> openTeam(player);
            case 45 -> openTop(player, Leaderboard.PLAYTIME);
            case 46 -> openTop(player, Leaderboard.KILLS);
            case 49 -> openRanks(player);
            case 52 -> openTop(player, Leaderboard.DEATHS);
            case 53 -> openTop(player, Leaderboard.BALANCE);
            default -> { }
        }
    }

    private ItemStack profile(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("&8Profil Veliora Gardens");
        lore.add("");
        lore.add("&7Rank: &f" + primaryGroup(player));
        lore.add("&7Level: &a" + level(player));
        lore.add("&7Uang: &e$" + format(balance(player)));
        lore.add("&7Kill / Mati: &c" + player.getStatistic(Statistic.PLAYER_KILLS) + " &8/ &7" + player.getStatistic(Statistic.DEATHS));
        lore.add("&7Playtime: &f" + formatTime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        lore.add("&7Ping: &a" + player.getPing() + "ms");
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(color("&8[&bProfil &f" + player.getName() + "&8]"));
        meta.setLore(color(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private void openWarps(Player player) {
        Holder holder = new Holder(Page.WARPS);
        WarpModule module = module("warp", WarpModule.class);
        List<String> names = module == null || module.getManager() == null
                ? List.of() : new ArrayList<>(module.getManager().warpNames());
        int size = Math.min(54, Math.max(27, ((names.size() + 8) / 9 + 2) * 9));
        Inventory gui = inventory(holder, size, title("warps", "&1Veliora &bWarp"));
        frame(gui);
        int slot = 10;
        for (String name : names) {
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= size - 9) break;
            Material icon = switch (name) {
                case "lobby" -> Material.GRASS_BLOCK;
                case "dungeon" -> Material.SPAWNER;
                case "pvp" -> Material.DIAMOND_SWORD;
                case "guild" -> Material.WHITE_BANNER;
                case "fishing" -> Material.FISHING_ROD;
                default -> Material.ENDER_PEARL;
            };
            gui.setItem(slot, item(icon, "&b&l" + name.toUpperCase(Locale.ROOT),
                    "&7Warp tersimpan dan siap digunakan.", "&eKlik untuk teleport."));
            holder.actions.put(slot, name);
            slot++;
        }
        gui.setItem(size - 5, back());
        player.openInventory(gui);
    }

    private void addWarp(Inventory gui, int slot, Material icon, String name, String title) {
        boolean ready = warpReady(name);
        gui.setItem(slot, item(ready ? icon : Material.BARRIER, title,
                ready ? "&7Tujuan sudah aktif." : "&cBelum diset dengan /vgwarp set " + name,
                ready ? "&eKlik untuk teleport." : "&8Admin perlu mengatur lokasinya."));
    }

    private void clickWarps(Player player, Holder holder, int slot) {
        if (slot == holder.inventory.getSize() - 5) { openMain(player); return; }
        String warp = holder.actions.get(slot);
        if (warp == null) return;
        WarpModule module = module("warp", WarpModule.class);
        if (module == null || module.getManager() == null || !module.getManager().hasDirectAlias(warp)) {
            player.sendMessage(prefix() + color("&cWarp " + warp + " belum diset."));
            return;
        }
        player.closeInventory();
        module.getManager().teleport(player, warp);
    }

    private void openRtp(Player player) {
        Holder holder = new Holder(Page.RTP);
        Inventory gui = inventory(holder, 54, title("rtp", "&6&l[ &e&lVELIORA &a&lRTP &6&l]"));
        frame(gui);
        gui.setItem(22, item(Material.COMPASS, "&a&lRANDOM TELEPORT", "&7Cari lokasi survival acak.", "&eKlik untuk menjalankan /rtp"));
        gui.setItem(49, back());
        player.openInventory(gui);
    }


    private void openTeam(Player player) {
        Holder holder = new Holder(Page.TEAM);
        Inventory gui = inventory(holder, 27, title("team", "&6&l[ &e&lVELIORA &a&lTEAM &6&l]"));
        fill(gui);
        gui.setItem(10, item(Material.ANVIL, "&a&lCREATE TEAM", "&7Buat team baru.", "&eKlik untuk melihat cara membuat."));
        gui.setItem(13, item(Material.BOOK, "&b&lTEAM INFO", "&7Lihat informasi team milikmu."));
        gui.setItem(16, item(Material.PAPER, "&e&lTEAM LIST", "&7Lihat daftar team."));
        gui.setItem(22, back());
        player.openInventory(gui);
    }

    private void clickTeam(Player player, int slot) {
        if (slot == 22) { openMain(player); return; }
        if (slot == 10) {
            player.closeInventory();
            player.sendMessage(prefix() + color("&aKetik &f/team create <nama> &auntuk membuat team."));
        } else if (slot == 13) run(player, "team info");
        else if (slot == 16) run(player, "team list");
    }

    private void openRanks(Player player) {
        Holder holder = new Holder(Page.RANKS);
        Inventory gui = inventory(holder, 54, title("ranks", "&6&l[ &e&lVELIORA &a&lRANK &6&l]"));
        frame(gui);
        ConfigurationSection ranks = config.getConfigurationSection("ranks");
        if (ranks != null) {
            int slot = 10;
            for (String key : ranks.getKeys(false)) {
                if (slot == 17) slot = 19;
                if (slot > 43) break;
                String path = "ranks." + key + ".";
                Material material = Material.matchMaterial(config.getString(path + "material", "NAME_TAG"));
                gui.setItem(slot++, item(material == null ? Material.NAME_TAG : material,
                        config.getString(path + "name", key), config.getStringList(path + "description")));
            }
        }
        gui.setItem(49, back());
        player.openInventory(gui);
    }

    private void clickRank(Player player, int slot) {
        if (slot == 49) { openMain(player); return; }
        ConfigurationSection ranks = config.getConfigurationSection("ranks");
        if (ranks == null) return;
        int index = slot >= 19 ? slot - 12 : slot - 10;
        List<String> keys = new ArrayList<>(ranks.getKeys(false));
        if (index < 0 || index >= keys.size()) return;
        String key = keys.get(index);
        player.closeInventory();
        player.sendMessage(prefix() + color(config.getString("ranks." + key + ".name", key)));
        for (String line : config.getStringList("ranks." + key + ".description")) player.sendMessage(color(" &8- &7" + line));
    }

    private void openTop(Player player, Leaderboard board) {
        refreshIfExpired();
        Page page = switch (board) {
            case PLAYTIME -> Page.PLAYTIME;
            case BALANCE -> Page.BALANCE;
            case KILLS -> Page.KILLS;
            case DEATHS -> Page.DEATHS;
        };
        Holder holder = new Holder(page);
        Inventory gui = inventory(holder, 54, title(board.configKey, board.fallbackTitle));
        frame(gui);
        List<Score> scores = scores(board);
        for (int i = 0; i < Math.min(10, scores.size()); i++) {
            Score score = scores.get(i);
            String value = formatScore(board, score.value);
            gui.setItem(TOP_SLOTS[i], item(i == 0 ? Material.GOLD_INGOT : Material.PAPER,
                    "&e&l#" + (i + 1) + " &f" + score.name, "&7" + value));
        }
        int ownRank = ownRank(player, board);
        double ownValue = statistic(player, board);
        gui.setItem(49, item(Material.PLAYER_HEAD, "&a&lPOSISIMU",
                "&7Peringkat: &f#" + (ownRank < 1 ? "-" : ownRank),
                "&7Nilai: &f" + formatScore(board, ownValue),
                "&8Data diperbarui berkala agar server tetap ringan."));
        gui.setItem(45, back());
        player.openInventory(gui);
    }

    private void clickTop(Player player, int slot) {
        if (slot == 45) openMain(player);
    }

    private void refreshIfExpired() {
        if (System.currentTimeMillis() >= nextRefresh) refreshLeaderboards();
    }

    private void refreshLeaderboards() {
        List<Score> play = new ArrayList<>();
        List<Score> money = new ArrayList<>();
        List<Score> kills = new ArrayList<>();
        List<Score> deaths = new ArrayList<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name == null || name.isBlank()) continue;
            play.add(new Score(offline.getUniqueId(), name, offline.getStatistic(Statistic.PLAY_ONE_MINUTE)));
            money.add(new Score(offline.getUniqueId(), name, balance(offline)));
            kills.add(new Score(offline.getUniqueId(), name, offline.getStatistic(Statistic.PLAYER_KILLS)));
            deaths.add(new Score(offline.getUniqueId(), name, offline.getStatistic(Statistic.DEATHS)));
        }
        Comparator<Score> descending = Comparator.comparingDouble(Score::value).reversed();
        play.sort(descending);
        money.sort(descending);
        kills.sort(descending);
        deaths.sort(descending);
        playtimeTop = List.copyOf(play.subList(0, Math.min(10, play.size())));
        balanceTop = List.copyOf(money.subList(0, Math.min(10, money.size())));
        killsTop = List.copyOf(kills.subList(0, Math.min(10, kills.size())));
        deathsTop = List.copyOf(deaths.subList(0, Math.min(10, deaths.size())));
        nextRefresh = System.currentTimeMillis() + Math.max(60, config.getInt("leaderboards.cache-seconds", 300)) * 1000L;
    }

    private int ownRank(Player player, Leaderboard board) {
        List<Score> all = new ArrayList<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name == null) continue;
            all.add(new Score(offline.getUniqueId(), name, statistic(offline, board)));
        }
        all.sort(Comparator.comparingDouble(Score::value).reversed());
        for (int i = 0; i < all.size(); i++) if (all.get(i).uuid.equals(player.getUniqueId())) return i + 1;
        return -1;
    }

    private List<Score> scores(Leaderboard board) {
        return switch (board) {
            case PLAYTIME -> playtimeTop;
            case BALANCE -> balanceTop;
            case KILLS -> killsTop;
            case DEATHS -> deathsTop;
        };
    }

    private double statistic(OfflinePlayer player, Leaderboard board) {
        return switch (board) {
            case PLAYTIME -> player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            case BALANCE -> balance(player);
            case KILLS -> player.getStatistic(Statistic.PLAYER_KILLS);
            case DEATHS -> player.getStatistic(Statistic.DEATHS);
        };
    }

    private String formatScore(Leaderboard board, double value) {
        return switch (board) {
            case PLAYTIME -> formatTime((long) value);
            case BALANCE -> "$" + format(value);
            case KILLS -> (long) value + " kill";
            case DEATHS -> (long) value + " death";
        };
    }

    private int level(Player player) {
        QuestModule module = module("quest", QuestModule.class);
        if (module == null || module.getQuestManager() == null) return 1;
        int total = 0;
        int count = 0;
        for (PlayerCategoryProgress progress : module.getQuestManager().getDataManager().getOrCreate(player).getCategories().values()) {
            total += progress.getLevel();
            count++;
        }
        return count == 0 ? 1 : Math.max(1, Math.round((float) total / count));
    }

    private boolean warpReady(String name) {
        WarpModule module = module("warp", WarpModule.class);
        return module != null && module.getManager() != null && module.getManager().hasDirectAlias(name);
    }

    private String primaryGroup(Player player) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user != null) return String.valueOf(user.getClass().getMethod("getPrimaryGroup").invoke(user));
        } catch (ReflectiveOperationException ignored) { }
        return "member";
    }

    private double balance(Player player) { return balance((OfflinePlayer) player); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private double balance(OfflinePlayer player) {
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economy);
            if (registration == null) return 0D;
            Object provider = registration.getProvider();
            Method method = provider.getClass().getMethod("getBalance", OfflinePlayer.class);
            return ((Number) method.invoke(provider, player)).doubleValue();
        } catch (ReflectiveOperationException | LinkageError ignored) { return 0D; }
    }

    private void run(Player player, String command) {
        player.closeInventory();
        if (!player.performCommand(command)) {
            String root = command.split(" ", 2)[0];
            player.sendMessage(prefix() + color("&cFitur /" + root + " belum tersedia atau pluginnya belum aktif."));
        }
    }

    private Inventory inventory(Holder holder, int size, String title) {
        Inventory result = Bukkit.createInventory(holder, size, color(title));
        holder.inventory = result;
        return result;
    }

    private void frame(Inventory gui) {
        ItemStack dark = pane(Material.BLUE_STAINED_GLASS_PANE);
        ItemStack gold = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < gui.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == gui.getSize() / 9 - 1 || col == 0 || col == 8) gui.setItem(slot, (slot % 2 == 0) ? gold : dark);
        }
    }

    private void fill(Inventory gui) {
        ItemStack pane = pane(Material.BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, pane);
    }

    private ItemStack pane(Material material) { return item(material, " "); }
    private ItemStack back() { return item(Material.ARROW, "&c&lKEMBALI", "&7Kembali ke menu utama."); }
    private ItemStack item(Material material, String name, String... lore) { return item(material, name, List.of(lore)); }
    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(color(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private String title(String key, String fallback) { return config.getString("titles." + key, fallback); }
    private String prefix() { return color(config.getString("settings.prefix", "&7[&eVELIORA &aMENU&7] &r")); }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
    private List<String> color(List<String> values) { return values.stream().map(this::color).toList(); }
    private String format(double value) { return String.format(Locale.US, "%,.0f", value); }
    private String formatTime(long ticks) {
        long seconds = Math.max(0L, ticks / 20L), days = seconds / 86400L, hours = (seconds % 86400L) / 3600L, minutes = (seconds % 3600L) / 60L;
        return days > 0 ? days + "h " + hours + "j" : hours > 0 ? hours + "j " + minutes + "m" : minutes + "m";
    }

    private <T> T module(String name, Class<T> type) {
        return plugin.getModuleManager().getModule(name).filter(type::isInstance).map(type::cast).orElse(null);
    }

    private enum Page { MAIN, WARPS, RANKS, PLAYTIME, BALANCE, KILLS, DEATHS, TEAM, RTP }
    private enum Leaderboard {
        PLAYTIME("playtime", "&1Top &bPlaytime"),
        BALANCE("balance", "&1Top &bBalance"),
        KILLS("kills", "&1Top &bKill"),
        DEATHS("deaths", "&1Top &bDeath");

        private final String configKey;
        private final String fallbackTitle;
        Leaderboard(String configKey, String fallbackTitle) {
            this.configKey = configKey;
            this.fallbackTitle = fallbackTitle;
        }
    }
    private static final class Holder implements InventoryHolder {
        private final Page page;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;
        private Holder(Page page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private record Score(UUID uuid, String name, double value) { }
}

