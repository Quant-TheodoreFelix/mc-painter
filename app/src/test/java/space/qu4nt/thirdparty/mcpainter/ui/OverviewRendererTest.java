package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class OverviewRendererTest {

    @TempDir
    Path dir;

    @Test
    void gridStepsGrowWhenZoomingOut() {
        assertEquals(1, MapView.gridStepTiles(1));
        assertEquals(1, MapView.gridStepTiles(0.25));
        assertEquals(2, MapView.gridStepTiles(0.125));
        assertEquals(16, MapView.gridStepTiles(Math.scalb(1.0, -6)));
        assertEquals(1 << 15, MapView.gridStepTiles(Math.scalb(1.0, -17)));
    }

    @Test
    void drawsBaseColourStoredThumbnailsAndEditedAreaTiles() throws IOException {
        TileRect bounds = new TileRect(-4, -4, 3, 3);
        Forms.NewWorld world = new Forms.NewWorld(bounds, 64, 63, TerrainType.GRASS, GenerationSettings.DEFAULT);
        try (Workspace workspace = Workspace.create(dir, world.spec(), "overview")) {
            int[] red = new int[Workspace.THUMB_PIXELS];
            Arrays.fill(red, 0xFF0000);
            WorldModel stored = world.spec().builder().editable(bounds).baseTile(new TilePos(-2, 0)).build();
            try (Edit edit = stored.beginEdit("paint")) {
                edit.setTerrain(-200, 5, TerrainType.SAND);
            }
            workspace.write(List.of(new Workspace.Write(new TilePos(-2, 0), stored.tile(new TilePos(-2, 0))
                    .orElseThrow(), red, false)), true);
            WorldModel area = Session.load(workspace, new TileRect(1, 1, 1, 1), 100, 1,
                    space.qu4nt.thirdparty.mcpainter.io.Progress.NONE);
            try (Edit edit = area.beginEdit("paint")) {
                for (int z = 128; z < 256; z++) {
                    for (int x = 128; x < 256; x++) {
                        edit.setTerrain(x, z, TerrainType.SNOW);
                    }
                }
            }
            Viewport viewport = new Viewport();
            viewport.setZoom(-3, 0, 0);
            viewport.centerOn(0, 0, 160, 160);
            OverviewRenderer renderer = new OverviewRenderer(new TileImageCache(TileImageCache.DEFAULT_MAX_BYTES));
            BufferedImage image = renderer.render(viewport, 160, 160, area, workspace, 0x123456, 1, null);
            int base = Session.baseColor(workspace.spec());
            assertEquals(base, image.getRGB(80 + 4, 80 - 20) & 0xFFFFFF);
            assertEquals(0xFF0000, image.getRGB(80 - 24, 80 + 4) & 0xFFFFFF);
            assertNotEquals(base, image.getRGB(80 + 24, 80 + 24) & 0xFFFFFF);
            assertSame(image, renderer.render(viewport, 160, 160, area, workspace, 0x123456, 1, null));
            BufferedImage gridded = renderer.render(viewport, 160, 160, area, workspace, 0x123456, 2,
                    new OverviewRenderer.Grid(bounds, 0, 0xFF000000, 128));
            assertEquals(0, gridded.getRGB(80, 80 - 20) & 0xFFFFFF);
        }
    }
}
