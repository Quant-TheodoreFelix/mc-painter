package space.qu4nt.thirdparty.mcpainter.nbt;

import java.util.Objects;

public record RootTag(String name, CompoundTag tag) {

    public RootTag {
        Mutf8.requireEncodable(name, "root name");
        Objects.requireNonNull(tag, "tag");
    }

    public static RootTag of(CompoundTag tag) {
        return new RootTag("", tag);
    }
}
