package space.qu4nt.thirdparty.mcpainter.anvil.internal;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;

public final class AtomicFiles {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AtomicFiles() {
    }

    @FunctionalInterface
    public interface ChannelWriter {
        void write(FileChannel channel) throws IOException;
    }

    public static void write(Path target, ChannelWriter writer) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path directory = absolute.getParent();
        if (directory == null) {
            throw new IOException("target has no parent directory: " + target);
        }
        requireNotSymlink(absolute);
        Path temp = createTemp(directory, absolute.getFileName().toString());
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                writer.write(channel);
                channel.force(true);
            }
            copyPermissions(absolute, temp);
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
            syncDirectory(directory);
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static void deleteIfExists(Path target) throws IOException {
        requireNotSymlink(target);
        Files.deleteIfExists(target);
    }

    public static void requireNotSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("refusing to follow symbolic link: " + path);
        }
    }

    private static Path createTemp(Path directory, String name) throws IOException {
        for (int attempt = 0; attempt < 16; attempt++) {
            byte[] suffix = new byte[8];
            RANDOM.nextBytes(suffix);
            Path temp = directory.resolve("." + name + "." + HexFormat.of().formatHex(suffix) + ".tmp");
            try {
                Files.newByteChannel(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS).close();
                return temp;
            } catch (FileAlreadyExistsException e) {
                continue;
            }
        }
        throw new IOException("could not create a unique temporary file in " + directory);
    }

    private static void copyPermissions(Path source, Path target) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        PosixFileAttributeView view = Files.getFileAttributeView(source, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            return;
        }
        Set<PosixFilePermission> permissions = view.readAttributes().permissions();
        Files.setPosixFilePermissions(target, permissions);
    }

    private static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException e) {
            return;
        }
    }
}
