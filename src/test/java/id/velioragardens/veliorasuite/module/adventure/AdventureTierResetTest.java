package id.velioragardens.veliorasuite.module.adventure;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdventureTierResetTest {
    @Test void resetsTierButPreservesProfessionAndCompletedHistory() {
        AdventureDataManager data = new AdventureDataManager(null);
        UUID id = UUID.randomUUID();
        var profile = data.player(id, "Member");
        profile.setExp(50000);
        profile.customRank("SSS");
        profile.complete();
        profile.addProfessionExp(AdventureProfession.FISHER, 75);
        assertTrue(data.resetTier(id));
        assertEquals(0, profile.exp());
        assertEquals("", profile.customRank());
        assertEquals(1, profile.completed());
        assertEquals(75, profile.professionExp(AdventureProfession.FISHER));
        assertFalse(data.resetTier(id));
    }

    @Test void unknownPlayerDoesNotCreateOrResetAProfile() {
        AdventureDataManager data = new AdventureDataManager(null);
        assertFalse(data.resetTier(UUID.randomUUID()));
        assertTrue(data.playerIds().isEmpty());
    }

    @Test void customRankWithZeroExpStillResets() {
        AdventureDataManager data = new AdventureDataManager(null);
        UUID id = UUID.randomUUID();
        data.player(id, "Member").customRank("S");
        assertTrue(data.resetTier(id));
        assertEquals("", data.player(id, "Member").customRank());
    }
}
