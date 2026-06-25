package id.velioragardens.veliorasuite.module.clearlag;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.clearlag.model.ClearResult;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClearLagManager {

    private final VelioraSuite plugin;
    private final ClearLagConfigManager configManager;
    private final ClearLagItemCleaner itemCleaner;
    private final ClearLagMobCleaner mobCleaner;
    private final ClearLagProjectileCleaner projectileCleaner;
    private final ClearLagStatsManager statsManager;
    private ClearLagTaskManager taskManager;
    private ClearResult lastResult = ClearResult.empty();

    public ClearLagManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new ClearLagConfigManager(plugin);
        this.itemCleaner = new ClearLagItemCleaner(configManager);
        this.mobCleaner = new ClearLagMobCleaner(configManager);
        this.projectileCleaner = new ClearLagProjectileCleaner(configManager);
        this.statsManager = new ClearLagStatsManager();
    }

    public void load() {
        configManager.load();
        taskManager = new ClearLagTaskManager(plugin, this, configManager);
    }

    public void enable() {
        if (taskManager != null) taskManager.start();
    }

    public void reload() {
        configManager.load();
        if (taskManager != null) taskManager.restart();
    }

    public void shutdown() {
        if (taskManager != null) taskManager.stop();
    }

    public ClearLagConfigManager getConfigManager() { return configManager; }
    public ClearLagStatsManager getStatsManager() { return statsManager; }
    public ClearResult getLastResult() { return lastResult; }
    public int getNextClearSeconds() { return taskManager == null ? 0 : taskManager.getRemainingSeconds(); }

    public ClearResult clearItems(boolean broadcast) {
        int items = 0;
        for (World world : Bukkit.getWorlds()) items += itemCleaner.clear(world);
        lastResult = new ClearResult(items, lastResult.mobs(), lastResult.projectiles());
        if (broadcast) broadcast("clear-done", "%prefix% &aBerhasil membersihkan &f%items% &aitem jatuh.", Map.of("%items%", String.valueOf(items)));
        return new ClearResult(items, 0, 0);
    }

    public ClearResult clearMobs(CommandSender sender) {
        int mobs = 0;
        for (World world : Bukkit.getWorlds()) mobs += mobCleaner.clear(world);
        lastResult = new ClearResult(lastResult.items(), mobs, lastResult.projectiles());
        send(sender, mobs <= 0 ? "no-entities-cleared" : "clear-mobs-done", mobs <= 0 ? "%prefix% &eTidak ada entity yang perlu dibersihkan." : "%prefix% &aBerhasil membersihkan &f%mobs% &amob.", Map.of("%mobs%", String.valueOf(mobs)));
        return new ClearResult(0, mobs, 0);
    }

    public ClearResult clearProjectiles(CommandSender sender) {
        int projectiles = 0;
        for (World world : Bukkit.getWorlds()) projectiles += projectileCleaner.clear(world);
        lastResult = new ClearResult(lastResult.items(), lastResult.mobs(), projectiles);
        send(sender, projectiles <= 0 ? "no-entities-cleared" : "clear-projectiles-done", projectiles <= 0 ? "%prefix% &eTidak ada entity yang perlu dibersihkan." : "%prefix% &aBerhasil membersihkan &f%projectiles% &aprojectile.", Map.of("%projectiles%", String.valueOf(projectiles)));
        return new ClearResult(0, 0, projectiles);
    }

    public void broadcastWarning(int seconds) {
        broadcast("clear-warning", "%prefix% &eItem jatuh akan dibersihkan dalam &f%time% &edetik.", Map.of("%time%", String.valueOf(seconds)));
    }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("help", List.of(
                "&8&m--------------------------------",
                "&a&lVelioraClearLag",
                "&f/vclearlag status &7- Cek status clearlag.",
                "&f/vclearlag clear &7- Clear item jatuh.",
                "&f/vclearlag clear items &7- Clear item jatuh.",
                "&f/vclearlag clear mobs &7- Clear mob aman.",
                "&f/vclearlag clear projectiles &7- Clear projectile.",
                "&f/vclearlag tps &7- Cek TPS.",
                "&f/vclearlag memory &7- Cek memory.",
                "&f/vclearlag reload &7- Reload config.",
                "&8&m--------------------------------"
        )), Map.of());
    }

    public void sendStatus(CommandSender sender) {
        sendLines(sender, configManager.getMessageList("status", List.of(
                "&8&m--------------------------------",
                "&a&lVelioraClearLag Status",
                "&7Enabled: &f%enabled%",
                "&7Auto Clear: &f%auto_clear%",
                "&7Interval: &f%interval%s",
                "&7Next Clear: &f%next_clear%s",
                "&7Warnings: &f%warnings%",
                "&7Whitelist: &f%whitelist% item",
                "&7Last Clear: &f%last_clear%",
                "&7Memory: &f%memory%",
                "&8&m--------------------------------"
        )), statusPlaceholders());
    }

    public void sendMemory(CommandSender sender) {
        sender.sendMessage(configManager.color(configManager.getPrefix() + "&7Memory: &f" + statsManager.getMemoryLine()));
    }

    public void sendTps(CommandSender sender) {
        sender.sendMessage(configManager.color(configManager.getPrefix() + "&7TPS: &f" + statsManager.getTpsLine()));
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, "reload-success", "%prefix% &aVelioraClearLag berhasil direload.", Map.of());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of());
    }

    private Map<String, String> statusPlaceholders() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%enabled%", String.valueOf(configManager.isEnabled()));
        placeholders.put("%auto_clear%", String.valueOf(configManager.isAutoClearEnabled()));
        placeholders.put("%interval%", String.valueOf(configManager.getAutoClearIntervalSeconds()));
        placeholders.put("%next_clear%", String.valueOf(getNextClearSeconds()));
        placeholders.put("%warnings%", String.valueOf(configManager.getWarningSeconds()));
        placeholders.put("%whitelist%", String.valueOf(configManager.getIgnoredMaterials().size()));
        placeholders.put("%last_clear%", lastResult.items() + " items, " + lastResult.mobs() + " mobs, " + lastResult.projectiles() + " projectiles");
        placeholders.put("%memory%", statsManager.getMemoryLine());
        return placeholders;
    }

    private void broadcast(String path, String fallback, Map<String, String> placeholders) {
        Bukkit.broadcastMessage(configManager.color(apply(configManager.getMessage(path, fallback), placeholders)));
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.getMessage(path, fallback), placeholders)));
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) sender.sendMessage(configManager.color(apply(line, placeholders)));
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }
}
