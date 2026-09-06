package id.velioragardens.veliorasuite.module.actionhouse;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ActionHouseModule implements VelioraModule {
    private static final String[] PROMOTION_TIERS = {"one-day", "three-days", "seven-days", "fourteen-days", "thirty-days"};
    private static final double[] DEFAULT_PROMOTION_PRICES = {5000, 12000, 25000, 50000, 100000};
    private static final int[] DEFAULT_PROMOTION_DAYS = {1, 3, 7, 14, 30};
    private final VelioraSuite plugin;
    private ActionHouseStore store;
    private ActionHouseGui gui;
    private BukkitTask expiry;
    private boolean enabled;
    public ActionHouseModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "actionhouse"; }
    @Override public void load() {
        plugin.saveResourceIfNotExists("modules/actionhouse.yml");
        File file = new File(plugin.getDataFolder(), "modules/actionhouse.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        mergeNewDefaults(file, config);
        int initialSlots = Math.max(1, config.getInt("shop.initial-slots", 8));
        double[] prices = new double[PROMOTION_TIERS.length]; int[] days = new int[PROMOTION_TIERS.length];
        for (int i = 0; i < PROMOTION_TIERS.length; i++) { String path = "promotion." + PROMOTION_TIERS[i]; prices[i] = Math.max(1D, config.getDouble(path + ".price", DEFAULT_PROMOTION_PRICES[i])); days[i] = Math.max(1, config.getInt(path + ".days", DEFAULT_PROMOTION_DAYS[i])); }
        store = new ActionHouseStore(plugin, initialSlots); store.load();
        gui = new ActionHouseGui(plugin, store, new ActionHouseEconomy(plugin), Math.max(1, config.getLong("listing.duration-days", 14)) * 86400000L, Math.max(1D, config.getDouble("shop.slot-price", 5000)), Math.max(1D, config.getDouble("shop.purchase-price", 10000)), prices, days);
    }
    /** Tambahkan key baru tanpa menimpa konfigurasi yang telah diubah owner. */
    private void mergeNewDefaults(File file, YamlConfiguration config) {
        try (InputStream input = plugin.getResource("modules/actionhouse.yml")) {
            if (input == null) return;
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
            config.options().copyDefaults(true); config.save(file);
        } catch (Exception exception) { plugin.getLogger().warning("ActionHouse: default config baru tidak dapat ditambahkan: " + exception.getMessage()); }
    }
    @Override public void enable() { enabled = true; PluginCommand command = plugin.getCommand("actionhouse"); if (command != null) command.setExecutor(new ActionHouseCommand(gui)); plugin.getServer().getPluginManager().registerEvents(gui, plugin); expiry = plugin.getServer().getScheduler().runTaskTimer(plugin, store::expire, 1200L, 1200L); }
    @Override public void disable() { enabled = false; if (expiry != null) expiry.cancel(); if (gui != null) HandlerList.unregisterAll(gui); if (store != null) store.save(); PluginCommand command = plugin.getCommand("actionhouse"); if (command != null) command.setExecutor(new DisabledCommand(plugin, "VelioraActionHouse")); }
    @Override public void reload() { disable(); load(); enable(); }
    @Override public boolean isEnabled() { return enabled; }
}
