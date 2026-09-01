package id.velioragardens.veliorasuite.module.gacha;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.gui.GuiLayout;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GachaManager implements Listener, CommandExecutor, TabCompleter {
    private static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final VelioraSuite plugin;
    private final org.bukkit.NamespacedKey offerKey;
    private final Set<UUID> purchasing = new HashSet<>();
    private final Map<UUID, Long> lastPurchase = new HashMap<>();
    private FileConfiguration config;
    private ExcellentCratesBridge bridge;
    private List<GachaOffer> offers = List.of();

    public GachaManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.offerKey = new org.bukkit.NamespacedKey(plugin, "gacha_offer");
    }

    public void load() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/gacha.yml"));
        Plugin crates = Bukkit.getPluginManager().getPlugin("ExcellentCrates");
        bridge = new ExcellentCratesBridge(crates);
        refreshOffers();
    }

    public boolean enabled() { return config != null && config.getBoolean("settings.enabled", true); }

    private void refreshOffers() {
        if (bridge == null || !bridge.available()) { offers = List.of(); return; }
        Map<String, String> overrides = strings("key-overrides");
        Map<String, Long> prices = longs("prices");
        List<GachaOffer> found = new ArrayList<>(bridge.discover(overrides, prices, Math.max(0L, config.getLong("settings.default-key-price", 500L)), config.getBoolean("settings.require-virtual-keys", true)));
        List<String> selected = config.getStringList("enabled-crates");
        if (!selected.isEmpty()) {
            Map<String, Integer> order = new HashMap<>();
            for (int i = 0; i < selected.size(); i++) order.put(selected.get(i).toLowerCase(Locale.ROOT), i);
            found.removeIf(offer -> !order.containsKey(offer.crateId().toLowerCase(Locale.ROOT)));
            found.sort(Comparator.comparingInt(offer -> order.get(offer.crateId().toLowerCase(Locale.ROOT))));
        } else found.sort(Comparator.comparing(GachaOffer::crateId, String.CASE_INSENSITIVE_ORDER));
        offers = List.copyOf(found);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Hanya player."); return true; }
        String action = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("reload") && player.hasPermission("veliorasuite.gacha.admin")) {
            load(); player.sendMessage(color(prefix() + "&aGacha key shop direload. &7Crate valid: &f" + offers.size())); return true;
        }
        if (action.equals("status") && player.hasPermission("veliorasuite.gacha.admin")) {
            player.sendMessage(color(prefix() + "&7ExcellentCrates: " + (bridge != null && bridge.available() ? "&aaktif" : "&cmati") + " &8| &7Crate valid: &f" + offers.size())); return true;
        }
        if (!player.hasPermission("veliorasuite.gacha.use")) { player.sendMessage(color(prefix() + "&cKamu tidak punya izin.")); return true; }
        open(player, 0);
        return true;
    }

    public void open(Player player) { open(player, 0); }
    private void open(Player player, int page) {
        refreshOffers();
        if (offers.isEmpty()) {
            player.sendMessage(color(prefix() + message("no-crates", "&cExcellentCrates belum aktif atau tidak ada key yang cocok.")));
            return;
        }
        int pages = Math.max(1, (offers.size() + CONTENT.length - 1) / CONTENT.length);
        int current = Math.max(0, Math.min(page, pages - 1));
        GachaHolder holder = new GachaHolder("list", null, current);
        Inventory inventory = Bukkit.createInventory(holder, 54, color(config.getString("settings.title", "&8Veliora &dGacha Key Shop")));
        holder.inventory = inventory;
        GuiLayout.decorateMenu(inventory, Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
        int start = current * CONTENT.length;
        for (int index = start; index < Math.min(start + CONTENT.length, offers.size()); index++) {
            GachaOffer offer = offers.get(index);
            inventory.setItem(CONTENT[index - start], crateItem(offer, index, "confirm"));
        }
        inventory.setItem(45, item(current > 0 ? Material.ARROW : Material.GRAY_DYE, current > 0 ? "&e&lHALAMAN SEBELUMNYA" : "&8Halaman pertama", List.of("&7Halaman " + (current + 1) + " dari " + pages), current > 0 ? "page:" + (current - 1) : null));
        inventory.setItem(49, item(Material.PAPER, "&e&lINFO", List.of("&7Crate valid: &f" + offers.size(), "&7Halaman " + (current + 1) + " dari " + pages), null));
        inventory.setItem(53, item(current + 1 < pages ? Material.ARROW : Material.GRAY_DYE, current + 1 < pages ? "&e&lHALAMAN BERIKUTNYA" : "&8Halaman terakhir", List.of("&7Halaman " + (current + 1) + " dari " + pages), current + 1 < pages ? "page:" + (current + 1) : null));
        player.openInventory(inventory);
    }

    private void confirm(Player player, String crateId) {
        GachaOffer offer = offer(crateId);
        if (offer == null) { open(player); return; }
        GachaHolder holder = new GachaHolder("confirm", offer.crateId(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&8Konfirmasi Pembelian Key"));
        holder.inventory = inventory;
        GuiLayout.decorateMenu(inventory, Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
        inventory.setItem(13, crateItem(offer, indexOf(offer), null));
        inventory.setItem(11, item(Material.LIME_WOOL, "&a&lBELI 1 KEY", List.of("&7Harga: &e" + money(offer.price()), "&7Key diberikan sebagai &fvirtual key&7.", "&eKlik untuk konfirmasi."), "buy:" + offer.crateId()));
        inventory.setItem(15, item(Material.RED_WOOL, "&c&lBATAL", List.of("&7Kembali ke daftar crate."), "back"));
        player.openInventory(inventory);
    }

    private void purchase(Player player, String crateId) {
        GachaOffer offer = offer(crateId);
        if (offer == null || bridge == null || !bridge.available()) { player.sendMessage(color(prefix() + message("failed", "&cKey gagal diberikan; uangmu tidak dipotong."))); return; }
        long now = System.currentTimeMillis();
        if (!purchasing.add(player.getUniqueId()) || now - lastPurchase.getOrDefault(player.getUniqueId(), 0L) < Math.max(250L, config.getLong("settings.purchase-cooldown-millis", 900L))) return;
        try {
            if (!economyReady()) { player.sendMessage(color(prefix() + message("no-economy", "&cVault Economy belum aktif."))); return; }
            if (!hasMoney(player, offer.price())) { player.sendMessage(color(prefix() + message("not-enough-money", "&cUangmu tidak cukup. Butuh &f%price%&c.").replace("%price%", money(offer.price())))); return; }
            if (!withdraw(player, offer.price())) { player.sendMessage(color(prefix() + message("failed", "&cTransaksi gagal; uangmu aman."))); return; }
            if (!bridge.giveKey(player, offer.keyId())) { deposit(player, offer.price()); player.sendMessage(color(prefix() + message("failed", "&cKey gagal diberikan; uangmu tidak dipotong."))); return; }
            lastPurchase.put(player.getUniqueId(), now);
            player.sendMessage(color(prefix() + message("success", "&aKamu membeli 1 key &f%crate% &aseharga &e%price%&a.").replace("%crate%", offer.displayName()).replace("%price%", money(offer.price()))));
            player.closeInventory();
        } finally { purchasing.remove(player.getUniqueId()); }
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof GachaHolder holder)) return;
        event.setCancelled(true);
        ItemStack stack = event.getCurrentItem();
        if (stack == null || !stack.hasItemMeta()) return;
        String action = stack.getItemMeta().getPersistentDataContainer().get(offerKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("back")) open(player);
        else if (action.startsWith("page:")) { try { open(player, Integer.parseInt(action.substring(5))); } catch (NumberFormatException ignored) { open(player); } }
        else if (action.startsWith("confirm:")) confirm(player, action.substring(8));
        else if (action.startsWith("buy:")) purchase(player, action.substring(4));
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof GachaHolder) event.setCancelled(true); }

    private ItemStack crateItem(GachaOffer offer, int index, String action) {
        ItemStack stack = offer.icon() == null || offer.icon().getType().isAir() ? new ItemStack(Material.ENDER_CHEST) : offer.icon().clone();
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(gradient(strip(offer.displayName()), index));
        List<String> lore = new ArrayList<>();
        lore.add(color("&8━━━━━━━━━━━━━━━━━━━━"));
        lore.add(color("&7Crate ID: &f" + offer.crateId()));
        lore.add(color("&7Key ID: &f" + offer.keyId()));
        lore.add(color("&7Harga 1 key: &e" + money(offer.price())));
        lore.add(color(offer.virtualKey() ? "&7Tipe key: &bVirtual &8(tersimpan di ExcellentCrates)" : "&7Tipe key: &eFisik &8(masuk ke inventory)"));
        lore.add(color("&8━━━━━━━━━━━━━━━━━━━━"));
        if (action != null) lore.add(color("&aKlik untuk membeli key ini."));
        meta.setLore(lore);
        if (action != null) meta.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, action.equals("confirm") ? "confirm:" + offer.crateId() : action);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(color(name)); meta.setLore(lore.stream().map(this::color).toList()); if (action != null) meta.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, action); stack.setItemMeta(meta); return stack;
    }
    private GachaOffer offer(String id) { return offers.stream().filter(offer -> offer.crateId().equalsIgnoreCase(id)).findFirst().orElse(null); }
    private int indexOf(GachaOffer offer) { return Math.max(0, offers.indexOf(offer)); }
    private String prefix() { return config.getString("messages.prefix", "&8[&dVELIORA &5GACHA&8] "); }
    private String message(String path, String fallback) { return config.getString("messages." + path, fallback); }
    private String money(long amount) { return String.format(Locale.US, "%,d", amount).replace(',', '.'); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
    private String strip(String text) { return ChatColor.stripColor(color(text)).replaceAll("<[^>]{1,64}>", ""); }

    private String gradient(String text, int index) {
        List<Map<?, ?>> gradients = config.getMapList("gradients");
        if (gradients.isEmpty() || text == null || text.isEmpty()) return color("&d" + text);
        Map<?, ?> gradient = gradients.get(Math.floorMod(index, gradients.size()));
        int from = hex(String.valueOf(gradient.get("from")), 0xFF53C9);
        int to = hex(String.valueOf(gradient.get("to")), 0x9D6CFF);
        StringBuilder out = new StringBuilder();
        int visible = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) { int rgb = mix(from, to, i / (double) visible); out.append(hexColor(rgb)).append(text.charAt(i)); }
        return out.toString();
    }
    private int hex(String input, int fallback) { try { return Integer.parseInt(input.replace("#", ""), 16) & 0xFFFFFF; } catch (RuntimeException ignored) { return fallback; } }
    private int mix(int from, int to, double t) { int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t); int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t); int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t); return (r << 16) | (g << 8) | b; }
    private String hexColor(int rgb) { String hex = String.format("%06X", rgb); return "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§" + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5); }
    private Map<String, String> strings(String path) { Map<String, String> out = new HashMap<>(); if (config.isConfigurationSection(path)) for (String key : config.getConfigurationSection(path).getKeys(false)) out.put(key.toLowerCase(Locale.ROOT), config.getString(path + "." + key, "")); return out; }
    private Map<String, Long> longs(String path) { Map<String, Long> out = new HashMap<>(); if (config.isConfigurationSection(path)) for (String key : config.getConfigurationSection(path).getKeys(false)) out.put(key.toLowerCase(Locale.ROOT), Math.max(0L, config.getLong(path + "." + key))); return out; }

    private Object economy() { try { if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null; Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy"); @SuppressWarnings({"rawtypes", "unchecked"}) RegisteredServiceProvider<?> service = Bukkit.getServicesManager().getRegistration((Class) type); return service == null ? null : service.getProvider(); } catch (ReflectiveOperationException ignored) { return null; } }
    private boolean economyReady() { return economy() != null; }
    private boolean hasMoney(Player player, long amount) { try { Object economy = economy(); return economy != null && (boolean) economy.getClass().getMethod("has", OfflinePlayer.class, double.class).invoke(economy, player, (double) amount); } catch (ReflectiveOperationException ignored) { return false; } }
    private boolean withdraw(Player player, long amount) { return transaction(player, amount, "withdrawPlayer"); }
    private void deposit(Player player, long amount) { transaction(player, amount, "depositPlayer"); }
    private boolean transaction(Player player, long amount, String method) { try { Object economy = economy(); if (economy == null) return false; Object response = economy.getClass().getMethod(method, OfflinePlayer.class, double.class).invoke(economy, player, (double) amount); Field field = response.getClass().getField("transactionSuccess"); return field.getBoolean(response); } catch (ReflectiveOperationException ignored) { return false; } }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return args.length == 1 && sender.hasPermission("veliorasuite.gacha.admin") ? List.of("reload", "status") : List.of(); }

    private static final class GachaHolder implements InventoryHolder {
        private final String type; private final String crateId; private final int page; private Inventory inventory;
        private GachaHolder(String type, String crateId, int page) { this.type = type; this.crateId = crateId; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
