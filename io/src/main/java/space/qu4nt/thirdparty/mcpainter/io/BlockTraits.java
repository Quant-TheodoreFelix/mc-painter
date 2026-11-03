package space.qu4nt.thirdparty.mcpainter.io;

public record BlockTraits(boolean air, boolean blocksMotion, boolean fluid, boolean leaves) {

    public static final BlockTraits AIR = new BlockTraits(true, false, false, false);
    public static final BlockTraits SOLID = new BlockTraits(false, true, false, false);
    public static final BlockTraits FLUID = new BlockTraits(false, false, true, false);
    public static final BlockTraits LEAVES = new BlockTraits(false, true, false, true);
    public static final BlockTraits PLANT = new BlockTraits(false, false, false, false);

    public boolean countsFor(String heightmapType) {
        return switch (heightmapType) {
            case "WORLD_SURFACE" -> !air;
            case "OCEAN_FLOOR" -> blocksMotion;
            case "MOTION_BLOCKING" -> blocksMotion || fluid;
            case "MOTION_BLOCKING_NO_LEAVES" -> (blocksMotion || fluid) && !leaves;
            default -> throw new IllegalArgumentException("unknown heightmap type " + heightmapType);
        };
    }
}
