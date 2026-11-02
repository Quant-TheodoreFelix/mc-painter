package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;

public record BlockStateEntry(ResourceLocation id, Optional<SequencedMap<String, String>> properties) {

    public BlockStateEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(properties, "properties");
        properties = properties.map(BlockStateEntry::copyProperties);
    }

    public static BlockStateEntry of(ResourceLocation id, Map<String, String> properties) {
        return new BlockStateEntry(id, Optional.of(copyProperties(properties)));
    }

    public static BlockStateEntry withoutProperties(ResourceLocation id) {
        return new BlockStateEntry(id, Optional.of(Collections.unmodifiableSequencedMap(new LinkedHashMap<>())));
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

    private static SequencedMap<String, String> copyProperties(Map<String, String> source) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "property name");
            String value = Objects.requireNonNull(entry.getValue(), "property value");
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("block property names and values must not be empty");
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableSequencedMap(copy);
    }
}
