package id.velioragardens.veliorasuite.module.fishing;
import id.velioragardens.veliorasuite.module.fishing.model.FishRarity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PatientAnglerHookTest {
    @Test void waveTwoPreservesTierGate() { assertEquals(0,FishGenerator.waveTwoWeight(FishRarity.SECRET,0,1,1)); }
    @Test void waveTwoCapsSecretBoost() { assertEquals(1.09,FishGenerator.waveTwoWeight(FishRarity.SECRET,1,1,1),1e-9); }
    @Test void waveTwoDoesNotBoostCommonOrGrantSecretBonusToEpic() {
        assertEquals(50,FishGenerator.waveTwoWeight(FishRarity.COMMON,50,.06,.03));
        assertEquals(10.6,FishGenerator.waveTwoWeight(FishRarity.EPIC,10,.06,.03),1e-9);
    }
    @Test void waveTwoRejectsInvalidBonuses() { assertEquals(1,FishGenerator.waveTwoWeight(FishRarity.SECRET,1,Double.NaN,Double.POSITIVE_INFINITY)); }
    @Test void gatesRemainZero() { assertEquals(0, FishGenerator.patientWeight(FishRarity.SECRET, 0, .1)); }
    @Test void commonUnchanged() { assertEquals(50, FishGenerator.patientWeight(FishRarity.COMMON, 50, .1)); }
    @Test void rareRelativeBonusCapped() { assertEquals(11, FishGenerator.patientWeight(FishRarity.EPIC, 10, 9), 1e-9); }
    @Test void secretGetsQuarterBonus() { assertEquals(1.025, FishGenerator.patientWeight(FishRarity.SECRET, 1, .1), 1e-9); }
    @Test void invalidBonusIgnored() { assertEquals(10, FishGenerator.patientWeight(FishRarity.EPIC, 10, Double.NaN)); }
}
