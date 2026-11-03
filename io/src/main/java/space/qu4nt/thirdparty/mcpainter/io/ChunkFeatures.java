package space.qu4nt.thirdparty.mcpainter.io;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.io.BlockCatalog.Block;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class ChunkFeatures {

    static final int TREE_CELL = 5;
    static final int TREE_MARGIN = 2;
    static final int MIN_TRUNK = 4;
    static final int TRUNK_VARIATION = 3;
    static final double GRASS_COVER = 0.7;
    static final double FLOWER_COVER = 0.3;
    static final int TALL_GRASS_ONE_IN = 6;

    private static final long ORE = 0x6F72_6573_0000_0001L;
    private static final long ORE_BLOCK = 0x6F72_6573_0000_0002L;
    private static final long TREE = 0x7472_6565_0000_0001L;
    private static final long LEAF = 0x7472_6565_0000_0002L;
    private static final long PLANT = 0x706C_616E_0000_0001L;
    private static final long FLOWER_KIND = 0x706C_616E_0000_0002L;
    private static final long FLOWER_SHAPE = 0x706C_616E_0000_0003L;
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    record Ore(Layer layer, Block stone, Block deepslate, int attempts, int minY, int maxY, double minRadius,
            double maxRadius) {

        int margin() {
            return (int) Math.ceil(maxRadius) + 1;
        }
    }

    static final List<Ore> ORES = List.of(
            new Ore(Layer.COAL_ORE, Block.COAL_ORE, Block.DEEPSLATE_COAL_ORE, 40, 0, 192, 1.0, 1.8),
            new Ore(Layer.IRON_ORE, Block.IRON_ORE, Block.DEEPSLATE_IRON_ORE, 50, -64, 72, 0.8, 1.4));

    interface BaseBlocks {
        BlockStateEntry blockAt(int x, int y, int z);
    }

    private final WorldModel model;
    private final BlockCatalog catalog;
    private final BaseBlocks base;
    private final int originX;
    private final int originZ;
    private final int minY;
    private final int maxY;
    private final long seed;
    private final Map<Integer, BlockStateEntry> blocks = new HashMap<>();

    private ChunkFeatures(WorldModel model, BlockCatalog catalog, BaseBlocks base, int chunkX, int chunkZ, int minY,
            int maxY) {
        this.model = model;
        this.catalog = catalog;
        this.base = base;
        this.originX = chunkX * 16;
        this.originZ = chunkZ * 16;
        this.minY = minY;
        this.maxY = maxY;
        this.seed = model.generation().seed();
    }

    static ChunkFeatures generate(WorldModel model, BlockCatalog catalog, BaseBlocks base, int chunkX, int chunkZ,
            int minY, int maxY) {
        ChunkFeatures features = new ChunkFeatures(model, catalog, base, chunkX, chunkZ, minY, maxY);
        for (Ore ore : ORES) {
            features.placeOre(ore);
        }
        if (catalog.has(Block.OAK_LOG) && catalog.has(Block.OAK_LEAVES)) {
            features.placeTrees();
        }
        features.placePlants();
        return features;
    }

    BlockStateEntry at(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    int size() {
        return blocks.size();
    }

    private int key(int x, int y, int z) {
        return ((y - minY) * 16 + (z - originZ)) * 16 + (x - originX);
    }

    private boolean inside(int x, int y, int z) {
        return x >= originX && x < originX + 16 && z >= originZ && z < originZ + 16 && y > minY && y < maxY;
    }

    private void placeOre(Ore ore) {
        BlockStateEntry stone = catalog.state(Block.STONE);
        BlockStateEntry deepslate = catalog.state(Block.DEEPSLATE);
        BlockStateEntry inStone = catalog.state(ore.stone());
        BlockStateEntry inDeepslate = catalog.state(ore.deepslate());
        int chunkX = Math.floorDiv(originX, 16);
        int chunkZ = Math.floorDiv(originZ, 16);
        int margin = ore.margin();
        for (int cz = chunkZ - 1; cz <= chunkZ + 1; cz++) {
            for (int cx = chunkX - 1; cx <= chunkX + 1; cx++) {
                for (int attempt = 0; attempt < ore.attempts(); attempt++) {
                    long h = GenHash.hash(seed, ORE + ore.layer().ordinal(), cx, attempt, cz);
                    int ax = cx * 16 + (int) ((h >>> 4) & 15);
                    int az = cz * 16 + (int) ((h >>> 8) & 15);
                    if (ax + margin < originX || ax - margin >= originX + 16
                            || az + margin < originZ || az - margin >= originZ + 16 || !model.contains(ax, az)) {
                        continue;
                    }
                    double chance = model.layer(ax, az, ore.layer()) / (double) Layer.MAX_VALUE;
                    long roll = GenHash.mix(h ^ ORE_BLOCK);
                    if (GenHash.unit(roll) >= chance) {
                        continue;
                    }
                    double t = (GenHash.unit(GenHash.mix(roll + 1)) + GenHash.unit(GenHash.mix(roll + 2))) / 2;
                    int ay = ore.minY() + (int) Math.floor(t * (ore.maxY() - ore.minY()));
                    double radius = ore.minRadius()
                            + GenHash.unit(GenHash.mix(roll + 3)) * (ore.maxRadius() - ore.minRadius());
                    int reach = (int) Math.ceil(radius);
                    for (int dy = -reach; dy <= reach; dy++) {
                        for (int dz = -reach; dz <= reach; dz++) {
                            for (int dx = -reach; dx <= reach; dx++) {
                                int x = ax + dx;
                                int y = ay + dy;
                                int z = az + dz;
                                if (!inside(x, y, z)) {
                                    continue;
                                }
                                double jitter = 0.7 + 0.6 * GenHash.unit(GenHash.hash(seed, ORE_BLOCK, x, y, z));
                                if (dx * dx + dy * dy + dz * dz > radius * radius * jitter) {
                                    continue;
                                }
                                int key = key(x, y, z);
                                if (blocks.containsKey(key)) {
                                    continue;
                                }
                                BlockStateEntry rock = base.blockAt(x, y, z);
                                if (rock.equals(stone)) {
                                    blocks.put(key, inStone);
                                } else if (rock.equals(deepslate)) {
                                    blocks.put(key, inDeepslate);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private int surface(int x, int z) {
        return Math.clamp((int) Math.floor(model.height(x, z)), minY + 1, maxY - 1);
    }

    private boolean dry(int x, int z, int surface) {
        return model.waterLevel(x, z) - 1 - surface <= 0;
    }

    private void placeTrees() {
        Map<Integer, Integer> leaves = new HashMap<>();
        Set<Integer> logs = new HashSet<>();
        Set<Integer> soil = new HashSet<>();
        int minCellX = Math.floorDiv(originX - TREE_MARGIN, TREE_CELL);
        int maxCellX = Math.floorDiv(originX + 15 + TREE_MARGIN, TREE_CELL);
        int minCellZ = Math.floorDiv(originZ - TREE_MARGIN, TREE_CELL);
        int maxCellZ = Math.floorDiv(originZ + 15 + TREE_MARGIN, TREE_CELL);
        for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                long h = GenHash.hash(seed, TREE, cellX, 0, cellZ);
                int ax = cellX * TREE_CELL + (int) Math.floorMod(h >>> 3, (long) TREE_CELL);
                int az = cellZ * TREE_CELL + (int) Math.floorMod(h >>> 17, (long) TREE_CELL);
                if (!model.contains(ax, az)) {
                    continue;
                }
                TerrainType terrain = model.terrain(ax, az);
                if (terrain != TerrainType.GRASS && terrain != TerrainType.DIRT) {
                    continue;
                }
                double chance = model.layer(ax, az, Layer.OAK_TREES) / (double) Layer.MAX_VALUE;
                long roll = GenHash.mix(h ^ LEAF);
                if (GenHash.unit(roll) >= chance) {
                    continue;
                }
                int ground = surface(ax, az);
                if (!dry(ax, az, ground)) {
                    continue;
                }
                int height = MIN_TRUNK + (int) Math.floorMod(roll >>> 7, (long) TRUNK_VARIATION);
                int top = ground + height;
                if (top + 2 >= maxY) {
                    continue;
                }
                if (inside(ax, ground, az)) {
                    soil.add(key(ax, ground, az));
                }
                for (int y = ground + 1; y <= top; y++) {
                    if (inside(ax, y, az)) {
                        logs.add(key(ax, y, az));
                    }
                }
                for (int y = top - 2; y <= top + 1; y++) {
                    int radius = y >= top ? 1 : 2;
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                            if (corner && (y == top + 1
                                    || GenHash.unit(GenHash.hash(seed, LEAF, ax + dx, y, az + dz)) < 0.5)) {
                                continue;
                            }
                            int x = ax + dx;
                            int z = az + dz;
                            if (!inside(x, y, z)) {
                                continue;
                            }
                            int distance = Math.abs(dx) + Math.abs(dz) + Math.max(0, y - top);
                            leaves.merge(key(x, y, z), Math.max(1, distance), Math::min);
                        }
                    }
                }
            }
        }
        BlockStateEntry air = catalog.state(Block.AIR);
        BlockStateEntry grass = catalog.state(Block.GRASS_BLOCK);
        BlockStateEntry dirt = catalog.state(Block.DIRT);
        BlockStateEntry log = catalog.state(Block.OAK_LOG);
        for (Map.Entry<Integer, Integer> leaf : leaves.entrySet()) {
            int key = leaf.getKey();
            if (!logs.contains(key) && base(key).equals(air)) {
                blocks.put(key, catalog.state(Block.OAK_LEAVES, "distance", Integer.toString(leaf.getValue())));
            }
        }
        for (int key : logs) {
            blocks.put(key, log);
        }
        for (int key : soil) {
            if (base(key).equals(grass)) {
                blocks.put(key, dirt);
            }
        }
    }

    private BlockStateEntry base(int key) {
        return base.blockAt(originX + (key & 15), (key >> 8) + minY, originZ + ((key >> 4) & 15));
    }

    private void placePlants() {
        BlockStateEntry air = catalog.state(Block.AIR);
        BlockStateEntry grassBlock = catalog.state(Block.GRASS_BLOCK);
        boolean shortGrass = catalog.has(Block.SHORT_GRASS);
        List<Layer> flowers = Layer.flowers().stream().filter(f -> catalog.has(FlowerBlocks.block(f))).toList();
        int[] values = new int[flowers.size()];
        for (int z = originZ; z < originZ + 16; z++) {
            for (int x = originX; x < originX + 16; x++) {
                if (!model.contains(x, z)) {
                    continue;
                }
                int strongest = 0;
                int total = 0;
                for (int i = 0; i < values.length; i++) {
                    values[i] = model.layer(x, z, flowers.get(i));
                    strongest = Math.max(strongest, values[i]);
                    total += values[i];
                }
                int grass = model.layer(x, z, Layer.GRASS);
                if (strongest == 0 && grass == 0) {
                    continue;
                }
                int ground = surface(x, z);
                int y = ground + 1;
                if (y + 1 >= maxY || !dry(x, z, ground) || !base.blockAt(x, ground, z).equals(grassBlock)
                        || blocks.containsKey(key(x, ground, z)) || blocks.containsKey(key(x, y, z))
                        || !base.blockAt(x, y, z).equals(air)) {
                    continue;
                }
                long h = GenHash.hash(seed, PLANT, x, y, z);
                if (GenHash.unit(h) < strongest / (double) Layer.MAX_VALUE * FLOWER_COVER) {
                    Layer flower = pick(flowers, values, total, GenHash.mix(h ^ FLOWER_KIND));
                    placeFlower(flower, x, y, z, GenHash.mix(h ^ FLOWER_SHAPE), air);
                    continue;
                }
                long g = GenHash.mix(h);
                if (GenHash.unit(g) >= grass / (double) Layer.MAX_VALUE * GRASS_COVER) {
                    continue;
                }
                if (Math.floorMod(g >>> 9, (long) TALL_GRASS_ONE_IN) == 0 && roomAbove(x, y, z, air)) {
                    blocks.put(key(x, y, z), catalog.state(Block.TALL_GRASS));
                    blocks.put(key(x, y + 1, z), catalog.state(Block.TALL_GRASS, "half", "upper"));
                } else if (shortGrass) {
                    blocks.put(key(x, y, z), catalog.state(Block.SHORT_GRASS));
                }
            }
        }
    }

    private static Layer pick(List<Layer> flowers, int[] values, int total, long roll) {
        double target = GenHash.unit(roll) * total;
        Layer chosen = null;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) {
                continue;
            }
            chosen = flowers.get(i);
            target -= values[i];
            if (target < 0) {
                break;
            }
        }
        return chosen;
    }

    private boolean roomAbove(int x, int y, int z, BlockStateEntry air) {
        return base.blockAt(x, y + 1, z).equals(air) && !blocks.containsKey(key(x, y + 1, z));
    }

    private void placeFlower(Layer flower, int x, int y, int z, long roll, BlockStateEntry air) {
        Block block = FlowerBlocks.block(flower);
        if (FlowerBlocks.tall(flower)) {
            if (roomAbove(x, y, z, air)) {
                blocks.put(key(x, y, z), catalog.state(block));
                blocks.put(key(x, y + 1, z), catalog.state(block, "half", "upper"));
            }
        } else if (FlowerBlocks.bed(flower)) {
            blocks.put(key(x, y, z), catalog.state(block, Map.of("facing", FACINGS.get((int) (roll & 3)),
                    "flower_amount", Integer.toString(1 + (int) ((roll >>> 2) & 3)))));
        } else {
            blocks.put(key(x, y, z), catalog.state(block));
        }
    }
}
