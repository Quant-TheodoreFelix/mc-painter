package space.qu4nt.thirdparty.mcpainter.anvil.region;

import java.io.Serial;

public final class UnsupportedCompressionException extends RegionFormatException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int compressionId;

    public UnsupportedCompressionException(int compressionId) {
        super("unsupported chunk compression id " + compressionId);
        this.compressionId = compressionId;
    }

    public int compressionId() {
        return compressionId;
    }
}
