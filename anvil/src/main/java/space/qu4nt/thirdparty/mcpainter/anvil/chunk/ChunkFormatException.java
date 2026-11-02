package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.io.IOException;
import java.io.Serial;

public final class ChunkFormatException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChunkFormatException(String message) {
        super(message);
    }

    public ChunkFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
