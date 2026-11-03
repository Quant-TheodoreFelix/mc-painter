package space.qu4nt.thirdparty.mcpainter.io;

final class GenHash {

    static final long DEEPSLATE = 0x6465_6570_736C_6174L;

    private GenHash() {
    }

    static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }

    static long hash(long seed, long salt, int x, int y, int z) {
        long h = mix(seed ^ salt);
        h = mix(h ^ (x * 0x9E37_79B9_7F4A_7C15L));
        h = mix(h ^ (y * 0xC2B2_AE3D_27D4_EB4FL));
        return mix(h ^ (z * 0x1656_67B1_9E37_79F9L));
    }

    static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
