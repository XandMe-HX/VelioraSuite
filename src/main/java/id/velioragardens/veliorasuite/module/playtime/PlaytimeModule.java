package id.velioragardens.veliorasuite.module.playtime;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlaytimeModule implements VelioraModule, Listener {

    private final VelioraSuite plugin;
    private PlaytimeConfigManager config;
    private PlaytimeDataManager data;
    private PlaytimeManager manager;
    private PlaceholderExpansion playtimeExpansion;
    private PlaceholderExpansion velioraExpansion;
    private boolean enabled;

    public PlaytimeModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "playtime"; }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/playtime.yml");
        this.config = new PlaytimeConfigManager(plugin);
        this.data = new PlaytimeDataManager(plugin);
        this.manager = new PlaytimeManager(plugin, config, data);
        config.load();
        manager.load();
    }

    @Override
    public void enable() {
        enabled = config.isEnabled();
        if (!enabled) {
            registerDisabledCommand();
            return;
        }
        PluginCommand command = plugin.getCommand("playtime");
        if (command == null) {
            plugin.getLogger().warning("Command /playtime tidak ditemukan di plugin.yml.");
        } else {
            PlaytimeCommand playtimeCommand = new PlaytimeCommand(manager, config);
            command.setExecutor(playtimeCommand);
            command.setTabCompleter(playtimeCommand);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        manager.start();
        registerPlaceholders();
        plugin.getLogger().info("VelioraPlaytime enabled.");
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        unregisterPlaceholders();
        if (manager != null) manager.shutdown();
        registerDisabledCommand();
        enabled = false;
    }

    @Override
    public void reload() {
        if (config == null || manager == null) load();
        else {
            config.load();
            manager.reload();
        }
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (enabled) manager.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (enabled) manager.onQuit(event.getPlayer());
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        playtimeExpansion = new PlaytimePlaceholderExpansion("playtime", manager);
        velioraExpansion = new PlaytimePlaceholderExpansion("veliorasuite", manager);
        playtimeExpansion.register();
        velioraExpansion.register();
    }

    private void unregisterPlaceholders() {
        if (playtimeExpansion != null) playtimeExpansion.unregister();
        if (velioraExpansion != null) velioraExpansion.unregister();
        playtimeExpansion = null;
        velioraExpansion = null;
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("playtime");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraPlaytime");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
