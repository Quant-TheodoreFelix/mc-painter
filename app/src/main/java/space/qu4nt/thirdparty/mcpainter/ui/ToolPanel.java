package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import space.qu4nt.thirdparty.mcpainter.world.Brush;

final class ToolPanel {

    private final BrushSettings settings;
    private final Messages messages;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final Map<Tool, JToggleButton> buttons = new EnumMap<>(Tool.class);
    private final JSlider radiusSlider;
    private final JSpinner radiusSpinner;
    private final JSlider strengthSlider;
    private final JLabel strengthValue = SafeText.label("");
    private final JComboBox<Brush.Falloff> falloff;
    private final JCheckBox autoAddTiles;
    private final BrushActions actions;
    private final DefaultComboBoxModel<ShapeEntry> shapeModel = new DefaultComboBoxModel<>();
    private final JComboBox<ShapeEntry> shapes = new JComboBox<>(shapeModel);
    private final JButton deleteShape;
    private boolean syncing;

    ToolPanel(BrushSettings settings, Messages messages, BrushActions actions) {
        this.settings = settings;
        this.messages = messages;
        this.actions = actions;

        JPanel tools = new JPanel(new GridLayout(0, 2, 4, 4));
        tools.setBorder(section(messages.text("panel.tools")));
        ButtonGroup group = new ButtonGroup();
        for (Tool tool : Tool.values()) {
            String name = messages.text(tool.messageKey());
            JToggleButton button = SafeText.plain(new JToggleButton(name));
            button.setToolTipText(messages.text("tool.shortcut", name, Integer.toString(tool.shortcut())));
            button.setFocusable(false);
            button.addActionListener(e -> settings.setTool(tool));
            group.add(button);
            tools.add(button);
            buttons.put(tool, button);
        }

        radiusSlider = new JSlider(0, BrushSettings.MAX_RADIUS, settings.radius());
        radiusSpinner = new JSpinner(new SpinnerNumberModel(settings.radius(), 0, BrushSettings.MAX_RADIUS, 1));
        strengthSlider = new JSlider(BrushSettings.MIN_STRENGTH_PERCENT, 100, settings.strengthPercent());
        falloff = new JComboBox<>(Brush.Falloff.values());
        falloff.setRenderer(Renderers.text(f -> messages.text(Labels.falloffKey(f))));

        radiusSlider.addChangeListener(e -> {
            if (!syncing) {
                settings.setRadius(radiusSlider.getValue());
            }
        });
        radiusSpinner.addChangeListener(e -> {
            if (!syncing) {
                settings.setRadius(((Number) radiusSpinner.getValue()).intValue());
            }
        });
        strengthSlider.addChangeListener(e -> {
            if (!syncing) {
                settings.setStrengthPercent(strengthSlider.getValue());
            }
        });
        falloff.addActionListener(e -> {
            if (!syncing && falloff.getSelectedItem() instanceof Brush.Falloff value) {
                settings.setFalloff(value);
            }
        });
        autoAddTiles = SafeText.plain(new JCheckBox(messages.text("brush.autoAddTiles")));
        autoAddTiles.setToolTipText(messages.text("brush.autoAddTilesHint"));
        autoAddTiles.setFocusable(false);
        autoAddTiles.addActionListener(e -> {
            if (!syncing) {
                settings.setAutoAddTiles(autoAddTiles.isSelected());
            }
        });

        shapes.setRenderer(Renderers.withIcon(ShapeEntry::name, ShapeEntry::icon));
        shapes.setMaximumRowCount(12);
        shapes.addActionListener(e -> {
            if (!syncing && shapes.getSelectedItem() instanceof ShapeEntry entry) {
                actions.selectShape(entry);
            }
        });
        JButton addShape = SafeText.plain(new JButton(messages.text("shape.add")));
        addShape.setFocusable(false);
        addShape.addActionListener(e -> actions.addShape());
        deleteShape = SafeText.plain(new JButton(messages.text("shape.delete")));
        deleteShape.setFocusable(false);
        deleteShape.addActionListener(e -> {
            if (shapes.getSelectedItem() instanceof ShapeEntry entry) {
                actions.deleteShape(entry);
            }
        });
        JPanel shapeButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        shapeButtons.add(addShape);
        shapeButtons.add(deleteShape);

        JPanel brush = new JPanel(new GridBagLayout());
        brush.setBorder(section(messages.text("panel.brush")));
        addRow(brush, 0, SafeText.label(messages.text("brush.radius")), radiusSpinner);
        addWide(brush, 1, radiusSlider);
        addRow(brush, 2, SafeText.label(messages.text("brush.strength")), strengthValue);
        addWide(brush, 3, strengthSlider);
        addRow(brush, 4, SafeText.label(messages.text("brush.falloff")), falloff);
        addRow(brush, 5, SafeText.label(messages.text("shape.label")), shapes);
        addWide(brush, 6, shapeButtons);
        addWide(brush, 7, autoAddTiles);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        tools.setAlignmentX(0);
        brush.setAlignmentX(0);
        stack.add(tools);
        stack.add(Box.createVerticalStrut(8));
        stack.add(brush);
        panel.add(stack, BorderLayout.NORTH);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 4));

        settings.addListener(this::sync);
        actions.catalog().addListener(this::reloadShapes);
        reloadShapes();
    }

    JComponent component() {
        return panel;
    }

    private void sync() {
        syncing = true;
        try {
            for (Map.Entry<Tool, JToggleButton> entry : buttons.entrySet()) {
                JToggleButton button = entry.getValue();
                boolean selected = entry.getKey() == settings.tool();
                button.setSelected(selected);
                button.setFont(button.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
            }
            radiusSlider.setValue(settings.radius());
            radiusSpinner.setValue(settings.radius());
            strengthSlider.setValue(settings.strengthPercent());
            strengthSlider.setEnabled(settings.tool().usesStrength());
            SafeText.setText(strengthValue,
                    messages.text("brush.strengthValue", Integer.toString(settings.strengthPercent())));
            falloff.setSelectedItem(settings.falloff());
            autoAddTiles.setSelected(settings.autoAddTiles());
            autoAddTiles.setEnabled(!settings.tool().editsTiles());
            ShapeEntry current = null;
            for (int i = 0; i < shapeModel.getSize(); i++) {
                if (shapeModel.getElementAt(i).shape().equals(settings.shape())) {
                    current = shapeModel.getElementAt(i);
                }
            }
            if (current == null) {
                current = actions.catalog().entryFor(settings.shape(), messages.text("shape.session"));
            }
            shapes.setSelectedItem(current);
            deleteShape.setEnabled(current.kind() != ShapeEntry.Kind.BUILTIN);
        } finally {
            syncing = false;
        }
    }

    private void reloadShapes() {
        syncing = true;
        try {
            shapeModel.removeAllElements();
            for (ShapeEntry entry : actions.catalog().shapes()) {
                shapeModel.addElement(entry);
            }
        } finally {
            syncing = false;
        }
        sync();
    }

    static Border section(String title) {
        return BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(SafeText.neutralize(title)),
                BorderFactory.createEmptyBorder(4, 6, 6, 6));
    }

    static void addRow(JPanel panel, int row, JComponent label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 2, 8);
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 2, 0);
        panel.add(field, c);
    }

    static void addWide(JPanel panel, int row, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }
}
