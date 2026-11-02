package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.anvil.world.UnsupportedVersionException;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;

public record ChunkData(int dataVersion, int x, int z, String status, List<ChunkSection> sections, RootTag source) {

    public static final int MAX_SECTIONS = 512;

    private static final String SECTIONS = "sections";

    public ChunkData {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        sections = List.copyOf(sections);
        Set<Integer> seen = new HashSet<>();
        for (ChunkSection section : sections) {
            if (!seen.add(section.y())) {
                throw new IllegalArgumentException("duplicate section y " + section.y());
            }
        }
    }

    public static ChunkData parse(RootTag root) throws ChunkFormatException, UnsupportedVersionException {
        CompoundTag tag = root.tag();
        int dataVersion = requireInt(tag, "DataVersion");
        DataVersions.requireSupported(dataVersion);
        int x = requireInt(tag, "xPos");
        int z = requireInt(tag, "zPos");
        String status = tag.get("Status", StringTag.class).map(StringTag::value).orElse("");
        ListTag sectionList = tag.get(SECTIONS, ListTag.class).orElse(ListTag.empty());
        if (!sectionList.isEmpty() && sectionList.elementType() != TagType.COMPOUND) {
            throw new ChunkFormatException("sections must be a list of compounds");
        }
        if (sectionList.size() > MAX_SECTIONS) {
            throw new ChunkFormatException("chunk has " + sectionList.size() + " sections, limit " + MAX_SECTIONS);
        }
        List<ChunkSection> sections = new ArrayList<>(sectionList.size());
        Set<Integer> seen = new HashSet<>();
        for (Tag element : sectionList) {
            ChunkSection section = ChunkSection.parse((CompoundTag) element, dataVersion);
            if (!seen.add(section.y())) {
                throw new ChunkFormatException("duplicate section y " + section.y());
            }
            sections.add(section);
        }
        return new ChunkData(dataVersion, x, z, status, sections, root);
    }

    public RootTag toNbt() {
        List<Tag> encoded = new ArrayList<>(sections.size());
        for (ChunkSection section : sections) {
            encoded.add(section.toNbt(dataVersion));
        }
        ListTag list = encoded.isEmpty() ? ListTag.empty() : ListTag.of(TagType.COMPOUND, encoded);
        CompoundTag tag = source.tag().toBuilder().put(SECTIONS, list).build();
        return new RootTag(source.name(), tag);
    }

    public ChunkData withSections(List<ChunkSection> newSections) {
        return new ChunkData(dataVersion, x, z, status, newSections, source);
    }

    private static int requireInt(CompoundTag tag, String key) throws ChunkFormatException {
        return tag.get(key, IntTag.class)
                .orElseThrow(() -> new ChunkFormatException("chunk has no " + key + " int"))
                .value();
    }
}
