package id.velioragardens.veliorasuite.module.security.xray;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OreColumnTest {
    private OreColumn filled() {
        OreColumn c = new OreColumn(-64, 384);
        c.opaque.set(0, 384 * 256);
        c.update(8, -20, 8, 99, true, true);
        return c;
    }
    @Test void enclosedOreHiddenWithoutChangingOriginal() {
        OreColumn c = filled();
        assertEquals(1, c.visibleState(8, -20, 8, 99, 1));
        assertEquals(99, c.ores.get(c.index(8, -20, 8)));
    }
    @Test void miningRevealsOnce() {
        OreColumn c = filled();
        c.visibleState(8, -20, 8, 99, 1);
        c.update(7, -20, 8, 0, false, false);
        assertEquals(99, c.revealAround(7, -20, 8).get(c.index(8, -20, 8)));
        assertTrue(c.revealAround(7, -20, 8).isEmpty());
    }
    @Test void everyExposedFacePreventsMasking() {
        int[][] faces = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : faces) {
            OreColumn c = filled();
            c.update(8+d[0], -20+d[1], 8+d[2], 0, false, false);
            assertFalse(c.enclosed(8, -20, 8));
        }
    }
    @Test void boundariesAreVisible() {
        OreColumn c = filled();
        assertFalse(c.enclosed(0, -20, 8));
        assertFalse(c.enclosed(15, -20, 8));
        assertFalse(c.enclosed(8, -20, 0));
        assertFalse(c.enclosed(8, -20, 15));
        assertFalse(c.enclosed(8, -64, 8));
        assertFalse(c.enclosed(8, 319, 8));
    }
    @Test void removedOreIsNotResurrected() {
        OreColumn c = filled();
        c.visibleState(8, -20, 8, 99, 1);
        c.update(8, -20, 8, 0, false, false);
        c.update(7, -20, 8, 0, false, false);
        assertTrue(c.revealAround(7, -20, 8).isEmpty());
        assertTrue(c.hidden.isEmpty());
    }
    @Test void yCrossesSectionBoundary() {
        OreColumn c = filled();
        c.update(8, 0, 8, 99, true, true);
        assertEquals(1, c.visibleState(8, 0, 8, 99, 1));
        c.update(8, -1, 8, 0, false, false);
        assertEquals(99, c.revealAround(8, -1, 8).get(c.index(8, 0, 8)));
    }
    @Test void recipientsKeepIndependentDisguises() {
        OreColumn first = filled(), second = filled();
        first.update(7, -20, 8, 0, false, false);
        assertEquals(1, first.visibleState(8, -20, 8, 99, 1));
        assertEquals(1, second.visibleState(8, -20, 8, 99, 1));
        assertEquals(99, first.revealAround(7, -20, 8).get(first.index(8, -20, 8)));
        assertTrue(second.hidden.get(second.index(8, -20, 8)));
    }
    @Test void localDisguisePersistsOnlyWhileTheOreExists() {
        OreColumn c = filled();
        c.setDisguise(8, -20, 8, 123);
        assertEquals(123, c.disguise(8, -20, 8, 1));
        c.update(8, -20, 8, 0, false, false);
        assertEquals(1, c.disguise(8, -20, 8, 1));
    }
    @Test void netherNegativeAndUpperCoordinates() {
        OreColumn c = new OreColumn(0, 256);
        c.opaque.set(0, 256 * 256);
        c.update(4, 15, 4, 77, true, true);
        assertEquals(2, c.visibleState(4, 15, 4, 77, 2));
        c.update(4, 16, 4, 0, false, false);
        assertEquals(77, c.revealAround(4, 16, 4).get(c.index(4, 15, 4)));
        assertFalse(c.contains(4, -1, 4));
        assertFalse(c.contains(4, 256, 4));
    }
    @Test void noRowWrappingInExposure() {
        OreColumn c = filled();
        c.update(1, -20, 8, 99, true, true);
        c.visibleState(1, -20, 8, 99, 1);
        c.update(15, -20, 7, 0, false, false);
        assertTrue(c.revealAround(15, -20, 7).isEmpty());
        assertTrue(c.enclosed(1, -20, 8));
    }
    @Test void packetCoordinatesRoundTripIncludingNegativeSections() {
        var p = new com.github.retrooper.packetevents.util.Vector3i(-2, -4, 3);
        var encoded = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange.EncodedBlock(99, -17, -63, 50);
        var decoded = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange.EncodedBlock(p, encoded.toLong());
        assertEquals(-17, decoded.getX());
        assertEquals(-63, decoded.getY());
        assertEquals(50, decoded.getZ());
        assertEquals(99, decoded.getBlockId());
    }
}
