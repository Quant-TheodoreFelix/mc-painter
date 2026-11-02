package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

final class ShapeEntry {

    enum Kind {
        BUILTIN,
        STORED,
        SESSION
    }

    static final int ICON_SIZE = 16;

    private final Kind kind;
    private final String file;
    private final String name;
    private final BrushShape shape;
    private Icon icon;

    private ShapeEntry(Kind kind, String file, String name, BrushShape shape) {
        this.kind = kind;
        this.file = file;
        this.name = Objects.requireNonNull(name, "name");
        this.shape = Objects.requireNonNull(shape, "shape");
    }

    static ShapeEntry builtin(BrushShape shape, String name) {
        return new ShapeEntry(Kind.BUILTIN, null, name, shape);
    }

    static ShapeEntry stored(String file, NamedShape shape) {
        return new ShapeEntry(Kind.STORED, Objects.requireNonNull(file, "file"), shape.name(), shape.mask());
    }

    static ShapeEntry session(String name, BrushShape shape) {
        return new ShapeEntry(Kind.SESSION, null, name, shape);
    }

    Kind kind() {
        return kind;
    }

    String file() {
        return file;
    }

    String name() {
        return name;
    }

    BrushShape shape() {
        return shape;
    }

    Icon icon() {
        if (icon == null) {
            icon = new ImageIcon(thumbnail(shape));
        }
        return icon;
    }

    static BufferedImage thumbnail(BrushShape shape) {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
            g.setColor(Color.DARK_GRAY);
            switch (shape) {
                case BrushShape.Circle _ -> g.fillOval(1, 1, ICON_SIZE - 2, ICON_SIZE - 2);
                case BrushShape.Square _ -> g.fillRect(2, 2, ICON_SIZE - 4, ICON_SIZE - 4);
                case BrushShape.Mask mask -> {
                    for (int y = 0; y < ICON_SIZE; y++) {
                        for (int x = 0; x < ICON_SIZE; x++) {
                            int v = mask.value(x * mask.size() / ICON_SIZE, y * mask.size() / ICON_SIZE);
                            int shade = 255 - v;
                            image.setRGB(x, y, 0xFF000000 | (shade << 16) | (shade << 8) | shade);
                        }
                    }
                }
            }
            g.setColor(Color.GRAY);
            g.drawRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
        } finally {
            g.dispose();
        }
        return image;
    }
}
