package id.velioragardens.veliorasuite.module.redeem;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

/** Admin-created, one-claim-per-player redemption codes. */
public final class CodeRedeemModule implements VelioraModule {
    private final VelioraSuite plugin;
    private CodeRedeemManager manager;
    private CodeRedeemGui gui;
    private boolean enabled;

    public CodeRedeemModule(VelioraSuite plugin) { this.plugin = plugin; }
    @Override public String getName() { return "redeem"; }

    @Override public void load() {
        manager = new CodeRedeemManager(plugin);
        manager.load();
    }

    @Override public void enable() {
        enabled = true;
        CodeRedeemCommand command = new CodeRedeemCommand(manager, this);
        register("redeem", command);
        register("cdmanager", command);
        gui = new CodeRedeemGui(manager, this);
        plugin.getServer().getPluginManager().registerEvents(gui, plugin);
    }

    @Override public void disable() {
        enabled = false;
        if (gui != null) HandlerList.unregisterAll(gui);
        if (manager != null) manager.shutdown();
        DisabledCommand disabled = new DisabledCommand(plugin, "CodeRedeem");
        register("redeem", disabled);
        register("cdmanager", disabled);
    }

    @Override public void reload() { if (manager == null) load(); else manager.load(); }
    @Override public boolean isEnabled() { return enabled; }
    public void openManager(org.bukkit.entity.Player player) { gui.openMain(player); }

    private void register(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) { plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml."); return; }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) command.setTabCompleter(completer);
    }
}
