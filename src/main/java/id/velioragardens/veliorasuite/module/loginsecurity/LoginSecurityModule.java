package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

import java.util.List;

public final class LoginSecurityModule implements VelioraModule {

    private final VelioraSuite plugin;
    private LoginSecurityManager manager;
    private LoginSecurityListener listener;
    private boolean enabled;

    public LoginSecurityModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "loginsecurity";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/loginsecurity.yml");
        manager = new LoginSecurityManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommands();
        registerListener();
    }

    @Override
    public void disable() {
        enabled = false;
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        LoginSecurityBlindnessManager.clearAll();
        if (manager != null) {
            manager.shutdown();
        }
        registerDisabledCommands();
    }

    @Override
    public void reload() {
        if (manager != null) {
            manager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Exposes the login service to approved companion plugins, such as
     * VelioraPremiumLogin. The companion still must perform Mojang session
     * verification itself before calling the premium methods on this manager.
     */
    public LoginSecurityManager getManager() {
        return manager;
    }

    private void registerCommands() {
        LoginSecurityCommand loginSecurityCommand = new LoginSecurityCommand(manager);
        for (String commandName : commandNames()) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) {
                plugin.getLogger().warning("Command /" + commandName + " tidak ditemukan di plugin.yml.");
                continue;
            }
            command.setExecutor(loginSecurityCommand);
            command.setTabCompleter(loginSecurityCommand);
        }
    }

    private void registerListener() {
        listener = new LoginSecurityListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    private void registerDisabledCommands() {
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraLoginSecurity");
        for (String commandName : commandNames()) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) continue;
            command.setExecutor(disabledCommand);
            command.setTabCompleter(disabledCommand);
        }
    }

    private List<String> commandNames() {
        return List.of("register", "login", "changepass", "unregister", "logout", "risetpw", "cpowner", "loginsecurity");
    }
}
