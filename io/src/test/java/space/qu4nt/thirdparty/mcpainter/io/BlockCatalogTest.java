package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.io.BlockCatalog.Block;

class BlockCatalogTest {

    private static List<String> keys(BlockStateEntry state) {
        return List.copyOf(state.properties().orElseThrow().keySet());
    }

    @Test
    void leavesFollowTheVersionPropertySets() {
        BlockStateEntry old = new BlockCatalog(DataVersions.V1_18).state(Block.OAK_LEAVES);
        assertEquals(List.of("distance", "persistent"), keys(old));
        BlockStateEntry current = new BlockCatalog(4671).state(Block.OAK_LEAVES);
        assertEquals(List.of("waterlogged", "distance", "persistent"), keys(current));
        assertFalse(new BlockCatalog(DataVersions.V1_18_2 + 1).has(Block.OAK_LEAVES));
        assertTrue(new BlockCatalog(DataVersions.V1_19).has(Block.OAK_LEAVES));
    }

    @Test
    void shortGrassIsRenamedAndUnknownSnapshotsAreSkipped() {
        assertEquals("minecraft:grass", new BlockCatalog(DataVersions.V1_18).state(Block.SHORT_GRASS).id().toString());
        assertEquals("minecraft:grass", new BlockCatalog(DataVersions.V1_20_2).state(Block.SHORT_GRASS).id().toString());
        BlockCatalog snapshot = new BlockCatalog(DataVersions.V1_20_2 + 50);
        assertFalse(snapshot.has(Block.SHORT_GRASS));
        assertFalse(snapshot.available().contains(Block.SHORT_GRASS));
        assertThrows(IllegalArgumentException.class, () -> snapshot.state(Block.SHORT_GRASS));
        assertEquals("minecraft:short_grass",
                new BlockCatalog(DataVersions.V26_3).state(Block.SHORT_GRASS).id().toString());
    }

    @Test
    void variantsReplaceOnePropertyAndKnowTheirTraits() {
        BlockCatalog catalog = new BlockCatalog(DataVersions.V26_3);
        BlockStateEntry far = catalog.state(Block.OAK_LEAVES, "distance", "4");
        assertEquals(Map.of("waterlogged", "false", "distance", "4", "persistent", "false"),
                Map.copyOf(far.properties().orElseThrow()));
        assertEquals(List.of("waterlogged", "distance", "persistent"), keys(far));
        assertSame(far, catalog.state(Block.OAK_LEAVES, "distance", "4"));
        assertEquals(BlockTraits.LEAVES, catalog.traits(far));
        BlockStateEntry upper = catalog.state(Block.TALL_GRASS, "half", "upper");
        assertEquals(BlockTraits.PLANT, catalog.traits(upper));
        assertThrows(IllegalArgumentException.class, () -> catalog.state(Block.STONE, "axis", "y"));
        BlockStateEntry petals = catalog.state(Block.PINK_PETALS, Map.of("facing", "west", "flower_amount", "3"));
        assertEquals(Map.of("facing", "west", "flower_amount", "3"), Map.copyOf(petals.properties().orElseThrow()));
        assertSame(petals, catalog.state(Block.PINK_PETALS, Map.of("flower_amount", "3", "facing", "west")));
        assertSame(catalog.state(Block.POPPY), catalog.state(Block.POPPY, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> catalog.state(Block.POPPY, Map.of("half", "upper")));
        assertTrue(catalog.traits(catalog.state(Block.OAK_LEAVES)).countsFor("MOTION_BLOCKING"));
        assertFalse(catalog.traits(catalog.state(Block.OAK_LEAVES)).countsFor("MOTION_BLOCKING_NO_LEAVES"));
        assertFalse(catalog.traits(upper).countsFor("MOTION_BLOCKING"));
        assertTrue(catalog.traits(upper).countsFor("WORLD_SURFACE"));
        assertEquals(Map.of("axis", "y"), Map.copyOf(catalog.state(Block.DEEPSLATE).properties().orElseThrow()));
    }

    @Test
    void flowersAppearFromTheirFirstRelease() {
        assertEquals(DataVersions.MIN_SUPPORTED, Block.PEONY.since());
        assertEquals(DataVersions.V1_20, Block.TORCHFLOWER.since());
        assertEquals(DataVersions.V1_21_5, Block.WILDFLOWERS.since());
        assertFalse(new BlockCatalog(DataVersions.V1_19).has(Block.PINK_PETALS));
        assertTrue(new BlockCatalog(DataVersions.V1_20).has(Block.PITCHER_PLANT));
        assertFalse(new BlockCatalog(DataVersions.V1_21_4 - 1).has(Block.CLOSED_EYEBLOSSOM));
        assertTrue(new BlockCatalog(DataVersions.V1_21_4).has(Block.CLOSED_EYEBLOSSOM));
        assertEquals(Map.of("half", "lower"),
                Map.copyOf(new BlockCatalog(DataVersions.V1_18).state(Block.SUNFLOWER).properties().orElseThrow()));
        for (space.qu4nt.thirdparty.mcpainter.world.Layer flower : space.qu4nt.thirdparty.mcpainter.world.Layer.flowers()) {
            assertTrue(new BlockCatalog(DataVersions.MAX_SUPPORTED).has(FlowerBlocks.block(flower)), flower.id());
        }
    }
}
