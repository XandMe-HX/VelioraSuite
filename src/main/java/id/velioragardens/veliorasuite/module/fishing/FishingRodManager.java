package id.velioragardens.veliorasuite.module.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.FishHook;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import id.velioragardens.veliorasuite.module.fishing.model.FishingRodDefinition;

import java.util.List;

public final class FishingRodManager implements Listener {

    private static final String SHOP_TITLE = "§8Fishing Rod Shop";
    private static final String QUEST_TITLE = "§8Fishing Quest Rods";

    private final FishingManager manager;
    private final NamespacedKey tierKey;
    private final NamespacedKey ownerKey;
    private List<FishingRodDefinition> rods;

    public FishingRodManager(FishingManager manager) {
        this.manager = manager;
        tierKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_rod_tier");
        ownerKey = new NamespacedKey(manager.getConfigManager().getPlugin(), "fishing_rod_owner");
        rods = manager.getConfigManager().getRodDefinitions();
        startAmbientAura();
    }

    private void startAmbientAura() {
        final double[] phase = {0.0D};
        Bukkit.getScheduler().runTaskTimer(manager.getConfigManager().getPlugin(), () -> {
            phase[0] += Math.PI / 8.0D;
            for (Player player : Bukkit.getOnlinePlayers()) {
                int tier = getTier(player);
                if (tier < 1 || player.isDead()) continue;
                showHeldAura(player, tier, phase[0]);
            }
        }, 20L, 10L);
    }

