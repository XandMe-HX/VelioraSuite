package id.velioragardens.veliorasuite.module.clearlag.model;

public record ClearResult(int items, int mobs, int projectiles) {

    public static ClearResult empty() {
        return new ClearResult(0, 0, 0);
    }

    public int total() {
        return items + mobs + projectiles;
    }
}
