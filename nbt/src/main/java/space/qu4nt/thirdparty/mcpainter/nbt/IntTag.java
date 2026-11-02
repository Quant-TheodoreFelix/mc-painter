package space.qu4nt.thirdparty.mcpainter.nbt;

public record IntTag(int value) implements Tag {

    @Override
    public TagType type() {
        return TagType.INT;
    }
}
