package id.velioragardens.veliorasuite.module.pets.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class OwnedPet {
    private final String id;
    private int level;
    private int exp;
    private String name;
    private long cooldownUntil;
    private long lastFed;
    private boolean publicRide;
    private final Set<UUID> trustedRiders = new LinkedHashSet<>();

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
    public boolean publicRide() { return publicRide; }
    public Set<UUID> trustedRiders() { return Set.copyOf(trustedRiders); }

    public void name(String name) { if (name != null && !name.isBlank()) this.name = name; }
    public void cooldownUntil(long cooldownUntil) { this.cooldownUntil = Math.max(0L, cooldownUntil); }
    public void lastFed(long lastFed) { this.lastFed = Math.max(0L, lastFed); }
    public void publicRide(boolean publicRide) { this.publicRide = publicRide; }
    public boolean trustRider(UUID uuid) { return uuid != null && trustedRiders.size() < 20 && trustedRiders.add(uuid); }
    public boolean untrustRider(UUID uuid) { return uuid != null && trustedRiders.remove(uuid); }
    public void trustedRiders(Set<UUID> riders) {
        trustedRiders.clear();
        if (riders == null) return;
        for (UUID rider : riders) {
            if (rider != null && trustedRiders.size() < 20) trustedRiders.add(rider);
        }
    }

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
