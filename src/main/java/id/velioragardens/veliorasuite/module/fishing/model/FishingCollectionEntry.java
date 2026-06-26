package id.velioragardens.veliorasuite.module.fishing.model;

public final class FishingCollectionEntry {

    private final String fishId;
    private final int totalCaught;
    private final double bestWeight;

    public FishingCollectionEntry(String fishId, int totalCaught, double bestWeight) {
        this.fishId = fishId;
        this.totalCaught = Math.max(0, totalCaught);
        this.bestWeight = Math.max(0.0D, bestWeight);
    }

    public String getFishId() { return fishId; }
    public int getTotalCaught() { return totalCaught; }
    public double getBestWeight() { return bestWeight; }
}
