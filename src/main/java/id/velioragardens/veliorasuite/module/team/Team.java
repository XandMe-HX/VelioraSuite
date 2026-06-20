package id.velioragardens.veliorasuite.module.team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Team {

    private final String name;
    private final UUID owner;
    private final List<UUID> admins;
    private final List<UUID> members;
    private final int level;
    private final int maxMembers;

    public Team(String name, UUID owner, List<UUID> admins, List<UUID> members, int level, int maxMembers) {
        this.name = name;
        this.owner = owner;
        this.admins = new ArrayList<>(admins);
        this.members = new ArrayList<>(members);
        this.level = level;
        this.maxMembers = maxMembers;
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public List<UUID> getAdmins() {
        return new ArrayList<>(admins);
    }

    public List<UUID> getMembers() {
        return new ArrayList<>(members);
    }

    public int getLevel() {
        return level;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isAdmin(UUID uuid) {
        return admins.contains(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid) || admins.contains(uuid) || owner.equals(uuid);
    }

    public int getTotalMembers() {
        return 1 + admins.size() + members.size();
    }
}
