package id.velioragardens.veliorasuite.module.pets.model;

import org.bukkit.entity.LivingEntity;

import java.util.UUID;

public final class VelioraPet {
    private final UUID ownerUuid;
    private final String petId;
    private final LivingEntity entity;
    private long lastAttackMillis;
    private UUID targetUuid;

    public VelioraPet(UUID ownerUuid, String petId, LivingEntity entity) {
        this.ownerUuid = ownerUuid;
        this.petId = petId;
        this.entity = entity;
    }

    public UUID ownerUuid() { return ownerUuid; }
    public String petId() { return petId; }
    public LivingEntity entity() { return entity; }
    public long lastAttackMillis() { return lastAttackMillis; }
    public void lastAttackMillis(long lastAttackMillis) { this.lastAttackMillis = lastAttackMillis; }
    public UUID targetUuid() { return targetUuid; }
    public void targetUuid(UUID targetUuid) { this.targetUuid = targetUuid; }
}
