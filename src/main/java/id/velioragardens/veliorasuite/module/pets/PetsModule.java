package id.velioragardens.veliorasuite.module.pets;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

public final class PetsModule implements VelioraModule {
    private final VelioraSuite plugin;
    private PetManager manager;
    private PetGuiManager guiManager;
    private PetSafetyListener safetyListener;
    private PetRideController rideController;
    private BukkitTask flyingFollowTask;
    private BukkitTask aquaticFollowTask;
    private BukkitTask rideTask;
    private BukkitTask quietTask;
    private BukkitTask visibleControllerTask;
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
        rideController = new PetRideController(manager);
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
        plugin.getServer().getPluginManager().registerEvents(guiManager, plugin);
        plugin.getServer().getPluginManager().registerEvents(safetyListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(rideController, plugin);
        manager.start(guiManager);
        flyingFollowTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetFlyingFollowTask(plugin, manager.config()), 5L, 5L);
        aquaticFollowTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetAquaticFollowTask(plugin, manager.config()), 2L, 2L);
        rideTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetRideTask(manager.config()), 2L, 2L);
        quietTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetQuietTask(manager.config()), 20L, 40L);
        visibleControllerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetVisibleControllerTask(plugin, manager), 1L, 5L);
    }

    @Override
    public void disable() {
        enabled = false;
        if (flyingFollowTask != null) flyingFollowTask.cancel();
        if (aquaticFollowTask != null) aquaticFollowTask.cancel();
        if (rideTask != null) rideTask.cancel();
        if (quietTask != null) quietTask.cancel();
        if (visibleControllerTask != null) visibleControllerTask.cancel();
        flyingFollowTask = null;
        aquaticFollowTask = null;
        rideTask = null;
        quietTask = null;
        visibleControllerTask = null;
        HandlerList.unregisterAll(manager);
        HandlerList.unregisterAll(guiManager);
        HandlerList.unregisterAll(safetyListener);
        HandlerList.unregisterAll(rideController);
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
