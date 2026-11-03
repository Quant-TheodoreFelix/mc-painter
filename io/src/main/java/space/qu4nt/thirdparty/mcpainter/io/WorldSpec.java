package space.qu4nt.thirdparty.mcpainter.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.FloatTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;
import space.qu4nt.thirdparty.mcpainter.world.BaseTerrain;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public record WorldSpec(int minY, int maxY, int seaLevel, List<String> biomes, GenerationSettings generation,
        TileRect bounds, BaseTerrain base) {

    static final int BOUNDS_VERSION = 4;

    public WorldSpec {
        biomes = List.copyOf(biomes);
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(base, "base");
        if (!TileRect.WORLD.contains(bounds)) {
            throw new IllegalArgumentException("bounds outside the world limit");
        }
        WorldModel.builder(minY, maxY, seaLevel).biomes(biomes).generation(generation).bounds(bounds).base(base)
                .build();
    }

    public static WorldSpec of(WorldModel model) {
        return new WorldSpec(model.minY(), model.maxY(), model.seaLevel(), model.biomes(), model.generation(),
                model.bounds(), model.base());
    }

    public WorldSpec withBiomes(List<String> value) {
        return new WorldSpec(minY, maxY, seaLevel, value, generation, bounds, base);
    }

    public WorldSpec withGeneration(GenerationSettings value) {
        return new WorldSpec(minY, maxY, seaLevel, biomes, value, bounds, base);
    }

    public WorldModel.Builder builder() {
        return WorldModel.builder(minY, maxY, seaLevel).biomes(biomes).generation(generation).bounds(bounds)
                .base(base);
    }

    CompoundTag toTag() {
        List<Tag> biomeTags = new ArrayList<>();
        for (String biome : biomes) {
            biomeTags.add(new StringTag(biome));
        }
        return CompoundTag.builder()
                .put("minY", new IntTag(minY))
                .put("maxY", new IntTag(maxY))
                .put("seaLevel", new IntTag(seaLevel))
                .put("terrainTypes", TileCodec.terrainIds())
                .put("biomes", ListTag.of(TagType.STRING, biomeTags))
                .put("seed", new LongTag(generation.seed()))
                .put("initialOres", new ByteTag((byte) (generation.initialOres() ? 1 : 0)))
                .put("bounds", IntArrayTag.of(new int[] {bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ()}))
                .put("base", new StringTag(base.kind().id()))
                .put("baseHeight", new FloatTag(base.height()))
                .put("baseTerrain", new StringTag(base.terrain().id()))
                .build();
    }

    static WorldSpec fromTag(CompoundTag header, int version) throws ProjectFormatException {
        GenerationSettings generation = GenerationSettings.DEFAULT;
        if (version >= 2) {
            long seed = header.get("seed", LongTag.class)
                    .orElseThrow(() -> new ProjectFormatException("missing long seed")).value();
            boolean initialOres = false;
            if (header.contains("initialOres")) {
                byte flag = header.get("initialOres", ByteTag.class)
                        .orElseThrow(() -> new ProjectFormatException("initialOres must be a byte")).value();
                if (flag != 0 && flag != 1) {
                    throw new ProjectFormatException("initialOres must be 0 or 1");
                }
                initialOres = flag == 1;
            }
            generation = new GenerationSettings(seed, initialOres);
        }
        List<String> biomes = new ArrayList<>();
        for (Tag tag : TileCodec.requireList(header, "biomes")) {
            biomes.add(((StringTag) tag).value());
        }
        TileRect bounds = TileRect.WORLD;
        BaseTerrain base = BaseTerrain.VOID;
        try {
            if (version >= BOUNDS_VERSION) {
                int[] rect = header.get("bounds", IntArrayTag.class)
                        .orElseThrow(() -> new ProjectFormatException("missing bounds")).toArray();
                if (rect.length != 4) {
                    throw new ProjectFormatException("bounds must have 4 entries");
                }
                bounds = new TileRect(rect[0], rect[1], rect[2], rect[3]);
                String kind = header.get("base", StringTag.class)
                        .orElseThrow(() -> new ProjectFormatException("missing base")).value();
                float height = header.get("baseHeight", FloatTag.class)
                        .orElseThrow(() -> new ProjectFormatException("missing base height")).value();
                String terrain = header.get("baseTerrain", StringTag.class)
                        .orElseThrow(() -> new ProjectFormatException("missing base terrain")).value();
                base = new BaseTerrain(BaseTerrain.Kind.fromId(kind)
                        .orElseThrow(() -> new ProjectFormatException("unknown base " + kind)), height,
                        TerrainType.fromId(terrain)
                                .orElseThrow(() -> new ProjectFormatException("unknown terrain " + terrain)));
            }
            return new WorldSpec(TileCodec.requireInt(header, "minY"), TileCodec.requireInt(header, "maxY"),
                    TileCodec.requireInt(header, "seaLevel"), biomes, generation, bounds, base);
        } catch (IllegalArgumentException e) {
            throw new ProjectFormatException("invalid header: " + e.getMessage(), e);
        }
    }
}
