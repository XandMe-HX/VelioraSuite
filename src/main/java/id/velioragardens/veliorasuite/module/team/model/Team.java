package id.velioragardens.veliorasuite.module.team.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Team {

    private final int id;
    private final String name;
    private String displayName;
    private UUID ownerUuid;
    private String ownerName;
    private int maxMembers;
    private boolean upgraded;
    private final String createdAt;
    private String lastActive;
    private final Map<UUID, TeamMember> members = new LinkedHashMap<>();

    public Team(int id, String name, String displayName, UUID ownerUuid, String ownerName, int maxMembers, boolean upgraded, String createdAt, String lastActive) {
        this.id = id;
        this.name = name;
        this.displayName = displayName == null || displayName.isBlank() ? name : displayName;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null ? "Unknown" : ownerName;
        this.maxMembers = Math.max(1, maxMembers);
        this.upgraded = upgraded;
        this.createdAt = createdAt == null || createdAt.isBlank() ? "-" : createdAt;
        this.lastActive = lastActive == null ? "" : lastActive;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? name : displayName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? this.ownerName : ownerName;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(1, maxMembers);
    }

    public boolean isUpgraded() {
        return upgraded;
    }

    public void setUpgraded(boolean upgraded) {
        this.upgraded = upgraded;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getLastActive() {
        return lastActive;
    }

    public void setLastActive(String lastActive) {
        this.lastActive = lastActive == null ? "" : lastActive;
    }

    public Map<UUID, TeamMember> getMembers() {
        return members;
    }

    public boolean isOwner(UUID uuid) {
        return uuid != null && uuid.equals(ownerUuid);
    }

    public boolean isMember(UUID uuid) {
        return uuid != null && members.containsKey(uuid);
    }

    public boolean isFull() {
        return members.size() >= maxMembers;
    }

    public void addMember(TeamMember member) {
        if (member != null && member.getUuid() != null) {
            members.put(member.getUuid(), member);
        }
    }

    public void removeMember(UUID uuid) {
        if (uuid != null) {
            members.remove(uuid);
        }
    }
}
