package space.qu4nt.thirdparty.mcpainter.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtCompression;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtFormatException;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtIo;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtReader;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class TileCodec {

    static final int MAX_RECORD_BYTES = 8 * 1024 * 1024;
    static final NbtLimits HEADER_LIMITS = new NbtLimits(16, 4L * 1024 * 1024);
    static final NbtLimits TILE_LIMITS = new NbtLimits(4, 2L * 1024 * 1024);
    static final int FLOWER_LAYERS_VERSION = 3;
    static final String LEGACY_FLOWERS = "flowers";
    static final List<Layer> LEGACY_FLOWER_LAYERS = List.of(Layer.DANDELION, Layer.POPPY);

    private TileCodec() {
    }

    static CompoundTag encode(TilePos pos, Tile tile) {
        float[] heights = tile.heights();
        int[] heightBits = new int[Tile.AREA];
        for (int i = 0; i < Tile.AREA; i++) {
            heightBits[i] = Float.floatToIntBits(heights[i]);
        }
        short[] water = tile.waterLevels();
        short[] biomes = tile.biomeIndices();
        int[] waterInts = new int[Tile.AREA];
        int[] biomeInts = new int[Tile.AREA];
        for (int i = 0; i < Tile.AREA; i++) {
            waterInts[i] = water[i];
            biomeInts[i] = biomes[i] & 0xFFFF;
        }
        CompoundTag.Builder layers = CompoundTag.builder();
        for (Layer layer : Layer.values()) {
            if (tile.layerUniform(layer)) {
                if (tile.layerFill(layer) != 0) {
                    layers.put(layer.id(), new IntTag(tile.layerFill(layer)));
                }
            } else {
                layers.put(layer.id(), ByteArrayTag.of(tile.layerValues(layer)));
            }
        }
        return CompoundTag.builder()
                .put("x", new IntTag(pos.x()))
                .put("z", new IntTag(pos.z()))
                .put("heights", IntArrayTag.of(heightBits))
                .put("terrain", ByteArrayTag.of(tile.terrainOrdinals()))
                .put("water", IntArrayTag.of(waterInts))
                .put("biomes", IntArrayTag.of(biomeInts))
                .put("layers", layers.build())
                .build();
    }

    static byte[] gzip(CompoundTag tag) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        NbtIo.write(encoded, RootTag.of(tag), NbtCompression.GZIP);
        if (encoded.size() > MAX_RECORD_BYTES) {
            throw new IOException("record exceeds " + MAX_RECORD_BYTES + " bytes");
        }
        return encoded.toByteArray();
    }

    static CompoundTag gunzip(byte[] payload, NbtLimits limits) throws IOException {
        if (payload.length > MAX_RECORD_BYTES) {
            throw new ProjectFormatException("record exceeds " + MAX_RECORD_BYTES + " bytes");
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            CompoundTag tag = NbtReader.read(in, limits).tag();
            if (in.read() != -1) {
                throw new ProjectFormatException("trailing data in record");
            }
            return tag;
        } catch (NbtFormatException | ZipException | EOFException e) {
            throw new ProjectFormatException("corrupt record: " + e.getMessage(), e);
        }
    }

    static TilePos position(CompoundTag tag) throws ProjectFormatException {
        return new TilePos(requireInt(tag, "x"), requireInt(tag, "z"));
    }

    static ListTag terrainIds() {
        List<Tag> terrain = new ArrayList<>();
        for (TerrainType type : TerrainType.values()) {
            terrain.add(new StringTag(type.id()));
        }
        return ListTag.of(TagType.STRING, terrain);
    }

    static List<TerrainType> terrainTypes(CompoundTag header) throws ProjectFormatException {
        List<TerrainType> types = new ArrayList<>();
        ListTag list = requireList(header, "terrainTypes");
        if (list.size() > 255) {
            throw new ProjectFormatException("too many terrain types");
        }
        for (Tag tag : list) {
            String id = ((StringTag) tag).value();
            types.add(TerrainType.fromId(id).orElseThrow(() -> new ProjectFormatException("unknown terrain " + id)));
        }
        return types;
    }

    static TilePos addTo(WorldModel.Builder builder, CompoundTag tag, List<TerrainType> terrainTypes, int version)
            throws ProjectFormatException {
        int[] heightBits = requireArray(tag, "heights");
        byte[] terrainIndices = tag.get("terrain", ByteArrayTag.class)
                .orElseThrow(() -> new ProjectFormatException("tile has no terrain")).toArray();
        int[] waterInts = requireArray(tag, "water");
        int[] biomeInts = requireArray(tag, "biomes");
        if (terrainIndices.length != Tile.AREA) {
            throw new ProjectFormatException("tile arrays must have " + Tile.AREA + " entries");
        }
        float[] heights = new float[Tile.AREA];
        byte[] terrain = new byte[Tile.AREA];
        short[] water = new short[Tile.AREA];
        short[] biomes = new short[Tile.AREA];
        for (int i = 0; i < Tile.AREA; i++) {
            heights[i] = Float.intBitsToFloat(heightBits[i]);
            int index = terrainIndices[i] & 0xFF;
            if (index >= terrainTypes.size()) {
                throw new ProjectFormatException("terrain index out of range");
            }
            terrain[i] = (byte) terrainTypes.get(index).ordinal();
            if (waterInts[i] < Short.MIN_VALUE || waterInts[i] > Short.MAX_VALUE) {
                throw new ProjectFormatException("water level out of range");
            }
            water[i] = (short) waterInts[i];
            if (biomeInts[i] < 0 || biomeInts[i] > WorldModel.MAX_BIOMES) {
                throw new ProjectFormatException("biome index out of range");
            }
            biomes[i] = (short) biomeInts[i];
        }
        try {
            TilePos pos = position(tag);
            builder.tile(pos, heights, terrain, water, biomes);
            if (tag.contains("layers")) {
                CompoundTag layers = tag.get("layers", CompoundTag.class)
                        .orElseThrow(() -> new ProjectFormatException("layers must be a compound"));
                for (Map.Entry<String, Tag> entry : layers.entries().entrySet()) {
                    for (Layer layer : layersFor(entry.getKey(), version)) {
                        switch (entry.getValue()) {
                            case IntTag fill -> builder.layerFill(pos, layer, fill.value());
                            case ByteArrayTag values -> builder.layer(pos, layer, values.toArray());
                            default -> throw new ProjectFormatException("layer " + entry.getKey() + " has a wrong type");
                        }
                    }
                }
            }
            return pos;
        } catch (IllegalArgumentException e) {
            throw new ProjectFormatException("invalid tile: " + e.getMessage(), e);
        }
    }

    static List<Layer> layersFor(String id, int version) throws ProjectFormatException {
        if (version < FLOWER_LAYERS_VERSION) {
            if (id.equals(LEGACY_FLOWERS)) {
                return LEGACY_FLOWER_LAYERS;
            }
            Optional<Layer> layer = Layer.fromId(id).filter(found -> !found.flower());
            if (layer.isPresent()) {
                return List.of(layer.get());
            }
        } else {
            Optional<Layer> layer = Layer.fromId(id);
            if (layer.isPresent()) {
                return List.of(layer.get());
            }
        }
        throw new ProjectFormatException("unknown layer " + id);
    }

    static int requireInt(CompoundTag tag, String key) throws ProjectFormatException {
        return tag.get(key, IntTag.class)
                .orElseThrow(() -> new ProjectFormatException("missing int " + key)).value();
    }

    static int[] requireArray(CompoundTag tag, String key) throws ProjectFormatException {
        int[] values = tag.get(key, IntArrayTag.class)
                .orElseThrow(() -> new ProjectFormatException("missing array " + key)).toArray();
        if (values.length != Tile.AREA) {
            throw new ProjectFormatException(key + " must have " + Tile.AREA + " entries");
        }
        return values;
    }

    static ListTag requireList(CompoundTag tag, String key) throws ProjectFormatException {
        ListTag list = tag.get(key, ListTag.class)
                .orElseThrow(() -> new ProjectFormatException("missing list " + key));
        if (!list.isEmpty() && list.elementType() != TagType.STRING) {
            throw new ProjectFormatException(key + " must be a list of strings");
        }
        return list;
    }
}
