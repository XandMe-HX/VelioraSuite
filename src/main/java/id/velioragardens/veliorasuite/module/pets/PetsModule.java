package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class PetsModule implements VelioraModule {
    private final VelioraSuite plugin;
    private PetManager manager;
    private PetGuiManager guiManager;
    private PetSafetyListener safetyListener;
    private boolean enabled;

    public PetsModule(VelioraSuite plugin) { this.plugin = plugin; }

    @Override public String getName() { return "pets"; }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/pets.yml");
        manager = new PetManager(plugin);
        manager.load();
        guiManager = new PetGuiManager(plugin, manager, manager.config());
        safetyListener = new PetSafetyListener();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
        plugin.getServer().getPluginManager().registerEvents(guiManager, plugin);
        plugin.getServer().getPluginManager().registerEvents(safetyListener, plugin);
        manager.start(guiManager);
    }

    @Override
    public void disable() {
        enabled = false;
        HandlerList.unregisterAll(manager);
        HandlerList.unregisterAll(guiManager);
        HandlerList.unregisterAll(safetyListener);
        if (manager != null) manager.shutdown();
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) manager.reload();
        else load();
    }

    @Override public boolean isEnabled() { return enabled; }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("pet");
        if (command == null) {
            plugin.getLogger().warning("Command /pet tidak ditemukan di plugin.yml.");
            return;
        }
        PetCommand petCommand = new PetCommand(manager, manager.config());
        command.setExecutor(petCommand);
        command.setTabCompleter(petCommand);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("pet");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraPets");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
