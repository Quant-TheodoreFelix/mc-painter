package space.qu4nt.thirdparty.mcpainter.anvil.region;

import java.io.IOException;
import java.io.Serial;

public class RegionFormatException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RegionFormatException(String message) {
        super(message);
    }

    public RegionFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
