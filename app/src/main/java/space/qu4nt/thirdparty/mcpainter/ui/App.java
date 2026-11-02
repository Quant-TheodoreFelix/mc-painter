package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.GraphicsEnvironment;
import java.util.Locale;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public final class App {

    private static boolean reporting;

    private App() {
    }

    public static boolean headless() {
        return GraphicsEnvironment.isHeadless();
    }

    public static String headlessMessage() {
        return Messages.load(Settings.user().language()).text("app.headless");
    }

    public static void launch() {
        Settings settings = Settings.user();
        Language language = settings.language();
        Locale.setDefault(language.locale());
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "mc-painter");
        Messages messages = Messages.load(language);
        SwingUtilities.invokeLater(() -> start(settings, messages));
    }

    private static void start(Settings settings, Messages messages) {
        installLookAndFeel();
        MainWindow window = new MainWindow(messages, settings, Worlds.startup());
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            SwingUtilities.invokeLater(() -> report(window, messages, error));
        });
        window.show();
    }

    private static void report(MainWindow window, Messages messages, Throwable error) {
        if (reporting) {
            return;
        }
        reporting = true;
        try {
            Dialogs.error(window.frame(), messages, "error.unexpected", error);
        } finally {
            reporting = false;
        }
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
                return;
            }
        }
    }
}
