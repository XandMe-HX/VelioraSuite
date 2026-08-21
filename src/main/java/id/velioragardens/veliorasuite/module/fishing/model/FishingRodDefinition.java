package id.velioragardens.veliorasuite.module.fishing.model;

public record FishingRodDefinition(
        int tier,
        String name,
        String fromColor,
        String toColor,
        int price,
        int requiredCatches,
        int secondsBonus,
        int clickReduction,
        String aura,
        int luckPercent,
        double maxWeight,
        int speedPercent,
        boolean questRod
) {
    public FishingRodDefinition(int tier, String name, String fromColor, String toColor, int price,
                                int requiredCatches, int secondsBonus, int clickReduction, String aura) {
        this(tier, name, fromColor, toColor, price, requiredCatches, secondsBonus, clickReduction, aura,
                Math.max(0, (tier - 1) * 50), Math.max(10.0D, tier * 100.0D), Math.max(0, tier * 5), false);
    }
}
