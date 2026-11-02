package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class TerrainPanel {

    static final List<String> BIOMES = List.of(
            "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:ice_spikes",
            "minecraft:desert", "minecraft:swamp", "minecraft:forest", "minecraft:flower_forest",
            "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:old_growth_birch_forest",
            "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:taiga",
            "minecraft:snowy_taiga", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_hills",
            "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest", "minecraft:windswept_savanna",
            "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:badlands",
            "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:meadow", "minecraft:grove",
            "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks",
            "minecraft:river", "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach",
            "minecraft:stony_shore", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
            "minecraft:deep_lukewarm_ocean", "minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean",
            "minecraft:deep_cold_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
            "minecraft:mushroom_fields", "minecraft:dripstone_caves", "minecraft:lush_caves");

    private static final int NOTE_COLUMNS = 18;

    private final BrushSettings settings;
    private final Document document;
    private final Messages messages;
    private final Consumer<String> notice;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final JList<TerrainType> terrains = new JList<>(TerrainType.values());
    private final SpinnerNumberModel waterModel;
    private final JComboBox<String> biomes = new JComboBox<>(BIOMES.toArray(String[]::new));
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
        biomes.setMaximumRowCount(16);
        biomes.setRenderer(Renderers.text(String::valueOf));
        biomes.addActionListener(e -> {
            if (!syncing) {
                applyBiome();
            }
        });
        JPanel biomeSection = new JPanel(new BorderLayout(0, 4));
        biomeSection.setBorder(ToolPanel.section(messages.text("panel.biome")));
        biomeSection.add(SafeText.label(messages.text("biome.id")), BorderLayout.NORTH);
        biomeSection.add(biomes, BorderLayout.CENTER);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (JComponent section : List.of(terrainSection, waterSection, biomeSection)) {
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
            waterModel.setValue(settings.waterLevel());
            biomes.setSelectedItem(settings.biome());
        } finally {
            syncing = false;
        }
    }
}
