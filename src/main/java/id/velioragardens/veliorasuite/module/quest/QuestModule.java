package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class QuestModule extends AbstractModule {

    public QuestModule(VelioraSuite plugin) {
        super(plugin, "quest", "quest");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("Quest module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("Quest module stopped.");
    }
}
