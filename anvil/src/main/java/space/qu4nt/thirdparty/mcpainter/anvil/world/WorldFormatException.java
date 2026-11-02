package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.io.IOException;
import java.io.Serial;

public class WorldFormatException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WorldFormatException(String message) {
        super(message);
    }

    public WorldFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
