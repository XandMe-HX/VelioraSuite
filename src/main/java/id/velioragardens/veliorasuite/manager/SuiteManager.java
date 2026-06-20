package id.velioragardens.veliorasuite.manager;

import id.velioragardens.veliorasuite.VelioraSuite;

public abstract class SuiteManager {

    protected final VelioraSuite plugin;

    protected SuiteManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public abstract void load();

    public abstract void unload();

    public void reload() {
        unload();
        load();
    }
}
