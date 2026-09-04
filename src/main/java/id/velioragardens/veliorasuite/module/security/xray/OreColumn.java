package id.velioragardens.veliorasuite.module.security.xray;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/** A recipient's packet snapshot, never a live Bukkit chunk. */
final class OreColumn {
    final int minY;
    final int height;
    final BitSet opaque;
    final Map<Integer, Integer> ores = new HashMap<>();
    final BitSet hidden = new BitSet();

    OreColumn(int minY, int height) {
        this.minY = minY;
        this.height = height;
        opaque = new BitSet(height * 256);
    }

    int index(int x, int y, int z) { return ((y - minY) << 8) | (z << 4) | x; }
    boolean contains(int x, int y, int z) {
        return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= minY && y < minY + height;
    }
    boolean enclosed(int x, int y, int z) {
        // Unknown neighboring chunks are intentionally treated as exposed.
        if (x <= 0 || x >= 15 || z <= 0 || z >= 15 || y <= minY || y >= minY + height - 1) return false;
        int i = index(x, y, z);
        return opaque.get(i - 1) && opaque.get(i + 1)
                && opaque.get(i - 16) && opaque.get(i + 16)
                && opaque.get(i - 256) && opaque.get(i + 256);
    }
    void update(int x, int y, int z, int state, boolean solid, boolean ore) {
        if (!contains(x, y, z)) return;
        int i = index(x, y, z);
        opaque.set(i, solid);
        if (ore) ores.put(i, state);
        else { ores.remove(i); hidden.clear(i); }
    }
    int visibleState(int x, int y, int z, int original, int replacement) {
        int i = index(x, y, z);
        boolean mask = ores.containsKey(i) && enclosed(x, y, z);
        hidden.set(i, mask);
        return mask ? replacement : original;
    }
    Map<Integer, Integer> revealAround(int x, int y, int z) {
        Map<Integer, Integer> result = new HashMap<>();
        int[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : directions) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (!contains(nx, ny, nz)) continue;
            int i = index(nx, ny, nz);
            if (hidden.get(i) && !enclosed(nx, ny, nz)) {
                Integer state = ores.get(i);
                if (state != null) result.put(i, state);
                hidden.clear(i);
            }
        }
        return result;
    }
}
