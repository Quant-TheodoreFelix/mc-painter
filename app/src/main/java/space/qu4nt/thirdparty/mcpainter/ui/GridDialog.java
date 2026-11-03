package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.BackingStoreException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;

final class GridDialog {

    interface Target {
        void gridOpacity(int chunkPercent, int tilePercent);
    }

    private final JDialog dialog;
    private final Messages messages;
    private final Settings settings;
    private final Target target;
    private final JSlider chunk;
    private final JSlider tile;
    private final JLabel chunkValue = SafeText.label("");
    private final JLabel tileValue = SafeText.label("");

    GridDialog(JFrame owner, Messages messages, Settings settings, Target target) {
        this.messages = messages;
        this.settings = settings;
        this.target = target;
        this.chunk = slider(settings.chunkGridOpacity());
        this.tile = slider(settings.tileGridOpacity());
        dialog = new JDialog(owner, SafeText.neutralize(messages.text("grid.title")), false);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });

        JPanel rows = new JPanel(new GridBagLayout());
        row(rows, 0, "grid.chunk", chunk, chunkValue);
        row(rows, 1, "grid.tile", tile, tileValue);
        ToolPanel.addWide(rows, 2, SafeText.message(messages.text("grid.hint"), 30));

        JButton reset = SafeText.plain(new JButton(messages.text("grid.reset")));
        reset.addActionListener(e -> {
            chunk.setValue(Settings.DEFAULT_CHUNK_GRID);
            tile.setValue(Settings.DEFAULT_TILE_GRID);
        });
        JButton done = SafeText.plain(new JButton(messages.text("dialog.close")));
        done.addActionListener(e -> close());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        buttons.add(reset);
        buttons.add(done);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        content.add(rows, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(done);
        chunk.addChangeListener(e -> changed());
        tile.addChangeListener(e -> changed());
        changed();
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
    }

    void show() {
        dialog.setVisible(true);
        dialog.toFront();
    }

    int chunkPercent() {
        return chunk.getValue();
    }

    int tilePercent() {
        return tile.getValue();
    }

    void setPercents(int chunkPercent, int tilePercent) {
        chunk.setValue(chunkPercent);
        tile.setValue(tilePercent);
    }

    JDialog dialog() {
        return dialog;
    }

    private void changed() {
        SafeText.setText(chunkValue, messages.text("grid.percent", Integer.toString(chunk.getValue())));
        SafeText.setText(tileValue, messages.text("grid.percent", Integer.toString(tile.getValue())));
        target.gridOpacity(chunk.getValue(), tile.getValue());
    }

    private void close() {
        dialog.setVisible(false);
        try {
            settings.setGridOpacity(chunk.getValue(), tile.getValue());
        } catch (BackingStoreException | RuntimeException e) {
            Dialogs.error(dialog.getOwner(), messages, "error.settings", e);
        }
    }

    private void row(JPanel rows, int index, String key, JSlider slider, JLabel value) {
        JPanel field = new JPanel(new BorderLayout(8, 0));
        field.add(slider, BorderLayout.CENTER);
        field.add(value, BorderLayout.EAST);
        ToolPanel.addRow(rows, index, SafeText.label(messages.text(key)), field);
    }

    private static JSlider slider(int value) {
        JSlider slider = new JSlider(0, 100, value);
        slider.setMajorTickSpacing(25);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        return slider;
    }
}
