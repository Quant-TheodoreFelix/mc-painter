package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;

class BiomeCheckTest {

    private final Messages ko = Messages.load(Language.KOREAN);

    @Test
    void passesWhenEveryBiomeExists() {
        List<String> used = List.of("minecraft:plains", "minecraft:cherry_grove", "minecraft:dappled_forest");
        assertTrue(BiomeCheck.warning(ko, used, DataVersions.V26_3).isEmpty());
        assertTrue(BiomeCheck.warning(ko, List.of(), DataVersions.V1_18).isEmpty());
    }

    @Test
    void listsMissingBiomesByKind() {
        List<String> used = List.of("minecraft:plains", "minecraft:cherry_grove", "minecraft:plainz", "mymod:glow/caves");
        String text = BiomeCheck.warning(ko, used, DataVersions.V1_18).orElseThrow();
        assertTrue(text.startsWith(ko.text("export.biomeNote")), text);
        assertTrue(text.contains("- 벚나무 숲 (minecraft:cherry_grove), 1.20 이상"), text);
        assertTrue(text.contains(ko.text("export.biomeUnknown") + "\n- minecraft:plainz"), text);
        assertTrue(text.contains(ko.text("export.biomeCustom") + "\n- mymod:glow/caves"), text);
        assertFalse(text.contains("minecraft:plains\n"), text);
        String later = BiomeCheck.warning(ko, List.of("minecraft:cherry_grove", "minecraft:sulfur_caves"),
                DataVersions.V1_20).orElseThrow();
        assertTrue(later.contains("minecraft:sulfur_caves"), later);
        assertFalse(later.contains("minecraft:cherry_grove"), later);
    }

    @Test
    void capsLongLists() {
        List<String> used = new ArrayList<>();
        for (int i = 0; i < BiomeCheck.MAX_LISTED + 3; i++) {
            used.add("mymod:b" + i);
        }
        String text = BiomeCheck.warning(ko, used, DataVersions.V26_3).orElseThrow();
        assertTrue(text.contains("mymod:b" + (BiomeCheck.MAX_LISTED - 1)), text);
        assertFalse(text.contains("mymod:b" + BiomeCheck.MAX_LISTED), text);
        assertTrue(text.endsWith(ko.text("export.biomeMore", "3")), text);
        assertEquals(BiomeCheck.MAX_LISTED + 3, text.lines().filter(l -> l.startsWith("- ")).count() + 3);
    }
}
