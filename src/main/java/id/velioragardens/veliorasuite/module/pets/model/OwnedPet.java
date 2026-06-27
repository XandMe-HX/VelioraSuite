package id.velioragardens.veliorasuite.module.pets.model;

public final class OwnedPet {
    private final String id;
    private int level;
    private int exp;
    private String name;

    public OwnedPet(String id, int level, int exp, String name) {
        this.id = id;
        this.level = Math.max(1, level);
        this.exp = Math.max(0, exp);
        this.name = name == null || name.isBlank() ? id : name;
    }

    public String id() { return id; }
    public int level() { return level; }
    public int exp() { return exp; }
    public String name() { return name; }
    public void name(String name) { if (name != null && !name.isBlank()) this.name = name; }

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
