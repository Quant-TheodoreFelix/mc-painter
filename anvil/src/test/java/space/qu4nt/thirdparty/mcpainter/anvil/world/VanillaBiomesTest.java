package space.qu4nt.thirdparty.mcpainter.anvil.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.world.VanillaBiomes.Availability;

class VanillaBiomesTest {

    @Test
    void tableMatchesTheReleasedGames() {
        assertEquals(67, VanillaBiomes.all().size());
        assertEquals(56, VanillaBiomes.overworld().size());
        Set<String> ids = new HashSet<>();
        for (VanillaBiomes.Biome biome : VanillaBiomes.all()) {
            assertTrue(ids.add(biome.id()), biome.id());
            assertTrue(biome.path().matches("[a-z_]+"), biome.path());
            assertTrue(DataVersions.isSupported(biome.sinceDataVersion()), biome.id());
        }
        assertEquals(61, VanillaBiomes.all().stream().filter(b -> b.sinceDataVersion() == DataVersions.V1_18).count());
    }

    @Test
    void reportsAvailabilityPerVersion() {
        assertEquals(Availability.AVAILABLE, VanillaBiomes.availability("minecraft:plains", DataVersions.V1_18));
        assertEquals(Availability.TOO_NEW, VanillaBiomes.availability("minecraft:cherry_grove", DataVersions.V1_18));
        assertEquals(Availability.TOO_NEW, VanillaBiomes.availability("minecraft:cherry_grove", DataVersions.V1_20 - 1));
        assertEquals(Availability.AVAILABLE, VanillaBiomes.availability("minecraft:cherry_grove", DataVersions.V1_20));
        assertEquals(Availability.AVAILABLE, VanillaBiomes.availability("minecraft:deep_dark", DataVersions.V1_19));
        assertEquals(Availability.TOO_NEW, VanillaBiomes.availability("minecraft:pale_garden", 4671 - 500));
        assertEquals(Availability.AVAILABLE, VanillaBiomes.availability("minecraft:pale_garden", 4671));
        assertEquals(Availability.TOO_NEW, VanillaBiomes.availability("minecraft:sulfur_caves", 4790));
        assertEquals(Availability.AVAILABLE, VanillaBiomes.availability("minecraft:sulfur_caves", DataVersions.V26_2));
        assertEquals(Availability.TOO_NEW, VanillaBiomes.availability("minecraft:dappled_forest", DataVersions.V26_2));
        assertEquals(Availability.AVAILABLE, VanillaBiomes.availability("minecraft:dappled_forest", DataVersions.V26_3));
        assertEquals(Availability.UNKNOWN, VanillaBiomes.availability("minecraft:plainz", DataVersions.V26_3));
        assertEquals(Availability.CUSTOM, VanillaBiomes.availability("mymod:glow/caves", DataVersions.V26_3));
        assertEquals(Availability.CUSTOM, VanillaBiomes.availability(null, DataVersions.V26_3));
        assertTrue(VanillaBiomes.find("minecraft:the_void").isPresent());
        assertTrue(VanillaBiomes.find("plains").isEmpty());
    }
}
