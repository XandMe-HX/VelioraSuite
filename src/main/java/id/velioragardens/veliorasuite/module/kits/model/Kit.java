package id.velioragardens.veliorasuite.module.kits.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class Kit {

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final List<String> description;
    private final String permission;
    private final int premiumLevel;
    private final long cooldownMillis;
    private final boolean buyEnabled;
    private final double price;
    private final boolean oneTimePurchase;
    private final KitGuiItem guiItem;
    private final List<ItemStack> items;
    private final KitReward reward;

    public Kit(
            String id,
            boolean enabled,
            String displayName,
            List<String> description,
            String permission,
            int premiumLevel,
            long cooldownMillis,
            boolean buyEnabled,
            double price,
            boolean oneTimePurchase,
            KitGuiItem guiItem,
            List<ItemStack> items,
            KitReward reward
    ) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName == null ? id : displayName;
        this.description = description == null ? List.of() : List.copyOf(description);
        this.permission = permission == null ? "" : permission;
        this.premiumLevel = Math.max(0, premiumLevel);
        this.cooldownMillis = Math.max(0L, cooldownMillis);
        this.buyEnabled = buyEnabled;
        this.price = Math.max(0.0D, price);
        this.oneTimePurchase = oneTimePurchase;
        this.guiItem = guiItem;
        this.items = items == null ? List.of() : items.stream().map(ItemStack::clone).toList();
        this.reward = reward == null ? new KitReward(0, 0, List.of(), List.of()) : reward;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public String getPermission() {
        return permission;
    }

    public int getPremiumLevel() {
        return premiumLevel;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public boolean isBuyEnabled() {
        return buyEnabled;
    }

    public double getPrice() {
        return price;
    }

    public boolean isOneTimePurchase() {
        return oneTimePurchase;
    }

    public KitGuiItem getGuiItem() {
        return guiItem;
    }

    public List<ItemStack> getItems() {
        return items.stream().map(ItemStack::clone).toList();
    }

    public KitReward getReward() {
        return reward;
    }
}
