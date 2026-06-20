package id.velioragardens.veliorasuite.module.anti;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class AntiModule extends AbstractModule {

    public AntiModule(VelioraSuite plugin) {
        super(plugin, "anti", "anti");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("Anti module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("Anti module stopped.");
    }
}
