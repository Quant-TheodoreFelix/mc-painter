package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;

final class PaletteCodec {

    static final int BLOCK_COUNT = 4096;
    static final int BIOME_COUNT = 64;
    static final int BLOCK_MIN_BITS = 4;
    static final int BIOME_MIN_BITS = 0;
    static final int MAX_PALETTE = 4096;

    private static final String PALETTE = "palette";
    private static final String DATA = "data";

    private PaletteCodec() {
    }

    @FunctionalInterface
    private interface EntryParser<T> {
        List<T> parse(ListTag palette) throws ChunkFormatException;
    }

    static Paletted<BlockStateEntry> readBlocks(CompoundTag container, int dataVersion) throws ChunkFormatException {
        boolean idKeys = dataVersion >= DataVersions.PALETTE_ID_KEYS;
        return read(container, BLOCK_COUNT, BLOCK_MIN_BITS,
                palette -> idKeys ? parseIdPalette(palette) : parseLegacyPalette(palette));
    }

    static Paletted<ResourceLocation> readBiomes(CompoundTag container) throws ChunkFormatException {
        return read(container, BIOME_COUNT, BIOME_MIN_BITS, PaletteCodec::parseBiomePalette);
    }

    static CompoundTag writeBlocks(Paletted<BlockStateEntry> blocks, int dataVersion) {
        Paletted<BlockStateEntry> compact = blocks.compact();
        ListTag palette = dataVersion >= DataVersions.PALETTE_ID_KEYS
                ? encodeIdPalette(compact.palette())
                : encodeLegacyPalette(compact.palette());
        return write(compact, palette, BLOCK_MIN_BITS);
    }

    static CompoundTag writeBiomes(Paletted<ResourceLocation> biomes) {
        Paletted<ResourceLocation> compact = biomes.compact();
        List<Tag> names = new ArrayList<>(compact.palette().size());
        for (ResourceLocation biome : compact.palette()) {
            names.add(new StringTag(biome.toString()));
        }
        return write(compact, ListTag.of(TagType.STRING, names), BIOME_MIN_BITS);
    }

    private static <T> Paletted<T> read(CompoundTag container, int count, int minBits, EntryParser<T> parser)
            throws ChunkFormatException {
        ListTag paletteTag = container.get(PALETTE, ListTag.class)
                .orElseThrow(() -> new ChunkFormatException("paletted container has no palette list"));
        if (paletteTag.isEmpty()) {
            throw new ChunkFormatException("palette must not be empty");
        }
        if (paletteTag.size() > MAX_PALETTE) {
            throw new ChunkFormatException("palette has " + paletteTag.size() + " entries, limit " + MAX_PALETTE);
        }
        List<T> palette = parser.parse(paletteTag);
        int bits = PackedIndices.bitsFor(palette.size(), minBits);
        if (bits == 0) {
            return Paletted.trusted(palette, new int[count]);
        }
        LongArrayTag data = container.get(DATA, LongArrayTag.class)
                .orElseThrow(() -> new ChunkFormatException("paletted container has no data array"));
        int[] indices = PackedIndices.unpack(data.toArray(), count, bits, palette.size());
        return Paletted.trusted(palette, indices);
    }

    private static <T> CompoundTag write(Paletted<T> compact, ListTag palette, int minBits) {
        CompoundTag.Builder builder = CompoundTag.builder().put(PALETTE, palette);
        int bits = PackedIndices.bitsFor(compact.palette().size(), minBits);
        if (bits > 0) {
            int[] indices = new int[compact.size()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = compact.paletteIndex(i);
            }
            builder.put(DATA, LongArrayTag.of(PackedIndices.pack(indices, bits)));
        }
        return builder.build();
    }

    private static List<BlockStateEntry> parseLegacyPalette(ListTag palette) throws ChunkFormatException {
        requireElementType(palette, TagType.COMPOUND);
        List<BlockStateEntry> entries = new ArrayList<>(palette.size());
        for (Tag element : palette) {
            CompoundTag compound = (CompoundTag) element;
            ResourceLocation id = parseId(requireString(compound, "Name"));
            SortedMap<String, String> properties = parseProperties(compound.get("Properties"));
            entries.add(BlockStateEntry.of(id, properties));
        }
        return entries;
    }

