package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishDefinition;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import id.velioragardens.veliorasuite.module.trader.TraderFishingHook;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import id.velioragardens.veliorasuite.module.fishing.model.FishingRodDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class FishGenerator {

    private final FishingConfigManager configManager;
    private final Random random = new Random();

    public FishGenerator(FishingConfigManager configManager) {
        this.configManager = configManager;
    }

    public GeneratedFish generate() {
        return generate(null);
    }

    public GeneratedFish generate(Player player) {
        FishRarity rarity = rollRarity(player);
        FishingRodDefinition activeRod = configManager.getRodDefinition(rodTier(player));
        double weightLimit = player == null ? Double.MAX_VALUE : activeRod == null ? 10.0D : activeRod.maxWeight();
        List<FishDefinition> candidates = new ArrayList<>();
        for (FishDefinition definition : configManager.getFishDefinitions().values()) {
            if (definition.rarity() == rarity && definition.minWeight() <= weightLimit) candidates.add(definition);
        }
        List<FishDefinition> biomeMatches = candidates.stream().filter(definition -> biomeMatches(player, definition.region())).toList();
        if (!biomeMatches.isEmpty()) candidates = new ArrayList<>(biomeMatches);
        if (candidates.isEmpty()) {
            for (FishDefinition definition : configManager.getFishDefinitions().values()) {
                if (definition.minWeight() <= weightLimit) candidates.add(definition);
            }
        }
        if (candidates.isEmpty()) candidates.addAll(configManager.getFishDefinitions().values());
        FishDefinition definition = candidates.get(random.nextInt(candidates.size()));
        double weight = randomDouble(definition.minWeight(), Math.min(definition.maxWeight(), weightLimit));
        int basePrice = randomInt(definition.minPrice(), definition.maxPrice());
        int price = finalPrice(definition.rarity(), definition.minWeight(), definition.maxWeight(), weight, basePrice);
        FishingConfigManager.MutationRoll mutation = configManager.rollMutation(player);
        price = (int) Math.min(Integer.MAX_VALUE, Math.round(price * mutation.multiplier()));
        CaughtFish caughtFish = new CaughtFish(definition.id(), definition.name(), definition.rarity(), round(weight), price,
                definition.origin(), definition.region(), mutation.name(), mutation.multiplier());
        return new GeneratedFish(definition, caughtFish);
    }

    private FishRarity rollRarity(Player player) {
        int tier = rodTier(player);
        FishingRodDefinition rod = configManager.getRodDefinition(tier);
        int luckBonus = TraderFishingHook.getFishingLuckBonus(player) + (rod == null ? 0 : rod.luckPercent());
        if (potionActive(player, "luck")) luckBonus += 50;
        double total = 0.0D;
        for (Map.Entry<FishRarity, Double> entry : configManager.getRarityChances().entrySet()) total += adjustedChance(entry.getKey(), gatedChance(entry.getKey(), entry.getValue(), tier), luckBonus);
        if (total <= 0.0D) return FishRarity.COMMON;
        double roll = random.nextDouble() * total;
        double current = 0.0D;
        for (Map.Entry<FishRarity, Double> entry : configManager.getRarityChances().entrySet()) {
            current += adjustedChance(entry.getKey(), gatedChance(entry.getKey(), entry.getValue(), tier), luckBonus);
            if (roll <= current) return entry.getKey();
        }
        return FishRarity.COMMON;
    }

    private double gatedChance(FishRarity rarity, double chance, int tier) {
        if (rarity == FishRarity.SECRET && tier < 16) return 0.0D;
        if (rarity == FishRarity.MITOLOGI && tier < 8) return chance * 0.1D;
        return chance;
    }

    private int rodTier(Player player) {
        if (player == null || !player.getInventory().getItemInMainHand().hasItemMeta()) return 0;
        Integer tier = player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(configManager.getPlugin(), "fishing_rod_tier"), PersistentDataType.INTEGER);
        return tier == null ? 0 : Math.max(0, tier);
    }

    private boolean potionActive(Player player, String type) {
        if (player == null) return false;
        Long until = player.getPersistentDataContainer().get(new NamespacedKey(configManager.getPlugin(),
                "fishing_potion_" + type), PersistentDataType.LONG);
        return until != null && until > System.currentTimeMillis();
    }

    private boolean biomeMatches(Player player, String region) {
        if (player == null || region == null) return true;
        String biome = player.getLocation().getBlock().getBiome().getKey().getKey().toLowerCase(java.util.Locale.ROOT);
        String wanted = region.toLowerCase(java.util.Locale.ROOT);
        if (wanted.contains("deep ocean")) return biome.contains("deep") && biome.contains("ocean");
        if (wanted.equals("ocean")) return biome.contains("ocean");
        if (wanted.contains("beach")) return biome.contains("beach") || biome.contains("shore");
        return true;
    }

    private double adjustedChance(FishRarity rarity, double baseChance, int luckBonus) {
        double chance = Math.max(0.0D, baseChance);
        if (luckBonus <= 0) return chance;
        double bonus = Math.min(30.0D, luckBonus) / 100.0D;
        if (rarity == FishRarity.TRASH || rarity == FishRarity.VANILLA || rarity == FishRarity.COMMON) return Math.max(0.01D, chance * (1.0D - (bonus * 0.5D)));
        double multiplier = 1.0D + bonus;
        if (rarity == FishRarity.MITOLOGI || rarity == FishRarity.SECRET) multiplier = 1.0D + (bonus * 0.25D);
        return chance * multiplier;
    }

    private int finalPrice(FishRarity rarity, double minWeight, double maxWeight, double weight, int basePrice) {
        double range = Math.max(0.1D, maxWeight - minWeight);
        double normalized = Math.max(0.0D, Math.min(1.0D, (weight - minWeight) / range));
        int bonus = (int) Math.round(normalized * (configManager.maxPrice(rarity) * 0.35D));
        int finalPrice = basePrice + bonus;
        return Math.min(configManager.maxFinalPrice(rarity), finalPrice);
    }

    private double randomDouble(double min, double max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private int randomInt(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private double round(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    public record GeneratedFish(FishDefinition definition, CaughtFish fish) {
    }
}
