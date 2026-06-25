package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class ManaActionBarTask {

    private final VelioraSuite plugin;
    private final SkillsConfigManager configManager;
    private final ManaManager manaManager;
    private BukkitTask task;

    public ManaActionBarTask(VelioraSuite plugin, SkillsConfigManager configManager, ManaManager manaManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.manaManager = manaManager;
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
        PlayerManaData data = manaManager.getData(player);
        String text = configManager.getActionBarFormat()
                .replace("%health%", String.valueOf(Math.max(0, (int) Math.ceil(player.getHealth()))))
                .replace("%player_ping%", String.valueOf(player.getPing()))
                .replace("%veliorasuite_mana%", String.valueOf(data.getMana()))
                .replace("%veliorasuite_mana_max%", String.valueOf(data.getMaxMana()))
                .replace("%vault_eco_balance_formatted%", "N/A");

        if (configManager.isPlaceholderApiEnabled() && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                text = (String) papi.getMethod("setPlaceholders", Player.class, String.class).invoke(null, player, text);
            } catch (Exception ignored) {
                // PlaceholderAPI is optional.
            }
        }

        String colored = ChatColor.translateAlternateColorCodes('&', text);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
    }
}
