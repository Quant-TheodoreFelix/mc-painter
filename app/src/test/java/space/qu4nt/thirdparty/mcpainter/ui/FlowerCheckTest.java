package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.world.Layer;

class FlowerCheckTest {

    private final Messages ko = Messages.load(Language.KOREAN);

    @Test
    void listsFlowersTheWorldVersionDoesNotHave() {
        Set<Layer> used = Set.of(Layer.GRASS, Layer.POPPY, Layer.WILDFLOWERS, Layer.TORCHFLOWER, Layer.PEONY);
        assertTrue(FlowerCheck.warning(ko, used, DataVersions.V26_3).isEmpty());
        assertTrue(FlowerCheck.warning(ko, Set.of(), DataVersions.V1_18).isEmpty());
        assertTrue(FlowerCheck.warning(ko, used, 100).isEmpty());
        assertEquals(ko.text("export.flowerNote") + "\n- 횃불꽃 (1.20 이상)\n- 들꽃 (1.21.5 이상)",
                FlowerCheck.warning(ko, used, DataVersions.V1_18).orElseThrow());
        assertEquals(ko.text("export.flowerNote") + "\n- 들꽃 (1.21.5 이상)",
                FlowerCheck.warning(ko, used, DataVersions.V1_21_4).orElseThrow());
    }

    @Test
    void labelsNewerFlowersWithTheirFirstRelease() {
        assertEquals("모란", Labels.layerEntry(ko, Layer.PEONY));
        assertEquals("눈망울꽃 (1.21.4 이상)", Labels.layerEntry(ko, Layer.EYEBLOSSOM));
        assertEquals("벌레잡이풀 (1.20 이상)", Labels.layerEntry(ko, Layer.PITCHER_PLANT));
        assertEquals("석탄 광석", Labels.layerEntry(ko, Layer.COAL_ORE));
        assertEquals("수레국화", Labels.layerSelection(ko, List.of(Layer.CORNFLOWER)));
        assertEquals("꽃 3종", Labels.layerSelection(ko, List.of(Layer.CORNFLOWER, Layer.POPPY, Layer.LILAC)));
        for (LayerGroup group : LayerGroup.values()) {
            assertTrue(ko.has(Labels.layerKey(group)), group.id());
            assertEquals(group, LayerGroup.fromId(group.id()).orElseThrow());
        }
    }
}
