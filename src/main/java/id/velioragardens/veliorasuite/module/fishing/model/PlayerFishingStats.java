package id.velioragardens.veliorasuite.module.fishing.model;

import java.util.UUID;

public final class PlayerFishingStats {

    private final UUID uuid;
    private String name;
    private int totalCatches;
    private int totalSold;
    private long totalMoneyEarned;
    private FishRarity bestRarity;
    private String bestFishName;
    private double bestFishWeight;
    private int bestFishPrice;

    public PlayerFishingStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.bestRarity = FishRarity.TRASH;
        this.bestFishName = "-";
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTotalCatches() { return totalCatches; }
    public void setTotalCatches(int totalCatches) { this.totalCatches = Math.max(0, totalCatches); }
    public void addCatch() { this.totalCatches++; }
    public int getTotalSold() { return totalSold; }
    public void setTotalSold(int totalSold) { this.totalSold = Math.max(0, totalSold); }
    public void addSold(int amount) { this.totalSold += Math.max(0, amount); }
    public long getTotalMoneyEarned() { return totalMoneyEarned; }
    public void setTotalMoneyEarned(long totalMoneyEarned) { this.totalMoneyEarned = Math.max(0, totalMoneyEarned); }
    public void addMoneyEarned(long amount) { this.totalMoneyEarned += Math.max(0, amount); }
    public FishRarity getBestRarity() { return bestRarity; }
    public void setBestRarity(FishRarity bestRarity) { this.bestRarity = bestRarity == null ? FishRarity.TRASH : bestRarity; }
    public String getBestFishName() { return bestFishName; }
    public void setBestFishName(String bestFishName) { this.bestFishName = bestFishName == null ? "-" : bestFishName; }
    public double getBestFishWeight() { return bestFishWeight; }
    public void setBestFishWeight(double bestFishWeight) { this.bestFishWeight = Math.max(0, bestFishWeight); }
    public int getBestFishPrice() { return bestFishPrice; }
    public void setBestFishPrice(int bestFishPrice) { this.bestFishPrice = Math.max(0, bestFishPrice); }
}
