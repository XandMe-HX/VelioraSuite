package id.velioragardens.veliorasuite.module;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.announcement.AnnouncementModule;
import id.velioragardens.veliorasuite.module.anti.AntiModule;
import id.velioragardens.veliorasuite.module.boss.BossModule;
import id.velioragardens.veliorasuite.module.chat.ChatModule;
import id.velioragardens.veliorasuite.module.clearlag.ClearLagModule;
import id.velioragardens.veliorasuite.module.fishing.FishingModule;
import id.velioragardens.veliorasuite.module.guide.GuideModule;
import id.velioragardens.veliorasuite.module.kits.KitsModule;
import id.velioragardens.veliorasuite.module.login.LoginModule;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.report.ReportModule;
import id.velioragardens.veliorasuite.module.rewards.RewardsModule;
import id.velioragardens.veliorasuite.module.security.SecurityModule;
import id.velioragardens.veliorasuite.module.skills.SkillsModule;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import id.velioragardens.veliorasuite.module.trader.TraderModule;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleManager {

    private final VelioraSuite plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void loadModules() {
        plugin.getLogger().info("Loading VelioraSuite modules...");

        registerModule(new ClearLagModule(plugin));
        registerModule(new AntiModule(plugin));
        registerModule(new QuestModule(plugin));
        registerModule(new SkillsModule(plugin));
        registerModule(new TraderModule(plugin));
        registerModule(new FishingModule(plugin));
        registerModule(new BossModule(plugin));
        registerModule(new RewardsModule(plugin));
        registerModule(new ChatModule(plugin));
        registerModule(new TeamModule(plugin));
        registerModule(new GuideModule(plugin));
        registerModule(new SecurityModule(plugin));
        registerModule(new LoginModule(plugin));
        registerModule(new ReportModule(plugin));
        registerModule(new AnnouncementModule(plugin));
        registerModule(new KitsModule(plugin));

        plugin.getLogger().info("Loaded " + modules.size() + " module(s).");
    }

    public void registerModule(Module module) {
        String key = module.getName().toLowerCase();
        modules.put(key, module);

        boolean enabledInMainConfig = plugin.getConfig().getBoolean("modules." + key, true);
        boolean enabledInModuleConfig = true;

        if (plugin.getConfigManager().getFile(key) != null) {
            enabledInModuleConfig = plugin.getConfigManager().getFile(key).get().getBoolean("enabled", true);
        }

        if (enabledInMainConfig && enabledInModuleConfig) {
            module.enable();
            plugin.getLogger().info("Enabled module: " + module.getName());
        } else {
            plugin.getLogger().info("Skipped disabled module: " + module.getName());
        }
    }

    public void reloadModules() {
        for (Module module : modules.values()) {
            module.reload();
        }
    }

    public void unloadModules() {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                module.disable();
            }
        }
        modules.clear();
    }

    public boolean isModuleEnabled(String moduleName) {
        Module module = modules.get(moduleName.toLowerCase());
        return module != null && module.isEnabled();
    }

    public Module getModule(String moduleName) { return modules.get(moduleName.toLowerCase()); }
    public Map<String, Module> getModules() { return modules; }
}
