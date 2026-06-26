package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import org.bukkit.OfflinePlayer;

public final class SkillsPlaceholderManager {

    private final SkillsConfigManager configManager;
    private final ManaManager manaManager;

    public SkillsPlaceholderManager(SkillsConfigManager configManager, ManaManager manaManager) {
        this.configManager = configManager;
        this.manaManager = manaManager;
    }

    public String getPlaceholder(OfflinePlayer player, String identifier) {
        if (player == null || identifier == null) return "";
        PlayerManaData data = manaManager.getData(player);
        return switch (identifier.toLowerCase()) {
            case "mana" -> String.valueOf(data.getMana());
            case "mana_max" -> String.valueOf(data.getMaxMana());
            case "mana_bar" -> buildManaBar(data.getMana(), data.getMaxMana());
            case "mana_percent" -> String.valueOf(percent(data.getMana(), data.getMaxMana()));
            default -> "";
        };
    }

    public String buildManaBar(int mana, int maxMana) {
        int length = configManager.getManaBarLength();
        int filled = maxMana <= 0 ? 0 : (int) Math.round((mana / (double) maxMana) * length);
        filled = Math.max(0, Math.min(length, filled));
        String symbol = configManager.getManaBarSymbol();
        return configManager.getManaBarFilledColor() + symbol.repeat(filled) + configManager.getManaBarEmptyColor() + symbol.repeat(length - filled);
    }

    private int percent(int mana, int maxMana) {
        if (maxMana <= 0) return 0;
        return Math.max(0, Math.min(100, (int) Math.round((mana * 100.0D) / maxMana)));
    }
}
