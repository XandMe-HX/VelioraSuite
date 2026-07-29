package id.velioragardens.veliorasuite.module.pets.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerPetData {
    private String activePet;
    private String lastPet;
    private long cooldownUntil;
    private final Map<String, OwnedPet> owned = new LinkedHashMap<>();

    public String activePet() { return activePet; }
    public void activePet(String activePet) { this.activePet = activePet; }
    public String lastPet() { return lastPet; }
    public void lastPet(String lastPet) { this.lastPet = lastPet; }
    public long cooldownUntil() { return cooldownUntil; }
    public void cooldownUntil(long cooldownUntil) { this.cooldownUntil = Math.max(0L, cooldownUntil); }
    public Map<String, OwnedPet> owned() { return owned; }
    public boolean owns(String id) { return owned.containsKey(id.toLowerCase(java.util.Locale.ROOT)); }
    public OwnedPet get(String id) { return owned.get(id.toLowerCase(java.util.Locale.ROOT)); }
    public void add(OwnedPet pet) { owned.put(pet.id().toLowerCase(java.util.Locale.ROOT), pet); }
    public void remove(String id) { owned.remove(id.toLowerCase(java.util.Locale.ROOT)); }
}
