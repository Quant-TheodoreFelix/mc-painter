package space.qu4nt.thirdparty.mcpainter.nbt;

import java.io.Serial;

public final class NbtLimitException extends NbtFormatException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NbtLimitException(String message) {
        super(message);
    }
}
