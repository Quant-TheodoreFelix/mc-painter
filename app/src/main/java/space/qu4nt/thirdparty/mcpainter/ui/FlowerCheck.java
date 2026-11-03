package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.io.FlowerBlocks;
import space.qu4nt.thirdparty.mcpainter.world.Layer;

final class FlowerCheck {

    private FlowerCheck() {
    }

    static Optional<String> warning(Messages messages, Collection<Layer> usedLayers, int dataVersion) {
        if (!DataVersions.isSupported(dataVersion)) {
            return Optional.empty();
        }
        List<Layer> missing = FlowerBlocks.unavailable(usedLayers, dataVersion);
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder(messages.text("export.flowerNote"));
        for (Layer flower : missing) {
            text.append('\n').append(messages.text("export.flowerItem", messages.text(Labels.layerKey(flower)),
                    Labels.release(FlowerBlocks.since(flower))));
        }
        return Optional.of(text.toString());
    }
}
