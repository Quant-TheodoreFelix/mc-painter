package space.qu4nt.thirdparty.mcpainter.anvil.region;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;

public final class RegionWriter {

    private static final int MAX_SECTOR_OFFSET = 0xFFFFFF;

    private final RawChunk[] chunks = new RawChunk[RegionFile.ENTRIES];

    public RegionWriter() {
    }

    public RegionWriter put(int localX, int localZ, RawChunk chunk) {
        chunks[RegionPos.index(localX, localZ)] = Objects.requireNonNull(chunk, "chunk");
        return this;
    }

    public RegionWriter remove(int localX, int localZ) {
        chunks[RegionPos.index(localX, localZ)] = null;
        return this;
    }

    public Optional<RawChunk> get(int localX, int localZ) {
        return Optional.ofNullable(chunks[RegionPos.index(localX, localZ)]);
    }

    public void writeTo(Path regionPath) throws IOException {
        Objects.requireNonNull(regionPath, "regionPath");
        Path fileName = regionPath.getFileName();
        RegionPos pos = RegionPos.parseFileName(fileName == null ? "" : fileName.toString())
                .orElseThrow(() -> new RegionFormatException("not a region file name: " + regionPath));

        int[] offsets = new int[RegionFile.ENTRIES];
        int[] sectorCounts = new int[RegionFile.ENTRIES];
        boolean[] external = new boolean[RegionFile.ENTRIES];
        int cursor = RegionFile.HEADER_SECTORS;
        for (int i = 0; i < RegionFile.ENTRIES; i++) {
            RawChunk chunk = chunks[i];
            if (chunk == null) {
                continue;
            }
            long inlineBytes = (long) RegionFile.CHUNK_HEADER_BYTES + chunk.payloadLength();
            int sectors = (int) ((inlineBytes + RegionFile.SECTOR_BYTES - 1) / RegionFile.SECTOR_BYTES);
            if (sectors > RegionFile.MAX_INLINE_SECTORS) {
                external[i] = true;
                sectors = 1;
            }
            if ((long) cursor + sectors > MAX_SECTOR_OFFSET) {
                throw new RegionFormatException("region exceeds addressable sectors");
            }
            offsets[i] = cursor;
            sectorCounts[i] = sectors;
            cursor += sectors;
        }

        for (int i = 0; i < RegionFile.ENTRIES; i++) {
            if (external[i]) {
                byte[] payload = chunks[i].rawPayload();
                Path target = RegionFile.externalPath(regionPath, pos, i % RegionPos.CHUNKS_PER_SIDE,
                        i / RegionPos.CHUNKS_PER_SIDE);
                AtomicFiles.write(target, channel -> writeFully(channel, ByteBuffer.wrap(payload)));
            }
        }

        AtomicFiles.write(regionPath, channel -> writeRegion(channel, offsets, sectorCounts, external));

        for (int i = 0; i < RegionFile.ENTRIES; i++) {
            if (!external[i]) {
                AtomicFiles.deleteIfExists(RegionFile.externalPath(regionPath, pos,
                        i % RegionPos.CHUNKS_PER_SIDE, i / RegionPos.CHUNKS_PER_SIDE));
            }
        }
    }

    private void writeRegion(FileChannel channel, int[] offsets, int[] sectorCounts, boolean[] external)
            throws IOException {
        ByteBuffer header = ByteBuffer.allocate(RegionFile.HEADER_BYTES);
        for (int i = 0; i < RegionFile.ENTRIES; i++) {
            if (chunks[i] != null) {
                header.putInt(i * Integer.BYTES, (offsets[i] << 8) | sectorCounts[i]);
                header.putInt(RegionFile.SECTOR_BYTES + i * Integer.BYTES, chunks[i].timestamp());
            }
        }
        writeFully(channel, header);
        for (int i = 0; i < RegionFile.ENTRIES; i++) {
            RawChunk chunk = chunks[i];
            if (chunk == null) {
                continue;
            }
            byte[] payload = external[i] ? new byte[0] : chunk.rawPayload();
            int type = chunk.compressionId() | (external[i] ? ChunkCompression.EXTERNAL_FLAG : 0);
            ByteBuffer block = ByteBuffer.allocate(sectorCounts[i] * RegionFile.SECTOR_BYTES);
            block.putInt(1 + payload.length);
            block.put((byte) type);
            block.put(payload);
            block.clear();
            writeFully(channel, block);
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
