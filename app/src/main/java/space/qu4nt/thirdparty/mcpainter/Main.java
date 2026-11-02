package space.qu4nt.thirdparty.mcpainter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import space.qu4nt.thirdparty.mcpainter.io.HeightmapImporter;
import space.qu4nt.thirdparty.mcpainter.io.SmokeTest;
import space.qu4nt.thirdparty.mcpainter.io.WorldExporter;
import space.qu4nt.thirdparty.mcpainter.ui.App;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public class Main {

    private static final String USAGE = """
            usage:
              mc-painter smoke-test <world-directory>
              mc-painter export-heightmap <heightmap.png> <template-world-directory> [--low N] [--high N] [--sea N]""";

    static void main(String[] args) throws Exception {
        if (args.length == 0) {
            if (App.headless()) {
                IO.println(App.headlessMessage());
                IO.println(USAGE);
                System.exit(2);
            }
            App.launch();
            return;
        }
        if (args.length == 2 && args[0].equals("smoke-test")) {
            for (String line : SmokeTest.run(Path.of(args[1]))) {
                IO.println(line);
            }
            return;
        }
        if (args.length >= 3 && args[0].equals("export-heightmap")) {
            exportHeightmap(args);
            return;
        }
        IO.println(USAGE);
        System.exit(2);
    }

    private static void exportHeightmap(String[] args) throws Exception {
        Map<String, Integer> flags = new HashMap<>();
        for (int i = 3; i < args.length; i += 2) {
            if (i + 1 >= args.length || !args[i].matches("--(low|high|sea)")) {
                IO.println(USAGE);
                System.exit(2);
            }
            flags.put(args[i].substring(2), Integer.parseInt(args[i + 1]));
        }
        HeightmapImporter.Options defaults = HeightmapImporter.Options.defaults();
        HeightmapImporter.Options options = new HeightmapImporter.Options(defaults.minY(), defaults.maxY(),
                flags.getOrDefault("sea", defaults.seaLevel()),
                flags.getOrDefault("low", (int) defaults.low()),
                flags.getOrDefault("high", (int) defaults.high()),
                defaults.maxTiles());
        WorldModel model = HeightmapImporter.importPng(Path.of(args[1]), options);
        IO.println("imported " + model.tileCount() + " tiles");
        WorldExporter.Result result = WorldExporter.export(model, Path.of(args[2]));
        IO.println("regions=" + result.regionsWritten() + " replaced=" + result.chunksReplaced()
                + " created=" + result.chunksCreated() + " entityChunksRemoved=" + result.entityChunksRemoved()
                + " poiChunksRemoved=" + result.poiChunksRemoved());
    }
}
