package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import space.qu4nt.thirdparty.mcpainter.io.BrushMaskImporter;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

final class ShapeImportForm {

    static final int PREVIEW_SIZE = 160;

    private final Messages messages;
    private final BrushMaskImporter.Source source;
    private final JPanel panel = new JPanel(new BorderLayout(14, 0));
    private final JLabel preview = SafeText.label("");
    private final JLabel status = SafeText.label(" ");
    private final JTextField name;
    private final JCheckBox autoLevels;
    private final JCheckBox invert;

    ShapeImportForm(Messages messages, BrushMaskImporter.Source source, String suggestedName) {
        this.messages = messages;
        this.source = source;
        preview.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        preview.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY));
        name = SafeText.plain(new JTextField(suggestedName, 20));
        autoLevels = SafeText.plain(new JCheckBox(messages.text("shape.autoLevels"), true));
        autoLevels.setToolTipText(messages.text("shape.autoLevelsHint"));
        invert = SafeText.plain(new JCheckBox(messages.text("shape.invert"), false));
        autoLevels.addActionListener(e -> refresh());
        invert.addActionListener(e -> refresh());

        JPanel fields = new JPanel(new GridBagLayout());
        ToolPanel.addRow(fields, 0, SafeText.label(messages.text("brush.name")), name);
        ToolPanel.addWide(fields, 1, SafeText.label(messages.text("shape.source", source.format(),
                Integer.toString(source.sourceWidth()), Integer.toString(source.sourceHeight()),
                Integer.toString(source.size()))));
        ToolPanel.addWide(fields, 2, autoLevels);
        ToolPanel.addWide(fields, 3, invert);
        ToolPanel.addWide(fields, 4, status);
        JPanel north = new JPanel(new BorderLayout());
        north.add(fields, BorderLayout.NORTH);
        panel.add(preview, BorderLayout.WEST);
        panel.add(north, BorderLayout.CENTER);
        refresh();
    }

    JPanel panel() {
        return panel;
    }

    String name() {
        return name.getText();
    }

    Optional<BrushShape.Mask> mask() {
        try {
            return Optional.of(source.toMask(autoLevels.isSelected(), invert.isSelected()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    void setOptions(boolean levels, boolean inverted) {
        autoLevels.setSelected(levels);
        invert.setSelected(inverted);
        refresh();
    }

    private void refresh() {
        Optional<BrushShape.Mask> mask = mask();
        if (mask.isPresent()) {
            preview.setIcon(new ImageIcon(ShapeEntry.thumbnail(mask.get(), PREVIEW_SIZE)));
            SafeText.setText(status, " ");
        } else {
            preview.setIcon(null);
            SafeText.setText(status, messages.text("shape.empty"));
        }
    }
}
