package id.velioragardens.veliorasuite.module.guide;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;

public final class GuideModule implements VelioraModule {

    private final VelioraSuite plugin;
    private GuideManager guideManager;
    private boolean enabled;

    public GuideModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "guide";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/guide.yml");
        guideManager = new GuideManager(plugin);
        guideManager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerGuideCommand("velioraguide", "guide");
        registerGuideCommand("veliorarules", "rules");
        registerGuideCommand("velioraproduct", "product");
        registerGuideCommand("veliorarank", "rank");
    }

    @Override
    public void disable() {
        enabled = false;
        registerDisabledCommand("velioraguide");
        registerDisabledCommand("veliorarules");
        registerDisabledCommand("velioraproduct");
        registerDisabledCommand("veliorarank");
    }

    @Override
    public void reload() {
        if (guideManager != null) {
            guideManager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerGuideCommand(String commandName, String sectionName) {
        PluginCommand command = plugin.getCommand(commandName);

        if (command == null) {
            plugin.getLogger().warning("Command /" + commandName + " tidak ditemukan di plugin.yml.");
            return;
        }

        GuideCommand guideCommand = new GuideCommand(guideManager, sectionName);
        command.setExecutor(guideCommand);
        command.setTabCompleter(guideCommand);
    }

    private void registerDisabledCommand(String commandName) {
        PluginCommand command = plugin.getCommand(commandName);

        if (command == null) {
            return;
        }

        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraGuide");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
