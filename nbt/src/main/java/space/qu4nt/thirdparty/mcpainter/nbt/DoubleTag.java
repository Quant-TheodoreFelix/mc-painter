package space.qu4nt.thirdparty.mcpainter.nbt;

public record DoubleTag(double value) implements Tag {

    @Override
    public TagType type() {
        return TagType.DOUBLE;
    }
}
