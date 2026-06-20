package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class SkillsModule extends AbstractModule {

    public SkillsModule(VelioraSuite plugin) {
        super(plugin, "skills", "skills");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("Skills module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("Skills module stopped.");
    }
}
