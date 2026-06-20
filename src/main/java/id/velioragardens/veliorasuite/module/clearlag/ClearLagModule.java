package id.velioragardens.veliorasuite.module.clearlag;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class ClearLagModule extends AbstractModule {

    public ClearLagModule(VelioraSuite plugin) {
        super(plugin, "clearlag", "clearlag");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("ClearLag module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("ClearLag module stopped.");
    }
}
