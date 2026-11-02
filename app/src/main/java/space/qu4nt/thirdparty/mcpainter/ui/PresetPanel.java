package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

final class PresetPanel {

    private final BrushActions actions;
    private final DefaultListModel<BrushLibrary.Stored<BrushPreset>> model = new DefaultListModel<>();
    private final JList<BrushLibrary.Stored<BrushPreset>> list = new JList<>(model);
    private final JPanel panel = new JPanel(new BorderLayout(0, 6));
    private final JButton delete;

    PresetPanel(BrushActions actions, Messages messages) {
        this.actions = actions;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(5);
        list.setCellRenderer(Renderers.text(stored -> stored.value().name()));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    actions.applyPreset(model.get(index));
                }
            }
        });
        list.addListSelectionListener(e -> refreshButtons());

        JButton save = button(messages.text("preset.save"));
        save.addActionListener(e -> actions.savePreset());
        delete = button(messages.text("preset.delete"));
        delete.addActionListener(e -> actions.deletePreset(list.getSelectedValue()));
        JButton importFile = button(messages.text("preset.import"));
        importFile.addActionListener(e -> actions.importFile());
        JButton folder = button(messages.text("preset.openFolder"));
        folder.addActionListener(e -> actions.openFolder());
        JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
        buttons.add(save);
        buttons.add(delete);
        buttons.add(importFile);
        buttons.add(folder);

        JScrollPane scroll = new JScrollPane(list);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        panel.setBorder(ToolPanel.section(messages.text("panel.presets")));
        actions.catalog().addListener(this::reload);
        reload();
    }

    JComponent component() {
        return panel;
    }

    private void reload() {
        BrushLibrary.Stored<BrushPreset> selected = list.getSelectedValue();
        model.clear();
        for (BrushLibrary.Stored<BrushPreset> preset : actions.catalog().presets()) {
            model.addElement(preset);
        }
        if (selected != null && model.contains(selected)) {
            list.setSelectedValue(selected, true);
        }
        refreshButtons();
    }

    private void refreshButtons() {
        delete.setEnabled(list.getSelectedValue() != null);
    }

    private static JButton button(String text) {
        JButton button = SafeText.plain(new JButton(text));
        button.setFocusable(false);
        return button;
    }
}
