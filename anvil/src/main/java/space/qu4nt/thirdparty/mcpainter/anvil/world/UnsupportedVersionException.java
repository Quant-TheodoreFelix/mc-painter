package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.io.Serial;

public final class UnsupportedVersionException extends WorldFormatException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int dataVersion;

    public UnsupportedVersionException(int dataVersion) {
        super("unsupported data version " + dataVersion + " (supported "
                + DataVersions.MIN_SUPPORTED + " to " + DataVersions.MAX_SUPPORTED + ")");
        this.dataVersion = dataVersion;
    }

    public int dataVersion() {
        return dataVersion;
    }
}
