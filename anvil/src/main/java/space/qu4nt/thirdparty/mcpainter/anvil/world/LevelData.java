package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtCompression;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtIo;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;

public record LevelData(int dataVersion, String levelName, String versionName, RootTag root) {

    public static final String FILE_NAME = "level.dat";

    public LevelData {
        Objects.requireNonNull(levelName, "levelName");
        Objects.requireNonNull(versionName, "versionName");
        Objects.requireNonNull(root, "root");
    }

    public static LevelData read(Path file) throws IOException {
        return read(file, NbtLimits.DEFAULT);
    }

    public static LevelData read(Path file, NbtLimits limits) throws IOException {
        AtomicFiles.requireNotSymlink(file);
        RootTag root;
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            root = NbtIo.read(in, NbtCompression.GZIP, limits);
        } catch (NoSuchFileException e) {
            throw new WorldFormatException("missing " + FILE_NAME + ": " + file, e);
        }
        return fromRoot(root);
    }

    public static LevelData fromRoot(RootTag root) throws WorldFormatException {
        CompoundTag data = root.tag().get("Data", CompoundTag.class)
                .orElseThrow(() -> new WorldFormatException(FILE_NAME + " has no Data compound"));
        int dataVersion = data.get("DataVersion", IntTag.class)
                .orElseThrow(() -> new WorldFormatException(FILE_NAME + " has no DataVersion"))
                .value();
        String levelName = data.get("LevelName", StringTag.class).map(StringTag::value).orElse("");
        String versionName = data.get("Version", CompoundTag.class)
                .flatMap(version -> version.get("Name", StringTag.class))
                .map(StringTag::value)
                .orElse("");
        return new LevelData(dataVersion, levelName, versionName, root);
    }
}
