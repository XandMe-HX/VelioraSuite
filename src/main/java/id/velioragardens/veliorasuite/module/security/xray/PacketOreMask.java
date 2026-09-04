package id.velioragardens.veliorasuite.module.security.xray;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import id.velioragardens.veliorasuite.VelioraSuite;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Own packet-only HIDE implementation. No Paper internal code or live-world access. */
public final class PacketOreMask extends PacketListenerAbstract implements AutoCloseable, Listener {
    private final VelioraSuite plugin;
    private final Set<String> worlds;
    private final Set<String> worldNames;
    private final Map<String, Integer> heightsByName;
    private final Map<String, Integer> heightsByKey = new ConcurrentHashMap<>();
    private final int maxColumns, maxPerPlayer, maxOres;
    private final Map<User, View> views = new ConcurrentHashMap<>();
    private final Map<Integer, State> states = new ConcurrentHashMap<>();
    // Fast direct lookup for common state IDs, like Paper's state-indexed lookup tables.
    // Overflow stays supported rather than assuming a fixed Minecraft registry size.
    private final AtomicReferenceArray<State> stateTable = new AtomicReferenceArray<>(65536);
    private final AtomicInteger columns = new AtomicInteger();
    private final AtomicLong masked = new AtomicLong(), skipped = new AtomicLong(), errors = new AtomicLong();
    private final AtomicLong lastWarning = new AtomicLong();
    private volatile boolean closed;
    // Deliberately conservative full opaque cubes, not StateType.isSolid (glass/slabs can be solid).
    private static final Set<String> COVER = Set.of("stone", "deepslate", "netherrack", "tuff", "granite",
            "diorite", "andesite", "dirt", "grass_block", "bedrock", "gravel", "sand", "red_sand",
            "sandstone", "red_sandstone", "basalt", "smooth_basalt", "blackstone", "end_stone",
            "clay", "calcite", "obsidian", "soul_soil");
    private static final Set<String> ORES = Set.of("coal_ore", "iron_ore", "copper_ore", "gold_ore",
            "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore", "deepslate_coal_ore",
            "deepslate_iron_ore", "deepslate_copper_ore", "deepslate_gold_ore", "deepslate_redstone_ore",
            "deepslate_lapis_ore", "deepslate_diamond_ore", "deepslate_emerald_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris");

    private static final class View {
        final String world;
        final Map<Long, OreColumn> chunks = new HashMap<>();
        boolean failed;
        View(String world) { this.world = world; }
    }
    private record State(boolean solid, boolean ore, int replacement) {}

    public static AutoCloseable start(VelioraSuite plugin) {
        plugin.saveResourceIfNotExists("modules/ore-mask.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "modules/ore-mask.yml"));
        if (!config.getBoolean("enabled", false)) return null;
        if (!plugin.getServer().getPluginManager().isPluginEnabled("packetevents")) {
            plugin.getLogger().warning("OreMask inactive: install PacketEvents 2.13.0 before enabling.");
            return null;
        }
        PacketOreMask mask = new PacketOreMask(plugin, config);
        PacketEvents.getAPI().getEventManager().registerListener(mask);
        plugin.getServer().getPluginManager().registerEvents(mask, plugin);
        plugin.getLogger().warning("OreMask EXPERIMENTAL enabled for " + mask.worlds
                + "; existing players must reconnect. Paper Anti-Xray must be disabled.");
        return mask;
    }

    private PacketOreMask(VelioraSuite plugin, YamlConfiguration config) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
        worldNames = Set.copyOf(config.getStringList("world-names"));
        heightsByName = new HashMap<>();
        for (String name : worldNames) {
            int fallback = name.equals("world_nether") ? 128 : name.equals("world") ? 320 : 512;
            heightsByName.put(name, config.getInt("max-block-height." + name, fallback));
        }
        worlds = ConcurrentHashMap.newKeySet();
        // Resolve actual dimension keys on the main thread; packet handlers never access Bukkit worlds.
        plugin.getServer().getWorlds().stream().filter(w -> worldNames.contains(w.getName()))
                .forEach(w -> {
                    worlds.add(w.getKey().toString());
                    heightsByKey.put(w.getKey().toString(), heightsByName.get(w.getName()));
                });
        maxColumns = Math.max(16, Math.min(8192, config.getInt("max-cached-columns", 2048)));
        maxPerPlayer = Math.max(16, Math.min(1024, config.getInt("max-columns-per-player", 512)));
        maxOres = Math.max(64, Math.min(4096, config.getInt("max-ores-per-column", 2048)));
        // Resolve server block-state IDs, not client IDs (ViaVersion translation happens separately).
        for (String ore : ORES) state(WrappedBlockState.getByString("minecraft:" + ore).getGlobalId());
    }

