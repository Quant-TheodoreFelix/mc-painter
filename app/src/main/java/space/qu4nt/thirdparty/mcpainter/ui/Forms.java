package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Component;
import java.awt.GridBagLayout;
import java.text.ParseException;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import space.qu4nt.thirdparty.mcpainter.io.HeightmapImporter;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class Forms {

    static final int MAX_SIDE_TILES = 64;

    static final int SEED_COLUMNS = 16;

    record NewWorld(int width, int depth, int height, int seaLevel, TerrainType terrain,
            GenerationSettings generation) {
    }

    record Import(HeightmapImporter.Options options, GenerationSettings generation) {
    }

    private Forms() {
    }

    static final class NewWorldForm {

        private final JSpinner width = spinner(Worlds.STARTUP_TILES, 1, MAX_SIDE_TILES);
        private final JSpinner depth = spinner(Worlds.STARTUP_TILES, 1, MAX_SIDE_TILES);
        private final JSpinner height = spinner(Worlds.BASE_HEIGHT, Worlds.MIN_Y + 1, Worlds.MAX_Y - 1);
        private final JSpinner sea = spinner(Worlds.SEA_LEVEL, Worlds.MIN_Y, Worlds.MAX_Y);
        private final JComboBox<TerrainType> terrain = new JComboBox<>(TerrainType.values());
        private final JTextField seed = seedField();
        private final JCheckBox initialOres;
        private final JPanel panel = form();

        NewWorldForm(Messages messages) {
            initialOres = initialOresBox(messages);
            terrain.setRenderer(Renderers.withIcon(t -> messages.text(Labels.terrainKey(t)),
                    t -> new Renderers.Swatch(TileRenderer.terrainColor(t))));
            ToolPanel.addRow(panel, 0, SafeText.label(messages.text("new.width")), width);
            ToolPanel.addRow(panel, 1, SafeText.label(messages.text("new.depth")), depth);
            ToolPanel.addRow(panel, 2, SafeText.label(messages.text("new.baseHeight")), height);
            ToolPanel.addRow(panel, 3, SafeText.label(messages.text("new.seaLevel")), sea);
            ToolPanel.addRow(panel, 4, SafeText.label(messages.text("new.terrain")), terrain);
            ToolPanel.addRow(panel, 5, SafeText.label(messages.text("new.seed")), seed);
            ToolPanel.addWide(panel, 6, SafeText.label(messages.text("new.seedHint")));
            ToolPanel.addWide(panel, 7, initialOres);
            ToolPanel.addWide(panel, 8, SafeText.label(messages.text("new.heightRange",
                    Integer.toString(Worlds.MIN_Y), Integer.toString(Worlds.MAX_Y))));
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

        Optional<NewWorld> read() {
            int w = value(width);
            int d = value(depth);
            if ((long) w * d > WorldModel.DEFAULT_MAX_TILES) {
                return Optional.empty();
            }
            TerrainType type = terrain.getSelectedItem() instanceof TerrainType t ? t : TerrainType.GRASS;
            return Optional.of(new NewWorld(w, d, value(height), value(sea), type, generation(seed, initialOres)));
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

    static Optional<NewWorld> newWorld(Component parent, Messages messages) {
        NewWorldForm form = new NewWorldForm(messages);
        while (Dialogs.form(parent, messages, "new.title", form.panel())) {
            Optional<NewWorld> spec = form.read();
            if (spec.isPresent()) {
                return spec;
            }
            Dialogs.error(parent, messages,
                    messages.text("new.tooManyTiles", Integer.toString(WorldModel.DEFAULT_MAX_TILES)), "");
        }
        return Optional.empty();
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
        return new JSpinner(new SpinnerNumberModel(Math.clamp(value, min, max), min, max, 1));
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
