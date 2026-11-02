package space.qu4nt.thirdparty.mcpainter.anvil.internal;

import java.util.Objects;

public final class XxHash32 {

    private static final int PRIME1 = 0x9E3779B1;
    private static final int PRIME2 = 0x85EBCA77;
    private static final int PRIME3 = 0xC2B2AE3D;
    private static final int PRIME4 = 0x27D4EB2F;
    private static final int PRIME5 = 0x165667B1;

    private XxHash32() {
    }

    public static int hash(byte[] data, int offset, int length, int seed) {
        Objects.checkFromIndexSize(offset, length, data.length);
        int end = offset + length;
        int p = offset;
        int h;
        if (length >= 16) {
            int v1 = seed + PRIME1 + PRIME2;
            int v2 = seed + PRIME2;
            int v3 = seed;
            int v4 = seed - PRIME1;
            int limit = end - 16;
            do {
                v1 = round(v1, intLE(data, p));
                v2 = round(v2, intLE(data, p + 4));
                v3 = round(v3, intLE(data, p + 8));
                v4 = round(v4, intLE(data, p + 12));
                p += 16;
            } while (p <= limit);
            h = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7)
                    + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
        } else {
            h = seed + PRIME5;
        }
        h += length;
        while (p + 4 <= end) {
            h += intLE(data, p) * PRIME3;
            h = Integer.rotateLeft(h, 17) * PRIME4;
            p += 4;
        }
        while (p < end) {
            h += (data[p] & 0xFF) * PRIME5;
            h = Integer.rotateLeft(h, 11) * PRIME1;
            p++;
        }
        h ^= h >>> 15;
        h *= PRIME2;
        h ^= h >>> 13;
        h *= PRIME3;
        h ^= h >>> 16;
        return h;
    }

    private static int round(int acc, int lane) {
        return Integer.rotateLeft(acc + lane * PRIME2, 13) * PRIME1;
    }

    private static int intLE(byte[] data, int p) {
        return (data[p] & 0xFF) | (data[p + 1] & 0xFF) << 8 | (data[p + 2] & 0xFF) << 16 | (data[p + 3] & 0xFF) << 24;
    }
}
