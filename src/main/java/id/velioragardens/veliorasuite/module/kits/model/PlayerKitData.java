package id.velioragardens.veliorasuite.module.kits.model;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerKitData {

    private final UUID playerId;
    private final Map<String, Long> lastClaims;
    private final Set<String> purchasedKits;
    private final boolean firstJoinGiven;

    public PlayerKitData(UUID playerId, Map<String, Long> lastClaims, Set<String> purchasedKits, boolean firstJoinGiven) {
        this.playerId = playerId;
        this.lastClaims = lastClaims;
        this.purchasedKits = purchasedKits;
        this.firstJoinGiven = firstJoinGiven;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Map<String, Long> getLastClaims() {
        return lastClaims;
    }

    public Set<String> getPurchasedKits() {
        return purchasedKits;
    }

    public boolean isFirstJoinGiven() {
        return firstJoinGiven;
    }
}
