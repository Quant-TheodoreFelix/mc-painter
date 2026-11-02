package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.util.Optional;
import java.util.SequencedSet;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class MapView {

    interface Listener {

        void hovered(int x, int z);

        void hoverCleared();

        void viewChanged();

        void strokeFinished();

        void notice(String text);
    }

    static final int REPEAT_MILLIS = 50;
    private static final Color BACKGROUND = new Color(0x26282B);
    private static final Color CHUNK_LINE = new Color(0, 0, 0, 40);
    private static final Color TILE_LINE = new Color(0, 0, 0, 110);
    private static final Color BRUSH_OUTER = new Color(0, 0, 0, 170);
    private static final Color BRUSH_INNER = new Color(255, 255, 255, 230);
    private static final int GRID_MIN_PIXELS = 12;

    private final Document document;
    private final BrushSettings settings;
    private final Messages messages;
    private final Listener listener;
    private final Viewport viewport = new Viewport();
    private final TileImageCache cache = new TileImageCache(TileImageCache.DEFAULT_MAX_BYTES);
    private final JComponent canvas;
    private final Timer repeat;
    private final BrushStroke.Listener strokeListener;
    private BrushStroke stroke;
    private int activeButton = MouseEvent.NOBUTTON;
    private Point panAnchor;
    private Point mouse;
    private boolean spaceDown;
    private boolean showGrid = true;
    private boolean fitPending = true;
    private double wheelRemainder;

    MapView(Document document, BrushSettings settings, Messages messages, Listener listener) {
        this.document = document;
        this.settings = settings;
        this.messages = messages;
        this.listener = listener;
        this.canvas = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    paintMap(g2);
                } finally {
                    g2.dispose();
                }
            }
        };
        canvas.setOpaque(true);
        canvas.setFocusable(true);
        canvas.setPreferredSize(new Dimension(900, 640));
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repeat = new Timer(REPEAT_MILLIS, e -> repeatStroke());
        strokeListener = new BrushStroke.Listener() {
            @Override
            public void changed(SequencedSet<TilePos> tiles) {
                cache.invalidate(tiles);
                canvas.repaint();
            }

            @Override
            public void tileLimitReached(int limit) {
                listener.notice(messages.text("status.tileLimit", Integer.toString(limit)));
            }
        };

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                released(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dragged(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                hover();
                canvas.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouse = null;
                listener.hoverCleared();
                canvas.repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                wheel(e);
            }
        };
        canvas.addMouseListener(mouseHandler);
        canvas.addMouseMotionListener(mouseHandler);
        canvas.addMouseWheelListener(mouseHandler);
        canvas.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                spaceDown = false;
                endPan();
                finishStroke();
            }
        });
        installKeys();
        settings.addListener(canvas::repaint);
        document.onModelReplaced(() -> {
            finishStroke();
            cache.clear();
            fitToWorld();
        });
    }

    JComponent component() {
        return canvas;
    }

    Viewport viewport() {
        return viewport;
    }

    boolean showGrid() {
        return showGrid;
    }

    void setShowGrid(boolean value) {
        showGrid = value;
        canvas.repaint();
    }

    boolean strokeActive() {
        return stroke != null;
    }

    void invalidateAll() {
        cache.clear();
        canvas.repaint();
    }

    void zoomIn() {
        zoomBy(1, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
    }

    void zoomOut() {
        zoomBy(-1, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
    }

    void zoomReset() {
        if (viewport.setZoom(0, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0)) {
            listener.viewChanged();
            canvas.repaint();
        }
    }

    void fitToWorld() {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            fitPending = true;
            canvas.repaint();
            return;
        }
        fit(width, height);
        listener.viewChanged();
        canvas.repaint();
    }

    void finishStroke() {
        repeat.stop();
        if (stroke != null) {
            BrushStroke finished = stroke;
            stroke = null;
            activeButton = MouseEvent.NOBUTTON;
            finished.finish();
            listener.strokeFinished();
        }
    }

    private void fit(int width, int height) {
        fitPending = false;
        WorldModel model = document.model();
        if (model.tileCount() == 0) {
            viewport.setZoom(0, 0, 0);
            viewport.centerOn(0, 0, width, height);
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TilePos pos : model.tilePositions()) {
            minX = Math.min(minX, pos.minBlockX());
            minZ = Math.min(minZ, pos.minBlockZ());
            maxX = Math.max(maxX, pos.minBlockX() + Tile.SIZE);
            maxZ = Math.max(maxZ, pos.minBlockZ() + Tile.SIZE);
        }
        viewport.fit(minX, minZ, maxX, maxZ, width, height);
    }

    private void pressed(MouseEvent e) {
        canvas.requestFocusInWindow();
        mouse = e.getPoint();
        if (stroke != null || panAnchor != null) {
            return;
        }
        boolean left = SwingUtilities.isLeftMouseButton(e);
        boolean right = SwingUtilities.isRightMouseButton(e);
        if (SwingUtilities.isMiddleMouseButton(e) || (left && spaceDown)) {
            panAnchor = e.getPoint();
            activeButton = e.getButton();
            updateCursor();
            return;
        }
        if (!left && !right) {
            return;
        }
        Optional<Tool> tool = right ? settings.tool().inverse() : Optional.of(settings.tool());
        if (tool.isEmpty()) {
            return;
        }
        Tool effective = tool.get();
        stroke = BrushStroke.begin(document.model(), settings, effective, messages.text(effective.messageKey()),
                blockX(e.getX()), blockZ(e.getY()), strokeListener);
        activeButton = e.getButton();
        if (effective.continuous()) {
            repeat.start();
        }
    }

    private void released(MouseEvent e) {
        if (e.getButton() != activeButton) {
            return;
        }
        if (panAnchor != null) {
            endPan();
        } else {
            finishStroke();
        }
    }

    private void dragged(MouseEvent e) {
        mouse = e.getPoint();
        if (panAnchor != null) {
            viewport.pan(e.getX() - panAnchor.x, e.getY() - panAnchor.y);
            panAnchor = e.getPoint();
            listener.viewChanged();
        } else if (stroke != null) {
            try {
                stroke.dragTo(blockX(e.getX()), blockZ(e.getY()));
            } catch (RuntimeException ex) {
                finishStroke();
                throw ex;
            }
        }
        hover();
        canvas.repaint();
    }

    private void repeatStroke() {
        if (stroke == null) {
            repeat.stop();
            return;
        }
        try {
            stroke.repeat();
        } catch (RuntimeException ex) {
            finishStroke();
            throw ex;
        }
    }

    private void wheel(MouseWheelEvent e) {
        if (panAnchor != null) {
            return;
        }
        wheelRemainder += e.getPreciseWheelRotation();
        int steps = (int) wheelRemainder;
        if (steps != 0) {
            wheelRemainder -= steps;
            zoomBy(-steps, e.getX(), e.getY());
        }
    }

    private void zoomBy(int steps, double screenX, double screenZ) {
        if (viewport.zoomAt(steps, screenX, screenZ)) {
            listener.viewChanged();
            if (mouse != null) {
                hover();
            }
            canvas.repaint();
        }
    }

    private void endPan() {
        if (panAnchor != null) {
            panAnchor = null;
            activeButton = MouseEvent.NOBUTTON;
        }
        updateCursor();
    }

    private void hover() {
        if (mouse != null) {
            listener.hovered(blockX(mouse.x), blockZ(mouse.y));
        }
    }

    private int blockX(int screenX) {
        return (int) Math.floor(viewport.worldX(screenX));
    }

    private int blockZ(int screenZ) {
        return (int) Math.floor(viewport.worldZ(screenZ));
    }

    private void updateCursor() {
        int type = panAnchor != null || spaceDown ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR;
        canvas.setCursor(Cursor.getPredefinedCursor(type));
        canvas.repaint();
    }

    private void installKeys() {
        bind("pressed SPACE", "pan.on", () -> {
            spaceDown = true;
            updateCursor();
        });
        bind("released SPACE", "pan.off", () -> {
            spaceDown = false;
            updateCursor();
        });
        bind("OPEN_BRACKET", "radius.down", () -> settings.setRadius(settings.radius() - radiusStep()));
        bind("CLOSE_BRACKET", "radius.up", () -> settings.setRadius(settings.radius() + radiusStep()));
        for (Tool tool : Tool.values()) {
            bind(Integer.toString(tool.shortcut()), "tool." + tool.name(), () -> settings.setTool(tool));
            bind("NUMPAD" + tool.shortcut(), "tool.numpad." + tool.name(), () -> settings.setTool(tool));
        }
    }

    private int radiusStep() {
        return Math.max(1, settings.radius() / 8);
    }

    private void bind(String keyStroke, String name, Runnable action) {
        InputMap input = canvas.getInputMap(JComponent.WHEN_FOCUSED);
        input.put(KeyStroke.getKeyStroke(keyStroke), name);
        canvas.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void paintMap(Graphics2D g) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        if (fitPending && width > 0 && height > 0) {
            fit(width, height);
            SwingUtilities.invokeLater(listener::viewChanged);
        }
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, width, height);
        }
        WorldModel model = document.model();
        double scale = viewport.scale();
        int lod = Math.min(viewport.lod(), TileRenderer.MAX_LOD);
        int minTileX = tileIndex(viewport.worldX(clip.x));
        int maxTileX = tileIndex(viewport.worldX(clip.x + clip.width));
        int minTileZ = tileIndex(viewport.worldZ(clip.y));
        int maxTileZ = tileIndex(viewport.worldZ(clip.y + clip.height));
        boolean chunkGrid = showGrid && 16 * scale >= GRID_MIN_PIXELS;
        boolean tileGrid = showGrid && Tile.SIZE * scale >= GRID_MIN_PIXELS;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int tz = minTileZ; tz <= maxTileZ; tz++) {
            for (int tx = minTileX; tx <= maxTileX; tx++) {
                TilePos pos = new TilePos(tx, tz);
                if (model.tile(pos).isEmpty()) {
                    continue;
                }
                int x0 = screenX(pos.minBlockX());
                int x1 = screenX(pos.minBlockX() + Tile.SIZE);
                int z0 = screenZ(pos.minBlockZ());
                int z1 = screenZ(pos.minBlockZ() + Tile.SIZE);
                g.drawImage(cache.image(model, pos, lod), x0, z0, x1 - x0, z1 - z0, null);
                if (chunkGrid) {
                    g.setColor(CHUNK_LINE);
                    for (int offset = 16; offset < Tile.SIZE; offset += 16) {
                        int cx = screenX(pos.minBlockX() + offset);
                        int cz = screenZ(pos.minBlockZ() + offset);
                        g.drawLine(cx, z0, cx, z1 - 1);
                        g.drawLine(x0, cz, x1 - 1, cz);
                    }
                }
                if (tileGrid) {
                    g.setColor(TILE_LINE);
                    g.drawRect(x0, z0, x1 - x0 - 1, z1 - z0 - 1);
                }
            }
        }
        paintBrush(g, scale);
    }

    private void paintBrush(Graphics2D g, double scale) {
        if (mouse == null || panAnchor != null || spaceDown) {
            return;
        }
        int bx = blockX(mouse.x);
        int bz = blockZ(mouse.y);
        double cx = viewport.screenX(bx + 0.5);
        double cz = viewport.screenZ(bz + 0.5);
        double r = Math.max(2, (settings.radius() + 0.5) * scale);
        Ellipse2D circle = new Ellipse2D.Double(cx - r, cz - r, 2 * r, 2 * r);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(3f));
        g.setColor(BRUSH_OUTER);
        g.draw(circle);
        g.setStroke(new BasicStroke(1f));
        g.setColor(BRUSH_INNER);
        g.draw(circle);
    }

    private int screenX(int worldX) {
        return (int) Math.floor(viewport.screenX(worldX));
    }

    private int screenZ(int worldZ) {
        return (int) Math.floor(viewport.screenZ(worldZ));
    }

    private static int tileIndex(double world) {
        return Math.floorDiv((int) Math.floor(world), Tile.SIZE);
    }
}
