package id.velioragardens.veliorasuite.module.team.model;

import java.util.UUID;

public final class TeamInvite {

    private final String teamName;
    private final UUID invitedUuid;
    private final String invitedName;
    private final UUID inviterUuid;
    private final String inviterName;
    private final long expiresAt;

    public TeamInvite(String teamName, UUID invitedUuid, String invitedName, UUID inviterUuid, String inviterName, long expiresAt) {
        this.teamName = teamName;
        this.invitedUuid = invitedUuid;
        this.invitedName = invitedName;
        this.inviterUuid = inviterUuid;
        this.inviterName = inviterName;
        this.expiresAt = expiresAt;
    }

    public String getTeamName() {
        return teamName;
    }

    public UUID getInvitedUuid() {
        return invitedUuid;
    }

    public String getInvitedName() {
        return invitedName;
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }

    public String getInviterName() {
        return inviterName;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
