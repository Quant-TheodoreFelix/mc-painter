package space.qu4nt.thirdparty.mcpainter.ui;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.swing.JFrame;
import space.qu4nt.thirdparty.mcpainter.anvil.world.LevelData;
import space.qu4nt.thirdparty.mcpainter.anvil.world.MinecraftWorld;
import space.qu4nt.thirdparty.mcpainter.anvil.world.UnsupportedVersionException;
import space.qu4nt.thirdparty.mcpainter.anvil.world.WorldInUseException;
import space.qu4nt.thirdparty.mcpainter.io.HeightmapImporter;
import space.qu4nt.thirdparty.mcpainter.io.ProjectFile;
import space.qu4nt.thirdparty.mcpainter.io.WorldExporter;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class ProjectActions {

    record WorldInfo(MinecraftWorld world, boolean inUse, List<String> usedBiomes) {
    }

    private final JFrame frame;
    private final Messages messages;
    private final Document document;
    private final MapView map;
    private Path projectDirectory;
    private Path imageDirectory;
    private Path worldDirectory;

    ProjectActions(JFrame frame, Messages messages, Document document, MapView map) {
        this.frame = frame;
        this.messages = messages;
        this.document = document;
        this.map = map;
    }

    static boolean idle() {
        return !BackgroundTask.running();
    }

    void newWorld() {
        if (!confirmDiscard()) {
            return;
        }
        Optional<Forms.NewWorld> spec = Forms.newWorld(frame, messages);
        if (spec.isEmpty()) {
            return;
        }
        Forms.NewWorld s = spec.get();
        BackgroundTask.Outcome<WorldModel> created = BackgroundTask.run(frame, messages, "task.creating",
                () -> Worlds.blank(s.width(), s.depth(), s.height(), s.seaLevel(), s.terrain(), s.generation()));
        if (created.ok()) {
            document.replace(created.value(), null, false);
        } else {
            Dialogs.error(frame, messages, "error.create", created.error());
        }
    }

    void open() {
        if (!confirmDiscard()) {
            return;
        }
        Optional<Path> file = FileChoosers.openProject(frame, messages, projectDirectory);
        if (file.isEmpty()) {
            return;
        }
        Path path = file.get();
        projectDirectory = path.getParent();
        BackgroundTask.Outcome<WorldModel> loaded = BackgroundTask.run(frame, messages, "task.loading",
                () -> ProjectFile.load(path));
        if (loaded.ok()) {
            document.replace(loaded.value(), path, false);
        } else {
            Dialogs.error(frame, messages, "error.open", loaded.error());
        }
    }

    boolean save() {
        if (!idle()) {
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

    void importHeightmap() {
        if (!confirmDiscard()) {
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
        BackgroundTask.Outcome<WorldModel> imported = BackgroundTask.run(frame, messages, "task.importing",
                () -> HeightmapImporter.importPng(path, spec.get().options(), spec.get().generation()));
        if (imported.ok()) {
            document.replace(imported.value(), null, true);
        } else {
            Dialogs.error(frame, messages, "error.import", imported.error());
        }
    }

    void export() {
        if (!idle()) {
            return;
        }
        map.finishStroke();
        if (document.model().tileCount() == 0) {
            Dialogs.info(frame, messages, "export.title", messages.text("export.empty"));
            return;
        }
        Path start = worldDirectory != null ? worldDirectory : FileChoosers.defaultWorldDirectory();
        Optional<Path> chosen = FileChoosers.chooseWorld(frame, messages, start);
        if (chosen.isEmpty()) {
            return;
        }
        Path directory = chosen.get();
        worldDirectory = directory.getParent();
        if (!Files.isRegularFile(directory.resolve(LevelData.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
            Dialogs.error(frame, messages, messages.text("export.notWorld"), directory.toString());
            return;
        }
        WorldModel model = document.model();
        BackgroundTask.Outcome<WorldInfo> info = BackgroundTask.run(frame, messages, "task.reading", () -> {
            MinecraftWorld world = MinecraftWorld.open(directory);
            return new WorldInfo(world, world.isInUse(), model.usedBiomes());
        });
        if (!info.ok()) {
            reportExportFailure(info.error());
            return;
        }
        if (info.value().inUse()) {
            Dialogs.error(frame, messages, messages.text("export.inUse"), "");
            return;
        }
        LevelData level = info.value().world().level();
        String confirm = messages.text("export.confirm", level.levelName(), level.versionName(),
                Integer.toString(level.dataVersion()), info.value().world().root().toString());
        Optional<String> biomes = BiomeCheck.warning(messages, info.value().usedBiomes(), level.dataVersion());
        if (biomes.isPresent()) {
            confirm = confirm + "\n\n" + biomes.get();
        }
        if (!Dialogs.confirm(frame, messages, "export.title", confirm, "dialog.export")) {
            return;
        }
        BackgroundTask.Outcome<WorldExporter.Result> exported = BackgroundTask.run(frame, messages,
                "task.exporting", () -> WorldExporter.export(model, directory));
        if (!exported.ok()) {
            reportExportFailure(exported.error());
            return;
        }
        WorldExporter.Result result = exported.value();
        Dialogs.info(frame, messages, "export.title", messages.text("export.done",
                Integer.toString(result.regionsWritten()), Integer.toString(result.chunksReplaced()),
                Integer.toString(result.chunksCreated())));
    }

    boolean confirmDiscard() {
        if (!idle()) {
            return false;
        }
        map.finishStroke();
        if (!document.dirty()) {
            return true;
        }
        return switch (Dialogs.unsaved(frame, messages, document.displayName(messages))) {
            case SAVE -> save();
            case DISCARD -> true;
            case CANCEL -> false;
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

    private void reportExportFailure(Throwable error) {
        String key = exportFailureKey(error);
        Dialogs.error(frame, messages, messages.text(key), key.equals("export.inUse") ? "" : Dialogs.describe(error));
    }

    private boolean saveTo(Path path) {
        map.finishStroke();
        WorldModel model = document.model();
        long revision = model.revision();
        BackgroundTask.Outcome<Boolean> saved = BackgroundTask.run(frame, messages, "task.saving", () -> {
            ProjectFile.save(model, path);
            return Boolean.TRUE;
        });
        if (!saved.ok()) {
            Dialogs.error(frame, messages, "error.save", saved.error());
            return false;
        }
        projectDirectory = path.getParent();
        document.markSaved(path, revision);
        return true;
    }
}
