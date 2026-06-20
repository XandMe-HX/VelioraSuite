package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class BossModule extends AbstractModule {

    public BossModule(VelioraSuite plugin) {
        super(plugin, "boss", "boss");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("Boss module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("Boss module stopped.");
    }
}
