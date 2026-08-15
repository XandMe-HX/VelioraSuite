package id.velioragardens.veliorasuite.module.kits;

import id.velioragardens.veliorasuite.VelioraSuite;
import id.velioragardens.veliorasuite.module.kits.model.Kit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitsManager {

    private final VelioraSuite plugin;
    private final KitsConfigManager configManager;
    private final KitsDataManager dataManager;
    private final KitCooldownManager cooldownManager;
    private final KitPurchaseManager purchaseManager;
    private final KitRewardManager rewardManager;
    private final KitGuiManager guiManager;
    private final KitPreviewManager previewManager;

    public KitsManager(VelioraSuite plugin) {
        this.plugin = plugin;
        this.configManager = new KitsConfigManager(plugin);
        this.dataManager = new KitsDataManager(plugin);
        this.cooldownManager = new KitCooldownManager(dataManager);
        this.purchaseManager = new KitPurchaseManager(plugin);
        this.rewardManager = new KitRewardManager(plugin, purchaseManager);
        this.guiManager = new KitGuiManager(this, configManager);
        this.previewManager = new KitPreviewManager(configManager);
    }

    public void shutdown() { dataManager.shutdown(); }

    public void load() {
        configManager.load();
        dataManager.load();
        purchaseManager.load();
        plugin.getLogger().info("VelioraKits loaded with " + configManager.getEnabledKits().size() + " enabled kit(s).");
    }

    public void reload() { load(); }
    public boolean isEnabled() { return configManager.isEnabled(); }
    public KitsConfigManager getConfigManager() { return configManager; }

    public void openGui(Player player) {
        if (!isEnabled()) {
            send(player, "module-disabled", "%prefix% &cVelioraKits sedang dimatikan.", Map.of());
            return;
        }
        guiManager.open(player);
    }

    public void sendHelp(CommandSender sender) {
        List<String> help = configManager.getMessageList("help", List.of(
                "&8&m--------------------------------",
                "&b&lVelioraKits",
                "&f/kits &7- Membuka GUI kit.",
                "&f/kits list &7- Melihat kit aktif.",
                "&f/kits claim <kit> &7- Claim kit.",
                "&f/kits preview <kit> &7- Preview kit.",
                "&f/kits buy <kit> &7- Beli kit.",
                "&f/kits cooldown &7- Cek cooldown.",
                "&f/kits reload &7- Reload config.",
                "&8&m--------------------------------"
        ));
        for (String line : help) sender.sendMessage(configManager.color(applyPlaceholders(line, Map.of())));
    }

    public void sendList(CommandSender sender) {
        sender.sendMessage(configManager.color(configManager.getMessage("list-header", "&8&m--------------------------------")));
        sender.sendMessage(configManager.color(configManager.getMessage("list-title", "&b&lVelioraKits List")));
        List<Kit> enabledKits = configManager.getEnabledKits();
        if (enabledKits.isEmpty()) {
            sender.sendMessage(configManager.color(configManager.getMessage("list-empty", "%prefix% &cTidak ada kit aktif.")));
        } else {
            String format = configManager.getMessage("list-format", "&7- &f%kit_display% &8(&7%kit_id%&8) &7Cooldown: &f%cooldown% &8| &7Harga: &a$%price%");
            for (Kit kit : enabledKits) sender.sendMessage(configManager.color(applyPlaceholders(format, getKitPlaceholders(kit))));
        }
        sender.sendMessage(configManager.color(configManager.getMessage("list-footer", "&8&m--------------------------------")));
    }

    public void sendCooldowns(Player player) {
        boolean any = false;
        player.sendMessage(configManager.color("&8&m--------------------------------"));
        player.sendMessage(configManager.color("&b&lCooldown Kit"));
        for (Kit kit : configManager.getEnabledKits()) {
            long remaining = cooldownManager.getRemainingMillis(player.getUniqueId(), kit);
            if (remaining > 0) {
                any = true;
                player.sendMessage(configManager.color("&7- &f" + getKitDisplayName(kit) + " &8(&7" + kit.getId() + "&8) &8: &c" + cooldownManager.formatTime(remaining)));
            }
        }
        if (!any) send(player, "no-cooldown", "%prefix% &aTidak ada kit yang sedang cooldown.", Map.of());
        player.sendMessage(configManager.color("&8&m--------------------------------"));
    }

    public void claimKit(Player player, String kitId) { claimKit(player, kitId, false); }

    public void claimKit(Player player, String kitId, boolean firstJoin) {
        Kit kit = getUsableKit(player, kitId);
        if (kit == null) return;

        if (!firstJoin && cooldownManager.isOnCooldown(player.getUniqueId(), kit) && !player.hasPermission(configManager.getBypassCooldownPermission()) && !player.hasPermission(configManager.getAdminPermission())) {
            String time = cooldownManager.formatTime(cooldownManager.getRemainingMillis(player.getUniqueId(), kit));
            send(player, "kit-on-cooldown", "%prefix% &cKit &f%kit% &cmasih cooldown: &f%time%&c.", Map.of("%kit%", kit.getId(), "%time%", time));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
            return;
        }

        if (configManager.isBlockClaimWhenInventoryFull()) {
            int missingSlots = rewardManager.getMissingSlots(player, kit);
            if (missingSlots > 0) {
                send(player, "inventory-not-enough-space", "%prefix% &cInventory kamu penuh. Kosongkan minimal &f%slots% slot &cuntuk claim kit ini.", Map.of("%slots%", String.valueOf(missingSlots)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
                return;
            }
        }

        boolean freeClaim = isFreeClaimAvailable(player, kit);
        boolean armorEquipped = rewardManager.hasArmorToEquip(kit);
        boolean armorReplaced = rewardManager.willReplaceArmor(player, kit);
        if (!handlePriceBeforeClaim(player, kit, freeClaim)) return;

        rewardManager.giveKit(player, kit, configManager.isDropExtraItems());
        dataManager.setLastClaim(player.getUniqueId(), kit.getId(), System.currentTimeMillis());
        if (freeClaim) dataManager.setClaimedFree(player.getUniqueId(), kit.getId(), true);
        if (armorReplaced) send(player, "kit-armor-replaced", "%prefix% &eArmor lama kamu dipindahkan ke inventory.", Map.of());
        if (freeClaim && kit.getPremiumLevel() > 0) {
            send(player, "premium-first-claim-free", "%prefix% &aPremium kit &f%kit% &aberhasil diambil gratis 1x. Claim berikutnya bayar &f$%price%&a.", Map.of("%kit%", kit.getId(), "%price%", formatPrice(effectivePrice(kit))));
        } else if (freeClaim) {
            send(player, "kit-first-claim-free", "%prefix% &aKit &f%kit% &aberhasil diambil gratis 1x.", Map.of("%kit%", kit.getId()));
        }
        if (armorEquipped) {
            send(player, "kit-claimed-equipped", "%prefix% &aKit &f%kit% &aberhasil diclaim. Armor otomatis dipakai.", Map.of("%kit%", kit.getId()));
        } else {
            send(player, "kit-claimed", "%prefix% &aKamu berhasil claim kit &f%kit%&a.", Map.of("%kit%", kit.getId()));
        }
        if (!firstJoin) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.25F);
        }
    }

    public void previewKit(Player player, String kitId) {
        Kit kit = configManager.getKit(kitId);
        if (kit == null) {
            send(player, "kit-not-found", "%prefix% &cKit &f%kit% &ctidak ditemukan.", Map.of("%kit%", kitId));
            return;
        }
        previewManager.openPreview(player, kit);
        send(player, "kit-preview", "%prefix% &aPreview kit &f%kit%&a.", Map.of("%kit%", kit.getId()));
    }

    public void buyKit(Player player, String kitId) {
        Kit kit = getUsableKit(player, kitId);
        if (kit == null) return;
        if (!effectiveBuyEnabled(kit)) {
            send(player, "buy-not-required", "%prefix% &cKit ini tidak perlu dibeli.", Map.of("%kit%", kit.getId()));
            return;
        }
        if (!kit.isOneTimePurchase()) {
            send(player, "buy-each-claim", "%prefix% &cKit ini dibayar setiap claim. Gunakan &f/kits claim %kit%&c.", Map.of("%kit%", kit.getId(), "%price%", formatPrice(effectivePrice(kit))));
            return;
        }
        if (dataManager.hasPurchased(player.getUniqueId(), kit.getId())) {
            send(player, "already-bought", "%prefix% &cKamu sudah membeli kit ini.", Map.of("%kit%", kit.getId()));
            return;
        }
        if (!chargePlayer(player, kit)) return;
        dataManager.setPurchased(player.getUniqueId(), kit.getId());
        send(player, "kit-bought", "%prefix% &aKamu berhasil membeli kit &f%kit% &adengan harga &f$%price%&a.", Map.of("%kit%", kit.getId(), "%price%", formatPrice(effectivePrice(kit))));
    }

    public void giveFirstJoinKit(Player player) {
        if (!configManager.isFirstJoinKitEnabled()) return;
        if (dataManager.isFirstJoinGiven(player.getUniqueId())) return;
        Kit kit = configManager.getKit(configManager.getFirstJoinKit());
        if (kit == null || !kit.isEnabled()) {
            dataManager.setFirstJoinGiven(player.getUniqueId(), true);
            return;
        }
        if (configManager.isBlockClaimWhenInventoryFull() && rewardManager.getMissingSlots(player, kit) > 0) return;
        rewardManager.giveKit(player, kit, configManager.isDropExtraItems());
        dataManager.setLastClaim(player.getUniqueId(), kit.getId(), System.currentTimeMillis());
        if (isFirstClaimFreeEffective(kit)) dataManager.setClaimedFree(player.getUniqueId(), kit.getId(), true);
        dataManager.setFirstJoinGiven(player.getUniqueId(), true);
        send(player, "first-join-given", "%prefix% &aKamu menerima first join kit &f%kit%&a.", Map.of("%kit%", kit.getId()));
    }

    public String getStatusKey(Player player, Kit kit) {
        if (!hasKitPermission(player, kit)) return "locked-permission";
        if (!hasPremiumPermission(player, kit)) return "locked-premium";
        if (isFreeClaimAvailable(player, kit)) return "available";
        if (cooldownManager.isOnCooldown(player.getUniqueId(), kit) && !player.hasPermission(configManager.getBypassCooldownPermission()) && !player.hasPermission(configManager.getAdminPermission())) return "cooldown";
        if (effectiveBuyEnabled(kit) && kit.isOneTimePurchase() && !dataManager.hasPurchased(player.getUniqueId(), kit.getId()) && !player.hasPermission(configManager.getBypassPricePermission()) && !player.hasPermission(configManager.getAdminPermission())) return "need-buy";
        if (effectiveBuyEnabled(kit) && !kit.isOneTimePurchase() && !player.hasPermission(configManager.getBypassPricePermission()) && !player.hasPermission(configManager.getAdminPermission())) return "need-buy";
        if (effectiveBuyEnabled(kit) && kit.isOneTimePurchase() && dataManager.hasPurchased(player.getUniqueId(), kit.getId())) return "bought";
        return "available";
    }

    public String applyKitPlaceholders(String text, Player player, Kit kit) {
        String time = cooldownManager.formatTime(cooldownManager.getRemainingMillis(player.getUniqueId(), kit));
        return applyPlaceholders(text, getKitPlaceholders(kit, time));
    }

    public List<String> getKitIds() { return configManager.getKitIds(); }

    private Kit getUsableKit(Player player, String kitId) {
        Kit kit = configManager.getKit(kitId);
        String safeKit = kitId == null ? "" : kitId.toLowerCase(Locale.ROOT);
        if (kit == null) {
            send(player, "kit-not-found", "%prefix% &cKit &f%kit% &ctidak ditemukan.", Map.of("%kit%", safeKit));
            return null;
        }
        if (!kit.isEnabled()) {
            send(player, "kit-disabled", "%prefix% &cKit &f%kit% &csedang dimatikan.", Map.of("%kit%", kit.getId()));
            return null;
        }
        if (!hasKitPermission(player, kit)) {
            send(player, "kit-locked-permission", "%prefix% &cKamu belum punya permission untuk kit ini.", Map.of());
            return null;
        }
        if (!hasPremiumPermission(player, kit)) {
            send(player, "premium-required", "%prefix% &cKit ini butuh akses premium: &f%premium_permission%", Map.of("%premium_permission%", getPremiumPermission(kit)));
            return null;
        }
        return kit;
    }

    private boolean hasKitPermission(Player player, Kit kit) {
        if (player.hasPermission(configManager.getAdminPermission())) return true;
        if (!configManager.isUsePerKitPermission()) return true;
        if (kit.getPermission().isBlank()) return true;
        if (kit.getPermission().equalsIgnoreCase("auto")) return player.hasPermission(configManager.getKitPermissionPrefix() + kit.getId());
        return player.hasPermission(kit.getPermission());
    }

    private boolean hasPremiumPermission(Player player, Kit kit) {
        if (kit.getPremiumLevel() <= 0 || player.hasPermission(configManager.getAdminPermission())) return true;
        return player.hasPermission(getPremiumPermission(kit));
    }

    private String getPremiumPermission(Kit kit) { return configManager.getPremiumPermissionPrefix() + kit.getPremiumLevel(); }

    private boolean isFreeClaimAvailable(Player player, Kit kit) {
        return isFirstClaimFreeEffective(kit) && !dataManager.hasClaimedFree(player.getUniqueId(), kit.getId());
    }

    private boolean isFirstClaimFreeEffective(Kit kit) {
        return kit.isFirstClaimFree() || kit.getPremiumLevel() > 0;
    }

    private boolean effectiveBuyEnabled(Kit kit) {
        return kit.isBuyEnabled() || kit.getPremiumLevel() > 0;
    }

    private double effectivePrice(Kit kit) {
        if (kit.getPremiumLevel() <= 0) return kit.getPrice();
        int level = Math.max(1, Math.min(5, kit.getPremiumLevel()));
        return level * 10000.0D;
    }

    private boolean handlePriceBeforeClaim(Player player, Kit kit, boolean freeClaim) {
        if (!effectiveBuyEnabled(kit) || freeClaim || player.hasPermission(configManager.getBypassPricePermission()) || player.hasPermission(configManager.getAdminPermission())) return true;
        if (kit.isOneTimePurchase()) {
            if (dataManager.hasPurchased(player.getUniqueId(), kit.getId())) return true;
            send(player, "need-buy", "%prefix% &cKit ini harus dibeli dulu. Gunakan &f/kits buy %kit%&c. Harga: &f$%price%&c.", Map.of("%kit%", kit.getId(), "%price%", formatPrice(effectivePrice(kit))));
            return false;
        }
        return chargePlayer(player, kit);
    }

    private boolean chargePlayer(Player player, Kit kit) {
        double price = effectivePrice(kit);
        if (price <= 0.0D || !configManager.isEconomyEnabled()) return true;
        if (!purchaseManager.hasEconomy()) {
            send(player, "vault-not-found", "%prefix% &cEconomy belum tersedia, pembelian kit tidak bisa digunakan.", Map.of());
            return false;
        }
        if (!purchaseManager.hasEnough(player, price)) {
            send(player, "not-enough-money", "%prefix% &cUang kamu kurang. Butuh &f$%price%&c.", Map.of("%price%", formatPrice(price)));
            return false;
        }
        boolean charged = purchaseManager.withdraw(player, price);
        if (charged) send(player, "kit-paid-claim", "%prefix% &aKamu membayar &f$%price% &auntuk claim kit &f%kit%&a.", Map.of("%price%", formatPrice(price), "%kit%", kit.getId()));
        return charged;
    }

    private Map<String, String> getKitPlaceholders(Kit kit) { return getKitPlaceholders(kit, cooldownManager.formatTime(kit.getCooldownMillis())); }

    private Map<String, String> getKitPlaceholders(Kit kit, String cooldown) {
        return Map.of(
                "%kit%", kit.getId(),
                "%kit_id%", kit.getId(),
                "%kit_display%", getKitDisplayName(kit),
                "%display_name%", getKitDisplayName(kit),
                "%cooldown%", cooldown,
                "%price%", formatPrice(effectivePrice(kit)),
                "%premium_level%", String.valueOf(kit.getPremiumLevel()),
                "%premium_permission%", getPremiumPermission(kit)
        );
    }

    private String getKitDisplayName(Kit kit) {
        String displayName = kit.getDisplayName();
        if (displayName == null || displayName.isBlank() || displayName.equalsIgnoreCase(kit.getId())) return prettifyKitId(kit.getId());
        return displayName;
    }

    private String prettifyKitId(String id) {
        if (id == null || id.isBlank()) return "Kit";
        String spaced = id.replace('_', ' ').replace('-', ' ').replaceAll("(?<=\\D)(?=\\d)", " ").trim();
        if (spaced.isEmpty()) return "Kit";
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1).toLowerCase(Locale.ROOT);
    }

    private void send(CommandSender sender, String path, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(configManager.color(applyPlaceholders(configManager.getMessage(path, fallback), placeholders)));
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text.replace("%prefix%", configManager.getPrefix());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) return String.valueOf((long) price);
        return String.format(Locale.US, "%.2f", price);
    }
}
