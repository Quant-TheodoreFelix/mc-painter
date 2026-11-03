package space.qu4nt.thirdparty.mcpainter.ui;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class Performance {

    static final long MIB = 1024L * 1024;
    static final int MIN_AREA_MIB = 256;
    static final long OVERHEAD_BYTES = 256 * MIB;
    static final long RESERVE_BYTES = WorldModel.UNDO_BYTES + TileImageCache.DEFAULT_MAX_BYTES
            + PreviewCache.DEFAULT_MAX_BYTES + OVERHEAD_BYTES;
    static final long TILE_BYTES = Tile.ESTIMATED_BYTES + Tile.AREA;
    static final double RECOMMENDED_SHARE = 0.8;

    record Environment(int cores, long maxHeap, long physical) {

        static Environment current() {
            long physical = -1;
            OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
                physical = os.getTotalMemorySize();
            }
            return new Environment(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(),
                    physical);
        }

        long availableBytes() {
            return Math.max(MIN_AREA_MIB * MIB, maxHeap - RESERVE_BYTES);
        }

        int maxAreaMiB() {
            return (int) Math.min(Integer.MAX_VALUE, availableBytes() / MIB);
        }

        int recommendedAreaMiB() {
            return Math.max(MIN_AREA_MIB, (int) Math.min(Integer.MAX_VALUE,
                    (long) (availableBytes() * RECOMMENDED_SHARE / MIB)));
        }

        int maxThreads() {
            return Math.max(1, cores);
        }

        int recommendedThreads() {
            return Math.max(1, cores - 1);
        }
    }

    private Performance() {
    }

    static int areaTiles(int areaMiB) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, areaMiB * MIB / TILE_BYTES));
    }

    static long areaBytes(long tiles) {
        return tiles * TILE_BYTES;
    }

    static int areaSideBlocks(int tiles) {
        return (int) Math.floor(Math.sqrt(tiles)) * Tile.SIZE;
    }

    static boolean fits(TileRect loaded, int areaMiB) {
        return loaded.count() <= areaTiles(areaMiB);
    }
}
