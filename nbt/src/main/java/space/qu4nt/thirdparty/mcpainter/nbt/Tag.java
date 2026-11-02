package space.qu4nt.thirdparty.mcpainter.nbt;

public sealed interface Tag
        permits ByteTag, ShortTag, IntTag, LongTag, FloatTag, DoubleTag, StringTag,
                ByteArrayTag, IntArrayTag, LongArrayTag, ListTag, CompoundTag {

    TagType type();
}
