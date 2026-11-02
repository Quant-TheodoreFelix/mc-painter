package space.qu4nt.thirdparty.mcpainter.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class EditHistory {

    static final int MAX_STEPS = 100;
    static final long MAX_BYTES = 512L * 1024 * 1024;

    record Step(String label, Map<TilePos, Tile> before, long fromRevision, long toRevision) {

        Step(String label, Map<TilePos, Tile> before) {
            this(label, before, 0, 0);
        }

        long estimatedBytes() {
            return before.values().stream().filter(t -> t != null).count() * Tile.ESTIMATED_BYTES;
        }
    }

    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private long undoBytes;
    private long revision;
    private long lastRevision;

    long revision() {
        return revision;
    }

    void push(Step unstamped) {
        Step step = new Step(unstamped.label(), unstamped.before(), revision, ++lastRevision);
        revision = step.toRevision();
        undo.push(step);
        undoBytes += step.estimatedBytes();
        redo.clear();
        while (undo.size() > MAX_STEPS || (undoBytes > MAX_BYTES && undo.size() > 1)) {
            undoBytes -= undo.removeLast().estimatedBytes();
        }
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    Optional<String> undoLabel() {
        return Optional.ofNullable(undo.peek()).map(Step::label);
    }

    boolean undo(Map<TilePos, Tile> tiles) {
        Step step = undo.poll();
        if (step == null) {
            return false;
        }
        undoBytes -= step.estimatedBytes();
        redo.push(swap(step, tiles));
        revision = step.fromRevision();
        return true;
    }

    boolean redo(Map<TilePos, Tile> tiles) {
        Step step = redo.poll();
        if (step == null) {
            return false;
        }
        Step inverse = swap(step, tiles);
        undo.push(inverse);
        undoBytes += inverse.estimatedBytes();
        revision = step.toRevision();
        return true;
    }

    private static Step swap(Step step, Map<TilePos, Tile> tiles) {
        Map<TilePos, Tile> current = new HashMap<>();
        for (Map.Entry<TilePos, Tile> entry : step.before().entrySet()) {
            current.put(entry.getKey(), tiles.get(entry.getKey()));
            if (entry.getValue() == null) {
                tiles.remove(entry.getKey());
            } else {
                tiles.put(entry.getKey(), entry.getValue());
            }
        }
        return new Step(step.label(), current, step.fromRevision(), step.toRevision());
    }
}
