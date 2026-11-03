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
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import space.qu4nt.thirdparty.mcpainter.io.SurfacePreview;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
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
    static final double WHEEL_ZOOM = 0.2;
    static final int MAX_RADIUS_STEPS = 64;
    static final int PAN_MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK;
    private static final Color BACKGROUND = new Color(0x26282B);
    private static final Color BRUSH_OUTER = new Color(0, 0, 0, 170);
    private static final Color BRUSH_INNER = new Color(255, 255, 255, 230);
    private static final Color ADD_PREVIEW = new Color(255, 255, 255, 70);
    private static final Color ADD_EDGE = new Color(255, 255, 255, 200);
    private static final Color DELETE_PREVIEW = new Color(235, 35, 35, 130);
    private static final Color DELETE_EDGE = new Color(255, 40, 40, 230);
    private static final int GRID_MIN_PIXELS = 12;

    private final Document document;
    private final BrushSettings settings;
    private final Messages messages;
    private final Listener listener;
    private final Viewport viewport = new Viewport();
    private final TileImageCache cache = new TileImageCache(TileImageCache.DEFAULT_MAX_BYTES);
    private final PreviewCache previews;
    private final ExecutorService previewPool;
    private boolean preview;
    private final JComponent canvas;
    private final Timer repeat;
    private final BrushStroke.Listener strokeListener;
    private BrushStroke stroke;
    private int activeButton = MouseEvent.NOBUTTON;
    private Point panAnchor;
    private Point mouse;
    private boolean spaceDown;
    private boolean showGrid = true;
    private Color chunkLine = gridColor(Settings.DEFAULT_CHUNK_GRID);
    private Color tileLine = gridColor(Settings.DEFAULT_TILE_GRID);
    private boolean fitPending = true;
    private double radiusRemainder;

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
        ExecutorService pool = PreviewCache.workerPool();
        previewPool = pool;
        previews = new PreviewCache(document::model, pool, SwingUtilities::invokeLater, SurfacePreview::render,
                pos -> {
                    cache.invalidate(List.of(pos));
                    canvas.repaint();
                }, 2 * PreviewCache.threads(pool), PreviewCache.DEFAULT_MAX_BYTES);
        strokeListener = new BrushStroke.Listener() {
            @Override
            public void changed(SequencedSet<TilePos> tiles) {
                cache.invalidate(tiles);
                previews.invalidate(tiles);
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
        MacGestures.installMagnify(canvas, this::pinch);
        settings.addListener(canvas::repaint);
        document.onModelReplaced(() -> {
            finishStroke();
            cache.clear();
            previews.clear();
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

    void setGridOpacity(int chunkPercent, int tilePercent) {
        chunkLine = gridColor(chunkPercent);
        tileLine = gridColor(tilePercent);
        canvas.repaint();
    }

    static Color gridColor(int percent) {
        return new Color(0, 0, 0, Math.round(Math.clamp(percent, 0, 100) * 255 / 100f));
    }

    boolean strokeActive() {
        return stroke != null;
    }

    void invalidateAll() {
        cache.clear();
        previews.invalidateAll();
        canvas.repaint();
    }

    boolean preview() {
        return preview;
    }

    void setThreads(int threads) {
        PreviewCache.resize(previewPool, threads);
        previews.setMaxInFlight(2 * Math.max(1, threads));
    }

    void setPreview(boolean value) {
        if (value != preview) {
            preview = value;
            if (!value) {
                previews.keepOnly(Set.of());
            }
            canvas.repaint();
        }
    }

    void zoomIn() {
        zoomed(viewport.zoomInAt(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
    }

    void zoomOut() {
        zoomed(viewport.zoomOutAt(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
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
        boolean modified = (e.getModifiersEx() & PAN_MODIFIERS) != 0;
        if (SwingUtilities.isMiddleMouseButton(e) || ((left || right) && (spaceDown || modified))) {
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
        double rotation = e.getPreciseWheelRotation();
        if (panAnchor != null || !Double.isFinite(rotation)) {
            return;
        }
        if ((e.getModifiersEx() & PAN_MODIFIERS) != 0) {
            resizeBrush(rotation);
            return;
        }
        radiusRemainder = 0;
        if (e.isShiftDown()) {
            return;
        }
        zoomed(viewport.zoomAt(-rotation * WHEEL_ZOOM, e.getX(), e.getY()));
    }

    private void resizeBrush(double rotation) {
        radiusRemainder += rotation;
        int steps = (int) radiusRemainder;
        radiusRemainder -= steps;
        for (int i = 0; i < Math.min(Math.abs(steps), MAX_RADIUS_STEPS); i++) {
            int step = radiusStep();
            settings.setRadius(settings.radius() + (steps < 0 ? step : -step));
        }
    }

    private void pinch(double magnification) {
        if (panAnchor != null || !Double.isFinite(magnification) || magnification <= -1) {
            return;
        }
        double steps = Math.log(1 + magnification) / Math.log(2);
        Point anchor = mouse != null ? mouse : new Point(canvas.getWidth() / 2, canvas.getHeight() / 2);
        zoomed(viewport.zoomAt(steps, anchor.x, anchor.y));
    }

    private void zoomed(boolean changed) {
        if (changed) {
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
            if (tool.shortcut() == Tool.NO_SHORTCUT) {
                continue;
            }
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
        Tool active = stroke != null ? stroke.tool() : settings.tool();
        List<Layer> overlay = active.paintsLayer() ? settings.paintLayers() : List.of();
        boolean chunkGrid = showGrid && chunkLine.getAlpha() > 0 && 16 * scale >= GRID_MIN_PIXELS;
        boolean tileGrid = showGrid && tileLine.getAlpha() > 0 && Tile.SIZE * scale >= GRID_MIN_PIXELS;
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
                Optional<SurfacePreview.Surface> surface = preview ? previews.surface(pos) : Optional.empty();
                g.drawImage(cache.image(model, pos, lod, overlay, surface.orElse(null)), x0, z0, x1 - x0, z1 - z0,
                        null);
                if (chunkGrid) {
                    g.setColor(chunkLine);
                    for (int offset = 16; offset < Tile.SIZE; offset += 16) {
                        int cx = screenX(pos.minBlockX() + offset);
                        int cz = screenZ(pos.minBlockZ() + offset);
                        g.drawLine(cx, z0, cx, z1 - 1);
                        g.drawLine(x0, cz, x1 - 1, cz);
                    }
                }
                if (tileGrid) {
                    g.setColor(tileLine);
                    g.drawRect(x0, z0, x1 - x0 - 1, z1 - z0 - 1);
                }
            }
        }
        if (preview) {
            previews.keepOnly(visibleTiles(width, height));
        }
        paintBrush(g, scale);
    }

    private Set<TilePos> visibleTiles(int width, int height) {
        Set<TilePos> visible = new HashSet<>();
        int minX = tileIndex(viewport.worldX(0));
        int maxX = tileIndex(viewport.worldX(width));
        int minZ = tileIndex(viewport.worldZ(0));
        int maxZ = tileIndex(viewport.worldZ(height));
        for (int tz = minZ; tz <= maxZ; tz++) {
            for (int tx = minX; tx <= maxX; tx++) {
                visible.add(new TilePos(tx, tz));
            }
        }
        return visible;
    }

    private void paintBrush(Graphics2D g, double scale) {
        if (mouse == null || panAnchor != null || spaceDown) {
            return;
        }
        int bx = blockX(mouse.x);
        int bz = blockZ(mouse.y);
        double cx = viewport.screenX(bx + 0.5);
        double cz = viewport.screenZ(bz + 0.5);
        Tool tool = stroke != null ? stroke.tool() : settings.tool();
        if (tool.editsTiles()) {
            paintTilePreview(g, tool == Tool.DELETE_TILES, bx, bz);
        }
        Shape outline;
        if (settings.shape().square()) {
            double half = Math.max(2, (settings.radius() + 0.5) * scale);
            outline = new Rectangle2D.Double(cx - half, cz - half, 2 * half, 2 * half);
        } else {
            double r = Math.max(2, (settings.radius() + 0.5) * scale);
            outline = new Ellipse2D.Double(cx - r, cz - r, 2 * r, 2 * r);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(3f));
        g.setColor(BRUSH_OUTER);
        g.draw(outline);
        g.setStroke(new BasicStroke(1f));
        g.setColor(BRUSH_INNER);
        g.draw(outline);
    }

    private void paintTilePreview(Graphics2D g, boolean delete, int bx, int bz) {
        WorldModel model = document.model();
        g.setStroke(new BasicStroke(2f));
        for (TilePos pos : BrushStroke.touchedTiles(settings.brush(), bx, bz)) {
            if (model.tile(pos).isPresent() == delete) {
                int x0 = screenX(pos.minBlockX());
                int z0 = screenZ(pos.minBlockZ());
                int w = screenX(pos.minBlockX() + Tile.SIZE) - x0;
                int h = screenZ(pos.minBlockZ() + Tile.SIZE) - z0;
                g.setColor(delete ? DELETE_PREVIEW : ADD_PREVIEW);
                g.fillRect(x0, z0, w, h);
                g.setColor(delete ? DELETE_EDGE : ADD_EDGE);
                g.drawRect(x0 + 1, z0 + 1, w - 2, h - 2);
            }
        }
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
