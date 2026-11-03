package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import space.qu4nt.thirdparty.mcpainter.anvil.world.VanillaBiomes;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class TerrainPanel {

    static final List<String> BIOMES = VanillaBiomes.overworld().stream().map(VanillaBiomes.Biome::id).toList();

    private static final int NOTE_COLUMNS = 18;
    private static final int BIOME_COLUMNS = 20;
    private static final int FLOWER_ROWS = 6;
    private static final int SCROLL_UNIT = 16;

    private final BrushSettings settings;
    private final Document document;
    private final Messages messages;
    private final Consumer<String> notice;
    private final JPanel panel = new ScrollablePanel();
    private final JList<TerrainType> terrains = new JList<>(TerrainType.values());
    private final JList<LayerGroup> layers = new JList<>(LayerGroup.values());
    private final Map<Layer, JCheckBox> flowerBoxes = new EnumMap<>(Layer.class);
    private final JPanel flowerSection = new JPanel(new BorderLayout());
    private final JTextArea layerHint = SafeText.message("", NOTE_COLUMNS);
    private final SpinnerNumberModel waterModel;
    private final JComboBox<String> biomes = new JComboBox<>(BIOMES.toArray(String[]::new));
    private final JTextArea biomeHint = SafeText.message("", NOTE_COLUMNS);
    private boolean syncing;

    TerrainPanel(BrushSettings settings, Document document, Messages messages, Consumer<String> notice) {
        this.settings = settings;
        this.document = document;
        this.messages = messages;
        this.notice = notice;

        terrains.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        terrains.setVisibleRowCount(TerrainType.values().length);
        terrains.setCellRenderer(Renderers.withIcon(t -> messages.text(Labels.terrainKey(t)),
                t -> new Renderers.Swatch(TileRenderer.terrainColor(t))));
        terrains.addListSelectionListener(e -> {
            TerrainType selected = terrains.getSelectedValue();
            if (!syncing && !e.getValueIsAdjusting() && selected != null) {
                settings.setTerrain(selected);
                if (settings.tool() != Tool.ADD_TILES) {
                    settings.setTool(Tool.TERRAIN);
                }
            }
        });
        JPanel terrainSection = new JPanel(new BorderLayout());
        terrainSection.setBorder(ToolPanel.section(messages.text("panel.terrain")));
        terrainSection.add(terrains, BorderLayout.CENTER);

        WorldModel model = document.model();
        waterModel = new SpinnerNumberModel(model.seaLevel(), model.minY(), model.maxY(), 1);
        JSpinner water = new JSpinner(waterModel);
        water.addChangeListener(e -> {
            if (!syncing) {
                settings.setWaterLevel(waterModel.getNumber().intValue());
                settings.setTool(Tool.WATER);
            }
        });
        JButton seaLevel = SafeText.plain(new JButton(messages.text("water.seaLevel")));
        seaLevel.addActionListener(e -> water.setValue(document.model().seaLevel()));
        JTextArea hint = SafeText.message(messages.text("water.hint"), NOTE_COLUMNS);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, hint.getFont().getSize2D() - 1));
        JPanel waterSection = new JPanel(new java.awt.GridBagLayout());
        waterSection.setBorder(ToolPanel.section(messages.text("panel.water")));
        ToolPanel.addRow(waterSection, 0, SafeText.label(messages.text("water.level")), water);
        ToolPanel.addWide(waterSection, 1, seaLevel);
        ToolPanel.addWide(waterSection, 2, hint);

        biomes.setEditable(true);
        if (biomes.getEditor().getEditorComponent() instanceof JTextField field) {
            field.setColumns(BIOME_COLUMNS);
        }
        biomes.setMaximumRowCount(16);
        biomes.setRenderer(Renderers.withTooltip(id -> Labels.biomeEntry(messages, String.valueOf(id)),
                String::valueOf));
        biomes.addActionListener(e -> {
            if (!syncing) {
                applyBiome();
            }
        });
        JPanel biomeSection = new JPanel(new BorderLayout(0, 4));
        biomeSection.setBorder(ToolPanel.section(messages.text("panel.biome")));
        biomeSection.add(SafeText.label(messages.text("biome.id")), BorderLayout.NORTH);
        biomeSection.add(biomes, BorderLayout.CENTER);
        biomeHint.setFont(biomeHint.getFont().deriveFont(Font.PLAIN, biomeHint.getFont().getSize2D() - 1));
        biomeSection.add(biomeHint, BorderLayout.SOUTH);

        layers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layers.setVisibleRowCount(LayerGroup.values().length);
        layers.setCellRenderer(Renderers.withIcon(g -> messages.text(Labels.layerKey(g)),
                g -> new Renderers.Swatch(groupColor(g))));
        layers.addListSelectionListener(e -> {
            LayerGroup selected = layers.getSelectedValue();
            if (!syncing && !e.getValueIsAdjusting() && selected != null) {
                settings.setLayerGroup(selected);
                settings.setTool(Tool.LAYER);
            }
        });
        layers.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!syncing && layers.getSelectedValue() != null) {
                    settings.setTool(Tool.LAYER);
                }
            }
        });
        layerHint.setFont(layerHint.getFont().deriveFont(Font.PLAIN, layerHint.getFont().getSize2D() - 1));
        JPanel layerSection = new JPanel(new BorderLayout(0, 4));
        layerSection.setBorder(ToolPanel.section(messages.text("panel.layers")));
        layerSection.add(layers, BorderLayout.NORTH);
        layerSection.add(flowerSection, BorderLayout.CENTER);
        layerSection.add(layerHint, BorderLayout.SOUTH);
        buildFlowers();

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (JComponent section : List.of(terrainSection, waterSection, biomeSection, layerSection)) {
            section.setAlignmentX(0);
            if (stack.getComponentCount() > 0) {
                stack.add(Box.createVerticalStrut(8));
            }
            stack.add(section);
        }
        panel.add(stack, BorderLayout.NORTH);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        settings.setWaterLevel(model.seaLevel());
        settings.addListener(this::sync);
        document.onModelReplaced(this::modelReplaced);
        sync();
    }

    JComponent component() {
        return panel;
    }

    private static final class ScrollablePanel extends JPanel implements Scrollable {

        private static final long serialVersionUID = 1L;

        ScrollablePanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return SCROLL_UNIT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(SCROLL_UNIT, visible.height - SCROLL_UNIT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static int groupColor(LayerGroup group) {
        return group == LayerGroup.FLOWERS ? TileRenderer.FLOWER_MIX
                : TileRenderer.layerColor(group.layers(List.of()).getFirst());
    }

    private void buildFlowers() {
        JPanel rows = new JPanel(new GridLayout(0, 1));
        for (Layer flower : Layer.flowers()) {
            JCheckBox box = new JCheckBox();
            String name = Labels.layerEntry(messages, flower);
            box.getAccessibleContext().setAccessibleName(SafeText.neutralize(name));
            box.addActionListener(e -> flowerToggled(box));
            JLabel label = SafeText.label(name);
            label.setIcon(new Renderers.Swatch(TileRenderer.layerColor(flower)));
            label.setLabelFor(box);
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    box.doClick();
                }
            });
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
            row.add(box);
            row.add(label);
            rows.add(row);
            flowerBoxes.put(flower, box);
        }
        JScrollPane scroll = new JScrollPane(rows, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        Dimension size = rows.getPreferredSize();
        int rowHeight = size.height / Layer.flowers().size();
        scroll.getVerticalScrollBar().setUnitIncrement(rowHeight);
        scroll.setPreferredSize(new Dimension(size.width + scroll.getVerticalScrollBar().getPreferredSize().width + 4,
                rowHeight * FLOWER_ROWS + 4));
        flowerSection.setBorder(ToolPanel.section(messages.text("panel.flowers")));
        flowerSection.add(scroll, BorderLayout.CENTER);
    }

    private void flowerToggled(JCheckBox source) {
        if (syncing) {
            return;
        }
        List<Layer> chosen = new ArrayList<>();
        flowerBoxes.forEach((flower, box) -> {
            if (box.isSelected()) {
                chosen.add(flower);
            }
        });
        if (chosen.isEmpty()) {
            source.setSelected(true);
            notice.accept(messages.text("flowers.atLeastOne"));
            return;
        }
        settings.setFlowers(chosen);
        settings.setLayerGroup(LayerGroup.FLOWERS);
        settings.setTool(Tool.LAYER);
    }

    private void applyBiome() {
        Object item = biomes.getSelectedItem();
        try {
            settings.setBiome(item == null ? "" : item.toString());
            settings.setTool(Tool.BIOME);
        } catch (IllegalArgumentException e) {
            notice.accept(messages.text("biome.invalid"));
        }
        sync();
    }

    private void modelReplaced() {
        WorldModel model = document.model();
        syncing = true;
        try {
            waterModel.setMinimum(model.minY());
            waterModel.setMaximum(model.maxY());
        } finally {
            syncing = false;
        }
        settings.setWaterLevel(model.seaLevel());
        sync();
    }

    private void sync() {
        syncing = true;
        try {
            terrains.setSelectedValue(settings.terrain(), true);
            layers.setSelectedValue(settings.layerGroup(), true);
            boolean flowers = settings.layerGroup() == LayerGroup.FLOWERS;
            String hint = flowers ? messages.text("layer.hint") + "\n" + messages.text("flowers.hint")
                    : messages.text("layer.hint");
            boolean changed = !layerHint.getText().equals(SafeText.neutralize(hint));
            if (changed) {
                SafeText.setText(layerHint, hint);
            }
            if (flowerSection.isVisible() != flowers) {
                flowerSection.setVisible(flowers);
                changed = true;
            }
            if (changed) {
                panel.revalidate();
            }
            flowerBoxes.forEach((flower, box) -> box.setSelected(settings.flowers().contains(flower)));
            waterModel.setValue(settings.waterLevel());
            biomes.setSelectedItem(settings.biome());
            SafeText.setText(biomeHint, Labels.biomeHint(messages, settings.biome()));
        } finally {
            syncing = false;
        }
    }
}
