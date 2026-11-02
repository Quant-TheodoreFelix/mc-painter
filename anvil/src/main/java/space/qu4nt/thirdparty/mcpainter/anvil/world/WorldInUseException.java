package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.io.Serial;

public final class WorldInUseException extends WorldFormatException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WorldInUseException(String message) {
        super(message);
    }
}
