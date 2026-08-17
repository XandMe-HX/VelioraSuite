package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ManaAbilityManager {
    private final VelioraSuite plugin;
    private final SkillsConfigManager config;
    private final ManaManager mana;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public ManaAbilityManager(VelioraSuite plugin, SkillsConfigManager config, ManaManager mana) {
        this.plugin = plugin;
        this.config = config;
        this.mana = mana;
    }

    public boolean activate(Player player, String rawAbility) {
        String ability = rawAbility == null ? "" : rawAbility.toLowerCase(Locale.ROOT);
        if (!config.isAbilityEnabled(ability)) {
            send(player, "&cAbility tidak ditemukan atau sedang dimatikan.");
            return false;
        }
        if (config.getAbilityBlockedWorlds().stream().anyMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()))) {
            send(player, "&cAbility Mana tidak dapat digunakan di world ini.");
            return false;
        }
        long remaining = remaining(player, ability);
        if (remaining > 0) {
            send(player, "&eAbility masih cooldown &f" + ((remaining + 999L) / 1000L) + " detik&e.");
            return false;
        }
        int cost = config.getAbilityCost(ability);
        if (!mana.takeMana(player, cost, "ability:" + ability)) {
            send(player, "&cMana tidak cukup. Dibutuhkan &f" + cost + " Mana&c.");
            return false;
        }
        int seconds = config.getAbilityDuration(ability);
        switch (ability) {
            case "miner" -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, seconds * 20, 0, false, true));
            case "guardian" -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, false, true));
            case "dash" -> {
                player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.35D).setY(0.32D));
                player.setFallDistance(0F);
            }
            case "fisher" -> player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, seconds * 20, 1, false, true));
            default -> {
                mana.giveMana(player, cost, "ability:refund");
                return false;
            }
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + config.getAbilityCooldown(ability) * 1000L);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 18, 0.45, 0.6, 0.45, 0.02);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9F, 1.25F);
        send(player, "&aAbility &f" + ability + " &aaktif. Mana tersisa &b" + mana.getMana(player) + "/" + mana.getMaxMana(player) + "&a.");
        return true;
    }

    private long remaining(Player player, String ability) {
        return Math.max(0L, cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(ability, 0L) - System.currentTimeMillis());
    }

    private void send(Player player, String text) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getPrefix() + text));
    }
}

