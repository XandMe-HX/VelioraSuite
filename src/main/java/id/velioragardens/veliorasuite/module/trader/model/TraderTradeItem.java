package id.velioragardens.veliorasuite.module.trader.model;

import org.bukkit.Material;

import java.util.List;

public final class TraderTradeItem {

    private final String id;
    private final Material material;
    private final String name;
    private final int amount;
    private final List<String> lore;
    private final List<String> enchantments;
    private final int stock;
    private final int weight;
    private final int customDamage;
    private final int fishingLuckBonus;
    private final boolean unrepairable;
    private final TraderPaymentType paymentType;
    private final long money;
    private final List<TraderFishRequirement> fishRequirements;

    public TraderTradeItem(String id, Material material, String name, int amount, List<String> lore, List<String> enchantments, int stock, int weight, int customDamage, int fishingLuckBonus, boolean unrepairable, TraderPaymentType paymentType, long money, List<TraderFishRequirement> fishRequirements) {
        this.id = id;
        this.material = material;
        this.name = name;
        this.amount = Math.max(1, Math.min(64, amount));
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
        this.stock = Math.max(1, stock);
        this.weight = Math.max(1, weight);
        this.customDamage = Math.max(0, customDamage);
        this.fishingLuckBonus = Math.max(0, fishingLuckBonus);
        this.unrepairable = unrepairable;
        this.paymentType = paymentType == null ? TraderPaymentType.MONEY : paymentType;
        this.money = Math.max(0L, money);
        this.fishRequirements = fishRequirements == null ? List.of() : List.copyOf(fishRequirements);
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getName() { return name; }
    public int getAmount() { return amount; }
    public List<String> getLore() { return lore; }
    public List<String> getEnchantments() { return enchantments; }
    public int getStock() { return stock; }
    public int getWeight() { return weight; }
    public int getCustomDamage() { return customDamage; }
    public int getFishingLuckBonus() { return fishingLuckBonus; }
    public boolean isUnrepairable() { return unrepairable; }
    public TraderPaymentType getPaymentType() { return paymentType; }
    public long getMoney() { return money; }
    public List<TraderFishRequirement> getFishRequirements() { return fishRequirements; }
}
