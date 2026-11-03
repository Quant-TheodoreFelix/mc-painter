package space.qu4nt.thirdparty.mcpainter.io;

import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.io.BlockCatalog.Block;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class SurfacePreview {

    public static final int DATA_VERSION = DataVersions.MAX_SUPPORTED;
    static final int CHUNKS = Tile.SIZE / 16;

    public static final class Surface {

        private final TilePos pos;
        private final Block[] blocks;
        private final int[] heights;
        private final int[] waterDepths;

        private Surface(TilePos pos, Block[] blocks, int[] heights, int[] waterDepths) {
            this.pos = pos;
            this.blocks = blocks;
            this.heights = heights;
            this.waterDepths = waterDepths;
        }

        public TilePos pos() {
            return pos;
        }

        public boolean known(int localX, int localZ) {
            return blocks[index(localX, localZ)] != null;
        }

        public Block block(int localX, int localZ) {
            Block block = blocks[index(localX, localZ)];
            if (block == null) {
                throw new IllegalStateException("no surface north of " + pos);
            }
            return block;
        }

        public int height(int localX, int localZ) {
            return heights[index(localX, localZ)];
        }

        public int waterDepth(int localX, int localZ) {
            return waterDepths[index(localX, localZ)];
        }

        private static int index(int localX, int localZ) {
            Objects.checkIndex(localX, Tile.SIZE);
            Objects.checkIndex(localZ + 1, Tile.SIZE + 1);
            return (localZ + 1) * Tile.SIZE + localX;
        }
    }

    private SurfacePreview() {
    }

    public static Surface render(WorldModel model, TilePos pos) {
        Objects.requireNonNull(model, "model");
        if (model.tile(Objects.requireNonNull(pos, "pos")).isEmpty()) {
            throw new IllegalArgumentException("no tile at " + pos);
        }
        BlockCatalog catalog = new BlockCatalog(DATA_VERSION);
        int area = Tile.SIZE * (Tile.SIZE + 1);
        Block[] blocks = new Block[area];
        int[] heights = new int[area];
        int[] depths = new int[area];
        int firstChunkX = pos.x() * CHUNKS;
        int firstChunkZ = pos.z() * CHUNKS;
        boolean north = model.tile(new TilePos(pos.x(), pos.z() - 1)).isPresent();
        int[] topY = new int[256];
        BlockStateEntry[] topState = new BlockStateEntry[256];
        for (int cz = north ? firstChunkZ - 1 : firstChunkZ; cz < firstChunkZ + CHUNKS; cz++) {
            for (int cx = firstChunkX; cx < firstChunkX + CHUNKS; cx++) {
                WorldExporter.ChunkColumns columns = new WorldExporter.ChunkColumns(model, catalog, cx, cz,
                        model.minY(), model.maxY());
                ChunkFeatures features = ChunkFeatures.generate(model, catalog, columns::blockAt, cx, cz,
                        model.minY(), model.maxY(), exposedRock(catalog, columns, cx, cz));
                features.tops(topY, topState);
                for (int lz = cz < firstChunkZ ? 15 : 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int column = lz * 16 + lx;
                        int x = cx * 16 + lx;
                        int z = cz * 16 + lz;
                        int surface = columns.surface(column);
                        int water = columns.water(column);
                        Block block;
                        int y;
                        int depth = 0;
                        if (surface + 1 < water) {
                            y = water - 1;
                            depth = y - surface;
                            block = Block.WATER;
                        } else {
                            y = surface;
                            block = lookup(catalog, columns.blockAt(x, surface, z));
                        }
                        if (topState[column] != null && topY[column] >= y) {
                            y = topY[column];
                            depth = 0;
                            block = lookup(catalog, topState[column]);
                        }
                        int index = Surface.index(x - pos.minBlockX(), z - pos.minBlockZ());
                        blocks[index] = block;
                        heights[index] = y;
                        depths[index] = depth;
                    }
                }
            }
        }
        return new Surface(pos, blocks, heights, depths);
    }

    private static boolean exposedRock(BlockCatalog catalog, WorldExporter.ChunkColumns columns, int cx, int cz) {
        BlockStateEntry stone = catalog.state(Block.STONE);
        BlockStateEntry deepslate = catalog.state(Block.DEEPSLATE);
        for (int column = 0; column < 256; column++) {
            int surface = columns.surface(column);
            if (surface + 1 >= columns.water(column)) {
                BlockStateEntry top = columns.blockAt(cx * 16 + (column & 15), surface, cz * 16 + (column >> 4));
                if (top.equals(stone) || top.equals(deepslate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Block lookup(BlockCatalog catalog, BlockStateEntry state) {
        return catalog.block(state).orElseThrow(() -> new IllegalStateException("block outside catalog " + state.id()));
    }
}
