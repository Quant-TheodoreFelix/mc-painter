package space.qu4nt.thirdparty.mcpainter.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class Workspace implements Closeable {

    public static final String FOLDER = "workspaces";
    public static final String FORMAT = "mc-painter-workspace";
    public static final int FORMAT_VERSION = 1;
    public static final int THUMB_SIDE = 8;
    public static final int THUMB_PIXELS = THUMB_SIDE * THUMB_SIDE;
    public static final int MAX_NAME_LENGTH = 256;
    static final int GROUP = 32;
    static final String HEADER = "workspace.dat";
    static final String LOCK = "workspace.lock";
    static final String TILES = "tiles";
    static final String INDEX = "index.dat";

    private static final Pattern ID = Pattern.compile("[0-9a-f]{16}");
    private static final Pattern GROUP_NAME = Pattern.compile("g\\.(-?\\d{1,6})\\.(-?\\d{1,6})");
    private static final NbtLimits INDEX_LIMITS = new NbtLimits(4, 4L * 1024 * 1024);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Entry(TilePos pos, boolean pending, int[] thumb) {

        public Entry {
            Objects.requireNonNull(pos, "pos");
            if (thumb.length != THUMB_PIXELS) {
                throw new IllegalArgumentException("thumbnail must have " + THUMB_PIXELS + " pixels");
            }
            thumb = thumb.clone();
        }

        @Override
        public int[] thumb() {
            return thumb.clone();
        }

        int pixel(int index) {
            return thumb[index];
        }
    }

    public record State(String name, Optional<Path> project, boolean unsaved, Optional<TileRect> area,
            Optional<Path> dumpTarget, long created, long modified) {

        public State {
            name = name == null ? "" : name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
            Objects.requireNonNull(project, "project");
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(dumpTarget, "dumpTarget");
        }

        public State withProject(Path value, boolean nowUnsaved) {
            return new State(name, Optional.of(value), nowUnsaved, area, dumpTarget, created, modified);
        }

        public State withUnsaved(boolean value) {
            return new State(name, project, value, area, dumpTarget, created, modified);
        }

        public State withArea(Optional<TileRect> value) {
            return new State(name, project, unsaved, value, dumpTarget, created, modified);
        }

        public State withDumpTarget(Path value) {
            return new State(name, project, unsaved, area, Optional.of(value), created, modified);
        }

        public State withName(String value) {
            return new State(value, project, unsaved, area, dumpTarget, created, modified);
        }
    }

    public record Summary(Path directory, State state, WorldSpec spec, int tiles, boolean locked) {
    }

    public record Write(TilePos pos, Tile tile, int[] thumb, boolean pending) {

        public Write {
            Objects.requireNonNull(pos, "pos");
            if (tile != null && (thumb == null || thumb.length != THUMB_PIXELS)) {
                throw new IllegalArgumentException("stored tiles need a thumbnail");
            }
        }

        public static Write remove(TilePos pos) {
            return new Write(pos, null, null, false);
        }
    }

    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Map<TilePos, Entry> entries;
    private WorldSpec spec;
    private State state;
    private boolean closed;

    private Workspace(Path directory, FileChannel lockChannel, FileLock lock, WorldSpec spec, State state,
            Map<TilePos, Entry> entries) {
        this.directory = directory;
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.spec = spec;
        this.state = state;
        this.entries = entries;
    }

    public static Workspace create(Path root, WorldSpec spec, String name) throws IOException {
        Objects.requireNonNull(spec, "spec");
        Files.createDirectories(root);
        AtomicFiles.requireNotSymlink(root);
        for (int attempt = 0; attempt < 16; attempt++) {
            byte[] id = new byte[8];
            RANDOM.nextBytes(id);
            Path directory = root.resolve(HexFormat.of().formatHex(id));
            try {
                Files.createDirectory(directory);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                continue;
            }
            Files.createDirectory(directory.resolve(TILES));
            long now = System.currentTimeMillis();
            State state = new State(name, Optional.empty(), false, Optional.empty(), Optional.empty(), now, now);
            writeHeader(directory, spec, state, 0);
            return open(directory);
        }
        throw new IOException("could not create a workspace folder in " + root);
    }

    public static Workspace open(Path directory) throws IOException {
        requireWorkspaceDirectory(directory);
        FileChannel channel = FileChannel.open(directory.resolve(LOCK), Set.of(StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        }
        if (lock == null) {
            channel.close();
            throw new WorkspaceLockedException(directory);
        }
        try {
            Header header = readHeader(directory);
            Map<TilePos, Entry> entries = readIndexes(directory, header.spec().bounds());
            return new Workspace(directory, channel, lock, header.spec(), header.state(), entries);
        } catch (IOException | RuntimeException e) {
            lock.release();
            channel.close();
            throw e;
        }
    }

    public static List<Summary> list(Path root) throws IOException {
        List<Summary> found = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return found;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                Path name = child.getFileName();
                if (name == null || !ID.matcher(name.toString()).matches()
                        || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    Header header = readHeader(child);
                    found.add(new Summary(child, header.state(), header.spec(), header.tiles(), locked(child)));
                } catch (IOException | RuntimeException e) {
                    continue;
                }
            }
        }
        found.sort((a, b) -> Long.compare(b.state().modified(), a.state().modified()));
        return found;
    }

    public static void delete(Path directory) throws IOException {
        requireWorkspaceDirectory(directory);
        if (locked(directory)) {
            throw new WorkspaceLockedException(directory);
        }
        deleteTree(directory);
    }

    public Path directory() {
        return directory;
    }

    public WorldSpec spec() {
        return spec;
    }

    public State state() {
        return state;
    }

    public int tileCount() {
        return entries.size();
    }

    public boolean stored(TilePos pos) {
        return entries.containsKey(pos);
    }

    public Optional<Entry> entry(TilePos pos) {
        return Optional.ofNullable(entries.get(pos));
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public int pendingCount() {
        int pending = 0;
        for (Entry entry : entries.values()) {
            if (entry.pending()) {
                pending++;
            }
        }
        return pending;
    }

    public void update(WorldSpec nextSpec, State nextState) throws IOException {
        requireOpen();
        if (!nextSpec.bounds().equals(spec.bounds()) || !nextSpec.base().equals(spec.base())) {
            throw new IllegalArgumentException("bounds and base cannot change");
        }
        State stamped = new State(nextState.name(), nextState.project(), nextState.unsaved(), nextState.area(),
                nextState.dumpTarget(), state.created(), System.currentTimeMillis());
        writeHeader(directory, nextSpec, stamped, entries.size());
        spec = nextSpec;
        state = stamped;
    }

    public byte[] readRaw(TilePos pos) throws IOException {
        requireOpen();
        if (!entries.containsKey(pos)) {
            throw new IOException("tile " + pos + " is not stored");
        }
        Path file = tileFile(pos);
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectFormatException("missing tile file for " + pos);
        }
        if (Files.size(file) > TileCodec.MAX_RECORD_BYTES) {
            throw new ProjectFormatException("tile file too large for " + pos);
        }
        return Files.readAllBytes(file);
    }

    public void loadInto(WorldModel.Builder builder, TilePos pos) throws IOException {
        CompoundTag tag = TileCodec.gunzip(readRaw(pos), TileCodec.TILE_LIMITS);
        if (!TileCodec.position(tag).equals(pos)) {
            throw new ProjectFormatException("tile file for " + pos + " holds another tile");
        }
        TileCodec.addTo(builder, tag, List.of(TerrainType.values()), ProjectFile.FORMAT_VERSION);
    }

    public void write(List<Write> writes, boolean durable) throws IOException {
        requireOpen();
        Set<TilePos> groups = new HashSet<>();
        for (Write write : writes) {
            if (!spec.bounds().contains(write.pos())) {
                throw new IllegalArgumentException("tile " + write.pos() + " outside bounds");
            }
            TilePos group = group(write.pos());
            groups.add(group);
            Path file = tileFile(write.pos());
            if (write.tile() == null) {
                AtomicFiles.deleteIfExists(file);
                entries.remove(write.pos());
                continue;
            }
            Files.createDirectories(file.getParent());
            byte[] bytes = TileCodec.gzip(TileCodec.encode(write.pos(), write.tile()));
            AtomicFiles.write(file, channel -> writeFully(channel, bytes), durable);
            entries.put(write.pos(), new Entry(write.pos(), write.pending(), write.thumb()));
        }
        for (TilePos group : groups) {
            writeIndex(group, durable);
        }
        if (!writes.isEmpty()) {
            update(spec, state);
        }
    }

    public void markDumped(Collection<TilePos> positions) throws IOException {
        requireOpen();
        Set<TilePos> groups = new HashSet<>();
        for (TilePos pos : positions) {
            Entry entry = entries.get(pos);
            if (entry != null && entry.pending()) {
                entries.put(pos, new Entry(pos, false, entry.thumb));
                groups.add(group(pos));
            }
        }
        for (TilePos group : groups) {
            writeIndex(group, true);
        }
    }

    public void deleteAndClose() throws IOException {
        close();
        deleteTree(directory);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            lock.release();
        } finally {
            lockChannel.close();
        }
    }

    static TilePos group(TilePos pos) {
        return new TilePos(Math.floorDiv(pos.x(), GROUP), Math.floorDiv(pos.z(), GROUP));
    }

    private Path groupDirectory(TilePos group) {
        return directory.resolve(TILES).resolve("g." + group.x() + "." + group.z());
    }

    private Path tileFile(TilePos pos) {
        return groupDirectory(group(pos)).resolve("t." + pos.x() + "." + pos.z() + ".dat");
    }

    private void writeIndex(TilePos group, boolean durable) throws IOException {
        List<Tag> list = new ArrayList<>();
        int minX = group.x() * GROUP;
        int minZ = group.z() * GROUP;
        for (int z = minZ; z < minZ + GROUP; z++) {
            for (int x = minX; x < minX + GROUP; x++) {
                Entry entry = entries.get(new TilePos(x, z));
                if (entry != null) {
                    list.add(CompoundTag.builder()
                            .put("x", new IntTag(x))
                            .put("z", new IntTag(z))
                            .put("pending", new ByteTag((byte) (entry.pending() ? 1 : 0)))
                            .put("thumb", IntArrayTag.of(entry.thumb))
                            .build());
                }
            }
        }
        Path directoryPath = groupDirectory(group);
        Path index = directoryPath.resolve(INDEX);
        if (list.isEmpty()) {
            AtomicFiles.deleteIfExists(index);
            if (Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
                try (DirectoryStream<Path> rest = Files.newDirectoryStream(directoryPath)) {
                    if (!rest.iterator().hasNext()) {
                        Files.delete(directoryPath);
                    }
                }
            }
            return;
        }
        Files.createDirectories(directoryPath);
        byte[] bytes = TileCodec.gzip(CompoundTag.builder().put("entries", ListTag.of(TagType.COMPOUND, list))
                .build());
        AtomicFiles.write(index, channel -> writeFully(channel, bytes), durable);
    }

    private static Map<TilePos, Entry> readIndexes(Path directory, TileRect bounds) throws IOException {
        Map<TilePos, Entry> entries = new HashMap<>();
        Path tiles = directory.resolve(TILES);
        AtomicFiles.requireNotSymlink(tiles);
        if (!Files.isDirectory(tiles, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectFormatException("workspace has no tiles folder");
        }
        try (DirectoryStream<Path> groups = Files.newDirectoryStream(tiles)) {
            for (Path groupPath : groups) {
                Path name = groupPath.getFileName();
                Matcher matcher = name == null ? null : GROUP_NAME.matcher(name.toString());
                if (matcher == null || !matcher.matches() || !Files.isDirectory(groupPath, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                TilePos group = new TilePos(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
                Path index = groupPath.resolve(INDEX);
                AtomicFiles.requireNotSymlink(index);
                if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (Files.size(index) > TileCodec.MAX_RECORD_BYTES) {
                    throw new ProjectFormatException("workspace index too large");
                }
                CompoundTag tag = TileCodec.gunzip(Files.readAllBytes(index), INDEX_LIMITS);
                ListTag list = tag.get("entries", ListTag.class)
                        .orElseThrow(() -> new ProjectFormatException("index has no entries"));
                if (!list.isEmpty() && list.elementType() != TagType.COMPOUND) {
                    throw new ProjectFormatException("index entries must be compounds");
                }
                if (list.size() > GROUP * GROUP) {
                    throw new ProjectFormatException("index has too many entries");
                }
                for (Tag element : list) {
                    CompoundTag item = (CompoundTag) element;
                    TilePos pos = TileCodec.position(item);
                    if (!group(pos).equals(group) || !bounds.contains(pos)) {
                        throw new ProjectFormatException("index entry " + pos + " outside its group");
                    }
                    byte pending = item.get("pending", ByteTag.class)
                            .orElseThrow(() -> new ProjectFormatException("index entry has no flag")).value();
                    int[] thumb = item.get("thumb", IntArrayTag.class)
                            .orElseThrow(() -> new ProjectFormatException("index entry has no thumbnail")).toArray();
                    if (thumb.length != THUMB_PIXELS || (pending != 0 && pending != 1)) {
                        throw new ProjectFormatException("malformed index entry " + pos);
                    }
                    if (entries.put(pos, new Entry(pos, pending == 1, thumb)) != null) {
                        throw new ProjectFormatException("duplicate index entry " + pos);
                    }
                }
            }
        }
        return entries;
    }

    private record Header(WorldSpec spec, State state, int tiles) {
    }

    private static void writeHeader(Path directory, WorldSpec spec, State state, int tiles) throws IOException {
        CompoundTag.Builder tag = CompoundTag.builder()
                .put("format", new StringTag(FORMAT))
                .put("version", new IntTag(FORMAT_VERSION))
                .put("spec", spec.toTag())
                .put("name", new StringTag(state.name()))
                .put("unsaved", new ByteTag((byte) (state.unsaved() ? 1 : 0)))
                .put("created", new LongTag(state.created()))
                .put("modified", new LongTag(state.modified()))
                .put("tiles", new IntTag(tiles));
        state.project().ifPresent(p -> tag.put("project", new StringTag(p.toString())));
        state.dumpTarget().ifPresent(p -> tag.put("dumpTarget", new StringTag(p.toString())));
        state.area().ifPresent(a -> tag.put("area", IntArrayTag.of(new int[] {a.minX(), a.minZ(), a.maxX(), a.maxZ()})));
        byte[] bytes = TileCodec.gzip(tag.build());
        AtomicFiles.write(directory.resolve(HEADER), channel -> writeFully(channel, bytes), true);
    }

    private static Header readHeader(Path directory) throws IOException {
        Path file = directory.resolve(HEADER);
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > TileCodec.MAX_RECORD_BYTES) {
            throw new ProjectFormatException("missing or oversized workspace header");
        }
        CompoundTag tag = TileCodec.gunzip(Files.readAllBytes(file), TileCodec.HEADER_LIMITS);
        String format = tag.get("format", StringTag.class).map(StringTag::value).orElse("");
        int version = TileCodec.requireInt(tag, "version");
        if (!format.equals(FORMAT) || version < 1 || version > FORMAT_VERSION) {
            throw new ProjectFormatException("not a supported workspace");
        }
        CompoundTag specTag = tag.get("spec", CompoundTag.class)
                .orElseThrow(() -> new ProjectFormatException("workspace has no spec"));
        WorldSpec spec = WorldSpec.fromTag(specTag, ProjectFile.FORMAT_VERSION);
        String name = tag.get("name", StringTag.class).map(StringTag::value).orElse("");
        byte unsaved = tag.get("unsaved", ByteTag.class).map(ByteTag::value).orElse((byte) 0);
        long created = tag.get("created", LongTag.class).map(LongTag::value).orElse(0L);
        long modified = tag.get("modified", LongTag.class).map(LongTag::value).orElse(created);
        int tiles = Math.max(0, tag.get("tiles", IntTag.class).map(IntTag::value).orElse(0));
        Optional<TileRect> area = Optional.empty();
        Optional<int[]> rect = tag.get("area", IntArrayTag.class).map(IntArrayTag::toArray);
        if (rect.isPresent()) {
            int[] r = rect.get();
            try {
                if (r.length == 4) {
                    TileRect candidate = new TileRect(r[0], r[1], r[2], r[3]);
                    area = candidate.intersect(spec.bounds());
                }
            } catch (IllegalArgumentException e) {
                area = Optional.empty();
            }
        }
        State state = new State(name, path(tag, "project"), unsaved == 1, area, path(tag, "dumpTarget"), created,
                modified);
        return new Header(spec, state, tiles);
    }

    private static Optional<Path> path(CompoundTag tag, String key) {
        Optional<String> text = tag.get(key, StringTag.class).map(StringTag::value);
        if (text.isEmpty() || text.get().isEmpty() || text.get().length() > 4096) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(text.get());
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static boolean locked(Path directory) {
        Path file = directory.resolve(LOCK);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(file, Set.of(StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS))) {
            FileLock probe = channel.tryLock();
            if (probe == null) {
                return true;
            }
            probe.release();
            return false;
        } catch (OverlappingFileLockException e) {
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    private static void requireWorkspaceDirectory(Path directory) throws IOException {
        Path name = directory.getFileName();
        if (name == null || !ID.matcher(name.toString()).matches()) {
            throw new IOException("not a workspace folder: " + directory);
        }
        AtomicFiles.requireNotSymlink(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("not a workspace folder: " + directory);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        AtomicFiles.requireNotSymlink(directory);
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            stream.forEach(children::add);
        }
        for (Path child : children) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(child);
            } else {
                Files.delete(child);
            }
        }
        Files.delete(directory);
    }

    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("workspace is closed");
        }
    }
}
