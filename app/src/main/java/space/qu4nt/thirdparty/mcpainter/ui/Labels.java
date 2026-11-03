package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.anvil.world.VanillaBiomes;
import space.qu4nt.thirdparty.mcpainter.io.FlowerBlocks;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;

final class Labels {

    private Labels() {
    }

    static String terrainKey(TerrainType type) {
        return "terrain." + type.id();
    }

    static String layerKey(Layer layer) {
        return "layer." + layer.id();
    }

    static String layerKey(LayerGroup group) {
        return "layer." + group.id();
    }

    static String layerSelection(Messages messages, List<Layer> layers) {
        if (layers.size() == 1) {
            return messages.text(layerKey(layers.getFirst()));
        }
        return messages.text("layer.mix", Integer.toString(layers.size()));
    }

    static String layerEntry(Messages messages, Layer layer) {
        String name = messages.text(layerKey(layer));
        if (!layer.flower() || FlowerBlocks.since(layer) <= DataVersions.MIN_SUPPORTED) {
            return name;
        }
        return messages.text("layer.entryNew", name, release(FlowerBlocks.since(layer)));
    }

    static int percent(int layerValue) {
        return (int) Math.round(layerValue * 100.0 / Layer.MAX_VALUE);
    }

    static String biomeKey(VanillaBiomes.Biome biome) {
        return "biome.name." + biome.path();
    }

    static String biomeName(Messages messages, String id) {
        return known(messages, id).map(b -> messages.text(biomeKey(b))).orElse(id);
    }

    static String biomeEntry(Messages messages, String id) {
        Optional<VanillaBiomes.Biome> biome = known(messages, id);
        if (biome.isEmpty()) {
            return id;
        }
        String name = messages.text(biomeKey(biome.get()));
        if (biome.get().sinceDataVersion() <= DataVersions.MIN_SUPPORTED) {
            return name;
        }
        return messages.text("biome.entryNew", name, release(biome.get().sinceDataVersion()));
    }

    static String biomeHint(Messages messages, String id) {
        return switch (VanillaBiomes.availability(id, DataVersions.MAX_SUPPORTED)) {
            case AVAILABLE, TOO_NEW -> biomeEntry(messages, id);
            case UNKNOWN -> messages.text("biome.unknown");
            case CUSTOM -> messages.text("biome.custom");
        };
    }

    static String release(int dataVersion) {
        return DataVersions.releaseName(dataVersion).orElse(Integer.toString(dataVersion));
    }

    private static Optional<VanillaBiomes.Biome> known(Messages messages, String id) {
        return VanillaBiomes.find(id).filter(b -> messages.has(biomeKey(b)));
    }

    static String falloffKey(Brush.Falloff falloff) {
        return "falloff." + falloff.name().toLowerCase(Locale.ROOT);
    }
}
