package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

final class BrushCatalog {

    private final List<ShapeEntry> shapes = new ArrayList<>();
    private final List<BrushLibrary.Stored<BrushPreset>> presets = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    BrushCatalog(Messages messages) {
        shapes.add(ShapeEntry.builtin(BrushShape.CIRCLE, messages.text("shape.circle")));
        shapes.add(ShapeEntry.builtin(BrushShape.SQUARE, messages.text("shape.square")));
    }

    void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    List<ShapeEntry> shapes() {
        return List.copyOf(shapes);
    }

    List<BrushLibrary.Stored<BrushPreset>> presets() {
        return List.copyOf(presets);
    }

    void load(BrushLibrary.Contents contents) {
        shapes.removeIf(entry -> entry.kind() == ShapeEntry.Kind.STORED);
        for (BrushLibrary.Stored<NamedShape> stored : contents.shapes()) {
            shapes.add(ShapeEntry.stored(stored.file(), stored.value()));
        }
        presets.clear();
        presets.addAll(contents.presets());
        fire();
    }

    ShapeEntry entryFor(BrushShape shape, String fallbackName) {
        for (ShapeEntry entry : shapes) {
            if (entry.shape().equals(shape)) {
                return entry;
            }
        }
        ShapeEntry session = ShapeEntry.session(fallbackName, shape);
        shapes.add(session);
        fire();
        return session;
    }

    void addShape(ShapeEntry entry) {
        shapes.removeIf(existing -> existing.kind() == ShapeEntry.Kind.SESSION && existing.shape().equals(entry.shape()));
        shapes.add(entry);
        fire();
    }

    void removeShape(ShapeEntry entry) {
        if (shapes.remove(entry)) {
            fire();
        }
    }

    void addPreset(BrushLibrary.Stored<BrushPreset> preset) {
        presets.add(preset);
        fire();
    }

    void removePreset(BrushLibrary.Stored<BrushPreset> preset) {
        if (presets.remove(preset)) {
            fire();
        }
    }

    private void fire() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
