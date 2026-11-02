package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;

public record BlockStateEntry(ResourceLocation id, Optional<SortedMap<String, String>> properties) {

    public BlockStateEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(properties, "properties");
        properties = properties.map(map -> {
            for (var entry : map.entrySet()) {
                if (entry.getKey().isEmpty() || entry.getValue().isEmpty()) {
                    throw new IllegalArgumentException("block property names and values must not be empty");
                }
            }
            return Collections.unmodifiableSortedMap(new TreeMap<>(map));
        });
    }

    public static BlockStateEntry of(ResourceLocation id, SortedMap<String, String> properties) {
        return new BlockStateEntry(id, Optional.of(properties));
    }

    public static BlockStateEntry withoutProperties(ResourceLocation id) {
        return new BlockStateEntry(id, Optional.of(new TreeMap<>()));
    }

    public static BlockStateEntry defaultState(ResourceLocation id) {
        return new BlockStateEntry(id, Optional.empty());
    }

    public boolean isDefaultElided() {
        return properties.isEmpty();
    }

    public boolean hasKnownProperties() {
        return properties.isPresent();
    }
}
