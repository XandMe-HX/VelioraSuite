package id.velioragardens.veliorasuite.module.kits;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class KitsDefaultsTest {
    @Test void approvedPricesAndFood() {
        var stream=getClass().getClassLoader().getResourceAsStream("modules/kits.yml");
        assertNotNull(stream);
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
        for(int i=1;i<=5;i++) {
            assertEquals(5000+(i-1)*2500,config.getInt("kits.premium_"+i+".buy.price"));
            assertEquals(i,config.getInt("kits.premium_"+i+".premium-level"));
        }
        var food=config.getMapList("kits.food.items");
        assertEquals(2,food.size());
        assertEquals("COOKED_CHICKEN",food.get(0).get("material"));
        assertEquals(64,food.get(0).get("amount"));
        assertEquals("CARROT",food.get(1).get("material"));
        assertEquals(64,food.get(1).get("amount"));
    }
    @Test void racePriceDefault() {
        var stream=getClass().getClassLoader().getResourceAsStream("modules/race.yml");
        assertNotNull(stream);
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
        assertEquals(20000,config.getInt("change.cost"));
    }
}
