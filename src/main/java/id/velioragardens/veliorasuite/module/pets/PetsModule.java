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
    private PetSafeModeGuardListener safeModeGuardListener;
    private PetRedProtectGuardListener redProtectGuardListener;
    private BukkitTask quietTask;
    private BukkitTask coreControllerTask;
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
        safeModeGuardListener = new PetSafeModeGuardListener(manager);
        redProtectGuardListener = new PetRedProtectGuardListener(plugin, manager);
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
        plugin.getServer().getPluginManager().registerEvents(guiManager, plugin);
        plugin.getServer().getPluginManager().registerEvents(safetyListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(safeModeGuardListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(redProtectGuardListener, plugin);
        cleanupAnchorsOnly();
        manager.start(guiManager);
        coreControllerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetCoreControllerTask(plugin, manager), 2L, 2L);
        quietTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new PetQuietTask(manager.config()), 20L, 40L);
    }

    @Override
    public void disable() {
        enabled = false;
        if (guiManager != null) guiManager.saveAndCloseOpenStorages();
        if (quietTask != null) quietTask.cancel();
        if (coreControllerTask != null) coreControllerTask.cancel();
        quietTask = null;
        coreControllerTask = null;
        if (manager != null) HandlerList.unregisterAll(manager);
        if (guiManager != null) HandlerList.unregisterAll(guiManager);
        if (safetyListener != null) HandlerList.unregisterAll(safetyListener);
        if (safeModeGuardListener != null) HandlerList.unregisterAll(safeModeGuardListener);
        if (redProtectGuardListener != null) HandlerList.unregisterAll(redProtectGuardListener);
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

    private void cleanupAnchorsOnly() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("veliorapets_aquatic_anchor")) entity.remove();
            }
        }
    }
}
