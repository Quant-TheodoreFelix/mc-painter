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
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import space.qu4nt.thirdparty.mcpainter.io.SurfacePreview;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class MapView {

    interface Listener {

        void hovered(int x, int z);

        void hoverCleared();

        void viewChanged();

        void strokeFinished();

        void notice(String text);

        void areaRequested(TileRect area);

        void areaModeChanged(boolean active);
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
    static final int GRID_MIN_PIXELS = 12;
    static final int DETAIL_TILE_PIXELS = 16;
    static final int TILE_GRID_PIXELS = 32;
    static final int EDGE_PIXELS = 6;
    private static final Color OUTSIDE_AREA = new Color(0, 0, 0, 120);
    private static final Color NO_AREA = new Color(0, 0, 0, 60);
    private static final Color BOUNDS_EDGE = new Color(255, 255, 255, 110);
    private static final Color AREA_EDGE = new Color(255, 255, 255, 235);
    private static final Color SELECTION_FILL = new Color(80, 200, 255, 50);
    private static final Color SELECTION_EDGE = new Color(80, 200, 255, 240);
    private static final Color SELECTION_OVER = new Color(255, 80, 80, 240);
    private static final Color PENDING_EDGE = new Color(255, 166, 64, 200);
    private static final Color LABEL_BACK = new Color(0, 0, 0, 170);
    private static final int HANDLE = 4;

    private final Document document;
    private final BrushSettings settings;
    private final Messages messages;
    private final Listener listener;
    private final Viewport viewport = new Viewport();
    private final TileImageCache cache = new TileImageCache(TileImageCache.DEFAULT_MAX_BYTES);
    private final OverviewRenderer overview = new OverviewRenderer(cache);
    private final AreaSelection selection = new AreaSelection();
    private boolean areaMode;
    private long dataVersion;
    private IntSupplier budget = () -> Integer.MAX_VALUE;
    private ToLongFunction<TileRect> areaCost = TileRect::count;
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
                dataVersion++;
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
            overview.clearThumbs();
            dataVersion++;
            selection.set(document.area().orElse(null));
            setAreaMode(document.workspace().isPresent() && document.area().isEmpty());
            fitToWorld();
        });
        document.onStateChanged(() -> {
            dataVersion++;
            overview.clearThumbs();
            canvas.repaint();
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

    void setAreaBudget(IntSupplier tiles, ToLongFunction<TileRect> cost) {
        budget = tiles;
        areaCost = cost;
    }

    boolean areaMode() {
        return areaMode;
    }

    void setAreaMode(boolean value) {
        boolean next = value && document.workspace().isPresent();
        if (next == areaMode) {
            return;
        }
        areaMode = next;
        if (next) {
            finishStroke();
            if (selection.rect().isEmpty()) {
                selection.set(document.area().orElse(null));
            }
        }
        selection.release();
        updateCursor();
        listener.areaModeChanged(next);
    }

    Optional<TileRect> selection() {
        return selection.rect();
    }

    void requestArea() {
        Optional<TileRect> rect = selection.rect();
        if (rect.isPresent() && document.workspace().isPresent()) {
            listener.areaRequested(rect.get());
        }
    }

    void fitWorldBounds() {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        Optional<TileRect> world = worldRect();
        if (width <= 0 || height <= 0 || world.isEmpty()) {
            fitToWorld();
            return;
        }
        TileRect rect = world.get();
        viewport.fit(rect.minBlockX(), rect.minBlockZ(), rect.maxBlockX() + 1, rect.maxBlockZ() + 1, width, height);
        listener.viewChanged();
        canvas.repaint();
    }

    void invalidateAll() {
        dataVersion++;
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
        Optional<TileRect> target = fitRect();
        if (target.isEmpty()) {
            viewport.setZoom(0, 0, 0);
            viewport.centerOn(0, 0, width, height);
            return;
        }
        TileRect rect = target.get();
        viewport.fit(rect.minBlockX(), rect.minBlockZ(), rect.maxBlockX() + 1, rect.maxBlockZ() + 1, width, height);
    }

    private Optional<TileRect> fitRect() {
        Optional<Workspace> workspace = document.workspace();
        WorldModel model = document.model();
        if (workspace.isPresent() && model.base().filled()) {
            return document.area().or(() -> Optional.of(workspace.get().spec().bounds()));
        }
        Optional<TileRect> tiles = tileBox(model.tilePositions());
        return tiles.isPresent() ? tiles : worldRect();
    }

    private Optional<TileRect> worldRect() {
        Optional<Workspace> workspace = document.workspace();
        if (workspace.isEmpty()) {
            return tileBox(document.model().tilePositions());
        }
        if (workspace.get().spec().base().filled()) {
            return Optional.of(workspace.get().spec().bounds());
        }
        List<TilePos> stored = new java.util.ArrayList<>(document.model().tilePositions());
        for (Workspace.Entry entry : workspace.get().entries()) {
            stored.add(entry.pos());
        }
        return tileBox(stored);
    }

    private static Optional<TileRect> tileBox(java.util.Collection<TilePos> positions) {
        if (positions.isEmpty()) {
            return Optional.empty();
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TilePos pos : positions) {
            minX = Math.min(minX, pos.x());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxZ = Math.max(maxZ, pos.z());
        }
        return Optional.of(new TileRect(minX, minZ, maxX, maxZ));
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
        if (areaMode) {
            if (left && document.workspace().isPresent()) {
                if (e.getClickCount() == 2 && selection.rect().filter(r -> r.contains(tileAt(e.getPoint()))).isPresent()) {
                    requestArea();
                    return;
                }
                selection.press(tileAt(e.getPoint()), edgesAt(e.getPoint()), document.workspace().get().spec().bounds());
                activeButton = e.getButton();
                canvas.repaint();
            }
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
        } else if (selection.dragging()) {
            selection.release();
            activeButton = MouseEvent.NOBUTTON;
            canvas.repaint();
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
        } else if (selection.dragging() && document.workspace().isPresent()) {
            selection.dragTo(tileAt(e.getPoint()), document.workspace().get().spec().bounds());
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

    private TilePos tileAt(Point point) {
        return TilePos.ofBlock(blockX(point.x), blockZ(point.y));
    }

    private int edgesAt(Point point) {
        Optional<TileRect> rect = selection.rect();
        if (rect.isEmpty()) {
            return 0;
        }
        TileRect r = rect.get();
        return AreaSelection.edgesNear(point.x, point.y, viewport.screenX(r.minBlockX()),
                viewport.screenZ(r.minBlockZ()), viewport.screenX(r.maxBlockX() + 1.0),
                viewport.screenZ(r.maxBlockZ() + 1.0), EDGE_PIXELS);
    }

    private int blockX(int screenX) {
        return (int) Math.floor(viewport.worldX(screenX));
    }

    private int blockZ(int screenZ) {
        return (int) Math.floor(viewport.worldZ(screenZ));
    }

    private void updateCursor() {
        int type = panAnchor != null || spaceDown ? Cursor.MOVE_CURSOR
                : areaMode ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR;
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
        bind("ENTER", "areaLoad", () -> {
            if (areaMode) {
                requestArea();
            }
        });
        bind("ESCAPE", "areaCancel", () -> setAreaMode(false));
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
        Optional<Workspace> workspace = document.workspace();
        double scale = viewport.scale();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Optional<TileRect> gridArea = showGrid ? gridRect(workspace, model) : Optional.empty();
        if (workspace.isPresent() && (areaMode || Tile.SIZE * scale < DETAIL_TILE_PIXELS)) {
            OverviewRenderer.Grid grid = gridArea.map(rect -> new OverviewRenderer.Grid(rect, chunkLine.getRGB(),
                    tileLine.getRGB(), (long) gridStepTiles(scale) * Tile.SIZE)).orElse(null);
            g.drawImage(overview.render(viewport, width, height, model, workspace.get(),
                    BACKGROUND.getRGB() & 0xFFFFFF, dataVersion, grid), 0, 0, null);
        } else {
            paintTiles(g, clip, model, workspace);
            gridArea.ifPresent(rect -> paintGrid(g, rect, width, height));
        }
        if (workspace.isPresent()) {
            paintBounds(g, workspace.get(), width, height);
        }
        if (preview) {
            previews.keepOnly(visibleTiles(width, height));
        }
        if (areaMode) {
            paintSelection(g);
        } else {
            paintBrush(g, scale);
        }
        paintHint(g, width, height, workspace);
    }

    private void paintTiles(Graphics2D g, Rectangle clip, WorldModel model, Optional<Workspace> workspace) {
        double scale = viewport.scale();
        int lod = Math.min(viewport.lod(), TileRenderer.MAX_LOD);
        int minTileX = tileIndex(viewport.worldX(clip.x));
        int maxTileX = tileIndex(viewport.worldX(clip.x + clip.width));
        int minTileZ = tileIndex(viewport.worldZ(clip.y));
        int maxTileZ = tileIndex(viewport.worldZ(clip.y + clip.height));
        Tool active = stroke != null ? stroke.tool() : settings.tool();
        List<Layer> overlay = active.paintsLayer() ? settings.paintLayers() : List.of();
        TileRect bounds = workspace.map(w -> w.spec().bounds()).orElse(TileRect.WORLD);
        if (workspace.isPresent()) {
            int base = Session.baseColor(workspace.get().spec());
            if (base >= 0) {
                Rectangle area = screenRect(bounds).intersection(clip);
                if (!area.isEmpty()) {
                    g.setColor(new Color(base));
                    g.fill(area);
                }
            }
        }
        for (int tz = minTileZ; tz <= maxTileZ; tz++) {
            for (int tx = minTileX; tx <= maxTileX; tx++) {
                TilePos pos = new TilePos(tx, tz);
                if (!bounds.contains(pos)) {
                    continue;
                }
                int x0 = screenX(pos.minBlockX());
                int x1 = screenX(pos.minBlockX() + Tile.SIZE);
                int z0 = screenZ(pos.minBlockZ());
                int z1 = screenZ(pos.minBlockZ() + Tile.SIZE);
                if (model.tile(pos).isEmpty()) {
                    if (workspace.isPresent()) {
                        java.awt.image.BufferedImage thumb = overview.thumb(workspace.get(), pos);
                        if (thumb != null) {
                            g.drawImage(thumb, x0, z0, x1 - x0, z1 - z0, null);
                            if (workspace.get().entry(pos).map(Workspace.Entry::pending).orElse(false)) {
                                g.setColor(PENDING_EDGE);
                                g.drawRect(x0 + 1, z0 + 1, x1 - x0 - 3, z1 - z0 - 3);
                            }
                        }
                    }
                    continue;
                }
                Optional<SurfacePreview.Surface> surface = preview ? previews.surface(pos) : Optional.empty();
                g.drawImage(cache.image(model, pos, lod, overlay, surface.orElse(null)), x0, z0, x1 - x0, z1 - z0,
                        null);
            }
        }
    }

    static int gridStepTiles(double scale) {
        int step = 1;
        while (step < (1 << 20) && step * Tile.SIZE * scale < TILE_GRID_PIXELS) {
            step <<= 1;
        }
        return step;
    }

    private Optional<TileRect> gridRect(Optional<Workspace> workspace, WorldModel model) {
        if (workspace.isPresent() && workspace.get().spec().base().filled()) {
            return Optional.of(workspace.get().spec().bounds());
        }
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        model.forEachTile((pos, tile) -> {
            box[0] = Math.min(box[0], pos.x());
            box[1] = Math.min(box[1], pos.z());
            box[2] = Math.max(box[2], pos.x());
            box[3] = Math.max(box[3], pos.z());
        });
        return box[0] > box[2] ? Optional.empty() : Optional.of(new TileRect(box[0], box[1], box[2], box[3]));
    }

    private void paintGrid(Graphics2D g, TileRect rect, int width, int height) {
        double scale = viewport.scale();
        Rectangle area = screenRect(rect).intersection(new Rectangle(0, 0, width, height));
        if (area.isEmpty()) {
            return;
        }
        if (chunkLine.getAlpha() > 0 && 16 * scale >= GRID_MIN_PIXELS) {
            g.setColor(chunkLine);
            gridLines(g, area, 16);
        }
        if (tileLine.getAlpha() > 0) {
            g.setColor(tileLine);
            gridLines(g, area, (long) gridStepTiles(scale) * Tile.SIZE);
        }
    }

    private void gridLines(Graphics2D g, Rectangle area, long spacing) {
        double left = viewport.worldX(area.x);
        double right = viewport.worldX(area.x + area.width);
        double top = viewport.worldZ(area.y);
        double bottom = viewport.worldZ(area.y + area.height);
        for (long x = Math.ceilDiv((long) Math.floor(left), spacing) * spacing; x <= right; x += spacing) {
            int sx = (int) Math.floor(viewport.screenX(x));
            g.drawLine(sx, area.y, sx, area.y + area.height - 1);
        }
        for (long z = Math.ceilDiv((long) Math.floor(top), spacing) * spacing; z <= bottom; z += spacing) {
            int sz = (int) Math.floor(viewport.screenZ(z));
            g.drawLine(area.x, sz, area.x + area.width - 1, sz);
        }
    }

    private void paintBounds(Graphics2D g, Workspace workspace, int width, int height) {
        TileRect bounds = workspace.spec().bounds();
        Rectangle world = screenRect(bounds);
        Rectangle view = new Rectangle(0, 0, width, height);
        Optional<TileRect> area = document.area();
        Rectangle visibleWorld = world.intersection(view);
        if (!visibleWorld.isEmpty()) {
            if (area.isEmpty()) {
                g.setColor(NO_AREA);
                g.fill(visibleWorld);
            } else {
                Rectangle inside = screenRect(area.get());
                java.awt.geom.Area dim = new java.awt.geom.Area(visibleWorld);
                dim.subtract(new java.awt.geom.Area(inside));
                g.setColor(OUTSIDE_AREA);
                g.fill(dim);
            }
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(BOUNDS_EDGE);
        g.draw(world.intersection(new Rectangle(-4, -4, width + 8, height + 8)));
        if (area.isPresent() && !area.get().equals(bounds)) {
            g.setColor(AREA_EDGE);
            g.setStroke(new BasicStroke(2f));
            g.draw(screenRect(area.get()).intersection(new Rectangle(-4, -4, width + 8, height + 8)));
            g.setStroke(new BasicStroke(1f));
        }
    }

    private void paintSelection(Graphics2D g) {
        Optional<TileRect> rect = selection.rect();
        if (rect.isEmpty()) {
            return;
        }
        TileRect r = rect.get();
        long cost = areaCost.applyAsLong(r);
        int limit = budget.getAsInt();
        boolean over = cost > limit;
        Rectangle box = screenRect(r);
        g.setColor(SELECTION_FILL);
        g.fill(box.intersection(new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight())));
        g.setColor(over ? SELECTION_OVER : SELECTION_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.draw(box.intersection(new Rectangle(-4, -4, canvas.getWidth() + 8, canvas.getHeight() + 8)));
        g.setStroke(new BasicStroke(1f));
        for (int corner = 0; corner < 4; corner++) {
            int hx = corner % 2 == 0 ? box.x : box.x + box.width;
            int hz = corner < 2 ? box.y : box.y + box.height;
            g.fillRect(hx - HANDLE, hz - HANDLE, 2 * HANDLE, 2 * HANDLE);
        }
        String text = messages.text("area.selection", Forms.number(r.width()), Forms.number(r.depth()),
                Forms.number((long) r.width() * Tile.SIZE), Forms.number((long) r.depth() * Tile.SIZE),
                Forms.number(cost), Forms.number(limit));
        String safe = SafeText.neutralize(text);
        int textWidth = g.getFontMetrics().stringWidth(safe);
        int lx = Math.clamp(box.x, 8, Math.max(8, canvas.getWidth() - textWidth - 10));
        int lz = Math.clamp(box.y - 10, 50, Math.max(50, canvas.getHeight() - 8));
        paintLabel(g, safe, lx, lz, over ? SELECTION_OVER : Color.WHITE);
    }

    private void paintHint(Graphics2D g, int width, int height, Optional<Workspace> workspace) {
        String key;
        if (workspace.isEmpty()) {
            key = "map.emptyHint";
        } else if (areaMode) {
            key = "area.modeHint";
        } else if (document.area().isEmpty()) {
            key = "area.noAreaHint";
        } else {
            return;
        }
        String text = SafeText.neutralize(messages.text(key));
        int textWidth = g.getFontMetrics().stringWidth(text);
        paintLabel(g, text, Math.max(8, (width - textWidth) / 2), workspace.isEmpty() ? height / 2 : 24, Color.WHITE);
    }

    private void paintLabel(Graphics2D g, String text, int x, int baseline, Color color) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        java.awt.FontMetrics metrics = g.getFontMetrics();
        int w = metrics.stringWidth(text);
        g.setColor(LABEL_BACK);
        g.fillRoundRect(x - 6, baseline - metrics.getAscent() - 4, w + 12, metrics.getHeight() + 6, 8, 8);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private Rectangle screenRect(TileRect rect) {
        double x0 = Math.clamp(viewport.screenX(rect.minBlockX()), -1e6, 1e6);
        double z0 = Math.clamp(viewport.screenZ(rect.minBlockZ()), -1e6, 1e6);
        double x1 = Math.clamp(viewport.screenX(rect.maxBlockX() + 1.0), -1e6, 1e6);
        double z1 = Math.clamp(viewport.screenZ(rect.maxBlockZ() + 1.0), -1e6, 1e6);
        int ix = (int) Math.floor(x0);
        int iz = (int) Math.floor(z0);
        return new Rectangle(ix, iz, Math.max(1, (int) Math.floor(x1) - ix), Math.max(1, (int) Math.floor(z1) - iz));
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