    /**
     * Aura ketika rod dipegang. Jumlah partikel sengaja kecil dan hanya untuk
     * pemilik rod, supaya efek tetap terlihat mewah tanpa membebani server.
     */
    private void showHeldAura(Player player, int tier, double phase) {
        Particle particle = particleForTier(tier);
        // Tier rendah sengaja memakai motif berbeda per rod, bukan hanya warna/partikel yang diganti.
        if (tier <= 4) {
            switch (tier) {
                case 1 -> { for (int i = 0; i < 5; i++) point(player, particle, phase + i * Math.PI * 2.0D / 5.0D, 0.30D, 0.72D); }
                case 2 -> { for (int i = 0; i < 5; i++) point(player, particle, phase + i * 0.65D, 0.20D + i * 0.045D, 0.50D + i * 0.15D); }
                case 3 -> { for (int i = 0; i < 4; i++) point(player, particle, phase + i * Math.PI / 2.0D, i % 2 == 0 ? 0.55D : 0.28D, 0.85D); }
                default -> { for (int i = 0; i < 6; i++) point(player, particle, phase + i * Math.PI / 3.0D, 0.42D, 0.72D + (i % 2) * 0.22D); }
            }
            return;
        }
        // Tier 5-8: spiral/gelombang berbeda agar setiap upgrade terasa baru.
        if (tier <= 8) {
            int points = tier == 5 ? 5 : tier == 6 ? 6 : tier == 7 ? 7 : 8;
            for (int i = 0; i < points; i++) {
                double angle = phase + i * (tier == 8 ? 1.10D : 0.78D);
                point(player, particle, angle, 0.25D + i * 0.045D, 0.50D + i * 0.19D);
            }
            return;
        }
        // Tier 9+ is intentionally a per-rod signature, not one shared wing effect.
        switch (tier) {
            case 9 -> { // Steampunk: two rotating copper gear rings.
                ring(player, Particle.ELECTRIC_SPARK, phase, 4, 0.38D, 0.70D);
                ring(player, Particle.ELECTRIC_SPARK, -phase, 4, 0.58D, 1.22D);
            }
            case 10 -> { // Fluorescent: neon double halo.
                ring(player, Particle.GLOW, phase, 6, 0.46D, 1.02D);
                ring(player, Particle.END_ROD, -phase * 0.7D, 4, 0.27D, 1.58D);
            }
            case 11 -> { // Lava: rising ember column.
                for (int i = 0; i < 6; i++) point(player, Particle.FLAME, phase + i * 1.05D, 0.24D + i * 0.05D, 0.52D + i * 0.20D);
            }
            case 12 -> { // Radioactive: unstable green orbit.
                ring(player, Particle.COMPOSTER, phase, 5, 0.62D, 1.04D);
                particle(player.getLocation().add(0, 1.48D, 0), Particle.ELECTRIC_SPARK);
            }
            case 13 -> { // Obsidian: four floating shards.
                for (int i = 0; i < 4; i++) point(player, Particle.REVERSE_PORTAL, phase + i * Math.PI / 2.0D, 0.62D, 1.15D + (i % 2) * 0.30D);
            }
            case 14 -> wings(player, Particle.END_ROD, phase, 5, 0.62D, 1.03D); // Chrome
            case 15 -> { // Coral: bubbles drifting upward around the player.
                ring(player, Particle.BUBBLE_POP, phase, 6, 0.55D, 0.70D);
                ring(player, Particle.NAUTILUS, -phase, 3, 0.32D, 1.48D);
            }
            case 16 -> { // Poseidon: wide sea crown.
                wings(player, Particle.DOLPHIN, phase, 5, 0.72D, 0.98D);
                ring(player, Particle.NAUTILUS, phase, 5, 0.48D, 2.02D);
            }
            case 17 -> { // Ghostfinn: spectral fins.
                wings(player, Particle.SOUL, phase, 6, 0.72D, 1.02D);
                ring(player, Particle.SOUL, -phase, 4, 0.34D, 1.84D);
            }
            case 18 -> { // Angler: ancient luminous crown.
                ring(player, Particle.GLOW, phase, 6, 0.45D, 2.08D);
                particle(player.getLocation().add(0, 2.42D, 0), Particle.ELECTRIC_SPARK);
            }
            case 19 -> { // Ares: a fiery battle cross.
                for (int i = 0; i < 4; i++) point(player, Particle.SOUL_FIRE_FLAME, phase + i * Math.PI / 2.0D, 0.63D, 1.10D);
                ring(player, Particle.FLAME, -phase, 4, 0.34D, 1.72D);
            }
            case 20 -> { // Element: one visible orbit for each element.
                Particle[] elements = {Particle.FLAME, Particle.BUBBLE_POP, Particle.ENCHANT, Particle.ELECTRIC_SPARK};
                for (int i = 0; i < elements.length; i++) point(player, elements[i], phase + i * Math.PI / 2.0D, 0.68D, 1.16D);
                ring(player, Particle.END_ROD, -phase, 4, 0.38D, 2.00D);
            }
            default -> { // Diamond and future highest rods: crystal constellation.
                ring(player, Particle.END_ROD, phase, 8, 0.64D, 1.10D);
                ring(player, Particle.ELECTRIC_SPARK, -phase, 4, 0.34D, 2.08D);
            }
        }
    }

    public void reload() {
        rods = manager.getConfigManager().getRodDefinitions();
    }

    public void open(Player player) {
        open(player, false);
    }

    public void openQuest(Player player) {
        open(player, true);
    }

    private void open(Player player, boolean quests) {
        if (!manager.getConfigManager().isRodsEnabled()) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eRod Shop sedang dimatikan."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, quests ? QUEST_TITLE : SHOP_TITLE);
        int[] slots = rodSlots();
        List<FishingRodDefinition> shown = shownRods(quests);
        for (int i = 0; i < shown.size() && i < slots.length; i++) inventory.setItem(slots[i], createShopItem(player, shown.get(i)));
        inventory.setItem(45, basic(Material.ARROW, "§aKembali", List.of("§7Kembali ke menu Fishing.")));
        inventory.setItem(49, basic(Material.SUNFLOWER, "§6Saldo: §f" + manager.formattedCoins(player) + " Koin", List.of("§7Mata uang khusus VelioraFishing.", "§7Tidak memengaruhi ekonomi Vault.")));
        inventory.setItem(53, basic(Material.BARRIER, "§cTutup", List.of("§7Tutup Rod Shop.")));
        player.openInventory(inventory);
    }

