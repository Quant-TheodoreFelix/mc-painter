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
    private static final int BACKGROUND_LEVEL = 0x22;
    private static final Color BACKGROUND = new Color(BACKGROUND_LEVEL, BACKGROUND_LEVEL, BACKGROUND_LEVEL);

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
            icon = new ImageIcon(thumbnail(shape, ICON_SIZE));
        }
        return icon;
    }

    static BufferedImage blank(int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, side, side);
            g.setColor(Color.GRAY);
            g.drawRect(0, 0, side - 1, side - 1);
            g.drawLine(0, side - 1, side - 1, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    static BufferedImage thumbnail(BrushShape shape, int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, side, side);
            g.setColor(Color.WHITE);
            switch (shape) {
                case BrushShape.Circle _ -> g.fillOval(1, 1, side - 2, side - 2);
                case BrushShape.Square _ -> g.fillRect(2, 2, side - 4, side - 4);
                case BrushShape.Mask mask -> {
                    for (int y = 0; y < side; y++) {
                        for (int x = 0; x < side; x++) {
                            int v = mask.value(x * mask.size() / side, y * mask.size() / side);
                            int shade = BACKGROUND_LEVEL + v * (255 - BACKGROUND_LEVEL) / 255;
                            image.setRGB(x, y, 0xFF000000 | (shade << 16) | (shade << 8) | shade);
                        }
                    }
                }
            }
            g.setColor(Color.GRAY);
            g.drawRect(0, 0, side - 1, side - 1);
        } finally {
            g.dispose();
        }
        return image;
    }
}
