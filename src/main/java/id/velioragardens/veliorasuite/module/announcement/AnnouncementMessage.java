package id.velioragardens.veliorasuite.module.announcement;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class AnnouncementMessage {

    private final String id;
    private final boolean enabled;
    private final String permission;
    private final List<String> worlds;
    private final List<String> lines;

    public AnnouncementMessage(String id, boolean enabled, String permission, List<String> worlds, List<String> lines) {
        this.id = id;
        this.enabled = enabled;
        this.permission = permission == null ? "" : permission;
        this.worlds = worlds == null ? List.of() : List.copyOf(worlds);
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPermission() {
        return permission;
    }

    public List<String> getWorlds() {
        return Collections.unmodifiableList(worlds);
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isValid() {
        return id != null && !id.isBlank() && enabled && !lines.isEmpty();
    }

    public boolean canReceive(Player player) {
        if (player == null) {
            return false;
        }

        if (!permission.isBlank() && !player.hasPermission(permission)) {
            return false;
        }

        return worlds.isEmpty() || worlds.contains(player.getWorld().getName());
    }
}
