package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

final class ShapeBatchForm {

    static final int THUMB_SIZE = 64;
    static final int COLUMNS = 5;
    static final int VISIBLE_ROWS = 3;
    static final int CELL_WIDTH = THUMB_SIZE + 28;
    static final int CELL_HEIGHT = THUMB_SIZE + 26;
    static final int GAP = 6;

    private final Messages messages;
    private final List<ShapeBatch.Item> items;
    private final List<JLabel> cells = new ArrayList<>();
    private final JPanel panel = new JPanel(new BorderLayout(0, 10));
    private final JLabel status = SafeText.label(" ");
    private final JCheckBox autoLevels;
    private final JCheckBox invert;

    ShapeBatchForm(Messages messages, ShapeBatch.Previews previews) {
        this.messages = messages;
        this.items = previews.items();
        autoLevels = SafeText.plain(new JCheckBox(messages.text("shape.autoLevels"), true));
        autoLevels.setToolTipText(messages.text("shape.autoLevelsHint"));
        invert = SafeText.plain(new JCheckBox(messages.text("shape.invert"), false));
        autoLevels.addActionListener(e -> refresh());
        invert.addActionListener(e -> refresh());

        JPanel grid = new JPanel(new GridLayout(0, COLUMNS, GAP, GAP));
        grid.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        for (ShapeBatch.Item item : items) {
            JLabel cell = SafeText.label(item.name());
            cell.setToolTipText(SafeText.neutralize(item.name()));
            cell.setHorizontalAlignment(SwingConstants.CENTER);
            cell.setHorizontalTextPosition(SwingConstants.CENTER);
            cell.setVerticalTextPosition(SwingConstants.BOTTOM);
            cell.setPreferredSize(new Dimension(CELL_WIDTH, CELL_HEIGHT));
            cells.add(cell);
            grid.add(cell);
        }
        int rows = Math.min(VISIBLE_ROWS, (items.size() + COLUMNS - 1) / COLUMNS);
        JScrollPane scroll = new JScrollPane(grid, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(CELL_HEIGHT / 3);
        int scrollBar = UIManager.getInt("ScrollBar.width");
        scroll.setPreferredSize(new Dimension(COLUMNS * (CELL_WIDTH + GAP) + GAP * 2 + Math.max(scrollBar, 16),
                rows * (CELL_HEIGHT + GAP) + GAP * 2));

        JPanel fields = new JPanel(new GridBagLayout());
        int row = 0;
        ToolPanel.addWide(fields, row++, autoLevels);
        ToolPanel.addWide(fields, row++, invert);
        ToolPanel.addWide(fields, row++, status);
        if (!previews.unreadable().isEmpty()) {
            ToolPanel.addWide(fields, row++, SafeText.message(messages.text("shape.batchUnreadable",
                    Integer.toString(previews.unreadable().size()), ShapeBatch.list(messages, previews.unreadable()))));
        }
        if (previews.omitted() > 0) {
            ToolPanel.addWide(fields, row, SafeText.message(messages.text("shape.batchOmitted",
                    Integer.toString(ShapeBatch.MAX_FILES), Integer.toString(previews.omitted()))));
        }
        panel.add(SafeText.label(messages.text("shape.batchCount", Integer.toString(items.size()))),
                BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(fields, BorderLayout.SOUTH);
        refresh();
    }

    JPanel panel() {
        return panel;
    }

    boolean autoLevels() {
        return autoLevels.isSelected();
    }

    boolean invert() {
        return invert.isSelected();
    }

    int emptyCount() {
        int empty = 0;
        for (ShapeBatch.Item item : items) {
            if (ShapeBatch.empty(item, autoLevels(), invert())) {
                empty++;
            }
        }
        return empty;
    }

    private void refresh() {
        int empty = 0;
        for (int i = 0; i < items.size(); i++) {
            ShapeBatch.Item item = items.get(i);
            JLabel cell = cells.get(i);
            try {
                cell.setIcon(new ImageIcon(ShapeEntry.thumbnail(item.preview().toMask(autoLevels(), invert()),
                        THUMB_SIZE)));
                cell.setEnabled(true);
            } catch (IllegalArgumentException e) {
                cell.setIcon(new ImageIcon(ShapeEntry.blank(THUMB_SIZE)));
                cell.setEnabled(false);
                empty++;
            }
        }
        SafeText.setText(status, empty == 0 ? " " : messages.text("shape.batchEmpty", Integer.toString(empty)));
    }
}
