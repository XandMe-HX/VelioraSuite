package id.velioragardens.veliorasuite.module;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.config.ConfigFile;

public abstract class AbstractModule implements Module {

    protected final VelioraSuite plugin;
    protected final String name;
    protected final ConfigFile configFile;
    protected boolean enabled;

    protected AbstractModule(VelioraSuite plugin, String name, String configKey) {
        this.plugin = plugin;
        this.name = name;
        this.configFile = plugin.getConfigManager().getFile(configKey);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void enable() {
        enabled = true;
        onEnable();
    }

    @Override
    public void disable() {
        onDisable();
        enabled = false;
    }

    @Override
    public void reload() {
        if (configFile != null) {
            configFile.reload();
        }

        if (enabled) {
            onReload();
        }
    }

    protected abstract void onEnable();

    protected abstract void onDisable();

    protected void onReload() {
        onDisable();
        onEnable();
    }
}
