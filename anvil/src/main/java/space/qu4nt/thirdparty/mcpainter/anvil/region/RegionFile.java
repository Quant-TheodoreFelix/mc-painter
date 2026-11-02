package space.qu4nt.thirdparty.mcpainter.anvil.region;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;

public final class RegionFile implements AutoCloseable {

    public static final int SECTOR_BYTES = 4096;
    public static final int MAX_INLINE_SECTORS = 255;

    static final int ENTRIES = RegionPos.CHUNKS_PER_SIDE * RegionPos.CHUNKS_PER_SIDE;
    static final int HEADER_SECTORS = 2;
    static final int HEADER_BYTES = HEADER_SECTORS * SECTOR_BYTES;
    static final int CHUNK_HEADER_BYTES = 5;

    public enum ChunkState {
        ABSENT,
        PRESENT,
        CORRUPT
    }

    private final Path path;
    private final RegionPos pos;
    private final RegionLimits limits;
    private final FileChannel channel;
    private final long fileSize;
    private final int[] offsets = new int[ENTRIES];
    private final int[] sectorCounts = new int[ENTRIES];
    private final int[] timestamps = new int[ENTRIES];
    private final String[] problems = new String[ENTRIES];

    private RegionFile(Path path, RegionPos pos, RegionLimits limits, FileChannel channel, long fileSize) {
        this.path = path;
        this.pos = pos;
        this.limits = limits;
        this.channel = channel;
        this.fileSize = fileSize;
    }

    public static RegionFile open(Path path) throws IOException {
        return open(path, RegionLimits.DEFAULT);
    }