    private State state(int id) {
        if (id >= 0 && id < stateTable.length()) {
            State cached = stateTable.get(id);
            if (cached != null) return cached;
        }
        State result = states.computeIfAbsent(id, value -> {
            String name = WrappedBlockState.getByGlobalId(value).getType().getName().replace("minecraft:", "");
            boolean ore = ORES.contains(name);
            String replacement = name.startsWith("deepslate_") ? "deepslate"
                    : name.startsWith("nether_") || name.equals("ancient_debris") ? "netherrack" : "stone";
            return new State(ore || COVER.contains(name), ore,
                    ore ? WrappedBlockState.getByString("minecraft:" + replacement).getGlobalId() : value);
        });
        if (id >= 0 && id < stateTable.length()) stateTable.compareAndSet(id, null, result);
        return result;
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event) {
        if (worldNames.contains(event.getWorld().getName())) {
            heightsByKey.put(event.getWorld().getKey().toString(), heightsByName.get(event.getWorld().getName()));
            worlds.add(event.getWorld().getKey().toString());
        }
    }

    @Override public void onPacketSend(PacketSendEvent event) {
        if (closed || event.isCancelled()) return;
        User user = event.getUser();
        var type = event.getPacketType();
        try {
            if (type == PacketType.Play.Server.JOIN_GAME) {
                reset(user, new WrapperPlayServerJoinGame(event).getWorldName());
                return;
            }
            if (type == PacketType.Play.Server.RESPAWN) {
                reset(user, new WrapperPlayServerRespawn(event).getWorldName().orElse(""));
                return;
            }
            View view = views.get(user);
            if (view == null) return;
            synchronized (view) {
                if (closed || view.failed || views.get(user) != view) return;
                if (type == PacketType.Play.Server.UNLOAD_CHUNK) {
                    var packet = new WrapperPlayServerUnloadChunk(event);
                    if (view.chunks.remove(key(packet.getChunkX(), packet.getChunkZ())) != null) columns.decrementAndGet();
                    reconcileBorders(event, view, packet.getChunkX(), packet.getChunkZ());
                } else if (type == PacketType.Play.Server.CHUNK_DATA) chunk(event, view);
                else if (type == PacketType.Play.Server.BLOCK_CHANGE) block(event, view);
                else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) multi(event, view);
            }
        } catch (RuntimeException exception) {
            errors.incrementAndGet();
            // Do not continue mutating a recipient with uncertain packet state.
            View view = views.get(user);
            if (view != null) synchronized (view) {
                view.failed = true;
                // Restore known masks after the original packet, then stop masking this session.
                Map<Long, OreColumn> restore = new HashMap<>(view.chunks);
                event.getTasksAfterSend().add(() -> {
                    if (!closed && views.get(user) == view) restoreUser(user, restore);
                });
                columns.addAndGet(-view.chunks.size());
                view.chunks.clear();
            }
            warn("Packet processing failed; affected player must reconnect. " + exception.getClass().getSimpleName());
        }
    }

    private void chunk(PacketSendEvent event, View view) {
        var packet = new WrapperPlayServerChunkData(event);
        var column = packet.getColumn();
        long key = key(column.getX(), column.getZ());
        boolean replacing = view.chunks.containsKey(key);
        if (!replacing && (view.chunks.size() >= maxPerPlayer || columns.get() >= maxColumns)) {
            skipped.incrementAndGet();
            warn("Cache limit reached: NEW chunks pass through unprotected. Increase limits only after profiling.");
            return;
        }
        BaseChunk[] sections = column.getChunks();
        int height = sections.length * 16;
        if (height < 16 || height > 512) {
            if (view.chunks.remove(key) != null) columns.decrementAndGet();
            reconcileBorders(event, view, column.getX(), column.getZ());
            skipped.incrementAndGet(); return;
        }
        int minY = event.getUser().getMinWorldHeight();
        int scanHeight = OreNeighborhood.scanHeight(minY, height, heightsByKey.getOrDefault(view.world, 512));
        if (scanHeight == 0) {
            if (view.chunks.remove(key) != null) columns.decrementAndGet();
            reconcileBorders(event, view, column.getX(), column.getZ());
            skipped.incrementAndGet(); return;
        }
        OreColumn snapshot = new OreColumn(minY, scanHeight);
        for (int s = 0; s < scanHeight / 16; s++) {
            BaseChunk section = sections[s];
            if (section == null || section.isEmpty()) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int id = section.getBlockId(x, y, z);
                State state = state(id);
                snapshot.update(x, snapshot.minY + s * 16 + y, z, id, state.solid, state.ore);
                if (snapshot.ores.size() > maxOres) {
                    // A replacement chunk itself restores old client masking.
                    if (view.chunks.remove(key) != null) columns.decrementAndGet();
                    reconcileBorders(event, view, column.getX(), column.getZ());
                    skipped.incrementAndGet();
                    return;
                }
            }
        }
        if (!replacing && columns.incrementAndGet() > maxColumns) {
            columns.decrementAndGet(); skipped.incrementAndGet(); return;
        }
        view.chunks.put(key, snapshot);
        boolean changed = false;
        for (var ore : snapshot.ores.entrySet()) {
            int i = ore.getKey(), x = i & 15, z = (i >> 4) & 15, y = (i >> 8) + snapshot.minY;
            int original = ore.getValue();
            int visible = OreNeighborhood.visible(view.chunks, column.getX() * 16 + x, y,
                    column.getZ() * 16 + z, original, state(original).replacement);
            if (visible != original) {
                int relativeY = y - snapshot.minY;
                sections[relativeY >> 4].set(x, relativeY & 15, z, visible);
                changed = true;
                masked.incrementAndGet();
            }
        }
        if (changed) event.markForReEncode(true);
        reconcileBorders(event, view, column.getX(), column.getZ());
    }

    private void block(PacketSendEvent event, View view) {
        var packet = new WrapperPlayServerBlockChange(event);
        Vector3i p = packet.getBlockPosition();
        update(view, p.getX(), p.getY(), p.getZ(), packet.getBlockId());
        int visible = visible(view, p.getX(), p.getY(), p.getZ(), packet.getBlockId());
        if (visible != packet.getBlockId()) { packet.setBlockID(visible); event.markForReEncode(true); }
        reveal(event, view, List.of(p));
    }

    private void multi(PacketSendEvent event, View view) {
        var packet = new WrapperPlayServerMultiBlockChange(event);
        var blocks = packet.getBlocks();
        List<Vector3i> positions = new ArrayList<>(blocks.length);
        // Apply the complete batch before exposure tests (explosions/pistons/WorldEdit).
        for (var block : blocks) {
            update(view, block.getX(), block.getY(), block.getZ(), block.getBlockId());
            positions.add(new Vector3i(block.getX(), block.getY(), block.getZ()));
        }
        boolean changed = false;
        for (var block : blocks) {
            int visible = visible(view, block.getX(), block.getY(), block.getZ(), block.getBlockId());
            if (visible != block.getBlockId()) { block.setBlockId(visible); changed = true; }
        }
        if (changed) event.markForReEncode(true);
        reveal(event, view, positions);
    }

    private void update(View view, int x, int y, int z, int id) {
        OreColumn chunk = view.chunks.get(key(x >> 4, z >> 4));
        if (chunk == null) return;
        State state = state(id);
        // Never grow the sparse cache without a limit, even on ore-filled custom builds.
        boolean track = state.ore && (chunk.ores.size() < maxOres || chunk.ores.containsKey(chunk.index(x & 15, y, z & 15)));
        chunk.update(x & 15, y, z & 15, id, state.solid, track);
    }

    private int visible(View view, int x, int y, int z, int id) {
        OreColumn chunk = view.chunks.get(key(x >> 4, z >> 4));
        if (chunk == null || !chunk.contains(x & 15, y, z & 15)) return id;
        return OreNeighborhood.visible(view.chunks, x, y, z, id, state(id).replacement);
    }

    private void reveal(PacketSendEvent event, View view, List<Vector3i> positions) {
        Map<Vector3i, Integer> revealed = new HashMap<>();
        for (Vector3i p : positions) {
            OreNeighborhood.revealAround(view.chunks, p.getX(), p.getY(), p.getZ()).forEach((pos, id) ->
                    revealed.put(new Vector3i(pos.x(), pos.y(), pos.z()), id));
        }
        afterSend(event, view, revealed);
    }

    private void reconcileBorders(PacketSendEvent event, View view, int cx, int cz) {
        Map<Vector3i, Integer> changed = new HashMap<>();
        OreNeighborhood.borders(view.chunks, cx, cz, id -> state(id).replacement).forEach((p, id) ->
                changed.put(new Vector3i(p.x(), p.y(), p.z()), id));
        afterSend(event, view, changed);
    }

    private void afterSend(PacketSendEvent event, View view, Map<Vector3i, Integer> revealed) {
        User recipient = event.getUser();
        if (!revealed.isEmpty()) event.getTasksAfterSend().add(() -> {
            if (!closed && views.get(recipient) == view) sendChanges(recipient, revealed);
        });
    }

    private static void sendChanges(User user, Map<Vector3i, Integer> changes) {
        Map<Vector3i, List<WrapperPlayServerMultiBlockChange.EncodedBlock>> sections = new HashMap<>();
        changes.forEach((p, id) -> sections.computeIfAbsent(new Vector3i(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4),
                k -> new ArrayList<>()).add(new WrapperPlayServerMultiBlockChange.EncodedBlock(id, p.getX(), p.getY(), p.getZ())));
        sections.forEach((section, blocks) -> user.sendPacketSilently(new WrapperPlayServerMultiBlockChange(section, false,
                blocks.toArray(WrapperPlayServerMultiBlockChange.EncodedBlock[]::new))));
    }

    private void reset(User user, String world) {
        discard(user);
        if (worlds.contains(world)) views.put(user, new View(world));
    }
    private void discard(User user) {
        View old = views.remove(user);
        if (old != null) synchronized (old) { columns.addAndGet(-old.chunks.size()); old.chunks.clear(); }
    }
    @Override public void onUserDisconnect(UserDisconnectEvent event) { discard(event.getUser()); }
    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    private void warn(String message) {
        long now = System.currentTimeMillis(), previous = lastWarning.get();
        if (now - previous > 30000 && lastWarning.compareAndSet(previous, now)) plugin.getLogger().warning("OreMask: " + message);
    }
    public String status() {
        return "OreMask experimental: players=" + views.size() + ", columns=" + columns.get()
                + "/" + maxColumns + ", masked=" + masked.get() + ", unprotected-chunks=" + skipped.get() + ", errors=" + errors.get();
    }
    @Override public String toString() { return status(); }
    private static void restoreUser(User user, Map<Long, OreColumn> chunks) {
        // Bound temporary restoration memory to one column, not a whole player's view.
        chunks.forEach((key, chunk) -> {
            Map<Vector3i, Integer> restore = new HashMap<>();
            chunk.ores.forEach((i, id) -> {
                if (chunk.hidden.get(i)) restore.put(new Vector3i((int)(key >> 32) * 16 + (i & 15),
                        chunk.minY + (i >> 8), (int)(long)key * 16 + ((i >> 4) & 15)), id);
            });
            sendChanges(user, restore);
        });
    }
    @Override public void close() {
        closed = true;
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        HandlerList.unregisterAll(this);
        views.forEach((user, view) -> {
            synchronized (view) {
                try { restoreUser(user, view.chunks); }
                catch (RuntimeException failure) { warn("Could not restore a disconnected recipient; reconnect resets their chunks."); }
            }
        });
        plugin.getLogger().info(status());
        views.clear(); columns.set(0); states.clear();
        for (int i = 0; i < stateTable.length(); i++) stateTable.set(i, null);
    }
}
