@SuppressWarnings("module")
module space.qu4nt.thirdparty.mcpainter.anvil {
    requires transitive space.qu4nt.thirdparty.mcpainter.nbt;

    exports space.qu4nt.thirdparty.mcpainter.anvil.chunk;
    exports space.qu4nt.thirdparty.mcpainter.anvil.region;
    exports space.qu4nt.thirdparty.mcpainter.anvil.world;
    exports space.qu4nt.thirdparty.mcpainter.anvil.internal to space.qu4nt.thirdparty.mcpainter.io;
}
