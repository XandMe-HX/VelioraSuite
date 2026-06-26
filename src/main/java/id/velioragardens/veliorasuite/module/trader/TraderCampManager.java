package id.velioragardens.veliorasuite.module.trader;

import id.velioragardens.veliorasuite.module.trader.model.TraderCampBlock;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;

public final class TraderCampManager {

    private final TraderConfigManager configManager;
    private final Map<String, BlockData> originalBlocks = new HashMap<>();

    public TraderCampManager(TraderConfigManager configManager) {
        this.configManager = configManager;
    }

    public void build(Location origin) {
        if (origin == null || origin.getWorld() == null || !configManager.isCampEnabled()) return;
        originalBlocks.clear();
        for (TraderCampBlock campBlock : configManager.getCampBlocks()) {
            Block block = origin.getWorld().getBlockAt(origin.getBlockX() + campBlock.offsetX(), origin.getBlockY() + campBlock.offsetY(), origin.getBlockZ() + campBlock.offsetZ());
            originalBlocks.put(key(block), block.getBlockData().clone());
            block.setType(campBlock.material(), false);
        }
    }

    public void restore() {
        if (!configManager.isRestoreOnDespawn()) {
            originalBlocks.clear();
            return;
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
    }

    private String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }
}
