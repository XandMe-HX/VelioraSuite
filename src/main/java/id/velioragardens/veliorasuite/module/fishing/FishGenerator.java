package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishDefinition;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;

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
        FishRarity rarity = rollRarity();
        List<FishDefinition> candidates = new ArrayList<>();
        for (FishDefinition definition : configManager.getFishDefinitions().values()) {
            if (definition.rarity() == rarity) candidates.add(definition);
        }
        if (candidates.isEmpty()) candidates.addAll(configManager.getFishDefinitions().values());
        FishDefinition definition = candidates.get(random.nextInt(candidates.size()));
        double weight = randomDouble(definition.minWeight(), definition.maxWeight());
        int basePrice = randomInt(definition.minPrice(), definition.maxPrice());
        int price = finalPrice(definition.rarity(), definition.minWeight(), definition.maxWeight(), weight, basePrice);
        CaughtFish caughtFish = new CaughtFish(definition.id(), definition.name(), definition.rarity(), round(weight), price, "FISHING", definition.region());
        return new GeneratedFish(definition, caughtFish);
    }

    private FishRarity rollRarity() {
        double total = 0.0D;
        for (double chance : configManager.getRarityChances().values()) total += Math.max(0.0D, chance);
        if (total <= 0.0D) return FishRarity.COMMON;
        double roll = random.nextDouble() * total;
        double current = 0.0D;
        for (Map.Entry<FishRarity, Double> entry : configManager.getRarityChances().entrySet()) {
            current += Math.max(0.0D, entry.getValue());
            if (roll <= current) return entry.getKey();
        }
        return FishRarity.COMMON;
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
