package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import id.velioragardens.veliorasuite.core.effects.VelioraEffects.Priority;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Locale;

public final class FishingMinigameManager implements Listener {

    private final VelioraSuite plugin;
    private final FishingManager manager;
    private final Random random = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();

    public FishingMinigameManager(VelioraSuite plugin, FishingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            int potionSpeed = manager.getPotionManager().active(event.getPlayer(), "lure") ? 25 : 0;
            int speed = Math.min(80, manager.getRodManager().speedPercent(event.getPlayer()) + potionSpeed);
            int min = Math.max(20, (int) Math.round(100 * (1.0D - speed / 100.0D)));
            int max = Math.max(min + 20, (int) Math.round(600 * (1.0D - speed / 100.0D)));
            event.getHook().setMinWaitTime(min);
            event.getHook().setMaxWaitTime(max);
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        removeCaught(event.getCaught());
        FishGenerator.GeneratedFish generatedFish = manager.getGenerator().generate(player);
        FishRarity rarity = generatedFish.fish().rarity();
        if (!canHold(player, rarity)) {
            lineSnap(player, generatedFish.fish());
            return;
        }
        boolean skipMinigame = !manager.getConfigManager().isMinigameEnabled()
                || !manager.getConfigManager().isMinigameEnabledForRarity(rarity)
                || manager.getConfigManager().getSpamNeeded(rarity) <= 0
                || manager.getConfigManager().getMinigameSeconds(rarity) <= 0.0D
                || random.nextDouble() * 100.0D > manager.getConfigManager().getMinigameTriggerChance();
        if (skipMinigame) {
            manager.giveGeneratedFish(player, generatedFish);
            return;
        }
        start(player, generatedFish, event.getHook());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - session.lastClick < manager.getConfigManager().getClickCooldownMs()) return;
        session.lastClick = now;
        session.clicks++;
        showActionBar(event.getPlayer(), session);
        if (session.clicks >= session.targetClicks) success(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    public boolean isActive(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public void clear() {
        for (Session session : sessions.values()) session.cancelTask();
        sessions.clear();
    }

    private void start(Player player, FishGenerator.GeneratedFish generatedFish, FishHook hook) {
        remove(player.getUniqueId());
        CaughtFish fish = generatedFish.fish();
        int target = Math.max(1, manager.getConfigManager().getSpamNeeded(fish.rarity()) - manager.getRodManager().clickReduction(player));
        double seconds = manager.getConfigManager().getMinigameSeconds(fish.rarity()) + manager.getRodManager().secondsBonus(player);
        Session session = new Session(generatedFish, hook, target, System.currentTimeMillis() + (long) (seconds * 1000.0D));
        sessions.put(player.getUniqueId(), session);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (sessions.get(player.getUniqueId()) != session) return;
            if (!player.isOnline() || System.currentTimeMillis() >= session.endAt) {
                fail(player);
                return;
            }
            showActionBar(player, session);
            manager.getRodManager().showAura(player, session.hook);
            showRodAndHookEffect(player, session);
        }, 0L, 5L);
        session.task = task;
        showActionBar(player, session);
    }

    private void success(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cancelTask();
        manager.giveGeneratedFish(player, session.generatedFish);
    }

    private void fail(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cancelTask();
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("minigame-fail", "%prefix% &cIkan lepas.")));
    }

    private void showActionBar(Player player, Session session) {
        if (!manager.getConfigManager().isMinigameShowTitle()) return;
        double secondsLeft = Math.max(0.0D, (session.endAt - System.currentTimeMillis()) / 1000.0D);
        int bars = 32;
        int filled = Math.min(bars, (int) Math.ceil((session.clicks / (double) session.targetClicks) * bars));
        int remaining = Math.max(0, session.targetClicks - session.clicks);
        String green = "|".repeat(filled);
        String empty = "|".repeat(Math.max(0, bars - filled));
        String timeColor = secondsLeft <= 3.0D ? "&c" : "&e";
        String text = "&bTarikan &7[&a" + green + "&8" + empty + "&7] &f"
                + session.clicks + "&7/&f" + session.targetClicks + " &8• &fSisa: &e"
                + remaining + " klik &8• " + timeColor + String.format(Locale.US, "%.1fs", secondsLeft);
        player.sendActionBar(manager.getConfigManager().color(text));
    }

    private void showRodAndHookEffect(Player player, Session session) {
        FishRarity rarity = session.generatedFish.fish().rarity();
        if (rarity.power() < FishRarity.EPIC.power()) return;
        boolean mythic = rarity.power() >= FishRarity.MITOLOGI.power();
        Particle playerParticle = mythic ? Particle.DRAGON_BREATH : Particle.ENCHANT;
        Particle hookParticle = mythic ? Particle.TOTEM_OF_UNDYING : Particle.END_ROD;
        plugin.getEffects().particle(player.getLocation().add(0.0D, 1.0D, 0.0D), playerParticle,
                mythic ? 7 : 3, 0.35D, 0.55D, 0.35D, 0.01D, mythic ? Priority.IMPORTANT : Priority.GAMEPLAY);
        if (session.hook != null && session.hook.isValid()) {
            plugin.getEffects().particle(session.hook.getLocation(), hookParticle,
                    mythic ? 10 : 4, 0.18D, 0.18D, 0.18D, 0.01D, mythic ? Priority.IMPORTANT : Priority.GAMEPLAY);
        }
    }

    /** High rarities can appear early, but a weak rod visibly loses the fight instead of silently awarding them. */
    private boolean canHold(Player player, FishRarity rarity) {
        int tier = manager.getRodManager().getTier(player);
        int required = switch (rarity) {
            case EPIC -> 5;
            case LEGENDARY -> 10;
            case MITOLOGI -> 14;
            case SECRET -> 18;
            default -> 1;
        };
        return tier >= required;
    }

    private void lineSnap(Player player, CaughtFish fish) {
        int required = switch (fish.rarity()) {
            case EPIC -> 5; case LEGENDARY -> 10; case MITOLOGI -> 14; case SECRET -> 18; default -> 1;
        };
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("rod-too-weak", "%prefix% &cTarikan &f%fish% &cterlepas! Butuh Rod Tier &f%tier%&c atau lebih.")
                .replace("%fish%", fish.name()).replace("%tier%", String.valueOf(required))));
        player.sendActionBar(manager.getConfigManager().color("&c✖ Pancingan tidak cukup kuat — ikan lepas!"));
        plugin.getEffects().ring(player.getLocation(), Particle.SMOKE, 0.75D, 10, 0.25D);
        plugin.getEffects().sound(player.getLocation(), org.bukkit.Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.75F, 0.55F);
    }

    private void removeCaught(Entity entity) {
        if (entity != null && manager.getConfigManager().isRemoveVanillaCaughtEntity()) entity.remove();
    }

    private void remove(UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session != null) session.cancelTask();
    }

    private static final class Session {
        private final FishGenerator.GeneratedFish generatedFish;
        private final FishHook hook;
        private final int targetClicks;
        private final long endAt;
        private int clicks;
        private long lastClick;
        private BukkitTask task;

        private Session(FishGenerator.GeneratedFish generatedFish, FishHook hook, int targetClicks, long endAt) {
            this.generatedFish = generatedFish;
            this.hook = hook;
            this.targetClicks = targetClicks;
            this.endAt = endAt;
        }

        private void cancelTask() {
            if (task != null) task.cancel();
        }
    }
}
