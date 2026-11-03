package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Optional;

public record TileRect(int minX, int minZ, int maxX, int maxZ) {

    public static final int WORLD_BLOCK_LIMIT = 29_999_984;
    public static final TileRect WORLD = ofBlocks(-WORLD_BLOCK_LIMIT, -WORLD_BLOCK_LIMIT, WORLD_BLOCK_LIMIT - 1,
            WORLD_BLOCK_LIMIT - 1);

    public TileRect {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("empty tile rectangle");
        }
    }

    public static TileRect ofBlocks(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        TilePos min = TilePos.ofBlock(Math.min(minBlockX, maxBlockX), Math.min(minBlockZ, maxBlockZ));
        TilePos max = TilePos.ofBlock(Math.max(minBlockX, maxBlockX), Math.max(minBlockZ, maxBlockZ));
        return new TileRect(min.x(), min.z(), max.x(), max.z());
    }

    public static TileRect centered(long widthTiles, long depthTiles) {
        if (widthTiles < 1 || depthTiles < 1 || widthTiles > WORLD.width() || depthTiles > WORLD.depth()) {
            throw new IllegalArgumentException("size outside 1.." + WORLD.width() + " tiles");
        }
        int minX = (int) -(widthTiles / 2);
        int minZ = (int) -(depthTiles / 2);
        int maxX = (int) (minX + widthTiles - 1);
        int maxZ = (int) (minZ + depthTiles - 1);
        int shiftX = Math.max(0, maxX - WORLD.maxX);
        int shiftZ = Math.max(0, maxZ - WORLD.maxZ);
        return new TileRect(minX - shiftX, minZ - shiftZ, maxX - shiftX, maxZ - shiftZ);
    }

    public static TileRect centeredBlocks(long widthBlocks, long depthBlocks) {
        return centered(Math.ceilDiv(widthBlocks, Tile.SIZE), Math.ceilDiv(depthBlocks, Tile.SIZE));
    }

    public static TileRect of(TilePos a, TilePos b) {
        return new TileRect(Math.min(a.x(), b.x()), Math.min(a.z(), b.z()), Math.max(a.x(), b.x()),
                Math.max(a.z(), b.z()));
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public long count() {
        return (long) width() * depth();
    }

    public int minBlockX() {
        return minX << Tile.SHIFT;
    }

    public int minBlockZ() {
        return minZ << Tile.SHIFT;
    }

    public int maxBlockX() {
        return (maxX << Tile.SHIFT) + Tile.SIZE - 1;
    }

    public int maxBlockZ() {
        return (maxZ << Tile.SHIFT) + Tile.SIZE - 1;
    }

    public boolean contains(TilePos pos) {
        return pos.x() >= minX && pos.x() <= maxX && pos.z() >= minZ && pos.z() <= maxZ;
    }

    public boolean contains(TileRect other) {
        return other.minX >= minX && other.maxX <= maxX && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public TileRect expand(int tiles) {
        return new TileRect(saturate((long) minX - tiles), saturate((long) minZ - tiles),
                saturate((long) maxX + tiles), saturate((long) maxZ + tiles));
    }

    public Optional<TileRect> intersect(TileRect other) {
        int x0 = Math.max(minX, other.minX);
        int z0 = Math.max(minZ, other.minZ);
        int x1 = Math.min(maxX, other.maxX);
        int z1 = Math.min(maxZ, other.maxZ);
        return x0 > x1 || z0 > z1 ? Optional.empty() : Optional.of(new TileRect(x0, z0, x1, z1));
    }

    public TileRect clampInto(TileRect outer) {
        int w = Math.min(width(), outer.width());
        int d = Math.min(depth(), outer.depth());
        int x0 = Math.clamp(minX, outer.minX, outer.maxX - w + 1);
        int z0 = Math.clamp(minZ, outer.minZ, outer.maxZ - d + 1);
        return new TileRect(x0, z0, x0 + w - 1, z0 + d - 1);
    }

    private static int saturate(long value) {
        return Math.clamp(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
