package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.io.Serial;

public final class ProjectFormatException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProjectFormatException(String message) {
        super(message);
    }

    public ProjectFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
