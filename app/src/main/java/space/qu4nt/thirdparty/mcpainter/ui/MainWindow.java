package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.BackingStoreException;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class MainWindow {

    private final Messages messages;
    private final Settings settings;
    private final Document document;
    private final BrushSettings brush = new BrushSettings();
    private final JFrame frame = new JFrame();
    private final StatusBar status;
    private final MapView map;
    private final JMenuItem undoItem;
    private final JMenuItem redoItem;
    private final boolean nativeQuit;
    private final int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    MainWindow(Messages messages, Settings settings, WorldModel initial) {
        this.messages = messages;
        this.settings = settings;
        this.document = new Document(initial);
        this.status = new StatusBar(messages);
        this.map = new MapView(document, brush, messages, new MapView.Listener() {
            @Override
            public void hovered(int x, int z) {
                status.showColumn(document.model(), x, z);
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
        this.undoItem = item("menu.edit.undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), this::undo);
        this.redoItem = item("menu.edit.redo",
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK), this::redo);
        this.nativeQuit = Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER);

        ToolPanel tools = new ToolPanel(brush, messages);
        TerrainPanel terrain = new TerrainPanel(brush, document, messages, status::notice);
        JPanel content = new JPanel(new BorderLayout());
        content.add(tools.component(), BorderLayout.WEST);
        content.add(map.component(), BorderLayout.CENTER);
        content.add(terrain.component(), BorderLayout.EAST);
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
        if (!nativeQuit) {
            file.add(item("menu.file.exit", null, this::requestClose));
        }
        if (file.getItemCount() > 0) {
            bar.add(file);
        }

        JMenu edit = menu("menu.edit");
        edit.add(undoItem);
        edit.add(redoItem);
        if (!nativeQuit) {
            redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask));
        }
        bar.add(edit);

        JMenu view = menu("menu.view");
        JCheckBoxMenuItem grid = SafeText.plain(new JCheckBoxMenuItem(messages.text("menu.view.grid"), map.showGrid()));
        grid.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, menuMask));
        grid.addActionListener(e -> map.setShowGrid(grid.isSelected()));
        view.add(grid);
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
                if (confirmDiscard()) {
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
        map.finishStroke();
        if (document.model().undo()) {
            map.invalidateAll();
            document.changed();
        }
    }

    private void redo() {
        map.finishStroke();
        if (document.model().redo()) {
            map.invalidateAll();
            document.changed();
        }
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

    boolean confirmDiscard() {
        map.finishStroke();
        if (!document.dirty()) {
            return true;
        }
        return switch (Dialogs.unsaved(frame, messages, document.displayName(messages))) {
            case SAVE -> false;
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    private void requestClose() {
        if (confirmDiscard()) {
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
