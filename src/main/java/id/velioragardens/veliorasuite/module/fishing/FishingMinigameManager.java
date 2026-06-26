package id.velioragardens.veliorasuite.module.fishing;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.fishing.model.CaughtFish;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        removeCaught(event.getCaught());
        FishGenerator.GeneratedFish generatedFish = manager.getGenerator().generate(player);
        FishRarity rarity = generatedFish.fish().rarity();
        boolean skipMinigame = !manager.getConfigManager().isMinigameEnabled()
                || !manager.getConfigManager().isMinigameEnabledForRarity(rarity)
                || manager.getConfigManager().getSpamNeeded(rarity) <= 0
                || manager.getConfigManager().getMinigameSeconds(rarity) <= 0.0D
                || random.nextDouble() * 100.0D > manager.getConfigManager().getMinigameTriggerChance();
        if (skipMinigame) {
            manager.giveGeneratedFish(player, generatedFish);
            return;
        }
        start(player, generatedFish);
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
        showTitle(event.getPlayer(), session);
        if (session.clicks >= session.targetClicks) success(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        for (Session session : sessions.values()) session.cancelTask();
        sessions.clear();
    }

    private void start(Player player, FishGenerator.GeneratedFish generatedFish) {
        remove(player.getUniqueId());
        CaughtFish fish = generatedFish.fish();
        int target = manager.getConfigManager().getSpamNeeded(fish.rarity());
        double seconds = manager.getConfigManager().getMinigameSeconds(fish.rarity());
        Session session = new Session(generatedFish, target, System.currentTimeMillis() + (long) (seconds * 1000.0D));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> fail(player), Math.max(1L, (long) (seconds * 20.0D)));
        session.task = task;
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("minigame-start", "%prefix% &eTarikan kuat! Spam klik untuk menarik ikan!")));
        showTitle(player, session);
    }

    private void success(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cancelTask();
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("minigame-success", "%prefix% &aBerhasil menarik ikan!")));
        manager.giveGeneratedFish(player, session.generatedFish);
    }

    private void fail(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cancelTask();
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().message("minigame-fail", "%prefix% &cIkan lepas.")));
    }

    private void showTitle(Player player, Session session) {
        if (!manager.getConfigManager().isMinigameShowTitle()) return;
        FishRarity rarity = session.generatedFish.fish().rarity();
        double secondsLeft = Math.max(0.0D, (session.endAt - System.currentTimeMillis()) / 1000.0D);
        String title = rarity.color() + rarity.name() + " &8» &f" + session.clicks + "&7/&f" + session.targetClicks;
        String subtitle = "&e" + String.format("%.1f", secondsLeft) + "s";
        player.sendTitle(manager.getConfigManager().color(title), manager.getConfigManager().color(subtitle), 0, 20, 5);
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
        private final int targetClicks;
        private final long endAt;
        private int clicks;
        private long lastClick;
        private BukkitTask task;

        private Session(FishGenerator.GeneratedFish generatedFish, int targetClicks, long endAt) {
            this.generatedFish = generatedFish;
            this.targetClicks = targetClicks;
            this.endAt = endAt;
        }

        private void cancelTask() {
            if (task != null) task.cancel();
        }
    }
}
