package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import space.qu4nt.thirdparty.mcpainter.io.HeightmapImporter;
import space.qu4nt.thirdparty.mcpainter.io.WorldExporter;
import space.qu4nt.thirdparty.mcpainter.io.WorldSpec;
import space.qu4nt.thirdparty.mcpainter.world.BaseTerrain;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class Forms {

    static final int SEED_COLUMNS = 16;
    static final int MAX_SIDE_TILES = TileRect.WORLD.width();
    static final int MAX_SIDE_BLOCKS = 2 * TileRect.WORLD_BLOCK_LIMIT;

    record NewWorld(TileRect bounds, int height, int seaLevel, TerrainType terrain, GenerationSettings generation) {

        WorldSpec spec() {
            return new WorldSpec(Worlds.MIN_Y, Worlds.MAX_Y, seaLevel, List.of(WorldModel.DEFAULT_BIOME), generation,
                    bounds, BaseTerrain.flat(height, terrain));
        }
    }

    record Import(HeightmapImporter.Options options, GenerationSettings generation) {
    }

    enum Start {
        CREATE,
        OPEN,
        RESTORE,
        CANCEL
    }

    record StartChoice(Start action, Optional<NewWorld> world) {
    }

    private Forms() {
    }

    static final class NewWorldForm {

        private final JRadioButton byTiles;
        private final JRadioButton byBlocks;
        private final JSpinner width = spinner(Worlds.STARTUP_TILES, 1, MAX_SIDE_TILES);
        private final JSpinner depth = spinner(Worlds.STARTUP_TILES, 1, MAX_SIDE_TILES);
        private final JSpinner widthBlocks = spinner(Worlds.STARTUP_TILES * Tile.SIZE, 1, MAX_SIDE_BLOCKS,
                Tile.SIZE);
        private final JSpinner depthBlocks = spinner(Worlds.STARTUP_TILES * Tile.SIZE, 1, MAX_SIDE_BLOCKS,
                Tile.SIZE);
        private final JSpinner height = spinner(Worlds.BASE_HEIGHT, Worlds.MIN_Y + 1, Worlds.MAX_Y - 1);
        private final JSpinner sea = spinner(Worlds.SEA_LEVEL, Worlds.MIN_Y, Worlds.MAX_Y);
        private final JComboBox<TerrainType> terrain = new JComboBox<>(TerrainType.values());
        private final JTextField seed = seedField();
        private final JCheckBox initialOres;
        private final JLabel summary = SafeText.label(" ");
        private final JLabel areaNote = SafeText.label(" ");
        private final Messages messages;
        private final int budgetTiles;
        private final JPanel panel = form();

        NewWorldForm(Messages messages) {
            this(messages, Integer.MAX_VALUE);
        }

        NewWorldForm(Messages messages, int budgetTiles) {
            this.messages = messages;
            this.budgetTiles = budgetTiles;
            initialOres = initialOresBox(messages);
            terrain.setRenderer(Renderers.withIcon(t -> messages.text(Labels.terrainKey(t)),
                    t -> new Renderers.Swatch(TileRenderer.terrainColor(t))));
            byTiles = SafeText.plain(new JRadioButton(messages.text("new.byTiles"), true));
            byBlocks = SafeText.plain(new JRadioButton(messages.text("new.byBlocks")));
            ButtonGroup modes = new ButtonGroup();
            modes.add(byTiles);
            modes.add(byBlocks);
            JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            modeRow.add(byTiles);
            modeRow.add(byBlocks);
            int row = 0;
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.size")), modeRow);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.width")), width);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.depth")), depth);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.widthBlocks")), widthBlocks);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.depthBlocks")), depthBlocks);
            ToolPanel.addWide(panel, row++, summary);
            ToolPanel.addWide(panel, row++, areaNote);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.baseHeight")), height);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.seaLevel")), sea);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.terrain")), terrain);
            ToolPanel.addRow(panel, row++, SafeText.label(messages.text("new.seed")), seed);
            ToolPanel.addWide(panel, row++, SafeText.label(messages.text("new.seedHint")));
            ToolPanel.addWide(panel, row++, initialOres);
            ToolPanel.addWide(panel, row, SafeText.label(messages.text("new.heightRange",
                    Integer.toString(Worlds.MIN_Y), Integer.toString(Worlds.MAX_Y))));
            byTiles.addActionListener(e -> refresh());
            byBlocks.addActionListener(e -> refresh());
            for (JSpinner spinner : List.of(width, depth, widthBlocks, depthBlocks)) {
                spinner.addChangeListener(e -> refresh());
            }
            refresh();
        }

        JPanel panel() {
            return panel;
        }

        JTextField seed() {
            return seed;
        }

        JCheckBox initialOres() {
            return initialOres;
        }

        void useBlocks(boolean blocks) {
            (blocks ? byBlocks : byTiles).setSelected(true);
            refresh();
        }

        JSpinner widthSpinner() {
            return byBlocks.isSelected() ? widthBlocks : width;
        }

        JSpinner depthSpinner() {
            return byBlocks.isSelected() ? depthBlocks : depth;
        }

        TileRect bounds() {
            if (byBlocks.isSelected()) {
                return TileRect.centeredBlocks(value(widthBlocks), value(depthBlocks));
            }
            return TileRect.centered(value(width), value(depth));
        }

        Optional<NewWorld> read() {
            TerrainType type = terrain.getSelectedItem() instanceof TerrainType t ? t : TerrainType.GRASS;
            try {
                return Optional.of(new NewWorld(bounds(), value(height), value(sea), type,
                        generation(seed, initialOres)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        private void refresh() {
            boolean blocks = byBlocks.isSelected();
            width.setEnabled(!blocks);
            depth.setEnabled(!blocks);
            widthBlocks.setEnabled(blocks);
            depthBlocks.setEnabled(blocks);
            TileRect bounds = bounds();
            SafeText.setText(summary, messages.text("new.summary", number(bounds.minBlockX()),
                    number(bounds.maxBlockX()), number(bounds.minBlockZ()), number(bounds.maxBlockZ()),
                    number(bounds.count())));
            long tiles = bounds.count();
            WorldExporter.Estimate estimate = WorldExporter.estimate(tiles, WorldExporter.regionCount(bounds), 1);
            String size = PerformanceForm.gigabytes(estimate.bytes());
            SafeText.setText(areaNote, tiles <= budgetTiles ? messages.text("new.fitsArea", size)
                    : messages.text("new.needsAreas", number(budgetTiles), size));
        }
    }

    static final class ImportForm {

        private final HeightmapImporter.Options defaults = HeightmapImporter.Options.defaults();
        private final JSpinner low = spinner((int) defaults.low(), defaults.minY(), defaults.maxY());
        private final JSpinner high = spinner((int) defaults.high(), defaults.minY(), defaults.maxY());
        private final JSpinner sea = spinner(defaults.seaLevel(), defaults.minY(), defaults.maxY());
        private final JTextField seed = seedField();
        private final JCheckBox initialOres;
        private final JPanel panel = form();

        ImportForm(Messages messages) {
            initialOres = initialOresBox(messages);
            ToolPanel.addRow(panel, 0, SafeText.label(messages.text("import.low")), low);
            ToolPanel.addRow(panel, 1, SafeText.label(messages.text("import.high")), high);
            ToolPanel.addRow(panel, 2, SafeText.label(messages.text("import.seaLevel")), sea);
            ToolPanel.addRow(panel, 3, SafeText.label(messages.text("new.seed")), seed);
            ToolPanel.addWide(panel, 4, SafeText.label(messages.text("new.seedHint")));
            ToolPanel.addWide(panel, 5, initialOres);
        }

        JPanel panel() {
            return panel;
        }

        JTextField seed() {
            return seed;
        }

        JCheckBox initialOres() {
            return initialOres;
        }

        Import read() {
            return new Import(new HeightmapImporter.Options(defaults.minY(), defaults.maxY(), value(sea), value(low),
                    value(high), defaults.maxTiles()), generation(seed, initialOres));
        }
    }

    static Optional<NewWorld> newWorld(Component parent, Messages messages, int budgetTiles) {
        NewWorldForm form = new NewWorldForm(messages, budgetTiles);
        while (Dialogs.form(parent, messages, "new.title", form.panel())) {
            Optional<NewWorld> spec = form.read();
            if (spec.isPresent()) {
                return spec;
            }
            Dialogs.error(parent, messages, messages.text("new.invalidSize"), "");
        }
        return Optional.empty();
    }

    static StartChoice start(Component parent, Messages messages, int budgetTiles, boolean hasTempSaves) {
        NewWorldForm form = new NewWorldForm(messages, budgetTiles);
        JPanel content = new JPanel(new java.awt.BorderLayout(0, 10));
        content.add(SafeText.message(messages.text(hasTempSaves ? "start.hintRestore" : "start.hint")),
                java.awt.BorderLayout.NORTH);
        content.add(form.panel(), java.awt.BorderLayout.CENTER);
        Object[] options = {messages.text("start.create"), messages.text("start.open"),
                messages.text("start.restore"), messages.text("dialog.cancel")};
        while (true) {
            int result = JOptionPane.showOptionDialog(parent, content, messages.text("new.title"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            switch (result) {
                case 0 -> {
                    Optional<NewWorld> world = form.read();
                    if (world.isPresent()) {
                        return new StartChoice(Start.CREATE, world);
                    }
                    Dialogs.error(parent, messages, messages.text("new.invalidSize"), "");
                }
                case 1 -> {
                    return new StartChoice(Start.OPEN, Optional.empty());
                }
                case 2 -> {
                    return new StartChoice(Start.RESTORE, Optional.empty());
                }
                default -> {
                    return new StartChoice(Start.CANCEL, Optional.empty());
                }
            }
        }
    }

    static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static Optional<Import> importOptions(Component parent, Messages messages) {
        ImportForm form = new ImportForm(messages);
        while (Dialogs.form(parent, messages, "import.title", form.panel())) {
            try {
                return Optional.of(form.read());
            } catch (IllegalArgumentException e) {
                Dialogs.error(parent, messages, "error.import", e);
            }
        }
        return Optional.empty();
    }

    private static JTextField seedField() {
        return SafeText.plain(new JTextField(SEED_COLUMNS));
    }

    private static GenerationSettings generation(JTextField seed, JCheckBox initialOres) {
        return new GenerationSettings(Seeds.parse(seed.getText()), initialOres.isSelected());
    }

    private static JCheckBox initialOresBox(Messages messages) {
        JCheckBox box = SafeText.plain(new JCheckBox(messages.text("new.initialOres")));
        box.setToolTipText(SafeText.neutralize(messages.text("new.initialOresHint")));
        return box;
    }

    private static JPanel form() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return form;
    }

    private static JSpinner spinner(int value, int min, int max) {
        return spinner(value, min, max, 1);
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(Math.clamp(value, min, max), min, max, step));
    }

    private static int value(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException ignored) {
            return ((Number) spinner.getValue()).intValue();
        }
        return ((Number) spinner.getValue()).intValue();
    }
}
