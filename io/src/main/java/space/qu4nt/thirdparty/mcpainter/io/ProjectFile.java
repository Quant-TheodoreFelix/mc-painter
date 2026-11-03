package space.qu4nt.thirdparty.mcpainter.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtCompression;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtFormatException;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtIo;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtReader;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class ProjectFile {

    public static final String EXTENSION = ".mcpaint";
    public static final int FORMAT_VERSION = 3;
    static final int FLOWER_LAYERS_VERSION = 3;
    static final String LEGACY_FLOWERS = "flowers";
    static final List<Layer> LEGACY_FLOWER_LAYERS = List.of(Layer.DANDELION, Layer.POPPY);
    public static final int MIN_FORMAT_VERSION = 1;
    public static final int HARD_MAX_TILES = 65536;

    static final byte[] MAGIC = {'M', 'C', 'P', 'A', 'I', 'N', 'T', 'R'};
    static final int RECORD_END = 0;
    static final int RECORD_HEADER = 1;
    static final int RECORD_TILE = 2;
    static final int MAX_RECORD_BYTES = 8 * 1024 * 1024;

    private static final NbtLimits HEADER_LIMITS = new NbtLimits(16, 4L * 1024 * 1024);
    private static final NbtLimits TILE_LIMITS = new NbtLimits(4, 2L * 1024 * 1024);

    private ProjectFile() {
    }

    public static void save(WorldModel model, Path file) throws IOException {
        AtomicFiles.write(file, channel -> {
            OutputStream raw = Channels.newOutputStream(channel);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw));
            out.write(MAGIC);
            out.writeShort(FORMAT_VERSION);
            writeRecord(out, RECORD_HEADER, header(model));
            for (TilePos pos : model.tilePositions()) {
                writeRecord(out, RECORD_TILE, tile(pos, model.tile(pos).orElseThrow()));
            }
            out.writeByte(RECORD_END);
            out.writeInt(0);
            out.flush();
        });
    }

    public static WorldModel load(Path file) throws IOException {
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectFormatException("not a regular file: " + file);
        }
        long remaining = Files.size(file);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new ProjectFormatException("not an mc-painter project file");
            }
            int version = in.readUnsignedShort();
            if (version < MIN_FORMAT_VERSION || version > FORMAT_VERSION) {
                throw new ProjectFormatException("unsupported project format version " + version);
            }
            remaining -= MAGIC.length + Short.BYTES;
            WorldModel.Builder builder = null;
            List<TerrainType> terrainTypes = List.of();
            int maxTiles = 0;
            int tiles = 0;
            while (true) {
                int type = in.readUnsignedByte();
                int length = in.readInt();
                remaining -= 1 + Integer.BYTES;
                if (length < 0 || length > MAX_RECORD_BYTES || length > remaining) {
                    throw new ProjectFormatException("invalid record length " + length);
                }
                remaining -= length;
                if (type == RECORD_END) {
                    if (length != 0 || builder == null) {
                        throw new ProjectFormatException("malformed end record");
                    }
                    return builder.build();
                }
                byte[] payload = in.readNBytes(length);
                if (payload.length != length) {
                    throw new ProjectFormatException("project file truncated");
                }
                if (type == RECORD_HEADER) {
                    if (builder != null) {
                        throw new ProjectFormatException("duplicate header record");
                    }
                    CompoundTag header = decode(payload, HEADER_LIMITS);
                    maxTiles = requireInt(header, "maxTiles");
                    if (maxTiles < 1 || maxTiles > HARD_MAX_TILES) {
                        throw new ProjectFormatException("maxTiles out of range: " + maxTiles);
                    }
                    terrainTypes = terrainTypes(header);
                    builder = newBuilder(header, maxTiles, version);
                } else if (type == RECORD_TILE) {
                    if (builder == null) {
                        throw new ProjectFormatException("tile record before header");
                    }
                    if (++tiles > maxTiles) {
                        throw new ProjectFormatException("more tiles than declared limit " + maxTiles);
                    }
                    addTile(builder, decode(payload, TILE_LIMITS), terrainTypes, version);
                } else {
                    throw new ProjectFormatException("unknown record type " + type);
                }
            }
        } catch (EOFException e) {
            throw new ProjectFormatException("project file truncated", e);
        }
    }

    private static CompoundTag header(WorldModel model) {
        List<Tag> terrain = new ArrayList<>();
        for (TerrainType type : TerrainType.values()) {
            terrain.add(new StringTag(type.id()));
        }
        List<Tag> biomes = new ArrayList<>();
        for (String biome : model.biomes()) {
            biomes.add(new StringTag(biome));
        }
        return CompoundTag.builder()
                .put("minY", new IntTag(model.minY()))
                .put("maxY", new IntTag(model.maxY()))
                .put("seaLevel", new IntTag(model.seaLevel()))
                .put("maxTiles", new IntTag(model.maxTiles()))
                .put("terrainTypes", ListTag.of(TagType.STRING, terrain))
                .put("biomes", ListTag.of(TagType.STRING, biomes))
                .put("seed", new LongTag(model.generation().seed()))
                .put("initialOres", new ByteTag((byte) (model.generation().initialOres() ? 1 : 0)))
                .build();
    }

    private static CompoundTag tile(TilePos pos, Tile tile) {
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

    private static WorldModel.Builder newBuilder(CompoundTag header, int maxTiles, int version)
            throws ProjectFormatException {
        GenerationSettings generation = GenerationSettings.DEFAULT;
        if (version >= 2) {
            long seed = header.get("seed", LongTag.class)
                    .orElseThrow(() -> new ProjectFormatException("missing long seed")).value();
            boolean initialOres = false;
            if (header.contains("initialOres")) {
                byte flag = header.get("initialOres", ByteTag.class)
                        .orElseThrow(() -> new ProjectFormatException("initialOres must be a byte")).value();
                if (flag != 0 && flag != 1) {
                    throw new ProjectFormatException("initialOres must be 0 or 1");
                }
                initialOres = flag == 1;
            }
            generation = new GenerationSettings(seed, initialOres);
        }
        List<String> biomes = new ArrayList<>();
        for (Tag tag : requireList(header, "biomes")) {
            biomes.add(((StringTag) tag).value());
        }
        try {
            return WorldModel.builder(requireInt(header, "minY"), requireInt(header, "maxY"),
                    requireInt(header, "seaLevel")).maxTiles(maxTiles).biomes(biomes).generation(generation);
        } catch (IllegalArgumentException e) {
            throw new ProjectFormatException("invalid project header: " + e.getMessage(), e);
        }
    }

    private static List<TerrainType> terrainTypes(CompoundTag header) throws ProjectFormatException {
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

    private static void addTile(WorldModel.Builder builder, CompoundTag tag, List<TerrainType> terrainTypes, int version)
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
            TilePos pos = new TilePos(requireInt(tag, "x"), requireInt(tag, "z"));
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
        } catch (IllegalArgumentException e) {
            throw new ProjectFormatException("invalid tile: " + e.getMessage(), e);
        }
    }

    private static List<Layer> layersFor(String id, int version) throws ProjectFormatException {
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

    private static void writeRecord(DataOutputStream out, int type, CompoundTag payload) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        NbtIo.write(encoded, RootTag.of(payload), NbtCompression.GZIP);
        if (encoded.size() > MAX_RECORD_BYTES) {
            throw new IOException("project record exceeds " + MAX_RECORD_BYTES + " bytes");
        }
        out.writeByte(type);
        out.writeInt(encoded.size());
        encoded.writeTo(out);
    }

    private static CompoundTag decode(byte[] payload, NbtLimits limits) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            CompoundTag tag = NbtReader.read(in, limits).tag();
            if (in.read() != -1) {
                throw new ProjectFormatException("trailing data in project record");
            }
            return tag;
        } catch (NbtFormatException | ZipException | EOFException e) {
            throw new ProjectFormatException("corrupt project record: " + e.getMessage(), e);
        }
    }

    private static int requireInt(CompoundTag tag, String key) throws ProjectFormatException {
        return tag.get(key, IntTag.class)
                .orElseThrow(() -> new ProjectFormatException("missing int " + key)).value();
    }

    private static int[] requireArray(CompoundTag tag, String key) throws ProjectFormatException {
        int[] values = tag.get(key, IntArrayTag.class)
                .orElseThrow(() -> new ProjectFormatException("missing array " + key)).toArray();
        if (values.length != Tile.AREA) {
            throw new ProjectFormatException(key + " must have " + Tile.AREA + " entries");
        }
        return values;
    }

    private static ListTag requireList(CompoundTag tag, String key) throws ProjectFormatException {
        ListTag list = tag.get(key, ListTag.class)
                .orElseThrow(() -> new ProjectFormatException("missing list " + key));
        if (!list.isEmpty() && list.elementType() != TagType.STRING) {
            throw new ProjectFormatException(key + " must be a list of strings");
        }
        return list;
    }
}
