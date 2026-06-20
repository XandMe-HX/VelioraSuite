package id.velioragardens.veliorasuite.module.login;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class LoginModule extends AbstractModule {

    private LoginManager loginManager;
    private LoginListener listener;

    public LoginModule(VelioraSuite plugin) {
        super(plugin, "login", "login");
    }

    @Override
    protected void onEnable() {
        this.loginManager = new LoginManager(plugin, configFile);
        this.loginManager.load();
        this.listener = new LoginListener(loginManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        register("register", new LoginUserCommand(loginManager, LoginUserCommand.Mode.REGISTER));
        register("login", new LoginUserCommand(loginManager, LoginUserCommand.Mode.LOGIN));
        register("logout", new LoginUserCommand(loginManager, LoginUserCommand.Mode.LOGOUT));
        register("changepass", new LoginUserCommand(loginManager, LoginUserCommand.Mode.CHANGEPASS));
        register("vls", new VlsCommand(loginManager));
        plugin.getLogger().info("Veliora Login Security module started.");
    }

    @Override
    protected void onDisable() {
        if (listener != null) HandlerList.unregisterAll(listener);
        if (loginManager != null) loginManager.save();
        plugin.getLogger().info("Veliora Login Security module stopped.");
    }

    private void register(String name, Object executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml.");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) command.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter tc) command.setTabCompleter(tc);
    }
}
