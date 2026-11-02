package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Comparator;

public record TilePos(int x, int z) implements Comparable<TilePos> {

    private static final Comparator<TilePos> ORDER = Comparator.comparingInt(TilePos::z).thenComparingInt(TilePos::x);

    public static TilePos ofBlock(int blockX, int blockZ) {
        return new TilePos(blockX >> Tile.SHIFT, blockZ >> Tile.SHIFT);
    }

    public int minBlockX() {
        return x << Tile.SHIFT;
    }

    public int minBlockZ() {
        return z << Tile.SHIFT;
    }

    @Override
    public int compareTo(TilePos other) {
        return ORDER.compare(this, other);
    }
}
