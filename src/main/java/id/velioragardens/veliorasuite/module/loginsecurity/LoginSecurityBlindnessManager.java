package id.velioragardens.veliorasuite.module.loginsecurity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class LoginSecurityBlindnessManager {

    private static final Set<UUID> BLINDED_BY_LOGIN_SECURITY = new HashSet<>();

    private LoginSecurityBlindnessManager() {
    }

    public static void apply(Player player, LoginSecurityConfigManager configManager) {
        if (player == null || !player.isOnline() || configManager == null || !configManager.isBlindBeforeLoginEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!BLINDED_BY_LOGIN_SECURITY.contains(uuid) && player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            return;
        }

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                configManager.getBlindDurationTicks(),
                configManager.getBlindAmplifier(),
                false,
                configManager.isBlindParticles(),
                configManager.isBlindIcon()
        ));
        BLINDED_BY_LOGIN_SECURITY.add(uuid);
    }

    public static void remove(Player player) {
        if (player == null) {
            return;
        }

        if (BLINDED_BY_LOGIN_SECURITY.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }

    public static void sync(Player player, LoginSecurityManager manager) {
        if (player == null || manager == null) {
            return;
        }

        if (manager.isAuthenticated(player)) {
            remove(player);
        } else {
            apply(player, manager.getConfigManager());
        }
    }

    public static void clearAll() {
        for (UUID uuid : new HashSet<>(BLINDED_BY_LOGIN_SECURITY)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
        BLINDED_BY_LOGIN_SECURITY.clear();
    }
}
