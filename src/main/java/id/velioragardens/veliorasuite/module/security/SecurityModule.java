package id.velioragardens.veliorasuite.module.security;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class SecurityModule implements VelioraModule {

    private final VelioraSuite plugin;
    private SecurityManager manager;
    private SecurityListener listener;
    private CombatGuardManager combatGuard;
    private boolean enabled;
    private AutoCloseable oreMask;

    public SecurityModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "security";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/security.yml");
        manager = new SecurityManager(plugin);
        manager.load();
        combatGuard = new CombatGuardManager(plugin, manager.getConfigManager());
        combatGuard.load();
    }

    @Override
    public void enable() {
        enabled = true;
        plugin.saveResourceIfNotExists("modules/ore-mask.yml");
        PluginCommand maskStatus = plugin.getCommand("voremask");
        if (maskStatus != null) maskStatus.setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("veliorasuite.security.admin")) return true;
            if (args.length == 1 && args[0].equalsIgnoreCase("disable")) {
                sender.sendMessage("OreMask dibuat selalu aktif untuk melindungi world. Gunakan /voremask untuk melihat statusnya.");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("enable")) {
                java.io.File file = new java.io.File(plugin.getDataFolder(), "modules/ore-mask.yml");
                try {
                    var config = new org.bukkit.configuration.file.YamlConfiguration();
                    config.load(file);
                    config.set("enabled", args[0].equalsIgnoreCase("enable"));
                    config.save(file);
                    sender.sendMessage("OreMask setting saved. Restart server, then reconnect all players. Disable Paper Anti-Xray before enabling this mask.");
                } catch (Exception failure) {
                    sender.sendMessage("OreMask configuration could not be saved: " + failure.getMessage());
                }
                return true;
            }
            sender.sendMessage(oreMask == null ? "OreMask belum aktif: pastikan PacketEvents 2.13.0 terpasang, lalu restart server."
                    : oreMask.toString());
            sender.sendMessage("OreWatch dan OreMask terpisah. OreMask otomatis aktif; /vxray exempt hanya memengaruhi pemeriksaan OreWatch.");
            return true;
        });
        if (plugin.getServer().getPluginManager().isPluginEnabled("packetevents")) {
            try {
                oreMask = id.velioragardens.veliorasuite.module.security.xray.PacketOreMask.start(plugin);
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().warning("OreMask could not start: " + failure.getMessage());
            }
        } else {
            plugin.getLogger().info("OreMask inactive: optional PacketEvents dependency not installed.");
        }
        registerCommand();
        listener = new SecurityListener(manager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        plugin.getServer().getPluginManager().registerEvents(combatGuard, plugin);
        PluginCommand guardCommand = plugin.getCommand("vguard");
        if (guardCommand != null) {
            guardCommand.setExecutor(combatGuard);
            guardCommand.setTabCompleter(combatGuard);
        }
    }

    @Override
    public void disable() {
        enabled = false;
        if (oreMask != null) {
            try { oreMask.close(); }
            catch (Exception failure) { plugin.getLogger().warning("OreMask stop: " + failure.getMessage()); }
            oreMask = null;
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (combatGuard != null) {
            HandlerList.unregisterAll(combatGuard);
            combatGuard.shutdown();
        }
        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (manager != null) manager.reload();
        else load();
        if (combatGuard != null) combatGuard.reload();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("vsecurity");
        if (command == null) {
            plugin.getLogger().warning("Command /vsecurity tidak ditemukan di plugin.yml.");
            return;
        }
        SecurityCommand securityCommand = new SecurityCommand(manager);
        command.setExecutor(securityCommand);
        command.setTabCompleter(securityCommand);
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("vsecurity");
        if (command == null) return;
        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraSecurity");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
