package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Path;

public final class WorkspaceLockedException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WorkspaceLockedException(Path directory) {
        super("workspace is in use: " + directory);
    }
}
