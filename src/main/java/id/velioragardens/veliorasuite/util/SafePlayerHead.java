package id.velioragardens.veliorasuite.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Applies a skin only when the player is currently online.
 *
 * <p>Calling {@link SkullMeta#setOwningPlayer(org.bukkit.OfflinePlayer)} for
 * offline or Floodgate identities may cause Paper to query Mojang's session
 * server. Administrative menus must never make network calls merely to render
 * a list, therefore offline entries deliberately retain the normal player-head
 * appearance.</p>
 */
public final class SafePlayerHead {
    private SafePlayerHead() {
    }

    public static void applyOnlineProfile(SkullMeta meta, UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            meta.setOwnerProfile(online.getPlayerProfile());
        }
    }
}
