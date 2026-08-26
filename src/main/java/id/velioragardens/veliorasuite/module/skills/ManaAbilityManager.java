package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.quest.QuestModule;
import id.velioragardens.veliorasuite.module.quest.model.QuestCategory;
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
        int abilityLevel = abilityLevel(player, ability);
        if (abilityLevel <= 0) {
            QuestCategory category = QuestCategory.fromKey(config.getAbilitySkill(ability));
            String skill = category == null ? config.getAbilitySkill(ability) : category.key();
            send(player, "&eAbility ini terbuka pada &f" + skill + " &elevel &f" + config.getAbilityUnlockLevel(ability) + "&e.");
            return false;
        }
        long remaining = remaining(player, ability);
        if (remaining > 0) {
            send(player, "&eAbility masih cooldown &f" + ((remaining + 999L) / 1000L) + " detik&e.");
            return false;
        }
        int cost = Math.max(0, config.getAbilityCost(ability) + config.getAbilityCostPerLevel(ability) * (abilityLevel - 1));
        if (!mana.takeMana(player, cost, "ability:" + ability)) {
            send(player, "&cMana tidak cukup. Dibutuhkan &f" + cost + " Mana&c.");
            return false;
        }
        int seconds = Math.max(1, config.getAbilityDuration(ability) + config.getAbilityDurationPerLevel(ability) * (abilityLevel - 1));
        switch (ability) {
            case "miner" -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, seconds * 20, 0, false, true));
            case "guardian" -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, false, true));
            case "dash" -> {
                player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.35D).setY(0.32D));
                player.setFallDistance(0F);
            }
            case "fisher" -> player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, seconds * 20, 1, false, true));
            case "chef" -> {
                player.setFoodLevel(20);
                player.setSaturation(Math.min(20F, player.getSaturation() + 8F));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, false, true));
            }
            default -> {
                mana.giveMana(player, cost, "ability:refund");
                return false;
            }
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + Math.max(1, config.getAbilityCooldown(ability) + config.getAbilityCooldownPerLevel(ability) * (abilityLevel - 1)) * 1000L);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 18, 0.45, 0.6, 0.45, 0.02);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9F, 1.25F);
        send(player, "&aAbility &f" + ability + " &aLv.&f" + abilityLevel + " &aaktif. Mana tersisa &b" + mana.getMana(player) + "/" + mana.getMaxMana(player) + "&a.");
        return true;
    }

    private long remaining(Player player, String ability) {
        return Math.max(0L, cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(ability, 0L) - System.currentTimeMillis());
    }

    private int abilityLevel(Player player, String ability) {
        QuestModule module = plugin.getModuleManager().getModule("quest")
                .filter(QuestModule.class::isInstance).map(QuestModule.class::cast).orElse(null);
        QuestCategory category = QuestCategory.fromKey(config.getAbilitySkill(ability));
        if (module == null || module.getQuestManager() == null || category == null) return 0;
        int skillLevel = module.getQuestManager().getDataManager().getOrCreate(player).getCategoryProgress(category).getLevel();
        int unlock = config.getAbilityUnlockLevel(ability);
        if (skillLevel < unlock) return 0;
        int level = ((skillLevel - unlock) / config.getAbilityLevelInterval(ability)) + 1;
        int cap = config.getAbilityMaxLevel(ability);
        return cap > 0 ? Math.min(cap, level) : level;
    }

    private void send(Player player, String text) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getPrefix() + text));
    }
}

