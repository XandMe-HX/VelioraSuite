package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class ReportModule implements VelioraModule {

    private final VelioraSuite plugin;
    private ReportManager reportManager;
    private ReportListener reportListener;
    private boolean enabled;

    public ReportModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "report";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/report.yml");
        reportManager = new ReportManager(plugin);
        reportManager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerPlayerCommand();
        registerStaffCommand();
        registerListener();
    }

    @Override
    public void disable() {
        enabled = false;

        if (reportListener != null) {
            HandlerList.unregisterAll(reportListener);
            reportListener = null;
        }

        if (reportManager != null) {
            reportManager.shutdown();
        }

        registerDisabledCommand("report");
        registerDisabledCommand("reports");
    }

    @Override
    public void reload() {
        if (reportManager != null) {
            reportManager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerPlayerCommand() {
        PluginCommand command = plugin.getCommand("report");

        if (command == null) {
            plugin.getLogger().warning("Command /report tidak ditemukan di plugin.yml.");
            return;
        }

        ReportCommand reportCommand = new ReportCommand(reportManager);
        command.setExecutor(reportCommand);
        command.setTabCompleter(reportCommand);
    }

    private void registerStaffCommand() {
        PluginCommand command = plugin.getCommand("reports");

        if (command == null) {
            plugin.getLogger().warning("Command /reports tidak ditemukan di plugin.yml.");
            return;
        }

        ReportsCommand reportsCommand = new ReportsCommand(reportManager);
        command.setExecutor(reportsCommand);
        command.setTabCompleter(reportsCommand);
    }

    private void registerListener() {
        reportListener = new ReportListener(reportManager);
        plugin.getServer().getPluginManager().registerEvents(reportListener, plugin);
    }

    private void registerDisabledCommand(String commandName) {
        PluginCommand command = plugin.getCommand(commandName);

        if (command == null) {
            return;
        }

        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraReport");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
