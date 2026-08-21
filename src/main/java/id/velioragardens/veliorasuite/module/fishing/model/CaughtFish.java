package id.velioragardens.veliorasuite.module.fishing.model;

public record CaughtFish(
        String id,
        String name,
        FishRarity rarity,
        double weight,
        int price,
        String origin,
        String region,
        String mutation,
        double mutationMultiplier
) {
    public CaughtFish(String id, String name, FishRarity rarity, double weight, int price, String origin, String region) {
        this(id, name, rarity, weight, price, origin, region, "Normal", 1.0D);
    }
}