    public int getTier(Player player) {
        if (player == null) return 0;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        String owner = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (tier == null || tier < 1 || definition(tier).tier() != tier) return 0;
        return player.getUniqueId().toString().equals(owner) && manager.getRodDataManager().has(player.getUniqueId(), tier) ? tier : 0;
    }

    public int clickReduction(Player player) {
        return definition(getTier(player)).clickReduction();
    }

    public int secondsBonus(Player player) {
        return definition(getTier(player)).secondsBonus();
    }

    public int speedPercent(Player player) {
        return definition(getTier(player)).speedPercent();
    }

    public void showAura(Player player, FishHook hook) {
        int tier = getTier(player);
        if (tier < 3 || hook == null || !hook.isValid()) return;
        Particle particle = particleForTier(tier);
        double phase = (System.currentTimeMillis() % 4000L) / 4000.0D * Math.PI * 2.0D;
        // 3-5 ring, 6-10 double spiral, 11-16 wings, then a unique hooked signature per endgame rod.
        if (tier <= 5) {
            for (int i = 0; i < 10; i++) point(player, particle, phase + i * Math.PI / 5.0D, 0.75D, 1.05D);
        } else if (tier <= 10) {
            for (int i = 0; i < 7; i++) {
                double angle = phase + i * 0.7D;
                point(player, particle, angle, 0.55D, 0.45D + i * 0.14D);
                point(player, particle, angle + Math.PI, 0.55D, 0.45D + i * 0.14D);
            }
        } else if (tier <= 16) {
            wings(player, particle, phase, 7, 0.82D, 1.12D);
        } else {
            switch (tier) {
                case 17 -> { // Abyss: a low soul-current around player and hook.
                    ring(player, Particle.SOUL, phase, 8, 0.66D, 0.72D);
                    ring(player, Particle.SOUL, -phase, 5, 0.38D, 1.55D);
                }
                case 18 -> { // Spirit: vertical sparks rather than another crown.
                    for (int i = 0; i < 5; i++) point(player, Particle.ELECTRIC_SPARK, phase + i * Math.PI * 0.4D, 0.48D, 0.70D + i * 0.30D);
                    ring(player, Particle.ENCHANT, -phase, 5, 0.36D, 1.95D);
                }
                case 19 -> { // Ares: battle-cross and ember ring.
                    for (int i = 0; i < 4; i++) point(player, Particle.SOUL_FIRE_FLAME, phase + i * Math.PI / 2.0D, 0.70D, 1.12D);
                    ring(player, Particle.FLAME, -phase, 6, 0.46D, 0.52D);
                }
                case 20 -> { // Element: four clearly separate elements circle the hooked fight.
                    Particle[] elements = {Particle.FLAME, Particle.BUBBLE_POP, Particle.ENCHANT, Particle.ELECTRIC_SPARK};
                    for (int i = 0; i < elements.length; i++) point(player, elements[i], phase + i * Math.PI / 2.0D, 0.74D, 1.30D);
                    ring(player, Particle.END_ROD, -phase, 5, 0.44D, 2.02D);
                }
                default -> { // Diamond: bright two-level constellation.
                    ring(player, Particle.END_ROD, phase, 9, 0.72D, 1.10D);
                    ring(player, Particle.ELECTRIC_SPARK, -phase, 5, 0.38D, 2.12D);
                }
            }
        }
        Particle hookParticle = switch (tier) {
            case 17 -> Particle.SOUL;
            case 18 -> Particle.ELECTRIC_SPARK;
            case 19 -> Particle.FLAME;
            case 20 -> Particle.BUBBLE_POP;
            default -> particle;
        };
        manager.getConfigManager().getPlugin().getEffects().particle(hook.getLocation(), hookParticle, Math.min(10, 3 + tier / 3), 0.18D, 0.18D, 0.18D, 0.01D);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (getBoundTier(event.getItemDrop().getItemStack()) <= 0) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eRod ini terikat pada pemiliknya."));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(SHOP_TITLE) && !event.getView().getTitle().equals(QUEST_TITLE)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(manager.getConfigManager().getPlugin(), () -> manager.openMainGui(player));
            return;
        }
        if (slot == 53) { player.closeInventory(); return; }
        int index = rodIndex(slot);
        List<FishingRodDefinition> shown = shownRods(event.getView().getTitle().equals(QUEST_TITLE));
        if (index < 0 || index >= shown.size()) return;
        buy(player, shown.get(index));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(SHOP_TITLE) || event.getView().getTitle().equals(QUEST_TITLE)) event.setCancelled(true);
    }

    private void buy(Player player, FishingRodDefinition rod) {
        if (!manager.getConfigManager().isRodsEnabled()) return;
        boolean bypass = player.hasPermission(manager.getConfigManager().getRodBypassPermission())
                || player.hasPermission(manager.getConfigManager().getAdminPermission()) || player.isOp();
        boolean unlocked = manager.getRodDataManager().has(player.getUniqueId(), rod.tier());
        int owned = manager.getRodDataManager().highest(player.getUniqueId(), maxTier());
        if (unlocked) {
            if (hasBoundRod(player, rod.tier())) {
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eRod ini sudah ada di inventory kamu."));
                return;
            }
            if (player.getInventory().firstEmpty() < 0) {
                player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eInventory kamu penuh."));
                return;
            }
            player.getInventory().addItem(createRod(player, rod));
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&aRod berhasil diambil kembali."));
            player.closeInventory();
            return;
        }
        if (!bypass && rod.tier() > 1 && owned < rod.tier() - 1) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cRod belum terbuka. &7Misi: buka Rod Tier &f" + (rod.tier() - 1) + " &7terlebih dahulu."));
            return;
        }
        int catches = manager.getDataManager().getOrCreate(player).getTotalCatches();
        if (!bypass && catches < rod.requiredCatches()) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cRod belum terbuka. &7Misi: tangkap &f" + rod.requiredCatches() + " ikan &8(kamu: &f" + catches + "&8)."));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&eInventory kamu penuh."));
            return;
        }
        if (!bypass && !manager.withdrawRodCost(player, rod.price())) {
            player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&cKoin Fishing tidak cukup."));
            return;
        }
        manager.getRodDataManager().unlock(player.getUniqueId(), rod.tier());
        player.getInventory().addItem(createRod(player, rod));
        player.sendMessage(manager.getConfigManager().color(manager.getConfigManager().getPrefix() + "&aKamu mendapatkan rod baru!"));
        player.closeInventory();
    }

    private boolean hasBoundRod(Player player, int tier) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (getBoundTier(item, player) == tier) return true;
        }
        return false;
    }

    private ItemStack createShopItem(Player player, FishingRodDefinition rod) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(gradient(rod.name(), rod.fromColor(), rod.toColor()));
        boolean bypass = player.hasPermission(manager.getConfigManager().getRodBypassPermission())
                || player.hasPermission(manager.getConfigManager().getAdminPermission()) || player.isOp();
        int catches = manager.getDataManager().getOrCreate(player).getTotalCatches();
        List<Component> lore = List.of(
                Component.text(""),
                Component.text("Minigame", TextColor.color(0x55D6FF)),
                Component.text(" +" + rod.secondsBonus() + ".0 detik waktu", TextColor.color(0xB8C4D2)),
                Component.text(" -" + rod.clickReduction() + " klik diperlukan", TextColor.color(0xB8C4D2)),
                Component.text(" Luck " + rod.luckPercent() + "% • Speed " + rod.speedPercent() + "%", TextColor.color(0x70E0C0)),
                Component.text(" Max Weight " + Math.round(rod.maxWeight()) + " Kg", TextColor.color(0x70E0C0)),
                Component.text(""),
                Component.text("Aura", TextColor.color(0x55D6FF)),
                Component.text(" " + rod.aura(), TextColor.color(0xB8C4D2)),
                Component.text(""),
                Component.text(bypass ? " Admin bypass aktif" : " Syarat: " + rod.requiredCatches() + " tangkapan", TextColor.color(bypass ? 0x70E090 : 0xE6CE79)),
                Component.text(bypass ? " Gratis untuk admin" : rod.questRod() ? " Rod misi khusus" : " Harga: " + manager.getConfigManager().formatCoins(rod.price()) + " Koin", TextColor.color(bypass ? 0x70E090 : 0xE6CE79)),
                Component.text(" Kamu: " + catches + " tangkapan", TextColor.color(0x8391A5)),
                Component.text(""),
                Component.text("Klik untuk membeli", TextColor.color(0xFFFFFF))
        );
        meta.lore(lore);
        applyRodEnchantments(meta, rod.tier());
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private void point(Player player, Particle particle, double angle, double radius, double y) {
        particle(player.getLocation().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius), particle);
    }

    private void ring(Player player, Particle particle, double phase, int points, double radius, double y) {
        for (int i = 0; i < points; i++) point(player, particle, phase + i * Math.PI * 2.0D / points, radius, y);
    }

    private void particle(Location location, Particle particle) {
        manager.getConfigManager().getPlugin().getEffects().particle(location, particle, 1, 0, 0, 0, 0);
    }

    /** Each rod tier deliberately has its own particle identity, not a shared tier group. */
    private Particle particleForTier(int tier) {
        return switch (tier) {
            case 1 -> Particle.BUBBLE_POP;
            case 2 -> Particle.HAPPY_VILLAGER;
            case 3 -> Particle.SMOKE;
            case 4 -> Particle.SPORE_BLOSSOM_AIR;
            case 5 -> Particle.ENCHANT;
            case 6 -> Particle.SNOWFLAKE;
            case 7 -> Particle.WAX_ON;
            case 8 -> Particle.PORTAL;
            case 9 -> Particle.ELECTRIC_SPARK;
            case 10 -> Particle.GLOW;
            case 11 -> Particle.FLAME;
            case 12 -> Particle.COMPOSTER;
            case 13 -> Particle.REVERSE_PORTAL;
            case 14 -> Particle.END_ROD;
            case 15 -> Particle.DOLPHIN;
            case 16 -> Particle.NAUTILUS;
            case 17 -> Particle.SOUL;
            // CRIT membutuhkan payload berbeda pada sebagian build Paper 1.21.11.
            case 18 -> Particle.ELECTRIC_SPARK;
            case 19 -> Particle.LAVA;
            case 20 -> Particle.TOTEM_OF_UNDYING;
            default -> Particle.FIREWORK;
        };
    }

    /** Places a mirrored wing in coordinates relative to the player's yaw. */
    private void wings(Player player, Particle particle, double phase, int feathers, double width, double baseY) {
        for (int i = 0; i < feathers; i++) {
            double progress = feathers <= 1 ? 0.0D : (double) i / (feathers - 1);
            double side = 0.20D + progress * width;
            double y = baseY + Math.sin(progress * Math.PI) * 0.48D
                    + Math.sin(phase + i * 0.55D) * 0.055D;
            double behind = -0.18D - progress * 0.22D;
            spawnLocal(player, particle, side, y, behind);
            spawnLocal(player, particle, -side, y, behind);
            if (i > 1) {
                spawnLocal(player, particle, side * 0.88D, y - 0.16D, behind - 0.04D);
                spawnLocal(player, particle, -side * 0.88D, y - 0.16D, behind - 0.04D);
            }
        }
    }

    private void spawnLocal(Player player, Particle particle, double rightOffset, double y, double forwardOffset) {
        Vector forward = player.getEyeLocation().getDirection().clone().setY(0);
        if (forward.lengthSquared() < 0.0001D) forward.setZ(1);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location point = player.getLocation().clone().add(0, y, 0)
                .add(right.multiply(rightOffset)).add(forward.multiply(forwardOffset));
        particle(point, particle);
    }

    private ItemStack createRod(Player owner, FishingRodDefinition rod) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(gradient(rod.name(), rod.fromColor(), rod.toColor()));
        meta.lore(List.of(
                Component.text("VelioraFishing Rod • Tier " + rod.tier(), TextColor.color(0x55D6FF)),
                Component.text("+" + rod.secondsBonus() + ".0 detik • -" + rod.clickReduction() + " klik", TextColor.color(0xD6E0EB)),
                Component.text("Luck " + rod.luckPercent() + "% • Speed " + rod.speedPercent() + "% • Max " + Math.round(rod.maxWeight()) + " Kg", TextColor.color(0x70E0C0)),
                Component.text("Custom: " + customEnchantName(rod.tier()), TextColor.color(0xB56CFF)),
                Component.text("Enchant: Lure " + rod.tier() + " • Luck " + rod.tier() + " • Unbreaking " + (rod.tier() + 2), TextColor.color(0x70E0C0)),
                Component.text("Terikat: " + owner.getName(), TextColor.color(0x8391A5))
        ));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, rod.tier());
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        applyRodEnchantments(meta, rod.tier());
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private void applyRodEnchantments(ItemMeta meta, int tier) {
        int level = Math.max(1, Math.min(5, tier));
        meta.addEnchant(Enchantment.LURE, level, true);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, level, true);
        meta.addEnchant(Enchantment.UNBREAKING, level + 2, true);
        if (tier >= 4) meta.addEnchant(Enchantment.MENDING, 1, true);
    }

    private String customEnchantName(int tier) {
        return switch (tier) {
            case 1 -> "River Sense I";
            case 2 -> "Current Reader II";
            case 3 -> "Tidal Focus III";
            case 4 -> "Abyssal Hunter IV";
            default -> "Leviathan's Favor V";
        };
    }

    private int getBoundTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer tier = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return tier == null ? 0 : tier;
    }

    private int getBoundTier(ItemStack item, Player owner) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        String rodOwner = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return tier != null && owner.getUniqueId().toString().equals(rodOwner) ? tier : 0;
    }

    private FishingRodDefinition definition(int tier) {
        return rods.stream().filter(rod -> rod.tier() == tier).findFirst().orElse(rods.getFirst());
    }

    private int maxTier() {
        return rods.stream().mapToInt(FishingRodDefinition::tier).max().orElse(1);
    }

    private int[] rodSlots() {
        return new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    }

    private List<FishingRodDefinition> shownRods(boolean quests) {
        return rods.stream().filter(rod -> rod.questRod() == quests).toList();
    }

    private int rodIndex(int slot) {
        int[] slots = rodSlots();
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i;
        return -1;
    }

    private ItemStack filler() {
        return basic(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private ItemStack basic(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component gradient(String text, String from, String to) {
        TextColor first = TextColor.fromHexString(from);
        TextColor last = TextColor.fromHexString(to);
        if (first == null || last == null || text.isEmpty()) return Component.text(text);
        Component result = Component.empty();
        for (int index = 0; index < text.length(); index++) {
            double ratio = text.length() == 1 ? 0.0D : index / (double) (text.length() - 1);
            int red = (int) Math.round(first.red() + (last.red() - first.red()) * ratio);
            int green = (int) Math.round(first.green() + (last.green() - first.green()) * ratio);
            int blue = (int) Math.round(first.blue() + (last.blue() - first.blue()) * ratio);
            result = result.append(Component.text(String.valueOf(text.charAt(index)), TextColor.color(red, green, blue)));
        }
        return result;
    }

}
