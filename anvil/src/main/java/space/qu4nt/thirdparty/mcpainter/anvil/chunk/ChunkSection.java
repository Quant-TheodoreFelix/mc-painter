package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.util.Objects;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;

public record ChunkSection(
        int y,
        Optional<Paletted<BlockStateEntry>> blocks,
        Optional<Paletted<ResourceLocation>> biomes,
        CompoundTag source) {

    public static final int BLOCKS = PaletteCodec.BLOCK_COUNT;
    public static final int BIOMES = PaletteCodec.BIOME_COUNT;

    static final String BLOCK_STATES = "block_states";
    static final String BIOME_DATA = "biomes";
    static final String Y = "Y";

    public ChunkSection {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(biomes, "biomes");
        Objects.requireNonNull(source, "source");
        if (y < Byte.MIN_VALUE || y > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("section y out of byte range: " + y);
        }
        blocks.ifPresent(b -> requireSize(b, BLOCKS, "blocks"));
        biomes.ifPresent(b -> requireSize(b, BIOMES, "biomes"));
    }

    public static int blockIndex(int x, int y, int z) {
        Objects.checkIndex(x, 16);
        Objects.checkIndex(y, 16);
        Objects.checkIndex(z, 16);
        return (y << 8) | (z << 4) | x;
    }

    public static int biomeIndex(int cellX, int cellY, int cellZ) {
        Objects.checkIndex(cellX, 4);
        Objects.checkIndex(cellY, 4);
        Objects.checkIndex(cellZ, 4);
        return (cellY << 4) | (cellZ << 2) | cellX;
    }

    static ChunkSection parse(CompoundTag tag, int dataVersion) throws ChunkFormatException {
        int y = tag.get(Y, ByteTag.class)
                .orElseThrow(() -> new ChunkFormatException("section has no Y byte"))
                .value();
        for (String key : new String[] {BLOCK_STATES, BIOME_DATA}) {
            if (tag.contains(key) && tag.get(key, CompoundTag.class).isEmpty()) {
                throw new ChunkFormatException("section " + key + " must be a compound");
            }
        }
        Optional<Paletted<BlockStateEntry>> blocks = Optional.empty();
        Optional<CompoundTag> blockTag = tag.get(BLOCK_STATES, CompoundTag.class);
        if (blockTag.isPresent()) {
            blocks = Optional.of(PaletteCodec.readBlocks(blockTag.get(), dataVersion));
        }
        Optional<Paletted<ResourceLocation>> biomes = Optional.empty();
        Optional<CompoundTag> biomeTag = tag.get(BIOME_DATA, CompoundTag.class);
        if (biomeTag.isPresent()) {
            biomes = Optional.of(PaletteCodec.readBiomes(biomeTag.get()));
        }
        return new ChunkSection(y, blocks, biomes, tag);
    }

    CompoundTag toNbt(int dataVersion) {
        CompoundTag.Builder builder = source.toBuilder().put(Y, new ByteTag((byte) y));
        blocks.ifPresent(b -> builder.put(BLOCK_STATES, PaletteCodec.writeBlocks(b, dataVersion)));
        biomes.ifPresent(b -> builder.put(BIOME_DATA, PaletteCodec.writeBiomes(b)));
        return builder.build();
    }

    public ChunkSection withBlocks(Paletted<BlockStateEntry> newBlocks) {
        return new ChunkSection(y, Optional.of(newBlocks), biomes, source);
    }

    public ChunkSection withBiomes(Paletted<ResourceLocation> newBiomes) {
        return new ChunkSection(y, blocks, Optional.of(newBiomes), source);
    }

    private static void requireSize(Paletted<?> paletted, int expected, String what) {
        if (paletted.size() != expected) {
            throw new IllegalArgumentException(what + " must have " + expected + " entries, got " + paletted.size());
        }
    }
}