    public static RegionFile open(Path path, RegionLimits limits) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(limits, "limits");
        Path fileName = path.getFileName();
        RegionPos pos = RegionPos.parseFileName(fileName == null ? "" : fileName.toString())
                .orElseThrow(() -> new RegionFormatException("not a region file name: " + path));
        AtomicFiles.requireNotSymlink(path);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try {
            RegionFile region = new RegionFile(path, pos, limits, channel, channel.size());
            region.loadHeader();
            return region;
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private void loadHeader() throws IOException {
        if (fileSize == 0) {
            return;
        }
        if (fileSize < HEADER_BYTES) {
            throw new RegionFormatException("region header truncated: " + fileSize + " bytes");
        }
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        readFully(channel, header, 0);
        long fileSectors = (fileSize + SECTOR_BYTES - 1) / SECTOR_BYTES;
        List<Integer> present = new ArrayList<>();
        for (int i = 0; i < ENTRIES; i++) {
            int location = header.getInt(i * Integer.BYTES);
            timestamps[i] = header.getInt(SECTOR_BYTES + i * Integer.BYTES);
            offsets[i] = location >>> 8;
            sectorCounts[i] = location & 0xFF;
            if (location == 0) {
                continue;
            }
            if (offsets[i] < HEADER_SECTORS) {
                problems[i] = "chunk sectors overlap the region header";
            } else if (sectorCounts[i] == 0) {
                problems[i] = "chunk has zero allocated sectors";
            } else if ((long) offsets[i] + sectorCounts[i] > fileSectors) {
                problems[i] = "chunk sectors extend beyond end of file";
            } else {
                present.add(i);
            }
        }
        markOverlaps(present);
    }

    private void markOverlaps(List<Integer> present) {
        present.sort(Comparator.comparingInt(i -> offsets[i]));
        int ownerOfMaxEnd = -1;
        long maxEnd = 0;
        for (int i : present) {
            if (offsets[i] < maxEnd) {
                problems[i] = "chunk sectors overlap another chunk";
                problems[ownerOfMaxEnd] = "chunk sectors overlap another chunk";
            }
            long end = (long) offsets[i] + sectorCounts[i];
            if (end > maxEnd) {
                maxEnd = end;
                ownerOfMaxEnd = i;
            }
        }
    }

    public Path path() {
        return path;
    }

    public RegionPos pos() {
        return pos;
    }

    public ChunkState state(int localX, int localZ) {
        int i = RegionPos.index(localX, localZ);
        if (problems[i] != null) {
            return ChunkState.CORRUPT;
        }
        return offsets[i] == 0 && sectorCounts[i] == 0 ? ChunkState.ABSENT : ChunkState.PRESENT;
    }

    public Optional<String> problem(int localX, int localZ) {
        return Optional.ofNullable(problems[RegionPos.index(localX, localZ)]);
    }

    public int timestamp(int localX, int localZ) {
        return timestamps[RegionPos.index(localX, localZ)];
    }

    public Optional<RawChunk> readRaw(int localX, int localZ) throws IOException {
        int i = RegionPos.index(localX, localZ);
        switch (state(localX, localZ)) {
            case ABSENT -> {
                return Optional.empty();
            }
            case CORRUPT -> throw corrupt(localX, localZ, problems[i]);
            case PRESENT -> {
            }
        }
        long start = (long) offsets[i] * SECTOR_BYTES;
        ByteBuffer head = ByteBuffer.allocate(CHUNK_HEADER_BYTES);
        readFully(channel, head, start);
        int length = head.getInt(0);
        int type = head.get(Integer.BYTES) & 0xFF;
        if (length < 1) {
            throw corrupt(localX, localZ, "invalid payload length " + length);
        }
        if (Integer.BYTES + (long) length > (long) sectorCounts[i] * SECTOR_BYTES) {
            throw corrupt(localX, localZ, "payload length exceeds allocated sectors");
        }
        if (start + Integer.BYTES + length > fileSize) {
            throw corrupt(localX, localZ, "payload truncated by end of file");
        }
        int compressionId = type & ~ChunkCompression.EXTERNAL_FLAG;
        if (compressionId == 0) {
            throw corrupt(localX, localZ, "compression id 0");
        }
        byte[] data;
        if ((type & ChunkCompression.EXTERNAL_FLAG) != 0) {
            data = readExternal(localX, localZ);
        } else {
            data = new byte[length - 1];
            readFully(channel, ByteBuffer.wrap(data), start + CHUNK_HEADER_BYTES);
        }
        return Optional.of(RawChunk.wrap(compressionId, data, timestamps[i]));
    }

    public Optional<RootTag> readChunk(int localX, int localZ) throws IOException {
        Optional<RawChunk> raw = readRaw(localX, localZ);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(raw.get().decode(limits.nbt()));
    }

    Path externalPath(int localX, int localZ) {
        return externalPath(path, pos, localX, localZ);
    }

    static Path externalPath(Path regionPath, RegionPos pos, int localX, int localZ) {
        return regionPath.resolveSibling("c." + pos.chunkX(localX) + "." + pos.chunkZ(localZ) + ".mcc");
    }

    private byte[] readExternal(int localX, int localZ) throws IOException {
        Path external = externalPath(localX, localZ);
        AtomicFiles.requireNotSymlink(external);
        try (FileChannel file = FileChannel.open(external, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = file.size();
            if (size > limits.maxExternalChunkBytes()) {
                throw corrupt(localX, localZ, "external chunk exceeds " + limits.maxExternalChunkBytes() + " bytes");
            }
            byte[] data = new byte[(int) size];
            readFully(file, ByteBuffer.wrap(data), 0);
            return data;
        } catch (NoSuchFileException e) {
            throw new RegionFormatException("missing external chunk file " + external.getFileName(), e);
        }
    }

    private RegionFormatException corrupt(int localX, int localZ, String reason) {
        return new RegionFormatException(
                path.getFileName() + " chunk (" + localX + ", " + localZ + "): " + reason);
    }

    static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        long cursor = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, cursor);
            if (read < 0) {
                throw new EOFException("unexpected end of file at " + cursor);
            }
            cursor += read;
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
