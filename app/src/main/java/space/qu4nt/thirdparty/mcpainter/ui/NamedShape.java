package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

record NamedShape(String name, BrushShape.Mask mask) {

    NamedShape {
        name = BrushLibrary.requireName(name);
        Objects.requireNonNull(mask, "mask");
    }
}
