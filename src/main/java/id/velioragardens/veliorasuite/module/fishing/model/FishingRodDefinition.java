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
        String aura
) { }
