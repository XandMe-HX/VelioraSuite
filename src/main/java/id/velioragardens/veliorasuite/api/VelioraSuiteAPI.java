package id.velioragardens.veliorasuite.api;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.ModuleManager;

public final class VelioraSuiteAPI {

    private final VelioraSuite plugin;

    public VelioraSuiteAPI(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public ModuleManager getModuleManager() {
        return plugin.getModuleManager();
    }

    public boolean isModuleEnabled(String moduleName) {
        return plugin.getModuleManager().isModuleEnabled(moduleName);
    }
}
