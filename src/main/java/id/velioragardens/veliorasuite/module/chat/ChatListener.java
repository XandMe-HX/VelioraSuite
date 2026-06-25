package id.velioragardens.veliorasuite.module.chat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class ChatListener implements Listener {

    private final ChatManager chatManager;

    public ChatListener(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        ChatManager.ChatProcessResult result = chatManager.processChat(event.getPlayer(), event.getMessage());

        if (result.cancelled()) {
            event.setCancelled(true);
            return;
        }

        if (result.formatted()) {
            event.setCancelled(true);
            chatManager.broadcastFormatted(result.message());
            return;
        }

        event.setMessage(result.message());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (chatManager.shouldCancelCommand(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }
}
