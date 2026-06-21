package id.velioragardens.veliorasuite.module.quest;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.AbstractModule;
import id.velioragardens.veliorasuite.module.Module;
import id.velioragardens.veliorasuite.module.skills.SkillsModule;
import id.velioragardens.veliorasuite.util.ColorUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class QuestModule extends AbstractModule implements Listener, CommandExecutor, TabCompleter {
    private File dataFile;
    private FileConfiguration data;

    public QuestModule(VelioraSuite plugin) {
        super(plugin, "quest", "quest");
    }

    @Override
    protected void onEnable() {
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("vquest");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        plugin.getLogger().info("VelioraQuest module started.");
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        save();
        plugin.getLogger().info("VelioraQuest module stopped.");
    }

    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "quests.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try {
            if (data != null) data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal save quests.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only player.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            openGui(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            sendList(player);
            return true;
        }
        if (sub.equals("start") && args.length >= 2) {
            start(player, args[1]);
            return true;
        }
        if (sub.equals("status")) {
            status(player);
            return true;
        }
        if (sub.equals("abandon")) {
            abandon(player);
            return true;
        }
        if (sub.equals("reload") && player.hasPermission("veliorasuite.quest.admin")) {
            configFile.reload();
            player.sendMessage(color(msg("reload", Map.of())));
            return true;
        }
        player.sendMessage(color("&8【&aVelioraQuest&8】 &f/vquest, /vquest list, /vquest start <quest>, /vquest status, /vquest abandon"));
        return true;
    }

    private void openGui(Player player) {
        int size = Math.max(9, Math.min(54, configFile.get().getInt("gui.size", 54)));
        Inventory inventory = Bukkit.createInventory(new QuestGuiHolder(), size, color(configFile.get().getString("gui.title", "&8Veliora Quest")));
        ConfigurationSection quests = configFile.get().getConfigurationSection("quests");
        if (quests != null) {
            for (String id : quests.getKeys(false)) {
                String path = "quests." + id;
                int slot = configFile.get().getInt(path + ".slot", firstEmpty(inventory));
                if (slot < 0 || slot >= inventory.getSize()) continue;
                Material material = Material.matchMaterial(configFile.get().getString(path + ".icon", "BOOK"));
                if (material == null) material = Material.BOOK;
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(color(configFile.get().getString(path + ".display-name", id)));
                    List<String> lore = new ArrayList<>();
                    for (String line : configFile.get().getStringList(path + ".description")) lore.add(color(line));
                    lore.add(color(""));
                    lore.add(color("&7Target: &f" + targetText(id)));
                    lore.add(color("&7Jumlah: &a" + configFile.get().getInt(path + ".target-amount", 1)));
                    lore.add(color("&8ID: " + id));
                    lore.add(color("&eKlik untuk mulai quest."));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inventory.setItem(slot, item);
            }
        }
        player.openInventory(inventory);
    }

    private int firstEmpty(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) if (inventory.getItem(i) == null) return i;
        return -1;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuestGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuestGuiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().getLore() == null) return;
        for (String line : clicked.getItemMeta().getLore()) {
            String stripped = stripColor(line);
            if (stripped.startsWith("ID: ")) {
                start(player, stripped.substring(4).trim());
                player.closeInventory();
                return;
            }
        }
    }

    private void sendList(Player player) {
        player.sendMessage(color("&8&m------------------------"));
        player.sendMessage(color("&aVelioraQuest List"));
        ConfigurationSection quests = configFile.get().getConfigurationSection("quests");
        if (quests == null) {
            player.sendMessage(color("&7Belum ada quest di config."));
            return;
        }
        for (String id : quests.getKeys(false)) {
            player.sendMessage(color("&7- &f" + display(id) + " &8| &a/vquest start " + id));
            player.sendMessage(color("  &8Target: &7" + targetText(id)));
        }
        player.sendMessage(color("&8&m------------------------"));
    }

    private void start(Player player, String id) {
        String path = "quests." + id;
        if (!configFile.get().isConfigurationSection(path)) {
            player.sendMessage(color(msg("not-found", Map.of("quest", id))));
            return;
        }
        String base = base(player);
        if (data.getString(base + ".active") != null) {
            player.sendMessage(color(msg("already-active", Map.of())));
            return;
        }
        int manaCost = configFile.get().getInt(path + ".mana-cost", 0);
        SkillsModule skills = getSkills();
        if (skills != null && !skills.takeMana(player, manaCost)) {
            player.sendMessage(color(msg("not-enough-mana", Map.of("mana", String.valueOf(manaCost)))));
            return;
        }
        data.set(base + ".active", id);
        data.set(base + ".progress", 0);
        data.set(base + ".started-at", System.currentTimeMillis());
        save();
        player.sendMessage(color(msg("started", Map.of("quest", display(id)))));
        showInstruction(player, id);
    }

    private void showInstruction(Player player, String id) {
        player.sendMessage(color("&8&m------------------------"));
        player.sendMessage(color("&a" + display(id)));
        for (String line : configFile.get().getStringList("quests." + id + ".description")) player.sendMessage(color("&7- &f" + line));
        player.sendMessage(color("&7Target: &a" + targetText(id)));
        player.sendMessage(color("&8&m------------------------"));
    }

    private void status(Player player) {
        String id = data.getString(base(player) + ".active");
        if (id == null) {
            player.sendMessage(color(msg("no-active", Map.of())));
            return;
        }
        int progress = data.getInt(base(player) + ".progress", 0);
        int target = configFile.get().getInt("quests." + id + ".target-amount", 1);
        player.sendMessage(color(msg("status", Map.of("quest", display(id), "progress", String.valueOf(progress), "target", String.valueOf(target)))));
        showInstruction(player, id);
    }

    private void abandon(Player player) {
        if (data.getString(base(player) + ".active") == null) {
            player.sendMessage(color(msg("no-active", Map.of())));
            return;
        }
        data.set(base(player), null);
        save();
        player.sendMessage(color(msg("abandoned", Map.of())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        progress(event.getPlayer(), "break", event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) progress(killer, "kill", event.getEntityType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) progress(event.getPlayer(), "fish", "ANY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        progress(event.getPlayer(), "cook", event.getItemType().name(), event.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getRecipe() != null) {
            progress(player, "craft", event.getRecipe().getResult().getType().name(), Math.max(1, event.getRecipe().getResult().getAmount()));
        }
    }

    private void progress(Player player, String type, String target, int amount) {
        String id = data.getString(base(player) + ".active");
        if (id == null) return;
        FileConfiguration cfg = configFile.get();
        String path = "quests." + id;
        if (!type.equalsIgnoreCase(cfg.getString(path + ".type", ""))) return;
        List<String> targets = cfg.getStringList(path + ".targets");
        if (!targets.isEmpty() && !targets.contains(target) && !targets.contains("ANY")) return;
        int progress = data.getInt(base(player) + ".progress", 0) + Math.max(1, amount);
        int need = cfg.getInt(path + ".target-amount", 1);
        if (progress > need) progress = need;
        data.set(base(player) + ".progress", progress);
        save();
        sendQuestActionBar(player, id, progress, need);
        if (progress >= need) complete(player, id);
        else player.sendMessage(color(msg("progress", Map.of("quest", display(id), "progress", String.valueOf(progress), "target", String.valueOf(need)))));
    }

    private void sendQuestActionBar(Player player, String id, int progress, int target) {
        String text = configFile.get().getString("actionbar.progress", "&aQuest &f%quest% &7%progress%/%target%");
        text = text.replace("%quest%", display(id)).replace("%progress%", String.valueOf(progress)).replace("%target%", String.valueOf(target));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(text)));
    }

    private void complete(Player player, String id) {
        String path = "quests." + id + ".rewards";
        Economy eco = plugin.getHookManager().getEconomy();
        double money = configFile.get().getDouble(path + ".money", 0);
        if (eco != null && money > 0) eco.depositPlayer(player, money);
        int exp = configFile.get().getInt(path + ".vanilla-exp", configFile.get().getInt(path + ".exp", 0));
        if (exp > 0) player.giveExp(exp);
        SkillsModule skills = getSkills();
        if (skills != null) {
            String skill = configFile.get().getString(path + ".skill", configFile.get().getString("quests." + id + ".skill", ""));
            int skillExp = configFile.get().getInt(path + ".skill-exp", 0);
            if (skill != null && !skill.isBlank() && skillExp > 0) skills.addExp(player, skill, skillExp);
        }
        data.set(base(player), null);
        save();
        player.sendMessage(color(msg("completed", Map.of("quest", display(id), "money", String.valueOf((int) money), "exp", String.valueOf(exp)))));
    }

    private SkillsModule getSkills() {
        Module module = plugin.getModuleManager().getModule("skills");
        return module instanceof SkillsModule skillsModule ? skillsModule : null;
    }

    private String targetText(String id) {
        List<String> targets = configFile.get().getStringList("quests." + id + ".targets");
        if (targets.isEmpty()) return "ANY";
        return String.join(", ", targets);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("gui", "list", "start", "status", "abandon", "reload").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            ConfigurationSection quests = configFile.get().getConfigurationSection("quests");
            return quests == null ? List.of() : new ArrayList<>(quests.getKeys(false));
        }
        return List.of();
    }

    private String base(Player player) { return "players." + player.getUniqueId(); }
    private String display(String id) { return configFile.get().getString("quests." + id + ".display-name", id); }
    private String msg(String key, Map<String,String> vars) { String s = configFile.get().getString("messages." + key, "&8【&aVelioraQuest&8】 &cMessage not found: " + key); for (var e : vars.entrySet()) s = s.replace("%" + e.getKey() + "%", e.getValue()); return s; }
    private String color(String s) { return ColorUtil.color(s); }
    private String stripColor(String s) { return s.replaceAll("§[0-9A-FK-ORa-fk-or]", ""); }
}
