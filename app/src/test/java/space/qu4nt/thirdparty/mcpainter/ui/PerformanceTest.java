package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.BackingStoreException;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;

class PerformanceTest {

    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    void recommendationsFollowTheHeapAndCores() {
        Performance.Environment big = new Performance.Environment(18, 16 * GIB, 64 * GIB);
        long available = 16 * GIB - Performance.RESERVE_BYTES;
        assertEquals(available, big.availableBytes());
        assertEquals((int) (available / Performance.MIB), big.maxAreaMiB());
        assertEquals((int) (available * 0.8 / Performance.MIB), big.recommendedAreaMiB());
        assertEquals(17, big.recommendedThreads());
        assertEquals(18, big.maxThreads());

        Performance.Environment tiny = new Performance.Environment(1, 512 * Performance.MIB, -1);
        assertEquals(Performance.MIN_AREA_MIB, tiny.maxAreaMiB());
        assertEquals(Performance.MIN_AREA_MIB, tiny.recommendedAreaMiB());
        assertEquals(1, tiny.recommendedThreads());

        assertEquals(1600, Performance.areaTiles(250));
        assertEquals(40 * 128, Performance.areaSideBlocks(1600));
        assertTrue(Performance.fits(new TileRect(0, 0, 39, 39), 250));
        assertFalse(Performance.fits(new TileRect(0, 0, 40, 39), 250));
    }

    @Test
    void settingsUseRecommendationsUntilChanged() throws BackingStoreException {
        Performance.Environment environment = new Performance.Environment(8, 8 * GIB, 32 * GIB);
        MemoryPreferences preferences = new MemoryPreferences();
        Settings settings = new Settings(preferences);
        assertEquals(environment.recommendedAreaMiB(), settings.areaMemoryMiB(environment));
        assertEquals(7, settings.threads(environment));
        assertFalse(settings.customPerformance());
        settings.setPerformance(1024, 3);
        assertEquals(1024, settings.areaMemoryMiB(environment));
        assertEquals(3, settings.threads(environment));
        assertTrue(settings.customPerformance());
        preferences.put(Settings.AREA_MEMORY, "999999999");
        preferences.put(Settings.THREADS, "64");
        assertEquals(environment.maxAreaMiB(), settings.areaMemoryMiB(environment));
        assertEquals(8, settings.threads(environment));
        preferences.put(Settings.AREA_MEMORY, "lots");
        assertEquals(environment.recommendedAreaMiB(), settings.areaMemoryMiB(environment));
        assertThrows(IllegalArgumentException.class, () -> settings.setPerformance(10, 2));
        settings.resetPerformance();
        assertFalse(settings.customPerformance());
    }

    @Test
    void durationsReadNaturally() {
        Messages ko = Messages.load(Language.KOREAN);
        assertEquals("1초", BackgroundTask.duration(ko, 0));
        assertEquals("59초", BackgroundTask.duration(ko, 59));
        assertEquals("2분", BackgroundTask.duration(ko, 61));
        assertEquals("18시간 27분", BackgroundTask.duration(ko, 66_400));
    }
}
