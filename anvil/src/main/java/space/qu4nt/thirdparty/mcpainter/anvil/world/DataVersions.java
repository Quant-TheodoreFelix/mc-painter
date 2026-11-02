package space.qu4nt.thirdparty.mcpainter.anvil.world;

public final class DataVersions {

    public static final int V1_18 = 2861;
    public static final int V26_1_SNAPSHOT_6 = 4774;
    public static final int V26_3_SNAPSHOT_7 = 5009;
    public static final int V26_3 = 5023;

    public static final int MIN_SUPPORTED = V1_18;
    public static final int MAX_SUPPORTED = V26_3;
    public static final int DIMENSIONS_FOLDER = V26_1_SNAPSHOT_6;
    public static final int PALETTE_ID_KEYS = V26_3_SNAPSHOT_7;

    private DataVersions() {
    }

    public static boolean isSupported(int dataVersion) {
        return dataVersion >= MIN_SUPPORTED && dataVersion <= MAX_SUPPORTED;
    }

    public static void requireSupported(int dataVersion) throws UnsupportedVersionException {
        if (!isSupported(dataVersion)) {
            throw new UnsupportedVersionException(dataVersion);
        }
    }
}
