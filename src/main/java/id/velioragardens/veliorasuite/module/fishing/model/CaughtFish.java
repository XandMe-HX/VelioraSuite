package id.velioragardens.veliorasuite.module.fishing.model;

public record CaughtFish(
        String id,
        String name,
        FishRarity rarity,
        double weight,
        int price,
        String origin,
        String region
) {
}
