package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

final class BackgroundTask {

    @FunctionalInterface
    interface Work<T> {

        T call() throws Exception;
    }

    record Outcome<T>(T value, Throwable error) {

        boolean ok() {
            return error == null;
        }
    }

    private static boolean running;

    private BackgroundTask() {
    }

    static boolean running() {
        return running;
    }

    static <T> Outcome<T> run(JFrame owner, Messages messages, String messageKey, Work<T> work) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("background tasks start on the event dispatch thread");
        }
        if (running) {
            throw new IllegalStateException("another background task is running");
        }
        JDialog dialog = new JDialog(owner, messages.text("app.name"), Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 18, 20));
        panel.add(SafeText.label(messages.text(messageKey)), BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        dialog.setContentPane(panel);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 380), dialog.getHeight());
        dialog.setLocationRelativeTo(owner);

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                dialog.dispose();
            }
        };
        running = true;
        try {
            worker.execute();
            dialog.setVisible(true);
            return new Outcome<>(worker.get(), null);
        } catch (ExecutionException e) {
            return new Outcome<>(null, e.getCause() == null ? e : e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome<>(null, e);
        } catch (CancellationException e) {
            return new Outcome<>(null, e);
        } finally {
            running = false;
        }
    }
}
