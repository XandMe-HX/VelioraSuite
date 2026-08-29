package id.velioragardens.veliorasuite.module.team.model;

public enum TeamRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    public boolean isHigherThan(TeamRole other) {
        return ordinal() < (other == null ? MEMBER.ordinal() : other.ordinal());
    }

    public static TeamRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return MEMBER;
        }

        try {
            return TeamRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return MEMBER;
        }
    }
}
