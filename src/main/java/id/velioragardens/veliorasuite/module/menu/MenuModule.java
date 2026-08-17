package id.velioragardens.veliorasuite.module.menu;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.quest.model.PlayerCategoryProgress;
import id.velioragardens.veliorasuite.module.skills.SkillsModule;
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
import java.lang.reflect.Method;
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
    private boolean enabled;
    private List<Score> playtimeTop = List.of();
    private List<Score> balanceTop = List.of();
    private long nextRefresh;

    public MenuModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "menu"; }

    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/menu.yml");
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/menu.yml"));
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
            case WARPS -> clickWarps(player, slot);
            case RANKS -> clickRank(player, slot);
            case PLAYTIME -> clickTop(player, slot, true);
            case BALANCE -> clickTop(player, slot, false);
            case TEAM -> clickTeam(player, slot);
            case RTP -> { if (slot == 22) run(player, "rtp"); else if (slot == 49) openMain(player); }
            case SKILLS -> clickSkills(player, slot);
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
        gui.setItem(30, item(Material.BEACON, "&b&lMANA & SKILLS", "&7Gunakan Mana untuk kemampuan aktif."));
        gui.setItem(32, item(Material.ENDER_PEARL, "&a&lWARP", "&7Lobby, Dungeon, PvP, dan Guild."));
        gui.setItem(34, item(Material.COMPASS, "&6&lRTP", "&7Teleport acak dengan aman."));
        gui.setItem(37, item(Material.CHEST, "&e&lKITS", "&7Buka kit VelioraSuite."));
        gui.setItem(39, item(Material.IRON_AXE, "&a&lFTB SKILLS", "&7Farmer, Tree Feller, dan Vein Miner."));
        gui.setItem(41, item(Material.TRIPWIRE_HOOK, "&d&lKEY SHOP", "&7Hadiah dan key VelioraGacha."));
        gui.setItem(43, item(Material.WHITE_BANNER, "&b&lTEAM", "&7Buat, undang, dan kelola team."));
        gui.setItem(45, item(Material.CLOCK, "&e&lTOP PLAYTIME", "&7Sepuluh pemain paling aktif."));
        gui.setItem(49, item(Material.NETHER_STAR, "&6&lRANK", "&7Lihat seluruh rank dan manfaatnya."));
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
            case 30 -> openSkills(player);
            case 32 -> openWarps(player);
            case 34 -> openRtp(player);
            case 37 -> run(player, "kits");
            case 39 -> run(player, "ftb");
            case 41 -> run(player, "key shop");
            case 43 -> openTeam(player);
            case 45 -> openTop(player, true);
            case 49 -> openRanks(player);
            case 53 -> openTop(player, false);
            default -> { }
        }
    }

    private ItemStack profile(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("&8Profil Veliora Gardens");
        lore.add("");
        lore.add("&7Rank: &f" + primaryGroup(player));
        lore.add("&7Level: &a" + level(player));
        lore.add("&7Mana: &b" + mana(player));
        lore.add("&7Uang: &e$" + format(balance(player)));
        lore.add("&7Kill / Mati: &c" + player.getStatistic(Statistic.PLAYER_KILLS) + " &8/ &7" + player.getStatistic(Statistic.DEATHS));
        lore.add("&7Playtime: &f" + formatTime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        lore.add("&7Ping: &a" + player.getPing() + "ms");
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(color("&6&l[ &e&lPROFIL &a&l" + player.getName() + " &6&l]"));
        meta.setLore(color(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private void openWarps(Player player) {
        Holder holder = new Holder(Page.WARPS);
        Inventory gui = inventory(holder, 27, title("warps", "&6&l[ &e&lVELIORA &a&lWARP &6&l]"));
        fill(gui);
        addWarp(gui, 10, Material.GRASS_BLOCK, "lobby", "&a&lLOBBY");
        addWarp(gui, 12, Material.SPAWNER, "dungeon", "&c&lDUNGEON");
        addWarp(gui, 14, Material.DIAMOND_SWORD, "pvp", "&e&lPVP");
        addWarp(gui, 16, Material.WHITE_BANNER, "guild", "&b&lGUILD");
        gui.setItem(22, back());
        player.openInventory(gui);
    }

    private void addWarp(Inventory gui, int slot, Material icon, String name, String title) {
        boolean ready = warpReady(name);
        gui.setItem(slot, item(ready ? icon : Material.BARRIER, title,
                ready ? "&7Tujuan sudah aktif." : "&cBelum diset dengan /vgwarp set " + name,
                ready ? "&eKlik untuk teleport." : "&8Admin perlu mengatur lokasinya."));
    }

    private void clickWarps(Player player, int slot) {
        if (slot == 22) { openMain(player); return; }
        String warp = switch (slot) { case 10 -> "lobby"; case 12 -> "dungeon"; case 14 -> "pvp"; case 16 -> "guild"; default -> null; };
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

    private void openSkills(Player player) {
        Holder holder = new Holder(Page.SKILLS);
        Inventory gui = inventory(holder, 27, title("skills", "&6&l[ &e&lVELIORA &a&lSKILLS &6&l]"));
        fill(gui);
        gui.setItem(10, item(Material.GOLDEN_PICKAXE, "&e&lMINER FOCUS", "&7Haste selama 30 detik.", "&bBiaya: 25 Mana"));
        gui.setItem(12, item(Material.SHIELD, "&a&lGUARDIAN", "&7Regeneration selama 10 detik.", "&bBiaya: 35 Mana"));
        gui.setItem(14, item(Material.FEATHER, "&f&lDASH", "&7Meluncur ke arah pandangan.", "&bBiaya: 18 Mana"));
        gui.setItem(16, item(Material.FISHING_ROD, "&b&lFISHER FOCUS", "&7Luck selama 60 detik.", "&bBiaya: 20 Mana"));
        gui.setItem(22, back());
        player.openInventory(gui);
    }

    private void clickSkills(Player player, int slot) {
        if (slot == 22) { openMain(player); return; }
        String ability = switch (slot) { case 10 -> "miner"; case 12 -> "guardian"; case 14 -> "dash"; case 16 -> "fisher"; default -> null; };
        if (ability != null) run(player, "vskills ability " + ability);
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

    private void openTop(Player player, boolean playtime) {
        refreshIfExpired();
        Page page = playtime ? Page.PLAYTIME : Page.BALANCE;
        Holder holder = new Holder(page);
        Inventory gui = inventory(holder, 54, title(playtime ? "playtime" : "balance",
                playtime ? "&6&l[ &e&lTOP &a&lPLAYTIME &6&l]" : "&6&l[ &e&lTOP &a&lBALANCE &6&l]"));
        frame(gui);
        List<Score> scores = playtime ? playtimeTop : balanceTop;
        for (int i = 0; i < Math.min(10, scores.size()); i++) {
            Score score = scores.get(i);
            String value = playtime ? formatTime((long) score.value) : "$" + format(score.value);
            gui.setItem(TOP_SLOTS[i], item(i == 0 ? Material.GOLD_INGOT : Material.PAPER,
                    "&e&l#" + (i + 1) + " &f" + score.name, "&7" + value));
        }
        int ownRank = ownRank(player, playtime);
        double ownValue = playtime ? player.getStatistic(Statistic.PLAY_ONE_MINUTE) : balance(player);
        gui.setItem(49, item(Material.PLAYER_HEAD, "&a&lPOSISIMU",
                "&7Peringkat: &f#" + (ownRank < 1 ? "-" : ownRank),
                "&7Nilai: &f" + (playtime ? formatTime((long) ownValue) : "$" + format(ownValue)),
                "&8Data diperbarui berkala agar server tetap ringan."));
        gui.setItem(45, back());
        player.openInventory(gui);
    }

    private void clickTop(Player player, int slot, boolean playtime) {
        if (slot == 45) openMain(player);
    }

    private void refreshIfExpired() {
        if (System.currentTimeMillis() >= nextRefresh) refreshLeaderboards();
    }

    private void refreshLeaderboards() {
        List<Score> play = new ArrayList<>();
        List<Score> money = new ArrayList<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name == null || name.isBlank()) continue;
            play.add(new Score(offline.getUniqueId(), name, offline.getStatistic(Statistic.PLAY_ONE_MINUTE)));
            money.add(new Score(offline.getUniqueId(), name, balance(offline)));
        }
        Comparator<Score> descending = Comparator.comparingDouble(Score::value).reversed();
        play.sort(descending);
        money.sort(descending);
        playtimeTop = List.copyOf(play.subList(0, Math.min(10, play.size())));
        balanceTop = List.copyOf(money.subList(0, Math.min(10, money.size())));
        nextRefresh = System.currentTimeMillis() + Math.max(60, config.getInt("leaderboards.cache-seconds", 300)) * 1000L;
    }

    private int ownRank(Player player, boolean playtime) {
        List<Score> all = new ArrayList<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name == null) continue;
            all.add(new Score(offline.getUniqueId(), name,
                    playtime ? offline.getStatistic(Statistic.PLAY_ONE_MINUTE) : balance(offline)));
        }
        all.sort(Comparator.comparingDouble(Score::value).reversed());
        for (int i = 0; i < all.size(); i++) if (all.get(i).uuid.equals(player.getUniqueId())) return i + 1;
        return -1;
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

    private String mana(Player player) {
        SkillsModule module = module("skills", SkillsModule.class);
        if (module == null || module.getApi() == null) return "-";
        return module.getApi().getMana(player) + "/" + module.getApi().getMaxMana(player);
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
        ItemStack dark = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack gold = pane(Material.YELLOW_STAINED_GLASS_PANE);
        for (int slot = 0; slot < gui.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == gui.getSize() / 9 - 1 || col == 0 || col == 8) gui.setItem(slot, (slot % 2 == 0) ? gold : dark);
        }
    }

    private void fill(Inventory gui) {
        ItemStack pane = pane(Material.BLACK_STAINED_GLASS_PANE);
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
    private String prefix() { return color(config.getString("settings.prefix", "&6&l[ &e&lVELIORA &a&lMENU &6&l] &r")); }
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

    private enum Page { MAIN, WARPS, RANKS, PLAYTIME, BALANCE, TEAM, RTP, SKILLS }
    private static final class Holder implements InventoryHolder {
        private final Page page;
        private Inventory inventory;
        private Holder(Page page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private record Score(UUID uuid, String name, double value) { }
}

