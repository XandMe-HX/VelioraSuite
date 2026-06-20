package id.velioragardens.veliorasuite.module.report;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;

public final class ReportModule extends AbstractModule {
    private ReportManager reportManager;
    public ReportModule(VelioraSuite plugin) { super(plugin, "report", "report"); }

    @Override
    protected void onEnable() {
        this.reportManager = new ReportManager(plugin, configFile);
        this.reportManager.load();
        register("report", new PlayerReportCommand(reportManager, false));
        register("bugreport", new PlayerReportCommand(reportManager, true));
        register("vreport", new VReportCommand(reportManager));
        plugin.getLogger().info("VelioraReport module started.");
    }

    @Override
    protected void onDisable() {
        if (reportManager != null) reportManager.save();
        plugin.getLogger().info("VelioraReport module stopped.");
    }

    private void register(String name, Object executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) { plugin.getLogger().warning("Command /" + name + " tidak ditemukan di plugin.yml."); return; }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) command.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter tc) command.setTabCompleter(tc);
    }
}
