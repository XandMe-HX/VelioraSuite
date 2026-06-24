package id.velioragardens.veliorasuite.core;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModuleManager {

    private final VelioraSuite plugin;
    private final Map<String, VelioraModule> modules = new LinkedHashMap<>();
    private final Set<String> activeModules = ConcurrentHashMap.newKeySet();

    public ModuleManager(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    public void register(VelioraModule module) {
        String name = normalize(module.getName());
        modules.put(name, module);
    }

    public void enableAll() {
        for (VelioraModule module : modules.values()) {
            String name = normalize(module.getName());

            if (!plugin.getConfigManager().isModuleEnabled(name)) {
                plugin.getLogger().info("Module disabled from modules.yml: " + name);
                continue;
            }

            try {
                module.load();
                module.enable();
                activeModules.add(name);
                plugin.getLogger().info("Module enabled: " + name);
            } catch (Exception exception) {
                activeModules.remove(name);
                plugin.getLogger().severe("Gagal enable module " + name + ": " + exception.getMessage());
                exception.printStackTrace();
            }
        }
    }

    public void disableAll() {
        List<VelioraModule> reversedModules = new ArrayList<>(modules.values());
        Collections.reverse(reversedModules);

        for (VelioraModule module : reversedModules) {
            String name = normalize(module.getName());

            try {
                module.disable();
            } catch (Exception exception) {
                plugin.getLogger().severe("Gagal disable module " + name + ": " + exception.getMessage());
                exception.printStackTrace();
            }
        }

        activeModules.clear();
    }

    public void reloadAll() {
        disableAll();
        enableAll();
    }

    public Optional<VelioraModule> getModule(String name) {
        return Optional.ofNullable(modules.get(normalize(name)));
    }

    public Collection<VelioraModule> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public boolean isModuleActive(String name) {
        String normalized = normalize(name);

        if (normalized.equals("core")) {
            return true;
        }

        return activeModules.contains(normalized);
    }

    public int getEnabledCount() {
        return activeModules.size();
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "");
    }
}
