package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class FishingEffectManager {

    private final FishingConfigManager configManager;
    private final FishItemFactory itemFactory;

    public FishingEffectManager(FishingConfigManager configManager, FishItemFactory itemFactory) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
    }

    public void play(Player player, CaughtFish fish) {
        if (player == null || fish == null) return;
        FishRarity rarity = fish.rarity();
        if (rarity.power() < FishRarity.ORNAMENTAL.power() || !configManager.isEffectEnabled(rarity)) return;
        Location location = player.getLocation().add(0.0D, 1.0D, 0.0D);
        player.getWorld().playSound(player.getLocation(), configManager.getEffectSound(rarity), 1.0F, 1.0F);
        int amount = configManager.getEffectAmount(rarity);
        if (amount > 0) player.getWorld().spawnParticle(configManager.getEffectParticle(rarity), location, amount, 0.6D, 0.8D, 0.6D, 0.02D);
        if (rarity == FishRarity.LEGENDARY || rarity == FishRarity.MITOLOGI) playGrandCatchEffect(player, rarity);
        if (configManager.isVisualLightning(rarity)) player.getWorld().strikeLightningEffect(player.getLocation());
        if (configManager.isEffectBroadcast(rarity)) broadcast(player, fish);
    }

    private void playGrandCatchEffect(Player player, FishRarity rarity) {
        boolean mythic = rarity == FishRarity.MITOLOGI;
        Location center = player.getLocation().add(0.0D, 1.0D, 0.0D);
        player.getWorld().playSound(center, mythic ? Sound.ENTITY_LIGHTNING_BOLT_THUNDER : Sound.UI_TOAST_CHALLENGE_COMPLETE,
                mythic ? 0.9F : 0.75F, mythic ? 0.8F : 1.15F);
        new BukkitRunnable() {
            private int wave;

            @Override
            public void run() {
                if (!player.isOnline() || wave >= (mythic ? 7 : 5)) {
                    cancel();
                    return;
                }
                double radius = 0.45D + (wave * 0.26D);
                Particle particle = mythic ? Particle.DRAGON_BREATH : Particle.TOTEM_OF_UNDYING;
                player.getWorld().spawnParticle(particle, center, mythic ? 26 : 16,
                        radius, 0.35D + radius / 3.0D, radius, 0.015D);
                player.getWorld().spawnParticle(Particle.END_ROD, center, mythic ? 14 : 8,
                        radius, 0.55D, radius, 0.01D);
                if (wave == 2 || (mythic && wave == 5)) {
                    player.getWorld().playSound(center, mythic ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            mythic ? 0.9F : 0.6F, mythic ? 0.7F : 1.35F);
                }
                wave++;
            }
        }.runTaskTimer(configManager.getPlugin(), 0L, 5L);
    }

    private void broadcast(Player player, CaughtFish fish) {
        String key = fish.rarity() == FishRarity.MITOLOGI ? "mitologi-broadcast" : "legendary-broadcast";
        String fallback = fish.rarity() == FishRarity.MITOLOGI
                ? "%prefix% &c%player% mendapatkan ikan Mitologi: &f%fish% &7(&f%weight%&7)!"
                : "%prefix% &6%player% mendapatkan ikan Legendary: &f%fish% &7(&f%weight%&7)!";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        placeholders.put("%fish%", fish.name());
        placeholders.put("%weight%", itemFactory.formatWeight(fish.weight()));
        String message = configManager.message(key, fallback);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) message = message.replace(entry.getKey(), entry.getValue());
        Bukkit.broadcastMessage(configManager.color(message));
    }
}
