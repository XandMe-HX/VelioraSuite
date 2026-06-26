package id.velioragardens.veliorasuite.module.team;

import id.velioragardens.veliorasuite.module.team.model.TeamInvite;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TeamInviteManager {

    private final Map<UUID, TeamInvite> invites = new HashMap<>();

    public void createInvite(TeamInvite invite) {
        if (invite != null && invite.getInvitedUuid() != null) {
            invites.put(invite.getInvitedUuid(), invite);
        }
    }

    public TeamInvite getInvite(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        TeamInvite invite = invites.get(uuid);
        if (invite != null && invite.isExpired()) {
            invites.remove(uuid);
            return null;
        }
        return invite;
    }

    public TeamInvite removeInvite(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return invites.remove(uuid);
    }

    public void clear() {
        invites.clear();
    }
}
