package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkSection;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionFile;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionPos;
import space.qu4nt.thirdparty.mcpainter.anvil.world.Dimension;
import space.qu4nt.thirdparty.mcpainter.anvil.world.MinecraftWorld;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;

final class Prototypes {

    private Prototypes() {
    }

    static Optional<ChunkData> find(MinecraftWorld world, Dimension dimension) throws IOException {
        for (RegionPos pos : world.listRegions(dimension)) {
            try (RegionFile region = RegionFile.open(world.regionPath(dimension, pos))) {
                for (int z = 0; z < RegionPos.CHUNKS_PER_SIDE; z++) {
                    for (int x = 0; x < RegionPos.CHUNKS_PER_SIDE; x++) {
                        Optional<ChunkData> chunk = fullChunk(region, x, z, world.level().dataVersion());
                        if (chunk.isPresent()) {
                            return chunk;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    static Optional<ChunkData> fullChunk(RegionFile region, int x, int z, int dataVersion) throws IOException {
        if (region.state(x, z) != RegionFile.ChunkState.PRESENT) {
            return Optional.empty();
        }
        Optional<RootTag> root = region.readChunk(x, z);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        ChunkData data = ChunkData.parse(root.get());
        boolean full = data.status().equals("full") || data.status().equals("minecraft:full");
        return full && data.dataVersion() == dataVersion ? Optional.of(data) : Optional.empty();
    }

    static int minY(ChunkData chunk) {
        return chunk.sections().stream().filter(s -> s.blocks().isPresent())
                .mapToInt(s -> s.y() * 16).min().orElseThrow();
    }

    static int maxY(ChunkData chunk) {
        return chunk.sections().stream().filter(s -> s.blocks().isPresent())
                .mapToInt(ChunkSection::y).max().orElseThrow() * 16 + 16;
    }
}
