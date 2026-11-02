package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionPos;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;

public final class MinecraftWorld {

    public static final String DIMENSIONS = "dimensions";
    public static final String REGION = "region";
    public static final String ENTITIES = "entities";
    public static final String POI = "poi";

    private static final int MAX_DIMENSION_PATH_DEPTH = 8;

    private final Path root;
    private final LevelData level;

    private MinecraftWorld(Path root, LevelData level) {
        this.root = root;
        this.level = level;
    }

    public static MinecraftWorld open(Path directory) throws IOException {
        return open(directory, NbtLimits.DEFAULT);
    }

    public static MinecraftWorld open(Path directory, NbtLimits limits) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Path root = directory.toRealPath();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorldFormatException("not a directory: " + directory);
        }
        LevelData level = LevelData.read(root.resolve(LevelData.FILE_NAME), limits);
        DataVersions.requireSupported(level.dataVersion());
        return new MinecraftWorld(root, level);
    }

    public Path root() {
        return root;
    }

    public LevelData level() {
        return level;
    }

    public boolean usesDimensionsFolder() {
        return level.dataVersion() >= DataVersions.DIMENSIONS_FOLDER;
    }

    public Path dimensionDirectory(Dimension dimension) {
        Objects.requireNonNull(dimension, "dimension");
        Path resolved;
        if (!usesDimensionsFolder() && dimension.equals(Dimension.OVERWORLD)) {
            resolved = root;
        } else if (!usesDimensionsFolder() && dimension.equals(Dimension.THE_NETHER)) {
            resolved = root.resolve("DIM-1");
        } else if (!usesDimensionsFolder() && dimension.equals(Dimension.THE_END)) {
            resolved = root.resolve("DIM1");
        } else {
            resolved = root.resolve(DIMENSIONS).resolve(dimension.id().namespace());
            for (String segment : dimension.id().path().split("/")) {
                resolved = resolved.resolve(segment);
            }
        }
        Path normalized = resolved.normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalStateException("dimension path escapes world root: " + dimension);
        }
        return normalized;
    }

    public Path regionDirectory(Dimension dimension) {
        return dimensionDirectory(dimension).resolve(REGION);
    }

    public Path entitiesDirectory(Dimension dimension) {
        return dimensionDirectory(dimension).resolve(ENTITIES);
    }

    public Path poiDirectory(Dimension dimension) {
        return dimensionDirectory(dimension).resolve(POI);
    }

    public Path regionPath(Dimension dimension, RegionPos pos) {
        return regionDirectory(dimension).resolve(pos.fileName());
    }

    public List<RegionPos> listRegions(Dimension dimension) throws IOException {
        Path directory = regionDirectory(dimension);
        requireRealDirectoryChain(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<RegionPos> regions = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                Optional<RegionPos> pos = RegionPos.parseFileName(entry.getFileName().toString());
                if (pos.isPresent() && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    regions.add(pos.get());
                }
            }
        }
        regions.sort(Comparator.comparingInt(RegionPos::x).thenComparingInt(RegionPos::z));
        return regions;
    }

    public List<Dimension> listDimensions() throws IOException {
        SequencedSet<Dimension> found = new LinkedHashSet<>();
        if (!usesDimensionsFolder()) {
            for (Dimension vanilla : List.of(Dimension.OVERWORLD, Dimension.THE_NETHER, Dimension.THE_END)) {
                if (Files.isDirectory(regionDirectory(vanilla), LinkOption.NOFOLLOW_LINKS)) {
                    found.add(vanilla);
                }
            }
        }
        Path dimensions = root.resolve(DIMENSIONS);
        if (Files.isDirectory(dimensions, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(dimensions)) {
                for (Path namespace : namespaces) {
                    if (Files.isDirectory(namespace, LinkOption.NOFOLLOW_LINKS)) {
                        scanDimensionPaths(namespace.getFileName().toString(), namespace, "", 1, found);
                    }
                }
            }
        }
        List<Dimension> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparing(Dimension::toString));
        return sorted;
    }

    private void scanDimensionPaths(String namespace, Path directory, String path, int depth,
            SequencedSet<Dimension> found) throws IOException {
        if (depth > MAX_DIMENSION_PATH_DEPTH) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String name = child.getFileName().toString();
                String childPath = path.isEmpty() ? name : path + "/" + name;
                if (Files.isDirectory(child.resolve(REGION), LinkOption.NOFOLLOW_LINKS)) {
                    parseDimension(namespace, childPath).ifPresent(found::add);
                }
                if (!name.equals(REGION) && !name.equals(ENTITIES) && !name.equals(POI) && !name.equals("data")) {
                    scanDimensionPaths(namespace, child, childPath, depth + 1, found);
                }
            }
        }
    }

    private static Optional<Dimension> parseDimension(String namespace, String path) {
        try {
            return Optional.of(new Dimension(new ResourceLocation(namespace, path)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isInUse() throws IOException {
        if (!Files.exists(root.resolve(WorldLock.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        WorldLock probe;
        try {
            probe = WorldLock.acquire(root);
        } catch (WorldInUseException e) {
            return true;
        }
        probe.close();
        return false;
    }

    public WorldLock lock() throws IOException {
        return WorldLock.acquire(root);
    }

    private void requireRealDirectoryChain(Path directory) throws IOException {
        Path relative = root.relativize(directory);
        Path cursor = root;
        for (Path segment : relative) {
            cursor = cursor.resolve(segment);
            AtomicFiles.requireNotSymlink(cursor);
        }
    }
}
