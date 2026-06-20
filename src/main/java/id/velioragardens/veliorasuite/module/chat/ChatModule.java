package id.velioragardens.veliorasuite.module.chat;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;

public final class ChatModule extends AbstractModule {

    public ChatModule(VelioraSuite plugin) {
        super(plugin, "chat", "chat");
    }

    @Override
    protected void onEnable() {
        plugin.getLogger().info("Chat module started.");
    }

    @Override
    protected void onDisable() {
        plugin.getLogger().info("Chat module stopped.");
    }
}
