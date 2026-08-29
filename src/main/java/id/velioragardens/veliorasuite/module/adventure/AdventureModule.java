package id.velioragardens.veliorasuite.module.adventure;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class AdventureModule implements VelioraModule {
    private final VelioraSuite plugin;
    private AdventureManager manager;
    private AuraSkillsAdventureBridge auraSkillsBridge;
    private boolean enabled;

    public AdventureModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "adventure"; }
    @Override public void load() { manager = new AdventureManager(plugin); manager.load(); }
    @Override public void enable() {
        if (!manager.config().raw().getBoolean("settings.enabled", true)) {
            enabled = false;
            manager.shutdown();
            disabled("vgpetualang");
            disabled("vgteam");
            return;
        }
        enabled = true;
        register("vgpetualang", new AdventureCommand(manager, false));
        register("vgteam", new AdventureCommand(manager, true));
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
        auraSkillsBridge = new AuraSkillsAdventureBridge(plugin, manager);
        auraSkillsBridge.enable();
    }
    @Override public void disable() {
        enabled = false;
        if (auraSkillsBridge != null) { auraSkillsBridge.disable(); auraSkillsBridge = null; }
        if (manager != null) { HandlerList.unregisterAll(manager); manager.shutdown(); }
        disabled("vgpetualang"); disabled("vgteam");
    }
    @Override public void reload() { if (manager == null) load(); else manager.reload(); }
    @Override public boolean isEnabled() { return enabled; }
    public AdventureManager getManager() { return manager; }

    private void register(String name, AdventureCommand executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) { plugin.getLogger().warning("Command /" + name + " tidak ditemukan."); return; }
        command.setExecutor(executor); command.setTabCompleter(executor);
    }
    private void disabled(String name) {
        PluginCommand command = plugin.getCommand(name); if (command == null) return;
        DisabledCommand disabled = new DisabledCommand(plugin, "VelioraPetualang");
        command.setExecutor(disabled); command.setTabCompleter(disabled);
    }
}
