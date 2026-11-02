package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;

public final class JsonFileStore {

    public static final long MAX_FILE_BYTES = 512L * 1024;
    public static final int MAX_FILES = 500;

    private static final Pattern NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json");

    private final Path directory;

    public JsonFileStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public Path directory() {
        return directory;
    }

    public static boolean isStoreName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public List<String> list() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (isStoreName(name) && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    names.add(name);
                }
            }
        }
        Collections.sort(names);
        return names.size() > MAX_FILES ? List.copyOf(names.subList(0, MAX_FILES)) : List.copyOf(names);
    }

    public String read(String name) throws IOException {
        return readFile(resolve(name));
    }

    public String write(String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("JSON file exceeds " + MAX_FILE_BYTES + " bytes");
        }
        Files.createDirectories(directory);
        String name = UUID.randomUUID() + ".json";
        AtomicFiles.write(directory.resolve(name), channel -> {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        });
        return name;
    }

    public void delete(String name) throws IOException {
        Path file = resolve(name);
        AtomicFiles.requireNotSymlink(file);
        Files.deleteIfExists(file);
    }

    public static String readFile(Path file) throws IOException {
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("not a regular file: " + file);
        }
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = in.readNBytes((int) MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("JSON file exceeds " + MAX_FILE_BYTES + " bytes");
        }
        try {
            CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (CharacterCodingException e) {
            throw new JsonFormatException("JSON file is not valid UTF-8");
        }
    }

    private Path resolve(String name) throws IOException {
        if (!isStoreName(name)) {
            throw new IOException("invalid store file name");
        }
        return directory.resolve(name);
    }
}
