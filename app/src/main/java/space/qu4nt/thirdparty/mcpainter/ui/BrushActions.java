package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JFrame;
import space.qu4nt.thirdparty.mcpainter.io.BrushMaskImporter;
import space.qu4nt.thirdparty.mcpainter.io.JsonFileStore;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class BrushActions {

    private final JFrame frame;
    private final Messages messages;
    private final BrushSettings settings;
    private final BrushCatalog catalog;
    private final BrushLibrary library;
    private final Document document;
    private final Consumer<String> notice;
    private Path imageDirectory;
    private Path importDirectory;

    BrushActions(JFrame frame, Messages messages, BrushSettings settings, BrushCatalog catalog, BrushLibrary library,
            Document document, Consumer<String> notice) {
        this.frame = frame;
        this.messages = messages;
        this.settings = settings;
        this.catalog = catalog;
        this.library = library;
        this.document = document;
        this.notice = notice;
    }

    BrushCatalog catalog() {
        return catalog;
    }

    void loadLibrary() {
        try {
            BrushLibrary.Contents contents = library.load();
            catalog.load(contents);
            if (contents.failures() > 0) {
                notice.accept(messages.text("brush.loadFailures", Integer.toString(contents.failures())));
            }
        } catch (IOException | RuntimeException e) {
            notice.accept(messages.text("brush.loadFailures", "1"));
        }
    }

    void selectShape(ShapeEntry entry) {
        settings.setShape(entry.shape());
    }

    void addShapeFiles() {
        if (!ProjectActions.idle()) {
            return;
        }
        List<Path> files = FileChoosers.openShapeImages(frame, messages, imageDirectory);
        if (files.isEmpty()) {
            return;
        }
        imageDirectory = files.getFirst().getParent();
        if (files.size() == 1) {
            addShape(files.getFirst());
            return;
        }
        BackgroundTask.Outcome<ShapeBatch.Previews> previews = BackgroundTask.run(frame, messages,
                "task.readingShapes", () -> ShapeBatch.previews(files, 0));
        addShapes(previews);
    }

    void addShapeFolder() {
        if (!ProjectActions.idle()) {
            return;
        }
        Optional<Path> folder = FileChoosers.chooseFolder(frame, messages.text("chooser.shapeFolder"),
                imageDirectory);
        if (folder.isEmpty()) {
            return;
        }
        Path directory = folder.get();
        imageDirectory = directory;
        BackgroundTask.Outcome<ShapeBatch.Previews> previews = BackgroundTask.run(frame, messages,
                "task.readingShapes", () -> {
                    BrushMaskImporter.Listing listing = BrushMaskImporter.listImages(directory, ShapeBatch.MAX_FILES);
                    return ShapeBatch.previews(listing.files(), listing.omitted());
                });
        if (previews.ok() && previews.value().items().isEmpty() && previews.value().unreadable().isEmpty()) {
            Dialogs.info(frame, messages, "shape.batchTitle", messages.text("shape.folderEmpty"));
            return;
        }
        addShapes(previews);
    }

    private void addShape(Path path) {
        BackgroundTask.Outcome<BrushMaskImporter.Source> source = BackgroundTask.run(frame, messages,
                "task.readingShape", () -> BrushMaskImporter.read(path));
        if (!source.ok()) {
            Dialogs.error(frame, messages, "error.brushShape", source.error());
            return;
        }
        ShapeImportForm form = new ShapeImportForm(messages, source.value(), baseName(path));
        Optional<NamedShape> named = Optional.empty();
        while (named.isEmpty()) {
            if (!Dialogs.form(frame, messages, "shape.importTitle", form.panel())) {
                return;
            }
            Optional<BrushShape.Mask> mask = form.mask();
            if (mask.isEmpty()) {
                Dialogs.error(frame, messages, messages.text("shape.empty"), "");
                continue;
            }
            try {
                named = Optional.of(new NamedShape(form.name(), mask.get()));
            } catch (IllegalArgumentException e) {
                Dialogs.error(frame, messages, messages.text("brush.nameInvalid"), "");
            }
        }
        try {
            BrushLibrary.Stored<NamedShape> stored = library.saveShape(named.get());
            catalog.addShape(ShapeEntry.stored(stored.file(), stored.value()));
            settings.setShape(stored.value().mask());
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.brushSave", e);
        }
    }

    private void addShapes(BackgroundTask.Outcome<ShapeBatch.Previews> previews) {
        if (!previews.ok()) {
            Dialogs.error(frame, messages, "error.brushShape", previews.error());
            return;
        }
        ShapeBatch.Previews read = previews.value();
        if (read.items().isEmpty()) {
            Dialogs.error(frame, messages, messages.text("shape.batchNone"), messages.text("shape.batchUnreadable",
                    Integer.toString(read.unreadable().size()), ShapeBatch.list(messages, read.unreadable())));
            return;
        }
        ShapeBatchForm form = new ShapeBatchForm(messages, read);
        while (true) {
            if (!Dialogs.form(frame, messages, "shape.batchTitle", form.panel())) {
                return;
            }
            if (form.emptyCount() < read.items().size()) {
                break;
            }
            Dialogs.error(frame, messages, messages.text("shape.empty"), "");
        }
        boolean levels = form.autoLevels();
        boolean inverted = form.invert();
        BackgroundTask.Outcome<ShapeBatch.Saved> saved = BackgroundTask.run(frame, messages, "task.savingShapes",
                () -> ShapeBatch.save(read.items(), levels, inverted, library::saveShape));
        if (!saved.ok()) {
            Dialogs.error(frame, messages, "error.brushSave", saved.error());
            return;
        }
        ShapeBatch.Saved result = saved.value();
        for (BrushLibrary.Stored<NamedShape> stored : result.stored()) {
            catalog.addShape(ShapeEntry.stored(stored.file(), stored.value()));
        }
        if (!result.stored().isEmpty()) {
            settings.setShape(result.stored().getLast().value().mask());
        }
        if (result.saveError() != null) {
            Dialogs.error(frame, messages, "error.brushSave", result.saveError());
        }
        String summary = messages.text("shape.batchDone", Integer.toString(result.stored().size()));
        if (!result.skipped().isEmpty()) {
            summary = summary + "\n" + messages.text("shape.batchSkipped", Integer.toString(result.skipped().size()),
                    ShapeBatch.list(messages, result.skipped()));
        }
        Dialogs.info(frame, messages, "shape.batchTitle", summary);
    }

    void deleteShape(ShapeEntry entry) {
        if (entry == null || entry.kind() == ShapeEntry.Kind.BUILTIN || !ProjectActions.idle()) {
            return;
        }
        if (!Dialogs.confirm(frame, messages, "dialog.confirmTitle",
                messages.text("shape.deleteConfirm", entry.name()), "dialog.delete")) {
            return;
        }
        try {
            if (entry.kind() == ShapeEntry.Kind.STORED) {
                library.deleteShape(entry.file());
            }
            catalog.removeShape(entry);
            if (settings.shape().equals(entry.shape())) {
                settings.setShape(BrushShape.CIRCLE);
            }
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.brushDelete", e);
        }
    }

    void savePreset() {
        if (!ProjectActions.idle()) {
            return;
        }
        String shapeName = catalog.entryFor(settings.shape(), messages.text("shape.session")).name();
        Optional<BrushPreset> preset = askName("preset.nameTitle", "",
                name -> BrushPreset.of(name, settings, shapeName));
        if (preset.isEmpty()) {
            return;
        }
        try {
            catalog.addPreset(library.savePreset(preset.get()));
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.brushSave", e);
        }
    }

    void applyPreset(BrushLibrary.Stored<BrushPreset> stored) {
        if (stored == null) {
            return;
        }
        BrushPreset preset = stored.value();
        catalog.entryFor(preset.shape(), preset.shapeName().isEmpty() ? messages.text("shape.session")
                : preset.shapeName());
        WorldModel model = document.model();
        settings.apply(preset, model.minY(), model.maxY());
        notice.accept(messages.text("preset.applied", preset.name()));
    }

    void deletePreset(BrushLibrary.Stored<BrushPreset> stored) {
        if (stored == null || !ProjectActions.idle()) {
            return;
        }
        if (!Dialogs.confirm(frame, messages, "dialog.confirmTitle",
                messages.text("preset.deleteConfirm", stored.value().name()), "dialog.delete")) {
            return;
        }
        try {
            library.deletePreset(stored.file());
            catalog.removePreset(stored);
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.brushDelete", e);
        }
    }

    void importFile() {
        if (!ProjectActions.idle()) {
            return;
        }
        Optional<Path> file = FileChoosers.openBrushFile(frame, messages, importDirectory);
        if (file.isEmpty()) {
            return;
        }
        Path path = file.get();
        importDirectory = path.getParent();
        Object parsed;
        try {
            parsed = BrushLibrary.parse(JsonFileStore.readFile(path));
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.brushImport", e);
            return;
        }
        try {
            switch (parsed) {
                case NamedShape shape -> {
                    BrushLibrary.Stored<NamedShape> stored = library.saveShape(shape);
                    catalog.addShape(ShapeEntry.stored(stored.file(), stored.value()));
                    notice.accept(messages.text("brush.imported", shape.name()));
                }
                case BrushPreset preset -> {
                    catalog.addPreset(library.savePreset(preset));
                    notice.accept(messages.text("brush.imported", preset.name()));
                }
                default -> throw new IOException("unsupported brush file");
            }
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.brushSave", e);
        }
    }

    void openFolder() {
        try {
            Files.createDirectories(library.root());
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException(library.root().toString());
            }
            Desktop.getDesktop().open(library.root().toFile());
        } catch (IOException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.openFolder", e);
        }
    }

    private <T> Optional<T> askName(String titleKey, String initial, java.util.function.Function<String, T> build) {
        String current = initial;
        while (true) {
            Optional<String> name = Dialogs.prompt(frame, messages, titleKey, "brush.name", current);
            if (name.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(build.apply(name.get()));
            } catch (IllegalArgumentException e) {
                Dialogs.error(frame, messages, messages.text("brush.nameInvalid"), "");
                current = name.get();
            }
        }
    }

    static String baseName(Path file) {
        Path name = file.getFileName();
        if (name == null) {
            return "";
        }
        String text = name.toString();
        int dot = text.lastIndexOf('.');
        String base = dot > 0 ? text.substring(0, dot) : text;
        return base.length() > BrushLibrary.MAX_NAME_LENGTH ? base.substring(0, BrushLibrary.MAX_NAME_LENGTH) : base;
    }
}
