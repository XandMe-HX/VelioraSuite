package id.velioragardens.veliorasuite.module.rewards;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class RewardsModule extends AbstractModule {

    public RewardsModule(VelioraSuite plugin) {
        super(plugin, "rewards", "rewards");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("Rewards module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("Rewards module stopped.");
    }
}
