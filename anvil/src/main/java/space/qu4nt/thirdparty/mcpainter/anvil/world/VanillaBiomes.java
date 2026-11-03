package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class VanillaBiomes {

    public static final String NAMESPACE = "minecraft";

    public enum Availability {
        AVAILABLE,
        TOO_NEW,
        UNKNOWN,
        CUSTOM
    }

    public record Biome(String path, int sinceDataVersion, boolean overworld) {

        public String id() {
            return NAMESPACE + ":" + path;
        }
    }

    private static final List<Biome> ALL = List.of(
            overworld("plains", DataVersions.V1_18),
            overworld("sunflower_plains", DataVersions.V1_18),
            overworld("snowy_plains", DataVersions.V1_18),
            overworld("ice_spikes", DataVersions.V1_18),
            overworld("desert", DataVersions.V1_18),
            overworld("swamp", DataVersions.V1_18),
            overworld("mangrove_swamp", DataVersions.V1_19),
            overworld("forest", DataVersions.V1_18),
            overworld("flower_forest", DataVersions.V1_18),
            overworld("birch_forest", DataVersions.V1_18),
            overworld("dark_forest", DataVersions.V1_18),
            overworld("pale_garden", DataVersions.V1_21_4),
            overworld("dappled_forest", DataVersions.V26_3),
            overworld("old_growth_birch_forest", DataVersions.V1_18),
            overworld("old_growth_pine_taiga", DataVersions.V1_18),
            overworld("old_growth_spruce_taiga", DataVersions.V1_18),
            overworld("taiga", DataVersions.V1_18),
            overworld("snowy_taiga", DataVersions.V1_18),
            overworld("savanna", DataVersions.V1_18),
            overworld("savanna_plateau", DataVersions.V1_18),
            overworld("windswept_hills", DataVersions.V1_18),
            overworld("windswept_gravelly_hills", DataVersions.V1_18),
            overworld("windswept_forest", DataVersions.V1_18),
            overworld("windswept_savanna", DataVersions.V1_18),
            overworld("jungle", DataVersions.V1_18),
            overworld("sparse_jungle", DataVersions.V1_18),
            overworld("bamboo_jungle", DataVersions.V1_18),
            overworld("badlands", DataVersions.V1_18),
            overworld("eroded_badlands", DataVersions.V1_18),
            overworld("wooded_badlands", DataVersions.V1_18),
            overworld("meadow", DataVersions.V1_18),
            overworld("cherry_grove", DataVersions.V1_20),
            overworld("grove", DataVersions.V1_18),
            overworld("snowy_slopes", DataVersions.V1_18),
            overworld("frozen_peaks", DataVersions.V1_18),
            overworld("jagged_peaks", DataVersions.V1_18),
            overworld("stony_peaks", DataVersions.V1_18),
            overworld("river", DataVersions.V1_18),
            overworld("frozen_river", DataVersions.V1_18),
            overworld("beach", DataVersions.V1_18),
            overworld("snowy_beach", DataVersions.V1_18),
            overworld("stony_shore", DataVersions.V1_18),
            overworld("warm_ocean", DataVersions.V1_18),
            overworld("lukewarm_ocean", DataVersions.V1_18),
            overworld("deep_lukewarm_ocean", DataVersions.V1_18),
            overworld("ocean", DataVersions.V1_18),
            overworld("deep_ocean", DataVersions.V1_18),
            overworld("cold_ocean", DataVersions.V1_18),
            overworld("deep_cold_ocean", DataVersions.V1_18),
            overworld("frozen_ocean", DataVersions.V1_18),
            overworld("deep_frozen_ocean", DataVersions.V1_18),
            overworld("mushroom_fields", DataVersions.V1_18),
            overworld("dripstone_caves", DataVersions.V1_18),
            overworld("lush_caves", DataVersions.V1_18),
            overworld("deep_dark", DataVersions.V1_19),
            overworld("sulfur_caves", DataVersions.V26_2),
            other("nether_wastes", DataVersions.V1_18),
            other("soul_sand_valley", DataVersions.V1_18),
            other("crimson_forest", DataVersions.V1_18),
            other("warped_forest", DataVersions.V1_18),
            other("basalt_deltas", DataVersions.V1_18),
            other("the_end", DataVersions.V1_18),
            other("small_end_islands", DataVersions.V1_18),
            other("end_midlands", DataVersions.V1_18),
            other("end_highlands", DataVersions.V1_18),
            other("end_barrens", DataVersions.V1_18),
            other("the_void", DataVersions.V1_18));

    private static final Map<String, Biome> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Biome::id, Function.identity()));

    private VanillaBiomes() {
    }

    public static List<Biome> all() {
        return ALL;
    }

    public static List<Biome> overworld() {
        return ALL.stream().filter(Biome::overworld).toList();
    }

    public static Optional<Biome> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
    }

    public static Availability availability(String id, int dataVersion) {
        Biome biome = id == null ? null : BY_ID.get(id);
        if (biome != null) {
            return biome.sinceDataVersion() <= dataVersion ? Availability.AVAILABLE : Availability.TOO_NEW;
        }
        return id != null && id.startsWith(NAMESPACE + ":") ? Availability.UNKNOWN : Availability.CUSTOM;
    }

    private static Biome overworld(String path, int since) {
        return new Biome(path, since, true);
    }

    private static Biome other(String path, int since) {
        return new Biome(path, since, false);
    }
}
