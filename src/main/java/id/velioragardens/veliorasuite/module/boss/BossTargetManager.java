package id.velioragardens.veliorasuite.module.boss;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BossTargetManager {
    private final VelioraSuite plugin;
    private final BossConfigManager config;

    public BossTargetManager(VelioraSuite plugin, BossConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Player findBestTarget(Location center, Location arenaCenter) {
        List<Player> players = validPlayers(center, arenaCenter, config.targetingRadiusHorizontal());
        if (players.isEmpty()) {
            debug("no target found");
            return null;
        }
    Player target = players.stream()
            .filter(Player::isOnline)
            .filter(player -> !player.isDead())
            .min(Comparator.comparingDouble(player ->
                    horizontalDistanceSquared(center, player.getLocation())
            ))
            .orElse(null);
        if (target != null) debug("boss target player " + target.getName());
        return target;
    }

    public List<Player> validPlayers(Location center, Location arenaCenter, double horizontalRadius) {
        List<Player> result = new ArrayList<>();
        if (center == null || center.getWorld() == null) return result;
        for (Player player : center.getWorld().getPlayers()) {
            if (!isGamemodeAllowed(player)) {
                debug("target skipped " + player.getName() + " karena gamemode " + player.getGameMode());
                continue;
            }
            if (!isWithinVerticalAwareRadius(center, player.getLocation(), horizontalRadius, config.targetingRadiusVertical())) {
                debug("target skipped " + player.getName() + " karena radius");
                continue;
            }
            if (config.arenaEnabled() && arenaCenter != null && arenaCenter.getWorld() != null) {
                if (!player.getWorld().equals(arenaCenter.getWorld())) {
                    debug("target skipped " + player.getName() + " karena world beda");
                    continue;
                }
                if (horizontalDistance(arenaCenter, player.getLocation()) > config.arenaRadius() + 6.0D) {
                    debug("target skipped " + player.getName() + " karena di luar arena");
                    continue;
                }
            }
            result.add(player);
        }
        return result;
    }

    public boolean isValidCurrentTarget(Player player, Location center, Location arenaCenter) {
        if (player == null) return false;
        if (center == null || center.getWorld() == null || !player.getWorld().equals(center.getWorld())) return false;
        if (!isGamemodeAllowed(player)) return false;
        if (!isWithinVerticalAwareRadius(center, player.getLocation(), config.targetingRadiusHorizontal(), config.targetingRadiusVertical())) return false;
        if (config.arenaEnabled() && arenaCenter != null && arenaCenter.getWorld() != null) {
            if (!player.getWorld().equals(arenaCenter.getWorld())) return false;
            return horizontalDistance(arenaCenter, player.getLocation()) <= config.arenaRadius() + 6.0D;
        }
        return true;
    }

    public boolean isWithinVerticalAwareRadius(Location center, Location check, double horizontalRadius, double verticalRadius) {
        if (center == null || check == null || center.getWorld() == null || check.getWorld() == null || !center.getWorld().equals(check.getWorld())) return false;
        double dy = check.getY() - center.getY();
        if (dy > 0 && !config.targetPlayersAbove()) return false;
        if (dy < 0 && !config.targetPlayersBelow()) return false;
        if (Math.abs(dy) > verticalRadius) return false;
        return horizontalDistance(center, check) <= horizontalRadius;
    }

    private boolean isGamemodeAllowed(Player player) {
        GameMode mode = player.getGameMode();
        return switch (mode) {
            case SURVIVAL -> config.targetingIncludeSurvival();
            case ADVENTURE -> config.targetingIncludeAdventure();
            case CREATIVE -> config.targetingIncludeCreative();
            case SPECTATOR -> config.targetingIncludeSpectator();
        };
    }

    private double horizontalDistance(Location a, Location b) {
        return Math.sqrt(horizontalDistanceSquared(a, b));
    }

    private double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private double verticalDistance(Location a, Location b) {
        return Math.abs(a.getY() - b.getY());
    }

    private void debug(String message) {
        if (config.debugTargeting()) plugin.getLogger().info("VelioraBoss Targeting: " + message);
    }
}
