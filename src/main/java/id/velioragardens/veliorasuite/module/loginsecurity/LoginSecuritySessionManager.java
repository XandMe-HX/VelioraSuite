package id.velioragardens.veliorasuite.module.loginsecurity;

import id.velioragardens.veliorasuite.module.loginsecurity.model.AuthState;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LoginSecuritySessionManager {

    private final Map<UUID, AuthState> states = new HashMap<>();
    private final Map<UUID, Location> authLocations = new HashMap<>();

    public void setState(Player player, AuthState state) {
        if (player == null || state == null) return;
        states.put(player.getUniqueId(), state);
    }

    public AuthState getState(Player player) {
        if (player == null) return AuthState.WAITING_LOGIN;
        return states.getOrDefault(player.getUniqueId(), AuthState.WAITING_LOGIN);
    }

    public boolean isAuthenticated(Player player) {
        return getState(player) == AuthState.AUTHENTICATED;
    }

    public boolean needsAuth(Player player) {
        return !isAuthenticated(player);
    }

    public void setAuthLocation(Player player, Location location) {
        if (player == null || location == null) return;
        authLocations.put(player.getUniqueId(), location.clone());
    }

    public Location getAuthLocation(Player player) {
        if (player == null) return null;
        Location location = authLocations.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public void clear(Player player) {
        if (player == null) return;
        states.remove(player.getUniqueId());
        authLocations.remove(player.getUniqueId());
    }

    public void clearAll() {
        states.clear();
        authLocations.clear();
    }

    public int countAuthenticated() {
        int count = 0;
        for (AuthState state : states.values()) {
            if (state == AuthState.AUTHENTICATED) count++;
        }
        return count;
    }
}
