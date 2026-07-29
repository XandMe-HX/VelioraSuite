package id.velioragardens.veliorasuite.module.chat.placeholder;
import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.chat.ChatPlaceholderManager;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class VelioraPlaceholderExpansion extends PlaceholderExpansion {

    private final VelioraSuite plugin;
    private final ChatPlaceholderManager placeholderManager;

    public VelioraPlaceholderExpansion(VelioraSuite plugin, ChatPlaceholderManager placeholderManager) {
        this.plugin = plugin;
        this.placeholderManager = placeholderManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "veliorasuite";
    }

    @Override
    public @NotNull String getAuthor() {
        return "XandMe";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return placeholderManager.getPlaceholder(player, params);
    }
}
