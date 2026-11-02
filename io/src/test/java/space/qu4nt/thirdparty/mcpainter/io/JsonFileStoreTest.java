package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileStoreTest {

    @TempDir
    Path dir;

    @Test
    void writesListsReadsAndDeletes() throws IOException {
        JsonFileStore store = new JsonFileStore(dir.resolve("brushes").resolve("presets"));
        assertEquals(List.of(), store.list());
        String name = store.write("{\"name\": \"한글\"}\n");
        assertTrue(JsonFileStore.isStoreName(name));
        assertEquals(List.of(name), store.list());
        assertEquals("{\"name\": \"한글\"}\n", store.read(name));
        store.delete(name);
        assertEquals(List.of(), store.list());
    }

    @Test
    void ignoresForeignEntriesAndRefusesLinks() throws IOException {
        JsonFileStore store = new JsonFileStore(dir);
        String real = store.write("{}");
        Files.writeString(dir.resolve("notes.json"), "{}");
        Files.writeString(dir.resolve("ABCDEF00-0000-0000-0000-000000000000.json"), "{}");
        Files.createDirectory(dir.resolve("00000000-0000-0000-0000-000000000001.json"));
        String linkName = "00000000-0000-0000-0000-000000000002.json";
        Files.createSymbolicLink(dir.resolve(linkName), dir.resolve(real));
        assertEquals(List.of(real), store.list());
        assertThrows(IOException.class, () -> store.read(linkName));
        assertThrows(IOException.class, () -> store.read("../" + real));
        assertThrows(IOException.class, () -> store.read("notes.json"));
        assertThrows(IOException.class, () -> store.delete("../x.json"));
        assertThrows(IOException.class, () -> store.delete(linkName));
        assertTrue(Files.exists(dir.resolve(real)));
    }

    @Test
    void rejectsOversizedAndMalformedFiles() throws IOException {
        JsonFileStore store = new JsonFileStore(dir);
        assertThrows(IOException.class, () -> store.write("x".repeat((int) JsonFileStore.MAX_FILE_BYTES + 1)));
        String big = "00000000-0000-0000-0000-00000000000a.json";
        Files.write(dir.resolve(big), new byte[(int) JsonFileStore.MAX_FILE_BYTES + 1]);
        assertThrows(IOException.class, () -> store.read(big));
        String bad = "00000000-0000-0000-0000-00000000000b.json";
        Files.write(dir.resolve(bad), new byte[] {'"', (byte) 0xC3, (byte) 0x28, '"'});
        assertThrows(JsonFormatException.class, () -> store.read(bad));
        assertFalse(JsonFileStore.isStoreName(null));
    }

    @Test
    void resolvesConfigDirectoryPerPlatform() {
        assertEquals(Path.of("/Users/u/Library/Application Support/mc-painter"),
                AppDirectories.config("Mac OS X", "/Users/u", null, null));
        assertEquals(Path.of("/roaming", "mc-painter"), AppDirectories.config("Windows 11", "/home/u", "/roaming", null));
        assertEquals(Path.of("/home/u/AppData/Roaming/mc-painter"),
                AppDirectories.config("Windows 11", "/home/u", "relative", null));
        assertEquals(Path.of("/home/u/.config/mc-painter"), AppDirectories.config("Linux", "/home/u", null, null));
        assertEquals(Path.of("/xdg/mc-painter"), AppDirectories.config("Linux", "/home/u", null, "/xdg"));
        assertEquals(Path.of("/home/u/.config/mc-painter"), AppDirectories.config("Linux", "/home/u", null, "xdg"));
        assertEquals(Path.of("/home/u/.config/mc-painter"), AppDirectories.config("Linux", "/home/u", null, " "));
        assertEquals(Path.of("/home/u/.mc-painter"), AppDirectories.config("Linux", "/home/u", null, "/bad\0"));
    }
}
