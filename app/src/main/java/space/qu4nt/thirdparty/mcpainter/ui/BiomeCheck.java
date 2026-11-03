package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.world.VanillaBiomes;
import space.qu4nt.thirdparty.mcpainter.anvil.world.VanillaBiomes.Availability;

final class BiomeCheck {

    static final int MAX_LISTED = 8;

    private BiomeCheck() {
    }

    static Optional<String> warning(Messages messages, List<String> usedBiomes, int dataVersion) {
        List<String> tooNew = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> custom = new ArrayList<>();
        for (String id : usedBiomes) {
            Availability availability = VanillaBiomes.availability(id, dataVersion);
            switch (availability) {
                case AVAILABLE -> {
                }
                case TOO_NEW -> tooNew.add(messages.text("export.biomeItemSince", Labels.biomeName(messages, id), id,
                        Labels.release(VanillaBiomes.find(id).orElseThrow().sinceDataVersion())));
                case UNKNOWN -> unknown.add(messages.text("export.biomeItem", id));
                case CUSTOM -> custom.add(messages.text("export.biomeItem", id));
            }
        }
        if (tooNew.isEmpty() && unknown.isEmpty() && custom.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder(messages.text("export.biomeNote"));
        group(text, messages, "export.biomeTooNew", tooNew);
        group(text, messages, "export.biomeUnknown", unknown);
        group(text, messages, "export.biomeCustom", custom);
        return Optional.of(text.toString());
    }

    private static void group(StringBuilder text, Messages messages, String headingKey, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        text.append("\n\n").append(messages.text(headingKey));
        for (String item : items.subList(0, Math.min(items.size(), MAX_LISTED))) {
            text.append('\n').append(item);
        }
        if (items.size() > MAX_LISTED) {
            text.append('\n').append(messages.text("export.biomeMore", Integer.toString(items.size() - MAX_LISTED)));
        }
    }
}