    private static List<BlockStateEntry> parseIdPalette(ListTag palette) throws ChunkFormatException {
        List<BlockStateEntry> entries = new ArrayList<>(palette.size());
        if (palette.elementType() == TagType.STRING) {
            for (Tag element : palette) {
                entries.add(BlockStateEntry.defaultState(parseId(((StringTag) element).value())));
            }
            return entries;
        }
        requireElementType(palette, TagType.COMPOUND);
        for (Tag element : palette) {
            CompoundTag compound = (CompoundTag) element;
            Optional<StringTag> wrapped = compound.get("", StringTag.class);
            if (wrapped.isPresent() && compound.size() == 1) {
                entries.add(BlockStateEntry.defaultState(parseId(wrapped.get().value())));
                continue;
            }
            ResourceLocation id = parseId(requireString(compound, "id"));
            Optional<Tag> properties = compound.get("properties");
            if (properties.isEmpty()) {
                entries.add(BlockStateEntry.defaultState(id));
            } else {
                entries.add(BlockStateEntry.of(id, parseProperties(properties)));
            }
        }
        return entries;
    }

    private static List<ResourceLocation> parseBiomePalette(ListTag palette) throws ChunkFormatException {
        requireElementType(palette, TagType.STRING);
        List<ResourceLocation> entries = new ArrayList<>(palette.size());
        for (Tag element : palette) {
            entries.add(parseId(((StringTag) element).value()));
        }
        return entries;
    }

    private static ListTag encodeLegacyPalette(List<BlockStateEntry> palette) {
        List<Tag> entries = new ArrayList<>(palette.size());
        for (BlockStateEntry entry : palette) {
            SortedMap<String, String> properties = entry.properties().orElseThrow(() -> new IllegalStateException(
                    "block " + entry.id() + " has elided default properties; legacy palettes need a block registry"));
            CompoundTag.Builder builder = CompoundTag.builder().put("Name", new StringTag(entry.id().toString()));
            if (!properties.isEmpty()) {
                builder.put("Properties", encodeProperties(properties));
            }
            entries.add(builder.build());
        }
        return ListTag.of(TagType.COMPOUND, entries);
    }

    private static ListTag encodeIdPalette(List<BlockStateEntry> palette) {
        boolean allDefault = palette.stream().allMatch(PaletteCodec::isElidable);
        List<Tag> entries = new ArrayList<>(palette.size());
        if (allDefault) {
            for (BlockStateEntry entry : palette) {
                entries.add(new StringTag(entry.id().toString()));
            }
            return ListTag.of(TagType.STRING, entries);
        }
        for (BlockStateEntry entry : palette) {
            CompoundTag.Builder builder = CompoundTag.builder().put("id", new StringTag(entry.id().toString()));
            if (!isElidable(entry)) {
                builder.put("properties", encodeProperties(entry.properties().orElseThrow()));
            }
            entries.add(builder.build());
        }
        return ListTag.of(TagType.COMPOUND, entries);
    }

    private static boolean isElidable(BlockStateEntry entry) {
        return entry.properties().map(Map::isEmpty).orElse(true);
    }

    private static CompoundTag encodeProperties(SortedMap<String, String> properties) {
        CompoundTag.Builder builder = CompoundTag.builder();
        for (Map.Entry<String, String> property : properties.entrySet()) {
            builder.put(property.getKey(), new StringTag(property.getValue()));
        }
        return builder.build();
    }

    private static SortedMap<String, String> parseProperties(Optional<Tag> tag) throws ChunkFormatException {
        SortedMap<String, String> properties = new TreeMap<>();
        if (tag.isEmpty()) {
            return properties;
        }
        if (!(tag.get() instanceof CompoundTag compound)) {
            throw new ChunkFormatException("block properties must be a compound");
        }
        for (Map.Entry<String, Tag> property : compound.entries().entrySet()) {
            if (!(property.getValue() instanceof StringTag value)
                    || property.getKey().isEmpty() || value.value().isEmpty()) {
                throw new ChunkFormatException("invalid block property " + property.getKey());
            }
            properties.put(property.getKey(), value.value());
        }
        return properties;
    }

    private static void requireElementType(ListTag palette, TagType type) throws ChunkFormatException {
        if (palette.elementType() != type) {
            throw new ChunkFormatException("palette must be a list of " + type + ", got " + palette.elementType());
        }
    }

    private static String requireString(CompoundTag compound, String key) throws ChunkFormatException {
        return compound.get(key, StringTag.class)
                .orElseThrow(() -> new ChunkFormatException("palette entry has no " + key + " string"))
                .value();
    }

    private static ResourceLocation parseId(String value) throws ChunkFormatException {
        try {
            return ResourceLocation.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ChunkFormatException("invalid resource location in palette: " + value, e);
        }
    }
}
