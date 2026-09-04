package id.velioragardens.veliorasuite.module.security.xray;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/** Six-face exposure using only available snapshots, following Paper's loaded-neighbor approach.
 * Independent implementation; does not call or copy Paper's NMS controller. */
final class OreNeighborhood {
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    record Position(int x, int y, int z) {}
    static long key(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }
    static int scanHeight(int minY, int height, int exclusiveMaxY) {
        // Section-aligned exclusive ceiling, matching Paper's max-block-height convention.
        long alignedCeiling = Math.floorDiv((long)exclusiveMaxY, 16) * 16;
        return (int)Math.max(0, Math.min(height, alignedCeiling - minY));
    }

    static boolean enclosed(Map<Long, OreColumn> chunks, int x, int y, int z) {
        for (int[] face : FACES) {
            int nx = x + face[0], ny = y + face[1], nz = z + face[2];
            OreColumn neighbor = chunks.get(key(nx >> 4, nz >> 4));
            if (neighbor == null || !neighbor.contains(nx & 15, ny, nz & 15)
                    || !neighbor.opaque.get(neighbor.index(nx & 15, ny, nz & 15))) return false;
        }
        return true;
    }

    static int visible(Map<Long, OreColumn> chunks, int x, int y, int z, int original, int replacement) {
        OreColumn chunk = chunks.get(key(x >> 4, z >> 4));
        if (chunk == null || !chunk.contains(x & 15, y, z & 15)) return original;
        int i = chunk.index(x & 15, y, z & 15);
        boolean hide = chunk.ores.containsKey(i) && enclosed(chunks, x, y, z);
        chunk.hidden.set(i, hide);
        return hide ? replacement : original;
    }

    static Map<Position, Integer> revealAround(Map<Long, OreColumn> chunks, int x, int y, int z) {
        Map<Position, Integer> changes = new HashMap<>();
        for (int[] face : FACES) {
            int nx = x + face[0], ny = y + face[1], nz = z + face[2];
            OreColumn chunk = chunks.get(key(nx >> 4, nz >> 4));
            if (chunk == null || !chunk.contains(nx & 15, ny, nz & 15)) continue;
            int i = chunk.index(nx & 15, ny, nz & 15);
            Integer original = chunk.ores.get(i);
            if (original != null && chunk.hidden.get(i) && !enclosed(chunks, nx, ny, nz)) {
                chunk.hidden.clear(i);
                changes.put(new Position(nx, ny, nz), original);
            }
        }
        return changes;
    }

    /** Reconcile only the four facing edges when a neighbor loads, is replaced or disappears. */
    static Map<Position, Integer> borders(Map<Long, OreColumn> chunks, int cx, int cz, IntUnaryOperator replacement) {
        Map<Position, Integer> changes = new HashMap<>();
        for (int[] offset : FACES) {
            if (offset[1] != 0) continue;
            int nx = cx + offset[0], nz = cz + offset[2];
            OreColumn chunk = chunks.get(key(nx, nz));
            if (chunk == null) continue;
            chunk.ores.forEach((i, original) -> {
                int lx = i & 15, lz = (i >> 4) & 15;
                if (offset[0] == 1 && lx != 0 || offset[0] == -1 && lx != 15
                        || offset[2] == 1 && lz != 0 || offset[2] == -1 && lz != 15) return;
                int x = nx * 16 + lx, y = chunk.minY + (i >> 8), z = nz * 16 + lz;
                boolean wasHidden = chunk.hidden.get(i);
                int state = visible(chunks, x, y, z, original, replacement.applyAsInt(original));
                if (wasHidden != chunk.hidden.get(i)) changes.put(new Position(x, y, z), state);
            });
        }
        return changes;
    }
}
