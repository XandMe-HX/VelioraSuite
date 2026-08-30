package id.velioragardens.veliorasuite.module.adventure;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.team.TeamModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Optional bridge deliberately kept reflection based: VelioraSuite remains usable
 * when AuraSkills is not installed. Only safe gathering skills provide Adventure XP.
 */
public final class AuraSkillsAdventureBridge implements Listener {
    private static final String XP_EVENT = "dev.aurelium.auraskills.api.event.skill.XpGainEvent";
    private final VelioraSuite plugin;
    private final AdventureManager adventure;
    private boolean registered;
    private boolean waitingForAuraSkills;

    public AuraSkillsAdventureBridge(VelioraSuite plugin, AdventureManager adventure) {
        this.plugin = plugin;
        this.adventure = adventure;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void enable() {
        if (!adventure.config().auraSkillsEnabled()) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerAuraSkillsEvent();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (!"AuraSkills".equalsIgnoreCase(event.getPlugin().getName()) || registered) return;
        // PluginEnableEvent is fired while the other plugin is finishing its
        // enable sequence. Deferring one tick makes the bridge reliable across
        // AuraSkills releases without forcing a hard dependency.
        Bukkit.getScheduler().runTask(plugin, this::registerAuraSkillsEvent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerAuraSkillsEvent() {
        if (registered || !adventure.config().auraSkillsEnabled()) return;
        Plugin auraSkills = Bukkit.getPluginManager().getPlugin("AuraSkills");
        if (auraSkills == null || !auraSkills.isEnabled()) {
            if (!waitingForAuraSkills) {
                waitingForAuraSkills = true;
                plugin.getLogger().info("VelioraPetualang: menunggu AuraSkills aktif sebelum memasang bridge.");
            }
            return;
        }
        try {
            Class<?> rawEvent = Class.forName(XP_EVENT, false, auraSkills.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(rawEvent)) return;
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) rawEvent, this, EventPriority.MONITOR,
                    (listener, event) -> receive(event), plugin, true);
            registered = true;
            waitingForAuraSkills = false;
            plugin.getLogger().info("VelioraPetualang: bridge AuraSkills aktif untuk XP skill aman.");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("VelioraPetualang: API AuraSkills tidak cocok, bridge dilewati.");
        }
    }

    public void disable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        registered = false;
        waitingForAuraSkills = false;
    }

    private void receive(Event event) {
        try {
            Object playerValue = method(event, "getPlayer");
            Object skillValue = method(event, "getSkill");
            Object amountValue = method(event, "getAmount");
            if (!(playerValue instanceof Player player) || !(amountValue instanceof Number number) || skillValue == null) return;
            double sourceXp = number.doubleValue();
            String skill = String.valueOf(method(skillValue, "name")).toUpperCase(java.util.Locale.ROOT);
            if (sourceXp < adventure.config().auraSkillsMinimumXp() || !adventure.config().auraSkillEnabled(skill)) return;
            long adventureXp = (long) Math.floor(sourceXp * adventure.config().auraSkillsRatio());
            if (adventureXp > 0L) {
                adventure.addExperience(player, adventureXp);
                plugin.getModuleManager().getModule("team")
                        .filter(TeamModule.class::isInstance).map(TeamModule.class::cast)
                        .ifPresent(module -> module.getTeamManager().addActivityScore(player, Math.max(1L, adventureXp / 10L), "aktivitas " + skill.toLowerCase(java.util.Locale.ROOT)));
            }
        } catch (ReflectiveOperationException ignored) {
            // A future AuraSkills API change must never prevent normal XP gain.
        }
    }

    private Object method(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}
