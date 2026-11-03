package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import space.qu4nt.thirdparty.mcpainter.world.Layer;

enum LayerGroup {
    COAL_ORE(Layer.COAL_ORE),
    IRON_ORE(Layer.IRON_ORE),
    OAK_TREES(Layer.OAK_TREES),
    GRASS(Layer.GRASS),
    FLOWERS(null);

    static final String FLOWERS_ID = "flowers";
    static final List<Layer> DEFAULT_FLOWERS = List.of(Layer.DANDELION, Layer.POPPY);

    private final Layer layer;

    LayerGroup(Layer layer) {
        this.layer = layer;
    }

    String id() {
        return layer == null ? FLOWERS_ID : layer.id();
    }

    List<Layer> layers(List<Layer> flowers) {
        return layer == null ? flowers : List.of(layer);
    }

    static Optional<LayerGroup> fromId(String id) {
        for (LayerGroup group : values()) {
            if (group.id().equals(id)) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    static List<Layer> requireFlowers(Collection<Layer> flowers) {
        Objects.requireNonNull(flowers, "flowers");
        if (flowers.isEmpty()) {
            throw new IllegalArgumentException("at least one flower is required");
        }
        Set<Layer> set = EnumSet.noneOf(Layer.class);
        for (Layer flower : flowers) {
            if (!Objects.requireNonNull(flower, "flower").flower()) {
                throw new IllegalArgumentException(flower.id() + " is not a flower");
            }
            set.add(flower);
        }
        return List.copyOf(set);
    }
}
