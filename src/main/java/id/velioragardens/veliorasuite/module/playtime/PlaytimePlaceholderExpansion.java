package id.velioragardens.veliorasuite.module.playtime;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PlaytimePlaceholderExpansion extends PlaceholderExpansion {

    private final String identifier;
    private final PlaytimeManager manager;

    public PlaytimePlaceholderExpansion(String identifier, PlaytimeManager manager) {
        this.identifier = identifier;
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() { return identifier; }

    @Override
    public @NotNull String getAuthor() { return "XandMe"; }

    @Override
    public @NotNull String getVersion() { return "1.0.0"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        if (player == null) return "0s";
        return manager.format(manager.currentSessionMillis(player.getUniqueId()));
    }
}
