package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.util.Map;
import java.util.Optional;

public final class DataVersions {

    public static final int V1_18 = 2860;
    public static final int V1_19 = 3105;
    public static final int V1_20 = 3463;
    public static final int V1_21_4 = 4189;
    public static final int V26_1_SNAPSHOT_6 = 4774;
    public static final int V26_2 = 4903;
    public static final int V26_3_SNAPSHOT_7 = 5009;
    public static final int V26_3 = 5023;

    public static final int MIN_SUPPORTED = V1_18;
    public static final int MAX_SUPPORTED = V26_3;
    public static final int DIMENSIONS_FOLDER = V26_1_SNAPSHOT_6;
    public static final int PALETTE_ID_KEYS = V26_3_SNAPSHOT_7;

    private static final Map<Integer, String> RELEASES = Map.of(V1_18, "1.18", V1_19, "1.19", V1_20, "1.20",
            V1_21_4, "1.21.4", V26_2, "26.2", V26_3, "26.3");

    private DataVersions() {
    }

    public static Optional<String> releaseName(int dataVersion) {
        return Optional.ofNullable(RELEASES.get(dataVersion));
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
