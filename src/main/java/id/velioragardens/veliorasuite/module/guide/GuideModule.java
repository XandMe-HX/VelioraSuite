package id.velioragardens.veliorasuite.module.guide;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import org.bukkit.command.PluginCommand;

public final class GuideModule extends AbstractModule {

    private GuideManager guideManager;

    public GuideModule(VelioraSuite plugin) {
        super(plugin, "guide", "guide");
    }

    @Override
    protected void onEnable() {
        this.guideManager = new GuideManager(configFile);

        registerGuideCommand("velioraguide", GuideType.GUIDE);
        registerGuideCommand("veliorarules", GuideType.RULES);
        registerGuideCommand("velioraproduct", GuideType.PRODUCT);

        plugin.getLogger().info("VelioraGuide module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("VelioraGuide module stopped.");
    }

    private void registerGuideCommand(String commandName, GuideType type) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            plugin.getLogger().warning("Command /" + commandName + " tidak ditemukan di plugin.yml.");
            return;
        }

        GuideCommand executor = new GuideCommand(guideManager, type);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
