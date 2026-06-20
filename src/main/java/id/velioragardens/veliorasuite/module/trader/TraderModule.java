package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class TraderModule extends AbstractModule {

    public TraderModule(VelioraSuite plugin) {
        super(plugin, "trader", "trader");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("Trader module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("Trader module stopped.");
    }
}
