package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import space.qu4nt.thirdparty.mcpainter.io.Progress;

final class BackgroundTask {

    @FunctionalInterface
    interface Work<T> {

        T call() throws Exception;
    }

    @FunctionalInterface
    interface ProgressWork<T> {

        T call(Progress progress) throws Exception;
    }

    record Outcome<T>(T value, Throwable error) {

        boolean ok() {
            return error == null;
        }

        boolean cancelled() {
            return error instanceof CancellationException;
        }
    }

    static final int BAR_STEPS = 1000;
    static final int REFRESH_MILLIS = 150;

    private static boolean running;

    private BackgroundTask() {
    }

    static boolean running() {
        return running;
    }

    static <T> Outcome<T> runWithProgress(JFrame owner, Messages messages, String messageKey, ProgressWork<T> work) {
        requireIdle();
        AtomicLong done = new AtomicLong();
        AtomicLong total = new AtomicLong();
        AtomicBoolean cancel = new AtomicBoolean();
        JDialog dialog = new JDialog(owner, messages.text("app.name"), Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar bar = new JProgressBar(0, BAR_STEPS);
        bar.setIndeterminate(true);
        JLabel detail = SafeText.label(" ");
        JButton stop = SafeText.plain(new JButton(messages.text("task.cancel")));
        stop.addActionListener(e -> {
            cancel.set(true);
            stop.setEnabled(false);
            SafeText.setText(detail, messages.text("task.cancelling"));
        });
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(bar, BorderLayout.NORTH);
        center.add(detail, BorderLayout.SOUTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(stop);
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        panel.add(SafeText.label(messages.text(messageKey)), BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 460), dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        long started = System.nanoTime();
        Timer refresh = new Timer(REFRESH_MILLIS, e -> {
            long t = total.get();
            long d = done.get();
            if (t <= 0 || cancel.get()) {
                return;
            }
            bar.setIndeterminate(false);
            double fraction = Math.clamp(d / (double) t, 0.0, 1.0);
            bar.setValue((int) Math.round(fraction * BAR_STEPS));
            String text = messages.text("task.progress", Long.toString(d), Long.toString(t));
            double elapsed = (System.nanoTime() - started) / 1e9;
            if (fraction > 0.01 && fraction < 1) {
                text = text + "  " + messages.text("task.remaining",
                        duration(messages, (long) Math.ceil(elapsed * (1 - fraction) / fraction)));
            }
            SafeText.setText(detail, text);
        });
        Progress progress = (d, t) -> {
            done.set(d);
            total.set(t);
            return !cancel.get();
        };
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call(progress);
            }

            @Override
            protected void done() {
                refresh.stop();
                dialog.dispose();
            }
        };
        return await(worker, dialog, refresh);
    }

    static String duration(Messages messages, long seconds) {
        if (seconds < 60) {
            return messages.text("time.seconds", Long.toString(Math.max(1, seconds)));
        }
        long minutes = Math.ceilDiv(seconds, 60);
        if (minutes < 60) {
            return messages.text("time.minutes", Long.toString(minutes));
        }
        return messages.text("time.hours", Long.toString(minutes / 60), Long.toString(minutes % 60));
    }

    private static void requireIdle() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("background tasks start on the event dispatch thread");
        }
        if (running) {
            throw new IllegalStateException("another background task is running");
        }
    }

    private static <T> Outcome<T> await(SwingWorker<T, Void> worker, JDialog dialog, Timer refresh) {
        running = true;
        try {
            worker.execute();
            if (refresh != null) {
                refresh.start();
            }
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
            if (refresh != null) {
                refresh.stop();
            }
            running = false;
        }
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
        return await(worker, dialog, null);
    }
}
