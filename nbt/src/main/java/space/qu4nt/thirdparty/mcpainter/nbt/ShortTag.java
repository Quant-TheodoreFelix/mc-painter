package space.qu4nt.thirdparty.mcpainter.nbt;

public record ShortTag(short value) implements Tag {

    @Override
    public TagType type() {
        return TagType.SHORT;
    }
}
