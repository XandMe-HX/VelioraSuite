package id.velioragardens.veliorasuite.module.pets.model;

public final class OwnedPet {
    private final String id;
    private int level;
    private int exp;
    private String name;
    private long cooldownUntil;
    private long lastFed;

    public OwnedPet(String id, int level, int exp, String name) {
        this(id, level, exp, name, 0L, System.currentTimeMillis());
    }

    public OwnedPet(String id, int level, int exp, String name, long cooldownUntil, long lastFed) {
        this.id = id;
        this.level = Math.max(1, level);
        this.exp = Math.max(0, exp);
        this.name = name == null || name.isBlank() ? id : name;
        this.cooldownUntil = Math.max(0L, cooldownUntil);
        this.lastFed = Math.max(0L, lastFed);
    }

    public String id() { return id; }
    public int level() { return level; }
    public int exp() { return exp; }
    public String name() { return name; }
    public long cooldownUntil() { return cooldownUntil; }
    public long lastFed() { return lastFed; }

    public void name(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public void cooldownUntil(long cooldownUntil) { this.cooldownUntil = Math.max(0L, cooldownUntil); }
    public void lastFed(long lastFed) { this.lastFed = Math.max(0L, lastFed); }

    public boolean addExp(int amount, int maxLevel) {
        int oldLevel = level;
        exp += Math.max(0, amount);
        while (exp >= level * 100 && level < maxLevel) {
            exp -= level * 100;
            level++;
        }
        return level > oldLevel;
    }
}
