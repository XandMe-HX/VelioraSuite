package id.velioragardens.veliorasuite.module.redeem;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.core.storage.BufferedYamlWriter;
import id.velioragardens.veliorasuite.core.storage.VelioraDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public final class CodeRedeemManager {
    public enum Type { MONEY, TEMPLATE, ITEM, COMMAND }
    public record Draft(Type type, double money, String template, ItemStack item) {}
    public record DeletedCodeAudit(String code, String actor, long deletedAt, int claimCount, String reward) {}
    private final VelioraSuite plugin;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Map<UUID, Long> attempts = new HashMap<>();
    private final Map<String, Template> templates = new LinkedHashMap<>();
    private final File file;
    private YamlConfiguration data;
    private BufferedYamlWriter yamlWriter;
    private boolean databaseBacked;
    private long lastAttemptPrune;
    private Object economy;
    private Class<?> economyClass;

    public CodeRedeemManager(VelioraSuite plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "data/redeem.yml"); seedTemplates(); }
    public void load() {
        if (yamlWriter != null) yamlWriter.shutdown();
        yamlWriter = null;
        VelioraDatabase database = plugin.getDatabase();
        databaseBacked = database != null && database.isAvailable();
        data = new YamlConfiguration();
        String snapshot = databaseBacked ? database.loadModuleStateNow("redeem") : null;
        if (snapshot != null && !snapshot.isBlank()) {
            try { data.loadFromString(snapshot); }
            catch (org.bukkit.configuration.InvalidConfigurationException ex) {
                plugin.getLogger().warning("CodeRedeem: snapshot database rusak, memakai data cadangan YAML.");
                data = YamlConfiguration.loadConfiguration(file);
            }
        }
        else {
            data = YamlConfiguration.loadConfiguration(file);
            if (databaseBacked) database.saveModuleStateNow("redeem", data.saveToString());
        }
        if (!databaseBacked) {
            yamlWriter = new BufferedYamlWriter(plugin, file, data, "data/redeem.yml");
            yamlWriter.start();
        }
        hookEconomy();
    }
    public void shutdown() { saveNow(); drafts.clear(); attempts.clear(); }
    public Set<String> codes() { ConfigurationSection section = data.getConfigurationSection("codes"); return section == null ? Set.of() : new TreeSet<>(section.getKeys(false)); }
    public Map<String, Template> templates() { return Collections.unmodifiableMap(templates); }
    public Draft draft(Player player) { return drafts.get(player.getUniqueId()); }
    public void setDraft(Player player, Draft draft) { drafts.put(player.getUniqueId(), draft); }
    public void clearDraft(Player player) { drafts.remove(player.getUniqueId()); }
    public String createFromDraft(Player player, String code) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null) return "§cPilih hadiah dahulu di /cdmanager.";
        String result = switch (draft.type()) { case MONEY -> createMoney(code, draft.money(), player.getName()); case TEMPLATE -> createTemplate(code, draft.template(), player.getName()); case ITEM -> createItem(code, draft.item(), player.getName()); case COMMAND -> "§cHadiah command hanya bisa dibuat melalui /cd command set."; };
        if (result.startsWith("§a")) clearDraft(player);
        return result;
    }
    public String createMoney(String raw, double money, String creator) {
        String code = normalize(raw); if (code == null || money <= 0) return "§cKode harus 3-32 karakter (huruf/angka/_/-), nominal harus positif.";
        putBase(code, Type.MONEY, creator); data.set("codes." + code + ".money", money); save(); return "§aKode §f" + code + " §aberhasil dibuat: §6$" + trim(money) + ".";
    }
    public String createTemplate(String raw, String template, String creator) {
        String code = normalize(raw); if (code == null || !templates.containsKey(template)) return "§cKode atau template tidak valid.";
        putBase(code, Type.TEMPLATE, creator); data.set("codes." + code + ".template", template); save(); return "§aKode §f" + code + " §aberhasil dibuat dengan template §e" + templates.get(template).name + "§a.";
    }
    public String createItem(String raw, ItemStack item, String creator) {
        String code = normalize(raw); if (code == null || item == null || item.getType().isAir()) return "§cPegang item yang ingin dijadikan hadiah dan gunakan kode yang valid.";
        putBase(code, Type.ITEM, creator); data.set("codes." + code + ".item", item.clone()); save(); return "§aKode §f" + code + " §aberhasil dibuat dengan hadiah §f" + item.getType().name() + "§a.";
    }
    /** Reward adapter for optional plugins; only %player% and %uuid% are expanded. */
    public String createCommand(String raw, String command, String creator) {
        String code = normalize(raw); String safe = sanitizeCommand(command);
        if (code == null || safe == null) return "§cKode atau command tidak valid. Jangan pakai /, baris baru, atau ;.";
        putBase(code, Type.COMMAND, creator); data.set("codes." + code + ".command", safe); save();
        return "§aKode §f" + code + " §aberhasil dibuat untuk hadiah plugin eksternal.";
    }
    public String delete(String raw, String actor) {
        String code = normalize(raw);
        if (code == null || !data.contains("codes." + code)) return "§cKode tidak ditemukan.";
        auditDeletion(code, actor);
        data.set("codes." + code, null);
        data.set("claims." + code, null);
        save();
        plugin.getLogger().warning("CodeRedeem audit: " + actor + " menghapus kode " + code + ".");
        return "§aKode §f" + code + " §aberhasil dihapus dan masuk riwayat audit.";
    }
    public List<DeletedCodeAudit> deletionHistory(String rawCode) {
        String filter = rawCode == null || rawCode.isBlank() ? null : normalize(rawCode);
        ConfigurationSection section = data.getConfigurationSection("history.deleted");
        if (section == null) return List.of();
        List<DeletedCodeAudit> rows = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String path = "history.deleted." + id;
            String code = data.getString(path + ".code", "?");
            if (filter != null && !filter.equals(code)) continue;
            rows.add(new DeletedCodeAudit(code, data.getString(path + ".actor", "Console"), data.getLong(path + ".deleted-at"), data.getInt(path + ".claim-count"), data.getString(path + ".reward", "Tidak diketahui")));
        }
        rows.sort(Comparator.comparingLong(DeletedCodeAudit::deletedAt).reversed());
        return rows;
    }
    public String redeem(Player player, String raw) {
        long now = System.currentTimeMillis();
        pruneAttemptCache(now);
        long last = attempts.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 1200) return "§cTunggu sebentar sebelum mencoba kode lagi.";
        attempts.put(player.getUniqueId(), now);
        String code = normalize(raw); if (code == null || !data.getBoolean("codes." + code + ".enabled", false)) return "§cKode tidak ditemukan atau sudah tidak aktif.";
        List<String> claimed = new ArrayList<>(data.getStringList("claims." + code));
        if (claimed.contains(player.getUniqueId().toString())) return "§eKamu sudah pernah mengambil kode §f" + code + "§e.";
        Type type; try { type = Type.valueOf(data.getString("codes." + code + ".type", "").toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return "§cData hadiah kode rusak. Hubungi admin."; }
        String failure = grant(player, type, code); if (failure != null) return failure;
        claimed.add(player.getUniqueId().toString()); data.set("claims." + code, claimed); save();
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.25f);
        return "§aBerhasil menukarkan kode §f" + code + "§a! Hadiah sudah masuk.";
    }
    private String grant(Player player, Type type, String code) {
        if (type == Type.MONEY) { double money = data.getDouble("codes." + code + ".money"); return deposit(player, money) ? null : "§cVault Economy tidak tersedia; kode belum diklaim."; }
        if (type == Type.COMMAND) {
            String command = sanitizeCommand(data.getString("codes." + code + ".command", ""));
            if (command == null) return "§cCommand hadiah kode tidak valid.";
            String resolved = command.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString());
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved) ? null : "§cPlugin hadiah menolak command; kode belum diklaim.";
        }
        List<ItemStack> items; double money = 0;
        if (type == Type.ITEM) { ItemStack item = data.getItemStack("codes." + code + ".item"); if (item == null) return "§cHadiah item kode rusak."; items = List.of(item); }
        else { Template template = templates.get(data.getString("codes." + code + ".template", "")); if (template == null) return "§cTemplate hadiah tidak tersedia."; items = template.items; money = template.money; }
        if (emptySlots(player) < items.size()) return "§cTasmu tidak cukup kosong. Kosongkan minimal " + items.size() + " slot; kode belum diklaim.";
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        if (!leftovers.isEmpty()) return "§cTasmu penuh; kode belum diklaim.";
        if (money > 0 && !deposit(player, money)) { player.sendMessage("§eItem berhasil diterima, tetapi bonus uang dilewati karena Vault tidak aktif."); }
        return null;
    }
    private int emptySlots(Player p) { int n=0; for (ItemStack item : p.getInventory().getStorageContents()) if (item == null || item.getType().isAir()) n++; return n; }
    private void putBase(String code, Type type, String creator) { data.set("codes." + code + ".enabled", true); data.set("codes." + code + ".type", type.name()); data.set("codes." + code + ".created-by", creator); data.set("codes." + code + ".created-at", System.currentTimeMillis()); data.set("claims." + code, new ArrayList<String>()); }
    private void auditDeletion(String code, String actor) {
        long now = System.currentTimeMillis();
        String id = now + "-" + UUID.randomUUID().toString().substring(0, 8);
        String path = "history.deleted." + id;
        data.set(path + ".code", code);
        data.set(path + ".actor", actor == null || actor.isBlank() ? "Console" : actor);
        data.set(path + ".deleted-at", now);
        data.set(path + ".claim-count", data.getStringList("claims." + code).size());
        data.set(path + ".reward", rewardSummary(code));
        pruneDeletionHistory();
    }
    private String rewardSummary(String code) {
        Type type;
        try { type = Type.valueOf(data.getString("codes." + code + ".type", "").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return "Data tipe rusak"; }
        return switch (type) {
            case MONEY -> "Uang $" + trim(data.getDouble("codes." + code + ".money"));
            case TEMPLATE -> "Template " + data.getString("codes." + code + ".template", "?");
            case ITEM -> { ItemStack item = data.getItemStack("codes." + code + ".item"); yield "Item " + (item == null ? "rusak" : item.getType().name() + " x" + item.getAmount()); }
            case COMMAND -> "Command hadiah";
        };
    }
    private void pruneDeletionHistory() {
        ConfigurationSection section = data.getConfigurationSection("history.deleted");
        if (section == null || section.getKeys(false).size() <= 200) return;
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.comparingLong(id -> data.getLong("history.deleted." + id + ".deleted-at")));
        while (ids.size() > 200) data.set("history.deleted." + ids.removeFirst(), null);
    }
    private String normalize(String input) { if (input == null) return null; String c=input.trim().toUpperCase(Locale.ROOT); return c.matches("[A-Z0-9_-]{3,32}") ? c : null; }
    private String sanitizeCommand(String command) { if(command==null)return null; String safe=command.trim(); if(safe.startsWith("/"))safe=safe.substring(1); return safe.isBlank()||safe.contains("\n")||safe.contains("\r")||safe.contains(";") ? null : safe; }
    /** Persistence is async during gameplay; a redemption can never block ticks on a YAML write. */
    private void save() {
        if (databaseBacked) plugin.getDatabase().saveModuleStateAsync("redeem", data.saveToString());
        else if (yamlWriter != null) { yamlWriter.markDirty(); yamlWriter.flushAsync(); }
    }
    private void saveNow() {
        if (data == null) return;
        if (databaseBacked) plugin.getDatabase().saveModuleStateNow("redeem", data.saveToString());
        else if (yamlWriter != null) yamlWriter.shutdown();
    }

    /** Keeps the anti-spam map bounded without clearing a player's active cooldown on reconnect. */
    private void pruneAttemptCache(long now) {
        if (now - lastAttemptPrune < 60_000L) return;
        lastAttemptPrune = now;
        attempts.entrySet().removeIf(entry -> now - entry.getValue() > 300_000L);
    }
    private void hookEconomy() { economy=null; economyClass=null; try { if (Bukkit.getPluginManager().getPlugin("Vault") == null) return; economyClass=Class.forName("net.milkbowl.vault.economy.Economy"); @SuppressWarnings({"rawtypes","unchecked"}) RegisteredServiceProvider<?> provider=Bukkit.getServicesManager().getRegistration((Class)economyClass); if(provider != null) economy=provider.getProvider(); } catch (ReflectiveOperationException ignored) {} }
    private boolean deposit(Player p, double value) { if (value<=0) return true; if(economy==null) return false; try { Method method=economyClass.getMethod("depositPlayer", OfflinePlayer.class,double.class); Object response=method.invoke(economy,p,value); Method ok=response.getClass().getMethod("transactionSuccess"); return Boolean.TRUE.equals(ok.invoke(response)); } catch (ReflectiveOperationException ex) { return false; } }
    private void seedTemplates() {
        add("BANSOS", "Bansos Builder", 0, stack(Material.STONE_PICKAXE), stack(Material.STONE_AXE), stack(Material.STONE_SWORD), stack(Material.COAL,64), stack(Material.GOLD_INGOT,64), stack(Material.IRON_INGOT,64));
        add("UANG_KEY", "Uang & Key Gacha", 5000, named(Material.TRIPWIRE_HOOK,"§dKey Gacha §7(x2)",2));
        add("MAKAN_KEY", "Food & Key Gacha", 0, stack(Material.COOKED_BEEF,64), stack(Material.BREAD,64), named(Material.TRIPWIRE_HOOK,"§dKey Gacha",1));
        add("PENAMBANG", "Paket Penambang", 0, stack(Material.IRON_PICKAXE), stack(Material.TORCH,64), stack(Material.COAL,64));
        add("PETANI", "Paket Petani", 0, stack(Material.IRON_HOE), stack(Material.WHEAT_SEEDS,64), stack(Material.BONE_MEAL,64));
        add("PETUALANG", "Paket Petualang", 0, stack(Material.IRON_SWORD), stack(Material.SHIELD), stack(Material.COOKED_BEEF,32), stack(Material.TORCH,64));
        add("PEMANCING", "Paket Pemancing", 0, stack(Material.FISHING_ROD), stack(Material.COD,32), stack(Material.SALMON,32));
        add("BUILDER", "Paket Builder", 0, stack(Material.STONE,64), stack(Material.OAK_LOG,64), stack(Material.GLASS,64), stack(Material.LANTERN,16));
        add("NETHER", "Paket Nether Aman", 0, stack(Material.OBSIDIAN,16), stack(Material.GOLDEN_SWORD), stack(Material.COOKED_PORKCHOP,32));
        add("HADIAH_SERVER", "Hadiah Server", 2500, stack(Material.DIAMOND,3), stack(Material.EXPERIENCE_BOTTLE,16), stack(Material.GOLDEN_APPLE,2));
    }
    private void add(String id,String name,double money,ItemStack...items){ templates.put(id,new Template(id,name,money,List.of(items))); }
    private ItemStack stack(Material material){ return new ItemStack(material); } private ItemStack stack(Material material,int amount){return new ItemStack(material,amount);} private ItemStack named(Material material,String name,int amount){ItemStack i=new ItemStack(material,amount);ItemMeta m=i.getItemMeta();m.setDisplayName(name);i.setItemMeta(m);return i;}
    private static String trim(double value) { return value == Math.rint(value) ? Long.toString((long)value) : String.format(Locale.US,"%.2f",value); }
    public static final class Template { public final String id,name; public final double money; public final List<ItemStack> items; private Template(String id,String name,double money,List<ItemStack>items){this.id=id;this.name=name;this.money=money;this.items=items;} }
}
