package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class BossModule implements VelioraModule {

    private final VelioraSuite plugin;
    private BossManager manager;
    private boolean enabled;

    public BossModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "boss"; }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/boss.yml");
        manager = new BossManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
        manager.start();
    }

    @Override
    public void disable() {
        enabled = false;
        if (manager != null) {
            HandlerList.unregisterAll(manager);
            manager.shutdown();
        }
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) manager.reload();
        else load();
    }

    @Override
    public boolean isEnabled() { return enabled; }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("boss");
        if (command == null) {
            plugin.getLogger().warning("Command /boss tidak ditemukan di plugin.yml.");
            return;
        }
        BossCommand bossCommand = new BossCommand(manager);
        command.setExecutor(bossCommand);
        command.setTabCompleter(bossCommand);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("boss");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraBoss");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
