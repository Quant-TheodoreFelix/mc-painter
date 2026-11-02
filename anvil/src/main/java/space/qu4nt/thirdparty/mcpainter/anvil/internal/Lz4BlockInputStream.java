package space.qu4nt.thirdparty.mcpainter.anvil.internal;

import java.io.InputStream;
import java.util.Objects;

public final class Lz4BlockInputStream extends InputStream {

    private static final byte[] MAGIC = {'L', 'Z', '4', 'B', 'l', 'o', 'c', 'k'};
    private static final int HEADER_LENGTH = MAGIC.length + 1 + 4 + 4 + 4;
    private static final int COMPRESSION_LEVEL_BASE = 10;
    private static final int METHOD_RAW = 0x10;
    private static final int METHOD_LZ4 = 0x20;
    private static final int CHECKSUM_SEED = 0x9747B28C;
    private static final int CHECKSUM_MASK = 0x0FFFFFFF;
    private static final int MAX_COMPRESSION_RATIO = 255;
    private static final int MIN_MATCH = 4;

    private final byte[] source;
    private final int end;
    private int position;
    private byte[] block = new byte[0];
    private int blockLength;
    private int blockPosition;
    private boolean finished;

    public Lz4BlockInputStream(byte[] source, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, source.length);
        this.source = source;
        this.position = offset;
        this.end = offset + length;
    }

    @Override
    public int read() throws Lz4FormatException {
        if (!ensureData()) {
            return -1;
        }
        return block[blockPosition++] & 0xFF;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws Lz4FormatException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        if (!ensureData()) {
            return -1;
        }
        int count = Math.min(length, blockLength - blockPosition);
        System.arraycopy(block, blockPosition, buffer, offset, count);
        blockPosition += count;
        return count;
    }

    @Override
    public int available() {
        return blockLength - blockPosition;
    }

    private boolean ensureData() throws Lz4FormatException {
        while (blockPosition >= blockLength) {
            if (finished) {
                return false;
            }
            readBlock();
        }
        return true;
    }

    private void readBlock() throws Lz4FormatException {
        if (end - position < HEADER_LENGTH) {
            throw new Lz4FormatException("LZ4 stream ended before its end mark");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (source[position + i] != MAGIC[i]) {
                throw new Lz4FormatException("bad LZ4 block magic");
            }
        }
        int token = source[position + MAGIC.length] & 0xFF;
        int method = token & 0xF0;
        int maxBlockSize = 1 << (COMPRESSION_LEVEL_BASE + (token & 0x0F));
        int compressedLength = intLE(position + MAGIC.length + 1);
        int originalLength = intLE(position + MAGIC.length + 5);
        int checksum = intLE(position + MAGIC.length + 9);
        position += HEADER_LENGTH;

        if (method != METHOD_RAW && method != METHOD_LZ4) {
            throw new Lz4FormatException("unknown LZ4 block method " + method);
        }
        if (originalLength < 0 || compressedLength < 0 || originalLength > maxBlockSize
                || (originalLength == 0) != (compressedLength == 0)) {
            throw new Lz4FormatException("invalid LZ4 block lengths");
        }
        if (originalLength == 0) {
            if (checksum != 0) {
                throw new Lz4FormatException("LZ4 end mark has a non-zero checksum");
            }
            finished = true;
            blockLength = 0;
            blockPosition = 0;
            return;
        }
        if (method == METHOD_RAW && originalLength != compressedLength) {
            throw new Lz4FormatException("raw LZ4 block length mismatch");
        }
        if (method == METHOD_LZ4 && (compressedLength >= originalLength
                || compressedLength > maxCompressedLength(originalLength)
                || originalLength > (long) compressedLength * MAX_COMPRESSION_RATIO)) {
            throw new Lz4FormatException("implausible LZ4 compression ratio");
        }
        if (end - position < compressedLength) {
            throw new Lz4FormatException("LZ4 block truncated");
        }
        if (block.length < originalLength) {
            block = new byte[originalLength];
        }
        if (method == METHOD_RAW) {
            System.arraycopy(source, position, block, 0, originalLength);
        } else {
            decompress(source, position, compressedLength, block, originalLength);
        }
        position += compressedLength;
        if ((XxHash32.hash(block, 0, originalLength, CHECKSUM_SEED) & CHECKSUM_MASK) != checksum) {
            throw new Lz4FormatException("LZ4 block checksum mismatch");
        }
        blockLength = originalLength;
        blockPosition = 0;
    }

    static void decompress(byte[] in, int inOffset, int inLength, byte[] out, int outLength)
            throws Lz4FormatException {
        int ip = inOffset;
        int inEnd = inOffset + inLength;
        int op = 0;
        while (true) {
            if (ip >= inEnd) {
                throw new Lz4FormatException("LZ4 sequence truncated");
            }
            int token = in[ip++] & 0xFF;
            int literals = token >>> 4;
            if (literals == 15) {
                int[] cursor = {ip};
                literals += readLength(in, cursor, inEnd);
                ip = cursor[0];
            }
            if (literals > inEnd - ip || literals > outLength - op) {
                throw new Lz4FormatException("LZ4 literals exceed block bounds");
            }
            System.arraycopy(in, ip, out, op, literals);
            ip += literals;
            op += literals;
            if (ip == inEnd) {
                break;
            }
            if (inEnd - ip < 2) {
                throw new Lz4FormatException("LZ4 match offset truncated");
            }
            int offset = (in[ip] & 0xFF) | (in[ip + 1] & 0xFF) << 8;
            ip += 2;
            if (offset == 0 || offset > op) {
                throw new Lz4FormatException("LZ4 match offset out of range");
            }
            int matchLength = token & 0x0F;
            if (matchLength == 15) {
                int[] cursor = {ip};
                matchLength += readLength(in, cursor, inEnd);
                ip = cursor[0];
            }
            matchLength += MIN_MATCH;
            if (matchLength > outLength - op) {
                throw new Lz4FormatException("LZ4 match exceeds block bounds");
            }
            int from = op - offset;
            for (int i = 0; i < matchLength; i++) {
                out[op++] = out[from++];
            }
        }
        if (op != outLength) {
            throw new Lz4FormatException("LZ4 block decoded to " + op + " bytes, expected " + outLength);
        }
    }

    private static int readLength(byte[] in, int[] cursor, int inEnd) throws Lz4FormatException {
        int total = 0;
        int b;
        do {
            if (cursor[0] >= inEnd) {
                throw new Lz4FormatException("LZ4 length truncated");
            }
            b = in[cursor[0]++] & 0xFF;
            total += b;
            if (total < 0 || total > Integer.MAX_VALUE / 2) {
                throw new Lz4FormatException("LZ4 length overflow");
            }
        } while (b == 255);
        return total;
    }

    private static int maxCompressedLength(int length) {
        return length + length / 255 + 16;
    }

    private int intLE(int p) {
        return (source[p] & 0xFF) | (source[p + 1] & 0xFF) << 8 | (source[p + 2] & 0xFF) << 16
                | (source[p + 3] & 0xFF) << 24;
    }
}
