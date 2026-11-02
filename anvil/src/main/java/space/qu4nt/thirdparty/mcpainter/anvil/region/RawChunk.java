package space.qu4nt.thirdparty.mcpainter.anvil.region;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.Lz4BlockInputStream;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.Lz4FormatException;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtCompression;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtIo;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;

public final class RawChunk {

    private final int compressionId;
    private final byte[] payload;
    private final int timestamp;

    private RawChunk(int compressionId, byte[] payload, int timestamp) {
        this.compressionId = compressionId;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public static RawChunk of(int compressionId, byte[] payload, int timestamp) {
        if (compressionId < 1 || compressionId >= ChunkCompression.EXTERNAL_FLAG) {
            throw new IllegalArgumentException("compression id out of range: " + compressionId);
        }
        return new RawChunk(compressionId, payload.clone(), timestamp);
    }

    static RawChunk wrap(int compressionId, byte[] payload, int timestamp) {
        return new RawChunk(compressionId, payload, timestamp);
    }

    public static RawChunk encode(RootTag root, ChunkCompression compression, int timestamp) throws IOException {
        Objects.requireNonNull(root, "root");
        NbtCompression codec = switch (compression) {
            case GZIP -> NbtCompression.GZIP;
            case ZLIB -> NbtCompression.ZLIB;
            case NONE -> NbtCompression.NONE;
            case LZ4, CUSTOM -> throw new UnsupportedCompressionException(compression.id());
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.write(out, root, codec);
        return new RawChunk(compression.id(), out.toByteArray(), timestamp);
    }

    public RootTag decode(NbtLimits limits) throws IOException {
        Objects.requireNonNull(limits, "limits");
        ChunkCompression compression = compression()
                .orElseThrow(() -> new UnsupportedCompressionException(compressionId));
        if (compression == ChunkCompression.LZ4) {
            try {
                return NbtIo.read(new Lz4BlockInputStream(payload, 0, payload.length), NbtCompression.NONE, limits);
            } catch (Lz4FormatException e) {
                throw new RegionFormatException("corrupt LZ4 chunk payload", e);
            }
        }
        NbtCompression codec = switch (compression) {
            case GZIP -> NbtCompression.GZIP;
            case ZLIB -> NbtCompression.ZLIB;
            case NONE -> NbtCompression.NONE;
            case LZ4, CUSTOM -> throw new UnsupportedCompressionException(compressionId);
        };
        return NbtIo.read(new ByteArrayInputStream(payload), codec, limits);
    }

    public int compressionId() {
        return compressionId;
    }

    public Optional<ChunkCompression> compression() {
        return ChunkCompression.fromId(compressionId);
    }

    public int timestamp() {
        return timestamp;
    }

    public RawChunk withTimestamp(int newTimestamp) {
        return new RawChunk(compressionId, payload, newTimestamp);
    }

    public int payloadLength() {
        return payload.length;
    }

    public byte[] payload() {
        return payload.clone();
    }

    byte[] rawPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RawChunk other
                && compressionId == other.compressionId
                && timestamp == other.timestamp
                && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * compressionId + timestamp) + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "RawChunk[compression=" + compressionId + ", bytes=" + payload.length + ", timestamp=" + timestamp + "]";
    }
}
