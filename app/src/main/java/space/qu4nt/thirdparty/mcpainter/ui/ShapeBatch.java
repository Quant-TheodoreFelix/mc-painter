package space.qu4nt.thirdparty.mcpainter.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import space.qu4nt.thirdparty.mcpainter.io.BrushMaskImporter;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

final class ShapeBatch {

    static final int MAX_FILES = 256;
    static final int PREVIEW_SIZE = 64;
    static final int LISTED_NAMES = 8;

    record Item(Path file, String name, BrushMaskImporter.Source preview) {
    }

    record Previews(List<Item> items, List<String> unreadable, int omitted) {

        Previews {
            items = List.copyOf(items);
            unreadable = List.copyOf(unreadable);
        }
    }

    record Saved(List<BrushLibrary.Stored<NamedShape>> stored, List<String> skipped, Throwable saveError) {

        Saved {
            stored = List.copyOf(stored);
            skipped = List.copyOf(skipped);
        }
    }

    interface Store {

        BrushLibrary.Stored<NamedShape> save(NamedShape shape) throws IOException;
    }

    private ShapeBatch() {
    }

    static Previews previews(List<Path> files, int omitted) {
        int count = Math.min(files.size(), MAX_FILES);
        List<Item> items = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        for (Path file : files.subList(0, count)) {
            try {
                items.add(new Item(file, nameFor(file), BrushMaskImporter.read(file, PREVIEW_SIZE)));
            } catch (IOException | RuntimeException e) {
                unreadable.add(fileName(file));
            }
        }
        return new Previews(items, unreadable, omitted + files.size() - count);
    }

    static Saved save(List<Item> items, boolean autoLevels, boolean invert, Store store) {
        List<BrushLibrary.Stored<NamedShape>> stored = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Item item : items) {
            NamedShape shape;
            try {
                BrushShape.Mask mask = BrushMaskImporter.read(item.file()).toMask(autoLevels, invert);
                shape = new NamedShape(item.name(), mask);
            } catch (IOException | RuntimeException e) {
                skipped.add(item.name());
                continue;
            }
            try {
                stored.add(store.save(shape));
            } catch (IOException | RuntimeException e) {
                return new Saved(stored, skipped, e);
            }
        }
        return new Saved(stored, skipped, null);
    }

    static boolean empty(Item item, boolean autoLevels, boolean invert) {
        try {
            item.preview().toMask(autoLevels, invert);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    static String nameFor(Path file) {
        String base = BrushActions.baseName(file);
        StringBuilder name = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            name.append(allowed(c) ? c : '_');
        }
        String result = name.toString().strip();
        return result.isEmpty() ? "_" : result;
    }

    static String list(Messages messages, List<String> names) {
        if (names.size() <= LISTED_NAMES) {
            return String.join(", ", names);
        }
        return messages.text("shape.listMore", String.join(", ", names.subList(0, LISTED_NAMES)),
                Integer.toString(names.size() - LISTED_NAMES));
    }

    private static boolean allowed(char c) {
        try {
            BrushLibrary.requireName("a" + c);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String fileName(Path file) {
        Path name = file.getFileName();
        return name == null ? file.toString() : name.toString();
    }
}
