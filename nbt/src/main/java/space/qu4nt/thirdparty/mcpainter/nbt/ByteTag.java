package space.qu4nt.thirdparty.mcpainter.nbt;

public record ByteTag(byte value) implements Tag {

    @Override
    public TagType type() {
        return TagType.BYTE;
    }
}
