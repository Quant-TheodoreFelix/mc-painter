package space.qu4nt.thirdparty.mcpainter.anvil.internal;

import java.io.IOException;
import java.io.Serial;

public final class Lz4FormatException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public Lz4FormatException(String message) {
        super(message);
    }
}
