package id.velioragardens.veliorasuite.module.kits;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class KitMenuLayoutTest {
    @Test void defaultKitsStayCentered() {
        assertEquals(20, KitMenuLayout.slot("starter", false, 0, Set.of()));
        assertEquals(22, KitMenuLayout.slot("build", false, 0, Set.of()));
        assertEquals(24, KitMenuLayout.slot("food", false, 0, Set.of()));
    }
    @Test void customKitsCannotReplacePremiumOrNavigation() {
        for (int requested : new int[]{4,20,22,24,31,45,49,53}) {
            int actual=KitMenuLayout.slot("custom",false,requested,Set.of());
            assertFalse(Set.of(4,20,22,24,31,45,49,53).contains(actual));
        }
    }
    @Test void premiumFiveAcrossAndNoDuplicateSlots() {
        var used=new HashSet<Integer>();
        for(int i=0;i<5;i++) {
            int slot=KitMenuLayout.slot("premium_"+i,true,20+i,used);
            assertEquals(20+i,slot);
            assertTrue(used.add(slot));
        }
        assertFalse(used.contains(KitMenuLayout.slot("extra",true,20,used)));
    }
}
