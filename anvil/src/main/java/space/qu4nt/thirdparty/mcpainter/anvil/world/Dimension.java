package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.util.Objects;

public record Dimension(ResourceLocation id) {

    public static final Dimension OVERWORLD = new Dimension(ResourceLocation.minecraft("overworld"));
    public static final Dimension THE_NETHER = new Dimension(ResourceLocation.minecraft("the_nether"));
    public static final Dimension THE_END = new Dimension(ResourceLocation.minecraft("the_end"));

    public Dimension {
        Objects.requireNonNull(id, "id");
    }

    public boolean isVanilla() {
        return equals(OVERWORLD) || equals(THE_NETHER) || equals(THE_END);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
