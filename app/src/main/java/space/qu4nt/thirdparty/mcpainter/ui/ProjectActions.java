package space.qu4nt.thirdparty.mcpainter.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JFrame;
import space.qu4nt.thirdparty.mcpainter.anvil.world.LevelData;
import space.qu4nt.thirdparty.mcpainter.anvil.world.MinecraftWorld;
import space.qu4nt.thirdparty.mcpainter.anvil.world.UnsupportedVersionException;
import space.qu4nt.thirdparty.mcpainter.anvil.world.WorldInUseException;
import space.qu4nt.thirdparty.mcpainter.io.AppDirectories;
import space.qu4nt.thirdparty.mcpainter.io.AreaLoader;
import space.qu4nt.thirdparty.mcpainter.io.HeightmapImporter;
import space.qu4nt.thirdparty.mcpainter.io.Progress;
import space.qu4nt.thirdparty.mcpainter.io.ProjectFile;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;
import space.qu4nt.thirdparty.mcpainter.io.WorkspaceLockedException;
import space.qu4nt.thirdparty.mcpainter.io.WorldExporter;
import space.qu4nt.thirdparty.mcpainter.io.WorldSpec;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class ProjectActions {

    record WorldInfo(MinecraftWorld world, boolean inUse, List<String> usedBiomes, Set<Layer> usedLayers) {
    }

    private record Opened(Workspace workspace, WorldModel model) {
    }

    private final JFrame frame;
    private final Messages messages;
    private final Document document;
    private final MapView map;
    private final Settings settings;
    private final Performance.Environment environment;
    private final Consumer<String> notice;
    private final Path workspaces;
    private Path projectDirectory;
    private Path imageDirectory;
    private Path worldDirectory;

    ProjectActions(JFrame frame, Messages messages, Document document, MapView map, Settings settings,
            Performance.Environment environment, Consumer<String> notice) {
        this(frame, messages, document, map, settings, environment, notice, AppDirectories.workspaces());
    }

    ProjectActions(JFrame frame, Messages messages, Document document, MapView map, Settings settings,
            Performance.Environment environment, Consumer<String> notice, Path workspaces) {
        this.frame = frame;
        this.messages = messages;
        this.document = document;
        this.map = map;
        this.settings = settings;
        this.environment = environment;
        this.notice = notice;
        this.workspaces = workspaces;
    }

    static boolean idle() {
        return !BackgroundTask.running();
    }

    int budgetTiles() {
        return Performance.areaTiles(settings.areaMemoryMiB(environment));
    }

    int threads() {
        return settings.threads(environment);
    }

    void start() {
        boolean temp = !otherWorkspaces().isEmpty();
        Forms.StartChoice choice = Forms.start(frame, messages, budgetTiles(), temp);
        switch (choice.action()) {
            case CREATE -> create(choice.world().orElseThrow(), () -> {
            });
            case OPEN -> open();
            case RESTORE -> restore();
            case CANCEL -> {
            }
        }
    }

    void newWorld() {
        Optional<Runnable> release = confirmDiscard();
        if (release.isEmpty()) {
            return;
        }
        Optional<Forms.NewWorld> spec = Forms.newWorld(frame, messages, budgetTiles());
        if (spec.isEmpty()) {
            return;
        }
        create(spec.get(), release.get());
    }

    private void create(Forms.NewWorld world, Runnable release) {
        int budget = budgetTiles();
        int threads = threads();
        String name = messages.text("app.untitled");
        BackgroundTask.Outcome<Opened> created = BackgroundTask.runWithProgress(frame, messages, "task.creating",
                progress -> {
                    Workspace workspace = Workspace.create(workspaces, world.spec(), name);
                    return openArea(workspace, budget, threads, progress);
                });
        if (!created.ok()) {
            if (!created.cancelled()) {
                Dialogs.error(frame, messages, "error.create", created.error());
            }
            return;
        }
        release.run();
        document.open(created.value().workspace(), created.value().model(), null);
        announceArea();
    }

    void open() {
        Optional<Runnable> release = confirmDiscard();
        if (release.isEmpty()) {
            return;
        }
        Optional<Path> file = FileChoosers.openProject(frame, messages, projectDirectory);
        if (file.isEmpty()) {
            return;
        }
        Path path = file.get();
        projectDirectory = path.getParent();
        int budget = budgetTiles();
        int threads = threads();
        BackgroundTask.Outcome<Opened> loaded = BackgroundTask.runWithProgress(frame, messages, "task.loading",
                progress -> {
                    Workspace workspace = ProjectFile.unpack(path, workspaces, Session.workspaceName(
                            Optional.of(path), ""), Session::thumbnail, progress);
                    try {
                        workspace.update(workspace.spec(), workspace.state().withProject(path.toAbsolutePath(),
                                false));
                        return openArea(workspace, budget, threads, progress);
                    } catch (IOException | RuntimeException e) {
                        workspace.deleteAndClose();
                        throw e;
                    }
                });
        if (!loaded.ok()) {
            if (!loaded.cancelled()) {
                Dialogs.error(frame, messages, "error.open", loaded.error());
            }
            return;
        }
        release.get().run();
        document.open(loaded.value().workspace(), loaded.value().model(), path);
        announceArea();
    }

    void restore() {
        List<Workspace.Summary> candidates = otherWorkspaces();
        if (candidates.isEmpty()) {
            Dialogs.info(frame, messages, "restore.title", messages.text("restore.none"));
            return;
        }
        Optional<Workspace.Summary> chosen = RestoreForm.choose(frame, messages, candidates, this::deleteWorkspace);
        if (chosen.isEmpty()) {
            return;
        }
        Optional<Runnable> release = confirmDiscard();
        if (release.isEmpty()) {
            return;
        }
        int budget = budgetTiles();
        int threads = threads();
        Path directory = chosen.get().directory();
        BackgroundTask.Outcome<Opened> opened = BackgroundTask.runWithProgress(frame, messages, "task.restoring",
                progress -> {
                    Workspace workspace = Workspace.open(directory);
                    try {
                        return openArea(workspace, budget, threads, progress);
                    } catch (IOException | RuntimeException e) {
                        workspace.close();
                        throw e;
                    }
                });
        if (!opened.ok()) {
            if (!opened.cancelled()) {
                Dialogs.error(frame, messages, opened.error() instanceof WorkspaceLockedException
                        ? messages.text("restore.locked") : messages.text("error.restore"),
                        Dialogs.describe(opened.error()));
            }
            return;
        }
        release.get().run();
        Workspace workspace = opened.value().workspace();
        document.open(workspace, opened.value().model(), workspace.state().project().orElse(null));
        announceArea();
    }

    boolean save() {
        if (!idle()) {
            return false;
        }
        if (document.workspace().isEmpty()) {
            Dialogs.info(frame, messages, "app.name", messages.text("session.empty"));
            return false;
        }
        Optional<Path> file = document.file();
        return file.isPresent() ? saveTo(file.get()) : saveAs();
    }

    boolean saveAs() {
        if (!idle()) {
            return false;
        }
        map.finishStroke();
        if (document.workspace().isEmpty()) {
            Dialogs.info(frame, messages, "app.name", messages.text("session.empty"));
            return false;
        }
        Path directory = document.file().map(Path::getParent).orElse(projectDirectory);
        String suggested = document.file().map(Path::getFileName).map(Path::toString)
                .orElse(messages.text("app.untitled") + ProjectFile.EXTENSION);
        Optional<Path> chosen = FileChoosers.saveProject(frame, messages, directory, suggested);
        if (chosen.isEmpty()) {
            return false;
        }
        Path path = FileChoosers.withProjectExtension(chosen.get());
        boolean replacing = document.file().map(path::equals).orElse(false);
        if (FileChoosers.needsOverwriteConfirmation(chosen.get(), path, Files.exists(path, LinkOption.NOFOLLOW_LINKS),
                replacing) && !Dialogs.confirm(frame, messages, "dialog.confirmTitle",
                        messages.text("overwrite.message", path.toString()), "dialog.overwrite")) {
            return false;
        }
        return saveTo(path);
    }

    void tempSave() {
        if (!idle() || document.workspace().isEmpty()) {
            return;
        }
        if (flushWithDialog()) {
            notice.accept(messages.text("session.tempSaved"));
        }
    }

    void importHeightmap() {
        Optional<Runnable> release = confirmDiscard();
        if (release.isEmpty()) {
            return;
        }
        Optional<Path> image = FileChoosers.openPng(frame, messages, imageDirectory);
        if (image.isEmpty()) {
            return;
        }
        Path path = image.get();
        imageDirectory = path.getParent();
        Optional<Forms.Import> spec = Forms.importOptions(frame, messages);
        if (spec.isEmpty()) {
            return;
        }
        int budget = budgetTiles();
        int threads = threads();
        String name = Session.workspaceName(Optional.of(path), "");
        BackgroundTask.Outcome<Opened> imported = BackgroundTask.runWithProgress(frame, messages,
                "task.importing", progress -> {
                    WorldModel model = HeightmapImporter.importPng(path, spec.get().options(),
                            spec.get().generation());
                    Workspace workspace = Workspace.create(workspaces, WorldSpec.of(model), name);
                    try {
                        List<Workspace.Write> writes = new ArrayList<>();
                        for (TilePos pos : model.tilePositions()) {
                            writes.add(new Workspace.Write(pos, model.tile(pos).orElseThrow(),
                                    Session.thumbnail(model, pos), true));
                        }
                        workspace.write(writes, true);
                        workspace.update(workspace.spec(), workspace.state().withUnsaved(true));
                        return openArea(workspace, budget, threads, progress);
                    } catch (IOException | RuntimeException e) {
                        workspace.deleteAndClose();
                        throw e;
                    }
                });
        if (!imported.ok()) {
            if (!imported.cancelled()) {
                Dialogs.error(frame, messages, "error.import", imported.error());
            }
            return;
        }
        release.get().run();
        document.open(imported.value().workspace(), imported.value().model(), null);
        announceArea();
    }

    boolean loadArea(TileRect requested) {
        if (!idle() || document.workspace().isEmpty()) {
            return false;
        }
        map.finishStroke();
        Workspace workspace = document.workspace().get();
        Optional<TileRect> area = requested.intersect(workspace.spec().bounds());
        if (area.isEmpty()) {
            return false;
        }
        int budget = budgetTiles();
        long cost = Session.areaCost(workspace, area.get());
        if (cost > budget) {
            Dialogs.error(frame, messages, messages.text("area.tooLarge", Forms.number(cost), Forms.number(budget)),
                    "");
            return false;
        }
        WorldModel current = document.model();
        int threads = threads();
        BackgroundTask.Outcome<WorldModel> loaded = BackgroundTask.runWithProgress(frame, messages,
                "task.loadingArea", progress -> {
                    Session.flush(current, workspace);
                    WorldModel next = Session.load(workspace, area.get(), budget, threads, progress);
                    workspace.update(workspace.spec(), workspace.state().withArea(Optional.of(area.get())));
                    return next;
                });
        if (!loaded.ok()) {
            document.changed();
            if (!loaded.cancelled()) {
                Dialogs.error(frame, messages, "error.area", loaded.error());
            }
            return false;
        }
        document.replaceModel(loaded.value());
        return true;
    }

    void closeArea() {
        if (!idle() || document.workspace().isEmpty() || document.area().isEmpty()) {
            return;
        }
        map.finishStroke();
        Workspace workspace = document.workspace().get();
        WorldModel current = document.model();
        BackgroundTask.Outcome<Boolean> flushed = BackgroundTask.run(frame, messages, "task.tempSaving", () -> {
            Session.flush(current, workspace);
            workspace.update(workspace.spec(), workspace.state().withArea(Optional.empty()));
            return Boolean.TRUE;
        });
        if (!flushed.ok()) {
            Dialogs.error(frame, messages, "error.tempSave", flushed.error());
            return;
        }
        document.replaceModel(Session.closedArea(workspace));
    }

    void export() {
        if (!idle()) {
            return;
        }
        map.finishStroke();
        if (document.workspace().isEmpty()) {
            Dialogs.info(frame, messages, "export.title", messages.text("session.empty"));
            return;
        }
        Workspace workspace = document.workspace().get();
        Optional<TileRect> area = document.area();
        if (area.isEmpty()) {
            Dialogs.info(frame, messages, "export.title", messages.text("export.noArea"));
            return;
        }
        WorldModel model = document.model();
        List<TilePos> tiles = Session.areaTiles(model);
        if (tiles.isEmpty()) {
            Dialogs.info(frame, messages, "export.title", messages.text("export.empty"));
            return;
        }
        if (!flushWithDialog()) {
            return;
        }
        Optional<Path> target = chooseTarget(workspace, model.usedBiomes(), model.usedLayers(),
                WorldExporter.estimate(tiles.size(), WorldExporter.regionCount(area.get()), threads()));
        if (target.isEmpty()) {
            return;
        }
        Path directory = target.get();
        int threads = threads();
        BackgroundTask.Outcome<WorldExporter.Result> exported = BackgroundTask.runWithProgress(frame, messages,
                "task.exporting", progress -> {
                    WorldExporter.Result result = WorldExporter.export(model, directory, tiles, threads, progress);
                    if (!result.cancelled()) {
                        workspace.markDumped(workspace.storedIn(area.get()));
                    }
                    workspace.update(workspace.spec(), workspace.state().withDumpTarget(directory));
                    return result;
                });
        reportExport(exported);
    }

    void exportAll() {
        if (!idle()) {
            return;
        }
        map.finishStroke();
        if (document.workspace().isEmpty()) {
            Dialogs.info(frame, messages, "export.title", messages.text("session.empty"));
            return;
        }
        Workspace workspace = document.workspace().get();
        TileRect bounds = workspace.spec().bounds();
        if (!flushWithDialog()) {
            return;
        }
        BackgroundTask.Outcome<WorldExporter.Estimate> estimated = BackgroundTask.run(frame, messages,
                "task.estimating", () -> WorldExporter.estimate(AreaLoader.tilesIn(workspace, bounds),
                        AreaLoader.regionsToWrite(workspace, bounds).size(), threads()));
        if (!estimated.ok()) {
            Dialogs.error(frame, messages, "error.export", estimated.error());
            return;
        }
        if (estimated.value().tiles() == 0) {
            Dialogs.info(frame, messages, "export.title", messages.text("export.empty"));
            return;
        }
        WorldModel model = document.model();
        Optional<Path> target = chooseTarget(workspace, workspace.spec().biomes(), model.usedLayers(),
                estimated.value());
        if (target.isEmpty()) {
            return;
        }
        Path directory = target.get();
        int threads = threads();
        BackgroundTask.Outcome<WorldExporter.Result> exported = BackgroundTask.runWithProgress(frame, messages,
                "task.exporting", progress -> {
                    WorldExporter.Result result = AreaLoader.exportWorkspace(workspace, bounds, directory, threads,
                            progress);
                    if (!result.cancelled()) {
                        workspace.markDumped(AreaLoader.storedIn(workspace));
                    }
                    workspace.update(workspace.spec(), workspace.state().withDumpTarget(directory));
                    return result;
                });
        reportExport(exported);
    }

    Optional<Runnable> confirmDiscard() {
        if (!idle()) {
            return Optional.empty();
        }
        map.finishStroke();
        Optional<Workspace> open = document.workspace();
        if (open.isEmpty()) {
            return Optional.of(() -> {
            });
        }
        Workspace workspace = open.get();
        if (!document.dirty()) {
            return Optional.of(() -> release(workspace, true));
        }
        return switch (Dialogs.unsaved(frame, messages, document.displayName(messages))) {
            case SAVE -> save() ? Optional.of(() -> release(workspace, true)) : Optional.empty();
            case KEEP -> flushWithDialog() ? Optional.of(() -> release(workspace, false)) : Optional.empty();
            case DISCARD -> Optional.of(() -> release(workspace, true));
            case CANCEL -> Optional.empty();
        };
    }

    static String exportFailureKey(Throwable error) {
        if (error instanceof WorldInUseException) {
            return "export.inUse";
        }
        if (error instanceof UnsupportedVersionException) {
            return "export.unsupported";
        }
        return "error.export";
    }

    private Opened openArea(Workspace workspace, int budget, int threads, Progress progress) throws IOException {
        Optional<TileRect> area = Session.initialArea(workspace, budget);
        WorldModel model = area.isPresent() ? Session.load(workspace, area.get(), budget, threads, progress)
                : Session.closedArea(workspace);
        workspace.update(workspace.spec(), workspace.state().withArea(area));
        return new Opened(workspace, model);
    }

    private void announceArea() {
        if (document.area().isEmpty()) {
            notice.accept(messages.text("area.choose"));
        }
    }

    private List<Workspace.Summary> otherWorkspaces() {
        List<Workspace.Summary> result = new ArrayList<>();
        Optional<Path> current = document.workspace().map(Workspace::directory);
        try {
            for (Workspace.Summary summary : Workspace.list(workspaces)) {
                if (current.isEmpty() || !summary.directory().equals(current.get())) {
                    result.add(summary);
                }
            }
        } catch (IOException | RuntimeException e) {
            return result;
        }
        return result;
    }

    private boolean deleteWorkspace(Workspace.Summary summary) {
        if (!Dialogs.confirm(frame, messages, "dialog.confirmTitle",
                messages.text("restore.deleteConfirm", RestoreForm.title(messages, summary)), "dialog.delete")) {
            return false;
        }
        try {
            Workspace.delete(summary.directory());
            return true;
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.restoreDelete", e);
            return false;
        }
    }

    private void release(Workspace workspace, boolean delete) {
        try {
            if (delete) {
                workspace.deleteAndClose();
            } else {
                workspace.close();
            }
        } catch (IOException | RuntimeException e) {
            notice.accept(messages.text("session.releaseFailed"));
        }
        if (document.workspace().filter(open -> open == workspace).isPresent()) {
            document.close(Session.empty());
        }
    }

    private boolean flushWithDialog() {
        Optional<Workspace> open = document.workspace();
        if (open.isEmpty()) {
            return true;
        }
        Workspace workspace = open.get();
        WorldModel model = document.model();
        BackgroundTask.Outcome<Boolean> flushed = BackgroundTask.run(frame, messages, "task.tempSaving",
                () -> Session.flush(model, workspace));
        if (!flushed.ok()) {
            Dialogs.error(frame, messages, "error.tempSave", flushed.error());
            return false;
        }
        document.synced();
        return true;
    }

    private Optional<Path> chooseTarget(Workspace workspace, List<String> biomes, Set<Layer> layers,
            WorldExporter.Estimate estimate) {
        Path start = workspace.state().dumpTarget().map(Path::getParent).orElse(worldDirectory);
        if (start == null) {
            start = FileChoosers.defaultWorldDirectory();
        }
        Optional<Path> chosen = FileChoosers.chooseWorld(frame, messages, start);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        Path directory = chosen.get();
        worldDirectory = directory.getParent();
        if (!Files.isRegularFile(directory.resolve(LevelData.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
            Dialogs.error(frame, messages, messages.text("export.notWorld"), directory.toString());
            return Optional.empty();
        }
        BackgroundTask.Outcome<WorldInfo> info = BackgroundTask.run(frame, messages, "task.reading", () -> {
            MinecraftWorld world = MinecraftWorld.open(directory);
            return new WorldInfo(world, world.isInUse(), biomes, layers);
        });
        if (!info.ok()) {
            reportExportFailure(info.error());
            return Optional.empty();
        }
        if (info.value().inUse()) {
            Dialogs.error(frame, messages, messages.text("export.inUse"), "");
            return Optional.empty();
        }
        long free;
        try {
            free = WorldExporter.usableSpace(directory);
        } catch (IOException e) {
            free = Long.MAX_VALUE;
        }
        if (estimate.bytes() > free) {
            Dialogs.error(frame, messages, messages.text("export.noSpace", PerformanceForm.gigabytes(estimate.bytes()),
                    PerformanceForm.gigabytes(free)), "");
            return Optional.empty();
        }
        LevelData level = info.value().world().level();
        String confirm = messages.text("export.confirm", level.levelName(), level.versionName(),
                Integer.toString(level.dataVersion()), info.value().world().root().toString());
        confirm = confirm + "\n\n" + messages.text("export.estimate", Forms.number(estimate.tiles()),
                Forms.number(estimate.chunks()), PerformanceForm.gigabytes(estimate.bytes()),
                BackgroundTask.duration(messages, estimate.seconds()));
        Optional<String> biomeWarning = BiomeCheck.warning(messages, info.value().usedBiomes(), level.dataVersion());
        if (biomeWarning.isPresent()) {
            confirm = confirm + "\n\n" + biomeWarning.get();
        }
        Optional<String> flowers = FlowerCheck.warning(messages, info.value().usedLayers(), level.dataVersion());
        if (flowers.isPresent()) {
            confirm = confirm + "\n\n" + flowers.get();
        }
        if (!Dialogs.confirm(frame, messages, "export.title", confirm, "dialog.export")) {
            return Optional.empty();
        }
        return Optional.of(directory);
    }

    private void reportExport(BackgroundTask.Outcome<WorldExporter.Result> exported) {
        if (!exported.ok()) {
            reportExportFailure(exported.error());
            return;
        }
        WorldExporter.Result result = exported.value();
        String key = result.cancelled() ? "export.cancelled" : "export.done";
        Dialogs.info(frame, messages, "export.title", messages.text(key,
                Integer.toString(result.regionsWritten()), Integer.toString(result.chunksReplaced()),
                Integer.toString(result.chunksCreated())));
        document.changed();
    }

    private void reportExportFailure(Throwable error) {
        String key = exportFailureKey(error);
        Dialogs.error(frame, messages, messages.text(key), key.equals("export.inUse") ? "" : Dialogs.describe(error));
    }

    private boolean saveTo(Path path) {
        map.finishStroke();
        Workspace workspace = document.workspace().orElseThrow();
        WorldModel model = document.model();
        BackgroundTask.Outcome<Boolean> saved = BackgroundTask.runWithProgress(frame, messages, "task.saving",
                progress -> {
                    Session.flush(model, workspace);
                    ProjectFile.save(workspace, path, progress);
                    workspace.update(workspace.spec(), workspace.state().withProject(path.toAbsolutePath(), false)
                            .withName(Session.workspaceName(Optional.of(path), workspace.state().name())));
                    return Boolean.TRUE;
                });
        if (!saved.ok()) {
            if (!saved.cancelled()) {
                Dialogs.error(frame, messages, "error.save", saved.error());
            }
            document.changed();
            return false;
        }
        projectDirectory = path.getParent();
        document.synced();
        document.markSaved(path);
        return true;
    }
}
