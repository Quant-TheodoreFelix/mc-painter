package space.qu4nt.thirdparty.mcpainter.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class ProjectFile {

    public static final String EXTENSION = ".mcpaint";
    public static final int FORMAT_VERSION = 4;
    public static final int MIN_FORMAT_VERSION = 1;
    public static final int HARD_MAX_TILES = 65536;

    static final byte[] MAGIC = {'M', 'C', 'P', 'A', 'I', 'N', 'T', 'R'};
    static final int RECORD_END = 0;
    static final int RECORD_HEADER = 1;
    static final int RECORD_TILE = 2;
    static final int MAX_RECORD_BYTES = TileCodec.MAX_RECORD_BYTES;
    static final int UNPACK_BATCH = 256;

    @FunctionalInterface
    public interface Thumbnailer {

        int[] thumbnail(WorldModel model, TilePos pos);
    }

    private interface Visitor {

        void header(WorldSpec spec, List<TerrainType> terrainTypes, int version, int maxTiles) throws IOException;

        void tile(CompoundTag tag) throws IOException;
    }

    private ProjectFile() {
    }

    public static void save(WorldModel model, Path file) throws IOException {
        WorldSpec spec = WorldSpec.of(model);
        AtomicFiles.write(file, channel -> {
            DataOutputStream out = open(channel);
            writeRecord(out, RECORD_HEADER, TileCodec.gzip(spec.toTag()));
            for (TilePos pos : model.tilePositions()) {
                if (model.base().filled() && model.isBaseTile(pos)) {
                    continue;
                }
                writeRecord(out, RECORD_TILE, TileCodec.gzip(TileCodec.encode(pos, model.tile(pos).orElseThrow())));
            }
            finish(out);
        });
    }

    public static void save(Workspace workspace, Path file, Progress progress) throws IOException {
        List<TilePos> positions = new ArrayList<>();
        for (Workspace.Entry entry : workspace.entries()) {
            positions.add(entry.pos());
        }
        positions.sort(null);
        AtomicFiles.write(file, channel -> {
            DataOutputStream out = open(channel);
            writeRecord(out, RECORD_HEADER, TileCodec.gzip(workspace.spec().toTag()));
            long done = 0;
            for (TilePos pos : positions) {
                writeRecord(out, RECORD_TILE, workspace.readRaw(pos));
                if (!progress.report(++done, positions.size())) {
                    throw new CancellationException("save cancelled");
                }
            }
            finish(out);
        });
    }

    public static WorldModel load(Path file) throws IOException {
        WorldModel.Builder[] builder = new WorldModel.Builder[1];
        int[] limit = new int[1];
        int[] count = new int[1];
        List<TerrainType>[] types = newTypes();
        int[] version = new int[1];
        read(file, new Visitor() {
            @Override
            public void header(WorldSpec spec, List<TerrainType> terrainTypes, int fileVersion, int maxTiles) {
                limit[0] = maxTiles;
                types[0] = terrainTypes;
                version[0] = fileVersion;
                builder[0] = spec.builder().maxTiles(maxTiles);
            }

            @Override
            public void tile(CompoundTag tag) throws IOException {
                if (++count[0] > limit[0]) {
                    throw new ProjectFormatException("more tiles than declared limit " + limit[0]);
                }
                TileCodec.addTo(builder[0], tag, types[0], version[0]);
            }
        }, Progress.NONE);
        try {
            return builder[0].build();
        } catch (IllegalArgumentException e) {
            throw new ProjectFormatException("invalid project: " + e.getMessage(), e);
        }
    }

    public static Workspace unpack(Path file, Path root, String name, Thumbnailer thumbnails, Progress progress)
            throws IOException {
        Workspace[] created = new Workspace[1];
        List<TerrainType>[] types = newTypes();
        int[] version = new int[1];
        Set<TilePos> seen = new HashSet<>();
        List<Workspace.Write> batch = new ArrayList<>();
        try {
            read(file, new Visitor() {
                @Override
                public void header(WorldSpec spec, List<TerrainType> terrainTypes, int fileVersion, int maxTiles)
                        throws IOException {
                    types[0] = terrainTypes;
                    version[0] = fileVersion;
                    created[0] = Workspace.create(root, spec, name);
                }

                @Override
                public void tile(CompoundTag tag) throws IOException {
                    Workspace workspace = created[0];
                    WorldSpec spec = workspace.spec();
                    TilePos pos = TileCodec.position(tag);
                    if (!spec.bounds().contains(pos)) {
                        throw new ProjectFormatException("tile " + pos + " outside the bounds");
                    }
                    if (!seen.add(pos)) {
                        throw new ProjectFormatException("duplicate tile " + pos);
                    }
                    WorldModel.Builder single = spec.builder().maxTiles(1);
                    TileCodec.addTo(single, tag, types[0], version[0]);
                    WorldModel model;
                    try {
                        model = single.build();
                    } catch (IllegalArgumentException e) {
                        throw new ProjectFormatException("invalid tile: " + e.getMessage(), e);
                    }
                    if (spec.base().filled() && model.isBaseTile(pos)) {
                        return;
                    }
                    batch.add(new Workspace.Write(pos, model.tile(pos).orElseThrow(),
                            thumbnails.thumbnail(model, pos), true));
                    if (batch.size() >= UNPACK_BATCH) {
                        workspace.write(List.copyOf(batch), false);
                        batch.clear();
                    }
                }
            }, progress);
            created[0].write(List.copyOf(batch), true);
            return created[0];
        } catch (IOException | RuntimeException e) {
            if (created[0] != null) {
                try {
                    created[0].deleteAndClose();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<TerrainType>[] newTypes() {
        return (List<TerrainType>[]) new List<?>[] {List.of()};
    }

    private static DataOutputStream open(FileChannel channel) throws IOException {
        OutputStream raw = Channels.newOutputStream(channel);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw));
        out.write(MAGIC);
        out.writeShort(FORMAT_VERSION);
        return out;
    }

    private static void finish(DataOutputStream out) throws IOException {
        out.writeByte(RECORD_END);
        out.writeInt(0);
        out.flush();
    }

    private static void read(Path file, Visitor visitor, Progress progress) throws IOException {
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectFormatException("not a regular file: " + file);
        }
        long size = Files.size(file);
        long remaining = size;
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
            boolean header = false;
            while (true) {
                int type = in.readUnsignedByte();
                int length = in.readInt();
                remaining -= 1 + Integer.BYTES;
                if (length < 0 || length > MAX_RECORD_BYTES || length > remaining) {
                    throw new ProjectFormatException("invalid record length " + length);
                }
                remaining -= length;
                if (type == RECORD_END) {
                    if (length != 0 || !header) {
                        throw new ProjectFormatException("malformed end record");
                    }
                    return;
                }
                byte[] payload = in.readNBytes(length);
                if (payload.length != length) {
                    throw new ProjectFormatException("project file truncated");
                }
                if (type == RECORD_HEADER) {
                    if (header) {
                        throw new ProjectFormatException("duplicate header record");
                    }
                    header = true;
                    CompoundTag tag = TileCodec.gunzip(payload, TileCodec.HEADER_LIMITS);
                    int maxTiles = HARD_MAX_TILES;
                    if (version < WorldSpec.BOUNDS_VERSION) {
                        maxTiles = TileCodec.requireInt(tag, "maxTiles");
                        if (maxTiles < 1 || maxTiles > HARD_MAX_TILES) {
                            throw new ProjectFormatException("maxTiles out of range: " + maxTiles);
                        }
                    }
                    visitor.header(WorldSpec.fromTag(tag, version), TileCodec.terrainTypes(tag), version, maxTiles);
                } else if (type == RECORD_TILE) {
                    if (!header) {
                        throw new ProjectFormatException("tile record before header");
                    }
                    visitor.tile(TileCodec.gunzip(payload, TileCodec.TILE_LIMITS));
                } else {
                    throw new ProjectFormatException("unknown record type " + type);
                }
                if (!progress.report(size - remaining, size)) {
                    throw new CancellationException("reading cancelled");
                }
            }
        } catch (EOFException e) {
            throw new ProjectFormatException("project file truncated", e);
        }
    }

    private static void writeRecord(DataOutputStream out, int type, byte[] payload) throws IOException {
        if (payload.length > MAX_RECORD_BYTES) {
            throw new IOException("project record exceeds " + MAX_RECORD_BYTES + " bytes");
        }
        out.writeByte(type);
        out.writeInt(payload.length);
        out.write(payload);
    }
}
