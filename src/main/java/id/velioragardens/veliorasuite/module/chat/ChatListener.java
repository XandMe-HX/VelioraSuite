package id.velioragardens.veliorasuite.module.chat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatListener implements Listener {

    private final ChatManager chatManager;

    public ChatListener(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        ChatManager.ChatProcessResult result = chatManager.processChat(event.getPlayer(), event.getMessage());

        if (result.cancelled()) {
            event.setCancelled(true);
            return;
        }

        if (chatManager.isInteractiveChatEnabled() && chatManager.doesInteractiveChatOwnFormat()) {
            event.setCancelled(true);
            chatManager.broadcastInteractive(event.getPlayer(), result.message());
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        chatManager.clearPlayerState(event.getPlayer().getUniqueId());
    }
}
