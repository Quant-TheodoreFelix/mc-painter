package space.qu4nt.thirdparty.mcpainter.nbt;

public record LongTag(long value) implements Tag {

    @Override
    public TagType type() {
        return TagType.LONG;
    }
}
