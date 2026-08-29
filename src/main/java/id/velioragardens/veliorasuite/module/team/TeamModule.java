package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class TeamModule implements VelioraModule {

    private final VelioraSuite plugin;
    private TeamManager teamManager;
    private TeamListener teamListener;
    private TeamGuiManager teamGuiManager;
    private boolean enabled;

    public TeamModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "team";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/team.yml");
        teamManager = new TeamManager(plugin);
        teamManager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        registerListener();
    }

    @Override
    public void disable() {
        enabled = false;

        if (teamListener != null) {
            HandlerList.unregisterAll(teamListener);
            teamListener = null;
        }
        if (teamGuiManager != null) {
            HandlerList.unregisterAll(teamGuiManager);
            teamGuiManager = null;
        }

        if (teamManager != null) {
            teamManager.shutdown();
        }

        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (teamManager != null) {
            teamManager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("team");

        if (command == null) {
            plugin.getLogger().warning("Command /team tidak ditemukan di plugin.yml.");
            return;
        }

        TeamCommand teamCommand = new TeamCommand(teamManager);
        command.setExecutor(teamCommand);
        command.setTabCompleter(teamCommand);

        PluginCommand adminCommand = plugin.getCommand("teama");
        if (adminCommand == null) {
            plugin.getLogger().warning("Command /teama tidak ditemukan di plugin.yml.");
            return;
        }
        TeamAdminCommand teamAdminCommand = new TeamAdminCommand(teamManager);
        adminCommand.setExecutor(teamAdminCommand);
        adminCommand.setTabCompleter(teamAdminCommand);
    }

    private void registerListener() {
        teamListener = new TeamListener(teamManager);
        teamGuiManager = new TeamGuiManager(teamManager);
        teamManager.setGuiManager(teamGuiManager);
        plugin.getServer().getPluginManager().registerEvents(teamListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(teamGuiManager, plugin);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("team");

        if (command == null) {
            return;
        }

        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraTeam");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);

        PluginCommand adminCommand = plugin.getCommand("teama");
        if (adminCommand != null) {
            adminCommand.setExecutor(disabledCommand);
            adminCommand.setTabCompleter(disabledCommand);
        }
    }
}
