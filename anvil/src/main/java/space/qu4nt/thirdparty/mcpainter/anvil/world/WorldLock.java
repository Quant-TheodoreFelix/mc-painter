package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;

public final class WorldLock implements AutoCloseable {

    public static final String FILE_NAME = "session.lock";

    private final FileChannel channel;
    private final FileLock lock;

    private WorldLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    static WorldLock acquire(Path worldRoot) throws IOException {
        Path file = worldRoot.resolve(FILE_NAME);
        AtomicFiles.requireNotSymlink(file);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new WorldInUseException("world is open in another program: " + worldRoot);
            }
            return new WorldLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            channel.close();
            throw new WorldInUseException("world is already locked by this program: " + worldRoot);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
