package id.velioragardens.veliorasuite.module.security.xray;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OreNeighborhoodTest {
    private OreColumn solid() {
        OreColumn c = new OreColumn(-64, 384);
        c.opaque.set(0, 384 * 256);
        return c;
    }
    private void ore(Map<Long, OreColumn> chunks, int x, int y, int z) {
        chunks.get(OreNeighborhood.key(x >> 4, z >> 4)).update(x & 15, y, z & 15, 99, true, true);
    }
    private Map<Long, OreColumn> pair(int x, int z) {
        Map<Long, OreColumn> c = new HashMap<>();
        c.put(OreNeighborhood.key(0, 0), solid());
        c.put(OreNeighborhood.key(x, z), solid());
        return c;
    }
    @Test void allFourEdgesHideWithKnownNeighbors() {
        int[][] edges = {{1,0,15,8},{-1,0,0,8},{0,1,8,15},{0,-1,8,0}};
        for (int[] e : edges) {
            var c = pair(e[0], e[1]);
            ore(c, e[2], -20, e[3]);
            assertEquals(1, OreNeighborhood.visible(c, e[2], -20, e[3], 99, 1));
        }
    }
    @Test void unknownNeighborExposesInsteadOfInventingCover() {
        var c = pair(1, 0);
        ore(c, 15, -20, 8);
        c.remove(OreNeighborhood.key(1, 0));
        assertEquals(99, OreNeighborhood.visible(c, 15, -20, 8, 99, 1));
    }
    @Test void miningAcrossPositiveBoundaryRevealsExactlyOnce() {
        var c = pair(1, 0);
        ore(c, 15, -20, 8);
        OreNeighborhood.visible(c, 15, -20, 8, 99, 1);
        c.get(OreNeighborhood.key(1, 0)).update(0, -20, 8, 0, false, false);
        assertEquals(Map.of(new OreNeighborhood.Position(15, -20, 8), 99),
                OreNeighborhood.revealAround(c, 16, -20, 8));
        assertTrue(OreNeighborhood.revealAround(c, 16, -20, 8).isEmpty());
    }
    @Test void miningAcrossNegativeBoundaryReveals() {
        var c = pair(-1, 0);
        ore(c, -1, -20, 8);
        assertEquals(1, OreNeighborhood.visible(c, -1, -20, 8, 99, 1));
        c.get(OreNeighborhood.key(0, 0)).update(0, -20, 8, 0, false, false);
        assertEquals(99, OreNeighborhood.revealAround(c, 0, -20, 8).get(new OreNeighborhood.Position(-1, -20, 8)));
    }
    @Test void arrivingNeighborHidesOnlyFacingEdge() {
        var c = pair(1, 0);
        OreColumn east = c.remove(OreNeighborhood.key(1, 0));
        ore(c, 15, -20, 8);
        ore(c, 8, -20, 8);
        OreNeighborhood.visible(c, 8, -20, 8, 99, 1);
        c.put(OreNeighborhood.key(1, 0), east);
        assertEquals(Map.of(new OreNeighborhood.Position(15, -20, 8), 1),
                OreNeighborhood.borders(c, 1, 0, id -> 1));
        assertTrue(OreNeighborhood.borders(c, 1, 0, id -> 1).isEmpty());
    }
    @Test void unloadingNeighborRestoresFacingOre() {
        var c = pair(1, 0);
        ore(c, 15, -20, 8);
        OreNeighborhood.visible(c, 15, -20, 8, 99, 1);
        c.remove(OreNeighborhood.key(1, 0));
        assertEquals(Map.of(new OreNeighborhood.Position(15, -20, 8), 99), OreNeighborhood.borders(c, 1, 0, id -> 1));
    }
    @Test void replacingNeighborWithCaveRestoresOre() {
        var c = pair(1, 0);
        ore(c, 15, -20, 8);
        OreNeighborhood.visible(c, 15, -20, 8, 99, 1);
        c.put(OreNeighborhood.key(1, 0), new OreColumn(-64, 384));
        assertEquals(99, OreNeighborhood.borders(c, 1, 0, id -> 1).get(new OreNeighborhood.Position(15, -20, 8)));
    }
    @Test void cornerNeedsTwoNeighborsButNotDiagonal() {
        var c = pair(1, 0);
        ore(c, 15, -20, 15);
        assertFalse(OreNeighborhood.enclosed(c, 15, -20, 15));
        c.put(OreNeighborhood.key(0, 1), solid());
        assertTrue(OreNeighborhood.enclosed(c, 15, -20, 15));
    }
    @Test void differentRecipientCannotRevealAnotherCache() {
        var first = pair(1, 0);
        var second = pair(1, 0);
        ore(first, 15, -20, 8); ore(second, 15, -20, 8);
        OreNeighborhood.visible(first, 15, -20, 8, 99, 1);
        OreNeighborhood.visible(second, 15, -20, 8, 99, 1);
        first.remove(OreNeighborhood.key(1, 0));
        OreNeighborhood.borders(first, 1, 0, id -> 1);
        assertTrue(second.get(OreNeighborhood.key(0, 0)).hidden.get(second.get(OreNeighborhood.key(0, 0)).index(15, -20, 8)));
    }
    @Test void explosionBatchDoesNotResurrectDestroyedOre() {
        var c = pair(1, 0);
        ore(c, 15, -20, 8);
        OreNeighborhood.visible(c, 15, -20, 8, 99, 1);
        c.get(OreNeighborhood.key(0, 0)).update(15, -20, 8, 0, false, false);
        c.get(OreNeighborhood.key(1, 0)).update(0, -20, 8, 0, false, false);
        assertTrue(OreNeighborhood.revealAround(c, 16, -20, 8).isEmpty());
    }
    @Test void sectionHeightBoundariesAndOverflowAreSafe() {
        assertEquals(384, OreNeighborhood.scanHeight(-64, 384, 320));
        assertEquals(128, OreNeighborhood.scanHeight(0, 256, 128));
        assertEquals(128, OreNeighborhood.scanHeight(0, 256, 143));
        assertEquals(0, OreNeighborhood.scanHeight(0, 256, -1));
        assertEquals(0, OreNeighborhood.scanHeight(-64, 384, Integer.MIN_VALUE));
        assertEquals(384, OreNeighborhood.scanHeight(-64, 384, Integer.MAX_VALUE));
    }
    @Test void negativeKeyDoesNotCollide() {
        assertNotEquals(OreNeighborhood.key(-1, 0), OreNeighborhood.key(0, -1));
        assertNotEquals(OreNeighborhood.key(-1, -1), OreNeighborhood.key(0, 0));
    }
}
