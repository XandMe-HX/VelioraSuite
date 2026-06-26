package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.module.trader.model.TraderCampBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TraderCampManager {

    private final TraderConfigManager configManager;
    private final TraderDataManager dataManager;
    private final Map<String, BlockData> originalBlocks = new HashMap<>();
    private int placedBlocks;
    private int skippedBlocks;

    public TraderCampManager(TraderConfigManager configManager, TraderDataManager dataManager) {
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    public void build(Location origin) {
        if (origin == null || origin.getWorld() == null || !configManager.isCampEnabled()) return;
        originalBlocks.clear();
        placedBlocks = 0;
        skippedBlocks = 0;
        Map<String, String> persisted = new LinkedHashMap<>();
        for (TraderCampBlock campBlock : configManager.getCampBlocks()) {
            Block block = origin.getWorld().getBlockAt(origin.getBlockX() + campBlock.offsetX(), origin.getBlockY() + campBlock.offsetY(), origin.getBlockZ() + campBlock.offsetZ());
            if (isNpcBodyBlock(origin, block)) {
                skippedBlocks++;
                continue;
            }
            BlockData oldData = block.getBlockData().clone();
            originalBlocks.put(key(block), oldData);
            persisted.put(key(block), oldData.getAsString());
            block.setType(campBlock.material(), false);
            placedBlocks++;
        }
        dataManager.saveCampBackup(persisted);
        if (configManager.isDebugSpawn()) Bukkit.getLogger().info("VelioraTrader debug: camp placed=" + placedBlocks + ", skipped=" + skippedBlocks);
    }

    public void restore() {
        if (!configManager.isRestoreOnDespawn()) {
            originalBlocks.clear();
            dataManager.clearCampBackup();
            return;
        }
        if (originalBlocks.isEmpty()) {
            for (Map.Entry<String, String> entry : dataManager.loadCampBackup().entrySet()) {
                try {
                    originalBlocks.put(entry.getKey(), Bukkit.createBlockData(entry.getValue()));
                } catch (Exception ignored) { }
            }
        }
        for (Map.Entry<String, BlockData> entry : originalBlocks.entrySet()) {
            String[] parts = entry.getKey().split(";");
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            world.getBlockAt(x, y, z).setBlockData(entry.getValue(), false);
        }
        originalBlocks.clear();
        dataManager.clearCampBackup();
    }

    public int getPlacedBlocks() { return placedBlocks; }
    public int getSkippedBlocks() { return skippedBlocks; }

    private boolean isNpcBodyBlock(Location origin, Block block) {
        int npcX = (int) Math.floor(origin.getX() + configManager.getNpcOffsetX());
        int npcFeetY = (int) Math.floor(origin.getY() + configManager.getNpcOffsetY());
        int npcZ = (int) Math.floor(origin.getZ() + configManager.getNpcOffsetZ());
        return block.getX() == npcX && block.getZ() == npcZ && (block.getY() == npcFeetY || block.getY() == npcFeetY + 1);
    }

    private String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }
}
