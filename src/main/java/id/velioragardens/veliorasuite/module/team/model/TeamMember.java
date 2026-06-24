package id.velioragardens.veliorasuite.module.team.model;

import java.util.UUID;

public final class TeamMember {

    private final UUID uuid;
    private String name;
    private TeamRole role;
    private final String joinedAt;

    public TeamMember(UUID uuid, String name, TeamRole role, String joinedAt) {
        this.uuid = uuid;
        this.name = name == null ? "Unknown" : name;
        this.role = role == null ? TeamRole.MEMBER : role;
        this.joinedAt = joinedAt == null || joinedAt.isBlank() ? "-" : joinedAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? this.name : name;
    }

    public TeamRole getRole() {
        return role;
    }

    public void setRole(TeamRole role) {
        this.role = role == null ? TeamRole.MEMBER : role;
    }

    public String getJoinedAt() {
        return joinedAt;
    }
}
