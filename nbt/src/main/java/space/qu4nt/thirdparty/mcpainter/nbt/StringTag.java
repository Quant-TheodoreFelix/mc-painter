package space.qu4nt.thirdparty.mcpainter.nbt;

public record StringTag(String value) implements Tag {

    public StringTag {
        Mutf8.requireEncodable(value, "string value");
    }

    @Override
    public TagType type() {
        return TagType.STRING;
    }
}
