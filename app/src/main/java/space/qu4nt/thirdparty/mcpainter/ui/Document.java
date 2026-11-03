package space.qu4nt.thirdparty.mcpainter.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class Document {

    private WorldModel model;
    private Workspace workspace;
    private Path file;
    private long syncedRevision;
    private boolean unsavedSource;
    private final List<Runnable> modelListeners = new ArrayList<>();
    private final List<Runnable> stateListeners = new ArrayList<>();

    Document(WorldModel model) {
        this.model = Objects.requireNonNull(model, "model");
        this.syncedRevision = model.revision();
    }

    WorldModel model() {
        return model;
    }

    Optional<Workspace> workspace() {
        return Optional.ofNullable(workspace);
    }

    Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    Optional<TileRect> area() {
        return workspace == null ? Optional.empty() : model.editable();
    }

    boolean unflushed() {
        return model.revision() != syncedRevision;
    }

    boolean dirty() {
        if (workspace != null) {
            return unflushed() || workspace.state().unsaved();
        }
        return unsavedSource || unflushed();
    }

    String displayName(Messages messages) {
        if (file == null || file.getFileName() == null) {
            if (workspace != null && !workspace.state().name().isBlank()) {
                return workspace.state().name();
            }
            return messages.text("app.untitled");
        }
        return file.getFileName().toString();
    }

    void onModelReplaced(Runnable listener) {
        modelListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    void onStateChanged(Runnable listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    void replace(WorldModel next, Path nextFile, boolean unsaved) {
        model = Objects.requireNonNull(next, "model");
        workspace = null;
        file = nextFile;
        syncedRevision = next.revision();
        unsavedSource = unsaved;
        fire(modelListeners);
        fire(stateListeners);
    }

    void open(Workspace nextWorkspace, WorldModel next, Path nextFile) {
        workspace = Objects.requireNonNull(nextWorkspace, "workspace");
        model = Objects.requireNonNull(next, "model");
        file = nextFile;
        syncedRevision = next.revision();
        unsavedSource = false;
        fire(modelListeners);
        fire(stateListeners);
    }

    void replaceModel(WorldModel next) {
        model = Objects.requireNonNull(next, "model");
        syncedRevision = next.revision();
        fire(modelListeners);
        fire(stateListeners);
    }

    void synced() {
        syncedRevision = model.revision();
        fire(stateListeners);
    }

    void close(WorldModel empty) {
        workspace = null;
        file = null;
        model = Objects.requireNonNull(empty, "model");
        syncedRevision = empty.revision();
        unsavedSource = false;
        fire(modelListeners);
        fire(stateListeners);
    }

    void changed() {
        fire(stateListeners);
    }

    void markSaved(Path savedFile, long revision) {
        file = Objects.requireNonNull(savedFile, "file");
        syncedRevision = revision;
        unsavedSource = false;
        fire(stateListeners);
    }

    void markSaved(Path savedFile) {
        file = Objects.requireNonNull(savedFile, "file");
        fire(stateListeners);
    }

    private static void fire(List<Runnable> listeners) {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
