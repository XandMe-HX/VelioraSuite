package id.velioragardens.veliorasuite.api;

import id.velioragardens.veliorasuite.VelioraSuite;

public abstract class PlannedModule implements VelioraModule {

    protected final VelioraSuite plugin;
    private final String name;
    private final String displayName;
    private boolean enabled;

    protected PlannedModule(VelioraSuite plugin, String name, String displayName) {
        this.plugin = plugin;
        this.name = name;
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/" + name + ".yml");
    }

    @Override
    public void enable() {
        enabled = true;
        plugin.getLogger().info(displayName + " skeleton enabled. Logic akan dibuat bertahap.");
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public void reload() {
        load();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
