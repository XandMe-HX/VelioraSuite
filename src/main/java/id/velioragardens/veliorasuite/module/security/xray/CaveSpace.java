package id.velioragardens.veliorasuite.module.security.xray;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded connected-space check: never performs a world scan or loads chunks. */
public final class CaveSpace {
    public record Pos(int x, int y, int z) {}
    @FunctionalInterface public interface Open { boolean at(int x, int y, int z); }
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private CaveSpace() {}
    public static boolean near(Open open) {
        Set<Pos> seen = new HashSet<>();
        for (int[] face : FACES) {
            Pos start = new Pos(face[0], face[1], face[2]);
            if (seen.contains(start) || !open.at(start.x, start.y, start.z)) continue;
            var pending = new ArrayDeque<Pos>();
            pending.add(start); seen.add(start);
            int count = 0, minX = 9, maxX = -9, minY = 9, maxY = -9, minZ = 9, maxZ = -9;
            while (!pending.isEmpty() && count < 256) {
                Pos p = pending.removeFirst(); count++;
                minX = Math.min(minX,p.x); maxX = Math.max(maxX,p.x);
                minY = Math.min(minY,p.y); maxY = Math.max(maxY,p.y);
                minZ = Math.min(minZ,p.z); maxZ = Math.max(maxZ,p.z);
                // Ore on a cave wall has only four sampled cells towards that cave.
                if (count >= 64 && maxX-minX >= 3 && maxZ-minZ >= 3 && maxY-minY >= 2) return true;
                for (int[] f : FACES) {
                    Pos next = new Pos(p.x+f[0],p.y+f[1],p.z+f[2]);
                    if (Math.abs(next.x)>4 || Math.abs(next.y)>4 || Math.abs(next.z)>4
                            || seen.contains(next) || !open.at(next.x,next.y,next.z)) continue;
                    seen.add(next); pending.addLast(next);
                }
            }
        }
        return false;
    }
}
