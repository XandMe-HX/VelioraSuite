package id.velioragardens.veliorasuite.module.fishing.model;

public final class FishingBagEntry {

    private final String key;
    private final CaughtFish fish;
    private int amount;

    public FishingBagEntry(String key, CaughtFish fish, int amount) {
        this.key = key;
        this.fish = fish;
        this.amount = Math.max(0, amount);
    }

    public String getKey() { return key; }
    public CaughtFish getFish() { return fish; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = Math.max(0, amount); }
    public void addAmount(int amount) { this.amount += Math.max(0, amount); }
    public void removeAmount(int amount) { this.amount = Math.max(0, this.amount - Math.max(0, amount)); }
}
