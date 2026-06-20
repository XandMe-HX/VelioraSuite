package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class TeamModule extends AbstractModule {

    private TeamManager teamManager;
    private TeamChatListener chatListener;

    public TeamModule(VelioraSuite plugin) {
        super(plugin, "team", "team");
    }

    @Override
    protected void onEnable() {
        this.teamManager = new TeamManager(plugin, configFile);
        this.teamManager.load();

        TeamCommand teamCommand = new TeamCommand(teamManager, this::reloadModuleOnly);
        registerCommand("vteam", teamCommand);
        registerCommand("yes", new ConfirmCommand(teamManager, true));
        registerCommand("no", new ConfirmCommand(teamManager, false));

        if (configFile.get().getBoolean("chat.enabled", true)) {
            this.chatListener = new TeamChatListener(teamManager);
            plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
        }

        plugin.getLogger().info("VelioraTeam module started.");
    }

    @Override
    protected void onDisable() {
        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }

        if (teamManager != null) {
            teamManager.save();
        }

        plugin.getLogger().info("VelioraTeam module stopped.");
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    private void reloadModuleOnly() {
        configFile.reload();
        if (teamManager != null) {
            teamManager.load();
        }
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml.");
            return;
        }

        if (executor instanceof org.bukkit.command.CommandExecutor commandExecutor) {
            command.setExecutor(commandExecutor);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
