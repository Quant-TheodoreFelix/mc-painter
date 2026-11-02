package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.io.Serial;

public final class JsonFormatException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public JsonFormatException(String message) {
        super(message);
    }
}
