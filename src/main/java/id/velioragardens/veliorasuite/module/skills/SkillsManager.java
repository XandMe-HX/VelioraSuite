package id.velioragardens.veliorasuite.module.skills;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.skills.model.PlayerManaData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SkillsManager {

    private final VelioraSuite plugin;
    private final SkillsConfigManager configManager;
    private final ManaDataManager dataManager;
    private final ManaManager manaManager;
    private final SkillsPlaceholderManager placeholderManager;
    private final SkillsApi api;
    private final ManaActionBarTask actionBarTask;
    private final ManaResetTask resetTask;

    public SkillsManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new SkillsConfigManager(plugin);
        this.dataManager = new ManaDataManager(plugin, configManager);
        this.manaManager = new ManaManager(configManager, dataManager);
        this.placeholderManager = new SkillsPlaceholderManager(configManager, manaManager);
        this.api = new SkillsApi(manaManager);
        this.actionBarTask = new ManaActionBarTask(plugin, configManager, manaManager);
        this.resetTask = new ManaResetTask(plugin, configManager, manaManager);
    }

    public void load() {
        configManager.load();
        dataManager.load();
    }

    public void enable() {
        actionBarTask.start();
        resetTask.start();
    }

    public void reload() {
        configManager.load();
        actionBarTask.start();
        resetTask.start();
    }

    public void shutdown() {
        actionBarTask.stop();
        resetTask.stop();
        dataManager.flush();
    }

    public SkillsConfigManager getConfigManager() { return configManager; }
    public ManaManager getManaManager() { return manaManager; }
    public SkillsPlaceholderManager getPlaceholderManager() { return placeholderManager; }
    public SkillsApi getApi() { return api; }

    public void sendHelp(CommandSender sender) {
        sendLines(sender, configManager.messageList("help", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraSkills",
                "&f/vskills mana &7- Cek mana kamu.",
                "&f/vskills mana <player> &7- Cek mana player.",
                "&f/vskills status &7- Cek status module.",
                "&f/vskills reload &7- Reload config.",
                "&8&m--------------------------------"
        )), Map.of());
        if (configManager.hasManaAdmin(sender)) {
            sendLines(sender, configManager.messageList("help-admin", List.of(
                    "&8&m--------------------------------",
                    "&b&lVelioraSkills Admin",
                    "&f/vskills mana set <player> <amount> &7- Set mana player.",
                    "&f/vskills mana add <player> <amount> &7- Tambah mana player.",
                    "&f/vskills mana remove <player> <amount> &7- Kurangi mana player.",
                    "&f/vskills mana reset <player> &7- Reset mana player.",
                    "&8&m--------------------------------"
            )), Map.of());
        }
    }

    public void sendStatus(CommandSender sender) {
        sendLines(sender, configManager.messageList("status", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraSkills Status",
                "&7Enabled: &f%enabled%",
                "&7Actionbar: &f%actionbar%",
                "&7PlaceholderAPI: &f%placeholderapi%",
                "&7Default Mana: &f%default_mana%",
                "&7Default Max Mana: &f%default_max_mana%",
                "&7Daily Reset: &f%daily_reset%",
                "&7Reset Time: &f%reset_time%",
                "&8&m--------------------------------"
        )), statusPlaceholders());
    }

    public void sendManaSelf(Player player) {
        PlayerManaData data = manaManager.getData(player);
        send(player, "mana-status", "%prefix% &bMana kamu: &f%mana%&7/&f%max_mana%", manaPlaceholders(data, 0));
    }

    public void sendManaOther(CommandSender sender, PlayerManaData data) {
        send(sender, "mana-status-other", "%prefix% &bMana &f%player%&b: &f%mana%&7/&f%max_mana%", manaPlaceholders(data, 0));
    }

    public void sendNoPermission(CommandSender sender) { send(sender, "no-permission", "%prefix% &cKamu tidak punya izin.", Map.of()); }
    public void sendPlayerOnly(CommandSender sender) { send(sender, "player-only", "%prefix% &cCommand ini hanya bisa digunakan oleh player.", Map.of()); }
    public void sendReloadSuccess(CommandSender sender) { send(sender, "reload-success", "%prefix% &aVelioraSkills berhasil direload.", Map.of()); }
    public void sendInvalidNumber(CommandSender sender) { send(sender, "invalid-number", "%prefix% &cAngka tidak valid.", Map.of()); }
    public void sendTargetNotFound(CommandSender sender, String target) { send(sender, "target-not-found", "%prefix% &cPlayer &f%player% &ctidak ditemukan.", Map.of("%player%", target)); }

    public void sendManaSet(CommandSender sender, PlayerManaData data) { send(sender, "mana-set", "%prefix% &aMana &f%player% &aberhasil di-set ke &f%mana%&7/&f%max_mana%&a.", manaPlaceholders(data, 0)); }
    public void sendManaAdd(CommandSender sender, PlayerManaData data, int amount) { send(sender, "mana-add", "%prefix% &aBerhasil menambah &f%amount% &amana ke &f%player%&a.", manaPlaceholders(data, amount)); }
    public void sendManaRemove(CommandSender sender, PlayerManaData data, int amount) { send(sender, "mana-remove", "%prefix% &aBerhasil mengurangi &f%amount% &amana dari &f%player%&a.", manaPlaceholders(data, amount)); }
    public void sendManaReset(CommandSender sender, PlayerManaData data) { send(sender, "mana-reset", "%prefix% &aMana &f%player% &aberhasil direset ke &f%mana%&7/&f%max_mana%&a.", manaPlaceholders(data, 0)); }

    public PlayerManaData findManaTarget(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return manaManager.getData(online);
        return manaManager.findByName(name);
    }

    private Map<String, String> statusPlaceholders() {
        Map<String, String> map = new HashMap<>();
        map.put("%enabled%", String.valueOf(configManager.isEnabled()));
        map.put("%actionbar%", String.valueOf(configManager.isActionBarEnabled()));
        map.put("%placeholderapi%", String.valueOf(configManager.isPlaceholderApiEnabled()));
        map.put("%default_mana%", String.valueOf(configManager.getDefaultMana()));
        map.put("%default_max_mana%", String.valueOf(configManager.getDefaultMaxMana()));
        map.put("%daily_reset%", String.valueOf(configManager.isDailyResetEnabled()));
        map.put("%reset_time%", configManager.getResetTime());
        return map;
    }

    private Map<String, String> manaPlaceholders(PlayerManaData data, int amount) {
        Map<String, String> map = new HashMap<>();
        map.put("%player%", data.getName() == null ? data.getUuid().toString() : data.getName());
        map.put("%mana%", String.valueOf(data.getMana()));
        map.put("%max_mana%", String.valueOf(data.getMaxMana()));
        map.put("%amount%", String.valueOf(amount));
        return map;
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(apply(configManager.message(path, fallback), placeholders)));
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
