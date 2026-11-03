package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class MainWindow {

    private final Messages messages;
    private final Settings settings;
    private final Document document;
    private final BrushSettings brush = new BrushSettings();
    private final JFrame frame = new JFrame();
    private final StatusBar status;
    private final MapView map;
    private final ProjectActions project;
    private final JMenuItem undoItem;
    private final JMenuItem redoItem;
    private final boolean nativeQuit;
    private final int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    private final Performance.Environment environment = Performance.Environment.current();
    private JCheckBoxMenuItem gridItem;
    private GridDialog gridDialog;

    MainWindow(Messages messages, Settings settings, WorldModel initial) {
        this.messages = messages;
        this.settings = settings;
        this.document = new Document(initial);
        this.status = new StatusBar(messages);
        this.map = new MapView(document, brush, messages, new MapView.Listener() {
            @Override
            public void hovered(int x, int z) {
                status.showColumn(document.model(), x, z, brush.tool().paintsLayer() ? brush.paintLayers() : List.of());
            }

            @Override
            public void hoverCleared() {
                status.clearColumn();
            }

            @Override
            public void viewChanged() {
                refreshView();
            }

            @Override
            public void strokeFinished() {
                document.changed();
            }

            @Override
            public void notice(String text) {
                status.notice(text);
            }
        });
        map.setGridOpacity(settings.chunkGridOpacity(), settings.tileGridOpacity());
        map.setPreview(settings.preview());
        map.setThreads(settings.threads(environment));
        this.project = new ProjectActions(frame, messages, document, map);
        this.undoItem = item("menu.edit.undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), this::undo);
        this.redoItem = item("menu.edit.redo",
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK), this::redo);
        this.nativeQuit = Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER);

        BrushActions brushes = new BrushActions(frame, messages, brush, new BrushCatalog(messages),
                BrushLibrary.user(), document, status::notice);
        brushes.loadLibrary();
        ToolPanel tools = new ToolPanel(brush, messages, brushes);
        PresetPanel presets = new PresetPanel(brushes, messages);
        TerrainPanel terrain = new TerrainPanel(brush, document, messages, status::notice);
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        tools.component().setAlignmentX(0);
        JComponent presetSection = presets.component();
        presetSection.setAlignmentX(0);
        JPanel presetHolder = new JPanel(new BorderLayout());
        presetHolder.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        presetHolder.add(presetSection, BorderLayout.CENTER);
        presetHolder.setAlignmentX(0);
        left.add(tools.component());
        left.add(presetHolder);
        JPanel leftColumn = new JPanel(new BorderLayout());
        leftColumn.add(left, BorderLayout.NORTH);
        JScrollPane leftScroll = new JScrollPane(leftColumn, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setBorder(BorderFactory.createEmptyBorder());
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel content = new JPanel(new BorderLayout());
        content.add(leftScroll, BorderLayout.WEST);
        content.add(map.component(), BorderLayout.CENTER);
        JScrollPane rightScroll = new JScrollPane(terrain.component(), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.setBorder(BorderFactory.createEmptyBorder());
        content.add(rightScroll, BorderLayout.EAST);
        content.add(status.component(), BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.setJMenuBar(menuBar());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                map.finishStroke();
            }
        });
        document.onStateChanged(this::refreshState);
        document.onModelReplaced(this::refreshView);
        installDesktopHandlers();
        refreshState();
        refreshView();
    }

    void show() {
        frame.pack();
        frame.setSize(Math.max(frame.getWidth(), 1280), Math.max(frame.getHeight(), 820));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        map.component().requestFocusInWindow();
    }

    JFrame frame() {
        return frame;
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = menu("menu.file");
        file.add(item("menu.file.new", KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask), project::newWorld));
        file.add(item("menu.file.open", KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask), project::open));
        file.add(item("menu.file.save", KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask), project::save));
        file.add(item("menu.file.saveAs",
                KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask | InputEvent.SHIFT_DOWN_MASK), project::saveAs));
        file.addSeparator();
        file.add(item("menu.file.importHeightmap", KeyStroke.getKeyStroke(KeyEvent.VK_I, menuMask),
                project::importHeightmap));
        file.add(item("menu.file.export", KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask), project::export));
        if (!nativeQuit) {
            file.addSeparator();
            file.add(item("menu.file.exit", null, this::requestClose));
        }
        bar.add(file);

        JMenu edit = menu("menu.edit");
        edit.add(undoItem);
        edit.add(redoItem);
        edit.addSeparator();
        edit.add(item("menu.edit.seed", null, this::editSeed));
        edit.add(item("menu.edit.performance", null, this::performanceSettings));
        if (!nativeQuit) {
            redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask));
        }
        bar.add(edit);

        JMenu view = menu("menu.view");
        JCheckBoxMenuItem grid = SafeText.plain(new JCheckBoxMenuItem(messages.text("menu.view.grid"), map.showGrid()));
        grid.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, menuMask));
        grid.addActionListener(e -> map.setShowGrid(grid.isSelected()));
        gridItem = grid;
        view.add(grid);
        view.add(item("menu.view.gridSettings", null, this::gridSettings));
        JCheckBoxMenuItem preview = SafeText.plain(new JCheckBoxMenuItem(messages.text("menu.view.preview"),
                map.preview()));
        preview.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuMask | InputEvent.SHIFT_DOWN_MASK));
        preview.setToolTipText(SafeText.neutralize(messages.text("menu.view.previewHint")));
        preview.addActionListener(e -> setPreview(preview.isSelected()));
        view.add(preview);
        view.addSeparator();
        view.add(item("menu.view.zoomIn", KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuMask), map::zoomIn));
        view.add(item("menu.view.zoomOut", KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuMask), map::zoomOut));
        view.add(item("menu.view.zoomReset", KeyStroke.getKeyStroke(KeyEvent.VK_0, menuMask), map::zoomReset));
        view.add(item("menu.view.fit", KeyStroke.getKeyStroke(KeyEvent.VK_9, menuMask), map::fitToWorld));
        view.addSeparator();
        JMenu language = menu("menu.view.language");
        ButtonGroup languages = new ButtonGroup();
        Language current = settings.language();
        for (Language option : Language.values()) {
            JRadioButtonMenuItem choice = SafeText.plain(new JRadioButtonMenuItem(messages.text(option.messageKey()),
                    option == current));
            choice.addActionListener(e -> changeLanguage(option));
            languages.add(choice);
            language.add(choice);
        }
        view.add(language);
        bar.add(view);

        if (!nativeQuit) {
            JMenu help = menu("menu.help");
            help.add(item("menu.help.about", null, this::about));
            bar.add(help);
        }
        return bar;
    }

    private JMenu menu(String key) {
        return SafeText.plain(new JMenu(messages.text(key)));
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = SafeText.plain(new JMenuItem(messages.text(key)));
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    private void installDesktopHandlers() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (nativeQuit) {
            desktop.setQuitHandler((event, response) -> {
                if (project.confirmDiscard()) {
                    frame.dispose();
                    response.performQuit();
                } else {
                    response.cancelQuit();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> about());
        }
    }

    private void undo() {
        if (!ProjectActions.idle()) {
            return;
        }
        map.finishStroke();
        if (document.model().undo()) {
            map.invalidateAll();
            document.changed();
        }
    }

    private void redo() {
        if (!ProjectActions.idle()) {
            return;
        }
        map.finishStroke();
        if (document.model().redo()) {
            map.invalidateAll();
            document.changed();
        }
    }

    private void editSeed() {
        if (!ProjectActions.idle()) {
            return;
        }
        map.finishStroke();
        WorldModel model = document.model();
        Optional<String> text = Dialogs.prompt(frame, messages, "seed.title", "seed.label",
                Long.toString(model.generation().seed()));
        if (text.isEmpty()) {
            return;
        }
        long seed = Seeds.parse(text.get());
        if (seed == model.generation().seed()) {
            return;
        }
        try (Edit edit = model.beginEdit(messages.text("seed.edit"))) {
            edit.setGeneration(model.generation().withSeed(seed));
        }
        map.invalidateAll();
        document.changed();
    }

    private void setPreview(boolean value) {
        map.setPreview(value);
        try {
            settings.setPreview(value);
        } catch (BackingStoreException | RuntimeException e) {
            status.notice(messages.text("error.settings"));
        }
    }

    private void performanceSettings() {
        PerformanceForm form = new PerformanceForm(messages, environment, settings.areaMemoryMiB(environment),
                settings.threads(environment));
        if (!Dialogs.form(frame, messages, "performance.title", form.panel())) {
            return;
        }
        try {
            if (form.areaMiB() == environment.recommendedAreaMiB() && form.threads() == environment.recommendedThreads()) {
                settings.resetPerformance();
            } else {
                settings.setPerformance(form.areaMiB(), form.threads());
            }
        } catch (BackingStoreException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.settings", e);
        }
        map.setThreads(settings.threads(environment));
    }

    private void gridSettings() {
        if (!map.showGrid()) {
            map.setShowGrid(true);
            gridItem.setSelected(true);
        }
        if (gridDialog == null) {
            gridDialog = new GridDialog(frame, messages, settings, map::setGridOpacity);
        }
        gridDialog.show();
    }

    private void changeLanguage(Language language) {
        try {
            settings.setLanguage(language);
        } catch (BackingStoreException | RuntimeException e) {
            Dialogs.error(frame, messages, "error.unexpected", e);
            return;
        }
        Dialogs.info(frame, messages, "menu.view.language", messages.text("language.restart"));
    }

    private void about() {
        String version = MainWindow.class.getModule().getDescriptor() == null ? "dev"
                : MainWindow.class.getModule().getDescriptor().rawVersion().orElse("dev");
        Dialogs.info(frame, messages, "about.title", messages.text("about.text", version));
    }

    private void requestClose() {
        if (project.confirmDiscard()) {
            frame.dispose();
        }
    }

    private void refreshState() {
        WorldModel model = document.model();
        frame.setTitle(messages.text("app.title", document.displayName(messages),
                document.dirty() ? messages.text("app.modified") : ""));
        boolean idle = !map.strokeActive();
        undoItem.setEnabled(idle && model.canUndo());
        redoItem.setEnabled(idle && model.canRedo());
        SafeText.setText(undoItem, model.undoLabel().map(label -> messages.text("menu.edit.undoNamed", label))
                .orElse(messages.text("menu.edit.undo")));
        refreshView();
    }

    private void refreshView() {
        WorldModel model = document.model();
        status.showView(map.viewport().percent(), model.tileCount(), model.maxTiles());
    }
}
