package space.qu4nt.thirdparty.mcpainter.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class Document {

    private WorldModel model;
    private Path file;
    private long savedRevision;
    private boolean unsavedSource;
    private final List<Runnable> modelListeners = new ArrayList<>();
    private final List<Runnable> stateListeners = new ArrayList<>();

    Document(WorldModel model) {
        this.model = Objects.requireNonNull(model, "model");
        this.savedRevision = model.revision();
    }

    WorldModel model() {
        return model;
    }

    Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    boolean dirty() {
        return unsavedSource || model.revision() != savedRevision;
    }

    String displayName(Messages messages) {
        if (file == null || file.getFileName() == null) {
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
        file = nextFile;
        savedRevision = next.revision();
        unsavedSource = unsaved;
        fire(modelListeners);
        fire(stateListeners);
    }

    void changed() {
        fire(stateListeners);
    }

    void markSaved(Path savedFile, long revision) {
        file = Objects.requireNonNull(savedFile, "file");
        savedRevision = revision;
        unsavedSource = false;
        fire(stateListeners);
    }

    private static void fire(List<Runnable> listeners) {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
