package id.velioragardens.veliorasuite.module.team.model;

public enum TeamRole {
    OWNER,
    MEMBER;

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
