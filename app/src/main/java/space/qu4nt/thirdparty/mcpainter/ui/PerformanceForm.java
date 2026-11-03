package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.GridBagLayout;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

final class PerformanceForm {

    static final int MEMORY_STEP_MIB = 256;

    private final Messages messages;
    private final Performance.Environment environment;
    private final JSpinner memory;
    private final JSpinner threads;
    private final JLabel capacity = SafeText.label(" ");
    private final JPanel panel = new JPanel(new GridBagLayout());

    PerformanceForm(Messages messages, Performance.Environment environment, int areaMiB, int threadCount) {
        this.messages = messages;
        this.environment = environment;
        memory = new JSpinner(new SpinnerNumberModel(Math.clamp(areaMiB, Performance.MIN_AREA_MIB,
                environment.maxAreaMiB()), Performance.MIN_AREA_MIB, environment.maxAreaMiB(), MEMORY_STEP_MIB));
        threads = new JSpinner(new SpinnerNumberModel(Math.clamp(threadCount, 1, environment.maxThreads()), 1,
                environment.maxThreads(), 1));
        memory.addChangeListener(e -> refresh());
        JButton recommended = SafeText.plain(new JButton(messages.text("performance.recommended")));
        recommended.addActionListener(e -> {
            memory.setValue(environment.recommendedAreaMiB());
            threads.setValue(environment.recommendedThreads());
        });
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        String physical = environment.physical() > 0 ? gigabytes(environment.physical())
                : messages.text("performance.unknown");
        int row = 0;
        ToolPanel.addWide(panel, row++, SafeText.label(messages.text("performance.environment",
                Integer.toString(environment.cores()), physical, gigabytes(environment.maxHeap()))));
        ToolPanel.addRow(panel, row++, SafeText.label(messages.text("performance.memory")), memory);
        ToolPanel.addWide(panel, row++, capacity);
        ToolPanel.addRow(panel, row++, SafeText.label(messages.text("performance.threads")), threads);
        ToolPanel.addWide(panel, row++, SafeText.label(messages.text("performance.suggestion",
                gigabytes(environment.recommendedAreaMiB() * Performance.MIB),
                Integer.toString(environment.recommendedThreads()))));
        ToolPanel.addWide(panel, row++, recommended);
        ToolPanel.addWide(panel, row, SafeText.message(messages.text("performance.heapNote")));
        refresh();
    }

    JPanel panel() {
        return panel;
    }

    int areaMiB() {
        return ((Number) memory.getValue()).intValue();
    }

    int threads() {
        return ((Number) threads.getValue()).intValue();
    }

    private void refresh() {
        int tiles = Performance.areaTiles(areaMiB());
        int side = Performance.areaSideBlocks(tiles);
        SafeText.setText(capacity, messages.text("performance.capacity", String.format(Locale.ROOT, "%,d", tiles),
                String.format(Locale.ROOT, "%,d", side)));
    }

    static String gigabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1024L * 1024 * 1024));
    }
}
