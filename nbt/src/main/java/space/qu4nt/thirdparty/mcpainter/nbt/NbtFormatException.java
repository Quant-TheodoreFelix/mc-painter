package space.qu4nt.thirdparty.mcpainter.nbt;

import java.io.IOException;
import java.io.Serial;

public class NbtFormatException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NbtFormatException(String message) {
        super(message);
    }

    public NbtFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
