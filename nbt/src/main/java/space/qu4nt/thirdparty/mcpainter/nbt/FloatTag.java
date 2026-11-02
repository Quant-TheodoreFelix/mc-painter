package space.qu4nt.thirdparty.mcpainter.nbt;

public record FloatTag(float value) implements Tag {

    @Override
    public TagType type() {
        return TagType.FLOAT;
    }
}
