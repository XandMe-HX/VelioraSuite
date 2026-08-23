package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.api.VelioraModule;
import id.velioragardens.veliorasuite.module.fishing.FishingModule;
import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;

public final class ManaActionBarTask {

    private final VelioraSuite plugin;
    private final SkillsConfigManager configManager;
    private final ManaManager manaManager;
    private final SkillsPlaceholderManager placeholderManager;
    private BukkitTask task;

    public ManaActionBarTask(VelioraSuite plugin, SkillsConfigManager configManager, ManaManager manaManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.manaManager = manaManager;
        this.placeholderManager = new SkillsPlaceholderManager(configManager, manaManager);
    }

    public void start() {
        stop();
        if (!configManager.isEnabled() || !configManager.isActionBarEnabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, configManager.getActionBarIntervalTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!configManager.isEnabled() || !configManager.isActionBarEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (configManager.getDisabledWorlds().contains(player.getWorld().getName())) continue;
            send(player);
        }
    }

    private void send(Player player) {
        if (isFishingMinigameActive(player)) return;
        PlayerManaData data = manaManager.getData(player);
        String text = configManager.getActionBarFormat()
                .replace("%health%", String.valueOf(Math.max(0, (int) Math.ceil(player.getHealth()))))
                .replace("%health_max%", String.valueOf(Math.max(1, (int) Math.ceil(player.getAttribute(Attribute.MAX_HEALTH) == null
                        ? 20.0D : player.getAttribute(Attribute.MAX_HEALTH).getValue()))))
                .replace("%player_ping%", String.valueOf(player.getPing()))
                .replace("%veliorasuite_mana%", String.valueOf(data.getMana()))
                .replace("%veliorasuite_mana_max%", String.valueOf(data.getMaxMana()))
                .replace("%veliorasuite_mana_bar%", placeholderManager.buildManaBar(data.getMana(), data.getMaxMana()))
                .replace("%veliorasuite_mana_percent%", String.valueOf(manaPercent(data.getMana(), data.getMaxMana())));

        if (configManager.isPlaceholderApiEnabled() && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                text = (String) papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class).invoke(null, player, text);
            } catch (Exception ignored) {
                // PlaceholderAPI is optional.
            }
        }

        if (text.contains("%vault_eco_balance_formatted%")) {
            text = text.replace("%vault_eco_balance_formatted%", "N/A");
        }

        String colored = ChatColor.translateAlternateColorCodes('&', text);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
    }

    private boolean isFishingMinigameActive(Player player) {
        if (plugin.getModuleManager() == null) return false;
        Optional<VelioraModule> module = plugin.getModuleManager().getModule("fishing");
        if (module.isEmpty() || !(module.get() instanceof FishingModule fishingModule)
                || fishingModule.getFishingManager() == null
                || fishingModule.getFishingManager().getMinigameManager() == null) {
            return false;
        }
        return fishingModule.getFishingManager().getMinigameManager().isActive(player);
    }

    private int manaPercent(int mana, int maxMana) {
        if (maxMana <= 0) return 0;
        return Math.max(0, Math.min(100, (int) Math.round((mana * 100.0D) / maxMana)));
    }
}
