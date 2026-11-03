package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class StatusBar {

    private static final int NOTICE_MILLIS = 6000;
    private static final String EMPTY = " ";

    private final Messages messages;
    private final JPanel panel = new JPanel(new BorderLayout(24, 0));
    private final JLabel column = SafeText.label(EMPTY);
    private final JLabel notice = SafeText.label(EMPTY);
    private final JLabel view = SafeText.label(EMPTY);
    private final Timer clearNotice;

    StatusBar(Messages messages) {
        this.messages = messages;
        Color line = UIManager.getColor("Separator.foreground");
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, line == null ? Color.GRAY : line),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        panel.add(column, BorderLayout.WEST);
        panel.add(notice, BorderLayout.CENTER);
        panel.add(view, BorderLayout.EAST);
        clearNotice = new Timer(NOTICE_MILLIS, e -> SafeText.setText(notice, EMPTY));
        clearNotice.setRepeats(false);
    }

    JComponent component() {
        return panel;
    }

    void showColumn(WorldModel model, int x, int z) {
        if (!model.contains(x, z)) {
            SafeText.setText(column, messages.text("status.outside", Integer.toString(x), Integer.toString(z)));
            return;
        }
        SafeText.setText(column, messages.text("status.column", Integer.toString(x), Integer.toString(z),
                String.format(Locale.ROOT, "%.1f", model.height(x, z)),
                messages.text(Labels.terrainKey(model.terrain(x, z))),
                Integer.toString(model.waterLevel(x, z)), Labels.biomeName(messages, model.biome(x, z))));
    }

    void clearColumn() {
        SafeText.setText(column, EMPTY);
    }

    void showView(int zoomPercent, int tiles, int maxTiles) {
        SafeText.setText(view, messages.text("status.view", Integer.toString(zoomPercent), Integer.toString(tiles),
                Integer.toString(maxTiles)));
    }

    void notice(String text) {
        SafeText.setText(notice, text);
        clearNotice.restart();
    }
}
