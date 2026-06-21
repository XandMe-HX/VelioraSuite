package id.velioragardens.veliorasuite.module.skills;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class SkillsPlaceholderExpansion extends PlaceholderExpansion {

    private final SkillsModule skills;

    public SkillsPlaceholderExpansion(SkillsModule skills) {
        this.skills = skills;
    }

    @Override
    public String getIdentifier() {
        return "veliorasuite";
    }

    @Override
    public String getAuthor() {
        return "Veliora Gardens";
    }

    @Override
    public String getVersion() {
        return "1.6.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (!(offlinePlayer instanceof Player player)) return "";
        String key = params.toLowerCase();
        if (key.equals("mana")) return String.valueOf(skills.getMana(player));
        if (key.equals("max_mana")) return String.valueOf(skills.getMaxMana());
        if (key.endsWith("_level")) return String.valueOf(skills.getLevel(player.getUniqueId(), key.replace("_level", "")));
        if (key.endsWith("_exp")) return String.valueOf(skills.getExp(player.getUniqueId(), key.replace("_exp", "")));
        return null;
    }
}
