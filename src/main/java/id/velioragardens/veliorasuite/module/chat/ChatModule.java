package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.command.DisabledCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class ChatModule implements VelioraModule {

    private final VelioraSuite plugin;
    private ChatManager chatManager;
    private ChatListener chatListener;
    private Object placeholderExpansion;
    private boolean enabled;

    public ChatModule(VelioraSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "chat";
    }

    @Override
    public void load() {
        plugin.saveResourceIfNotExists("modules/chat.yml");
        chatManager = new ChatManager(plugin);
        chatManager.load();
    }

    @Override
    public void enable() {
        enabled = true;
        registerCommand();
        registerListener();
        registerPlaceholderExpansion();
    }

    @Override
    public void disable() {
        enabled = false;

        if (chatListener != null) {
            HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }

        unregisterPlaceholderExpansion();

        if (chatManager != null) {
            chatManager.shutdown();
        }

        registerDisabledCommand();
    }

    @Override
    public void reload() {
        if (chatManager != null) {
            chatManager.reload();
        } else {
            load();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void registerCommand() {
        PluginCommand command = plugin.getCommand("veliorachat");

        if (command == null) {
            plugin.getLogger().warning("Command /veliorachat tidak ditemukan di plugin.yml.");
            return;
        }

        ChatCommand chatCommand = new ChatCommand(chatManager);
        command.setExecutor(chatCommand);
        command.setTabCompleter(chatCommand);
    }

    private void registerListener() {
        chatListener = new ChatListener(chatManager);
        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
    }

    private void registerPlaceholderExpansion() {
        if (!chatManager.getConfigManager().isUsePlaceholderApi() || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        try {
            placeholderExpansion = new id.velioragardens.veliorasuite.module.chat.placeholder.VelioraPlaceholderExpansion(plugin, chatManager.getPlaceholderManager());
            placeholderExpansion.getClass().getMethod("register").invoke(placeholderExpansion);
            plugin.getLogger().info("VelioraChat PlaceholderAPI expansion registered: veliorasuite.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            placeholderExpansion = null;
            plugin.getLogger().warning("VelioraChat: gagal register PlaceholderAPI expansion. PlaceholderAPI akan dilewati.");
        }
    }

    private void unregisterPlaceholderExpansion() {
        if (placeholderExpansion == null) {
            return;
        }

        try {
            placeholderExpansion.getClass().getMethod("unregister").invoke(placeholderExpansion);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // PlaceholderAPI is optional.
        }

        placeholderExpansion = null;
    }

    private void registerDisabledCommand() {
        PluginCommand command = plugin.getCommand("veliorachat");

        if (command == null) {
            return;
        }

        DisabledCommand disabledCommand = new DisabledCommand(plugin, "VelioraChat");
        command.setExecutor(disabledCommand);
        command.setTabCompleter(disabledCommand);
    }
}
