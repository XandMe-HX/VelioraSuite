package id.velioragardens.veliorasuite.module.security.xray;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CaveSpaceTest {
    @Test void enclosedOreIsNotCave() { assertFalse(CaveSpace.near((x,y,z)->false)); }
    @Test void twoExposedFacesAreNotCave() {
        assertFalse(CaveSpace.near((x,y,z)-> (x==1 && y==0 && z==0) || (x==0 && y==1 && z==0)));
    }
    @Test void narrowMiningTunnelIsNotLargeCave() {
        assertFalse(CaveSpace.near((x,y,z)-> z==0 && (y==0 || y==1)));
    }
    @Test void connectedLargeRoomIsCave() {
        assertTrue(CaveSpace.near((x,y,z)->x>=1));
    }
    @Test void roomBehindSolidWallDoesNotMakeOreExposed() {
        assertFalse(CaveSpace.near((x,y,z)->x>=2));
    }
}
