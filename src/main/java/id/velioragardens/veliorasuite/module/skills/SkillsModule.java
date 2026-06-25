package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;

public final class SkillsModule implements VelioraModule {

    private final VelioraSuite plugin;
    private SkillsManager manager;
    private boolean enabled;

    public SkillsModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "skills";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/skills.yml");
        manager = new SkillsManager(plugin);
        manager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        manager.enable();
    }

    @Override
    public void disable() {
        enabled = false;
        if (manager != null) manager.shutdown();
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) manager.reload();
        else load();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public SkillsManager getSkillsManager() {
        return manager;
    }

    public SkillsApi getApi() {
        return manager == null ? null : manager.getApi();
    }

    public SkillsPlaceholderManager getPlaceholderManager() {
        return manager == null ? null : manager.getPlaceholderManager();
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("vskills");
        if (command == null) {
            plugin.getLogger().warning("Command /vskills tidak ditemukan di plugin.yml.");
            return;
        }
        SkillsCommand skillsCommand = new SkillsCommand(manager);
        command.setExecutor(skillsCommand);
        command.setTabCompleter(skillsCommand);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("vskills");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraSkills");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
